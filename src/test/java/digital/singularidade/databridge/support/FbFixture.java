package digital.singularidade.databridge.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

/**
 * Firebird Testcontainer fixture. Uses {@code jacobalberty/firebird:v4.0} (the most-pulled
 * community image). No first-class Testcontainers module exists for Firebird, so we wrap a
 * {@link GenericContainer} manually.
 *
 * <p>Bootstrap creates a small {@code SHOP} schema with a primary-keyed table plus a single-column
 * index (so {@code RDB$INDICES.RDB$STATISTICS} is populated for both total-rows and per-column
 * approximate cardinality). {@code SET STATISTICS INDEX} is run before close.
 */
public final class FbFixture implements AutoCloseable {

    public static final String IMAGE = "jacobalberty/firebird:v4.0";
    public static final String DB_PATH = "/firebird/data/test.fdb";
    public static final int FB_PORT = 3050;
    public static final String USER = "SYSDBA";
    public static final String PASSWORD = "masterkey";

    private final GenericContainer<?> container;

    public FbFixture() {
        this.container = new GenericContainer<>(IMAGE)
            .withExposedPorts(FB_PORT)
            .withEnv("ISC_PASSWORD", PASSWORD)
            .withEnv("FIREBIRD_DATABASE", "test.fdb")
            .withEnv("EnableLegacyClientAuth", "true")
            .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
        this.container.start();
        bootstrap();
    }

    public String jdbcUrl() {
        return "jdbc:firebirdsql://localhost:" + container.getMappedPort(FB_PORT)
             + "/" + DB_PATH + "?user=" + USER + "&password=" + PASSWORD
             + "&encoding=UTF8&wireCrypt=DISABLED";
    }

    private void bootstrap() {
        try (Connection c = openWithRetries()) {
            try (Statement s = c.createStatement()) {
                // Two tables: a small lookup (CATEGORIES) and a 100-row table (PRODUCTS) with a
                // single-column index on CATEGORY to populate per-column selectivity stats.
                s.execute("CREATE TABLE CATEGORIES ("
                    + " ID INTEGER NOT NULL PRIMARY KEY,"
                    + " CODE VARCHAR(20) NOT NULL,"
                    + " NAME VARCHAR(100) NOT NULL"
                    + ")");
                s.execute("CREATE TABLE PRODUCTS ("
                    + " ID INTEGER NOT NULL PRIMARY KEY,"
                    + " SKU VARCHAR(50) NOT NULL,"
                    + " NAME VARCHAR(200) NOT NULL,"
                    + " CATEGORY VARCHAR(20) NOT NULL,"
                    + " PRICE NUMERIC(10,2) NOT NULL,"
                    + " IN_STOCK SMALLINT NOT NULL"
                    + ")");
                s.execute("CREATE INDEX IDX_PRODUCTS_CATEGORY ON PRODUCTS (CATEGORY)");
                s.execute("CREATE UNIQUE INDEX UK_PRODUCTS_SKU ON PRODUCTS (SKU)");

                s.execute("INSERT INTO CATEGORIES VALUES (1, 'BOOK', 'Books')");
                s.execute("INSERT INTO CATEGORIES VALUES (2, 'TECH', 'Tech')");
                s.execute("INSERT INTO CATEGORIES VALUES (3, 'FOOD', 'Food')");

                // 100 products spread across 3 categories so distinct estimates are non-trivial
                String[] cats = {"BOOK", "TECH", "FOOD"};
                for (int i = 1; i <= 100; i++) {
                    String cat = cats[i % 3];
                    s.execute("INSERT INTO PRODUCTS VALUES ("
                        + i + ", 'SKU-" + i + "', 'Product " + i + "', '" + cat + "',"
                        + " " + (10 + (i % 50)) + ".00, " + ((i % 4 == 0) ? 0 : 1) + ")");
                }

                // Force statistics recomputation so RDB$STATISTICS reflects the loaded data.
                s.execute("SET STATISTICS INDEX RDB$PRIMARY1");      // CATEGORIES PK (auto-named)
                s.execute("SET STATISTICS INDEX RDB$PRIMARY2");      // PRODUCTS PK (auto-named)
                s.execute("SET STATISTICS INDEX IDX_PRODUCTS_CATEGORY");
                s.execute("SET STATISTICS INDEX UK_PRODUCTS_SKU");
            }
        } catch (SQLException e) {
            throw new RuntimeException("FbFixture bootstrap failed", e);
        }
    }

    /** Firebird is slow to accept connections after the container reports listening. Retry briefly. */
    private Connection openWithRetries() throws SQLException {
        SQLException last = null;
        for (int i = 0; i < 30; i++) {
            try { return DriverManager.getConnection(jdbcUrl()); }
            catch (SQLException e) {
                last = e;
                try { Thread.sleep(500); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("interrupted while waiting for Firebird", ie);
                }
            }
        }
        throw last;
    }

    @Override
    public void close() { container.stop(); }
}
