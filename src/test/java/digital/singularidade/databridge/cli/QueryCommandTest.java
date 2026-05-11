package digital.singularidade.databridge.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class QueryCommandTest {

    @Test
    void parses_required_flags_with_safe_defaults() {
        QueryCommand cmd = new QueryCommand();
        new CommandLine(cmd).parseArgs(
            "--jdbc-url", "jdbc:postgresql://h/d",
            "--sql", "SELECT 1"
        );
        assertThat(cmd.jdbcUrl).isEqualTo("jdbc:postgresql://h/d");
        assertThat(cmd.sql).isEqualTo("SELECT 1");
        assertThat(cmd.limit).isEqualTo(100);
        assertThat(cmd.timeoutSec).isEqualTo(30);
        assertThat(cmd.writable).isFalse();      // safe default — no DML/DDL
        assertThat(cmd.outFile).isNull();        // stdout by default
    }

    @Test
    void parses_optional_flags() {
        QueryCommand cmd = new QueryCommand();
        new CommandLine(cmd).parseArgs(
            "--jdbc-url", "u",
            "--sql", "INSERT INTO t VALUES (1)",
            "--limit", "5",
            "--timeout-sec", "10",
            "--writable",
            "--out", "/tmp/result.json"
        );
        assertThat(cmd.limit).isEqualTo(5);
        assertThat(cmd.timeoutSec).isEqualTo(10);
        assertThat(cmd.writable).isTrue();
        assertThat(cmd.outFile).isEqualTo("/tmp/result.json");
    }
}
