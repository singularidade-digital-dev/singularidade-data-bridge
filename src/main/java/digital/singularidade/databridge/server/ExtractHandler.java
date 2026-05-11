package digital.singularidade.databridge.server;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.output.Metadata;
import digital.singularidade.databridge.pipeline.MetadataPipeline;
import digital.singularidade.databridge.source.jdbc.DriverHints;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

import java.io.OutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.util.Map;

public final class ExtractHandler implements Handler {

    private final ConnectionPoolManager pools;

    public ExtractHandler(ConnectionPoolManager pools) { this.pools = pools; }

    @Override
    public void handle(@NotNull Context ctx) {
        ExtractRequest req;
        try { req = ctx.bodyAsClass(ExtractRequest.class); }
        catch (Exception e) {
            ctx.status(400).json(Map.of("error", Map.of(
                "code", "INVALID_ARGS", "message", "invalid body: " + e.getMessage())));
            return;
        }
        if (req == null || req.jdbcUrl() == null || req.table() == null) {
            ctx.status(400).json(Map.of("error", Map.of(
                "code", "INVALID_ARGS", "message", "jdbcUrl and table are required")));
            return;
        }
        try {
            DriverHints hints = DriverHints.fromUrl(req.jdbcUrl());
            try (Connection c = pools.getConnection(req.jdbcUrl());
                 JdbcSource src = JdbcSource.wrap(c, hints)) {
                Metadata m = new MetadataPipeline(new PrintStream(OutputStream.nullOutputStream()),
                    req.resolvedCardinalityMode())
                    .run(src, req.jdbcUrl(), req.schema(), req.table(), req.sampleRowsOrDefault());
                ctx.json(m);
            }
        } catch (DataBridgeException e) {
            ctx.status(e.code().httpStatus()).json(Map.of("error", Map.of(
                "code", e.code().wireName(), "message", e.getMessage(),
                "hint", e.hint() == null ? "" : e.hint())));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", Map.of(
                "code", "UNSPECIFIED", "message", e.getMessage())));
        }
    }
}
