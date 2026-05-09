package digital.singularidade.databridge.output;

import java.util.List;

public record ForeignKey(
    String constraintName,
    List<String> fkColumns,
    String refSchema,
    String refTable,
    List<String> refColumns,
    String onUpdate,
    String onDelete,
    List<RefColumn> refTableColumns
) {
    public record RefColumn(String name, String sqlType, boolean nullable) {}
}
