package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardinalityExtractorIT {

    @Test
    void counts_total_and_distinct_per_column_for_cliente() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            List<Column> cols = src.columns("atl", "cliente");
            Cardinality c = new CardinalityExtractor().extract(src, "atl", "cliente", cols);
            assertThat(c.totalRows()).isEqualTo(5);

            Cardinality.PerColumn id = c.perColumn().stream()
                .filter(p -> "id".equals(p.name())).findFirst().orElseThrow();
            assertThat(id.distinctCount()).isEqualTo(5);

            Cardinality.PerColumn fk = c.perColumn().stream()
                .filter(p -> "fk_estadocivil_id".equals(p.name())).findFirst().orElseThrow();
            assertThat(fk.distinctCount()).isEqualTo(3);
            assertThat(fk.nullCount()).isEqualTo(1);
        }
    }
}
