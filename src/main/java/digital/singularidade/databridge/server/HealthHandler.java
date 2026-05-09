package digital.singularidade.databridge.server;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class HealthHandler implements Handler {
    @Override
    public void handle(@NotNull Context ctx) {
        ctx.json(Map.of("status", "ok"));
    }

    public static Handler version() {
        return ctx -> ctx.json(Map.of(
            "name", "singularidade-data-bridge",
            "version", "0.1.0"));
    }
}
