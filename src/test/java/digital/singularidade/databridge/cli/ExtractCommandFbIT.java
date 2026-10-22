package digital.singularidade.databridge.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.support.FbFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractCommandFbIT {

    private static FbFixture fx;
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private PrintStream originalErr;

    @BeforeAll
    static void start() { fx = new FbFixture(); }

    @AfterAll
    static void stop() { fx.close(); }

    @BeforeEach
    void captureStderr() {
        originalErr = System.err;
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStderr() { System.setErr(originalErr); }

    private int extract(Path out, String... tablesAndFlags) {
        String[] base = {"extract", "--jdbc-url", fx.jdbcUrl(), "--out", out.toString(),
            "--cardinality-mode", "skip", "-q"};
        String[] args = new String[base.length + tablesAndFlags.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(tablesAndFlags, 0, args, base.length, tablesAndFlags.length);
        return new CommandLine(new Main()).setErr(new PrintWriter(OutputStream.nullOutputStream())).execute(args);
    }

    @Test
    void should_extract_several_tables_in_one_run(@TempDir Path tmp) {
        assertThat(extract(tmp, "--table", "PRITENSESQ", "--table", "TBPAI")).isZero();
        assertThat(tmp.resolve("PRITENSESQ").resolve("metadata.json")).exists();
        assertThat(tmp.resolve("TBPAI").resolve("ddl.sql")).exists();
    }

    @Test
    void should_read_tables_from_a_file(@TempDir Path tmp) throws Exception {
        Path list = Files.writeString(tmp.resolve("tables.txt"), "PRITENSESQ\n# grupo\n\nTBGRUPO\n");
        assertThat(extract(tmp.resolve("out"), "--tables-file", list.toString())).isZero();
        assertThat(tmp.resolve("out").resolve("PRITENSESQ")).isDirectory();
        assertThat(tmp.resolve("out").resolve("TBGRUPO")).isDirectory();
    }

    @Test
    void should_find_unquoted_names_given_in_lower_case(@TempDir Path tmp) throws Exception {
        assertThat(extract(tmp, "--table", "pritensesq")).isZero();
        JsonNode m = new ObjectMapper().readTree(tmp.resolve("PRITENSESQ").resolve("metadata.json").toFile());
        assertThat(m.get("columns").size()).isEqualTo(16);
        assertThat(m.get("source").get("table").asText()).isEqualTo("PRITENSESQ");
    }

    @Test
    void should_match_quoted_mixed_case_names_exactly(@TempDir Path tmp) {
        assertThat(extract(tmp, "--table", "TbMista")).isZero();
        assertThat(tmp.resolve("TbMista").resolve("metadata.json")).exists();
    }

    @Test
    void should_not_treat_underscore_as_a_wildcard(@TempDir Path tmp) {
        assertThat(extract(tmp, "--table", "TB_X")).isZero();
        assertThat(tmp.resolve("TB_X")).isDirectory();
        assertThat(tmp.resolve("TBAX")).doesNotExist();
        assertThat(extract(tmp.resolve("other"), "--table", "TBA_")).isEqualTo(11);
    }

    @Test
    void should_fail_with_exit_11_listing_every_missing_table_before_writing(@TempDir Path tmp) {
        int exit = extract(tmp.resolve("out"), "--table", "PRITENSESQ", "--table", "NAOEXISTE", "--table", "OUTRA");
        assertThat(exit).isEqualTo(11);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("TABLE_NOT_FOUND").contains("NAOEXISTE, OUTRA");
        assertThat(tmp.resolve("out")).doesNotExist();
    }
}
