package digital.singularidade.databridge.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TsvWriterTest {

    @Test
    void writes_columns_fks_indexes_unique_check_sample_cardinality_tsvs(@TempDir Path tmp) throws Exception {
        Column col = new Column("nome", 1, "varchar(200)", 12, true, false, 200, null, null,
            null, null, new Column.Generated(false, false, null), null, null);
        Metadata m = new Metadata("u", "1.0", Instant.EPOCH,
            new Metadata.Generator("g", "v"),
            new SourceInfo("jdbc", "pg", "url", "s", "t"),
            new TableInfo("TABLE", null, null, null, null),
            List.of(col), List.of("id"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new Sample(1, List.of(Map.of("nome", "FULANO\tCOM\tTAB"))),
            List.of(),
            new Cardinality(10, List.of(new Cardinality.PerColumn("id", 10, 0))),
            new Partitioning(false, null, List.of(), null, List.of()),
            List.of());

        new TsvWriter().writeAll(m, tmp);

        assertThat(Files.exists(tmp.resolve("columns.tsv"))).isTrue();
        assertThat(Files.exists(tmp.resolve("sample.tsv"))).isTrue();
        assertThat(Files.exists(tmp.resolve("cardinality.tsv"))).isTrue();

        String columnsTsv = Files.readString(tmp.resolve("columns.tsv"));
        assertThat(columnsTsv.split("\n")[0]).startsWith("name\tordinalPosition\t");

        String sampleTsv = Files.readString(tmp.resolve("sample.tsv"));
        // tab in value must be escaped as \t literal
        assertThat(sampleTsv).contains("FULANO\\tCOM\\tTAB");
    }
}
