package digital.singularidade.databridge.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.support.FbFixture;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ListGeneratorsCommandIT {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private PrintStream originalOut;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() { System.setOut(originalOut); }

    private static int cli(String... args) {
        return new CommandLine(new Main()).setErr(new PrintWriter(OutputStream.nullOutputStream())).execute(args);
    }

    @Test
    void should_list_user_generators_by_name_without_identity_generators() throws Exception {
        try (FbFixture fx = new FbFixture()) {
            assertThat(cli("list-generators", "--jdbc-url", fx.jdbcUrl())).isZero();
            String[] names = new ObjectMapper().readValue(out.toString(StandardCharsets.UTF_8), String[].class);
            assertThat(names).containsExactly("GEN_IDITENSESQ", "GEN_PRITENSESQ_ID");
        }
    }

    @Test
    void should_be_unsupported_on_other_drivers() {
        try (PgFixture fx = new PgFixture()) {
            assertThat(cli("list-generators", "--jdbc-url", fx.jdbcUrl())).isEqualTo(64);
        }
    }
}
