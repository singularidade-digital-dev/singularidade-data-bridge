package digital.singularidade.databridge.server;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionPoolManagerTest {

    @Test
    void normalizes_keys_by_stripping_ephemeral_params() {
        ConnectionPoolManager m = new ConnectionPoolManager(5, Duration.ofMinutes(10));
        String url1 = "jdbc:postgresql://h/d?user=u&password=p&connectTimeout=5";
        String url2 = "jdbc:postgresql://h/d?user=u&password=p&connectTimeout=99";
        assertThat(m.normalizeKey(url1)).isEqualTo(m.normalizeKey(url2));
    }

    @Test
    void different_credentials_give_different_keys() {
        ConnectionPoolManager m = new ConnectionPoolManager(5, Duration.ofMinutes(10));
        String url1 = "jdbc:postgresql://h/d?user=u1&password=p1";
        String url2 = "jdbc:postgresql://h/d?user=u2&password=p2";
        assertThat(m.normalizeKey(url1)).isNotEqualTo(m.normalizeKey(url2));
    }

    @Test
    void getConnection_against_h2_and_close_releases() throws Exception {
        ConnectionPoolManager m = new ConnectionPoolManager(2, Duration.ofMinutes(10));
        try (Connection c = m.getConnection("jdbc:h2:mem:test_pool;DB_CLOSE_DELAY=-1")) {
            assertThat(c.isClosed()).isFalse();
        }
        m.shutdown();
    }
}
