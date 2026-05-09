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

class ExtractCommandIT {

    @Test
    void extracts_cliente_end_to_end(@TempDir Path tmp) throws Exception {
        try (PgFixture fx = new PgFixture()) {
            int exit = new CommandLine(new Main())
                .setErr(new PrintWriter(OutputStream.nullOutputStream()))
                .execute(
                    "extract",
                    "--jdbc-url", fx.jdbcUrl(),
                    "--schema", "atl",
                    "--table", "cliente",
                    "--out", tmp.toString(),
                    "--tsv"
                );
            assertThat(exit).isZero();

            Path metadata = tmp.resolve("metadata.json");
            assertThat(metadata).exists();

            ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
            JsonNode tree = m.readTree(Files.readString(metadata));

            assertThat(tree.get("version").asText()).isEqualTo("1.0");
            assertThat(tree.get("source").get("driver").asText()).isEqualTo("postgresql");
            assertThat(tree.get("source").get("url").asText()).contains("password=***");
            assertThat(tree.get("primaryKey").get(0).asText()).isEqualTo("id");
            assertThat(tree.get("foreignKeys").size()).isEqualTo(1);
            assertThat(tree.get("indexes").size()).isGreaterThanOrEqualTo(2);
            assertThat(tree.get("sample").get("rowCount").asInt()).isEqualTo(5);
            assertThat(tree.get("cardinality").get("totalRows").asLong()).isEqualTo(5L);
            assertThat(tree.get("warnings").isArray()).isTrue();

            assertThat(tmp.resolve("columns.tsv")).exists();
            assertThat(tmp.resolve("sample.tsv")).exists();
        }
    }
}
