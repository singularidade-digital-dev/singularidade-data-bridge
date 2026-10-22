package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.output.QueryResult;
import digital.singularidade.databridge.query.NamedQuery;
import digital.singularidade.databridge.query.NamedQueryFile;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "run-query",
    description = "Run one named, parameterized query from a file (read-only). Returns the same JSON as query.")
public final class RunQueryCommand implements Callable<Integer> {

    @Mixin ConnectionOptions conn;

    @Option(names = "--file", required = true, paramLabel = "PATH",
            description = "Queries file: blocks that start with '-- name: <id>'.")
    Path file;

    @Option(names = "--name", required = true, description = "Name of the query to run.")
    String name;

    @Option(names = "--param", paramLabel = "KEY=VALUE",
            description = "Value for the placeholder :KEY. Repeatable. Converted to the type the database expects.")
    Map<String, String> params = new LinkedHashMap<>();

    @Option(names = "--limit", defaultValue = "100", description = "Max rows to return. Default 100.")
    int limit;

    @Option(names = "--timeout-sec", defaultValue = "30", description = "Query timeout in seconds. 0 = none.")
    int timeoutSec;

    @Option(names = "--out", description = "Write JSON result to this file. Default: stdout.")
    String outFile;

    @Option(names = {"-q", "--quiet"}, defaultValue = "false") boolean quiet;
    @Option(names = {"-v", "--verbose"}, defaultValue = "false") boolean verbose;

    @Override
    public Integer call() {
        try {
            Map<String, NamedQuery> queries = NamedQueryFile.read(file);
            NamedQuery query = queries.get(name);
            if (query == null) {
                throw new DataBridgeException(ErrorCodes.INVALID_ARGS,
                    "no query named '" + name + "' in " + file,
                    "Available: " + String.join(", ", queries.keySet()));
            }
            try (JdbcSource src = JdbcSource.open(conn.resolve())) {
                QueryResult result = src.executeNamed(query, params, limit, timeoutSec);
                QueryResultWriter.write(result, outFile, quiet);
            }
            return ErrorCodes.OK.exitCode();
        } catch (IOException e) {
            return CliErrors.report(new DataBridgeException(ErrorCodes.OUTPUT_WRITE_FAILED,
                "failed to write result: " + e.getMessage(), null, e), verbose);
        } catch (RuntimeException e) {
            return CliErrors.report(e, verbose);
        }
    }
}
