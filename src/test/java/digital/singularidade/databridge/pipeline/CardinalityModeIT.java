package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.source.CardinalityMode;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardinalityModeIT {

    @Test
    void exact_mode_returns_correct_counts_and_skips_blob_columns() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            List<Column> cols = src.columns("atl", "cliente");
            // The cliente fixture has a foto BYTEA column; it should be omitted from perColumn
            // even in EXACT mode (BLOB/text-style types are always skipped).
            assertThat(cols).extracting(Column::name).contains("foto");

            Cardinality c = src.cardinality("atl", "cliente", cols, CardinalityMode.EXACT);
            assertThat(c.totalRows()).isEqualTo(5L);
            assertThat(c.perColumn()).extracting(Cardinality.PerColumn::name)
                .doesNotContain("foto");

            Cardinality.PerColumn id = c.perColumn().stream()
                .filter(p -> "id".equals(p.name())).findFirst().orElseThrow();
            assertThat(id.distinctCount()).isEqualTo(5L);

            Cardinality.PerColumn fk = c.perColumn().stream()
                .filter(p -> "fk_estadocivil_id".equals(p.name())).findFirst().orElseThrow();
            assertThat(fk.distinctCount()).isEqualTo(3L);
            assertThat(fk.nullCount()).isEqualTo(1L);
        }
    }

    @Test
    void approximate_mode_uses_pg_stats_and_returns_in_sub_second() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            List<Column> cols = src.columns("atl", "cliente");

            long t0 = System.nanoTime();
            Cardinality c = src.cardinality("atl", "cliente", cols, CardinalityMode.APPROXIMATE);
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

            // The fixture runs ANALYZE in atl-data.sql, so pg_stats is populated.
            // For 5 rows in a freshly-analyzed table, the estimates may be 0 or 5 depending on PG version;
            // we only assert that something came back and that it was fast.
            assertThat(c.totalRows()).isGreaterThanOrEqualTo(0L);
            assertThat(c.perColumn()).isNotEmpty();
            assertThat(c.perColumn()).extracting(Cardinality.PerColumn::name)
                .doesNotContain("foto");
            assertThat(elapsedMs).isLessThan(1000L);  // pg_stats is two indexed lookups, sub-second always
        }
    }

    @Test
    void skip_mode_returns_zero_total_and_empty_perColumn() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            List<Column> cols = src.columns("atl", "cliente");
            Cardinality c = src.cardinality("atl", "cliente", cols, CardinalityMode.SKIP);
            assertThat(c.totalRows()).isZero();
            assertThat(c.perColumn()).isEmpty();
        }
    }

    @Test
    void default_three_arg_is_exact_for_back_compat() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            List<Column> cols = src.columns("atl", "cliente");
            Cardinality c = src.cardinality("atl", "cliente", cols);
            assertThat(c.totalRows()).isEqualTo(5L);   // exact COUNT(*) result
        }
    }
}
