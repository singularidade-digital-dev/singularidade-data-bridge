package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.TableInfo;
import digital.singularidade.databridge.source.Source;

public final class TableInfoExtractor {

    public TableInfo extract(Source source, String schema, String table) {
        return source.tableInfo(schema, table);
    }
}
