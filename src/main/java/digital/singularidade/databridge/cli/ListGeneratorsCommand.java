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

@Command(name = "list-generators",
    description = "List the database's generators (sequences) by name, sorted; Firebird only. "
                + "No current values; identity-column generators are left out.")
public final class ListGeneratorsCommand implements Callable<Integer> {

    @Mixin ConnectionOptions conn;
    @Option(names = {"-v", "--verbose"}, defaultValue = "false") boolean verbose;

    @Override
    public Integer call() {
        try (JdbcSource src = JdbcSource.open(conn.resolve())) {
            List<String> generators = src.listGenerators();
            ObjectMapper mapper = JsonWriter.newMapper();
            System.out.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(generators));
            System.out.write('\n');
            System.out.flush();
            return ErrorCodes.OK.exitCode();
        } catch (Exception e) {
            return CliErrors.report(e, verbose);
        }
    }
}
