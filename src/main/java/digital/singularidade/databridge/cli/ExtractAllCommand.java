package digital.singularidade.databridge.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.BuildInfo;
import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.error.UrlRedaction;
import digital.singularidade.databridge.output.ExtractAllIndex;
import digital.singularidade.databridge.output.JsonWriter;
import digital.singularidade.databridge.output.Metadata;
import digital.singularidade.databridge.output.TsvWriter;
import digital.singularidade.databridge.pipeline.MetadataPipeline;
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
    description = "Extract metadata for every table in a schema. Produces one "
                + "subdirectory per table plus a top-level _index.json summary.")
public final class ExtractAllCommand implements Callable<Integer> {

    @Option(names = "--jdbc-url", required = true) String jdbcUrl;
    @Option(names = "--schema", required = true) String schema;
    @Option(names = "--out", required = true) Path outDir;

    @Option(names = "--sample-rows", defaultValue = "0",
            description = "Sample row count per table. Default 0 = no sample (PII-safe). "
                        + "Pass e.g. --sample-rows 5 for 5 real rows per table.")
    int sampleRows;

    @Option(names = "--tsv", defaultValue = "false") boolean tsv;
    @Option(names = "--no-cardinality", defaultValue = "false") boolean skipCardinality;

    @Option(names = "--include-views", defaultValue = "false",
            description = "Also extract views and materialized views (default: tables only).")
    boolean includeViews;

    @Option(names = "--exclude", arity = "*",
            description = "Table name(s) to skip. Repeatable: --exclude audit_log --exclude staging.")
    List<String> excludes = new ArrayList<>();

    @Option(names = {"-q", "--quiet"}, defaultValue = "false") boolean quiet;
    @Option(names = {"-v", "--verbose"}, defaultValue = "false") boolean verbose;

    @Override
    public Integer call() {
        PrintStream progress = quiet ? new PrintStream(OutputStream.nullOutputStream()) : System.err;
        try (JdbcSource src = JdbcSource.open(jdbcUrl)) {
            Set<String> excludeSet = new HashSet<>(excludes);
            List<String> tables = src.listTables(schema, includeViews).stream()
                .filter(t -> !excludeSet.contains(t))
                .toList();
            progress.printf("Found %d table(s) in schema '%s'%s%s%n",
                tables.size(), schema,
                includeViews ? " (incl. views)" : "",
                excludeSet.isEmpty() ? "" : " (excluded " + excludeSet.size() + ")");

            List<ExtractAllIndex.TableSummary> summaries = new ArrayList<>();
            for (int i = 0; i < tables.size(); i++) {
                String table = tables.get(i);
                progress.printf("== [%d/%d] %s.%s ==%n", i + 1, tables.size(), schema, table);

                Metadata m = new MetadataPipeline(progress, skipCardinality)
                    .run(src, jdbcUrl, schema, table, sampleRows);

                Path tableDir = ExtractCommand.targetDir(outDir, schema, table);
                new JsonWriter().write(m, tableDir.resolve("metadata.json"));
                if (tsv) new TsvWriter().writeAll(m, tableDir);

                summaries.add(new ExtractAllIndex.TableSummary(
                    schema, table, m.tableInfo().type(),
                    m.columns().size(), m.primaryKey(),
                    skipCardinality ? null : m.cardinality().totalRows()));
            }

            ExtractAllIndex index = new ExtractAllIndex(
                "https://singularidade.digital/data-bridge/extract-all-index.v1.json",
                "1.0",
                Instant.now(),
                new Metadata.Generator(BuildInfo.NAME, BuildInfo.VERSION),
                new ExtractAllIndex.SchemaSource("jdbc", src.driverWireName(),
                    UrlRedaction.redact(jdbcUrl), schema),
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
