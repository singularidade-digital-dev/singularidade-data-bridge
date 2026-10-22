package digital.singularidade.databridge.source.jdbc.firebird;

import java.util.List;
import java.util.Optional;

/**
 * Everything the Firebird catalog says about one table, read once by {@link FirebirdCatalog} and shared by
 * metadata.json enrichment and the DDL builder. Lists come in a fixed order: columns by position,
 * constraints / indexes by name, triggers by (timing, position, name).
 *
 * @param databaseCharset default character set of the database ({@code NONE} when unset)
 * @param domains         named domains used by the table's columns, sorted by name
 * @param triggerGenerators generators the table's triggers depend on, sorted
 */
public record FbTable(String name, boolean view, String viewSource, String description,
                      String databaseCharset, List<FbColumn> columns, List<FbDomain> domains,
                      FbKey primaryKey, List<FbKey> uniques, List<FbForeignKey> foreignKeys,
                      List<FbCheck> checks, List<FbIndex> indexes, List<FbTrigger> triggers,
                      List<String> triggerGenerators) {

    /**
     * @param charset            character set of text columns ({@code null} for other types)
     * @param collation          effective collation of text columns
     * @param domainName         named domain, or {@code null} when the column has an implicit one
     * @param columnNotNull      NOT NULL declared on the column itself
     * @param columnDefault      DEFAULT declared on the column itself, without the keyword
     * @param columnCollation    COLLATE declared on the column itself
     * @param computedSource     {@code COMPUTED BY} expression as stored, e.g. {@code (VALOR * QTDE)}
     * @param identityType       {@code 0} = ALWAYS, {@code 1} = BY DEFAULT, {@code null} = not an identity
     */
    public record FbColumn(String name, int position, FirebirdTypes.FieldType type, String nativeType,
                           String charset, String collation, String charsetDefaultCollation,
                           String domainName, boolean columnNotNull, String columnDefault,
                           String columnCollation, String computedSource, Integer identityType,
                           String identityGenerator, String description, FbSequenceLink sequence) {

        public boolean effectiveNotNull(FbDomain domain) {
            return columnNotNull || (domain != null && domain.notNull());
        }
    }

    /** @param checkSource the domain's CHECK as stored, e.g. {@code CHECK (VALUE IN ('T','F'))} */
    public record FbDomain(String name, String nativeType, String charset, String collation,
                           String charsetDefaultCollation, boolean notNull, String defaultValue,
                           String checkSource) {}

    /** PRIMARY KEY or UNIQUE constraint; {@code columns} in key order. */
    public record FbKey(String name, List<String> columns, String indexName, boolean descending) {}

    /** {@code updateRule}/{@code deleteRule} as stored: RESTRICT, NO ACTION, CASCADE, SET NULL, SET DEFAULT. */
    public record FbForeignKey(String name, List<String> columns, String refTable, List<String> refColumns,
                               String updateRule, String deleteRule, String indexName, boolean descending) {}

    /** @param source the CHECK as stored, e.g. {@code CHECK (QTDE >= 0)} */
    public record FbCheck(String name, String source) {}

    /** @param constraintName the PK/UNIQUE/FK this index backs, or {@code null} */
    public record FbIndex(String name, List<String> columns, boolean unique, boolean descending,
                          String expression, boolean active, String constraintName) {}

    public record FbTrigger(String name, String timing, List<String> events, int position, boolean active,
                            String body, List<String> generators) {}

    /** @param source {@code identity} or {@code trigger}; {@code trigger} names the trigger for the latter */
    public record FbSequenceLink(String generator, String source, String trigger) {}

    public Optional<FbDomain> domain(String domainName) {
        if (domainName == null) return Optional.empty();
        return domains.stream().filter(d -> d.name().equals(domainName)).findFirst();
    }
}
