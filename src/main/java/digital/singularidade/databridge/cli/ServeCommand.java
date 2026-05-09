package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.server.ConnectionPoolManager;
import digital.singularidade.databridge.server.HttpServer;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;

import java.time.Duration;
import java.util.concurrent.Callable;

@Command(name = "serve", description = "Start HTTP daemon mode.")
public final class ServeCommand implements Callable<Integer> {

    @Option(names = "--port", defaultValue = "8765") int port;
    @Option(names = "--max-pool", defaultValue = "5") int maxPool;
    @Option(names = "--idle-timeout", defaultValue = "10m", converter = ServeCommand.DurationConverter.class)
    Duration idleTimeout;

    @Override
    public Integer call() throws Exception {
        ConnectionPoolManager pools = new ConnectionPoolManager(maxPool, idleTimeout);
        HttpServer server = HttpServer.start(port, pools);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.close(); } catch (Exception ignored) {}
            pools.shutdown();
        }, "data-bridge-shutdown"));
        System.err.println("data-bridge listening on http://localhost:" + server.port());
        Thread.currentThread().join();
        return 0;
    }

    public static final class DurationConverter implements ITypeConverter<Duration> {
        @Override
        public Duration convert(String value) {
            String s = value.trim().toLowerCase();
            if (s.endsWith("ms")) return Duration.ofMillis(Long.parseLong(s.substring(0, s.length() - 2)));
            if (s.endsWith("s")) return Duration.ofSeconds(Long.parseLong(s.substring(0, s.length() - 1)));
            if (s.endsWith("m")) return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1)));
            if (s.endsWith("h")) return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1)));
            return Duration.ofSeconds(Long.parseLong(s));
        }
    }
}
