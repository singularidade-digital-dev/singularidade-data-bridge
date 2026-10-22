package digital.singularidade.databridge.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RunQueryCommandIT {

    @Test
    void should_bind_bigint_and_date_parameters_on_postgres(@TempDir Path tmp) throws Exception {
        try (PgFixture fx = new PgFixture()) {
            Path q = Files.writeString(tmp.resolve("q.sql"), """
                -- name: cliente
                SELECT id, nome FROM atl.cliente WHERE id = :id
                -- name: nascidos
                SELECT count(*) AS n FROM atl.cliente WHERE datanascimento >= :desde AND nome <> ':nao'
                """);
            Path out = tmp.resolve("out.json");
            CommandLine cl = new CommandLine(new Main()).setErr(new PrintWriter(OutputStream.nullOutputStream()));
            assertThat(cl.execute("run-query", "--jdbc-url", fx.jdbcUrl(), "--file", q.toString(),
                "--name", "cliente", "--param", "id=1", "--out", out.toString(), "-q")).isZero();
            JsonNode r = new ObjectMapper().readTree(out.toFile());
            assertThat(r.get("rowCount").asInt()).isEqualTo(1);
            assertThat(cl.execute("run-query", "--jdbc-url", fx.jdbcUrl(), "--file", q.toString(),
                "--name", "nascidos", "--param", "desde=1900-01-01", "--out", out.toString(), "-q")).isZero();
            assertThat(new ObjectMapper().readTree(out.toFile()).get("rows").get(0).get("n").asLong()).isPositive();
        }
    }
}
