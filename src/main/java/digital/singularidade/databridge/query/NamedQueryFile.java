package digital.singularidade.databridge.query;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A file of named queries, versioned by the consumer next to its code:
 *
 * <pre>
 * -- name: colunas
 * -- optional description lines
 * SELECT COLUNA, DESCRICAO FROM TBSGHCOLUNAS WHERE TABELA = :tabela ORDER BY COLUNA;
 * </pre>
 *
 * Each block holds one statement; a trailing {@code ;} is optional. Only comments and blank lines may
 * come before the first block.
 */
public final class NamedQueryFile {

    private static final Pattern MARKER = Pattern.compile("^\\s*--\\s*name:\\s*([A-Za-z_][A-Za-z0-9_.-]*)\\s*$");

    private NamedQueryFile() {}

    public static Map<String, NamedQuery> read(Path file) {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw invalid("cannot read --file " + file + ": " + e.getMessage());
        }
    }

    public static Map<String, NamedQuery> parse(String text) {
        Map<String, NamedQuery> out = new LinkedHashMap<>();
        String current = null;
        List<String> body = new ArrayList<>();
        for (String line : text.split("\\R", -1)) {
            Matcher m = MARKER.matcher(line);
            if (m.matches()) {
                add(out, current, body);
                current = m.group(1);
                body = new ArrayList<>();
                continue;
            }
            if (current == null) {
                String t = line.strip();
                if (t.isEmpty() || t.startsWith("--")) continue;
                throw invalid("SQL found before the first '-- name:' line: " + t);
            }
            body.add(line);
        }
        add(out, current, body);
        if (out.isEmpty()) throw invalid("no query in the file (each block starts with '-- name: <id>')");
        return out;
    }

    private static void add(Map<String, NamedQuery> out, String name, List<String> body) {
        if (name == null) return;
        int first = 0;
        while (first < body.size() && (body.get(first).isBlank() || body.get(first).strip().startsWith("--"))) first++;
        String sql = String.join("\n", body.subList(first, body.size())).stripTrailing();
        if (sql.endsWith(";")) sql = sql.substring(0, sql.length() - 1).stripTrailing();
        if (sql.isEmpty()) throw invalid("query '" + name + "' is empty");
        if (out.containsKey(name)) throw invalid("duplicate query name '" + name + "'");
        out.put(name, new NamedQuery(name, sql));
    }

    private static DataBridgeException invalid(String message) {
        return new DataBridgeException(ErrorCodes.INVALID_ARGS, message,
            "Blocks look like: -- name: <id>, then one SQL statement using :param placeholders");
    }
}
