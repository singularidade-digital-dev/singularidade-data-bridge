package digital.singularidade.databridge.query;

import java.util.ArrayList;
import java.util.List;

/**
 * A statement with its {@code :name} placeholders turned into JDBC {@code ?}, in order (a name used twice
 * appears twice). Placeholders inside string literals, comments and quoted identifiers are left alone, and
 * so is the PostgreSQL cast {@code ::type}.
 */
public record BoundSql(String jdbcSql, List<String> parameterNames) {

    public static BoundSql of(String sql) {
        String blanked = SqlScanner.blank(sql);
        StringBuilder out = new StringBuilder(sql.length());
        List<String> names = new ArrayList<>();
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = blanked.charAt(i);
            if (c == ':' && i + 1 < n && blanked.charAt(i + 1) == ':') {
                out.append("::");
                i += 2;
                continue;
            }
            boolean afterIdentifier = i > 0 && SqlScanner.isIdentifierChar(blanked.charAt(i - 1));
            if (c == ':' && !afterIdentifier && i + 1 < n && isNameStart(blanked.charAt(i + 1))) {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(blanked.charAt(j)) || blanked.charAt(j) == '_')) j++;
                names.add(sql.substring(i + 1, j));
                out.append('?');
                i = j;
                continue;
            }
            out.append(sql.charAt(i));
            i++;
        }
        return new BoundSql(out.toString(), List.copyOf(names));
    }

    private static boolean isNameStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }
}
