package digital.singularidade.databridge.source.jdbc.firebird;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.output.SystemNames;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbCheck;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbColumn;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbDomain;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbForeignKey;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbIndex;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbKey;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbSequenceLink;
import digital.singularidade.databridge.source.jdbc.firebird.FbTable.FbTrigger;
import digital.singularidade.databridge.source.jdbc.firebird.FirebirdTypes.FieldType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one table from the Firebird {@code RDB$} catalog (Firebird 3+). Names are compared with
 * {@code RDB$...NAME = ?} so the system indexes are used, and trimmed only in the select list; every
 * multi-row query is ordered explicitly.
 */
public final class FirebirdCatalog {

    /** {@code NEW.<col> = GEN_ID(<gen>, ...)} or {@code NEW.<col> = NEXT VALUE FOR <gen>}. */
    private static final Pattern ASSIGNMENT = Pattern.compile(
        "NEW\\s*\\.\\s*(?:\"([^\"]+)\"|([A-Za-z0-9_$]+))\\s*=\\s*"
      + "(?:GEN_ID\\s*\\(\\s*(?:\"([^\"]+)\"|([A-Za-z0-9_$]+))\\s*,"
      + "|NEXT\\s+VALUE\\s+FOR\\s+(?:\"([^\"]+)\"|([A-Za-z0-9_$]+)))",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern COMMENTS = Pattern.compile("/\\*.*?\\*/|--[^\\n]*", Pattern.DOTALL);

    private static final String COLUMNS_SQL = """
        SELECT TRIM(rf.RDB$FIELD_NAME) AS name, TRIM(rf.RDB$FIELD_SOURCE) AS src,
               rf.RDB$NULL_FLAG AS rf_nn, rf.RDB$DEFAULT_SOURCE AS rf_def, rf.RDB$DESCRIPTION AS descr,
               rf.RDB$IDENTITY_TYPE AS idt, TRIM(rf.RDB$GENERATOR_NAME) AS idgen,
               f.RDB$FIELD_TYPE AS ftype, f.RDB$FIELD_SUB_TYPE AS fsub, f.RDB$CHARACTER_LENGTH AS clen,
               f.RDB$FIELD_LENGTH AS flen, f.RDB$FIELD_PRECISION AS prec, f.RDB$FIELD_SCALE AS fscale,
               f.RDB$SEGMENT_LENGTH AS seg, f.RDB$NULL_FLAG AS f_nn, f.RDB$DEFAULT_SOURCE AS f_def,
               f.RDB$VALIDATION_SOURCE AS f_chk, f.RDB$COMPUTED_SOURCE AS f_comp,
               TRIM(cs.RDB$CHARACTER_SET_NAME) AS cs_name, TRIM(cs.RDB$DEFAULT_COLLATE_NAME) AS cs_coll,
               cs.RDB$BYTES_PER_CHARACTER AS bpc,
               TRIM(fc.RDB$COLLATION_NAME) AS f_coll, TRIM(rc.RDB$COLLATION_NAME) AS rf_coll
          FROM RDB$RELATION_FIELDS rf
          JOIN RDB$FIELDS f ON f.RDB$FIELD_NAME = rf.RDB$FIELD_SOURCE
          LEFT JOIN RDB$CHARACTER_SETS cs ON cs.RDB$CHARACTER_SET_ID = f.RDB$CHARACTER_SET_ID
          LEFT JOIN RDB$COLLATIONS fc ON fc.RDB$CHARACTER_SET_ID = f.RDB$CHARACTER_SET_ID
                                     AND fc.RDB$COLLATION_ID = COALESCE(f.RDB$COLLATION_ID, 0)
          LEFT JOIN RDB$COLLATIONS rc ON rc.RDB$CHARACTER_SET_ID = f.RDB$CHARACTER_SET_ID
                                     AND rc.RDB$COLLATION_ID = rf.RDB$COLLATION_ID
         WHERE rf.RDB$RELATION_NAME = ?
         ORDER BY rf.RDB$FIELD_POSITION, rf.RDB$FIELD_NAME
        """;

    private static final String CONSTRAINTS_SQL = """
        SELECT TRIM(rc.RDB$CONSTRAINT_NAME) AS cname, TRIM(rc.RDB$CONSTRAINT_TYPE) AS ctype,
               TRIM(rc.RDB$INDEX_NAME) AS iname
          FROM RDB$RELATION_CONSTRAINTS rc
         WHERE rc.RDB$RELATION_NAME = ?
           AND rc.RDB$CONSTRAINT_TYPE IN ('PRIMARY KEY', 'UNIQUE', 'FOREIGN KEY', 'CHECK')
         ORDER BY rc.RDB$CONSTRAINT_NAME
        """;

    private static final String INDEXES_SQL = """
        SELECT TRIM(i.RDB$INDEX_NAME) AS iname, COALESCE(i.RDB$UNIQUE_FLAG, 0) AS uniq,
               COALESCE(i.RDB$INDEX_TYPE, 0) AS itype, COALESCE(i.RDB$INDEX_INACTIVE, 0) AS inactive,
               i.RDB$EXPRESSION_SOURCE AS expr
          FROM RDB$INDICES i
         WHERE i.RDB$RELATION_NAME = ?
         ORDER BY i.RDB$INDEX_NAME
        """;

    private static final String SEGMENTS_SQL = """
        SELECT TRIM(s.RDB$INDEX_NAME) AS iname, TRIM(s.RDB$FIELD_NAME) AS fname
          FROM RDB$INDEX_SEGMENTS s
          JOIN RDB$INDICES i ON i.RDB$INDEX_NAME = s.RDB$INDEX_NAME
         WHERE i.RDB$RELATION_NAME = ?
         ORDER BY s.RDB$INDEX_NAME, s.RDB$FIELD_POSITION
        """;

    private static final String INDEX_SEGMENTS_SQL = """
        SELECT TRIM(s.RDB$FIELD_NAME) AS fname
          FROM RDB$INDEX_SEGMENTS s
         WHERE s.RDB$INDEX_NAME = ?
         ORDER BY s.RDB$FIELD_POSITION
        """;

    private static final String FOREIGN_KEYS_SQL = """
        SELECT TRIM(ref.RDB$CONSTRAINT_NAME) AS cname, TRIM(ref.RDB$UPDATE_RULE) AS upd,
               TRIM(ref.RDB$DELETE_RULE) AS del, TRIM(uq.RDB$RELATION_NAME) AS ref_table,
               TRIM(uq.RDB$INDEX_NAME) AS ref_index
          FROM RDB$REF_CONSTRAINTS ref
          JOIN RDB$RELATION_CONSTRAINTS rc ON rc.RDB$CONSTRAINT_NAME = ref.RDB$CONSTRAINT_NAME
          JOIN RDB$RELATION_CONSTRAINTS uq ON uq.RDB$CONSTRAINT_NAME = ref.RDB$CONST_NAME_UQ
         WHERE rc.RDB$RELATION_NAME = ?
         ORDER BY ref.RDB$CONSTRAINT_NAME
        """;

    /** Firebird keeps a CHECK as two triggers (BEFORE INSERT / BEFORE UPDATE); type 1 carries the source. */
    private static final String CHECKS_SQL = """
        SELECT TRIM(cc.RDB$CONSTRAINT_NAME) AS cname, t.RDB$TRIGGER_SOURCE AS src
          FROM RDB$CHECK_CONSTRAINTS cc
          JOIN RDB$RELATION_CONSTRAINTS rc ON rc.RDB$CONSTRAINT_NAME = cc.RDB$CONSTRAINT_NAME
          JOIN RDB$TRIGGERS t ON t.RDB$TRIGGER_NAME = cc.RDB$TRIGGER_NAME
         WHERE rc.RDB$RELATION_NAME = ? AND rc.RDB$CONSTRAINT_TYPE = 'CHECK' AND t.RDB$TRIGGER_TYPE = 1
         ORDER BY cc.RDB$CONSTRAINT_NAME
        """;

    /** User triggers only: check-constraint triggers carry system flag 3. */
    private static final String TRIGGERS_SQL = """
        SELECT TRIM(t.RDB$TRIGGER_NAME) AS tname, t.RDB$TRIGGER_TYPE AS ttype,
               COALESCE(t.RDB$TRIGGER_SEQUENCE, 0) AS pos, COALESCE(t.RDB$TRIGGER_INACTIVE, 0) AS inactive,
               t.RDB$TRIGGER_SOURCE AS src
          FROM RDB$TRIGGERS t
         WHERE t.RDB$RELATION_NAME = ? AND COALESCE(t.RDB$SYSTEM_FLAG, 0) = 0
         ORDER BY t.RDB$TRIGGER_NAME
        """;

    /** Dependent type 2 = trigger, depended-on type 14 = generator. */
    private static final String TRIGGER_GENERATORS_SQL = """
        SELECT DISTINCT TRIM(d.RDB$DEPENDENT_NAME) AS tname, TRIM(d.RDB$DEPENDED_ON_NAME) AS gname
          FROM RDB$DEPENDENCIES d
          JOIN RDB$TRIGGERS t ON t.RDB$TRIGGER_NAME = d.RDB$DEPENDENT_NAME
         WHERE t.RDB$RELATION_NAME = ? AND COALESCE(t.RDB$SYSTEM_FLAG, 0) = 0
           AND d.RDB$DEPENDENT_TYPE = 2 AND d.RDB$DEPENDED_ON_TYPE = 14
         ORDER BY 1, 2
        """;

    private record Relation(String viewSource, String description) {}

    private record Constraint(String name, String type, String indexName) {}

    private record RawIndex(String name, boolean unique, boolean descending, boolean active, String expression) {}

    private FirebirdCatalog() {}

    public static FbTable read(Connection c, String table) {
        try {
            Relation relation = relation(c, table);
            if (relation == null) {
                throw new DataBridgeException(ErrorCodes.TABLE_NOT_FOUND, "table not found: " + table,
                    "Run `data-bridge list-tables` to see the available tables");
            }
            String databaseCharset = databaseCharset(c);
            Map<String, List<String>> segments = segments(c, table);
            Map<String, RawIndex> rawIndexes = indexes(c, table);
            List<Constraint> constraints = constraints(c, table);
            Map<String, String> constraintByIndex = new HashMap<>();
            for (Constraint k : constraints) {
                if (k.indexName() != null) constraintByIndex.put(k.indexName(), k.name());
            }

            List<FbTrigger> triggers = triggers(c, table, triggerGenerators(c, table));
            TreeMap<String, FbDomain> domains = new TreeMap<>();
            List<FbColumn> columns = linkSequences(columns(c, table, domains), triggers);

            FbKey primaryKey = null;
            List<FbKey> uniques = new ArrayList<>();
            for (Constraint k : constraints) {
                if (!"PRIMARY KEY".equals(k.type()) && !"UNIQUE".equals(k.type())) continue;
                FbKey key = new FbKey(k.name(), segments.getOrDefault(k.indexName(), List.of()),
                    k.indexName(), isDescending(rawIndexes, k.indexName()));
                if ("PRIMARY KEY".equals(k.type())) primaryKey = key; else uniques.add(key);
            }

            List<FbIndex> indexes = new ArrayList<>();
            for (RawIndex i : rawIndexes.values()) {
                indexes.add(new FbIndex(i.name(),
                    i.expression() != null ? List.of() : segments.getOrDefault(i.name(), List.of()),
                    i.unique(), i.descending(), i.expression(), i.active(), constraintByIndex.get(i.name())));
            }

            Set<String> triggerGenerators = new TreeSet<>();
            for (FbTrigger t : triggers) triggerGenerators.addAll(t.generators());

            return new FbTable(table, relation.viewSource() != null, relation.viewSource(),
                relation.description(), databaseCharset, columns, List.copyOf(domains.values()),
                primaryKey, List.copyOf(uniques),
                foreignKeys(c, table, constraints, segments, rawIndexes), checks(c, table),
                List.copyOf(indexes), triggers, List.copyOf(triggerGenerators));
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "Firebird catalog read failed for " + table + ": " + e.getMessage(), null, e);
        }
    }

