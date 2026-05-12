package digital.singularidade.databridge.pipeline.ddl;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.source.jdbc.ServerVersion;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MysqlDdlBuilder implements DdlBuilder {

    @Override
    public Optional<DdlScript> build(JdbcSource source, String schema, String table,
                                      String redactedUrl, boolean includeTriggers) {
        Connection c = source.connection();
        ServerVersion v = source.serverVersion();
        List<String> warnings = new ArrayList<>();
        List<String> included = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        String fqn = quoteMysql(schema) + "." + quoteMysql(table);
        StringBuilder body = new StringBuilder();

        // Detect TABLE vs VIEW
        boolean isView = isView(c, schema, table);
        String showSql = isView ? "SHOW CREATE VIEW " + fqn : "SHOW CREATE TABLE " + fqn;

        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(showSql)) {
            if (rs.next()) {
                String ddl = rs.getString(2);   // column 1 is name, column 2 is the DDL
                body.append(ddl).append(";\n");
                included.add(isView ? "CREATE VIEW" : "CREATE TABLE (with all indexes/constraints/options)");
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "SHOW CREATE failed: " + e.getMessage(), null, e);
        }

        StringBuilder triggers = new StringBuilder();
        if (includeTriggers && !isView) {
            String trigSql = "SHOW TRIGGERS FROM " + quoteMysql(schema) + " WHERE `Table` = ?";
            try (var ps = c.prepareStatement(trigSql)) {
                ps.setString(1, table);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        // SHOW TRIGGERS columns: Trigger, Event, Table, Statement, Timing, ...
                        triggers.append("CREATE TRIGGER ").append(quoteMysql(rs.getString("Trigger"))).append('\n')
                            .append("  ").append(rs.getString("Timing")).append(' ')
                            .append(rs.getString("Event")).append(" ON ").append(fqn).append('\n')
                            .append("  FOR EACH ROW\n")
                            .append("  ").append(rs.getString("Statement")).append(";\n");
                    }
                }
            } catch (SQLException e) {
                warnings.add("trigger extraction failed: " + e.getMessage());
            }
            included.add(triggers.length() == 0 ? "TRIGGERS (none)" : "TRIGGERS");
        } else if (!isView) {
            skipped.add("triggers (use --include-triggers)");
        }

        skipped.add("GRANT/REVOKE");
        skipped.add("functions/procedures");

        String header = DdlScript.standardHeader("mysql", schema, table,
            redactedUrl, v.banner(), included, skipped);
        return Optional.of(new DdlScript(header, body.toString().stripTrailing(),
            triggers.toString().stripTrailing(), warnings));
    }

    private boolean isView(Connection c, String schema, String table) {
        String sql = "SELECT TABLE_TYPE FROM INFORMATION_SCHEMA.TABLES "
                  + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?";
        try (var ps = c.prepareStatement(sql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && "VIEW".equalsIgnoreCase(rs.getString(1));
            }
        } catch (SQLException e) {
            return false;
        }
    }

    private static String quoteMysql(String s) {
        return "`" + s.replace("`","``") + "`";
    }
}
