package digital.singularidade.databridge.server;

public record ExtractRequest(
    String jdbcUrl,
    String schema,
    String table,
    Integer sampleRows,
    Boolean skipCardinality
) {
    public int sampleRowsOrDefault() { return sampleRows == null ? 0 : sampleRows; }
    public boolean skipCardinalityOrDefault() { return skipCardinality != null && skipCardinality; }
}
