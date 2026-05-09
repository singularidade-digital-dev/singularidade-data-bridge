package digital.singularidade.databridge.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class VersionCommandTest {

    @Test
    void prints_name_and_version() {
        StringWriter out = new StringWriter();
        CommandLine cmd = new CommandLine(new Main()).setOut(new PrintWriter(out));
        int exit = cmd.execute("version");
        assertThat(exit).isZero();
        assertThat(out.toString()).contains("singularidade-data-bridge").contains("0.1.0");
    }
}
