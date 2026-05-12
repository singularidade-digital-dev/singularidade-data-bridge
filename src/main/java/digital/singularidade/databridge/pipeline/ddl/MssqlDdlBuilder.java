package digital.singularidade.databridge.pipeline.ddl;

import digital.singularidade.databridge.source.jdbc.JdbcSource;

import java.util.List;
import java.util.Optional;

public final class MssqlDdlBuilder implements DdlBuilder {

    @Override
    public Optional<DdlScript> build(JdbcSource source, String schema, String table,
                                      String redactedUrl, boolean includeTriggers) {
        String header = DdlScript.standardHeader("mssql", schema, table,
            redactedUrl, source.serverVersion().banner(),
            List.of(),
            List.of("CREATE TABLE/INDEX/CONSTRAINT/COMMENT — MSSQL DDL extraction not yet implemented (planned for v0.8.0)"));
        String body = "-- MSSQL DDL extraction is not implemented in v0.7.0.\n"
                    + "-- See `metadata.json` in the same directory for structured metadata.\n"
                    + "-- Track the planned work in v0.8.0 release notes.\n";
        return Optional.of(new DdlScript(header, body, "",
            List.of("MSSQL DDL extraction not yet implemented (planned for v0.8.0)")));
    }
}
