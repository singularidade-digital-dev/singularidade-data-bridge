package digital.singularidade.databridge.pipeline.ddl;

import digital.singularidade.databridge.output.SystemNames;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.source.jdbc.ServerVersion;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbCheck;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbColumn;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbDomain;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbForeignKey;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbIndex;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbKey;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbTrigger;
import digital.singularidade.databridge.source.jdbc.firebird.FirebirdCatalog;
import digital.singularidade.databridge.source.jdbc.firebird.FirebirdTypes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Firebird DDL rendered from the catalog model ({@link FbTable}), in a fixed order: shared domains and
 * sequences, CREATE TABLE (PK/UNIQUE inline), indexes, foreign keys, checks, comments. The script replays
 * with {@code isql}; names the database generated (INTEG_n, RDB$...) are never written, so Firebird
 * assigns fresh ones. {@code triggers.sql} is a {@code SET TERM} block with event, state and position.
 */
public final class FirebirdDdlBuilder implements DdlBuilder {

    /** Rules worth writing; RESTRICT and NO ACTION are the default (and RESTRICT is not valid syntax). */
    private static final Set<String> EXPLICIT_RULES = Set.of("CASCADE", "SET NULL", "SET DEFAULT");

    @Override
    public Optional<DdlScript> build(JdbcSource source, String schema, String table,
                                     String redactedUrl, boolean includeTriggers) {
        FbTable t = source.firebirdTable(table);
        ServerVersion v = source.serverVersion();
        List<String> included = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<String> sections = new ArrayList<>();

        List<String> shared = sharedObjects(t);
        if (!shared.isEmpty()) {
            sections.add("-- Shared objects used by this table (create once per database)\n"
                + String.join("\n", shared));
            included.add("CREATE DOMAIN / CREATE SEQUENCE (shared)");
        }
        if (t.view()) {
            sections.add("CREATE OR ALTER VIEW " + q(t.name()) + " AS\n" + t.viewSource() + ";");
            included.add("CREATE VIEW");
        } else {
            sections.add(createTable(t));
            included.add("CREATE TABLE");
        }
        addSection(sections, included, "CREATE INDEX", indexes(t));
        addSection(sections, included, "FOREIGN KEY", foreignKeys(t));
        addSection(sections, included, "CHECK", checks(t));
        addSection(sections, included, "COMMENT ON", comments(t));

        String triggers = "";
        if (includeTriggers) {
            triggers = triggers(t);
            included.add(t.triggers().isEmpty() ? "TRIGGERS (none)" : "TRIGGERS");
        } else {
            skipped.add("triggers (use --include-triggers)");
        }
        skipped.add("GRANT/REVOKE");
        skipped.add("generator values");

        String header = DdlScript.standardHeader("firebird", null, table, redactedUrl, v.banner(),
            included, skipped) + "\n-- Database default character set: " + t.databaseCharset();
        return Optional.of(new DdlScript(header, String.join("\n\n", sections), triggers, List.of()));
    }

    private static void addSection(List<String> sections, List<String> included, String label,
                                   List<String> statements) {
        if (statements.isEmpty()) return;
        sections.add(String.join("\n", statements));
        included.add(label);
    }

    private static List<String> sharedObjects(FbTable t) {
        List<String> out = new ArrayList<>();
        for (FbDomain d : t.domains()) {
            StringBuilder sb = new StringBuilder("CREATE DOMAIN ").append(q(d.name())).append(" AS ")
                .append(d.nativeType());
            if (d.charset() != null && !d.charset().equals(t.databaseCharset())) {
                sb.append(" CHARACTER SET ").append(d.charset());
            }
            if (d.defaultValue() != null) sb.append(" DEFAULT ").append(d.defaultValue());
            if (d.notNull()) sb.append(" NOT NULL");
            if (d.checkSource() != null) sb.append(' ').append(d.checkSource());
            if (d.collation() != null && !d.collation().equals(d.charsetDefaultCollation())) {
                sb.append(" COLLATE ").append(d.collation());
            }
            out.add(sb.append(';').toString());
        }
        for (String g : t.triggerGenerators()) out.add("CREATE SEQUENCE " + q(g) + ";");
        return out;
    }

    private static String createTable(FbTable t) {
        List<String> lines = new ArrayList<>();
        for (FbColumn c : t.columns()) lines.add("    " + column(t, c));
        if (t.primaryKey() != null) lines.add("    " + key(t.primaryKey(), "PRIMARY KEY"));
        for (FbKey u : t.uniques()) lines.add("    " + key(u, "UNIQUE"));
        return "CREATE TABLE " + q(t.name()) + " (\n" + String.join(",\n", lines) + "\n);";
    }

    private static String column(FbTable t, FbColumn c) {
        StringBuilder sb = new StringBuilder(q(c.name())).append(' ');
        if (c.computedSource() != null) {
            String src = c.computedSource();
            return sb.append("COMPUTED BY ").append(src.startsWith("(") ? src : "(" + src + ")").toString();
        }
        if (c.domainName() != null) {
            sb.append(q(c.domainName()));
            if (c.columnDefault() != null) sb.append(" DEFAULT ").append(c.columnDefault());
            if (c.columnNotNull()) sb.append(" NOT NULL");
            if (c.columnCollation() != null) sb.append(" COLLATE ").append(c.columnCollation());
            return sb.toString();
        }
        sb.append(c.nativeType());
        boolean text = FirebirdTypes.isText(c.type());
        if (text && c.charset() != null && !c.charset().equals(t.databaseCharset())) {
            sb.append(" CHARACTER SET ").append(c.charset());
        }
        if (c.identityType() != null) {
            sb.append(c.identityType() == 0 ? " GENERATED ALWAYS AS IDENTITY" : " GENERATED BY DEFAULT AS IDENTITY");
        }
        if (c.columnDefault() != null) sb.append(" DEFAULT ").append(c.columnDefault());
        if (c.columnNotNull()) sb.append(" NOT NULL");
        if (text && c.collation() != null && !c.collation().equals(c.charsetDefaultCollation())) {
            sb.append(" COLLATE ").append(c.collation());
        }
        return sb.toString();
    }

