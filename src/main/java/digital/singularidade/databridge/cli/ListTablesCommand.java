package digital.singularidade.databridge.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.output.JsonWriter;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "list-tables", description = "List tables in a schema.")
public final class ListTablesCommand implements Callable<Integer> {

    @Option(names = "--jdbc-url", required = true) String jdbcUrl;
    @Option(names = "--schema") String schema;

    @Override
    public Integer call() {
        try (JdbcSource src = JdbcSource.open(jdbcUrl)) {
            List<String> tables = src.listTables(schema);
            ObjectMapper mapper = JsonWriter.newMapper();
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tables));
            return ErrorCodes.OK.exitCode();
        } catch (DataBridgeException e) {
            System.err.println("{\"error\":{\"code\":\"" + e.code().wireName() + "\",\"message\":\"" + e.getMessage() + "\"}}");
            return e.code().exitCode();
        } catch (Exception e) {
            System.err.println("{\"error\":{\"code\":\"UNSPECIFIED\",\"message\":\"" + e.getMessage() + "\"}}");
            return ErrorCodes.UNSPECIFIED.exitCode();
        }
    }
}
