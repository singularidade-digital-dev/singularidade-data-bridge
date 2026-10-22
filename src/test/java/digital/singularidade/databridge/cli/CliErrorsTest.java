package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CliErrorsTest {

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private PrintStream original;

    @BeforeEach
    void captureStderr() {
        original = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStderr() {
        System.setErr(original);
    }

    @Test
    void should_print_escaped_redacted_json_and_return_the_exit_code() {
        int exit = CliErrors.report(new DataBridgeException(ErrorCodes.TABLE_NOT_FOUND,
            "table \"X\" not found in jdbc:postgresql://h/d?password=s3cret", "list the tables"), false);
        String out = err.toString(StandardCharsets.UTF_8);
        assertThat(exit).isEqualTo(11);
        assertThat(out).contains("\"code\":\"TABLE_NOT_FOUND\"")
            .contains("table \\\"X\\\" not found")
            .contains("password=***")
            .doesNotContain("s3cret")
            .contains("\"hint\":\"list the tables\"");
    }

    @Test
    void should_report_unexpected_errors_as_unspecified() {
        int exit = CliErrors.report(new IllegalStateException("boom"), false);
        assertThat(exit).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("\"code\":\"UNSPECIFIED\"").contains("boom");
    }
}
