package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class PrimaryKeyExtractor {

    public List<String> extract(Source source, String schema, String table) {
        return source.primaryKey(schema, table);
    }
}
