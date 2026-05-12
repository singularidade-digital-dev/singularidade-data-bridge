package digital.singularidade.databridge.pipeline.ddl;

import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.FbFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FirebirdDdlBuilderIT {

    @Test
    void emits_create_table_with_columns_pk_and_constraints() {
        try (FbFixture fx = new FbFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            DdlScript script = new FirebirdDdlBuilder()
                .build(src, null, "PRODUCTS",
                    "jdbc:firebirdsql://[redacted-host]/test.fdb", false).orElseThrow();
            String text = script.toFileText();
            assertThat(text).contains("CREATE TABLE \"PRODUCTS\"");
            assertThat(text).contains("\"ID\"");
            assertThat(text).contains("VARCHAR");
            assertThat(text).contains("singularidade-data-bridge");
        }
    }
}
