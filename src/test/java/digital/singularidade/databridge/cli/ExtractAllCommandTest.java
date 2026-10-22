package digital.singularidade.databridge.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractAllCommandTest {

    @Test
    void parses_required_flags_with_safe_defaults() {
        ExtractAllCommand cmd = new ExtractAllCommand();
        new CommandLine(cmd).parseArgs(
            "--jdbc-url", "jdbc:postgresql://h/d",
            "--schema", "shop",
            "--out", "/tmp/snap"
        );
        assertThat(cmd.conn.jdbcUrl()).isEqualTo("jdbc:postgresql://h/d");
        assertThat(cmd.schema).isEqualTo("shop");
        assertThat(cmd.outDir.toString()).isEqualTo("/tmp/snap");
        assertThat(cmd.sampleRows).isZero();         // PII-safe default
        assertThat(cmd.tsv).isFalse();
        assertThat(cmd.skipCardinality).isFalse();
    }

    @Test
    void parses_optional_flags() {
        ExtractAllCommand cmd = new ExtractAllCommand();
        new CommandLine(cmd).parseArgs(
            "--jdbc-url", "u",
            "--schema", "s",
            "--out", "/tmp/x",
            "--sample-rows", "3",
            "--tsv",
            "--no-cardinality",
            "-q"
        );
        assertThat(cmd.sampleRows).isEqualTo(3);
        assertThat(cmd.tsv).isTrue();
        assertThat(cmd.skipCardinality).isTrue();
        assertThat(cmd.quiet).isTrue();
    }

    @Test
    void should_keep_ddl_on_when_include_ddl_is_given_explicitly() {
        ExtractAllCommand cmd = new ExtractAllCommand();
        new CommandLine(cmd).parseArgs("--jdbc-url", "u", "--out", "/tmp/x", "--include-ddl");
        assertThat(cmd.includeDdl).isTrue();
    }

    @Test
    void should_turn_ddl_off_only_with_no_include_ddl() {
        ExtractAllCommand off = new ExtractAllCommand();
        new CommandLine(off).parseArgs("--jdbc-url", "u", "--out", "/tmp/x", "--no-include-ddl");
        assertThat(off.includeDdl).isFalse();
        ExtractAllCommand byDefault = new ExtractAllCommand();
        new CommandLine(byDefault).parseArgs("--jdbc-url", "u", "--out", "/tmp/x");
        assertThat(byDefault.includeDdl).isTrue();
    }
}
