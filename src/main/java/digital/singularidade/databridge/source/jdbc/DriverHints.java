package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;

public enum DriverHints {
    PG("postgresql", "jdbc:postgresql:", true),
    FIREBIRD("firebird", "jdbc:firebirdsql:", false),
    ORACLE("oracle", "jdbc:oracle:", true),
    MSSQL("mssql", "jdbc:sqlserver:", true),
    MYSQL("mysql", "jdbc:mysql:", false);

    private final String wireName;
    private final String urlPrefix;
    private final boolean requiresSchema;

    DriverHints(String wireName, String urlPrefix, boolean requiresSchema) {
        this.wireName = wireName;
        this.urlPrefix = urlPrefix;
        this.requiresSchema = requiresSchema;
    }

    public String wireName() { return wireName; }
    public boolean requiresSchema() { return requiresSchema; }

    public static DriverHints fromUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            throw new DataBridgeException(ErrorCodes.INVALID_ARGS,
                "JDBC URL is null", "Pass a non-null URL via --jdbc-url");
        }
        for (DriverHints h : values()) {
            if (jdbcUrl.startsWith(h.urlPrefix)) return h;
        }
        throw new DataBridgeException(ErrorCodes.UNSUPPORTED,
            "Unsupported JDBC URL prefix: " + jdbcUrl,
            "Supported: jdbc:postgresql:, jdbc:firebirdsql:, jdbc:oracle:, jdbc:sqlserver:, jdbc:mysql:");
    }
}
