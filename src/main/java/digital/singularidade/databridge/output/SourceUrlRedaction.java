package digital.singularidade.databridge.output;

import digital.singularidade.databridge.error.UrlRedaction;

/**
 * How aggressively the {@code source.url} field is scrubbed in metadata.json output.
 * Always applied AFTER {@link UrlRedaction#redact(String)} (which only handles passwords).
 *
 * <ul>
 *   <li>{@link #NONE}: keep host:port, just password redacted (legacy behavior).</li>
 *   <li>{@link #HOST_PORT} (default): replace host[:port] with {@code [redacted-host]}.
 *       Driver, db name, user, query params remain visible.</li>
 *   <li>{@link #FULL}: keep only the JDBC scheme; replace everything after with {@code [redacted]}.</li>
 * </ul>
 */
public enum SourceUrlRedaction {
    NONE,
    HOST_PORT,
    FULL;

    public String wireName() {
        return switch (this) {
            case NONE -> "none";
            case HOST_PORT -> "host-port";
            case FULL -> "full";
        };
    }

    public static SourceUrlRedaction fromWireName(String s) {
        if (s == null || s.isBlank()) return HOST_PORT;
        return switch (s.trim().toLowerCase()) {
            case "none", "off" -> NONE;
            case "host-port", "host_port", "host" -> HOST_PORT;
            case "full", "all" -> FULL;
            default -> throw new IllegalArgumentException(
                "unknown source-url-redaction: '" + s + "' (expected none, host-port, or full)");
        };
    }

    /** Apply the password-redaction THEN the chosen host-redaction. Returns {@code null} for {@code null} input. */
    public String apply(String url) {
        if (url == null) return null;
        String passwordRedacted = UrlRedaction.redact(url);
        return switch (this) {
            case NONE -> passwordRedacted;
            case HOST_PORT -> UrlRedaction.redactHostPort(passwordRedacted);
            case FULL -> UrlRedaction.redactFull(passwordRedacted);
        };
    }
}
