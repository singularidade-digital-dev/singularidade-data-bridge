package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSourceConnectIT {

    @Test
    void opens_and_closes_against_postgres() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            assertThat(src.type()).isEqualTo("jdbc");
            assertThat(src.driverWireName()).isEqualTo("postgresql");
            assertThat(src.listTables("atl")).contains("estadocivil", "cliente");
        }
    }
}
