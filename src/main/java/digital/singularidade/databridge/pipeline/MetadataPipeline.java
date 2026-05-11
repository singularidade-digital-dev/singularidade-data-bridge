package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.BuildInfo;
import digital.singularidade.databridge.error.UrlRedaction;
import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.CheckConstraint;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.output.ColumnStats;
import digital.singularidade.databridge.output.ForeignKey;
import digital.singularidade.databridge.output.Index;
import digital.singularidade.databridge.output.Metadata;
import digital.singularidade.databridge.output.Partitioning;
import digital.singularidade.databridge.output.Sample;
import digital.singularidade.databridge.output.SourceInfo;
import digital.singularidade.databridge.output.TableInfo;
import digital.singularidade.databridge.output.UniqueConstraint;
import digital.singularidade.databridge.source.CardinalityMode;
import digital.singularidade.databridge.source.Source;

import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

public final class MetadataPipeline {

    public static final String SCHEMA_URL = "https://singularidade.digital/data-bridge/metadata.v1.json";
    public static final String VERSION = "1.0";
    public static final String GENERATOR_NAME = BuildInfo.NAME;
    public static final String GENERATOR_VERSION = BuildInfo.VERSION;

    private final PrintStream progress;
    private final CardinalityMode cardinalityMode;

    public MetadataPipeline() { this(System.err, CardinalityMode.EXACT); }

    public MetadataPipeline(PrintStream progress, CardinalityMode cardinalityMode) {
        this.progress = progress;
        this.cardinalityMode = cardinalityMode;
    }

    public Metadata run(Source source, String jdbcUrl, String schema, String table, int sampleRows) {
        List<String> warnings = new ArrayList<>();
        TableInfo tableInfo = step(1, "table info", () -> source.tableInfo(schema, table));
        List<Column> columns = step(2, "columns", () -> source.columns(schema, table));
        List<String> pk = step(3, "primary key", () -> source.primaryKey(schema, table));
        List<ForeignKey> fks = step(4, "foreign keys (1-deep)", () -> source.foreignKeys(schema, table));
        List<Index> indexes = step(5, "indexes", () -> source.indexes(schema, table));
        List<UniqueConstraint> ucs = step(6, "unique constraints", () -> source.uniqueConstraints(schema, table));
        List<CheckConstraint> ccs = step(7, "check constraints", () -> source.checkConstraints(schema, table));
        Sample sample = step(8, "sample", () -> source.sample(schema, table, sampleRows));
        List<ColumnStats> stats = step(9, "column stats", () -> source.columnStats(schema, table));
        if (stats.isEmpty() && !"postgresql".equals(source.driverWireName())) {
            warnings.add("columnStats not available for driver '" + source.driverWireName() + "' in MVP");
        }
        Partitioning part = step(10, "partitioning", () -> source.partitioning(schema, table));

        Cardinality card = step(11, "cardinality (" + cardinalityMode.wireName() + ")",
            () -> source.cardinality(schema, table, columns, cardinalityMode));

        switch (cardinalityMode) {
            case SKIP -> warnings.add("Cardinality skipped (--cardinality-mode skip)");
            case APPROXIMATE -> {
                if ("postgresql".equals(source.driverWireName())) {
                    warnings.add("Cardinality is approximate (pg_class.reltuples + pg_stats.n_distinct); "
                        + "run --cardinality-mode exact for COUNT/COUNT(DISTINCT) accuracy.");
                } else {
                    warnings.add("Cardinality mode 'approximate' falls back to skip on driver '"
                        + source.driverWireName() + "' (pg_stats not available).");
                }
                addOmittedColumnWarnings(warnings, columns, card);
            }
            case EXACT -> addOmittedColumnWarnings(warnings, columns, card);
        }

        SourceInfo srcInfo = new SourceInfo("jdbc", source.driverWireName(),
            UrlRedaction.redact(jdbcUrl), schema, table);

        return new Metadata(
            SCHEMA_URL, VERSION, Instant.now(),
            new Metadata.Generator(GENERATOR_NAME, GENERATOR_VERSION),
            srcInfo, tableInfo, columns, pk, fks, indexes, ucs, ccs,
            sample, stats, card, part, warnings
        );
    }

    /**
     * For each column NOT present in {@code card.perColumn()}, emit one warning naming the column
     * and its sqlType. Covers BLOB/CLOB/text types (always skipped) and, in APPROXIMATE mode on PG,
     * columns that have no {@code pg_stats} row yet (table never ANALYZE'd, or column added since).
     */
    private static void addOmittedColumnWarnings(List<String> warnings,
                                                  List<Column> columns,
                                                  Cardinality card) {
        Set<String> kept = new HashSet<>();
        for (Cardinality.PerColumn pc : card.perColumn()) kept.add(pc.name());
        for (Column c : columns) {
            if (!kept.contains(c.name())) {
                warnings.add("Cardinality omitted for column '" + c.name()
                    + "' (sqlType=" + c.sqlType() + "); BLOB/text types are skipped, "
                    + "or no pg_stats row exists yet (run ANALYZE).");
            }
        }
    }

    private <T> T step(int n, String label, Callable<T> body) {
        long t0 = System.nanoTime();
        try {
            T result = body.call();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            progress.printf("[%d/11] %-22s ok (%dms)%n", n, label, ms);
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
