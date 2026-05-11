package digital.singularidade.databridge.output;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColumnStatsModeTest {

    @Test
    void parses_wire_names_case_insensitive_with_aliases() {
        assertThat(ColumnStatsMode.fromWireName("full")).isEqualTo(ColumnStatsMode.FULL);
        assertThat(ColumnStatsMode.fromWireName("FULL")).isEqualTo(ColumnStatsMode.FULL);
        assertThat(ColumnStatsMode.fromWireName("histogram-only")).isEqualTo(ColumnStatsMode.HISTOGRAM_ONLY);
        assertThat(ColumnStatsMode.fromWireName("histogram_only")).isEqualTo(ColumnStatsMode.HISTOGRAM_ONLY);
        assertThat(ColumnStatsMode.fromWireName("histogram")).isEqualTo(ColumnStatsMode.HISTOGRAM_ONLY);
        assertThat(ColumnStatsMode.fromWireName("off")).isEqualTo(ColumnStatsMode.OFF);
        assertThat(ColumnStatsMode.fromWireName("none")).isEqualTo(ColumnStatsMode.OFF);
    }

    @Test
    void default_when_null_or_blank_is_histogram_only() {
        assertThat(ColumnStatsMode.fromWireName(null)).isEqualTo(ColumnStatsMode.HISTOGRAM_ONLY);
        assertThat(ColumnStatsMode.fromWireName("")).isEqualTo(ColumnStatsMode.HISTOGRAM_ONLY);
        assertThat(ColumnStatsMode.fromWireName("   ")).isEqualTo(ColumnStatsMode.HISTOGRAM_ONLY);
    }

    @Test
    void unknown_throws_with_helpful_message() {
        assertThatThrownBy(() -> ColumnStatsMode.fromWireName("garbage"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("garbage")
            .hasMessageContaining("full, histogram-only, or off");
    }

    @Test
    void apply_full_returns_input_unchanged() {
        ColumnStats s = new ColumnStats("col", 10L, 0.05, List.of("a","b"), List.of(0.5,0.3), 0.8);
        List<ColumnStats> result = ColumnStatsMode.FULL.apply(List.of(s));
        assertThat(result).containsExactly(s);
    }

    @Test
    void apply_off_returns_empty_list() {
        ColumnStats s = new ColumnStats("col", 10L, 0.05, List.of("a"), List.of(0.5), 0.8);
        assertThat(ColumnStatsMode.OFF.apply(List.of(s))).isEmpty();
    }

    @Test
    void apply_histogram_only_zeros_mcv_and_mcf_keeps_aggregates() {
        ColumnStats s = new ColumnStats("col", 10L, 0.05, List.of("a","b"), List.of(0.5,0.3), 0.8);
        List<ColumnStats> result = ColumnStatsMode.HISTOGRAM_ONLY.apply(List.of(s));
        assertThat(result).hasSize(1);
        ColumnStats out = result.get(0);
        assertThat(out.name()).isEqualTo("col");
        assertThat(out.nDistinctEstimate()).isEqualTo(10L);
        assertThat(out.nullFraction()).isEqualTo(0.05);
        assertThat(out.correlation()).isEqualTo(0.8);
        assertThat(out.mostCommonValues()).isEmpty();
        assertThat(out.mostCommonFrequencies()).isEmpty();
    }
}
