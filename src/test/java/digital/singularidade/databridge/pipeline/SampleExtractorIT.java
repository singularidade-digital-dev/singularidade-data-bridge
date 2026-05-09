package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Sample;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SampleExtractorIT {

    @Test
    void samples_5_rows_with_blob_normalized() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            Sample s = new SampleExtractor().extract(src, "atl", "cliente", 5);
            assertThat(s.rowCount()).isEqualTo(5);
            assertThat(s.rows()).hasSize(5);
            Map<String, Object> first = s.rows().get(0);
            assertThat(first).containsKeys("id", "cpf", "nome", "foto");
            assertThat(first.get("foto")).isNull();   // foto is null in fixture (no INSERT writes BYTEA)
        }
    }
}
