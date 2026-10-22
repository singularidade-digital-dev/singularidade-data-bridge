package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.output.ColumnStatsMode;
import digital.singularidade.databridge.output.JsonWriter;
import digital.singularidade.databridge.output.Metadata;
import digital.singularidade.databridge.output.SourceUrlRedaction;
import digital.singularidade.databridge.output.SqlFileWriter;
import digital.singularidade.databridge.output.TsvWriter;
import digital.singularidade.databridge.pipeline.MetadataPipeline;
import digital.singularidade.databridge.pipeline.ddl.DdlBuilder;
import digital.singularidade.databridge.pipeline.ddl.DdlBuilders;
import digital.singularidade.databridge.source.CardinalityMode;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(name = "extract", description = "Extract metadata + sample for one table.")
public final class ExtractCommand implements Callable<Integer> {

    @Mixin ConnectionOptions conn;
    @Option(names = "--schema") String schema;
    @Option(names = "--table", paramLabel = "TABLE",
            description = "Table or view to extract. Repeatable: --table A --table B.")
    List<String> tables = new ArrayList<>();
    @Option(names = "--tables-file", paramLabel = "PATH",
            description = "File with one table name per line (blank lines and # comments ignored).")
    Path tablesFile;
    @Option(names = "--out", required = true) Path outDir;
    @Option(names = "--sample-rows", defaultValue = "0",
            description = "Sample row count. Default 0 = no sample collected (PII-safe). "
                        + "Pass e.g. --sample-rows 5 to include 5 real rows in metadata.json.")
    int sampleRows;
    @Option(names = "--tsv", defaultValue = "false") boolean tsv;

    @Option(names = "--cardinality-mode", defaultValue = "exact",
            description = "exact (default): COUNT(*) + COUNT(DISTINCT col) per column — authoritative, slow. "
                        + "approximate: read pg_class.reltuples + pg_stats (PG only; sub-second). "
                        + "skip: emit empty cardinality.")
    String cardinalityMode;

    @Option(names = "--column-stats-mode", defaultValue = "histogram-only",
            description = "Controls columnStats payload: full keeps mostCommonValues + mostCommonFrequencies "
                        + "(may contain PII real values); histogram-only zeros those (default — safe to commit); "
                        + "off makes columnStats empty.")
    String columnStatsMode;

    @Option(names = "--source-url-redaction", defaultValue = "host-port",
            description = "Controls source.url scrubbing in metadata.json: none keeps host:port "
                        + "(only password redacted); host-port replaces host:port with [redacted-host] "
                        + "(default — safe-by-default); full replaces everything after the JDBC scheme.")
    String sourceUrlRedaction;

    @Option(names = "--no-cardinality", defaultValue = "false",
            description = "Legacy alias for --cardinality-mode skip.")
    boolean skipCardinality;

    @Option(names = "--include-ddl", defaultValue = "true",
            description = "Write a sibling ddl.sql file with reconstructed CREATE TABLE/INDEX/etc. "
                        + "Default on for PG/MySQL/Oracle/Firebird. MSSQL gets a placeholder file.")
    boolean includeDdl;

    @Option(names = "--include-triggers", defaultValue = "false",
            description = "Also write triggers.sql alongside ddl.sql. Off by default because trigger "
                        + "extraction can substantially inflate the snapshot.")
    boolean includeTriggers;

    @Option(names = {"-q", "--quiet"}, defaultValue = "false") boolean quiet;
    @Option(names = {"-v", "--verbose"}, defaultValue = "false") boolean verbose;

    @Override
    public Integer call() {
        PrintStream progress = quiet ? new PrintStream(OutputStream.nullOutputStream()) : System.err;
        try {
            List<String> requested = requestedTables(tables, tablesFile);
            CardinalityMode mode = skipCardinality ? CardinalityMode.SKIP
                : CardinalityMode.fromWireName(cardinalityMode);
            ColumnStatsMode csMode = ColumnStatsMode.fromWireName(columnStatsMode);
            SourceUrlRedaction urlRed = SourceUrlRedaction.fromWireName(sourceUrlRedaction);
            try (JdbcSource src = JdbcSource.open(conn.resolve())) {
                src.requireSchema(schema);
                List<String> resolved = resolveAll(src, requested);
                for (String table : resolved) {
                    if (resolved.size() > 1) progress.printf("== %s ==%n", table);
                    extractOne(src, table, progress, mode, csMode, urlRed);
                }
            }
            return ErrorCodes.OK.exitCode();
        } catch (RuntimeException e) {
            return CliErrors.report(e, verbose);
        }
    }

    private void extractOne(JdbcSource src, String table, PrintStream progress, CardinalityMode mode,
                            ColumnStatsMode csMode, SourceUrlRedaction urlRed) {
        Metadata m = new MetadataPipeline(progress, mode, csMode, urlRed)
            .run(src, conn.jdbcUrl(), schema, table, sampleRows);
        Path tableDir = targetDir(outDir, schema, table);
        new JsonWriter().write(m, tableDir.resolve("metadata.json"));
        if (tsv) new TsvWriter().writeAll(m, tableDir);
        if (includeDdl) {
            DdlBuilder builder = DdlBuilders.forDriver(src.hints());
            builder.build(src, schema, table, urlRed.apply(conn.jdbcUrl()), includeTriggers).ifPresent(script -> {
                new SqlFileWriter().write(script.toFileText(), tableDir.resolve("ddl.sql"));
                if (includeTriggers && !script.triggers().isBlank()) {
                    new SqlFileWriter().write(script.triggersFileText(), tableDir.resolve("triggers.sql"));
                }
            });
        }
    }

    /** Resolve every requested name before anything is written; any miss fails the whole run. */
    private List<String> resolveAll(JdbcSource src, List<String> requested) {
        Set<String> resolved = new LinkedHashSet<>();
        List<String> missing = new ArrayList<>();
        for (String name : requested) {
            src.resolveTable(schema, name).ifPresentOrElse(resolved::add, () -> missing.add(name));
        }
        if (!missing.isEmpty()) {
            String where = schema == null || schema.isBlank() ? "" : " in schema '" + schema + "'";
            throw new DataBridgeException(ErrorCodes.TABLE_NOT_FOUND,
                "table(s) not found" + where + ": " + String.join(", ", missing),
                "Run `data-bridge list-tables` to see the available tables; names match exactly, "
              + "then in the database's default case");
        }
        return List.copyOf(resolved);
    }

    /** Tables from --table and --tables-file, trimmed, without blanks, comments or duplicates. */
    static List<String> requestedTables(List<String> tables, Path file) {
        Set<String> out = new LinkedHashSet<>();
        for (String t : tables == null ? List.<String>of() : tables) {
            if (t != null && !t.isBlank()) out.add(t.strip());
        }
        if (file != null) {
            List<String> lines;
            try {
                lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new DataBridgeException(ErrorCodes.INVALID_ARGS,
                    "cannot read --tables-file " + file + ": " + e.getMessage(), null, e);
            }
            for (String line : lines) {
                String t = line.strip();
                if (!t.isEmpty() && !t.startsWith("#")) out.add(t);
            }
        }
        if (out.isEmpty()) {
            throw new DataBridgeException(ErrorCodes.INVALID_ARGS,
                "no table to extract", "Pass --table NAME (repeatable) or --tables-file PATH");
        }
        return List.copyOf(out);
    }



    static Path targetDir(Path outDir, String schema, String table) {
        String name = (schema == null || schema.isBlank()) ? table : schema + "." + table;
        return outDir.resolve(name);
    }
}
