package digital.singularidade.databridge.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.support.FbFixture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RunQueryCommandFbIT {

    private static final String QUERIES = """
        -- name: tabela
        SELECT NOME, MODULO, DESCRICAO FROM TBSGHTABELAS WHERE NOME = :tabela
        -- name: colunas
        -- descricoes oficiais das colunas
        SELECT COLUNA, DESCRICAO FROM TBSGHCOLUNAS WHERE TABELA = :tabela ORDER BY COLUNA;
        -- name: por_id
        SELECT ID, CODEXT FROM PRITENSESQ WHERE ID = :id
        -- name: apaga
        DELETE FROM TBSGHCOLUNAS WHERE TABELA = :tabela
        -- name: desde
        SELECT COUNT(*) AS N FROM PRITENSESQ WHERE DTCAD >= :desde
        """;

    private static FbFixture fx;

    @BeforeAll
    static void start() { fx = new FbFixture(); }

    @AfterAll
    static void stop() { fx.close(); }

    private static int run(Path queries, String... extra) {
        String[] base = {"run-query", "--jdbc-url", fx.jdbcUrl(), "--file", queries.toString()};
        String[] args = new String[base.length + extra.length];
        System.arraycopy(base, 0, args, 0, base.length);
        System.arraycopy(extra, 0, args, base.length, extra.length);
        return new CommandLine(new Main()).setErr(new PrintWriter(OutputStream.nullOutputStream())).execute(args);
    }

    @Test
    void should_run_a_dictionary_query_with_a_text_parameter(@TempDir Path tmp) throws Exception {
        Path q = Files.writeString(tmp.resolve("dicionario.sql"), QUERIES);
        Path out = tmp.resolve("out.json");
        assertThat(run(q, "--name", "colunas", "--param", "tabela=PRITENSESQ", "--out", out.toString(), "-q")).isZero();
        JsonNode r = new ObjectMapper().readTree(out.toFile());
        assertThat(r.get("rowCount").asInt()).isEqualTo(2);
        assertThat(r.get("rows").get(0).get("COLUNA").asText().strip()).isEqualTo("ID");
        assertThat(r.get("rows").get(1).get("DESCRICAO").asText()).isEqualTo("Valor unitario");
    }

    @Test
    void should_convert_an_integer_parameter(@TempDir Path tmp) throws Exception {
        Path q = Files.writeString(tmp.resolve("q.sql"), QUERIES);
        Path out = tmp.resolve("out.json");
        assertThat(run(q, "--name", "por_id", "--param", "id=1", "--out", out.toString(), "-q")).isZero();
        assertThat(new ObjectMapper().readTree(out.toFile()).get("rowCount").asInt()).isEqualTo(1);
        assertThat(run(q, "--name", "por_id", "--param", "id=abc", "--out", out.toString(), "-q")).isEqualTo(2);
    }

    @Test
    void should_reject_missing_unknown_parameters_unknown_names_and_writes(@TempDir Path tmp) throws Exception {
        Path q = Files.writeString(tmp.resolve("q.sql"), QUERIES);
        assertThat(run(q, "--name", "colunas")).isEqualTo(2);
        assertThat(run(q, "--name", "colunas", "--param", "tabela=X", "--param", "extra=1")).isEqualTo(2);
        assertThat(run(q, "--name", "nao_existe", "--param", "tabela=X")).isEqualTo(2);
        assertThat(run(q, "--name", "apaga", "--param", "tabela=PRITENSESQ")).isEqualTo(2);
    }

    @Test
    void should_accept_a_plain_date_for_a_timestamp_parameter(@TempDir Path tmp) throws Exception {
        Path q = Files.writeString(tmp.resolve("q.sql"), QUERIES);
        Path out = tmp.resolve("out.json");
        assertThat(run(q, "--name", "desde", "--param", "desde=2000-01-01", "--out", out.toString(), "-q")).isZero();
        assertThat(new ObjectMapper().readTree(out.toFile()).get("rows").get(0).get("N").asLong()).isEqualTo(2);
        assertThat(run(q, "--name", "desde", "--param", "desde=2000-01-01T08:30:00", "--out", out.toString(), "-q")).isZero();
    }
}
