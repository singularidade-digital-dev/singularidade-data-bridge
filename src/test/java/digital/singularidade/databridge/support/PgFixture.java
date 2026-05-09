package digital.singularidade.databridge.support;

import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class PgFixture implements AutoCloseable {

    public static final String IMAGE = "postgres:16";

    private final PostgreSQLContainer<?> container;

    public PgFixture() {
        this.container = new PostgreSQLContainer<>(IMAGE)
            .withDatabaseName("test")
            .withUsername("test")
            .withPassword("test");
        this.container.start();
        bootstrap();
    }

    private void bootstrap() {
        try (Connection c = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword())) {
            execScript(c, "/fixtures/atl-schema.sql");
            execScript(c, "/fixtures/atl-data.sql");
        } catch (SQLException | IOException e) {
            throw new RuntimeException("PgFixture bootstrap failed", e);
        }
    }

    private void execScript(Connection c, String resource) throws IOException, SQLException {
        try (InputStream in = PgFixture.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("missing resource: " + resource);
            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement s = c.createStatement()) {
                for (String stmt : script.split(";\\s*\\n")) {
                    String trimmed = stmt.trim();
                    if (!trimmed.isEmpty()) s.execute(trimmed);
                }
            }
        }
    }

    public String jdbcUrl() {
        String base = container.getJdbcUrl();
        String sep = base.contains("?") ? "&" : "?";
        return base + sep + "user=" + container.getUsername()
            + "&password=" + container.getPassword();
    }

    public String username() { return container.getUsername(); }
    public String password() { return container.getPassword(); }

    @Override public void close() { container.stop(); }
}
