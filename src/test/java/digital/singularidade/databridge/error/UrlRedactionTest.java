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
}
