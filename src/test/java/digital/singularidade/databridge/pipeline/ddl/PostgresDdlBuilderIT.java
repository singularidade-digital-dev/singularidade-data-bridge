package digital.singularidade.databridge.pipeline.ddl;

import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresDdlBuilderIT {

    @Test
    void emits_create_table_with_pk_check_indexes_fk_and_comments() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            Optional<DdlScript> result = new PostgresDdlBuilder()
                .build(src, "atl", "cliente", "jdbc:postgresql://[redacted-host]/test", false);
            assertThat(result).isPresent();
            String text = result.get().toFileText();
            assertThat(text).contains("CREATE TABLE");
            assertThat(text).contains("\"id\"");
            assertThat(text).contains("CONSTRAINT");
            assertThat(text).contains("PRIMARY KEY");                   // PK inline
            assertThat(text).contains("ALTER TABLE");                   // CHECK or FK
            assertThat(text).contains("CREATE INDEX");                  // idx_cliente_cpf or uk_cliente_cpf (depending on whether UC is named)
            assertThat(text).contains("singularidade-data-bridge");     // header
        }
    }

    @Test
    void omits_triggers_when_not_requested() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            DdlScript script = new PostgresDdlBuilder()
                .build(src, "atl", "cliente", "redacted-url", false).orElseThrow();
            assertThat(script.triggers()).isEmpty();
            assertThat(script.toFileText()).contains("triggers (use --include-triggers)");
        }
    }

    @Test
    void emits_create_view_for_view_objects() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            DdlScript script = new PostgresDdlBuilder()
                .build(src, "atl", "cliente_vw", "url", false).orElseThrow();
            assertThat(script.toFileText()).contains("CREATE OR REPLACE VIEW");
            assertThat(script.toFileText()).contains("SELECT");
        }
    }
}
