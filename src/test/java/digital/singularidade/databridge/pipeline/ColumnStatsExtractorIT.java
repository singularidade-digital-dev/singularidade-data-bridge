package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.ColumnStats;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnStatsExtractorIT {

    @Test
    void extracts_pg_stats_for_estadocivil() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            List<ColumnStats> stats = new ColumnStatsExtractor().extract(src, "atl", "estadocivil");
            // After ANALYZE in fixture, pg_stats has rows; assert per-column entries exist
            assertThat(stats).extracting(ColumnStats::name).contains("mnemonico", "nome");
            ColumnStats mnem = stats.stream().filter(s -> "mnemonico".equals(s.name())).findFirst().orElseThrow();
            assertThat(mnem.nullFraction()).isNotNull();
        }
    }
}
