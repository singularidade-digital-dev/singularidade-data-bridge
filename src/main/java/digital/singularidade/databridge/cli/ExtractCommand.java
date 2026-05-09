package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.error.UrlRedaction;
import digital.singularidade.databridge.output.JsonWriter;
import digital.singularidade.databridge.output.Metadata;
import digital.singularidade.databridge.output.TsvWriter;
import digital.singularidade.databridge.pipeline.MetadataPipeline;
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
    @Option(names = "--sample-rows", defaultValue = "5") int sampleRows;
    @Option(names = "--tsv", defaultValue = "false") boolean tsv;
    @Option(names = "--no-cardinality", defaultValue = "false") boolean skipCardinality;
    @Option(names = {"-q", "--quiet"}, defaultValue = "false") boolean quiet;
    @Option(names = {"-v", "--verbose"}, defaultValue = "false") boolean verbose;

    @Override
    public Integer call() {
        PrintStream progress = quiet ? new PrintStream(OutputStream.nullOutputStream()) : System.err;
        try (JdbcSource src = JdbcSource.open(jdbcUrl)) {
            Metadata m = new MetadataPipeline(progress, skipCardinality)
                .run(src, jdbcUrl, schema, table, sampleRows);
            new JsonWriter().write(m, outDir.resolve("metadata.json"));
            if (tsv) new TsvWriter().writeAll(m, outDir);
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
}
