package digital.singularidade.databridge.server;

import digital.singularidade.databridge.BuildInfo;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HttpServerIT {

    @Test
    void health_returns_200_and_version_returns_payload() throws Exception {
        try (ConnectionPoolManager pools = new ConnectionPoolManager(2, Duration.ofMinutes(10));
             HttpServer server = HttpServer.start(0, pools)) {
            int port = server.port();
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> health = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("ok");

            HttpResponse<String> version = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/version")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(version.statusCode()).isEqualTo(200);
            assertThat(version.body()).contains(BuildInfo.NAME).contains(BuildInfo.VERSION);
        }
    }
}
