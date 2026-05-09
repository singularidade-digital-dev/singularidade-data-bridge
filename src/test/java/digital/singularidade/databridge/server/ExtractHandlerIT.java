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

class ExtractHandlerIT {

    @Test
    void post_extract_returns_metadata_json() throws Exception {
        try (PgFixture fx = new PgFixture();
             ConnectionPoolManager pools = new ConnectionPoolManager(2, Duration.ofMinutes(10));
             HttpServer server = HttpServer.start(0, pools)) {

            String body = String.format(
                "{\"jdbcUrl\":\"%s\",\"schema\":\"atl\",\"table\":\"cliente\",\"sampleRows\":5,\"skipCardinality\":false}",
                fx.jdbcUrl());

            HttpResponse<String> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/v1/extract"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(200);
            JsonNode tree = new ObjectMapper().readTree(resp.body());
            assertThat(tree.get("version").asText()).isEqualTo("1.0");
            assertThat(tree.get("primaryKey").get(0).asText()).isEqualTo("id");
        }
    }
}
