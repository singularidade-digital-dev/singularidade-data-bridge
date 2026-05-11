package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.output.QueryResult;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcSourceQueryIT {

    @Test
    void select_returns_rows_with_columns_and_metadata() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            QueryResult r = src.executeQuery(
                "SELECT id, mnemonico, nome FROM atl.estadocivil ORDER BY id",
                100, 30, false);

            assertThat(r.kind()).isEqualTo("query");
            assertThat(r.rowCount()).isEqualTo(4);
            assertThat(r.truncated()).isFalse();
            assertThat(r.columns()).extracting(QueryResult.ColumnInfo::name)
                .containsExactly("id", "mnemonico", "nome");
            assertThat(r.rows()).hasSize(4);
            assertThat(r.rows().get(0)).containsEntry("mnemonico", "S");
            assertThat(r.executionTimeMs()).isGreaterThanOrEqualTo(0);
            assertThat(r.updateCount()).isNull();
        }
    }

    @Test
    void truncated_true_when_result_exceeds_limit() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            QueryResult r = src.executeQuery(
                "SELECT id FROM atl.cliente ORDER BY id", 2, 30, false);
            assertThat(r.rowCount()).isEqualTo(2);
            assertThat(r.truncated()).isTrue();
        }
    }

    @Test
    void multi_statement_is_rejected() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            assertThatThrownBy(() -> src.executeQuery(
                "SELECT 1; DROP TABLE atl.cliente", 100, 30, true))
                .isInstanceOf(DataBridgeException.class)
                .hasMessageContaining("multi-statement");
        }
    }

    @Test
    void ddl_is_rejected_even_with_writable() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            assertThatThrownBy(() -> src.executeQuery(
                "DROP TABLE atl.cliente", 100, 30, true))
                .isInstanceOf(DataBridgeException.class)
                .hasMessageContaining("DDL");
        }
    }

    @Test
    void non_select_is_rejected_in_readonly_mode() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            assertThatThrownBy(() -> src.executeQuery(
                "INSERT INTO atl.estadocivil (mnemonico, nome) VALUES ('X','Test')",
                100, 30, false))
                .isInstanceOf(DataBridgeException.class)
                .hasMessageContaining("--writable");
        }
    }

    @Test
    void writable_mode_permits_insert_and_returns_update_count() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            QueryResult r = src.executeQuery(
                "INSERT INTO atl.estadocivil (mnemonico, nome) VALUES ('X','Test')",
                100, 30, true);
            assertThat(r.kind()).isEqualTo("update");
            assertThat(r.updateCount()).isEqualTo(1L);
            assertThat(r.rows()).isNull();
        }
    }

    @Test
    void with_clause_works_in_readonly_mode() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            QueryResult r = src.executeQuery(
                "WITH ec AS (SELECT * FROM atl.estadocivil) SELECT count(*) AS n FROM ec",
                10, 30, false);
            assertThat(r.rows()).hasSize(1);
            assertThat(((Number) r.rows().get(0).get("n")).longValue()).isEqualTo(4L);
        }
    }

    @Test
    void empty_or_null_sql_is_rejected() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            assertThatThrownBy(() -> src.executeQuery("", 100, 30, false))
                .isInstanceOf(DataBridgeException.class)
                .hasMessageContaining("sql is required");
            assertThatThrownBy(() -> src.executeQuery(null, 100, 30, false))
                .isInstanceOf(DataBridgeException.class)
                .hasMessageContaining("sql is required");
        }
    }

    @Test
    void zero_or_negative_limit_is_rejected() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            assertThatThrownBy(() -> src.executeQuery("SELECT 1", 0, 30, false))
                .isInstanceOf(DataBridgeException.class)
                .hasMessageContaining("limit must be > 0");
        }

        // Sanity: ensure the exception above carries INVALID_ARGS code
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            DataBridgeException e = catch_(() -> src.executeQuery("SELECT 1", -5, 30, false));
            assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_ARGS);
        }
    }

    private static DataBridgeException catch_(Runnable r) {
        try { r.run(); return null; }
        catch (DataBridgeException e) { return e; }
    }
}
