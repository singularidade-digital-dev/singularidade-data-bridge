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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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

    // ===== stubs for subsequent tasks =====
    @Override public TableInfo tableInfo(String schema, String table) { throw new UnsupportedOperationException("Task 19"); }
    @Override public List<Column> columns(String schema, String table) { throw new UnsupportedOperationException("Task 10"); }
    @Override public List<String> primaryKey(String schema, String table) { throw new UnsupportedOperationException("Task 11"); }
    @Override public List<ForeignKey> foreignKeys(String schema, String table) { throw new UnsupportedOperationException("Task 12"); }
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
