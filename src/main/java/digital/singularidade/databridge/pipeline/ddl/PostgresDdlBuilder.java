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

public final class PostgresDdlBuilder implements DdlBuilder {

    @Override
    public Optional<DdlScript> build(JdbcSource source, String schema, String table,
                                      String redactedUrl, boolean includeTriggers) {
        Connection c = source.connection();
        ServerVersion v = source.serverVersion();
        List<String> warnings = new ArrayList<>();
        List<String> included = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        StringBuilder body = new StringBuilder();

        // ---- 1. Determine relkind (table / view / matview / partitioned table) ----
        String relkind = fetchRelkind(c, schema, table);
        if (relkind == null) {
            throw new DataBridgeException(ErrorCodes.TABLE_NOT_FOUND,
                "table not found: " + schema + "." + table, null);
        }

        // ---- 2. CREATE TABLE / CREATE VIEW / CREATE MATERIALIZED VIEW ----
        switch (relkind) {
            case "v" -> {
                body.append("CREATE OR REPLACE VIEW ").append(qualifiedName(schema, table))
                    .append(" AS\n").append(fetchViewDef(c, schema, table)).append(";\n");
                included.add("CREATE VIEW");
            }
            case "m" -> {
                body.append("CREATE MATERIALIZED VIEW ").append(qualifiedName(schema, table))
                    .append(" AS\n").append(fetchViewDef(c, schema, table)).append(";\n");
                included.add("CREATE MATERIALIZED VIEW");
            }
            default -> {
                body.append(buildCreateTable(c, v, schema, table, warnings)).append("\n");
                included.add("CREATE TABLE");
            }
        }

        // ---- 3. Indexes (skip ones backing PK/UNIQUE constraints — those come via constraintdef) ----
        List<String> indexDefs = fetchIndexDefs(c, schema, table);
        if (!indexDefs.isEmpty()) {
            body.append('\n');
            for (String def : indexDefs) body.append(def).append(";\n");
            included.add("CREATE INDEX");
        }

        // ---- 4. ALTER TABLE ... ADD CONSTRAINT (FK, CHECK, EXCLUDE) ----
        List<String> constraintDefs = fetchAlterConstraints(c, schema, table);
        if (!constraintDefs.isEmpty()) {
            body.append('\n');
            for (String def : constraintDefs) body.append(def).append(";\n");
            included.add("FOREIGN KEY / CHECK constraints");
        }

        // ---- 5. COMMENT ON TABLE/COLUMN ----
        List<String> comments = fetchComments(c, schema, table);
        if (!comments.isEmpty()) {
            body.append('\n');
            for (String com : comments) body.append(com).append(";\n");
            included.add("COMMENT ON");
        }

        // ---- 6. Triggers (opt-in) ----
        StringBuilder triggers = new StringBuilder();
        if (includeTriggers) {
            List<String> trigDefs = fetchTriggerDefs(c, schema, table);
            for (String def : trigDefs) triggers.append(def).append(";\n");
            included.add(trigDefs.isEmpty() ? "TRIGGERS (none)" : "TRIGGERS");
        } else {
            skipped.add("triggers (use --include-triggers)");
        }

        skipped.add("RLS policies");
        skipped.add("GRANT/REVOKE");
        skipped.add("functions/procedures");
        if (!v.isAtLeast(12, 0)) {
            warnings.add("PG " + v.major() + "." + v.minor()
                + ": GENERATED ALWAYS AS columns and other PG 12+ features may not be extracted.");
            skipped.add("GENERATED columns (PG 12+)");
        }

        String header = DdlScript.standardHeader("postgresql", schema, table,
            redactedUrl, v.banner(), included, skipped);
        return Optional.of(new DdlScript(header, body.toString().stripTrailing(),
            triggers.toString().stripTrailing(), warnings));
    }

    // ---- helpers ----

    private static String qualifiedName(String schema, String table) {
        return "\"" + schema.replace("\"","\"\"") + "\".\"" + table.replace("\"","\"\"") + "\"";
    }

