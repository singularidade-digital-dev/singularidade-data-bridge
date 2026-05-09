package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.UniqueConstraint;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class UniqueConstraintsExtractor {

    public List<UniqueConstraint> extract(Source source, String schema, String table) {
        return source.uniqueConstraints(schema, table);
    }
}