    private static String key(FbKey k, String kind) {
        return constraintPrefix(k.name()) + kind + " (" + columnList(k.columns()) + ")"
            + usingIndex(k.name(), k.indexName(), k.descending());
    }

    private static List<String> indexes(FbTable t) {
        List<String> out = new ArrayList<>();
        for (FbIndex i : t.indexes()) {
            if (i.constraintName() != null) continue;   // created by its PK/UNIQUE/FK
            StringBuilder sb = new StringBuilder("CREATE ");
            if (i.unique()) sb.append("UNIQUE ");
            if (i.descending()) sb.append("DESCENDING ");
            sb.append("INDEX ").append(q(i.name())).append(" ON ").append(q(t.name()));
            if (i.expression() != null) {
                String e = i.expression();
                sb.append(" COMPUTED BY ").append(e.startsWith("(") ? e : "(" + e + ")");
            } else {
                sb.append(" (").append(columnList(i.columns())).append(')');
            }
            out.add(sb.append(';').toString());
            if (!i.active()) out.add("ALTER INDEX " + q(i.name()) + " INACTIVE;");
        }
        return out;
    }

    private static List<String> foreignKeys(FbTable t) {
        List<FbForeignKey> fks = new ArrayList<>(t.foreignKeys());
        fks.sort(Comparator.comparing(FbForeignKey::refTable)
            .thenComparing(f -> String.join(",", f.columns())));
        List<String> out = new ArrayList<>();
        for (FbForeignKey f : fks) {
            StringBuilder sb = new StringBuilder("ALTER TABLE ").append(q(t.name())).append(" ADD ")
                .append(constraintPrefix(f.name())).append("FOREIGN KEY (").append(columnList(f.columns()))
                .append(") REFERENCES ").append(q(f.refTable())).append(" (").append(columnList(f.refColumns()))
                .append(')');
            if (EXPLICIT_RULES.contains(f.deleteRule())) sb.append(" ON DELETE ").append(f.deleteRule());
            if (EXPLICIT_RULES.contains(f.updateRule())) sb.append(" ON UPDATE ").append(f.updateRule());
            sb.append(usingIndex(f.name(), f.indexName(), f.descending()));
            out.add(sb.append(';').toString());
        }
        return out;
    }

    private static List<String> checks(FbTable t) {
        List<FbCheck> checks = new ArrayList<>(t.checks());
        checks.sort(Comparator.comparing(c -> c.source() == null ? "" : c.source()));
        List<String> out = new ArrayList<>();
        for (FbCheck c : checks) {
            if (c.source() == null) continue;
            String expr = FirebirdCatalog.stripKeyword(c.source(), "CHECK");
            out.add("ALTER TABLE " + q(t.name()) + " ADD " + constraintPrefix(c.name()) + "CHECK " + expr + ";");
        }
        return out;
    }

    private static List<String> comments(FbTable t) {
        List<String> out = new ArrayList<>();
        if (t.description() != null) {
            out.add("COMMENT ON TABLE " + q(t.name()) + " IS " + literal(t.description()) + ";");
        }
        for (FbColumn c : t.columns()) {
            if (c.description() == null) continue;
            out.add("COMMENT ON COLUMN " + q(t.name()) + "." + q(c.name()) + " IS " + literal(c.description()) + ";");
        }
        return out;
    }

    private static String triggers(FbTable t) {
        if (t.triggers().isEmpty()) return "";
        List<String> blocks = new ArrayList<>();
        for (FbTrigger tr : t.triggers()) {
            if (tr.body() == null) continue;
            blocks.add("CREATE OR ALTER TRIGGER " + q(tr.name()) + " FOR " + q(t.name())
                + (tr.active() ? " ACTIVE " : " INACTIVE ") + tr.timing() + " " + String.join(" OR ", tr.events())
                + " POSITION " + tr.position() + "\n" + tr.body() + "^");
        }
        if (blocks.isEmpty()) return "";
        return "SET TERM ^ ;\n\n" + String.join("\n\n", blocks) + "\n\nSET TERM ; ^";
    }

    private static String constraintPrefix(String name) {
        return name == null || SystemNames.isSystemGenerated(name) ? "" : "CONSTRAINT " + q(name) + " ";
    }

    /**
     * {@code USING [DESC] INDEX name} only when the backing index is descending or named apart from the
     * constraint (Firebird requires the name in this clause, so a descending index keeps its own).
     */
    private static String usingIndex(String constraint, String index, boolean descending) {
        if (index == null) return "";
        boolean customName = !SystemNames.isSystemGenerated(index) && !index.equals(constraint);
        if (!customName && !descending) return "";
        return " USING " + (descending ? "DESC " : "") + "INDEX " + q(index);
    }

    private static String columnList(List<String> columns) {
        List<String> quoted = new ArrayList<>(columns.size());
        for (String c : columns) quoted.add(q(c));
        return String.join(", ", quoted);
    }

    private static String q(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static String literal(String text) {
        return "'" + text.replace("'", "''") + "'";
    }
}
