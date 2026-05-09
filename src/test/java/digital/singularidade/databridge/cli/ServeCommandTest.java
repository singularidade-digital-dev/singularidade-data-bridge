package digital.singularidade.databridge.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class ServeCommandTest {

    @Test
    void parses_defaults() {
        ServeCommand cmd = new ServeCommand();
        new CommandLine(cmd).parseArgs();
        assertThat(cmd.port).isEqualTo(8765);
        assertThat(cmd.maxPool).isEqualTo(5);
        assertThat(cmd.idleTimeout.toMinutes()).isEqualTo(10);
    }

    @Test
    void parses_overrides() {
        ServeCommand cmd = new ServeCommand();
        new CommandLine(cmd).parseArgs(
            "--port", "9000",
            "--max-pool", "10",
            "--idle-timeout", "5m"
        );
        assertThat(cmd.port).isEqualTo(9000);
        assertThat(cmd.maxPool).isEqualTo(10);
        assertThat(cmd.idleTimeout.toMinutes()).isEqualTo(5);
    }
}
