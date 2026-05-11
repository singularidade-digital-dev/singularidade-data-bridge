package digital.singularidade.databridge.source;

/**
 * How {@link Source#cardinality(String, String, java.util.List, CardinalityMode)} should compute its result.
 *
 * <ul>
 *   <li>{@link #EXACT}: {@code COUNT(*)} for {@code totalRows}, {@code COUNT(DISTINCT col)} per column.
 *       Authoritative but {@code O(N)} per column, sequential. Can take minutes on large tables.</li>
 *   <li>{@link #APPROXIMATE}: read pre-computed statistics from the database. On PostgreSQL, that's
 *       {@code pg_class.reltuples} for total rows and {@code pg_stats.n_distinct} / {@code pg_stats.null_frac}
 *       per column. Instantaneous; accurate to within whatever the last {@code ANALYZE} produced (typically
 *       within ±10%). Drivers without statistics support fall back to {@link #SKIP} with a warning.</li>
 *   <li>{@link #SKIP}: skip cardinality entirely; pipeline returns
 *       {@code Cardinality(0, [])} and emits a warning.</li>
 * </ul>
 */
public enum CardinalityMode {
    EXACT,
    APPROXIMATE,
    SKIP;

    public String wireName() { return name().toLowerCase(); }

    public static CardinalityMode fromWireName(String s) {
        if (s == null || s.isBlank()) return EXACT;
        return switch (s.trim().toLowerCase()) {
            case "exact"       -> EXACT;
            case "approximate" -> APPROXIMATE;
            case "skip", "none", "off" -> SKIP;
            default -> throw new IllegalArgumentException(
                "unknown cardinality mode: '" + s + "' (expected exact, approximate, or skip)");
        };
    }
}
