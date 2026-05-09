package digital.singularidade.databridge.output;

import java.util.List;

public record Cardinality(long totalRows, List<PerColumn> perColumn) {
    public record PerColumn(String name, long distinctCount, long nullCount) {}
}
