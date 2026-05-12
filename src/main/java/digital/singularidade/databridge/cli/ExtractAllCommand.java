package digital.singularidade.databridge.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.BuildInfo;
import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.error.UrlRedaction;
import digital.singularidade.databridge.output.ColumnStatsMode;
import digital.singularidade.databridge.output.ExtractAllIndex;
import digital.singularidade.databridge.output.JsonWriter;
import digital.singularidade.databridge.output.Metadata;
import digital.singularidade.databridge.output.SourceUrlRedaction;
import digital.singularidade.databridge.output.SqlFileWriter;
import digital.singularidade.databridge.output.TsvWriter;
import digital.singularidade.databridge.pipeline.MetadataPipeline;
import digital.singularidade.databridge.pipeline.ddl.DdlBuilders;
import digital.singularidade.databridge.source.CardinalityMode;
import digital.singularidade.databridge.source.jdbc.DriverHints;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(name = "extract-all",
    description = "Extract metadata for every table in a schema. --schema is required for "
                + "PG/MySQL/Oracle/MSSQL and optional for Firebird (single-namespace DB). "
                + "Produces one subdirectory per table plus a top-level _index.json summary.")
public final class ExtractAllCommand implements Callable<Integer> {

    @Option(names = "--jdbc-url", required = true) String jdbcUrl;
    @Option(names = "--schema",
            description = "Source schema name. Required for PG/MySQL/Oracle/MSSQL. Omit for Firebird.")
    String schema;
    @Option(names = "--out", required = true) Path outDir;

    @Option(names = "--sample-rows", defaultValue = "0",
            description = "Sample row count per table. Default 0 = no sample (PII-safe). "
                        + "Pass e.g. --sample-rows 5 for 5 real rows per table.")
    int sampleRows;

    @Option(names = "--tsv", defaultValue = "false") boolean tsv;

    @Option(names = "--cardinality-mode", defaultValue = "approximate",
            description = "approximate (default for extract-all): pg_class.reltuples + pg_stats (PG only; "
                        + "sub-second). exact: COUNT(*) + COUNT(DISTINCT col) per column (authoritative, "
                        + "minutes per large table). skip: emit empty cardinality.")
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

    @Option(names = "--include-views", defaultValue = "false",
            description = "Also extract views and materialized views (default: tables only).")
    boolean includeViews;

    @Option(names = "--include-ddl", defaultValue = "true",
            description = "Write a sibling ddl.sql file with reconstructed CREATE TABLE/INDEX/etc. "
                        + "Default on for PG/MySQL/Oracle/Firebird. MSSQL gets a placeholder file.")
    boolean includeDdl;

    @Option(names = "--include-triggers", defaultValue = "false",
            description = "Also write triggers.sql alongside ddl.sql. Off by default because trigger "
                        + "extraction can substantially inflate the snapshot.")
    boolean includeTriggers;

    @Option(names = "--exclude", arity = "*",
            description = "Table name(s) to skip. Repeatable: --exclude audit_log --exclude staging.")
    List<String> excludes = new ArrayList<>();

    @Option(names = {"-q", "--quiet"}, defaultValue = "false") boolean quiet;
    @Option(names = {"-v", "--verbose"}, defaultValue = "false") boolean verbose;

    @Override
    public Integer call() {
        PrintStream progress = quiet ? new PrintStream(OutputStream.nullOutputStream()) : System.err;
        CardinalityMode mode = skipCardinality ? CardinalityMode.SKIP
            : CardinalityMode.fromWireName(cardinalityMode);
        ColumnStatsMode csMode = ColumnStatsMode.fromWireName(columnStatsMode);
        SourceUrlRedaction urlRed = SourceUrlRedaction.fromWireName(sourceUrlRedaction);
        try (JdbcSource src = JdbcSource.open(jdbcUrl)) {
            boolean schemaEmpty = (schema == null || schema.isBlank());
            if (schemaEmpty && src.hints() != DriverHints.FIREBIRD) {
                throw new DataBridgeException(ErrorCodes.INVALID_ARGS,
                    "--schema is required for driver '" + src.hints().wireName() + "'",
                    "Firebird is the only single-namespace driver that can omit --schema. "
                  + "For PG/MySQL/Oracle/MSSQL pass e.g. --schema=public.");
            }
            String schemaLabel = schemaEmpty ? "(no schema — Firebird)" : "schema '" + schema + "'";
            Set<String> excludeSet = new HashSet<>(excludes);
            List<String> tables = src.listTables(schema, includeViews).stream()
                .filter(t -> !excludeSet.contains(t))
                .toList();
            progress.printf("Found %d table(s) in %s%s%s%n",
                tables.size(), schemaLabel,
                includeViews ? " (incl. views)" : "",
                excludeSet.isEmpty() ? "" : " (excluded " + excludeSet.size() + ")");

            List<ExtractAllIndex.TableSummary> summaries = new ArrayList<>();
            for (int i = 0; i < tables.size(); i++) {
                String table = tables.get(i);
                String tableLabel = schemaEmpty ? table : schema + "." + table;
                progress.printf("== [%d/%d] %s ==%n", i + 1, tables.size(), tableLabel);

                Metadata m = new MetadataPipeline(progress, mode, csMode, urlRed)
                    .run(src, jdbcUrl, schema, table, sampleRows);

                Path tableDir = ExtractCommand.targetDir(outDir, schema, table);
                new JsonWriter().write(m, tableDir.resolve("metadata.json"));
                if (tsv) new TsvWriter().writeAll(m, tableDir);
                if (includeDdl) {
                    DdlBuilders.forDriver(src.hints())
                        .build(src, schema, table, urlRed.apply(jdbcUrl), includeTriggers)
                        .ifPresent(script -> {
                            new SqlFileWriter().write(script.toFileText(), tableDir.resolve("ddl.sql"));
                            if (includeTriggers && !script.triggers().isBlank()) {
                                new SqlFileWriter().write(script.triggersFileText(), tableDir.resolve("triggers.sql"));
                            }
                        });
                }

                summaries.add(new ExtractAllIndex.TableSummary(
                    schema, table, m.tableInfo().type(),
                    m.columns().size(), m.primaryKey(),
                    mode == CardinalityMode.SKIP ? null : m.cardinality().totalRows()));
            }

            ExtractAllIndex index = new ExtractAllIndex(
                "https://singularidade.digital/data-bridge/extract-all-index.v1.json",
                "1.0",
                Instant.now(),
                new Metadata.Generator(BuildInfo.NAME, BuildInfo.VERSION),
                new ExtractAllIndex.SchemaSource("jdbc", src.driverWireName(),
                    urlRed.apply(jdbcUrl), schema),
                summaries.size(),
                summaries);
            writeIndex(index, outDir.resolve("_index.json"));

            progress.printf("Done — wrote %d table(s) + _index.json to %s%n",
                summaries.size(), outDir.toAbsolutePath());
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

    private static void writeIndex(ExtractAllIndex index, Path target) {
        Path dir = target.getParent();
        Path tmp = dir.resolve("." + target.getFileName().toString() + ".partial");
        try {
            Files.createDirectories(dir);
            ObjectMapper mapper = JsonWriter.newMapper();
            byte[] body = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(index);
            Files.write(tmp, body);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
            throw new DataBridgeException(ErrorCodes.OUTPUT_WRITE_FAILED,
                "Failed to write _index.json: " + e.getMessage(),
                "Verify --out is a writable directory with free space", e);
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
}
