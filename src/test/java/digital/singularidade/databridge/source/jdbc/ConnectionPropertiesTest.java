package digital.singularidade.databridge.source.jdbc;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionPropertiesTest {

    private static Properties props(String url) {
        return JdbcSource.connectionProperties(DriverHints.fromUrl(url), new ConnectionSettings(url, "U", "P"));
    }

    @Test
    void should_default_firebird_connections_to_utf8() {
        assertThat(props("jdbc:firebirdsql://h:3050//db.fdb")).containsEntry("encoding", "UTF8")
            .containsEntry("user", "U").containsEntry("password", "P");
    }

    @Test
    void should_keep_a_charset_given_in_the_url() {
        assertThat(props("jdbc:firebirdsql://h:3050//db.fdb?encoding=WIN1252")).doesNotContainKey("encoding");
        assertThat(props("jdbc:firebirdsql://h:3050//db.fdb?charSet=Cp1252")).doesNotContainKey("encoding");
        assertThat(props("jdbc:firebirdsql://h:3050//db.fdb?lc_ctype=ISO8859_1")).doesNotContainKey("encoding");
    }

    @Test
    void should_not_touch_other_drivers() {
        assertThat(props("jdbc:postgresql://h/d")).doesNotContainKey("encoding");
    }
}
