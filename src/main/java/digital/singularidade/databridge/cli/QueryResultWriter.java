package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.output.JsonWriter;
import digital.singularidade.databridge.output.QueryResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes a {@link QueryResult} as pretty JSON to stdout or to {@code --out}; shared by query and run-query. */
public final class QueryResultWriter {

    private QueryResultWriter() {}

    public static void write(QueryResult result, String outFile, boolean quiet) throws IOException {
        byte[] body = JsonWriter.newMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
        if (outFile == null || outFile.equals("-")) {
            System.out.write(body);
            System.out.write('\n');
            System.out.flush();
            return;
        }
        Path target = Path.of(outFile);
        Path dir = target.toAbsolutePath().getParent();
        if (dir != null) Files.createDirectories(dir);
        Files.write(target, body);
        if (!quiet) System.err.println("wrote " + target.toAbsolutePath());
    }
}
