package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.ColumnStats;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class ColumnStatsExtractor {

    public List<ColumnStats> extract(Source source, String schema, String table) {
        return source.columnStats(schema, table);
    }
}
