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
            "1.0",
            Instant.parse("2026-05-09T15:42:11Z"),
            new Metadata.Generator("singularidade-data-bridge", "0.1.0"),
            new SourceInfo("jdbc", "postgresql",
                "jdbc:postgresql://host/db?password=***", "atl", "cliente"),
            new TableInfo("TABLE", "Cadastro", "orgen_app", 18432L, null),
            List.of(new Column("id", 1, "bigint", -5, false, true,
                null, 19, 0, null, null,
                new Column.Generated(true, false, null), null, null)),
            List.of("id"),
            List.of(new ForeignKey("fk_x", List.of("y"), "atl", "z", List.of("id"),
                "NO ACTION", "NO ACTION",
                List.of(new ForeignKey.RefColumn("id", "bigint", false)))),
            List.of(new Index("idx_x", List.of("a"), List.of(true), false, false, "btree", null)),
            List.of(new UniqueConstraint("uk_x", List.of("a"))),
            List.of(new CheckConstraint("chk_x", "a > 0")),
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
}
