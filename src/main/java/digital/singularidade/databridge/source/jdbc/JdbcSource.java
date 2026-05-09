package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.CheckConstraint;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.output.ColumnStats;
import digital.singularidade.databridge.output.ForeignKey;
import digital.singularidade.databridge.output.Index;
import digital.singularidade.databridge.output.Partitioning;
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

public final class JdbcSource implements Source {

    private final Connection connection;
    private final DriverHints hints;

    private JdbcSource(Connection connection, DriverHints hints) {
        this.connection = connection;
        this.hints = hints;
    }

    public static JdbcSource open(String jdbcUrl) {
        DriverHints hints = DriverHints.fromUrl(jdbcUrl);
        try {
            Connection c = DriverManager.getConnection(jdbcUrl);
            c.setReadOnly(true);
            return new JdbcSource(c, hints);
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.CONNECTION_FAILED,
                "Failed to connect: " + e.getMessage(),
                "Check JDBC URL, credentials, network, and TLS settings", e);
        }
    }

    Connection connection() { return connection; }
    DriverHints hints() { return hints; }

    @Override public String type() { return "jdbc"; }
    @Override public String driverWireName() { return hints.wireName(); }

    @Override
    public List<String> listTables(String schema) {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData()
                .getTables(connection.getCatalog(), schema, "%", new String[]{"TABLE"})) {
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
        return out;
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

    // ===== stubs for subsequent tasks =====
    @Override public TableInfo tableInfo(String schema, String table) { throw new UnsupportedOperationException("Task 19"); }

    @Override
    public Sample sample(String schema, String table, int limit) {
        String fqn = quoteIdent(schema) + "." + quoteIdent(table);
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

    @Override
    public List<ColumnStats> columnStats(String schema, String table) {
        if (hints != DriverHints.PG) return List.of();
        String sql = """
            SELECT attname, n_distinct, null_frac,
                   most_common_vals::text AS mcv,
                   most_common_freqs::text AS mcf,
                   correlation
              FROM pg_stats
             WHERE schemaname = ? AND tablename = ?
            """;
        List<ColumnStats> out = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("attname");
                    Long nDist = rs.getObject("n_distinct") == null ? null : rs.getLong("n_distinct");
                    Double nullFrac = rs.getObject("null_frac") == null ? null : rs.getDouble("null_frac");
                    List<String> mcv = parseTextArray(rs.getString("mcv"));
                    List<Double> mcf = parseFloatArray(rs.getString("mcf"));
                    Double corr = rs.getObject("correlation") == null ? null : rs.getDouble("correlation");
                    out.add(new ColumnStats(name, nDist, nullFrac, mcv, mcf, corr));
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "columnStats failed: " + e.getMessage(), null, e);
        }
        return out;
    }

    private static List<String> parseTextArray(String pgArray) {
        if (pgArray == null || pgArray.isEmpty() || pgArray.equals("{}")) return List.of();
        String inner = pgArray.substring(1, pgArray.length() - 1);
        if (inner.isEmpty()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : inner.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                trimmed = trimmed.substring(1, trimmed.length() - 1);
            }
            out.add(trimmed);
        }
        return out;
    }

    private static List<Double> parseFloatArray(String pgArray) {
        if (pgArray == null || pgArray.isEmpty() || pgArray.equals("{}")) return List.of();
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
    @Override public Partitioning partitioning(String schema, String table) { throw new UnsupportedOperationException("Task 17"); }
    @Override public Cardinality cardinality(String schema, String table, List<Column> columns) { throw new UnsupportedOperationException("Task 18"); }

    @Override
    public void close() {
        try { connection.close(); } catch (SQLException ignored) {}
    }
}
