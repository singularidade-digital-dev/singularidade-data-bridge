package digital.singularidade.databridge.pipeline.ddl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.cli.Main;
import digital.singularidade.databridge.source.jdbc.firebird.FirebirdCatalog;
import digital.singularidade.databridge.support.FbFixture;
import digital.singularidade.databridge.support.FbFixture.IsqlResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Firebird 5 is on SGH's horizon: the same fixture must extract and replay there too. */
class Firebird5SmokeIT {

    private static FbFixture fx;

    @BeforeAll
    static void start() { fx = new FbFixture("firebirdsql/firebird:5.0.4"); }

    @AfterAll
    static void stop() { fx.close(); }

    @Test
    void should_extract_and_replay_the_fixture_on_firebird_5(@TempDir Path tmp) throws Exception {
        int exit = new CommandLine(new Main()).setErr(new PrintWriter(OutputStream.nullOutputStream()))
            .execute("extract-all", "--jdbc-url", fx.jdbcUrl(), "--out", tmp.toString(),
                "--include-triggers", "--cardinality-mode", "skip", "-q");
        assertThat(exit).isZero();
        JsonNode m = new ObjectMapper().readTree(tmp.resolve("PRITENSESQ").resolve("metadata.json").toFile());
        assertThat(m.get("triggers").size()).isEqualTo(3);
        assertThat(m.get("columns").get(4).get("domain").get("name").asText()).isEqualTo("DM_FLAG");

        List<String> tables = FirebirdDdlReplayIT.tableDirs(tmp);
        IsqlResult r = fx.isqlWithoutDatabase(FirebirdDdlReplayIT.replayScript(tmp, tables, "replay5.fdb"));
        assertThat(r.exitCode()).withFailMessage("isql failed:%n%s", r.output()).isZero();
        try (Connection original = DriverManager.getConnection(fx.jdbcUrl());
             Connection copy = DriverManager.getConnection(fx.jdbcUrl("replay5.fdb"))) {
            for (String table : tables) {
                assertThat(FirebirdDdlReplayIT.normalize(FirebirdCatalog.read(copy, table)))
                    .as("catalog of %s after replay on Firebird 5", table)
                    .isEqualTo(FirebirdDdlReplayIT.normalize(FirebirdCatalog.read(original, table)));
            }
        }
    }
}