    private String fetchRelkind(Connection c, String schema, String table) {
        String sql = "SELECT c.relkind FROM pg_class c "
                  + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                  + "WHERE n.nspname = ? AND c.relname = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "fetch relkind failed: " + e.getMessage(), null, e);
        }
    }

    private String fetchViewDef(Connection c, String schema, String table) {
        String sql = "SELECT pg_get_viewdef(format('%I.%I', ?, ?)::regclass, true)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1).trim() : "";
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "fetch viewdef failed: " + e.getMessage(), null, e);
        }
    }

    private String buildCreateTable(Connection c, ServerVersion v, String schema, String table,
                                     List<String> warnings) {
        // Use pg_attribute joined with pg_attrdef to get columns + defaults + identity.
        // PG 10+: a.attidentity ('a'=ALWAYS, 'd'=BY DEFAULT, ''=none)
        // PG 12+: a.attgenerated ('s'=STORED, ''=none)
        boolean hasGenerated = v.isAtLeast(12, 0);
        String genCol = hasGenerated ? "a.attgenerated" : "''::char AS attgenerated";

        String sql = """
            SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS type_def,
                   a.attnotnull, a.attidentity, """ + genCol + """
                   ,
                   pg_get_expr(d.adbin, d.adrelid) AS default_expr
              FROM pg_attribute a
              LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
              JOIN pg_class c ON c.oid = a.attrelid
              JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = ? AND c.relname = ?
               AND a.attnum > 0 AND NOT a.attisdropped
             ORDER BY a.attnum
            """;

        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE ").append(qualifiedName(schema, table)).append(" (\n");
        boolean first = true;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) sb.append(",\n");
                    first = false;
                    sb.append("    \"").append(rs.getString("attname").replace("\"","\"\"")).append("\" ");
                    sb.append(rs.getString("type_def"));
                    String identity = rs.getString("attidentity");
                    String generated = rs.getString("attgenerated");
                    String defExpr = rs.getString("default_expr");
                    if (identity != null && !identity.isEmpty()) {
                        sb.append(identity.equals("a") ? " GENERATED ALWAYS AS IDENTITY"
                                                       : " GENERATED BY DEFAULT AS IDENTITY");
                    } else if (generated != null && generated.equals("s")) {
                        sb.append(" GENERATED ALWAYS AS (").append(defExpr).append(") STORED");
                    } else if (defExpr != null) {
                        sb.append(" DEFAULT ").append(defExpr);
                    }
                    if (rs.getBoolean("attnotnull")) sb.append(" NOT NULL");
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "build CREATE TABLE failed: " + e.getMessage(), null, e);
        }

        // Inline PK/UNIQUE constraints (FKs and CHECKs come as separate ALTER TABLE statements)
        String inlineSql = """
            SELECT con.conname, pg_get_constraintdef(con.oid) AS def
              FROM pg_constraint con
              JOIN pg_class c ON c.oid = con.conrelid
              JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = ? AND c.relname = ?
               AND con.contype IN ('p', 'u')
             ORDER BY con.contype, con.conname
            """;
        try (PreparedStatement ps = c.prepareStatement(inlineSql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(",\n    CONSTRAINT \"").append(rs.getString("conname").replace("\"","\"\""))
                      .append("\" ").append(rs.getString("def"));
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "fetch inline constraints failed: " + e.getMessage(), null, e);
        }

        sb.append("\n);");
        return sb.toString();
    }

    private List<String> fetchIndexDefs(Connection c, String schema, String table) {
        List<String> out = new ArrayList<>();
        String sql = """
            SELECT pg_get_indexdef(idx.indexrelid) AS def
              FROM pg_index idx
              JOIN pg_class ic ON ic.oid = idx.indexrelid
              JOIN pg_class tc ON tc.oid = idx.indrelid
              JOIN pg_namespace n ON n.oid = tc.relnamespace
             WHERE n.nspname = ? AND tc.relname = ?
               AND NOT EXISTS (
                 SELECT 1 FROM pg_constraint cn
                  WHERE cn.conindid = idx.indexrelid AND cn.contype IN ('p','u'))
             ORDER BY ic.relname
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString("def"));
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "fetch indexdefs failed: " + e.getMessage(), null, e);
        }
        return out;
    }

    private List<String> fetchAlterConstraints(Connection c, String schema, String table) {
        List<String> out = new ArrayList<>();
        String sql = """
            SELECT con.conname, pg_get_constraintdef(con.oid) AS def
              FROM pg_constraint con
              JOIN pg_class cl ON cl.oid = con.conrelid
              JOIN pg_namespace n ON n.oid = cl.relnamespace
             WHERE n.nspname = ? AND cl.relname = ?
               AND con.contype IN ('f', 'c', 'x')
             ORDER BY con.contype, con.conname
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add("ALTER TABLE " + qualifiedName(schema, table)
                        + " ADD CONSTRAINT \"" + rs.getString("conname").replace("\"","\"\"")
                        + "\" " + rs.getString("def"));
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "fetch constraints failed: " + e.getMessage(), null, e);
        }
        return out;
    }

    private List<String> fetchComments(Connection c, String schema, String table) {
        List<String> out = new ArrayList<>();
        // Table comment
        String tSql = """
            SELECT obj_description(format('%I.%I', ?, ?)::regclass, 'pg_class') AS com
            """;
        try (PreparedStatement ps = c.prepareStatement(tSql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String com = rs.getString("com");
                    if (com != null) out.add("COMMENT ON TABLE " + qualifiedName(schema, table)
                        + " IS " + sqlString(com));
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "fetch table comment failed: " + e.getMessage(), null, e);
        }
        // Column comments
        String cSql = """
            SELECT a.attname, col_description(c.oid, a.attnum) AS com
              FROM pg_attribute a
              JOIN pg_class c ON c.oid = a.attrelid
              JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = ? AND c.relname = ?
               AND a.attnum > 0 AND NOT a.attisdropped
               AND col_description(c.oid, a.attnum) IS NOT NULL
             ORDER BY a.attnum
            """;
        try (PreparedStatement ps = c.prepareStatement(cSql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add("COMMENT ON COLUMN " + qualifiedName(schema, table)
                        + ".\"" + rs.getString("attname").replace("\"","\"\"")
                        + "\" IS " + sqlString(rs.getString("com")));
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "fetch column comments failed: " + e.getMessage(), null, e);
        }
        return out;
    }

    private List<String> fetchTriggerDefs(Connection c, String schema, String table) {
        List<String> out = new ArrayList<>();
        String sql = """
            SELECT pg_get_triggerdef(t.oid) AS def
              FROM pg_trigger t
              JOIN pg_class cl ON cl.oid = t.tgrelid
              JOIN pg_namespace n ON n.oid = cl.relnamespace
             WHERE n.nspname = ? AND cl.relname = ?
               AND NOT t.tgisinternal
             ORDER BY t.tgname
            """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString("def"));
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "fetch triggers failed: " + e.getMessage(), null, e);
        }
        return out;
    }

    private static String sqlString(String s) {
        return "'" + s.replace("'", "''") + "'";
    }
}
