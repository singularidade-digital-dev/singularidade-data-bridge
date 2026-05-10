package digital.singularidade.databridge.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractCommandTest {

    @Test
    void parses_required_flags() {
        ExtractCommand cmd = new ExtractCommand();
        new CommandLine(cmd).parseArgs(
            "--jdbc-url", "jdbc:postgresql://h/d",
            "--schema", "atl",
            "--table", "cliente",
            "--out", "/tmp/c"
        );
        assertThat(cmd.jdbcUrl).isEqualTo("jdbc:postgresql://h/d");
        assertThat(cmd.schema).isEqualTo("atl");
        assertThat(cmd.table).isEqualTo("cliente");
        assertThat(cmd.outDir.toString()).isEqualTo("/tmp/c");
        assertThat(cmd.sampleRows).isZero();   // sample is opt-in (PII safety)
        assertThat(cmd.tsv).isFalse();
        assertThat(cmd.skipCardinality).isFalse();
    }

    @Test
    void parses_optional_flags() {
        ExtractCommand cmd = new ExtractCommand();
        new CommandLine(cmd).parseArgs(
            "--jdbc-url", "u",
            "--table", "t",
            "--out", "/tmp/x",
            "--sample-rows", "10",
            "--tsv",
            "--no-cardinality"
        );
        assertThat(cmd.sampleRows).isEqualTo(10);
        assertThat(cmd.tsv).isTrue();
        assertThat(cmd.skipCardinality).isTrue();
        assertThat(cmd.schema).isNull();
    }
}
