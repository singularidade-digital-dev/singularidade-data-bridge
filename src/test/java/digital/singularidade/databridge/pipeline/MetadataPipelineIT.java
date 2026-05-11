package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Metadata;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataPipelineIT {

    @Test
    void runs_full_pipeline_for_cliente() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {

            Metadata m = new MetadataPipeline().run(src, fx.jdbcUrl(), "atl", "cliente", 5);

            assertThat(m.version()).isEqualTo("1.0");
            assertThat(m.source().table()).isEqualTo("cliente");
            assertThat(m.source().url()).contains("password=***");
            assertThat(m.tableInfo().type()).isEqualTo("TABLE");
            assertThat(m.columns()).extracting(c -> c.name()).contains("id", "cpf", "nome");
            assertThat(m.primaryKey()).containsExactly("id");
            assertThat(m.foreignKeys()).hasSize(1);
            assertThat(m.indexes()).extracting(i -> i.name()).contains("idx_cliente_cpf");
            assertThat(m.uniqueConstraints()).extracting(u -> u.name()).contains("uk_cliente_cpf");
            assertThat(m.checkConstraints()).extracting(c -> c.name()).contains("chk_cliente_sexo");
            assertThat(m.sample().rowCount()).isEqualTo(5);
            assertThat(m.cardinality().totalRows()).isEqualTo(5);
            assertThat(m.partitioning().isPartitioned()).isFalse();
            // Default cardinality mode is EXACT, but BLOB columns are auto-skipped — surfaced as a warning.
            assertThat(m.warnings())
                .anyMatch(w -> w.contains("foto") && w.contains("bytea"));
        }
    }
}
