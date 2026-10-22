package digital.singularidade.databridge.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StableOutputTest {

    private final ObjectMapper mapper = JsonWriter.newMapper();

    private static Column column(String name, int pos, Column.Sequence seq) {
        return new Column(name, pos, "integer", 4, false, false, null, 10, 0, null, null,
            new Column.Generated(seq != null, false, null), seq, null);
    }

    private static Metadata sample() {
        return new Metadata("https://x/metadata.v1.json", "1.1", Instant.parse("2026-10-22T10:00:00Z"),
            new Metadata.Generator("singularidade-data-bridge", "0.8.0"),
            new SourceInfo("jdbc", "firebird", "jdbc:firebirdsql://[redacted-host]/db", null, "T"),
            new TableInfo("TABLE", "c", null, 42L, null),
            List.of(column("B", 2, null), column("A", 1, new Column.Sequence("RDB$3", null, "identity", null))),
            List.of("A"),
            List.of(
                new ForeignKey("INTEG_8", List.of("B"), null, "TZ", List.of("ID"), "NO ACTION", "NO ACTION",
                    List.of(new ForeignKey.RefColumn("ID", "integer", false))),
                new ForeignKey("FK_A", List.of("A"), null, "TA", List.of("ID"), "NO ACTION", "CASCADE", List.of())),
            List.of(
                new Index("RDB$FOREIGN2", List.of("B"), List.of(true), false, false, null, null, null, true, "INTEG_8"),
                new Index("B_IDX", List.of("B"), List.of(true), false, false, null, null),
                new Index("A_IDX", List.of("A"), List.of(true), false, false, null, null)),
            List.of(new UniqueConstraint("INTEG_5", List.of("B")), new UniqueConstraint("UK_A", List.of("A"))),
            List.of(new CheckConstraint("INTEG_9", "(B > 0)"), new CheckConstraint("CK_A", "(A > 0)")),
            List.of(
                new Trigger("T_AFTER", "AFTER", List.of("DELETE"), 0, true, "AS BEGIN END", List.of()),
                new Trigger("T_B5", "BEFORE", List.of("INSERT"), 5, true, "AS BEGIN END", List.of()),
                new Trigger("T_B0", "BEFORE", List.of("INSERT"), 0, true, "AS BEGIN END", List.of())),
            new Sample(1, List.of(Map.of("A", 1))),
            List.of(new ColumnStats("A", 1L, 0.0, List.of(), List.of(), 1.0)),
            new Cardinality(42, List.of(new Cardinality.PerColumn("A", 42, 0))),
            new Partitioning(false, null, List.of(), null, List.of()),
            List.of("a warning"));
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.get(field).isNull() ? null : n.get(field).asText()));
        return out;
    }

    @Test
    void should_drop_volatile_fields() {
        ObjectNode t = StableOutput.metadata(mapper, sample());
        assertThat(t.has("generatedAt")).isFalse();
        assertThat(t.has("generator")).isFalse();
        assertThat(t.has("warnings")).isFalse();
        assertThat(t.has("sample")).isFalse();
        assertThat(t.has("columnStats")).isFalse();
        assertThat(t.has("cardinality")).isFalse();
        assertThat(t.get("source").has("url")).isFalse();
        assertThat(t.get("tableInfo").has("approximateRowCount")).isFalse();
        assertThat(t.get("version").asText()).isEqualTo("1.1");
        assertThat(t.get("foreignKeys").get(0).has("refTableColumns")).isFalse();
    }

    @Test
    void should_null_system_generated_names() {
        ObjectNode t = StableOutput.metadata(mapper, sample());
        assertThat(texts(t.get("foreignKeys"), "constraintName")).containsExactly("FK_A", null);
        assertThat(texts(t.get("uniqueConstraints"), "name")).containsExactly("UK_A", null);
        assertThat(texts(t.get("checkConstraints"), "name")).containsExactly("CK_A", null);
        assertThat(t.get("columns").get(0).get("sequence").get("name").isNull()).isTrue();
        assertThat(t.get("columns").get(0).get("sequence").get("source").asText()).isEqualTo("identity");
    }

    @Test
    void should_sort_every_list_by_a_natural_key() {
        ObjectNode t = StableOutput.metadata(mapper, sample());
        assertThat(texts(t.get("columns"), "name")).containsExactly("A", "B");
        assertThat(texts(t.get("foreignKeys"), "refTable")).containsExactly("TA", "TZ");
        assertThat(texts(t.get("indexes"), "name")).containsExactly("A_IDX", "B_IDX", null);
        assertThat(texts(t.get("checkConstraints"), "definition")).containsExactly("(A > 0)", "(B > 0)");
        assertThat(texts(t.get("triggers"), "name")).containsExactly("T_B0", "T_B5", "T_AFTER");
    }

    @Test
    void should_strip_the_index_file() {
        ExtractAllIndex idx = new ExtractAllIndex("https://x/index.v1.json", "1.0", Instant.EPOCH,
            new Metadata.Generator("singularidade-data-bridge", "0.8.0"),
            new ExtractAllIndex.SchemaSource("jdbc", "firebird", "jdbc:firebirdsql://h/db", null), 2,
            List.of(new ExtractAllIndex.TableSummary(null, "B", "TABLE", 1, List.of(), 9L),
                    new ExtractAllIndex.TableSummary(null, "A", "TABLE", 2, List.of("ID"), 3L)));
        ObjectNode t = StableOutput.index(mapper, idx);
        assertThat(t.has("generatedAt")).isFalse();
        assertThat(t.has("generator")).isFalse();
        assertThat(t.get("source").has("url")).isFalse();
        assertThat(texts(t.get("tables"), "table")).containsExactly("A", "B");
        assertThat(t.get("tables").get(0).has("totalRows")).isFalse();
    }
}
