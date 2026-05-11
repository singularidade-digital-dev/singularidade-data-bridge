package digital.singularidade.databridge.server;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.output.QueryResult;
import digital.singularidade.databridge.source.jdbc.DriverHints;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.util.Map;

public final class QueryHandler implements Handler {

    private final ConnectionPoolManager pools;

    public QueryHandler(ConnectionPoolManager pools) { this.pools = pools; }

    @Override
    public void handle(@NotNull Context ctx) {
        QueryRequest req;
        try { req = ctx.bodyAsClass(QueryRequest.class); }
        catch (Exception e) {
            ctx.status(400).json(Map.of("error", Map.of(
                "code", "INVALID_ARGS", "message", "invalid body: " + e.getMessage())));
            return;
        }
        if (req == null || req.jdbcUrl() == null || req.sql() == null) {
            ctx.status(400).json(Map.of("error", Map.of(
                "code", "INVALID_ARGS", "message", "jdbcUrl and sql are required")));
            return;
        }
        try {
            DriverHints hints = DriverHints.fromUrl(req.jdbcUrl());
            try (Connection c = pools.getConnection(req.jdbcUrl());
                 JdbcSource src = JdbcSource.wrap(c, hints)) {
                QueryResult result = src.executeQuery(
                    req.sql(),
                    req.limitOrDefault(),
                    req.timeoutSecOrDefault(),
                    req.writableOrDefault());
                ctx.json(result);
            }
        } catch (DataBridgeException e) {
            ctx.status(e.code().httpStatus()).json(Map.of("error", Map.of(
                "code", e.code().wireName(),
                "message", e.getMessage(),
                "hint", e.hint() == null ? "" : e.hint())));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", Map.of(
                "code", "UNSPECIFIED", "message", e.getMessage())));
        }
    }
}
