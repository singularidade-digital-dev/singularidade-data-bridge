package digital.singularidade.databridge.server;

import digital.singularidade.databridge.output.JsonWriter;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

public final class HttpServer implements AutoCloseable {

    private final Javalin app;
    private final ConnectionPoolManager pools;

    private HttpServer(Javalin app, ConnectionPoolManager pools) {
        this.app = app;
        this.pools = pools;
    }

    public static HttpServer start(int port, ConnectionPoolManager pools) {
        Javalin app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            cfg.jsonMapper(new JavalinJackson(JsonWriter.newMapper(), true));
        });
        app.get("/v1/health", new HealthHandler());
        app.get("/v1/version", HealthHandler.version());
        app.post("/v1/extract", new ExtractHandler(pools));
        app.start(port);
        return new HttpServer(app, pools);
    }

    public int port() {
        return app.port();
    }

    @Override
    public void close() {
        app.stop();
    }
}
