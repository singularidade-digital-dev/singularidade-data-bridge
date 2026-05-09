package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Index;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndexesExtractorIT {

    @Test
    void extracts_secondary_indexes_with_method_btree_for_cliente() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {

            List<Index> idx = new IndexesExtractor().extract(src, "atl", "cliente");
            assertThat(idx).extracting(Index::name)
                .contains("idx_cliente_cpf", "uk_cliente_cpf");

            Index uk = idx.stream().filter(i -> i.name().equals("uk_cliente_cpf")).findFirst().orElseThrow();
            assertThat(uk.unique()).isTrue();
            assertThat(uk.method()).isEqualTo("btree");
            assertThat(uk.columns()).containsExactly("cpf");
        }
    }
}
