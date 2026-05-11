package digital.singularidade.databridge.output;

import java.util.List;

/**
 * Controls what fields the {@code columnStats} array carries in the JSON output.
 *
 * <ul>
 *   <li>{@link #FULL}: all fields populated (nDistinct, nullFraction, MCV, MCF, correlation).
 *       MCV/MCF can contain real database values — PII risk.</li>
 *   <li>{@link #HISTOGRAM_ONLY} (default): aggregates kept (nDistinct, nullFraction, correlation);
 *       MCV/MCF zeroed to empty arrays. Safe to commit alongside source.</li>
 *   <li>{@link #OFF}: {@code columnStats} array is empty.</li>
 * </ul>
 */
public enum ColumnStatsMode {
    FULL,
    HISTOGRAM_ONLY,
    OFF;

    public String wireName() {
        return switch (this) {
            case FULL -> "full";
            case HISTOGRAM_ONLY -> "histogram-only";
            case OFF -> "off";
        };
    }

    public static ColumnStatsMode fromWireName(String s) {
        if (s == null || s.isBlank()) return HISTOGRAM_ONLY;
        return switch (s.trim().toLowerCase()) {
            case "full" -> FULL;
            case "histogram-only", "histogram_only", "histogram" -> HISTOGRAM_ONLY;
            case "off", "none", "skip" -> OFF;
            default -> throw new IllegalArgumentException(
                "unknown column-stats-mode: '" + s + "' (expected full, histogram-only, or off)");
        };
    }

    /** Apply this mode to a list of ColumnStats, returning a new list with the appropriate fields zeroed. */
    public List<ColumnStats> apply(List<ColumnStats> in) {
        if (in == null) return List.of();
        return switch (this) {
            case FULL -> in;
            case OFF -> List.of();
            case HISTOGRAM_ONLY -> in.stream().map(s -> new ColumnStats(
                s.name(), s.nDistinctEstimate(), s.nullFraction(),
                List.of(), List.of(),    // MCV + MCF zeroed
                s.correlation())).toList();
        };
    }
}
