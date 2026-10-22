package digital.singularidade.databridge.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.output.JsonWriter;
import digital.singularidade.databridge.output.QueryResult;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "query",
    description = "Execute one SQL statement (SELECT by default; --writable opts in to DML). "
                + "DDL is never permitted. Returns JSON with column metadata + rows.")
public final class QueryCommand implements Callable<Integer> {

    @Mixin ConnectionOptions conn;

    @Option(names = "--sql", required = true,
            description = "The SQL to execute. Must be a single statement.")
    String sql;

    @Option(names = "--limit", defaultValue = "100",
            description = "Max rows to return. The query also gets setMaxRows(limit+1) so "
                        + "the result is marked truncated=true if more rows exist. Default 100.")
    int limit;

    @Option(names = "--timeout-sec", defaultValue = "30",
            description = "JDBC query timeout in seconds. 0 = no timeout. Default 30.")
    int timeoutSec;

    @Option(names = "--writable", defaultValue = "false",
            description = "Permit INSERT/UPDATE/DELETE. DDL (DROP/CREATE/ALTER/TRUNCATE/GRANT/REVOKE) "
                        + "is rejected even with this flag.")
    boolean writable;

    @Option(names = "--out",
            description = "Write JSON result to this file. Default: stdout. Pass '-' for stdout explicitly.")
    String outFile;

    @Option(names = {"-q", "--quiet"}, defaultValue = "false") boolean quiet;
    @Option(names = {"-v", "--verbose"}, defaultValue = "false") boolean verbose;

    @Override
    public Integer call() {
        try (JdbcSource src = JdbcSource.open(conn.resolve())) {
            QueryResult result = src.executeQuery(sql, limit, timeoutSec, writable);
            ObjectMapper mapper = JsonWriter.newMapper();
            byte[] body = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
            writeBody(body);
            return ErrorCodes.OK.exitCode();
        } catch (IOException e) {
            return CliErrors.report(new DataBridgeException(ErrorCodes.OUTPUT_WRITE_FAILED,
                "failed to write result: " + e.getMessage(), null, e), verbose);
        } catch (RuntimeException e) {
            return CliErrors.report(e, verbose);
        }
    }

    private void writeBody(byte[] body) throws IOException {
        if (outFile == null || outFile.equals("-")) {
            System.out.write(body);
            System.out.println();
        } else {
            Path target = Path.of(outFile);
            Path dir = target.getParent();
            if (dir != null) Files.createDirectories(dir);
            Files.write(target, body);
            if (!quiet) System.err.println("wrote " + target.toAbsolutePath());
        }
    }


}
