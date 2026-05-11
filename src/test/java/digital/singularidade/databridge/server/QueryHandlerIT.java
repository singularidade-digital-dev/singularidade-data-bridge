package digital.singularidade.databridge.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class QueryHandlerIT {

    @Test
    void post_query_returns_rows_and_metadata() throws Exception {
        try (PgFixture fx = new PgFixture();
             ConnectionPoolManager pools = new ConnectionPoolManager(2, Duration.ofMinutes(10));
             HttpServer server = HttpServer.start(0, pools)) {

            String body = String.format(
                "{\"jdbcUrl\":\"%s\",\"sql\":\"SELECT id, mnemonico FROM atl.estadocivil ORDER BY id\",\"limit\":10}",
                fx.jdbcUrl());

            HttpResponse<String> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/v1/query"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(200);
            JsonNode tree = new ObjectMapper().readTree(resp.body());
            assertThat(tree.get("kind").asText()).isEqualTo("query");
            assertThat(tree.get("rowCount").asInt()).isEqualTo(4);
            assertThat(tree.get("rows").get(0).get("mnemonico").asText()).isEqualTo("S");
        }
    }

    @Test
    void post_query_with_ddl_returns_400() throws Exception {
        try (PgFixture fx = new PgFixture();
             ConnectionPoolManager pools = new ConnectionPoolManager(2, Duration.ofMinutes(10));
             HttpServer server = HttpServer.start(0, pools)) {

            String body = String.format(
                "{\"jdbcUrl\":\"%s\",\"sql\":\"DROP TABLE atl.cliente\",\"writable\":true}",
                fx.jdbcUrl());

            HttpResponse<String> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/v1/query"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(400);
            assertThat(resp.body()).contains("DDL");
        }
    }

    @Test
    void post_query_missing_sql_returns_400() throws Exception {
        try (PgFixture fx = new PgFixture();
             ConnectionPoolManager pools = new ConnectionPoolManager(2, Duration.ofMinutes(10));
             HttpServer server = HttpServer.start(0, pools)) {

            String body = String.format(
                "{\"jdbcUrl\":\"%s\"}", fx.jdbcUrl());

            HttpResponse<String> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/v1/query"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(400);
            assertThat(resp.body()).contains("required");
        }
    }
}
