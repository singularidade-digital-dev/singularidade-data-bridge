package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.error.UrlRedaction;

/**
 * The one place that prints the error document on stderr:
 * {@code {"error":{"code":..., "message":..., "hint":..., "cause":...}}} — escaped, password-redacted —
 * and returns the matching exit code.
 */
public final class CliErrors {

    private CliErrors() {}

    public static int report(DataBridgeException e, boolean verbose) {
        StringBuilder sb = new StringBuilder("{\"error\":{\"code\":\"")
            .append(e.code().wireName()).append("\",\"message\":\"")
            .append(escape(UrlRedaction.redact(e.getMessage()))).append('"');
        if (e.hint() != null) sb.append(",\"hint\":\"").append(escape(e.hint())).append('"');
        if (verbose && e.getCause() != null) {
            sb.append(",\"cause\":\"")
              .append(escape(UrlRedaction.redact(e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage())))
              .append('"');
        }
        sb.append("}}");
        System.err.println(sb);
        if (verbose && e.getCause() != null) e.getCause().printStackTrace(System.err);
        return e.code().exitCode();
    }

    public static int report(Exception e, boolean verbose) {
        if (e instanceof DataBridgeException dbe) return report(dbe, verbose);
        return report(new DataBridgeException(ErrorCodes.UNSPECIFIED, e.getMessage(), null, e), verbose);
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
                }
            }
        }
        return sb.toString();
    }
}
