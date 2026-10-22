package digital.singularidade.databridge.output;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One column of {@code metadata.json}. Since contract 1.1 a Firebird source also fills {@code nativeType},
 * {@code charset}, {@code collation}, {@code domain} and {@code sequence}; other drivers leave them
 * {@code null}.
 *
 * @param nativeType exact type as declared in the source database, e.g. {@code NUMERIC(15,2)}
 * @param charset    character set of text columns
 * @param domain     named domain the column is based on, or {@code null}
 */
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
    String collation,
    String nativeType,
    String charset,
    Domain domain
) {
    /** Pre-1.1 shape: the new fields stay {@code null}. */
    public Column(String name, int ordinalPosition, String sqlType, int jdbcType, boolean nullable,
                  boolean primaryKey, Integer characterMaxLength, Integer numericPrecision,
                  Integer numericScale, String defaultValue, String comment, Generated generated,
                  Sequence sequence, String collation) {
        this(name, ordinalPosition, sqlType, jdbcType, nullable, primaryKey, characterMaxLength,
            numericPrecision, numericScale, defaultValue, comment, generated, sequence, collation,
            null, null, null);
    }

    public record Generated(boolean isIdentity, boolean isComputed, String generationExpression) {}

    /**
     * Generator / sequence that fills the column.
     *
     * @param source  {@code identity} or {@code trigger} (Firebird); {@code null} when unknown
     * @param trigger trigger that assigns the generator when {@code source} is {@code trigger}
     */
    public record Sequence(String name, String schema, String source, String trigger) {
        public Sequence(String name, String schema) {
            this(name, schema, null, null);
        }
    }

    /**
     * @param notNull      NOT NULL declared on the domain
     * @param defaultValue DEFAULT declared on the domain, without the keyword
     * @param check        CHECK declared on the domain, without the keyword, e.g. {@code (VALUE IN ('T','F'))}
     */
    public record Domain(String name, boolean notNull, @JsonProperty("default") String defaultValue,
                         String check) {}
}
