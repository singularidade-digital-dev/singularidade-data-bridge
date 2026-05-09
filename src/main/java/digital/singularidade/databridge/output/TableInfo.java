package digital.singularidade.databridge.output;

public record TableInfo(
    String type,
    String comment,
    String owner,
    Long approximateRowCount,
    String viewDefinition
) {}
