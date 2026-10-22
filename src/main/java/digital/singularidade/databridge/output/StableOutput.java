package digital.singularidade.databridge.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * {@code --stable}: the output trees without what changes between runs or databases — timestamps,
 * versions, URLs, warnings, data-derived numbers, names the database generated — and with every list in
 * a fixed order, so the files can be committed and diffed.
 */
public final class StableOutput {

    private static final String LAST = "￿";

    private StableOutput() {}

    public static ObjectNode metadata(ObjectMapper mapper, Metadata m) {
        ObjectNode root = mapper.valueToTree(m);
        root.remove(List.of("generatedAt", "generator", "warnings", "sample", "columnStats", "cardinality"));
        removeField(root.get("source"), "url");
        removeField(root.get("tableInfo"), "approximateRowCount");

        for (JsonNode c : array(root, "columns")) {
            JsonNode seq = c.get("sequence");
            if (seq instanceof ObjectNode o) nullSystemName(o, "name");
        }
        sort(root, "columns", Comparator.comparingInt(n -> n.path("ordinalPosition").asInt()));

        for (JsonNode fk : array(root, "foreignKeys")) {
            ((ObjectNode) fk).remove("refTableColumns");
            nullSystemName((ObjectNode) fk, "constraintName");
        }
        sort(root, "foreignKeys", Comparator.comparing(text("refTable")).thenComparing(joined("fkColumns")));

        for (JsonNode ix : array(root, "indexes")) {
            nullSystemName((ObjectNode) ix, "name");
            nullSystemName((ObjectNode) ix, "constraint");
        }
        sort(root, "indexes", Comparator.comparing(textOr("name", LAST))
            .thenComparing(joined("columns")).thenComparing(text("expression")));

        for (JsonNode u : array(root, "uniqueConstraints")) nullSystemName((ObjectNode) u, "name");
        sort(root, "uniqueConstraints", Comparator.comparing(joined("columns")));

        for (JsonNode ck : array(root, "checkConstraints")) nullSystemName((ObjectNode) ck, "name");
        sort(root, "checkConstraints", Comparator.comparing(text("definition")));

        sort(root, "triggers", Comparator.comparing((JsonNode t) -> "BEFORE".equals(t.path("timing").asText()) ? 0 : 1)
            .thenComparingInt(t -> t.path("position").asInt()).thenComparing(text("name")));
        return root;
    }

    public static ObjectNode index(ObjectMapper mapper, ExtractAllIndex idx) {
        ObjectNode root = mapper.valueToTree(idx);
        root.remove(List.of("generatedAt", "generator"));
        removeField(root.get("source"), "url");
        for (JsonNode t : array(root, "tables")) ((ObjectNode) t).remove("totalRows");
        sort(root, "tables", Comparator.comparing(text("schema")).thenComparing(text("table")));
        return root;
    }

    private static List<JsonNode> array(ObjectNode root, String field) {
        List<JsonNode> out = new ArrayList<>();
        JsonNode a = root.get(field);
        if (a != null && a.isArray()) a.forEach(out::add);
        return out;
    }

    private static void sort(ObjectNode root, String field, Comparator<JsonNode> order) {
        JsonNode a = root.get(field);
        if (a == null || !a.isArray()) return;
        List<JsonNode> items = array(root, field);
        items.sort(order);
        ArrayNode sorted = root.putArray(field);
        items.forEach(sorted::add);
    }

    private static void removeField(JsonNode node, String field) {
        if (node instanceof ObjectNode o) o.remove(field);
    }

    private static void nullSystemName(ObjectNode node, String field) {
        JsonNode v = node.get(field);
        if (v != null && v.isTextual() && SystemNames.isSystemGenerated(v.asText())) node.putNull(field);
    }

    private static Function<JsonNode, String> text(String field) {
        return textOr(field, "");
    }

    private static Function<JsonNode, String> textOr(String field, String whenNull) {
        return n -> {
            JsonNode v = n.get(field);
            return v == null || v.isNull() ? whenNull : v.asText();
        };
    }

    private static Function<JsonNode, String> joined(String field) {
        return n -> {
            List<String> parts = new ArrayList<>();
            n.path(field).forEach(v -> parts.add(v.asText()));
            return String.join(",", parts);
        };
    }
}
