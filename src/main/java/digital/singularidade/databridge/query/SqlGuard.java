package digital.singularidade.databridge.query;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.source.jdbc.DriverHints;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Statement guard for {@code query} and {@code run-query}, applied to the blanked SQL
 * ({@link SqlScanner}) so comments and literals can neither hide a statement nor trigger a false alarm:
 * one statement only; first keyword from an allowlist (DML only with {@code --writable}; DDL and
 * procedural/session statements never); in read-only mode no data-modifying clause anywhere, and on
 * Firebird no generator increment (generators advance even inside read-only transactions).
 *
 * <p>This guards against accidents. Against an adversary only a database user with SELECT-only grants binds.
 */
public final class SqlGuard {

    private static final Set<String> READ = Set.of("SELECT", "WITH", "EXPLAIN", "SHOW");
    private static final Set<String> DML = Set.of("INSERT", "UPDATE", "DELETE", "MERGE", "UPSERT", "REPLACE");
    private static final Set<String> DDL = Set.of("CREATE", "ALTER", "DROP", "TRUNCATE", "GRANT", "REVOKE",
        "RECREATE", "COMMENT", "DECLARE", "RENAME");

    private static final Pattern DATA_MODIFYING = Pattern.compile(
        "\\b(INSERT\\s+INTO|DELETE|MERGE|UPSERT)\\b|\\bUPDATE\\s+\\S+\\s+SET\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern GEN_ID_INCREMENT = Pattern.compile(
        "\\bGEN_ID\\s*\\(\\s*[^,()]+,(?!\\s*0\\s*\\))", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_VALUE_FOR = Pattern.compile(
        "\\bNEXT\\s+VALUE\\s+FOR\\b", Pattern.CASE_INSENSITIVE);

    private SqlGuard() {}

    /** Returns the SQL to execute (one trailing {@code ;} removed) or throws INVALID_ARGS. */
    public static String check(String sql, DriverHints driver, boolean writable) {
        if (sql == null || sql.isBlank()) throw invalid("sql is required", null);
        String blanked = SqlScanner.blank(sql);
        int end = blanked.length();
        while (end > 0 && Character.isWhitespace(blanked.charAt(end - 1))) end--;
        String runnable = sql.substring(0, end);
        if (end > 0 && blanked.charAt(end - 1) == ';') {
            runnable = sql.substring(0, end - 1).stripTrailing();
            blanked = blanked.substring(0, end - 1);
        } else {
            blanked = blanked.substring(0, end);
        }
        if (blanked.indexOf(';') >= 0) {
            throw invalid("multi-statement queries are not allowed", "submit one statement per call");
        }
        String first = firstKeyword(blanked);
        if (DDL.contains(first)) {
            throw invalid("DDL is not allowed (use Liquibase/Flyway for schema changes)", null);
        }
        if (!READ.contains(first) && !DML.contains(first)) {
            throw invalid("statement type '" + first + "' is not allowed",
                "only SELECT, WITH, EXPLAIN and SHOW run (plus INSERT/UPDATE/DELETE/MERGE with --writable)");
        }
        if (!writable) {
            if (DML.contains(first)) {
                throw invalid("non-SELECT statement requires --writable",
                    "pass --writable to allow INSERT/UPDATE/DELETE; data-bridge never permits DDL");
            }
            if (DATA_MODIFYING.matcher(blanked).find()) {
                throw invalid("data-modifying statement requires --writable", null);
            }
            if (driver == DriverHints.FIREBIRD && GEN_ID_INCREMENT.matcher(blanked).find()) {
                throw invalid("GEN_ID with a non-zero increment advances the generator even in read-only mode",
                    "use GEN_ID(name, 0) to read the current value");
            }
            if (driver == DriverHints.FIREBIRD && NEXT_VALUE_FOR.matcher(blanked).find()) {
                throw invalid("NEXT VALUE FOR advances the generator even in read-only mode",
                    "use GEN_ID(name, 0) to read the current value");
            }
        }
        return runnable;
    }

    private static String firstKeyword(String blanked) {
        int i = 0;
        int n = blanked.length();
        while (i < n && (Character.isWhitespace(blanked.charAt(i)) || blanked.charAt(i) == '(')) i++;
        int start = i;
        while (i < n && (Character.isLetter(blanked.charAt(i)) || blanked.charAt(i) == '_')) i++;
        return blanked.substring(start, i).toUpperCase(Locale.ROOT);
    }

    private static DataBridgeException invalid(String message, String hint) {
        return new DataBridgeException(ErrorCodes.INVALID_ARGS, message, hint);
    }
}
