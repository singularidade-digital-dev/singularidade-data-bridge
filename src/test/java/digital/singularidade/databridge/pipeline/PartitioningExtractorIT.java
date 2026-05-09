package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Partitioning;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PartitioningExtractorIT {

    @Test
    void cliente_is_not_partitioned() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            Partitioning p = new PartitioningExtractor().extract(src, "atl", "cliente");
            assertThat(p.isPartitioned()).isFalse();
            assertThat(p.partitionKey()).isEmpty();
            assertThat(p.children()).isEmpty();
        }
    }
}
