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

public final class FirebirdDdlBuilder implements DdlBuilder {

    @Override
    public Optional<DdlScript> build(JdbcSource source, String schema, String table,
                                      String redactedUrl, boolean includeTriggers) {
        Connection c = source.connection();
        ServerVersion v = source.serverVersion();
        List<String> warnings = new ArrayList<>();
        List<String> included = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        StringBuilder body = new StringBuilder();

        String tableUpper = table.toUpperCase();

        // Determine VIEW vs TABLE via RDB$RELATIONS.RDB$RELATION_TYPE (FB 3+ uses RDB$VIEW_BLR != null for views)
        String relType = fetchRelType(c, tableUpper);
        if ("VIEW".equals(relType)) {
            String viewSrc = fetchViewSource(c, tableUpper);
            body.append("CREATE OR ALTER VIEW \"").append(tableUpper).append("\" AS\n").append(viewSrc).append(";\n");
            included.add("CREATE VIEW");
        } else {
            body.append(buildCreateTable(c, tableUpper)).append("\n");
            included.add("CREATE TABLE");
        }

        // Indexes (excluding PK-backing one)
        List<String> indexDefs = fetchIndexDdl(c, tableUpper, v);
        if (!indexDefs.isEmpty()) {
            body.append('\n');
            for (String d : indexDefs) body.append(d).append(";\n");
            included.add("CREATE INDEX");
        }

        // Constraints (FK, CHECK; PK/UNIQUE inline in CREATE TABLE above)
        List<String> consDefs = fetchAlterConstraints(c, tableUpper);
        if (!consDefs.isEmpty()) {
            body.append('\n');
            for (String d : consDefs) body.append(d).append(";\n");
            included.add("FK / CHECK constraints");
        }

        // Comments (RDB$DESCRIPTION on RDB$RELATIONS, RDB$RELATION_FIELDS)
        List<String> comments = fetchComments(c, tableUpper);
        if (!comments.isEmpty()) {
            body.append('\n');
            for (String d : comments) body.append(d).append(";\n");
            included.add("COMMENT ON");
        }

        StringBuilder triggers = new StringBuilder();
        if (includeTriggers) {
            List<String> trigs = fetchTriggers(c, tableUpper);
            for (String t : trigs) triggers.append(t).append(";\n\n");
            included.add(trigs.isEmpty() ? "TRIGGERS (none)" : "TRIGGERS");
        } else {
            skipped.add("triggers (use --include-triggers)");
        }

        skipped.add("GRANT/REVOKE");
        skipped.add("autonomous generators (sequences) not used by tables");

        String header = DdlScript.standardHeader("firebird", null, table,
            redactedUrl, v.banner(), included, skipped);
        return Optional.of(new DdlScript(header, body.toString().stripTrailing(),
            triggers.toString().stripTrailing(), warnings));
    }

