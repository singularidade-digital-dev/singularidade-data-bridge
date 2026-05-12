package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSourcePkCacheIT {

    @Test
    void primaryKey_cached_after_first_call_for_same_schema_table() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            // First call — full DB roundtrip
            long t0 = System.nanoTime();
            List<String> first = src.primaryKey("atl", "cliente");
            long firstNs = System.nanoTime() - t0;
            assertThat(first).containsExactly("id");

            // Subsequent calls — should be served from cache, sub-microsecond each
            int iterations = 100;
            long t1 = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                List<String> cached = src.primaryKey("atl", "cliente");
                assertThat(cached).containsExactly("id");
            }
            long cachedAvgNs = (System.nanoTime() - t1) / iterations;

            // Each cached call should be << 1 uncached call. Allow huge margin.
            assertThat(cachedAvgNs).isLessThan(firstNs);
        }
    }

    @Test
    void primaryKey_cache_keyed_by_schema_and_table() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            assertThat(src.primaryKey("atl", "cliente")).containsExactly("id");
            assertThat(src.primaryKey("atl", "estadocivil")).containsExactly("id");
            // Different tables → different cache entries → both correct
        }
    }
}
