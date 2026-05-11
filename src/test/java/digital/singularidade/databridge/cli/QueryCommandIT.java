package digital.singularidade.databridge.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QueryCommandIT {

    @Test
    void selects_via_cli_writes_json_with_column_meta(@TempDir Path tmp) throws Exception {
        try (PgFixture fx = new PgFixture()) {
            Path out = tmp.resolve("result.json");
            int exit = new CommandLine(new Main())
                .setErr(new PrintWriter(OutputStream.nullOutputStream()))
                .execute(
                    "query",
                    "--jdbc-url", fx.jdbcUrl(),
                    "--sql", "SELECT id, mnemonico, nome FROM atl.estadocivil ORDER BY id LIMIT 3",
                    "--out", out.toString()
                );
            assertThat(exit).isZero();
            assertThat(out).exists();

            ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
            JsonNode tree = m.readTree(Files.readString(out));

            assertThat(tree.get("kind").asText()).isEqualTo("query");
            assertThat(tree.get("rowCount").asInt()).isEqualTo(3);
            assertThat(tree.get("truncated").asBoolean()).isFalse();
            assertThat(tree.get("columns").size()).isEqualTo(3);
            assertThat(tree.get("rows").size()).isEqualTo(3);
            assertThat(tree.get("rows").get(0).get("mnemonico").asText()).isEqualTo("S");
            assertThat(tree.get("executionTimeMs").asInt()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void rejected_ddl_returns_invalid_args_exit_code(@TempDir Path tmp) throws Exception {
        try (PgFixture fx = new PgFixture()) {
            int exit = new CommandLine(new Main())
                .setErr(new PrintWriter(OutputStream.nullOutputStream()))
                .execute(
                    "query",
                    "--jdbc-url", fx.jdbcUrl(),
                    "--sql", "DROP TABLE atl.cliente",
                    "--writable",
                    "--out", tmp.resolve("never.json").toString()
                );
            assertThat(exit).isEqualTo(2);   // INVALID_ARGS
            assertThat(tmp.resolve("never.json")).doesNotExist();
        }
    }

    @Test
    void writable_insert_returns_update_count(@TempDir Path tmp) throws Exception {
        try (PgFixture fx = new PgFixture()) {
            Path out = tmp.resolve("update.json");
            int exit = new CommandLine(new Main())
                .setErr(new PrintWriter(OutputStream.nullOutputStream()))
                .execute(
                    "query",
                    "--jdbc-url", fx.jdbcUrl(),
                    "--sql", "INSERT INTO atl.estadocivil (mnemonico, nome) VALUES ('Z','Zeta')",
                    "--writable",
                    "--out", out.toString()
                );
            assertThat(exit).isZero();
            ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
            JsonNode tree = m.readTree(Files.readString(out));
            assertThat(tree.get("kind").asText()).isEqualTo("update");
            assertThat(tree.get("updateCount").asLong()).isEqualTo(1L);
        }
    }
}
