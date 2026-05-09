package digital.singularidade.databridge.server;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.source.jdbc.DriverHints;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.util.Map;

public final class ListTablesHandler implements Handler {

    private final ConnectionPoolManager pools;

    public ListTablesHandler(ConnectionPoolManager pools) { this.pools = pools; }

    @Override
    public void handle(@NotNull Context ctx) {
        String jdbcUrl = ctx.queryParam("jdbcUrl");
        String schema = ctx.queryParam("schema");
        if (jdbcUrl == null) {
            ctx.status(400).json(Map.of("error", Map.of(
                "code", "INVALID_ARGS", "message", "jdbcUrl query param is required")));
            return;
        }
        try {
            DriverHints hints = DriverHints.fromUrl(jdbcUrl);
            try (Connection c = pools.getConnection(jdbcUrl);
                 JdbcSource src = JdbcSource.wrap(c, hints)) {
                ctx.json(src.listTables(schema));
            }
        } catch (DataBridgeException e) {
            ctx.status(e.code().httpStatus()).json(Map.of("error", Map.of(
                "code", e.code().wireName(), "message", e.getMessage())));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", Map.of(
                "code", "UNSPECIFIED", "message", e.getMessage())));
        }
    }
}
