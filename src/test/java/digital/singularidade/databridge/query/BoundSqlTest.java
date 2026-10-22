package digital.singularidade.databridge.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoundSqlTest {

    @Test
    void should_turn_named_parameters_into_placeholders_in_order() {
        BoundSql b = BoundSql.of("SELECT * FROM t WHERE a = :a AND b = :b_2 OR c = :a");
        assertThat(b.jdbcSql()).isEqualTo("SELECT * FROM t WHERE a = ? AND b = ? OR c = ?");
        assertThat(b.parameterNames()).containsExactly("a", "b_2", "a");
    }

    @Test
    void should_skip_literals_comments_and_postgres_casts() {
        BoundSql b = BoundSql.of("SELECT ':x', a::text /* :y */ FROM t -- :z\nWHERE a = :a");
        assertThat(b.jdbcSql()).isEqualTo("SELECT ':x', a::text /* :y */ FROM t -- :z\nWHERE a = ?");
        assertThat(b.parameterNames()).containsExactly("a");
    }

    @Test
    void should_leave_sql_without_parameters_untouched() {
        assertThat(BoundSql.of("SELECT 1 FROM RDB$DATABASE").jdbcSql()).isEqualTo("SELECT 1 FROM RDB$DATABASE");
        assertThat(BoundSql.of("SELECT 1 FROM RDB$DATABASE").parameterNames()).isEmpty();
    }
}
