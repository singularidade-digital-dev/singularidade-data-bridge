package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Sample;
import digital.singularidade.databridge.source.Source;

public final class SampleExtractor {

    public Sample extract(Source source, String schema, String table, int limit) {
        return source.sample(schema, table, limit);
    }
}
