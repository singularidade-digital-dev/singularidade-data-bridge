package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.ForeignKey;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class ForeignKeysExtractor {

    public List<ForeignKey> extract(Source source, String schema, String table) {
        return source.foreignKeys(schema, table);
    }
}
