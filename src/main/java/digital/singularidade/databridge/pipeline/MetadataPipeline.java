package digital.singularidade.databridge.pipeline;

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
import digital.singularidade.databridge.source.Source;

import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public final class MetadataPipeline {

    public static final String SCHEMA_URL = "https://singularidade.digital/data-bridge/metadata.v1.json";
    public static final String VERSION = "1.0";
    public static final String GENERATOR_NAME = "singularidade-data-bridge";
    public static final String GENERATOR_VERSION = "0.1.0";

    private final PrintStream progress;
    private final boolean skipCardinality;

    public MetadataPipeline() { this(System.err, false); }

    public MetadataPipeline(PrintStream progress, boolean skipCardinality) {
        this.progress = progress;
        this.skipCardinality = skipCardinality;
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

        Cardinality card;
        if (skipCardinality) {
            warnings.add("Cardinality skipped (--no-cardinality)");
            card = new Cardinality(0L, List.of());
        } else {
            card = step(11, "cardinality", () -> source.cardinality(schema, table, columns));
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
