package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Partitioning;
import digital.singularidade.databridge.source.Source;

public final class PartitioningExtractor {

    public Partitioning extract(Source source, String schema, String table) {
        return source.partitioning(schema, table);
    }
}
