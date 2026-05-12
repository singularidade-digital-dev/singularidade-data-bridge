package digital.singularidade.databridge.pipeline.ddl;

import digital.singularidade.databridge.source.jdbc.JdbcSource;

import java.util.Optional;

public final class MssqlDdlBuilder implements DdlBuilder {
    @Override
    public Optional<DdlScript> build(JdbcSource source, String schema, String table,
                                      String redactedUrl, boolean includeTriggers) {
        return Optional.empty();   // populated in Task 13
    }
}
