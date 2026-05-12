package digital.singularidade.databridge.error;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UrlRedactionTest {

    @Test
    void redacts_password_query_param() {
        String in = "jdbc:postgresql://host/db?user=u&password=secret&sslmode=require";
        assertThat(UrlRedaction.redact(in))
            .isEqualTo("jdbc:postgresql://host/db?user=u&password=***&sslmode=require");
    }

    @Test
    void redacts_passwd_alias() {
        String in = "jdbc:mysql://host/db?passwd=secret";
        assertThat(UrlRedaction.redact(in)).isEqualTo("jdbc:mysql://host/db?passwd=***");
    }

    @Test
    void case_insensitive() {
        String in = "jdbc:postgresql://host/db?Password=secret";
        assertThat(UrlRedaction.redact(in)).isEqualTo("jdbc:postgresql://host/db?Password=***");
    }

    @Test
    void leaves_url_without_password_unchanged() {
        String in = "jdbc:postgresql://host/db?user=u&sslmode=require";
        assertThat(UrlRedaction.redact(in)).isEqualTo(in);
    }

    @Test
    void handles_password_at_first_position() {
        String in = "jdbc:postgresql://host/db?password=secret&user=u";
        assertThat(UrlRedaction.redact(in))
            .isEqualTo("jdbc:postgresql://host/db?password=***&user=u");
    }

    @Test
    void handles_userinfo_form() {
        // jdbc:mysql://user:pass@host/db
        String in = "jdbc:mysql://user:secret@host/db";
        assertThat(UrlRedaction.redact(in)).isEqualTo("jdbc:mysql://user:***@host/db");
    }

    @Test
    void null_input_returns_null() {
        assertThat(UrlRedaction.redact(null)).isNull();
    }

    @Test
    void redactHostPort_replaces_host_only() {
        assertThat(UrlRedaction.redactHostPort("jdbc:postgresql://orgen-prod.aws.com:5432/orgen?user=u&password=p"))
            .isEqualTo("jdbc:postgresql://[redacted-host]/orgen?user=u&password=p");
    }

    @Test
    void redactHostPort_handles_no_port() {
        assertThat(UrlRedaction.redactHostPort("jdbc:postgresql://hostname/dbname"))
            .isEqualTo("jdbc:postgresql://[redacted-host]/dbname");
    }

    @Test
    void redactHostPort_works_for_mysql_and_oracle_and_firebird() {
        assertThat(UrlRedaction.redactHostPort("jdbc:mysql://h:3306/d?user=u"))
            .isEqualTo("jdbc:mysql://[redacted-host]/d?user=u");
        assertThat(UrlRedaction.redactHostPort("jdbc:firebirdsql://h:3050//var/lib/db.fdb"))
            .isEqualTo("jdbc:firebirdsql://[redacted-host]//var/lib/db.fdb");
    }

    @Test
    void redactHostPort_null_returns_null() {
        assertThat(UrlRedaction.redactHostPort(null)).isNull();
    }

    @Test
    void redactFull_keeps_only_driver_scheme() {
        assertThat(UrlRedaction.redactFull("jdbc:postgresql://anything/here?with=params"))
            .isEqualTo("jdbc:postgresql:[redacted]");
        assertThat(UrlRedaction.redactFull("jdbc:mysql://h:3306/d"))
            .isEqualTo("jdbc:mysql:[redacted]");
    }

    @Test
    void redactFull_null_returns_null() {
        assertThat(UrlRedaction.redactFull(null)).isNull();
    }
}
