package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.CheckConstraint;
import digital.singularidade.databridge.output.UniqueConstraint;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConstraintsExtractorsIT {

    @Test
    void extracts_unique_constraints_for_cliente() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            List<UniqueConstraint> ucs = new UniqueConstraintsExtractor().extract(src, "atl", "cliente");
            assertThat(ucs).extracting(UniqueConstraint::name).contains("uk_cliente_cpf");
        }
    }

    @Test
    void extracts_check_constraints_for_cliente() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            List<CheckConstraint> cs = new CheckConstraintsExtractor().extract(src, "atl", "cliente");
            assertThat(cs).extracting(CheckConstraint::name).contains("chk_cliente_sexo");
            CheckConstraint sexo = cs.stream().filter(c -> "chk_cliente_sexo".equals(c.name())).findFirst().orElseThrow();
            assertThat(sexo.definition()).contains("'M'", "'F'", "'O'");
        }
    }
}
