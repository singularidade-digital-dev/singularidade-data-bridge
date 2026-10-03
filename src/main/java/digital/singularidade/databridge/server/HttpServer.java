package digital.singularidade.databridge.server;

import digital.singularidade.databridge.output.JsonWriter;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.eclipse.jetty.server.ServerConnector;

public final class HttpServer implements AutoCloseable {

    private final Javalin app;
    private final ConnectionPoolManager pools;

    private HttpServer(Javalin app, ConnectionPoolManager pools) {
        this.app = app;
        this.pools = pools;
    }

    /** Loopback-only bind: the daemon has no authentication, so it is never exposed by default. */
    public static final String DEFAULT_HOST = "127.0.0.1";

    public static HttpServer start(int port, ConnectionPoolManager pools) {
        return start(DEFAULT_HOST, port, pools);
    }

    public static HttpServer start(String host, int port, ConnectionPoolManager pools) {
        Javalin app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            cfg.jsonMapper(new JavalinJackson(JsonWriter.newMapper(), true));
        });
        app.get("/v1/health", new HealthHandler());
        app.get("/v1/version", HealthHandler.version());
        app.post("/v1/extract", new ExtractHandler(pools));
        app.post("/v1/query",   new QueryHandler(pools));
        app.get("/v1/list-tables", new ListTablesHandler(pools));
        app.start(host, port);
        return new HttpServer(app, pools);
    }

    public int port() {
        return app.port();
    }

    /** Host of the bound connector, e.g. {@code 127.0.0.1}. */
    public String host() {
        return ((ServerConnector) app.jettyServer().server().getConnectors()[0]).getHost();
    }

    @Override
    public void close() {
        app.stop();
    }
}
