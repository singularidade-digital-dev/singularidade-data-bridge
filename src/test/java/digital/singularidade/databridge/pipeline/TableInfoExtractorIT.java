package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.TableInfo;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TableInfoExtractorIT {

    @Test
    void table_returns_basic_info_for_estadocivil() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            TableInfo info = new TableInfoExtractor().extract(src, "atl", "estadocivil");
            assertThat(info.type()).isEqualTo("TABLE");
            assertThat(info.comment()).isEqualTo("Cadastro de estado civil");
            assertThat(info.approximateRowCount()).isNotNull();
            assertThat(info.viewDefinition()).isNull();
        }
    }

    @Test
    void view_returns_view_definition() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            TableInfo info = new TableInfoExtractor().extract(src, "atl", "cliente_vw");
            assertThat(info.type()).isEqualTo("VIEW");
            assertThat(info.viewDefinition()).contains("SELECT").contains("nome");
        }
    }
}
