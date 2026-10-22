package digital.singularidade.databridge.source.jdbc;

import java.util.Properties;

/**
 * JDBC URL plus credentials given outside it. {@code user}/{@code password} are {@code null} when the
 * URL carries them (or the driver needs none).
 */
public record ConnectionSettings(String url, String user, String password) {

    /** Driver properties: only the credentials that were given. */
    public Properties toProperties() {
        Properties p = new Properties();
        if (user != null) p.setProperty("user", user);
        if (password != null) p.setProperty("password", password);
        return p;
    }

    @Override
    public String toString() {
        return "ConnectionSettings[url=" + url + ", user=" + user + ", password=" + (password == null ? null : "***") + "]";
    }
}