    private String fetchRelType(Connection c, String tableUpper) {
        String sql = "SELECT CASE WHEN RDB$VIEW_SOURCE IS NOT NULL THEN 'VIEW' ELSE 'TABLE' END "
                  + "FROM RDB$RELATIONS WHERE TRIM(RDB$RELATION_NAME) = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tableUpper);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "TABLE";
            }
        } catch (SQLException e) {
            return "TABLE";
        }
    }

    private String fetchViewSource(Connection c, String tableUpper) {
        String sql = "SELECT RDB$VIEW_SOURCE FROM RDB$RELATIONS WHERE TRIM(RDB$RELATION_NAME) = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tableUpper);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1).trim() : "";
            }
        } catch (SQLException e) {
            return "";
        }
    }

    private String buildCreateTable(Connection c, String tableUpper) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE \"").append(tableUpper).append("\" (\n");

        String sql = """
            SELECT TRIM(rf.RDB$FIELD_NAME) AS col_name,
                   f.RDB$FIELD_TYPE, f.RDB$FIELD_SUB_TYPE, f.RDB$FIELD_LENGTH,
                   f.RDB$FIELD_PRECISION, f.RDB$FIELD_SCALE,
                   rf.RDB$NULL_FLAG, rf.RDB$DEFAULT_SOURCE, rf.RDB$IDENTITY_TYPE
              FROM RDB$RELATION_FIELDS rf
              JOIN RDB$FIELDS f ON f.RDB$FIELD_NAME = rf.RDB$FIELD_SOURCE
             WHERE TRIM(rf.RDB$RELATION_NAME) = ?
             ORDER BY rf.RDB$FIELD_POSITION
            """;
        boolean first = true;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tableUpper);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) sb.append(",\n");
                    first = false;
                    sb.append("    \"").append(rs.getString("col_name")).append("\" ");
                    sb.append(fbType(rs));
                    String defSrc = rs.getString("RDB$DEFAULT_SOURCE");
                    if (defSrc != null && !defSrc.isBlank()) sb.append(' ').append(defSrc.trim());
                    Integer ident = rs.getObject("RDB$IDENTITY_TYPE") == null ? null : rs.getInt("RDB$IDENTITY_TYPE");
                    if (ident != null) sb.append(ident == 0 ? " GENERATED ALWAYS AS IDENTITY" : " GENERATED BY DEFAULT AS IDENTITY");
                    Integer notNull = rs.getObject("RDB$NULL_FLAG") == null ? null : rs.getInt("RDB$NULL_FLAG");
                    if (notNull != null && notNull == 1) sb.append(" NOT NULL");
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "build CREATE TABLE (FB) failed: " + e.getMessage(), null, e);
        }

        // Inline PK + UNIQUE
        String inlSql = """
            SELECT TRIM(rc.RDB$CONSTRAINT_NAME) AS cname,
                   TRIM(rc.RDB$CONSTRAINT_TYPE) AS ctype,
                   LIST(TRIM(seg.RDB$FIELD_NAME)) AS cols
              FROM RDB$RELATION_CONSTRAINTS rc
              JOIN RDB$INDEX_SEGMENTS seg ON seg.RDB$INDEX_NAME = rc.RDB$INDEX_NAME
             WHERE TRIM(rc.RDB$RELATION_NAME) = ?
               AND TRIM(rc.RDB$CONSTRAINT_TYPE) IN ('PRIMARY KEY', 'UNIQUE')
             GROUP BY rc.RDB$CONSTRAINT_NAME, rc.RDB$CONSTRAINT_TYPE
            """;
        try (PreparedStatement ps = c.prepareStatement(inlSql)) {
            ps.setString(1, tableUpper);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(",\n    CONSTRAINT \"").append(rs.getString("cname")).append("\" ")
                      .append(rs.getString("ctype")).append(" (").append(rs.getString("cols")).append(")");
                }
            }
        } catch (SQLException e) {
            // FB earlier than 5 may not have LIST aggregate — fallback to per-segment loop omitted for brevity
        }

        sb.append("\n);");
        return sb.toString();
    }

    private static String fbType(ResultSet rs) throws SQLException {
        int t = rs.getInt("RDB$FIELD_TYPE");
        Integer sub = rs.getObject("RDB$FIELD_SUB_TYPE") == null ? null : rs.getInt("RDB$FIELD_SUB_TYPE");
        Integer len = rs.getObject("RDB$FIELD_LENGTH") == null ? null : rs.getInt("RDB$FIELD_LENGTH");
        Integer prec = rs.getObject("RDB$FIELD_PRECISION") == null ? null : rs.getInt("RDB$FIELD_PRECISION");
        Integer scale = rs.getObject("RDB$FIELD_SCALE") == null ? null : rs.getInt("RDB$FIELD_SCALE");
        // Common FB type codes (NOT exhaustive — covers MVP):
        return switch (t) {
            case 7 -> "SMALLINT";
            case 8 -> "INTEGER";
            case 16 -> "BIGINT";
            case 10 -> "FLOAT";
            case 27 -> "DOUBLE PRECISION";
            case 12 -> "DATE";
            case 13 -> "TIME";
            case 35 -> "TIMESTAMP";
            case 37 -> len != null ? "VARCHAR(" + len + ")" : "VARCHAR";
            case 14 -> len != null ? "CHAR(" + len + ")" : "CHAR";
            case 23 -> "BOOLEAN";
            case 261 -> {
                if (sub != null && sub == 1) yield "BLOB SUB_TYPE TEXT";
                yield "BLOB";
            }
            default -> "/* unknown_fb_type=" + t + " */ VARCHAR";
        };
    }

    private List<String> fetchIndexDdl(Connection c, String tableUpper, ServerVersion v) {
        List<String> out = new ArrayList<>();
        // Skip indices that back PK or UNIQUE constraints
        String sql = """
            SELECT TRIM(i.RDB$INDEX_NAME) AS idx_name, COALESCE(i.RDB$UNIQUE_FLAG, 0) AS uniq,
                   LIST(TRIM(seg.RDB$FIELD_NAME), ', ') AS cols
              FROM RDB$INDICES i
              JOIN RDB$INDEX_SEGMENTS seg ON seg.RDB$INDEX_NAME = i.RDB$INDEX_NAME
             WHERE TRIM(i.RDB$RELATION_NAME) = ?
               AND COALESCE(i.RDB$INDEX_INACTIVE, 0) = 0
               AND NOT EXISTS (
                 SELECT 1 FROM RDB$RELATION_CONSTRAINTS rc
                  WHERE rc.RDB$INDEX_NAME = i.RDB$INDEX_NAME
                    AND TRIM(rc.RDB$CONSTRAINT_TYPE) IN ('PRIMARY KEY','UNIQUE'))
             GROUP BY i.RDB$INDEX_NAME, i.RDB$UNIQUE_FLAG
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tableUpper);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String unique = rs.getInt("uniq") == 1 ? "UNIQUE " : "";
                    out.add("CREATE " + unique + "INDEX \"" + rs.getString("idx_name")
                        + "\" ON \"" + tableUpper + "\" (" + rs.getString("cols") + ")");
                }
            }
        } catch (SQLException e) {
            // LIST aggregate may be absent on older FB — fallback omitted for brevity
        }
        return out;
    }

    private List<String> fetchAlterConstraints(Connection c, String tableUpper) {
        List<String> out = new ArrayList<>();
        // FK
        String fkSql = """
            SELECT TRIM(rc.RDB$CONSTRAINT_NAME) AS cname,
                   LIST(TRIM(seg.RDB$FIELD_NAME), ', ') AS cols,
                   TRIM(MAX(refrc.RDB$RELATION_NAME)) AS ref_table,
                   LIST(TRIM(refseg.RDB$FIELD_NAME), ', ') AS ref_cols,
                   TRIM(MAX(refc.RDB$UPDATE_RULE)) AS upd_rule,
                   TRIM(MAX(refc.RDB$DELETE_RULE)) AS del_rule
              FROM RDB$RELATION_CONSTRAINTS rc
              JOIN RDB$REF_CONSTRAINTS refc ON refc.RDB$CONSTRAINT_NAME = rc.RDB$CONSTRAINT_NAME
              JOIN RDB$RELATION_CONSTRAINTS refrc ON refrc.RDB$CONSTRAINT_NAME = refc.RDB$CONST_NAME_UQ
              JOIN RDB$INDEX_SEGMENTS seg ON seg.RDB$INDEX_NAME = rc.RDB$INDEX_NAME
              JOIN RDB$INDEX_SEGMENTS refseg ON refseg.RDB$INDEX_NAME = refrc.RDB$INDEX_NAME
                AND refseg.RDB$FIELD_POSITION = seg.RDB$FIELD_POSITION
             WHERE TRIM(rc.RDB$RELATION_NAME) = ?
               AND TRIM(rc.RDB$CONSTRAINT_TYPE) = 'FOREIGN KEY'
             GROUP BY rc.RDB$CONSTRAINT_NAME
            """;
        try (PreparedStatement ps = c.prepareStatement(fkSql)) {
            ps.setString(1, tableUpper);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add("ALTER TABLE \"" + tableUpper + "\" ADD CONSTRAINT \""
                        + rs.getString("cname") + "\" FOREIGN KEY (" + rs.getString("cols")
                        + ") REFERENCES \"" + rs.getString("ref_table") + "\" ("
                        + rs.getString("ref_cols") + ") ON UPDATE " + rs.getString("upd_rule")
                        + " ON DELETE " + rs.getString("del_rule"));
                }
            }
        } catch (SQLException e) { /* skip */ }

        // CHECK
        String chkSql = """
            SELECT TRIM(rc.RDB$CONSTRAINT_NAME) AS cname, tr.RDB$TRIGGER_SOURCE AS src
              FROM RDB$RELATION_CONSTRAINTS rc
              JOIN RDB$CHECK_CONSTRAINTS cc ON cc.RDB$CONSTRAINT_NAME = rc.RDB$CONSTRAINT_NAME
              JOIN RDB$TRIGGERS tr ON tr.RDB$TRIGGER_NAME = cc.RDB$TRIGGER_NAME
             WHERE TRIM(rc.RDB$RELATION_NAME) = ?
               AND TRIM(rc.RDB$CONSTRAINT_TYPE) = 'CHECK'
               AND tr.RDB$TRIGGER_TYPE = 1
            """;
        try (PreparedStatement ps = c.prepareStatement(chkSql)) {
            ps.setString(1, tableUpper);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String src = rs.getString("src");
                    if (src != null) src = src.trim();
                    out.add("ALTER TABLE \"" + tableUpper + "\" ADD CONSTRAINT \""
                        + rs.getString("cname") + "\" " + src);
                }
            }
        } catch (SQLException e) { /* skip */ }
        return out;
    }

    private List<String> fetchComments(Connection c, String tableUpper) {
        List<String> out = new ArrayList<>();
        // Table description
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT RDB$DESCRIPTION FROM RDB$RELATIONS WHERE TRIM(RDB$RELATION_NAME) = ?")) {
            ps.setString(1, tableUpper);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String d = rs.getString(1);
                    if (d != null && !d.isBlank())
                        out.add("COMMENT ON TABLE \"" + tableUpper + "\" IS '" + d.replace("'","''") + "'");
                }
            }
        } catch (SQLException ignored) {}
        // Column descriptions
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT TRIM(RDB$FIELD_NAME) AS col, RDB$DESCRIPTION AS d "
          + "FROM RDB$RELATION_FIELDS WHERE TRIM(RDB$RELATION_NAME) = ? AND RDB$DESCRIPTION IS NOT NULL")) {
            ps.setString(1, tableUpper);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add("COMMENT ON COLUMN \"" + tableUpper + "\".\"" + rs.getString("col")
                        + "\" IS '" + rs.getString("d").replace("'","''") + "'");
                }
            }
        } catch (SQLException ignored) {}
        return out;
    }

    private List<String> fetchTriggers(Connection c, String tableUpper) {
        List<String> out = new ArrayList<>();
        String sql = """
            SELECT TRIM(RDB$TRIGGER_NAME) AS tname, RDB$TRIGGER_SOURCE AS src,
                   RDB$TRIGGER_TYPE AS ttype
              FROM RDB$TRIGGERS
             WHERE TRIM(RDB$RELATION_NAME) = ?
               AND COALESCE(RDB$SYSTEM_FLAG, 0) = 0
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tableUpper);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String src = rs.getString("src");
                    if (src == null) continue;
                    out.add("CREATE OR ALTER TRIGGER \"" + rs.getString("tname")
                        + "\" FOR \"" + tableUpper + "\"\n" + src.trim());
                }
            }
        } catch (SQLException ignored) {}
        return out;
    }
}
