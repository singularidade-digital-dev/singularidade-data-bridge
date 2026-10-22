package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.source.jdbc.ConnectionSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectionOptionsTest {

    private static final Function<String, String> NO_ENV = k -> null;

    private static ConnectionOptions parse(String... args) {
        ConnectionOptions o = new ConnectionOptions();
        new CommandLine(o).parseArgs(args);
        return o;
    }

    private static void assertInvalid(ConnectionOptions o, Function<String, String> env, String message) {
        assertThatThrownBy(() -> o.resolve(env))
            .isInstanceOfSatisfying(DataBridgeException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCodes.INVALID_ARGS))
            .hasMessageContaining(message);
    }

    @Test
    void should_pass_the_url_alone_without_credential_flags() {
        assertThat(parse("--jdbc-url", "jdbc:firebirdsql://h/db?user=SYSDBA&password=x").resolve(NO_ENV))
            .isEqualTo(new ConnectionSettings("jdbc:firebirdsql://h/db?user=SYSDBA&password=x", null, null));
    }

    @Test
    void should_read_the_password_from_an_environment_variable() {
        ConnectionOptions o = parse("--jdbc-url", "jdbc:firebirdsql://h/db", "--user", "SYSDBA",
            "--password-env", "PW");
        assertThat(o.resolve(k -> k.equals("PW") ? "s3:cr#t&x=y" : null))
            .isEqualTo(new ConnectionSettings("jdbc:firebirdsql://h/db", "SYSDBA", "s3:cr#t&x=y"));
    }

    @Test
    void should_strip_only_the_final_line_break_from_a_password_file(@TempDir Path tmp) throws Exception {
        Path lf = Files.writeString(tmp.resolve("lf"), "a b\\c\"' \n");
        Path crlf = Files.writeString(tmp.resolve("crlf"), "pw\r\n");
        Path inner = Files.writeString(tmp.resolve("inner"), "a\nb\n");
        assertThat(parse("--jdbc-url", "u", "--password-file", lf.toString()).resolve(NO_ENV).password())
            .isEqualTo("a b\\c\"' ");
        assertThat(parse("--jdbc-url", "u", "--password-file", crlf.toString()).resolve(NO_ENV).password())
            .isEqualTo("pw");
        assertThat(parse("--jdbc-url", "u", "--password-file", inner.toString()).resolve(NO_ENV).password())
            .isEqualTo("a\nb");
    }

    @Test
    void should_allow_the_user_in_the_url_with_the_password_from_a_flag() {
        ConnectionOptions o = parse("--jdbc-url", "jdbc:firebirdsql://h/db?user=SYSDBA", "--password-env", "PW");
        assertThat(o.resolve(k -> "x").user()).isNull();
        assertThat(o.resolve(k -> "x").password()).isEqualTo("x");
    }

    @Test
    void should_reject_two_password_sources(@TempDir Path tmp) throws Exception {
        Path f = Files.writeString(tmp.resolve("pw"), "x");
        assertInvalid(parse("--jdbc-url", "u", "--password-env", "PW", "--password-file", f.toString()),
            k -> "y", "only one of");
    }

    @Test
    void should_reject_a_missing_environment_variable() {
        assertInvalid(parse("--jdbc-url", "u", "--password-env", "NOPE"), NO_ENV, "NOPE");
    }

    @Test
    void should_reject_an_empty_or_missing_password_file(@TempDir Path tmp) throws Exception {
        Path empty = Files.writeString(tmp.resolve("empty"), "\n");
        assertInvalid(parse("--jdbc-url", "u", "--password-file", empty.toString()), NO_ENV, "empty");
        assertInvalid(parse("--jdbc-url", "u", "--password-file", tmp.resolve("missing").toString()),
            NO_ENV, "cannot read");
    }

    @Test
    void should_reject_credentials_given_twice() {
        assertInvalid(parse("--jdbc-url", "jdbc:postgresql://h/d?password=x", "--password-env", "PW"),
            k -> "y", "password");
        assertInvalid(parse("--jdbc-url", "jdbc:firebirdsql://h/db?user=SYSDBA", "--user", "OTHER"),
            NO_ENV, "user");
        assertInvalid(parse("--jdbc-url", "jdbc:postgresql://u:p@h/d", "--password-env", "PW"),
            k -> "y", "password");
        assertInvalid(parse("--jdbc-url", "jdbc:sqlserver://h;user=sa;password=x", "--user", "sa"),
            NO_ENV, "user");
    }

    @Test
    void should_turn_credentials_into_driver_properties() {
        Properties p = new ConnectionSettings("u", "SYSDBA", "pw").toProperties();
        assertThat(p).containsEntry("user", "SYSDBA").containsEntry("password", "pw");
        assertThat(new ConnectionSettings("u", null, null).toProperties()).isEmpty();
    }
}
