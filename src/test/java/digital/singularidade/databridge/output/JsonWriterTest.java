package digital.singularidade.databridge.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonWriterTest {

    @Test
    void writes_metadata_as_pretty_json_atomically(@TempDir Path tmp) throws Exception {
        Metadata m = new Metadata(
            "https://example/schema", "1.0",
            Instant.parse("2026-05-09T12:00:00Z"),
            new Metadata.Generator("singularidade-data-bridge", "0.1.0"),
            new SourceInfo("jdbc", "postgresql", "jdbc:postgresql://h/d?password=***", "atl", "cliente"),
            new TableInfo("TABLE", null, null, null, null),
            List.of(),
            List.of("id"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new Sample(0, List.of()),
            List.of(),
            new Cardinality(0, List.of()),
            new Partitioning(false, null, List.of(), null, List.of()),
            List.of()
        );

        Path target = tmp.resolve("metadata.json");
        new JsonWriter().write(m, target);

        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.exists(tmp.resolve(".metadata.json.partial"))).isFalse();

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonNode tree = mapper.readTree(Files.readString(target));
        assertThat(tree.get("version").asText()).isEqualTo("1.0");
        assertThat(tree.get("source").get("url").asText()).contains("password=***");
        assertThat(tree.get("primaryKey").get(0).asText()).isEqualTo("id");
    }

    @Test
    void renames_default_field_to_default_in_json(@TempDir Path tmp) throws Exception {
        Column col = new Column("c", 1, "varchar(10)", 12, true, false,
            10, null, null, "DEFAULT_VAL", null,
            new Column.Generated(false, false, null), null, null);
        Metadata m = new Metadata("u", "1.0", Instant.EPOCH,
            new Metadata.Generator("g", "v"),
            new SourceInfo("jdbc", "pg", "url", "s", "t"),
            new TableInfo("TABLE", null, null, null, null),
            List.of(col), List.of(), List.of(), List.of(), List.of(), List.of(),
            new Sample(0, List.of()), List.of(),
            new Cardinality(0, List.of()),
            new Partitioning(false, null, List.of(), null, List.of()),
            List.of());

        Path target = tmp.resolve("metadata.json");
        new JsonWriter().write(m, target);

        String json = Files.readString(target);
        assertThat(json).contains("\"default\" : \"DEFAULT_VAL\"")
            .doesNotContain("\"defaultValue\"");
    }
}
