package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.CheckConstraint;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class CheckConstraintsExtractor {

    public List<CheckConstraint> extract(Source source, String schema, String table) {
        return source.checkConstraints(schema, table);
    }
}
