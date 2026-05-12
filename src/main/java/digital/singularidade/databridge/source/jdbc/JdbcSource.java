package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.BuildInfo;
import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.CheckConstraint;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.output.ColumnStats;
import digital.singularidade.databridge.output.ForeignKey;
import digital.singularidade.databridge.output.Index;
import digital.singularidade.databridge.output.Partitioning;
import digital.singularidade.databridge.output.QueryResult;
import digital.singularidade.databridge.output.Sample;
import digital.singularidade.databridge.output.TableInfo;
import digital.singularidade.databridge.output.UniqueConstraint;
import digital.singularidade.databridge.source.Source;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JdbcSource implements Source {

    private final Connection connection;
    private final DriverHints hints;
    private final ServerVersion version;
    private final Map<String, List<String>> pkCache = new HashMap<>();
    private final Map<String, PgClassInfo> pgClassCache = new HashMap<>();
    private final Map<String, Map<String, PgStatsEntry>> pgStatsCache = new HashMap<>();

    private record PgClassInfo(String owner, Long approxRows, String viewDef,
                                String partitionStrategy, List<String> partitionKey) {}

    private record PgStatsEntry(double nDistinct, double nullFrac, String mcvText, String mcfText, double correlation) {}

    private JdbcSource(Connection connection, DriverHints hints, ServerVersion version) {
        this.connection = connection;
        this.hints = hints;
        this.version = version;
    }

    public static JdbcSource open(String jdbcUrl) {
        DriverHints hints = DriverHints.fromUrl(jdbcUrl);
        String urlWithAppName = withApplicationName(jdbcUrl, hints);
        try {
            Connection c = DriverManager.getConnection(urlWithAppName);
            c.setReadOnly(true);
            ServerVersion version = detectServerVersion(c, hints);
            if (hints == DriverHints.PG) {
                try (Statement st = c.createStatement()) {
                    st.execute("BEGIN READ ONLY");
                } catch (SQLException ignored) {
                    // If the server rejects BEGIN READ ONLY (very unusual), continue without it —
                    // setReadOnly(true) above is the more important guard.
                }
            }
            return new JdbcSource(c, hints, version);
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.CONNECTION_FAILED,
                "Failed to connect: " + e.getMessage(),
                "Check JDBC URL, credentials, network, and TLS settings", e);
        }
    }

    private static ServerVersion detectServerVersion(Connection c, DriverHints hints) {
        try {
            return switch (hints) {
                case PG       -> detectPgVersion(c);
                case MYSQL    -> detectMySqlVersion(c);
                case ORACLE   -> detectOracleVersion(c);
                case FIREBIRD -> detectFirebirdVersion(c);
                case MSSQL    -> detectMssqlVersion(c);
            };
        } catch (SQLException e) {
            return ServerVersion.UNKNOWN;
        }
    }

    private static ServerVersion detectPgVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT current_setting('server_version_num')::int AS num, "
               + "       current_setting('server_version') AS banner")) {
            if (!rs.next()) return ServerVersion.UNKNOWN;
            int num = rs.getInt("num");      // e.g. 140010 for PG 14.10
            return new ServerVersion("postgresql", num / 10000, num % 100, rs.getString("banner"));
        }
    }

    private static ServerVersion detectMySqlVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT VERSION() AS v")) {
            if (!rs.next()) return ServerVersion.UNKNOWN;
            String v = rs.getString("v");          // e.g. "8.0.36-mysql"
            String[] parts = v.split("[.-]");
            int major = parts.length > 0 ? safeInt(parts[0]) : 0;
            int minor = parts.length > 1 ? safeInt(parts[1]) : 0;
            return new ServerVersion("mysql", major, minor, v);
        }
    }

    private static ServerVersion detectOracleVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT BANNER FROM V$VERSION WHERE BANNER LIKE 'Oracle%' FETCH FIRST 1 ROWS ONLY")) {
            if (!rs.next()) return ServerVersion.UNKNOWN;
            String banner = rs.getString(1);        // e.g. "Oracle Database 19c Enterprise Edition Release 19.0.0.0.0"
            // Find first "NN.NN" in the banner
            Matcher m = Pattern.compile("(\\d+)\\.(\\d+)").matcher(banner);
            int major = 0, minor = 0;
            if (m.find()) { major = safeInt(m.group(1)); minor = safeInt(m.group(2)); }
            return new ServerVersion("oracle", major, minor, banner);
        }
    }

    private static ServerVersion detectFirebirdVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT RDB$GET_CONTEXT('SYSTEM','ENGINE_VERSION') AS v FROM RDB$DATABASE")) {
            if (!rs.next()) return ServerVersion.UNKNOWN;
            String v = rs.getString("v");          // e.g. "4.0.4"
            String[] parts = (v == null ? "" : v).split("\\.");
            int major = parts.length > 0 ? safeInt(parts[0]) : 0;
            int minor = parts.length > 1 ? safeInt(parts[1]) : 0;
            return new ServerVersion("firebird", major, minor, v);
        }
    }

    private static ServerVersion detectMssqlVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT @@VERSION AS v")) {
            if (!rs.next()) return ServerVersion.UNKNOWN;
            String banner = rs.getString("v");
            Matcher m = Pattern.compile("(\\d+)\\.(\\d+)").matcher(banner);
            int major = 0, minor = 0;
            if (m.find()) { major = safeInt(m.group(1)); minor = safeInt(m.group(2)); }
            return new ServerVersion("mssql", major, minor, banner);
        }
    }

    private static int safeInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    /** Generic feature-existence probe: does the named column exist in a system catalog table? */
    public boolean columnExists(String catalogTable, String columnName) {
        try (ResultSet rs = connection.getMetaData()
                .getColumns(connection.getCatalog(), null, catalogTable, columnName)) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public ServerVersion serverVersion() { return version; }

    /**
     * Set Postgres' {@code application_name} to {@code data-bridge/<version>} so the connection is
     * easy to spot in {@code pg_stat_activity}. Skipped if the user already passed
     * {@code ApplicationName} in the URL, or for non-PG drivers (each has a different syntax).
     */
    private static String withApplicationName(String jdbcUrl, DriverHints hints) {
        if (hints != DriverHints.PG) return jdbcUrl;
        if (jdbcUrl.toLowerCase().contains("applicationname=")) return jdbcUrl;
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + "ApplicationName=data-bridge%2F" + BuildInfo.VERSION;
    }

    public static JdbcSource wrap(Connection c, DriverHints hints) {
        try { c.setReadOnly(true); } catch (SQLException ignored) {}
        ServerVersion v;
        try { v = detectServerVersion(c, hints); } catch (Exception e) { v = ServerVersion.UNKNOWN; }
        return new JdbcSource(c, hints, v);
    }

    public Connection connection() { return connection; }
    public DriverHints hints() { return hints; }

    @Override public String type() { return "jdbc"; }
    @Override public String driverWireName() { return hints.wireName(); }

    @Override
    public List<String> listTables(String schema) {
        return listTables(schema, false);
    }

    @Override
    public List<String> listTables(String schema, boolean includeViews) {
        String[] types = includeViews
            ? new String[]{"TABLE", "VIEW", "MATERIALIZED VIEW"}
            : new String[]{"TABLE"};
        List<String> out = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData()
                .getTables(connection.getCatalog(), schema, "%", types)) {
            while (rs.next()) out.add(rs.getString("TABLE_NAME"));
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "listTables failed: " + e.getMessage(), null, e);
        }
        return out;
    }

    @Override
    public List<Column> columns(String schema, String table) {
        List<String> pk = primaryKey(schema, table);
        List<Column> out = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData()
                .getColumns(connection.getCatalog(), schema, table, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                int jdbcType = rs.getInt("DATA_TYPE");
                String typeName = rs.getString("TYPE_NAME");
                int colSize = rs.getInt("COLUMN_SIZE");
                Integer maxLen = (jdbcType == Types.VARCHAR
                    || jdbcType == Types.NVARCHAR
                    || jdbcType == Types.CHAR
                    || jdbcType == Types.NCHAR) ? colSize : null;
                Integer precision = (jdbcType == Types.NUMERIC
                    || jdbcType == Types.DECIMAL
                    || jdbcType == Types.BIGINT
                    || jdbcType == Types.INTEGER
                    || jdbcType == Types.SMALLINT) ? colSize : null;
                Integer scale = rs.getObject("DECIMAL_DIGITS") == null
                    ? null : rs.getInt("DECIMAL_DIGITS");
                String defaultValue = rs.getString("COLUMN_DEF");
                String remarks = rs.getString("REMARKS");
                int ord = rs.getInt("ORDINAL_POSITION");
                boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                boolean isAutoincrement = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
                boolean isGenerated = "YES".equalsIgnoreCase(rs.getString("IS_GENERATEDCOLUMN"));
                String generationExpr = rs.getString("COLUMN_DEF");

                String sqlType = TypeNormalization.toSqlType(
                    jdbcType, typeName, maxLen, precision, scale);

                Column.Generated gen = new Column.Generated(
                    isAutoincrement, isGenerated && !isAutoincrement,
                    isGenerated && !isAutoincrement ? generationExpr : null);

                out.add(new Column(
                    name, ord, sqlType, jdbcType, nullable, pk.contains(name),
                    maxLen, precision, scale,
                    defaultValue, remarks,
                    gen, null, null
                ));
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "columns failed: " + e.getMessage(), null, e);
        }
        return out;
    }

    @Override
    public List<String> primaryKey(String schema, String table) {
        String key = (schema == null ? "" : schema) + "." + table;
        List<String> cached = pkCache.get(key);
        if (cached != null) return cached;

        List<String> out = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData()
                .getPrimaryKeys(connection.getCatalog(), schema, table)) {
            TreeMap<Short, String> ordered = new TreeMap<>();
            while (rs.next()) ordered.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            out.addAll(ordered.values());
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "primaryKey failed: " + e.getMessage(), null, e);
        }
        pkCache.put(key, List.copyOf(out));
        return pkCache.get(key);
    }

    @Override
    public List<ForeignKey> foreignKeys(String schema, String table) {
        LinkedHashMap<String, FkBuilder> grouped = new LinkedHashMap<>();
        try (ResultSet rs = connection.getMetaData()
                .getImportedKeys(connection.getCatalog(), schema, table)) {
            while (rs.next()) {
                String name = rs.getString("FK_NAME");
                String pkSchema = rs.getString("PKTABLE_SCHEM");
                String pkTable = rs.getString("PKTABLE_NAME");
                String onUpdate = ruleName(rs.getShort("UPDATE_RULE"));
                String onDelete = ruleName(rs.getShort("DELETE_RULE"));
                String fkCol = rs.getString("FKCOLUMN_NAME");
                String pkCol = rs.getString("PKCOLUMN_NAME");
                FkBuilder b = grouped.computeIfAbsent(name,
                    k -> new FkBuilder(name, pkSchema, pkTable, onUpdate, onDelete));
                b.fkColumns.add(fkCol);
                b.refColumns.add(pkCol);
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "foreignKeys failed: " + e.getMessage(), null, e);
        }
        List<ForeignKey> out = new ArrayList<>();
        for (FkBuilder b : grouped.values()) {
            List<ForeignKey.RefColumn> refCols = oneDeepColumns(b.refSchema, b.refTable);
            out.add(new ForeignKey(b.name, List.copyOf(b.fkColumns),
                b.refSchema, b.refTable, List.copyOf(b.refColumns),
                b.onUpdate, b.onDelete, refCols));
        }
        return out;
    }

    private List<ForeignKey.RefColumn> oneDeepColumns(String schema, String table) {
        List<ForeignKey.RefColumn> out = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData()
                .getColumns(connection.getCatalog(), schema, table, "%")) {
            while (rs.next()) {
                int jdbcType = rs.getInt("DATA_TYPE");
                String typeName = rs.getString("TYPE_NAME");
                int colSize = rs.getInt("COLUMN_SIZE");
                Integer maxLen = (jdbcType == Types.VARCHAR
                    || jdbcType == Types.NVARCHAR
                    || jdbcType == Types.CHAR
                    || jdbcType == Types.NCHAR) ? colSize : null;
                Integer precision = (jdbcType == Types.NUMERIC
                    || jdbcType == Types.DECIMAL) ? colSize : null;
                Integer scale = rs.getObject("DECIMAL_DIGITS") == null ? null : rs.getInt("DECIMAL_DIGITS");
                String sqlType = TypeNormalization.toSqlType(jdbcType, typeName, maxLen, precision, scale);
                boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                out.add(new ForeignKey.RefColumn(rs.getString("COLUMN_NAME"), sqlType, nullable));
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "oneDeepColumns failed: " + e.getMessage(), null, e);
        }
        return out;
    }

    private static String ruleName(short rule) {
        return switch (rule) {
            case DatabaseMetaData.importedKeyCascade -> "CASCADE";
            case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
            case DatabaseMetaData.importedKeySetNull -> "SET NULL";
            case DatabaseMetaData.importedKeyNoAction -> "NO ACTION";
            case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
            default -> "UNKNOWN";
        };
    }

    private static final class FkBuilder {
        final String name;
        final String refSchema;
        final String refTable;
        final String onUpdate;
        final String onDelete;
        final List<String> fkColumns = new ArrayList<>();
        final List<String> refColumns = new ArrayList<>();

        FkBuilder(String name, String refSchema, String refTable, String onUpdate, String onDelete) {
            this.name = name;
            this.refSchema = refSchema;
            this.refTable = refTable;
            this.onUpdate = onUpdate;
            this.onDelete = onDelete;
        }
    }

    @Override
    public List<Index> indexes(String schema, String table) {
        Map<String, IndexBuilder> grouped = new LinkedHashMap<>();
        try (ResultSet rs = connection.getMetaData()
                .getIndexInfo(connection.getCatalog(), schema, table, false, false)) {
            while (rs.next()) {
                String name = rs.getString("INDEX_NAME");
                if (name == null) continue;
                String col = rs.getString("COLUMN_NAME");
                if (col == null) continue;
                String ascDesc = rs.getString("ASC_OR_DESC");
                boolean asc = ascDesc == null || "A".equalsIgnoreCase(ascDesc);
                boolean nonUnique = rs.getBoolean("NON_UNIQUE");
                IndexBuilder b = grouped.computeIfAbsent(name,
                    k -> new IndexBuilder(name, !nonUnique));
                b.columns.add(col);
                b.ordinalAsc.add(asc);
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "indexes failed: " + e.getMessage(), null, e);
        }
        Map<String, PgIndexAugment> augments = (hints == DriverHints.PG)
            ? pgIndexAugments(schema, table) : Collections.emptyMap();
        List<String> pk = primaryKey(schema, table);
        List<Index> out = new ArrayList<>();
        for (IndexBuilder b : grouped.values()) {
            boolean primary = !pk.isEmpty() && pk.equals(b.columns);
            PgIndexAugment a = augments.get(b.name);
            out.add(new Index(b.name, List.copyOf(b.columns), List.copyOf(b.ordinalAsc),
                b.unique, primary, a != null ? a.method() : null, a != null ? a.where() : null));
        }
        return out;
    }

    private Map<String, PgIndexAugment> pgIndexAugments(String schema, String table) {
        Map<String, PgIndexAugment> out = new HashMap<>();
        String sql = """
            SELECT i.indexname, am.amname AS method,
                   pg_get_expr(ix.indpred, ix.indrelid) AS where_clause
              FROM pg_indexes i
              JOIN pg_class c   ON c.relname = i.indexname
              JOIN pg_index ix  ON ix.indexrelid = c.oid
              JOIN pg_class tc  ON tc.oid = ix.indrelid
              JOIN pg_namespace n ON n.oid = tc.relnamespace
              JOIN pg_am am     ON am.oid = c.relam
             WHERE n.nspname = ? AND tc.relname = ?
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString("indexname"),
                        new PgIndexAugment(rs.getString("method"), rs.getString("where_clause")));
                }
            }
        } catch (SQLException ignored) { /* no augment, return what we have */ }
        return out;
    }

    private static final class IndexBuilder {
        final String name;
        final boolean unique;
        final List<String> columns = new ArrayList<>();
        final List<Boolean> ordinalAsc = new ArrayList<>();

        IndexBuilder(String name, boolean unique) {
            this.name = name;
            this.unique = unique;
        }
    }

    private record PgIndexAugment(String method, String where) {}

    @Override
    public List<UniqueConstraint> uniqueConstraints(String schema, String table) {
        if (hints != DriverHints.PG) return List.of();
        String sql = """
            SELECT tc.constraint_name, kcu.column_name, kcu.ordinal_position
              FROM information_schema.table_constraints tc
              JOIN information_schema.key_column_usage kcu
                ON tc.constraint_name = kcu.constraint_name
               AND tc.table_schema    = kcu.table_schema
             WHERE tc.constraint_type = 'UNIQUE'
               AND tc.table_schema    = ?
               AND tc.table_name      = ?
             ORDER BY tc.constraint_name, kcu.ordinal_position
            """;
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    grouped.computeIfAbsent(rs.getString("constraint_name"), k -> new ArrayList<>())
                        .add(rs.getString("column_name"));
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "uniqueConstraints failed: " + e.getMessage(), null, e);
        }
        List<UniqueConstraint> out = new ArrayList<>();
        grouped.forEach((name, cols) -> out.add(new UniqueConstraint(name, List.copyOf(cols))));
        return out;
    }

    @Override
    public List<CheckConstraint> checkConstraints(String schema, String table) {
        if (hints != DriverHints.PG) return List.of();
        String sql = """
            SELECT cc.constraint_name, cc.check_clause
              FROM information_schema.table_constraints tc
              JOIN information_schema.check_constraints cc
                ON tc.constraint_name  = cc.constraint_name
               AND tc.constraint_schema = cc.constraint_schema
             WHERE tc.constraint_type = 'CHECK'
               AND tc.table_schema    = ?
               AND tc.table_name      = ?
               AND cc.constraint_name NOT LIKE '%_not_null'
             ORDER BY cc.constraint_name
            """;
        List<CheckConstraint> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new CheckConstraint(rs.getString("constraint_name"), rs.getString("check_clause")));
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "checkConstraints failed: " + e.getMessage(), null, e);
        }
        return out;
    }

    private PgClassInfo loadPgClassInfo(String schema, String table) {
        String key = schema + "." + table;
        PgClassInfo cached = pgClassCache.get(key);
        if (cached != null) return cached;

        String sql = """
            SELECT pg_get_userbyid(c.relowner) AS owner,
                   c.reltuples::bigint AS approx_rows,
                   CASE WHEN c.relkind IN ('v','m')
                        THEN pg_get_viewdef(c.oid, true) ELSE NULL END AS view_def,
                   pt.partstrat AS partition_strategy,
                   pg_get_partkeydef(c.oid) AS partition_key_def
              FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
              LEFT JOIN pg_partitioned_table pt ON pt.partrelid = c.oid
             WHERE n.nspname = ? AND c.relname = ?
            """;
        PgClassInfo result = null;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Long approx = rs.getObject("approx_rows") == null ? null : rs.getLong("approx_rows");
                    String partStrat = rs.getString("partition_strategy");
                    List<String> partKey = List.of();
                    if (partStrat != null) {
                        String pkdef = rs.getString("partition_key_def");
                        if (pkdef != null) {
                            int open = pkdef.indexOf('('), close = pkdef.lastIndexOf(')');
                            if (open >= 0 && close > open) {
                                String inner = pkdef.substring(open + 1, close);
                                partKey = Arrays.stream(inner.split(",")).map(String::trim).toList();
                            }
                        }
                    }
                    result = new PgClassInfo(rs.getString("owner"), approx,
                        rs.getString("view_def"), partStrat, partKey);
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "pg_class augment failed: " + e.getMessage(), null, e);
        }
        if (result == null) result = new PgClassInfo(null, null, null, null, List.of());
        pgClassCache.put(key, result);
        return result;
    }

    private Map<String, PgStatsEntry> loadPgStats(String schema, String table) {
        String key = schema + "." + table;
        Map<String, PgStatsEntry> cached = pgStatsCache.get(key);
        if (cached != null) return cached;

        Map<String, PgStatsEntry> stats = new HashMap<>();
        String sql = """
            SELECT attname, n_distinct, null_frac,
                   most_common_vals::text AS mcv,
                   most_common_freqs::text AS mcf,
                   correlation
              FROM pg_stats
             WHERE schemaname = ? AND tablename = ?
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stats.put(rs.getString("attname"), new PgStatsEntry(
                        rs.getDouble("n_distinct"), rs.getDouble("null_frac"),
                        rs.getString("mcv"), rs.getString("mcf"),
                        rs.getDouble("correlation")));
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "pg_stats load failed: " + e.getMessage(), null, e);
        }
        pgStatsCache.put(key, stats);
        return stats;
    }

    @Override
    public TableInfo tableInfo(String schema, String table) {
        String type = "TABLE";
        String comment = null;
        String owner = null;
        Long approxRows = null;
        String viewDef = null;

        // Determine type via DatabaseMetaData
        try (ResultSet rs = connection.getMetaData()
                .getTables(connection.getCatalog(), schema, table, null)) {
            if (rs.next()) {
                String t = rs.getString("TABLE_TYPE");
                type = switch (t == null ? "" : t.toUpperCase()) {
                    case "VIEW" -> "VIEW";
                    case "MATERIALIZED VIEW" -> "MATERIALIZED_VIEW";
                    case "PARTITIONED TABLE", "FOREIGN TABLE" -> "TABLE";
                    default -> "TABLE";
                };
                comment = rs.getString("REMARKS");
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "tableInfo (type) failed: " + e.getMessage(), null, e);
        }

        if (hints == DriverHints.PG) {
            PgClassInfo info = loadPgClassInfo(schema, table);
            owner = info.owner();
            approxRows = info.approxRows();
            viewDef = info.viewDef();
            if (info.partitionStrategy() != null) type = "PARTITIONED_TABLE";
        }

        return new TableInfo(type, comment, owner, approxRows, viewDef);
    }

    @Override
    public Sample sample(String schema, String table, int limit) {
        if (limit <= 0) return new Sample(0, List.of());
        String fqn = fullyQualified(schema, table);
        String sql = "SELECT * FROM " + fqn + " LIMIT " + limit;
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            while (rs.next()) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= n; i++) {
                    String name = md.getColumnLabel(i);
                    int type = md.getColumnType(i);
                    Object value = readColumnValue(rs, i, type);
                    row.put(name, value);
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "sample failed: " + e.getMessage(), null, e);
        }
        return new Sample(rows.size(), rows);
    }

    private Object readColumnValue(ResultSet rs, int idx, int jdbcType) throws SQLException {
        if (jdbcType == Types.BLOB
            || jdbcType == Types.BINARY
            || jdbcType == Types.VARBINARY
            || jdbcType == Types.LONGVARBINARY) {
            byte[] bytes = rs.getBytes(idx);
            if (bytes == null) return null;
            int previewLen = Math.min(64, bytes.length);
            byte[] preview = Arrays.copyOf(bytes, previewLen);
            return Map.of(
                "_blob", true,
                "size", bytes.length,
                "preview", Base64.getEncoder().encodeToString(preview)
            );
        }
        Object obj = rs.getObject(idx);
        if (obj instanceof Timestamp ts) return ts.toInstant().toString();
        if (obj instanceof Date d) return d.toLocalDate().toString();
        if (obj instanceof Time t) return t.toLocalTime().toString();
        return obj;
    }

    private String quoteIdent(String ident) {
        return switch (hints) {
            case PG, ORACLE -> "\"" + ident.replace("\"", "\"\"") + "\"";
            case MSSQL -> "[" + ident.replace("]", "]]") + "]";
            case MYSQL -> "`" + ident.replace("`", "``") + "`";
            case FIREBIRD -> "\"" + ident.replace("\"", "\"\"") + "\"";
        };
    }

    /**
     * Build a fully-qualified table reference, omitting the schema entirely when null/blank.
     * Single-schema drivers (Firebird, MySQL) typically pass schema=null — we just quote the table.
     */
    private String fullyQualified(String schema, String table) {
        return (schema == null || schema.isBlank())
            ? quoteIdent(table)
            : quoteIdent(schema) + "." + quoteIdent(table);
    }

    @Override
    public List<ColumnStats> columnStats(String schema, String table) {
        if (hints != DriverHints.PG) return List.of();
        Map<String, PgStatsEntry> stats = loadPgStats(schema, table);
        List<ColumnStats> out = new ArrayList<>();
        for (Map.Entry<String, PgStatsEntry> e : stats.entrySet()) {
            PgStatsEntry s = e.getValue();
            Long nDist = (s.nDistinct() == 0.0) ? null : (long) s.nDistinct();
            Double nullFrac = s.nullFrac();
            List<String> mcv = parseTextArray(s.mcvText());
            List<Double> mcf = parseFloatArray(s.mcfText());
            Double corr = s.correlation();
            out.add(new ColumnStats(e.getKey(), nDist, nullFrac, mcv, mcf, corr));
        }
        return out;
    }

    /**
     * Parse a Postgres text array literal like {@code {"foo","bar","baz"}}. Returns empty list for
     * anything malformed: null, length &lt; 2, missing braces. Previously this would crash with
     * {@code StringIndexOutOfBoundsException} on length-1 strings (e.g., a single brace).
     */
    static List<String> parseTextArray(String pgArray) {
        if (pgArray == null || pgArray.length() < 2
            || !pgArray.startsWith("{") || !pgArray.endsWith("}")) {
            return List.of();
        }
        String inner = pgArray.substring(1, pgArray.length() - 1);
        if (inner.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : inner.split(",")) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            out.add(trimmed);
        }
        return out;
    }

    static List<Double> parseFloatArray(String pgArray) {
        if (pgArray == null || pgArray.length() < 2
            || !pgArray.startsWith("{") || !pgArray.endsWith("}")) {
            return List.of();
        }
        String inner = pgArray.substring(1, pgArray.length() - 1);
        if (inner.isEmpty()) return List.of();
        List<Double> out = new ArrayList<>();
        for (String part : inner.split(",")) {
            try {
                out.add(Double.parseDouble(part.trim()));
            } catch (NumberFormatException e) { /* skip */ }
        }
        return out;
    }
    @Override
    public Partitioning partitioning(String schema, String table) {
        if (hints != DriverHints.PG) {
            return new Partitioning(false, null, List.of(), null, List.of());
        }
        PgClassInfo info = loadPgClassInfo(schema, table);
        if (info.partitionStrategy() == null) {
            return new Partitioning(false, null, List.of(), null, List.of());
        }
        String strategy = switch (info.partitionStrategy()) {
            case "r" -> "RANGE"; case "l" -> "LIST"; case "h" -> "HASH";
            default -> info.partitionStrategy().toUpperCase();
        };
        return new Partitioning(true, strategy, info.partitionKey(), null, List.of());
    }
    /**
     * JDBC types whose {@code COUNT DISTINCT} is either prohibitively expensive or impossible/meaningless.
     * These are always omitted from the per-column cardinality result; pipeline post-processing surfaces
     * the omissions as warnings on {@code Metadata.warnings}.
     */
    private static boolean cardinalitySkippable(int jdbcType) {
        return jdbcType == Types.BLOB
            || jdbcType == Types.CLOB
            || jdbcType == Types.NCLOB
            || jdbcType == Types.BINARY           // PG BYTEA, MSSQL VARBINARY, etc.
            || jdbcType == Types.VARBINARY
            || jdbcType == Types.LONGVARBINARY
            || jdbcType == Types.LONGVARCHAR
            || jdbcType == Types.LONGNVARCHAR
            || jdbcType == Types.SQLXML;
    }

    @Override
    public Cardinality cardinality(String schema, String table, List<Column> columns,
                                    digital.singularidade.databridge.source.CardinalityMode mode) {
        return switch (mode) {
            case SKIP        -> new Cardinality(0L, List.of());
            case APPROXIMATE -> cardinalityApproximate(schema, table, columns);
            case EXACT       -> cardinalityExact(schema, table, columns);
        };
    }

    private Cardinality cardinalityExact(String schema, String table, List<Column> columns) {
        String fqn = fullyQualified(schema, table);
        long total;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + fqn)) {
            rs.next();
            total = rs.getLong(1);
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "cardinality count(*) failed: " + e.getMessage(), null, e);
        }

        List<Cardinality.PerColumn> per = new ArrayList<>();
        for (Column c : columns) {
            if (cardinalitySkippable(c.jdbcType())) continue;
            String colQ = quoteIdent(c.name());
            long dist;
            long nullCount;
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT COUNT(DISTINCT " + colQ + "), SUM(CASE WHEN " + colQ
                     + " IS NULL THEN 1 ELSE 0 END) FROM " + fqn)) {
                rs.next();
                dist = rs.getLong(1);
                nullCount = rs.getLong(2);
            } catch (SQLException e) {
                throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                    "cardinality of " + c.name() + " failed: " + e.getMessage(), null, e);
            }
            per.add(new Cardinality.PerColumn(c.name(), dist, nullCount));
        }
        return new Cardinality(total, per);
    }

    private Cardinality cardinalityApproximate(String schema, String table, List<Column> columns) {
        return switch (hints) {
            case PG       -> cardinalityApproximatePg(schema, table, columns);
            case FIREBIRD -> cardinalityApproximateFirebird(table, columns);
            default       -> new Cardinality(0L, List.of());   // Oracle/MSSQL/MySQL: pipeline emits warning
        };
    }

    private Cardinality cardinalityApproximatePg(String schema, String table, List<Column> columns) {
        PgClassInfo cls = loadPgClassInfo(schema, table);
        long totalRows = cls.approxRows() == null ? 0L : cls.approxRows();

        Map<String, PgStatsEntry> stats = loadPgStats(schema, table);
        List<Cardinality.PerColumn> per = new ArrayList<>();
        for (Column c : columns) {
            if (cardinalitySkippable(c.jdbcType())) continue;
            PgStatsEntry s = stats.get(c.name());
            if (s == null) continue;   // no stats yet (table never ANALYZE'd, or column added since)
            double n = s.nDistinct();
            long distinct = (n < 0) ? Math.max(0L, Math.round(-n * totalRows))
                                    : Math.max(0L, Math.round(n));
            long nulls = Math.max(0L, Math.round(s.nullFrac() * totalRows));
            per.add(new Cardinality.PerColumn(c.name(), distinct, nulls));
        }
        return new Cardinality(totalRows, per);
    }

    /**
     * Firebird approximate cardinality from {@code RDB$INDICES.RDB$STATISTICS}.
     *
     * <p>Firebird's {@code RDB$STATISTICS} on an index is {@code 1.0 / NDV} where NDV is the number
     * of distinct values that index covers. So for a unique index (PK), {@code 1/selectivity}
     * is the approximate row count of the table; for a single-column non-unique index,
     * {@code 1/selectivity} is the approximate distinct count of that column.
     *
     * <p>Limitations:
     * <ul>
     *   <li>Per-column distinct estimates are only available for columns that are the SOLE segment
     *       of a single-column index. Non-indexed columns are silently omitted (the pipeline turns
     *       this into a per-column warning).</li>
     *   <li>Tables without a PK index get {@code totalRows = 0}; the pipeline surfaces a warning.</li>
     *   <li>Statistics are only as fresh as the last {@code SET STATISTICS INDEX} run.</li>
     * </ul>
     *
     * <p>Firebird identifiers are stored uppercase (and trimmed by the engine), so we upper-case
     * the table name before comparing to {@code RDB$RELATIONS.RDB$RELATION_NAME}.
     */
    private Cardinality cardinalityApproximateFirebird(String table, List<Column> columns) {
        String tableUpper = table.toUpperCase();

        long totalRows = 0L;
        String totalSql = """
            SELECT 1.0 / NULLIF(i.RDB$STATISTICS, 0) AS approx_rows
              FROM RDB$INDICES i
              JOIN RDB$RELATION_CONSTRAINTS rc
                ON rc.RDB$INDEX_NAME = i.RDB$INDEX_NAME
             WHERE TRIM(rc.RDB$RELATION_NAME) = ?
               AND TRIM(rc.RDB$CONSTRAINT_TYPE) = 'PRIMARY KEY'
            """;
        try (PreparedStatement ps = connection.prepareStatement(totalSql)) {
            ps.setString(1, tableUpper);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    double approx = rs.getDouble(1);
                    if (!rs.wasNull() && Double.isFinite(approx)) {
                        totalRows = Math.max(0L, Math.round(approx));
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "Firebird approximate cardinality (PK index) failed: " + e.getMessage(), null, e);
        }

        // Map column-name -> approx distinct (from single-segment indexes only)
        Map<String, Long> distinctByCol = new HashMap<>();
        String distinctSql = """
            SELECT TRIM(seg.RDB$FIELD_NAME) AS col_name,
                   1.0 / NULLIF(i.RDB$STATISTICS, 0) AS approx_distinct
              FROM RDB$INDEX_SEGMENTS seg
              JOIN RDB$INDICES i ON i.RDB$INDEX_NAME = seg.RDB$INDEX_NAME
             WHERE TRIM(i.RDB$RELATION_NAME) = ?
               AND i.RDB$SEGMENT_COUNT = 1
               AND COALESCE(i.RDB$INDEX_INACTIVE, 0) = 0
            """;
        try (PreparedStatement ps = connection.prepareStatement(distinctSql)) {
            ps.setString(1, tableUpper);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("col_name");
                    double approx = rs.getDouble("approx_distinct");
                    if (rs.wasNull() || !Double.isFinite(approx)) continue;
                    distinctByCol.put(name, Math.max(0L, Math.round(approx)));
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "Firebird approximate cardinality (segment indices) failed: " + e.getMessage(), null, e);
        }

        // Firebird stores identifiers UPPERCASE; columns coming from DatabaseMetaData are also
        // typically uppercase, but normalize defensively when looking up.
        List<Cardinality.PerColumn> per = new ArrayList<>();
        for (Column c : columns) {
            if (cardinalitySkippable(c.jdbcType())) continue;
            Long distinct = distinctByCol.get(c.name());
            if (distinct == null) distinct = distinctByCol.get(c.name().toUpperCase());
            if (distinct == null) continue;   // column not indexed → no estimate available
            // Firebird does not expose null fraction in RDB$ catalogs; null count not available approximately.
            per.add(new Cardinality.PerColumn(c.name(), distinct, 0L));
        }
        return new Cardinality(totalRows, per);
    }

    // ================================================================================
    //   QueryExecutor — defense-in-depth read-only by default; --writable opts in
    //   for DML. DDL is rejected unconditionally. Multi-statement is rejected.
    // ================================================================================

    private static final Pattern MULTI_STMT_KEYWORD = Pattern.compile(
        ";\\s*(SELECT|INSERT|UPDATE|DELETE|MERGE|UPSERT|DROP|CREATE|ALTER|TRUNCATE|"
        + "GRANT|REVOKE|VACUUM|ANALYZE|EXPLAIN|WITH|BEGIN|COMMIT|ROLLBACK|CALL)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern DDL_LEADING = Pattern.compile(
        "^\\s*(DROP|CREATE|ALTER|TRUNCATE|GRANT|REVOKE)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern READONLY_OK_LEADING = Pattern.compile(
        "^\\s*(SELECT|WITH|EXPLAIN|SHOW)\\b",
        Pattern.CASE_INSENSITIVE);

    @Override
    public QueryResult executeQuery(String sql, int limit, int timeoutSec, boolean writable) {
        if (sql == null || sql.isBlank()) {
            throw new DataBridgeException(ErrorCodes.INVALID_ARGS,
                "sql is required", null);
        }
        if (limit <= 0) {
            throw new DataBridgeException(ErrorCodes.INVALID_ARGS,
                "limit must be > 0 (got " + limit + ")",
                "pass --limit 100 (or any positive value)");
        }
        // Layer 2a: reject multi-statement attempts (defense against `; DROP TABLE` injection patterns)
        if (MULTI_STMT_KEYWORD.matcher(sql).find()) {
            throw new DataBridgeException(ErrorCodes.INVALID_ARGS,
                "multi-statement queries are not allowed",
                "submit one statement per call");
        }
        // Layer 2b: DDL is forbidden ALWAYS, even with --writable (use Liquibase/Flyway for schema changes)
        if (DDL_LEADING.matcher(sql).find()) {
            throw new DataBridgeException(ErrorCodes.INVALID_ARGS,
                "DDL is not allowed (use Liquibase/Flyway for schema changes)",
                null);
        }
        // Layer 2c: when not writable, only allow SELECT-shaped statements
        if (!writable && !READONLY_OK_LEADING.matcher(sql).find()) {
            throw new DataBridgeException(ErrorCodes.INVALID_ARGS,
                "non-SELECT statement requires --writable",
                "pass --writable to allow INSERT/UPDATE/DELETE; data-bridge never permits DDL");
        }

        // Layer 1: read-only flag on the connection (Postgres etc. enforce; some drivers ignore)
        boolean wasReadOnly = false;
        try { wasReadOnly = connection.isReadOnly(); } catch (SQLException ignored) {}

        long t0 = System.nanoTime();
        try {
            if (writable && wasReadOnly) {
                // End any explicit read-only transaction started in open() (PG `BEGIN READ ONLY`)
                // before flipping the read-only flag — PG rejects setReadOnly(false) mid-transaction.
                if (hints == DriverHints.PG) {
                    try (Statement st = connection.createStatement()) {
                        st.execute("COMMIT");
                    } catch (SQLException ignored) { /* no txn open or already closed */ }
                }
                connection.setReadOnly(false);
            }
            try (Statement st = connection.createStatement()) {
                if (timeoutSec > 0) st.setQueryTimeout(timeoutSec);
                // Ask for one more than the limit so we can distinguish "exactly N rows"
                // from "more than N rows available" — the latter sets truncated=true.
                st.setMaxRows(limit + 1);
                boolean hasResultSet = st.execute(sql);
                long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
                if (hasResultSet) {
                    try (ResultSet rs = st.getResultSet()) {
                        return collectRows(rs, limit, elapsedMs);
                    }
                } else {
                    long updateCount = st.getUpdateCount();
                    return QueryResult.update(updateCount, elapsedMs);
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "query failed: " + e.getMessage(),
                "verify the SQL is valid for the target driver", e);
        } finally {
            if (writable && wasReadOnly) {
                try { connection.setReadOnly(true); } catch (SQLException ignored) {}
            }
        }
    }

    private QueryResult collectRows(ResultSet rs, int limit, long elapsedMs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        List<QueryResult.ColumnInfo> columns = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            int jdbcType = md.getColumnType(i);
            String typeName = md.getColumnTypeName(i);
            int precision = md.getPrecision(i);
            int scale = md.getScale(i);
            String sqlType = TypeNormalization.toSqlType(jdbcType, typeName,
                precision > 0 ? precision : null, precision > 0 ? precision : null,
                scale >= 0 ? scale : null);
            columns.add(new QueryResult.ColumnInfo(md.getColumnLabel(i), sqlType, jdbcType));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() >= limit) { truncated = true; break; }
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= n; i++) {
                row.put(columns.get(i - 1).name(),
                        readColumnValue(rs, i, md.getColumnType(i)));
            }
            rows.add(row);
        }
        return QueryResult.query(rows.size(), truncated, columns, rows, elapsedMs);
    }

    @Override
    public void close() {
        try { connection.close(); } catch (SQLException ignored) {}
    }
}
