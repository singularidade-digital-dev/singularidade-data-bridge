package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.output.ColumnStatsMode;
import digital.singularidade.databridge.output.Metadata;
import digital.singularidade.databridge.output.QueryResult;
import digital.singularidade.databridge.output.SourceUrlRedaction;
import digital.singularidade.databridge.pipeline.MetadataPipeline;
import digital.singularidade.databridge.source.CardinalityMode;
import digital.singularidade.databridge.support.FbFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FirebirdNoneTextIT {

    private static final String TEXT = "ação ‘x’ é";
    private static FbFixture fx;
    private static String url;

    @BeforeAll
    static void start() {
        fx = new FbFixture();
        url = fx.jdbcUrlWithoutEncoding(fx.mixedEncodingDatabase());
    }

    @AfterAll
    static void stop() { fx.close(); }

    private static List<Map<String, Object>> rows(ConnectionSettings settings) {
        try (JdbcSource src = JdbcSource.open(settings)) {
            QueryResult r = src.executeQuery("SELECT ID, D, B, C, W, F FROM TMIX ORDER BY ID", 10, 30, false);
            return r.rows();
        }
    }

    @Test
    void should_decode_win1252_and_utf8_rows_of_the_same_none_column() {
        List<Map<String, Object>> rows = rows(new ConnectionSettings(url, null, null));
        assertThat(rows.get(0)).containsEntry("D", TEXT).containsEntry("B", "ação").containsEntry("C", "çaa");
        assertThat(rows.get(1)).containsEntry("D", TEXT).containsEntry("B", "ação").containsEntry("C", "abc");
    }

    @Test
    void should_keep_typed_columns_exact_and_unpadded() {
        List<Map<String, Object>> rows = rows(new ConnectionSettings(url, null, null));
        assertThat(rows.get(0)).containsEntry("W", "ação").containsEntry("F", "T");
        assertThat(rows.get(1)).containsEntry("W", "é").containsEntry("F", "F");
    }

    @Test
    void should_use_the_chosen_fallback_for_bytes_that_are_not_utf8() {
        List<Map<String, Object>> rows = rows(new ConnectionSettings(url, null, null, "ISO8859_1"));
        assertThat(rows.get(0)).containsEntry("D", "ação \u0091x\u0092 é");
        assertThat(rows.get(1)).containsEntry("D", TEXT);
    }

    @Test
    void should_read_accented_metadata_exactly() {
        try (JdbcSource src = JdbcSource.open(new ConnectionSettings(url, null, null))) {
            Metadata m = new MetadataPipeline(new PrintStream(OutputStream.nullOutputStream()), CardinalityMode.SKIP,
                ColumnStatsMode.OFF, SourceUrlRedaction.HOST_PORT).run(src, url, null, "TMIX", 0);
            Column d = m.columns().stream().filter(c -> c.name().equals("D")).findFirst().orElseThrow();
            assertThat(d.comment()).isEqualTo("Descrição com acento");
            assertThat(d.charset()).isEqualTo("NONE");
        }
    }
}
