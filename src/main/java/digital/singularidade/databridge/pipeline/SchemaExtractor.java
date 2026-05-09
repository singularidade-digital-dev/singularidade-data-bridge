package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class SchemaExtractor {

    public List<Column> extract(Source source, String schema, String table) {
        return source.columns(schema, table);
    }
}
