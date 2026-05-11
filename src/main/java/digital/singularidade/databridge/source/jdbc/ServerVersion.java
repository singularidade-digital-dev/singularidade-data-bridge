package digital.singularidade.databridge.source.jdbc;

/**
 * Server software version, captured once per connection at {@link JdbcSource#open(String)} time.
 * Used by DDL builders to branch on capabilities (e.g. PG 12+ generated columns,
 * Firebird 5+ INCLUDE columns in indexes).
 *
 * @param driver  driver wire name (postgresql, mysql, oracle, firebird, mssql)
 * @param major   major version (e.g. 14 for "PostgreSQL 14.10")
 * @param minor   minor version (e.g. 10)
 * @param banner  raw version string from the server, for debugging only
 */
public record ServerVersion(String driver, int major, int minor, String banner) {

    public static final ServerVersion UNKNOWN = new ServerVersion("unknown", 0, 0, "unknown");

    /** Convenience: true when this server is at least the given major.minor. */
    public boolean isAtLeast(int targetMajor, int targetMinor) {
        if (major > targetMajor) return true;
        if (major < targetMajor) return false;
        return minor >= targetMinor;
    }
}
