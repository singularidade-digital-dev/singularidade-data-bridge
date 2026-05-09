package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaExtractorIT {

    @Test
    void extracts_columns_with_pk_and_identity_for_estadocivil() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {

            List<Column> cols = new SchemaExtractor().extract(src, "atl", "estadocivil");
            assertThat(cols).extracting(Column::name).containsExactly(
                "id", "mnemonico", "nome", "descricao");

            Column id = cols.get(0);
            assertThat(id.primaryKey()).isTrue();
            assertThat(id.nullable()).isFalse();
            assertThat(id.sqlType()).isEqualTo("bigint");
            assertThat(id.generated().isIdentity()).isTrue();

            Column mnem = cols.get(1);
            assertThat(mnem.sqlType()).isEqualTo("varchar(20)");
            assertThat(mnem.nullable()).isFalse();
            assertThat(mnem.comment()).isEqualTo("Codigo curto");

            Column desc = cols.get(3);
            assertThat(desc.nullable()).isTrue();
        }
    }
}
