package digital.singularidade.databridge.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.support.FbFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractAllCommandFbIT {

    @Test
    void extract_all_without_schema_enumerates_firebird_tables(@TempDir Path tmp) throws Exception {
        try (FbFixture fx = new FbFixture()) {
            StringWriter err = new StringWriter();
            int exit = new CommandLine(new Main())
                .setErr(new PrintWriter(err))
                .execute(
                    "extract-all",
                    "--jdbc-url", fx.jdbcUrl(),
                    "--out", tmp.toString()
                );
            assertThat(exit).withFailMessage("stderr=%s", err.toString()).isZero();

            assertThat(tmp.resolve("CATEGORIES").resolve("metadata.json")).exists();
            assertThat(tmp.resolve("PRODUCTS").resolve("metadata.json")).exists();
            assertThat(tmp.resolve("CATEGORIES").resolve("ddl.sql")).exists();
            assertThat(tmp.resolve("PRODUCTS").resolve("ddl.sql")).exists();

            JsonNode idx = new ObjectMapper().readTree(tmp.resolve("_index.json").toFile());
            assertThat(idx.get("tableCount").asInt()).isGreaterThanOrEqualTo(2);
            assertThat(idx.get("source").get("schema").isNull()).isTrue();
        }
    }
}
