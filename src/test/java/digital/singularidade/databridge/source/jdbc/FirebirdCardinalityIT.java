package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.source.CardinalityMode;
import digital.singularidade.databridge.support.FbFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FirebirdCardinalityIT {

    @Test
    void approximate_mode_derives_total_and_per_column_distinct_from_index_stats() {
        try (FbFixture fx = new FbFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {

            assertThat(src.driverWireName()).isEqualTo("firebird");

            // PRODUCTS has 100 rows, an indexed CATEGORY column with 3 distinct values, and a
            // unique index on SKU with 100 distinct values. The PK on ID is also unique → 100.
            List<Column> cols = src.columns(null, "PRODUCTS");
            Cardinality c = src.cardinality(null, "PRODUCTS", cols, CardinalityMode.APPROXIMATE);

            // 1.0 / selectivity_of_PK ≈ 100. Selectivity stats are sampled, so allow ±10.
            assertThat(c.totalRows()).isBetween(90L, 110L);

            // Per-column distinct (only indexed columns are estimated)
            Cardinality.PerColumn id = c.perColumn().stream()
                .filter(p -> "ID".equalsIgnoreCase(p.name())).findFirst().orElseThrow();
            assertThat(id.distinctCount()).isBetween(90L, 110L);

            Cardinality.PerColumn sku = c.perColumn().stream()
                .filter(p -> "SKU".equalsIgnoreCase(p.name())).findFirst().orElseThrow();
            assertThat(sku.distinctCount()).isBetween(90L, 110L);

            // CATEGORY has only 3 distinct values across 100 rows
            Cardinality.PerColumn cat = c.perColumn().stream()
                .filter(p -> "CATEGORY".equalsIgnoreCase(p.name())).findFirst().orElseThrow();
            assertThat(cat.distinctCount()).isBetween(2L, 4L);

            // Non-indexed columns (NAME, PRICE, IN_STOCK) have no estimate available — they should
            // be omitted from perColumn entirely (the pipeline turns this into a warning).
            assertThat(c.perColumn()).extracting(Cardinality.PerColumn::name)
                .doesNotContain("NAME", "PRICE", "IN_STOCK");
        }
    }

    @Test
    void exact_mode_still_works_on_firebird() {
        try (FbFixture fx = new FbFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            List<Column> cols = src.columns(null, "PRODUCTS");
            Cardinality c = src.cardinality(null, "PRODUCTS", cols, CardinalityMode.EXACT);
            assertThat(c.totalRows()).isEqualTo(100L);   // EXACT = COUNT(*) — authoritative
            // EXACT runs COUNT(DISTINCT) on every non-BLOB column, so all 6 should be present
            assertThat(c.perColumn()).hasSize(6);
        }
    }
}
