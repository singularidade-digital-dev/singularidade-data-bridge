package digital.singularidade.databridge.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class ListTablesCommandTest {

    @Test
    void parses_required_jdbc_url_and_optional_schema() {
        ListTablesCommand cmd = new ListTablesCommand();
        new CommandLine(cmd).parseArgs("--jdbc-url", "jdbc:postgresql://h/d", "--schema", "atl");
        assertThat(cmd.jdbcUrl).isEqualTo("jdbc:postgresql://h/d");
        assertThat(cmd.schema).isEqualTo("atl");
    }
}
