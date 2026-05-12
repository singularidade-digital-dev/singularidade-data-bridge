package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.error.UrlRedaction;
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
import picocli.CommandLine.Option;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "extract", description = "Extract metadata + sample for one table.")
public final class ExtractCommand implements Callable<Integer> {

    @Option(names = "--jdbc-url", required = true) String jdbcUrl;
    @Option(names = "--schema") String schema;
    @Option(names = "--table", required = true) String table;
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
        try (JdbcSource src = JdbcSource.open(jdbcUrl)) {
            CardinalityMode mode = skipCardinality ? CardinalityMode.SKIP
                : CardinalityMode.fromWireName(cardinalityMode);
            ColumnStatsMode csMode = ColumnStatsMode.fromWireName(columnStatsMode);
            SourceUrlRedaction urlRed = SourceUrlRedaction.fromWireName(sourceUrlRedaction);
            Metadata m = new MetadataPipeline(progress, mode, csMode, urlRed)
                .run(src, jdbcUrl, schema, table, sampleRows);
            Path tableDir = targetDir(outDir, schema, table);
            new JsonWriter().write(m, tableDir.resolve("metadata.json"));
            if (tsv) new TsvWriter().writeAll(m, tableDir);
            if (includeDdl) {
                DdlBuilder builder = DdlBuilders.forDriver(src.hints());
                builder.build(src, schema, table, urlRed.apply(jdbcUrl), includeTriggers).ifPresent(script -> {
                    new SqlFileWriter().write(script.toFileText(), tableDir.resolve("ddl.sql"));
                    if (includeTriggers && !script.triggers().isBlank()) {
                        new SqlFileWriter().write(script.triggersFileText(), tableDir.resolve("triggers.sql"));
                    }
                });
            }
            return ErrorCodes.OK.exitCode();
        } catch (DataBridgeException e) {
            emitError(e);
            return e.code().exitCode();
        } catch (RuntimeException e) {
            emitError(new DataBridgeException(ErrorCodes.UNSPECIFIED,
                e.getMessage(), null, e));
            return ErrorCodes.UNSPECIFIED.exitCode();
        }
    }

    private void emitError(DataBridgeException e) {
        StringBuilder sb = new StringBuilder("{\"error\":{\"code\":\"")
            .append(e.code().wireName()).append("\",\"message\":\"")
            .append(jsonEscape(UrlRedaction.redact(e.getMessage())))
            .append("\"");
        if (e.hint() != null) sb.append(",\"hint\":\"").append(jsonEscape(e.hint())).append("\"");
        if (verbose && e.getCause() != null) {
            sb.append(",\"cause\":\"")
                .append(jsonEscape(e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage()))
                .append("\"");
        }
        sb.append("}}");
        System.err.println(sb);
        if (verbose && e.getCause() != null) e.getCause().printStackTrace(System.err);
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    static Path targetDir(Path outDir, String schema, String table) {
        String name = (schema == null || schema.isBlank()) ? table : schema + "." + table;
        return outDir.resolve(name);
    }
}
