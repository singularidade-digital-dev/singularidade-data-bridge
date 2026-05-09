package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class CardinalityExtractor {

    public Cardinality extract(Source source, String schema, String table, List<Column> columns) {
        return source.cardinality(schema, table, columns);
    }
}
