package digital.singularidade.databridge.error;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlRedaction {

    private static final Pattern QUERY_PASSWORD = Pattern.compile(
        "([?&])(password|passwd)=([^&]*)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern USERINFO_PASSWORD = Pattern.compile(
        "://([^:/@\\s]+):([^@\\s]*)@"
    );

    private UrlRedaction() {}

    public static String redact(String url) {
        if (url == null) return null;
        String result = QUERY_PASSWORD.matcher(url).replaceAll(mr ->
            Matcher.quoteReplacement(mr.group(1) + mr.group(2) + "=***"));
        result = USERINFO_PASSWORD.matcher(result).replaceAll(mr ->
            Matcher.quoteReplacement("://" + mr.group(1) + ":***@"));
        return result;
    }

    private static final Pattern HOST_PORT = Pattern.compile(
        "(jdbc:[a-z]+://)([^/?\\s]+)"
    );

    /**
     * Replace the host[:port] portion of a JDBC URL with {@code [redacted-host]}.
     * Driver scheme, path (db name), and query params remain.
     */
    public static String redactHostPort(String url) {
        if (url == null) return null;
        return HOST_PORT.matcher(url).replaceFirst("$1[redacted-host]");
    }

    /**
     * Replace everything after the JDBC scheme with {@code [redacted]}.
     * Result preserves only the driver name (e.g. {@code jdbc:postgresql:[redacted]}).
     */
    public static String redactFull(String url) {
        if (url == null) return null;
        int colonAfterDriver = url.indexOf(':', url.indexOf(':') + 1);
        if (colonAfterDriver < 0) return "[redacted]";
        return url.substring(0, colonAfterDriver + 1) + "[redacted]";
    }
}
