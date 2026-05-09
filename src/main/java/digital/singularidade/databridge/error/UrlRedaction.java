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
}
