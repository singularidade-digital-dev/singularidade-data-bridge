package digital.singularidade.databridge.pipeline.ddl;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.source.jdbc.ServerVersion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class OracleDdlBuilder implements DdlBuilder {

    @Override
    public Optional<DdlScript> build(JdbcSource source, String schema, String table,
                                      String redactedUrl, boolean includeTriggers) {
        Connection c = source.connection();
        ServerVersion v = source.serverVersion();
        List<String> warnings = new ArrayList<>();
        List<String> included = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        StringBuilder body = new StringBuilder();

        // Determine object type: TABLE, VIEW, MATERIALIZED VIEW
        String objType = "TABLE";
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT OBJECT_TYPE FROM ALL_OBJECTS WHERE OWNER = ? AND OBJECT_NAME = ? "
          + "AND OBJECT_TYPE IN ('TABLE','VIEW','MATERIALIZED VIEW')")) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) objType = rs.getString(1);
            }
        } catch (SQLException e) {
            warnings.add("object type lookup failed: " + e.getMessage());
        }

        String getDdl = "SELECT DBMS_METADATA.GET_DDL(?, ?, ?) FROM DUAL";
        try (PreparedStatement ps = c.prepareStatement(getDdl)) {
            ps.setString(1, objType); ps.setString(2, table); ps.setString(3, schema);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    body.append(rs.getString(1).trim()).append(";\n");
                    included.add(objType.equals("TABLE") ? "CREATE TABLE (with constraints)" : "CREATE " + objType);
                }
            }
        } catch (SQLException e) {
            warnings.add("DBMS_METADATA.GET_DDL('" + objType + "') failed: " + e.getMessage()
                + " — may need SELECT_CATALOG_ROLE");
        }

        // Indexes
        String idxSql = "SELECT INDEX_NAME FROM ALL_INDEXES WHERE TABLE_OWNER = ? AND TABLE_NAME = ?";
        try (PreparedStatement ps = c.prepareStatement(idxSql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String idxName = rs.getString(1);
                    try (PreparedStatement ps2 = c.prepareStatement(
                        "SELECT DBMS_METADATA.GET_DDL('INDEX', ?, ?) FROM DUAL")) {
                        ps2.setString(1, idxName); ps2.setString(2, schema);
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            if (rs2.next()) body.append('\n').append(rs2.getString(1).trim()).append(";\n");
                        }
                    } catch (SQLException ignored) { /* skip individual */ }
                }
            }
            included.add("CREATE INDEX (each via DBMS_METADATA)");
        } catch (SQLException e) {
            warnings.add("index extraction failed: " + e.getMessage());
        }

        StringBuilder triggers = new StringBuilder();
        if (includeTriggers) {
            String tSql = "SELECT TRIGGER_NAME FROM ALL_TRIGGERS WHERE TABLE_OWNER = ? AND TABLE_NAME = ?";
            try (PreparedStatement ps = c.prepareStatement(tSql)) {
                ps.setString(1, schema); ps.setString(2, table);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try (PreparedStatement ps2 = c.prepareStatement(
                            "SELECT DBMS_METADATA.GET_DDL('TRIGGER', ?, ?) FROM DUAL")) {
                            ps2.setString(1, rs.getString(1)); ps2.setString(2, schema);
                            try (ResultSet rs2 = ps2.executeQuery()) {
                                if (rs2.next()) triggers.append(rs2.getString(1).trim()).append(";\n\n");
                            }
                        } catch (SQLException ignored) {}
                    }
                }
            } catch (SQLException e) {
                warnings.add("trigger extraction failed: " + e.getMessage());
            }
            included.add(triggers.length() == 0 ? "TRIGGERS (none)" : "TRIGGERS");
        } else {
            skipped.add("triggers (use --include-triggers)");
        }

        skipped.add("GRANT/REVOKE");
        skipped.add("RLS / VPD policies");

        String header = DdlScript.standardHeader("oracle", schema, table,
            redactedUrl, v.banner(), included, skipped);
        return Optional.of(new DdlScript(header, body.toString().stripTrailing(),
            triggers.toString().stripTrailing(), warnings));
    }
}
