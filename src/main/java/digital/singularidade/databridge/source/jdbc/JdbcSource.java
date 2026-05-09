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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    // ===== stubs for subsequent tasks =====
    @Override public TableInfo tableInfo(String schema, String table) { throw new UnsupportedOperationException("Task 19"); }
    @Override public List<Index> indexes(String schema, String table) { throw new UnsupportedOperationException("Task 13"); }
    @Override public List<UniqueConstraint> uniqueConstraints(String schema, String table) { throw new UnsupportedOperationException("Task 14"); }
    @Override public List<CheckConstraint> checkConstraints(String schema, String table) { throw new UnsupportedOperationException("Task 14"); }
    @Override public Sample sample(String schema, String table, int limit) { throw new UnsupportedOperationException("Task 15"); }
    @Override public List<ColumnStats> columnStats(String schema, String table) { throw new UnsupportedOperationException("Task 16"); }
    @Override public Partitioning partitioning(String schema, String table) { throw new UnsupportedOperationException("Task 17"); }
    @Override public Cardinality cardinality(String schema, String table, List<Column> columns) { throw new UnsupportedOperationException("Task 18"); }

    @Override
    public void close() {
        try { connection.close(); } catch (SQLException ignored) {}
    }
}
