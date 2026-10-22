package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(cmd.conn.jdbcUrl()).isEqualTo("jdbc:postgresql://h/d");
        assertThat(cmd.schema).isEqualTo("atl");
        assertThat(cmd.tables).containsExactly("cliente");
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

    @Test
    void parses_repeated_tables() {
        ExtractCommand cmd = new ExtractCommand();
        new CommandLine(cmd).parseArgs("--jdbc-url", "u", "--table", "A", "--table", "B", "--out", "/tmp/x");
        assertThat(cmd.tables).containsExactly("A", "B");
    }

    @Test
    void should_merge_flags_and_file_trimming_blanks_comments_and_duplicates(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("tables.txt"), "# lista\n\n  C \nA\n");
        assertThat(ExtractCommand.requestedTables(List.of("A", " b ", "A"), file)).containsExactly("A", "b", "C");
    }

    @Test
    void should_require_at_least_one_table() {
        assertThatThrownBy(() -> ExtractCommand.requestedTables(List.of(), null))
            .isInstanceOfSatisfying(DataBridgeException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_ARGS));
    }
}
