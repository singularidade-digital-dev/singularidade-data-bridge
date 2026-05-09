package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.ForeignKey;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForeignKeysExtractorIT {

    @Test
    void extracts_fk_with_1_deep_ref_columns() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {

            List<ForeignKey> fks = new ForeignKeysExtractor().extract(src, "atl", "cliente");
            assertThat(fks).hasSize(1);
            ForeignKey fk = fks.get(0);
            assertThat(fk.fkColumns()).containsExactly("fk_estadocivil_id");
            assertThat(fk.refSchema()).isEqualTo("atl");
            assertThat(fk.refTable()).isEqualTo("estadocivil");
            assertThat(fk.refColumns()).containsExactly("id");
            assertThat(fk.refTableColumns()).extracting(c -> c.name())
                .contains("id", "mnemonico", "nome", "descricao");
        }
    }
}
