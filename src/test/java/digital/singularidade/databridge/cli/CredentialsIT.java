package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.support.FbFixture;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialsIT {

    private static int cli(String... args) {
        return new CommandLine(new Main())
            .setErr(new PrintWriter(OutputStream.nullOutputStream()))
            .execute(args);
    }

    @Test
    void should_extract_from_postgres_with_user_flag_and_password_file(@TempDir Path tmp) throws Exception {
        try (PgFixture fx = new PgFixture()) {
            Path pw = Files.writeString(tmp.resolve("pw"), fx.password() + "\n");
            int exit = cli("extract", "--jdbc-url", fx.jdbcUrlWithoutCredentials(), "--user", fx.username(),
                "--password-file", pw.toString(), "--schema", "atl", "--table", "cliente",
                "--out", tmp.resolve("out").toString(), "--cardinality-mode", "skip", "-q");
            assertThat(exit).isZero();
            String json = Files.readString(tmp.resolve("out").resolve("atl.cliente").resolve("metadata.json"));
            assertThat(json).contains("\"primaryKey\"").doesNotContain("password");
        }
    }

    @Test
    void should_list_firebird_tables_with_user_flag_and_fail_on_a_wrong_password(@TempDir Path tmp) throws Exception {
        try (FbFixture fx = new FbFixture()) {
            Path good = Files.writeString(tmp.resolve("good"), FbFixture.PASSWORD);
            Path bad = Files.writeString(tmp.resolve("bad"), "wrong");
            assertThat(cli("list-tables", "--jdbc-url", fx.jdbcUrlWithoutCredentials(), "--user", FbFixture.USER,
                "--password-file", good.toString())).isZero();
            assertThat(cli("list-tables", "--jdbc-url", fx.jdbcUrlWithoutCredentials(), "--user", FbFixture.USER,
                "--password-file", bad.toString())).isEqualTo(10);
        }
    }

    @Test
    void should_reject_a_password_given_twice_before_connecting(@TempDir Path tmp) throws Exception {
        Path pw = Files.writeString(tmp.resolve("pw"), "x");
        assertThat(cli("list-tables", "--jdbc-url", "jdbc:postgresql://127.0.0.1:1/x?password=a",
            "--password-file", pw.toString())).isEqualTo(2);
    }
}
