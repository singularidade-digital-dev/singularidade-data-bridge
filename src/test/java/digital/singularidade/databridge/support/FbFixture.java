package digital.singularidade.databridge.support;

import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Firebird Testcontainer fixture on the official {@code firebirdsql/firebird} image (Firebird 3 by
 * default — the engine SGH runs). No first-class Testcontainers module is used; a {@link GenericContainer}
 * is enough and keeps the dependency list unchanged.
 *
 * <p>Bootstrap, in one {@code isql} script:
 * <ul>
 *   <li>the SHOP tables (CATEGORIES + PRODUCTS, 100 rows, statistics refreshed) used by the cardinality ITs;</li>
 *   <li>{@code fixtures/fb-sgh-like.sql}: domains, NUMERIC/DECIMAL, BLOBs, computed and identity columns,
 *       named and unnamed constraints, DESC/expression/inactive indexes, generators, triggers.</li>
 * </ul>
 *
 * <p>Meant to be shared per test class ({@code @BeforeAll}) — tests must not change the schema of
 * {@code sgh.fdb}; use {@link #isqlWithoutDatabase(String)} to create scratch databases.
 */
public final class FbFixture implements AutoCloseable {

    public static final String IMAGE_FB3 = "firebirdsql/firebird:3.0.14";
    public static final String DB_DIR = "/var/lib/firebird/data";
    public static final String DB_FILE = "sgh.fdb";
    public static final String USER = "SYSDBA";
    public static final String PASSWORD = "masterkey";
    private static final int FB_PORT = 3050;

    private final GenericContainer<?> container;
    private final AtomicInteger scriptCounter = new AtomicInteger();
    private boolean mixedCreated;

    public FbFixture() {
        this(IMAGE_FB3);
    }

    public FbFixture(String image) {
        this.container = new GenericContainer<>(DockerImageName.parse(image))
            .withExposedPorts(FB_PORT)
            .withEnv("FIREBIRD_ROOT_PASSWORD", PASSWORD)
            .withEnv("FIREBIRD_DATABASE", DB_FILE)
            .withEnv("FIREBIRD_DATABASE_DEFAULT_CHARSET", "ISO8859_1")
            .waitingFor(Wait.forLogMessage(".*Starting Firebird.*\\s", 1)
                .withStartupTimeout(Duration.ofMinutes(2)));
        this.container.start();
        waitUntilConnectable();
        IsqlResult boot = isql(shopScript() + "\n" + resource("/fixtures/fb-sgh-like.sql"));
        if (boot.exitCode() != 0) {
            throw new IllegalStateException("FbFixture bootstrap failed:\n" + boot.output());
        }
    }

    public String jdbcUrl() {
        return jdbcUrl(DB_FILE);
    }

    public String jdbcUrl(String dbFile) {
        return jdbcUrlWithoutCredentials(dbFile) + "&user=" + USER + "&password=" + PASSWORD;
    }

    /** Credentials inline but no connection character set: data-bridge's own default applies. */
    public String jdbcUrlWithoutEncoding(String dbFile) {
        return "jdbc:firebirdsql://" + container.getHost() + ":" + container.getMappedPort(FB_PORT)
             + "/" + DB_DIR + "/" + dbFile + "?user=" + USER + "&password=" + PASSWORD;
    }

    /**
     * Scratch database like SGH's: default character set NONE, one table whose NONE columns hold the same
     * text in WIN1252 (row 1) and UTF-8 (row 2), plus typed WIN1252 / ISO8859_1 columns and an accented
     * column comment. Created once per fixture.
     */
    public String mixedEncodingDatabase() {
        if (!mixedCreated) {
            IsqlResult r = isqlWithoutDatabase("""
                SET SQL DIALECT 3;
                SET NAMES UTF8;
                CREATE DATABASE '%s/mixed.fdb' USER '%s' PASSWORD '%s' DEFAULT CHARACTER SET NONE;
                CREATE TABLE TMIX (ID INTEGER NOT NULL PRIMARY KEY, D VARCHAR(40) CHARACTER SET NONE,
                  B BLOB SUB_TYPE TEXT CHARACTER SET NONE, C CHAR(3) CHARACTER SET NONE,
                  W VARCHAR(10) CHARACTER SET WIN1252, F CHAR(1) CHARACTER SET ISO8859_1);
                COMMENT ON COLUMN TMIX.D IS 'Descrição com acento';
                INSERT INTO TMIX VALUES (1, CAST(x'61E7E36F2091789220E9' AS VARCHAR(40) CHARACTER SET NONE),
                  CAST(x'61E7E36F' AS BLOB SUB_TYPE TEXT CHARACTER SET NONE),
                  CAST(x'E76161' AS CHAR(3) CHARACTER SET NONE), 'ação', 'T');
                INSERT INTO TMIX VALUES (2, CAST(x'61C3A7C3A36F20E2809878E2809920C3A9' AS VARCHAR(40) CHARACTER SET NONE),
                  CAST(x'61C3A7C3A36F' AS BLOB SUB_TYPE TEXT CHARACTER SET NONE), 'abc', 'é', 'F');
                COMMIT;
                """.formatted(DB_DIR, USER, PASSWORD));
            if (r.exitCode() != 0) throw new IllegalStateException("mixed.fdb bootstrap failed:\n" + r.output());
            mixedCreated = true;
        }
        return "mixed.fdb";
    }

    public String jdbcUrlWithoutCredentials() {
        return jdbcUrlWithoutCredentials(DB_FILE);
    }

    private String jdbcUrlWithoutCredentials(String dbFile) {
        return "jdbc:firebirdsql://" + container.getHost() + ":" + container.getMappedPort(FB_PORT)
             + "/" + DB_DIR + "/" + dbFile + "?encoding=ISO8859_1";
    }

    /** Run an isql script against {@code sgh.fdb}. Stops at the first error ({@code -bail}). */
    public IsqlResult isql(String script) {
        return runIsql(script, DB_DIR + "/" + DB_FILE);
    }

    /** Run an isql script that carries its own {@code CREATE DATABASE} / {@code CONNECT}. */
    public IsqlResult isqlWithoutDatabase(String script) {
        return runIsql(script, null);
    }

    private IsqlResult runIsql(String script, String database) {
        String path = "/tmp/fixture-" + scriptCounter.incrementAndGet() + ".sql";
        container.copyFileToContainer(Transferable.of(script.getBytes(StandardCharsets.UTF_8)), path);
        List<String> cmd = new ArrayList<>(List.of(
            "isql", "-bail", "-q", "-user", USER, "-password", PASSWORD, "-i", path));
        if (database != null) cmd.add(database);
        try {
            ExecResult r = container.execInContainer(cmd.toArray(String[]::new));
            return new IsqlResult(r.getExitCode(), r.getStdout() + r.getStderr());
        } catch (IOException e) {
            throw new IllegalStateException("isql failed to run", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while running isql", e);
        }
    }

    public record IsqlResult(int exitCode, String output) {}

    private static String shopScript() {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE CATEGORIES (ID INTEGER NOT NULL PRIMARY KEY,"
                + " CODE VARCHAR(20) NOT NULL, NAME VARCHAR(100) NOT NULL);\n");
        sb.append("CREATE TABLE PRODUCTS (ID INTEGER NOT NULL PRIMARY KEY, SKU VARCHAR(50) NOT NULL,"
                + " NAME VARCHAR(200) NOT NULL, CATEGORY VARCHAR(20) NOT NULL,"
                + " PRICE NUMERIC(10,2) NOT NULL, IN_STOCK SMALLINT NOT NULL);\n");
        sb.append("CREATE INDEX IDX_PRODUCTS_CATEGORY ON PRODUCTS (CATEGORY);\n");
        sb.append("CREATE UNIQUE INDEX UK_PRODUCTS_SKU ON PRODUCTS (SKU);\n");
        sb.append("COMMIT;\n");
        sb.append("INSERT INTO CATEGORIES VALUES (1, 'BOOK', 'Books');\n");
        sb.append("INSERT INTO CATEGORIES VALUES (2, 'TECH', 'Tech');\n");
        sb.append("INSERT INTO CATEGORIES VALUES (3, 'FOOD', 'Food');\n");
        String[] cats = {"BOOK", "TECH", "FOOD"};
        for (int i = 1; i <= 100; i++) {
            sb.append("INSERT INTO PRODUCTS VALUES (").append(i).append(", 'SKU-").append(i)
              .append("', 'Product ").append(i).append("', '").append(cats[i % 3]).append("', ")
              .append(10 + (i % 50)).append(".00, ").append(i % 4 == 0 ? 0 : 1).append(");\n");
        }
        sb.append("COMMIT;\n");
        // CATEGORIES and PRODUCTS are created first, so their unnamed PK indexes are RDB$PRIMARY1/2.
        sb.append("SET STATISTICS INDEX RDB$PRIMARY1;\n");
        sb.append("SET STATISTICS INDEX RDB$PRIMARY2;\n");
        sb.append("SET STATISTICS INDEX IDX_PRODUCTS_CATEGORY;\n");
        sb.append("SET STATISTICS INDEX UK_PRODUCTS_SKU;\n");
        sb.append("COMMIT;\n");
        return sb.toString();
    }

    private static String resource(String name) {
        try (InputStream in = FbFixture.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("missing resource: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + name, e);
        }
    }

    /** Firebird accepts connections a moment after it logs that it started. Retry briefly. */
    private void waitUntilConnectable() {
        SQLException last = null;
        for (int i = 0; i < 30; i++) {
            try (Connection ignored = DriverManager.getConnection(jdbcUrl())) {
                return;
            } catch (SQLException e) {
                last = e;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting for Firebird", ie);
                }
            }
        }
        throw new IllegalStateException("Firebird did not accept connections", last);
    }

    @Override
    public void close() {
        container.stop();
    }
}
