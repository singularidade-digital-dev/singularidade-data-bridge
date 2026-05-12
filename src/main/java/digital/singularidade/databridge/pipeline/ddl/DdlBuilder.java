package digital.singularidade.databridge.pipeline.ddl;

import digital.singularidade.databridge.source.jdbc.JdbcSource;

import java.util.Optional;

/**
 * Driver-specific DDL extractor. Implementations live alongside this interface
 * (PostgresDdlBuilder, MysqlDdlBuilder, OracleDdlBuilder, FirebirdDdlBuilder,
 * MssqlDdlBuilder).
 *
 * <p>Each impl is stateless w.r.t. tables — pass any state via the {@link JdbcSource}
 * (which carries the connection, hints, and serverVersion).
 */
public interface DdlBuilder {

    /**
     * Build a DdlScript for the given table.
     *
     * @param source           the open JdbcSource (carries connection + hints + version)
     * @param schema           schema name (null for single-schema drivers like Firebird/MySQL)
     * @param table            table name
     * @param redactedUrl      the source URL after redaction (for header provenance)
     * @param includeTriggers  if true, populate the triggers body; otherwise leave empty
     * @return DdlScript on success; Optional.empty() if the driver doesn't support DDL extraction yet
     */
    Optional<DdlScript> build(JdbcSource source, String schema, String table,
                               String redactedUrl, boolean includeTriggers);
}
