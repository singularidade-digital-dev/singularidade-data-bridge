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
            long firstMs = (System.nanoTime() - t0) / 1_000_000;
            assertThat(first).containsExactly("id");

            // Subsequent calls — should be served from cache, sub-millisecond each
            long t1 = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                List<String> cached = src.primaryKey("atl", "cliente");
                assertThat(cached).containsExactly("id");
            }
            long cachedTotalMs = (System.nanoTime() - t1) / 1_000_000;

            // 100 cached calls should be << 1 single uncached call. Allow huge margin.
            assertThat(cachedTotalMs).isLessThan(firstMs);
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
