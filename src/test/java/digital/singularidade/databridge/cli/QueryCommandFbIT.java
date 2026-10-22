package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.FbFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.OutputStream;
import java.io.PrintWriter;

import static org.assertj.core.api.Assertions.assertThat;

class QueryCommandFbIT {

    private static FbFixture fx;

    @BeforeAll
    static void start() { fx = new FbFixture(); }

    @AfterAll
    static void stop() { fx.close(); }

    private static int query(String... extra) {
        String[] base = {"query", "--jdbc-url", fx.jdbcUrl()};
        String[] args = new String[base.length + extra.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(extra, 0, args, base.length, extra.length);
        return new CommandLine(new Main()).setErr(new PrintWriter(OutputStream.nullOutputStream())).execute(args);
    }

    private static long generatorValue() {
        try (JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            Object v = src.executeQuery("SELECT GEN_ID(GEN_IDITENSESQ, 0) AS G FROM RDB$DATABASE", 1, 30, false)
                .rows().get(0).get("G");
            return ((Number) v).longValue();
        }
    }

    private static boolean tableExists(String name) {
        try (JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            return src.resolveTable(null, name).isPresent();
        }
    }

    @Test
    void should_not_advance_a_generator_from_a_readonly_query() {
        long before = generatorValue();
        assertThat(query("--sql", "SELECT GEN_ID(GEN_IDITENSESQ, 1) FROM RDB$DATABASE")).isEqualTo(2);
        assertThat(query("--sql", "SELECT NEXT VALUE FOR GEN_IDITENSESQ FROM RDB$DATABASE")).isEqualTo(2);
        assertThat(generatorValue()).isEqualTo(before);
    }

    @Test
    void should_reject_ddl_wrapped_in_execute_block_even_when_writable() {
        assertThat(query("--writable", "--sql",
            "EXECUTE BLOCK AS BEGIN EXECUTE STATEMENT 'CREATE TABLE TB_VIA_QUERY (X INTEGER)'; END")).isEqualTo(2);
        assertThat(tableExists("TB_VIA_QUERY")).isFalse();
    }

    @Test
    void should_still_block_writes_inside_a_readonly_transaction() {
        assertThat(query("--sql", "SELECT X FROM SP_GRAVA")).isEqualTo(13);
    }
}
