package digital.singularidade.databridge.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataRecordsTest {

    @Test
    void round_trips_a_populated_metadata_through_jackson() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        Metadata original = new Metadata(
            "https://singularidade.digital/data-bridge/metadata.v1.json",
            "1.1",
            Instant.parse("2026-05-09T15:42:11Z"),
            new Metadata.Generator("singularidade-data-bridge", "0.1.0"),
            new SourceInfo("jdbc", "postgresql",
                "jdbc:postgresql://host/db?password=***", "atl", "cliente"),
            new TableInfo("TABLE", "Cadastro", "orgen_app", 18432L, null),
            List.of(new Column("id", 1, "bigint", -5, false, true,
                null, 19, 0, null, null,
                new Column.Generated(true, false, null),
                new Column.Sequence("GEN_ID", null, "trigger", "TRG_BI"), "WIN_PTBR",
                "NUMERIC(15,2)", "WIN1252", new Column.Domain("DM_X", true, "0", "(VALUE > 0)"))),
            List.of("id"),
            List.of(new ForeignKey("fk_x", List.of("y"), "atl", "z", List.of("id"),
                "NO ACTION", "NO ACTION",
                List.of(new ForeignKey.RefColumn("id", "bigint", false)))),
            List.of(new Index("idx_x", List.of(), List.of(true), false, false, "btree", null,
                "(UPPER(A))", false, "FK_X")),
            List.of(new UniqueConstraint("uk_x", List.of("a"))),
            List.of(new CheckConstraint("chk_x", "a > 0")),
            List.of(new Trigger("TRG_BI", "BEFORE", List.of("INSERT", "UPDATE"), 5, false,
                "AS BEGIN END", List.of("GEN_ID"))),
            new Sample(1, List.of(Map.of("id", 1))),
            List.of(new ColumnStats("a", 7L, 0.001, List.of("1"), List.of(0.5), 0.9)),
            new Cardinality(100L, List.of(new Cardinality.PerColumn("id", 100L, 0L))),
            new Partitioning(false, null, List.of(), null, List.of()),
            List.of("warning text")
        );

        String json = mapper.writeValueAsString(original);
        Metadata roundTripped = mapper.readValue(json, Metadata.class);

        assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void serializes_domain_default_under_the_default_key() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new Column.Domain("DM_FLAG", true, "'F'", "(VALUE IN ('T','F'))"));
        assertThat(json).contains("\"default\":\"'F'\"").doesNotContain("defaultValue");
    }

    @Test
    void keeps_pre_1_1_constructors_with_null_new_fields() {
        Column c = new Column("c", 1, "integer", 4, true, false, null, 10, 0, null, null,
            new Column.Generated(false, false, null), new Column.Sequence("S", null), null);
        assertThat(c.nativeType()).isNull();
        assertThat(c.domain()).isNull();
        assertThat(c.sequence().source()).isNull();
        Index i = new Index("i", List.of("a"), List.of(true), false, false, null, null);
        assertThat(i.expression()).isNull();
        assertThat(i.active()).isNull();
    }
}
