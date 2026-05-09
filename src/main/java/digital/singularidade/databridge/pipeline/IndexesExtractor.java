package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Index;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class IndexesExtractor {

    public List<Index> extract(Source source, String schema, String table) {
        return source.indexes(schema, table);
    }
}
