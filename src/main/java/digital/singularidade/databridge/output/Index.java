package digital.singularidade.databridge.output;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * One index of {@code metadata.json}. Since contract 1.1 a Firebird source also fills {@code expression}
 * (expression indexes have empty {@code columns} and one {@code ordinalAsc} entry), {@code active} and
 * {@code constraint} (the PK/UNIQUE/FK the index backs).
 */
public record Index(
    String name,
    List<String> columns,
    List<Boolean> ordinalAsc,
    boolean unique,
    boolean primary,
    String method,
    @JsonProperty("where") String whereClause,
    String expression,
    Boolean active,
    String constraint
) {
    /** Pre-1.1 shape: the new fields stay {@code null}. */
    public Index(String name, List<String> columns, List<Boolean> ordinalAsc, boolean unique,
                 boolean primary, String method, String whereClause) {
        this(name, columns, ordinalAsc, unique, primary, method, whereClause, null, null, null);
    }
}
