package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrimaryKeyExtractorIT {

    @Test
    void extracts_pk_for_cliente() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            assertThat(new PrimaryKeyExtractor().extract(src, "atl", "cliente"))
                .containsExactly("id");
        }
    }
}
