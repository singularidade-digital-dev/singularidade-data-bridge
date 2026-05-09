package digital.singularidade.databridge.output;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Column(
    String name,
    int ordinalPosition,
    String sqlType,
    int jdbcType,
    boolean nullable,
    boolean primaryKey,
    Integer characterMaxLength,
    Integer numericPrecision,
    Integer numericScale,
    @JsonProperty("default") String defaultValue,
    String comment,
    Generated generated,
    Sequence sequence,
    String collation
) {
    public record Generated(boolean isIdentity, boolean isComputed, String generationExpression) {}
    public record Sequence(String name, String schema) {}
}
