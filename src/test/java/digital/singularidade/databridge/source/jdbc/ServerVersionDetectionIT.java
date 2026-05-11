package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerVersionDetectionIT {

    @Test
    void detects_postgres_version_from_server_version_num() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            ServerVersion v = src.serverVersion();
            assertThat(v.driver()).isEqualTo("postgresql");
            assertThat(v.major()).isGreaterThanOrEqualTo(11);
            assertThat(v.banner()).isNotBlank();
            assertThat(v.isAtLeast(11, 0)).isTrue();
        }
    }

    @Test
    void column_exists_returns_true_for_pg_attribute_attname() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            assertThat(src.columnExists("pg_attribute", "attname")).isTrue();
            assertThat(src.columnExists("pg_attribute", "definitely_not_a_column_42")).isFalse();
        }
    }
}
