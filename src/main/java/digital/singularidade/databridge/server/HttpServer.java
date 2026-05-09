package digital.singularidade.databridge.server;

import io.javalin.Javalin;

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
        });
        app.get("/v1/health", new HealthHandler());
        app.get("/v1/version", HealthHandler.version());
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
