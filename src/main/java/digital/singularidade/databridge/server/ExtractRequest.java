package digital.singularidade.databridge.server;

import digital.singularidade.databridge.output.ColumnStatsMode;
import digital.singularidade.databridge.source.CardinalityMode;

public record ExtractRequest(
    String jdbcUrl,
    String schema,
    String table,
    Integer sampleRows,
    Boolean skipCardinality,
    String cardinalityMode,
    String columnStatsMode
) {
    public int sampleRowsOrDefault() { return sampleRows == null ? 0 : sampleRows; }

    /**
     * Resolve the cardinality mode in this priority order:
     *   1. Legacy {@code skipCardinality=true} → SKIP (back-compat with v0.4.x clients).
     *   2. Explicit {@code cardinalityMode} field, parsed via {@link CardinalityMode#fromWireName(String)}.
     *   3. Default: EXACT (matches CLI extract default; HTTP /v1/extract is single-table).
     */
    public CardinalityMode resolvedCardinalityMode() {
        if (skipCardinality != null && skipCardinality) return CardinalityMode.SKIP;
        return CardinalityMode.fromWireName(cardinalityMode);
    }

    public ColumnStatsMode resolvedColumnStatsMode() {
        return ColumnStatsMode.fromWireName(columnStatsMode);
    }
}
