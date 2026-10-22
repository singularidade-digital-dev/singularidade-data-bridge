package digital.singularidade.databridge.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.output.JsonWriter;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "list-tables", description = "List tables in a schema.")
public final class ListTablesCommand implements Callable<Integer> {

    @Mixin ConnectionOptions conn;
    @Option(names = "--schema") String schema;
    @Option(names = {"-v", "--verbose"}, defaultValue = "false") boolean verbose;

    @Override
    public Integer call() {
        try (JdbcSource src = JdbcSource.open(conn.resolve())) {
            src.requireSchema(schema);
            List<String> tables = src.listTables(schema);
            ObjectMapper mapper = JsonWriter.newMapper();
            System.out.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(tables));
            System.out.write('\n');
            System.out.flush();
            return ErrorCodes.OK.exitCode();
        } catch (Exception e) {
            return CliErrors.report(e, verbose);
        }
    }
}
