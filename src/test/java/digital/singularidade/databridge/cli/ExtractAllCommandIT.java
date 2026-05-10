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

class ExtractAllCommandIT {

    @Test
    void exclude_skips_named_tables(@TempDir Path tmp) throws Exception {
        try (PgFixture fx = new PgFixture()) {
            int exit = new CommandLine(new Main())
                .setErr(new PrintWriter(OutputStream.nullOutputStream()))
                .execute(
                    "extract-all",
                    "--jdbc-url", fx.jdbcUrl(),
                    "--schema", "atl",
                    "--out", tmp.toString(),
                    "--exclude", "estadocivil"
                );
            assertThat(exit).isZero();
            assertThat(tmp.resolve("atl.estadocivil")).doesNotExist();
            assertThat(tmp.resolve("atl.cliente").resolve("metadata.json")).exists();

            ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
            JsonNode index = m.readTree(Files.readString(tmp.resolve("_index.json")));
            assertThat(index.get("tableCount").asInt()).isEqualTo(1);
        }
    }

    @Test
    void include_views_pulls_in_view_definitions(@TempDir Path tmp) throws Exception {
        try (PgFixture fx = new PgFixture()) {
            int exit = new CommandLine(new Main())
                .setErr(new PrintWriter(OutputStream.nullOutputStream()))
                .execute(
                    "extract-all",
                    "--jdbc-url", fx.jdbcUrl(),
                    "--schema", "atl",
                    "--out", tmp.toString(),
                    "--include-views"
                );
            assertThat(exit).isZero();
            // The PG fixture includes a view: atl.cliente_vw
            assertThat(tmp.resolve("atl.cliente_vw").resolve("metadata.json")).exists();

            ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
            JsonNode viewMeta = m.readTree(Files.readString(
                tmp.resolve("atl.cliente_vw").resolve("metadata.json")));
            assertThat(viewMeta.get("tableInfo").get("type").asText()).isEqualTo("VIEW");
            assertThat(viewMeta.get("tableInfo").get("viewDefinition").asText()).contains("SELECT");
        }
    }

    @Test
    void extracts_every_table_into_its_own_directory_and_writes_index(@TempDir Path tmp) throws Exception {
        try (PgFixture fx = new PgFixture()) {
            int exit = new CommandLine(new Main())
                .setErr(new PrintWriter(OutputStream.nullOutputStream()))
                .execute(
                    "extract-all",
                    "--jdbc-url", fx.jdbcUrl(),
                    "--schema", "atl",
                    "--out", tmp.toString()
                );
            assertThat(exit).isZero();

            // Per-table directories with metadata.json
            assertThat(tmp.resolve("atl.estadocivil").resolve("metadata.json")).exists();
            assertThat(tmp.resolve("atl.cliente").resolve("metadata.json")).exists();
            // No TSVs (no --tsv flag)
            assertThat(tmp.resolve("atl.cliente").resolve("columns.tsv")).doesNotExist();
            // No partial files left behind
            assertThat(tmp.resolve(".metadata.json.partial")).doesNotExist();
            assertThat(tmp.resolve("._index.json.partial")).doesNotExist();

            // _index.json at top
            Path indexPath = tmp.resolve("_index.json");
            assertThat(indexPath).exists();

            ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
            JsonNode index = m.readTree(Files.readString(indexPath));

            assertThat(index.get("version").asText()).isEqualTo("1.0");
            assertThat(index.get("source").get("driver").asText()).isEqualTo("postgresql");
            assertThat(index.get("source").get("schema").asText()).isEqualTo("atl");
            assertThat(index.get("source").get("url").asText()).contains("password=***");
            assertThat(index.get("tableCount").asInt()).isEqualTo(2);

            JsonNode tables = index.get("tables");
            assertThat(tables.isArray()).isTrue();
            assertThat(tables.size()).isEqualTo(2);

            // Verify a sample table entry has the expected fields
            boolean foundCliente = false;
            for (JsonNode t : tables) {
                if ("cliente".equals(t.get("table").asText())) {
                    foundCliente = true;
                    assertThat(t.get("schema").asText()).isEqualTo("atl");
                    assertThat(t.get("type").asText()).isEqualTo("TABLE");
                    assertThat(t.get("columnCount").asInt()).isGreaterThan(0);
                    assertThat(t.get("primaryKey").get(0).asText()).isEqualTo("id");
                    assertThat(t.get("totalRows").asLong()).isEqualTo(5L);
                }
            }
            assertThat(foundCliente).isTrue();
        }
    }
}
