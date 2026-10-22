package digital.singularidade.databridge.query;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.source.jdbc.DriverHints;
import org.junit.jupiter.api.Test;

import static digital.singularidade.databridge.source.jdbc.DriverHints.FIREBIRD;
import static digital.singularidade.databridge.source.jdbc.DriverHints.MSSQL;
import static digital.singularidade.databridge.source.jdbc.DriverHints.PG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlGuardTest {

    private static void assertRejected(String sql, DriverHints driver, boolean writable, String message) {
        assertThatThrownBy(() -> SqlGuard.check(sql, driver, writable))
            .isInstanceOfSatisfying(DataBridgeException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_ARGS))
            .hasMessageContaining(message);
    }

    @Test
    void should_reject_a_second_statement_hidden_after_a_comment() {
        assertRejected("SELECT 1; /* x */ DELETE FROM t", PG, true, "multi-statement");
    }

    @Test
    void should_allow_semicolons_inside_literals_and_one_trailing_semicolon() {
        assertThat(SqlGuard.check("SELECT ';' FROM t;  ", PG, false)).isEqualTo("SELECT ';' FROM t");
        assertThat(SqlGuard.check("SELECT 1 FROM RDB$DATABASE", FIREBIRD, false)).isEqualTo("SELECT 1 FROM RDB$DATABASE");
    }

    @Test
    void should_keep_the_ddl_message() {
        assertRejected("DROP TABLE t", PG, true, "DDL is not allowed");
        assertRejected("RECREATE TABLE t (a INT)", FIREBIRD, true, "DDL is not allowed");
    }

    @Test
    void should_reject_procedural_and_session_statements_even_when_writable() {
        assertRejected("EXECUTE BLOCK AS BEGIN EXECUTE STATEMENT 'CREATE TABLE X (A INT)'; END", FIREBIRD, true, "not allowed");
        assertRejected("CALL p()", PG, true, "not allowed");
        assertRejected("DO $$ BEGIN END $$", PG, true, "not allowed");
        assertRejected("SET GENERATOR G TO 0", FIREBIRD, true, "not allowed");
        assertRejected("EXEC sp_who", MSSQL, true, "not allowed");
    }

    @Test
    void should_keep_the_readonly_message_for_dml() {
        assertRejected("INSERT INTO t VALUES (1)", PG, false, "--writable");
    }

    @Test
    void should_reject_data_modifying_ctes_in_readonly_mode() {
        assertRejected("WITH x AS (SELECT 1 AS a) DELETE FROM t", MSSQL, false, "--writable");
        assertRejected("WITH x AS (SELECT 1) UPDATE t SET a = 1", MSSQL, false, "--writable");
    }

    @Test
    void should_allow_dml_when_writable() {
        assertThat(SqlGuard.check("UPDATE OR INSERT INTO t (a) VALUES (1) MATCHING (a)", FIREBIRD, true)).isNotBlank();
        assertThat(SqlGuard.check("DELETE FROM t WHERE a = 1", PG, true)).isNotBlank();
    }

    @Test
    void should_reject_generator_increments_on_firebird_in_readonly_mode() {
        assertRejected("SELECT GEN_ID(GEN_X, 1) FROM RDB$DATABASE", FIREBIRD, false, "GEN_ID");
        assertRejected("SELECT GEN_ID(\"Gen\", -1) FROM RDB$DATABASE", FIREBIRD, false, "GEN_ID");
        assertRejected("SELECT NEXT VALUE FOR GEN_X FROM RDB$DATABASE", FIREBIRD, false, "NEXT VALUE FOR");
        assertThat(SqlGuard.check("SELECT GEN_ID(GEN_X, 0) FROM RDB$DATABASE", FIREBIRD, false)).isNotBlank();
    }

    @Test
    void should_not_flag_keywords_inside_literals_or_quoted_identifiers() {
        assertThat(SqlGuard.check("SELECT 'DELETE FROM t; DROP' AS \"UPDATE\" FROM t", PG, false)).isNotBlank();
        assertThat(SqlGuard.check("SELECT * FROM t -- DELETE\nWHERE a = 1", PG, false)).isNotBlank();
    }

    @Test
    void should_accept_parenthesized_selects_and_require_sql() {
        assertThat(SqlGuard.check("(SELECT 1) UNION (SELECT 2)", PG, false)).isNotBlank();
        assertRejected("   ", PG, false, "sql is required");
    }
}