    /** {@code "DEFAULT 'F'"} → {@code "'F'"}, {@code "CHECK (x)"} → {@code "(x)"}; {@code null}/blank → {@code null}. */
    public static String stripKeyword(String source, String keyword) {
        String s = trimToNull(source);
        if (s == null) return null;
        int n = keyword.length();
        if (s.regionMatches(true, 0, keyword, 0, n)
                && (s.length() == n || !isIdentifierChar(s.charAt(n)))) {
            return trimToNull(s.substring(n));
        }
        return s;
    }

    private static boolean isIdentifierChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$';
    }

    private static Relation relation(Connection c, String table) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT r.RDB$VIEW_SOURCE AS vsrc, r.RDB$DESCRIPTION AS descr "
              + "FROM RDB$RELATIONS r WHERE r.RDB$RELATION_NAME = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Relation(trimToNull(rs.getString("vsrc")), trimToNull(rs.getString("descr")));
            }
        }
    }

    private static String databaseCharset(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT TRIM(RDB$CHARACTER_SET_NAME) AS cs FROM RDB$DATABASE");
             ResultSet rs = ps.executeQuery()) {
            String cs = rs.next() ? rs.getString("cs") : null;
            return cs == null || cs.isBlank() ? "NONE" : cs;
        }
    }

    private static List<FbColumn> columns(Connection c, String table, Map<String, FbDomain> domains)
            throws SQLException {
        List<FbColumn> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(COLUMNS_SQL)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FieldType type = new FieldType(rs.getInt("ftype"), intOrNull(rs, "fsub"),
                        intOrNull(rs, "clen"), intOrNull(rs, "flen"), intOrNull(rs, "bpc"),
                        intOrNull(rs, "prec"), intOrNull(rs, "fscale"), intOrNull(rs, "seg"));
                    boolean text = FirebirdTypes.isText(type);
                    String nativeType = FirebirdTypes.nativeType(type);
                    String charset = text ? orDefault(rs.getString("cs_name"), "NONE") : null;
                    String charsetDefaultCollation = text ? rs.getString("cs_coll") : null;
                    String domainCollation = text ? rs.getString("f_coll") : null;
                    String columnCollation = text ? rs.getString("rf_coll") : null;
                    String source = rs.getString("src");
                    boolean namedDomain = source != null && !SystemNames.isSystemGenerated(source);

                    boolean domainNotNull = Integer.valueOf(1).equals(intOrNull(rs, "f_nn"));
                    String domainDefault = stripKeyword(rs.getString("f_def"), "DEFAULT");
                    boolean columnNotNull = Integer.valueOf(1).equals(intOrNull(rs, "rf_nn"));
                    String columnDefault = stripKeyword(rs.getString("rf_def"), "DEFAULT");
                    if (!namedDomain) {
                        // Implicit domains: whatever the catalog put on RDB$FIELDS belongs to the column.
                        columnNotNull = columnNotNull || domainNotNull;
                        if (columnDefault == null) columnDefault = domainDefault;
                    } else if (!domains.containsKey(source)) {
                        domains.put(source, new FbDomain(source, nativeType, charset, domainCollation,
                            charsetDefaultCollation, domainNotNull, domainDefault,
                            trimToNull(rs.getString("f_chk"))));
                    }

                    out.add(new FbColumn(rs.getString("name"), out.size() + 1, type, nativeType, charset,
                        columnCollation != null ? columnCollation : domainCollation, charsetDefaultCollation,
                        namedDomain ? source : null, columnNotNull, columnDefault, columnCollation,
                        trimToNull(rs.getString("f_comp")), intOrNull(rs, "idt"),
                        trimToNull(rs.getString("idgen")), trimToNull(rs.getString("descr")), null));
                }
            }
        }
        return out;
    }

    private static List<FbColumn> linkSequences(List<FbColumn> columns, List<FbTrigger> triggers) {
        Map<String, FbSequenceLink> links = new HashMap<>();
        Set<String> names = new HashSet<>();
        for (FbColumn col : columns) {
            names.add(col.name());
            if (col.identityType() != null && col.identityGenerator() != null) {
                links.put(col.name(), new FbSequenceLink(col.identityGenerator(), "identity", null));
            }
        }
        for (FbTrigger t : triggers) {
            if (t.body() == null || t.generators().isEmpty()) continue;
            Matcher m = ASSIGNMENT.matcher(COMMENTS.matcher(t.body()).replaceAll(" "));
            while (m.find()) {
                String column = m.group(1) != null ? m.group(1) : m.group(2).toUpperCase(Locale.ROOT);
                String generator = m.group(3) != null ? m.group(3)
                    : m.group(4) != null ? m.group(4).toUpperCase(Locale.ROOT)
                    : m.group(5) != null ? m.group(5)
                    : m.group(6).toUpperCase(Locale.ROOT);
                if (names.contains(column) && t.generators().contains(generator)) {
                    links.putIfAbsent(column, new FbSequenceLink(generator, "trigger", t.name()));
                }
            }
        }
        List<FbColumn> out = new ArrayList<>(columns.size());
        for (FbColumn col : columns) {
            FbSequenceLink link = links.get(col.name());
            out.add(link == null ? col : new FbColumn(col.name(), col.position(), col.type(), col.nativeType(),
                col.charset(), col.collation(), col.charsetDefaultCollation(), col.domainName(),
                col.columnNotNull(), col.columnDefault(), col.columnCollation(), col.computedSource(),
                col.identityType(), col.identityGenerator(), col.description(), link));
        }
        return List.copyOf(out);
    }

    private static List<Constraint> constraints(Connection c, String table) throws SQLException {
        List<Constraint> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(CONSTRAINTS_SQL)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Constraint(rs.getString("cname"), rs.getString("ctype"),
                        trimToNull(rs.getString("iname"))));
                }
            }
        }
        return out;
    }

    private static Map<String, RawIndex> indexes(Connection c, String table) throws SQLException {
        Map<String, RawIndex> out = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(INDEXES_SQL)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("iname");
                    out.put(name, new RawIndex(name, rs.getInt("uniq") == 1, rs.getInt("itype") == 1,
                        rs.getInt("inactive") == 0, trimToNull(rs.getString("expr"))));
                }
            }
        }
        return out;
    }

    private static Map<String, List<String>> segments(Connection c, String table) throws SQLException {
        Map<String, List<String>> out = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(SEGMENTS_SQL)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String index = rs.getString("iname");
                    String field = rs.getString("fname");
                    out.computeIfAbsent(index, k -> new ArrayList<>()).add(field);
                }
            }
        }
        Map<String, List<String>> frozen = new HashMap<>();
        out.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
        return frozen;
    }

    private static List<String> indexSegments(Connection c, String index) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(INDEX_SEGMENTS_SQL)) {
            ps.setString(1, index);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString("fname"));
            }
        }
        return List.copyOf(out);
    }

    private static List<FbForeignKey> foreignKeys(Connection c, String table, List<Constraint> constraints,
                                                  Map<String, List<String>> segments,
                                                  Map<String, RawIndex> rawIndexes) throws SQLException {
        Map<String, String> indexByConstraint = new HashMap<>();
        for (Constraint k : constraints) indexByConstraint.put(k.name(), k.indexName());
        record Row(String name, String upd, String del, String refTable, String refIndex) {}
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(FOREIGN_KEYS_SQL)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(rs.getString("cname"), rs.getString("upd"), rs.getString("del"),
                        rs.getString("ref_table"), rs.getString("ref_index")));
                }
            }
        }
        List<FbForeignKey> out = new ArrayList<>();
        for (Row r : rows) {
            String index = indexByConstraint.get(r.name());
            out.add(new FbForeignKey(r.name(), segments.getOrDefault(index, List.of()), r.refTable(),
                indexSegments(c, r.refIndex()), r.upd(), r.del(), index, isDescending(rawIndexes, index)));
        }
        return List.copyOf(out);
    }

    private static List<FbCheck> checks(Connection c, String table) throws SQLException {
        List<FbCheck> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(CHECKS_SQL)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new FbCheck(rs.getString("cname"), trimToNull(rs.getString("src"))));
            }
        }
        return List.copyOf(out);
    }

    private static Map<String, List<String>> triggerGenerators(Connection c, String table) throws SQLException {
        Map<String, List<String>> out = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(TRIGGER_GENERATORS_SQL)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String trigger = rs.getString("tname");
                    String generator = rs.getString("gname");
                    out.computeIfAbsent(trigger, k -> new ArrayList<>()).add(generator);
                }
            }
        }
        return out;
    }

    private static List<FbTrigger> triggers(Connection c, String table, Map<String, List<String>> generators)
            throws SQLException {
        List<FbTrigger> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(TRIGGERS_SQL)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("tname");
                    FirebirdTriggerType type = FirebirdTriggerType.decode(rs.getInt("ttype"));
                    String body = rs.getString("src");
                    out.add(new FbTrigger(name, type.timing(), type.events(), rs.getInt("pos"),
                        rs.getInt("inactive") == 0, body == null ? null : body.strip(),
                        List.copyOf(new TreeSet<>(generators.getOrDefault(name, List.of())))));
                }
            }
        }
        out.sort(Comparator.comparing((FbTrigger t) -> "BEFORE".equals(t.timing()) ? 0 : 1)
            .thenComparingInt(FbTrigger::position)
            .thenComparing(FbTrigger::name));
        return List.copyOf(out);
    }

    private static boolean isDescending(Map<String, RawIndex> rawIndexes, String index) {
        RawIndex i = index == null ? null : rawIndexes.get(index);
        return i != null && i.descending();
    }

    private static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    private static String orDefault(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }
}
