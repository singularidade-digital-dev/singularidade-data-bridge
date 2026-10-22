package digital.singularidade.databridge.query;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lexical pass shared by {@link SqlGuard} and {@link BoundSql}: a copy of the SQL, same length, where
 * comments and the contents of string literals, quoted identifiers and PostgreSQL dollar-quoted bodies are
 * replaced by spaces (delimiters and line breaks kept). Keywords, {@code ;} and {@code :param} found in the
 * blanked copy are real code, and positions map 1:1 back to the original.
 */
final class SqlScanner {

    private static final Pattern DOLLAR_TAG = Pattern.compile("\\$[A-Za-z_]?[A-Za-z0-9_]*\\$");

    private SqlScanner() {}

    static String blank(String sql) {
        char[] out = sql.toCharArray();
        int n = sql.length();
        int i = 0;
        while (i < n) {
            char c = sql.charAt(i);
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                while (i < n && sql.charAt(i) != '\n') out[i++] = ' ';
                continue;
            }
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                blankRange(sql, out, i, stop);
                i = stop;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                int j = i + 1;
                while (j < n) {
                    if (sql.charAt(j) == c) {
                        if (j + 1 < n && sql.charAt(j + 1) == c) {      // doubled quote = escaped quote
                            out[j] = ' ';
                            out[j + 1] = ' ';
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    if (sql.charAt(j) != '\n') out[j] = ' ';
                    j++;
                }
                i = j + 1;
                continue;
            }
            if (c == '$' && (i == 0 || !isIdentifierChar(sql.charAt(i - 1)))) {
                Matcher m = DOLLAR_TAG.matcher(sql).region(i, n);
                if (m.lookingAt()) {
                    String tag = m.group();
                    int bodyStart = i + tag.length();
                    int end = sql.indexOf(tag, bodyStart);
                    blankRange(sql, out, bodyStart, end < 0 ? n : end);
                    i = end < 0 ? n : end + tag.length();
                    continue;
                }
            }
            i++;
        }
        return new String(out);
    }

    private static void blankRange(String sql, char[] out, int from, int to) {
        for (int j = from; j < to; j++) {
            if (sql.charAt(j) != '\n') out[j] = ' ';
        }
    }

    static boolean isIdentifierChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$';
    }
}
