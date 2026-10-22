package digital.singularidade.databridge.output;

import java.util.regex.Pattern;

/**
 * Names the database invents for objects the user left unnamed. They are stable inside one database
 * but differ between databases (and change when the object is recreated), so they are not identities:
 * {@code --stable} output drops them and Firebird DDL never writes them.
 */
public final class SystemNames {

    // Firebird: INTEG_n (constraints), RDB$PRIMARYn / RDB$FOREIGNn / RDB$n (indexes), RDB$n (implicit
    // domains, identity generators).
    private static final Pattern GENERATED = Pattern.compile("INTEG_\\d+|RDB\\$(PRIMARY|FOREIGN)?\\d+");

    private SystemNames() {}

    public static boolean isSystemGenerated(String name) {
        return name != null && GENERATED.matcher(name).matches();
    }
}
