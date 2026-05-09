package digital.singularidade.databridge.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ConnectionPoolManager implements AutoCloseable {

    private static final Set<String> EPHEMERAL = Set.of(
        "connecttimeout", "sockettimeout", "applicationname"
    );

    private final int maxPool;
    private final Duration idleTimeout;
    private final ConcurrentHashMap<String, Pool> pools = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public ConnectionPoolManager(int maxPool, Duration idleTimeout) {
        this.maxPool = maxPool;
        this.idleTimeout = idleTimeout;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "data-bridge-pool-evictor");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::evictIdle, 1, 1, TimeUnit.MINUTES);
    }

    public Connection getConnection(String jdbcUrl) throws SQLException {
        String key = normalizeKey(jdbcUrl);
        Pool pool = pools.computeIfAbsent(key, k -> createPool(jdbcUrl));
        pool.lastUsed = Instant.now();
        return pool.ds.getConnection();
    }

    private Pool createPool(String jdbcUrl) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setMaximumPoolSize(maxPool);
        cfg.setReadOnly(true);
        cfg.setIdleTimeout(idleTimeout.toMillis());
        cfg.setPoolName("data-bridge-" + Integer.toHexString(jdbcUrl.hashCode()));
        return new Pool(new HikariDataSource(cfg));
    }

    String normalizeKey(String jdbcUrl) {
        int qIdx = jdbcUrl.indexOf('?');
        if (qIdx < 0) return jdbcUrl;
        String base = jdbcUrl.substring(0, qIdx);
        String[] params = jdbcUrl.substring(qIdx + 1).split("&");
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String p : params) {
            int eq = p.indexOf('=');
            String k = (eq < 0 ? p : p.substring(0, eq));
            String v = (eq < 0 ? "" : p.substring(eq + 1));
            if (!EPHEMERAL.contains(k.toLowerCase())) {
                sorted.put(k, v);
            }
        }
        StringBuilder sb = new StringBuilder(base).append('?');
        boolean first = true;
        for (var e : sorted.entrySet()) {
            if (!first) sb.append('&');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    private void evictIdle() {
        Instant cutoff = Instant.now().minus(idleTimeout);
        pools.entrySet().removeIf(entry -> {
            if (entry.getValue().lastUsed.isBefore(cutoff)) {
                try {
                    entry.getValue().ds.close();
                } catch (Exception ignored) {
                }
                return true;
            }
            return false;
        });
    }

    public void shutdown() {
        scheduler.shutdown();
        pools.values().forEach(p -> {
            try {
                p.ds.close();
            } catch (Exception ignored) {
            }
        });
        pools.clear();
    }

    @Override
    public void close() {
        shutdown();
    }

    private static final class Pool {
        final HikariDataSource ds;
        volatile Instant lastUsed = Instant.now();

        Pool(HikariDataSource ds) {
            this.ds = ds;
        }
    }
}
