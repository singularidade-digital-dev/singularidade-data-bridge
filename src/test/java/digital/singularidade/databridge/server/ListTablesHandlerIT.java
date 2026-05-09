package digital.singularidade.databridge.server;

import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ListTablesHandlerIT {

    @Test
    void list_tables_for_atl_schema() throws Exception {
        try (PgFixture fx = new PgFixture();
             ConnectionPoolManager pools = new ConnectionPoolManager(2, Duration.ofMinutes(10));
             HttpServer server = HttpServer.start(0, pools)) {
            String url = "http://localhost:" + server.port()
                + "/v1/list-tables?jdbcUrl=" + URLEncoder.encode(fx.jdbcUrl(), StandardCharsets.UTF_8)
                + "&schema=atl";
            HttpResponse<String> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.body()).contains("estadocivil").contains("cliente");
        }
    }
}
