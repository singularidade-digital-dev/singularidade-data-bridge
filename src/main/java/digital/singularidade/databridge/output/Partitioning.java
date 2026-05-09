package digital.singularidade.databridge.output;

import java.util.List;

public record Partitioning(
    boolean isPartitioned,
    String strategy,
    List<String> partitionKey,
    Ref parent,
    List<Ref> children
) {
    public record Ref(String schema, String table) {}
}
