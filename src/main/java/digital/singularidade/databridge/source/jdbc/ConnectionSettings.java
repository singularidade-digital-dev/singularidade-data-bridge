package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.source.jdbc.firebird.NoneText;

import java.util.Properties;

/**
 * JDBC URL plus what is given outside it: credentials ({@code null} when the URL carries them or the driver
 * needs none) and, for Firebird, the fallback charset for {@code CHARACTER SET NONE} text that is not
 * valid UTF-8 (see {@link NoneText}).
 */
public record ConnectionSettings(String url, String user, String password, String noneCharset) {

    public ConnectionSettings(String url, String user, String password) {
        this(url, user, password, NoneText.DEFAULT_FALLBACK);
    }

    /** Driver properties: only the credentials that were given. */
    public Properties toProperties() {
        Properties p = new Properties();
        if (user != null) p.setProperty("user", user);
        if (password != null) p.setProperty("password", password);
        return p;
    }

    @Override
    public String toString() {
        return "ConnectionSettings[url=" + url + ", user=" + user + ", password="
            + (password == null ? null : "***") + ", noneCharset=" + noneCharset + "]";
    }
}
