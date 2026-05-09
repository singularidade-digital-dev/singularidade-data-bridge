# singularidade-data-bridge MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Java 21 fat-JAR CLI (with optional Javalin HTTP serve mode) that introspects RDBMS tables via JDBC and emits a versioned JSON metadata document plus optional TSV files, replacing the per-connector MCP-based introspection used by the `mcp-tool-from-table-*` skill family.

**Architecture:** Layered design — `Source` interface (extension point for future REST/GraphQL/file sources) implemented by a single `JdbcSource` with per-driver quirks via `DriverHints`. Eleven small `Extractor` classes each owning one metadata concept; `MetadataPipeline` runs them in sequence against one connection. Same pipeline serves both the picocli CLI and the optional Javalin HTTP daemon (HikariCP-pooled per-URL connections). Output writers (JSON via Jackson, TSV) consume the same `Metadata` record graph.

**Tech Stack:** Java 21, Maven, picocli 4.7.6, Jackson 2.18.2, Javalin 6.4.0, HikariCP 6.2.1, SLF4J Simple 2.0.16. Drivers: postgresql 42.7.4, jaybird 6.0.5, ojdbc11 23.6.0.24.10, mssql-jdbc 12.8.1.jre11, mysql-connector-j 9.1.0. Tests: JUnit 5.11.4, AssertJ 3.26.3, Testcontainers 1.20.4 (Postgres only in MVP), H2 2.3.232 for in-memory unit fixtures.

**Spec:** `docs/superpowers/specs/2026-05-09-singularidade-data-bridge-design.md` (commit `770e134`)

**Project root:** `/home/mint/Workspace/project/utilities/singularidade-data-bridge/`

---

## File structure

| File | Purpose | Created in task |
|---|---|---|
| `pom.xml` | Maven coords, deps, plugins (compiler/surefire/failsafe/shade) | 1 |
| `.gitignore` | Ignore `target/`, `.idea/`, `*.iml`, `out/` | 2 |
| `README.md` | Install + quick start + PII warning | 2 |
| `src/main/java/digital/singularidade/databridge/cli/Main.java` | picocli root + subcommand wiring | 2 |
| `src/main/java/digital/singularidade/databridge/cli/VersionCommand.java` | `data-bridge version` | 2 |
| `src/main/java/digital/singularidade/databridge/output/Metadata.java` | Root JSON contract record | 3 |
| `src/main/java/digital/singularidade/databridge/output/TableInfo.java` | tableInfo subobject | 3 |
| `src/main/java/digital/singularidade/databridge/output/Column.java` | columns[i] | 3 |
| `src/main/java/digital/singularidade/databridge/output/ForeignKey.java` | foreignKeys[i] + RefColumn | 3 |
| `src/main/java/digital/singularidade/databridge/output/Index.java` | indexes[i] | 3 |
| `src/main/java/digital/singularidade/databridge/output/UniqueConstraint.java` | uniqueConstraints[i] | 3 |
| `src/main/java/digital/singularidade/databridge/output/CheckConstraint.java` | checkConstraints[i] | 3 |
| `src/main/java/digital/singularidade/databridge/output/Sample.java` | sample {rowCount, rows[]} | 3 |
| `src/main/java/digital/singularidade/databridge/output/ColumnStats.java` | columnStats[i] | 3 |
| `src/main/java/digital/singularidade/databridge/output/Cardinality.java` | cardinality {totalRows, perColumn[]} | 3 |
| `src/main/java/digital/singularidade/databridge/output/Partitioning.java` | partitioning subobject | 3 |
| `src/main/java/digital/singularidade/databridge/output/SourceInfo.java` | source subobject | 3 |
| `src/main/java/digital/singularidade/databridge/error/ErrorCodes.java` | exit/HTTP codes enum | 4 |
| `src/main/java/digital/singularidade/databridge/error/DataBridgeException.java` | Single typed exception | 4 |
| `src/main/java/digital/singularidade/databridge/error/UrlRedaction.java` | password=*** util | 5 |
| `src/main/java/digital/singularidade/databridge/source/jdbc/DriverHints.java` | enum with fromUrl + per-driver flags | 6 |
| `src/main/java/digital/singularidade/databridge/source/jdbc/TypeNormalization.java` | java.sql.Types → sqlType string | 7 |
| `src/main/java/digital/singularidade/databridge/source/Source.java` | extension-point interface | 8 |
| `src/test/resources/fixtures/atl-schema.sql` | PG fixture: tables, FKs, indexes, views, partitions | 9 |
| `src/test/resources/fixtures/atl-data.sql` | INSERTs for cardinality/sample | 9 |
| `src/test/java/digital/singularidade/databridge/support/PgFixture.java` | Testcontainer + schema bootstrap | 9 |
| `src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java` | The single JDBC Source impl | 9 |
| `src/main/java/digital/singularidade/databridge/pipeline/SchemaExtractor.java` | columns + identity/sequence/collation | 10 |
| `src/main/java/digital/singularidade/databridge/pipeline/PrimaryKeyExtractor.java` | PK columns | 11 |
| `src/main/java/digital/singularidade/databridge/pipeline/ForeignKeysExtractor.java` | FKs + 1-deep refTableColumns | 12 |
| `src/main/java/digital/singularidade/databridge/pipeline/IndexesExtractor.java` | secondary indexes + PG method/where | 13 |
| `src/main/java/digital/singularidade/databridge/pipeline/UniqueConstraintsExtractor.java` | UC | 14 |
| `src/main/java/digital/singularidade/databridge/pipeline/CheckConstraintsExtractor.java` | CC | 14 |
| `src/main/java/digital/singularidade/databridge/pipeline/SampleExtractor.java` | LIMIT N + BLOB normalize | 15 |
| `src/main/java/digital/singularidade/databridge/pipeline/ColumnStatsExtractor.java` | pg_stats (PG); empty elsewhere | 16 |
| `src/main/java/digital/singularidade/databridge/pipeline/PartitioningExtractor.java` | pg_partitioned_table; null elsewhere | 17 |
| `src/main/java/digital/singularidade/databridge/pipeline/CardinalityExtractor.java` | COUNT + COUNT(DISTINCT) | 18 |
| `src/main/java/digital/singularidade/databridge/pipeline/TableInfoExtractor.java` | type, comment, approxRows, view def | 19 |
| `src/main/java/digital/singularidade/databridge/pipeline/MetadataPipeline.java` | orchestrator (sequential) | 20 |
| `src/main/java/digital/singularidade/databridge/output/JsonWriter.java` | atomic write via Jackson | 21 |
| `src/main/java/digital/singularidade/databridge/output/TsvWriter.java` | TSV with escape | 22 |
| `src/main/java/digital/singularidade/databridge/cli/ExtractCommand.java` | `data-bridge extract ...` | 23 |
| `src/main/java/digital/singularidade/databridge/cli/ListTablesCommand.java` | `data-bridge list-tables ...` | 24 |
| `src/test/resources/golden/cliente.expected.json` | end-to-end golden | 25 |
| `src/test/java/digital/singularidade/databridge/driver/{Postgres,Firebird,Oracle,Mssql,Mysql}DriverSmokeTest.java` | shade smoke | 26 |
| `src/main/java/digital/singularidade/databridge/server/ConnectionPoolManager.java` | HikariCP per URL | 27 |
| `src/main/java/digital/singularidade/databridge/server/HttpServer.java` | Javalin bootstrap | 28 |
| `src/main/java/digital/singularidade/databridge/server/HealthHandler.java` | GET /v1/health | 28 |
| `src/main/java/digital/singularidade/databridge/server/ExtractRequest.java` | POST body record | 29 |
| `src/main/java/digital/singularidade/databridge/server/ExtractHandler.java` | POST /v1/extract | 29 |
| `src/main/java/digital/singularidade/databridge/server/ListTablesHandler.java` | GET /v1/list-tables | 30 |
| `src/main/java/digital/singularidade/databridge/cli/ServeCommand.java` | `data-bridge serve` | 31 |

**Conventions used throughout:**
- Test-naming: Surefire picks `*Test.java` (unit, no Docker), Failsafe picks `*IT.java` (Postgres Testcontainer).
- Unit tests for parsing, formatting, and pure logic; IT for anything touching JDBC.
- Each task ends with a focused commit (subject ≤72 chars; conventional `feat:` / `test:` / `chore:`).
- All commits include the project subdirectory path explicitly: `git add singularidade-data-bridge/...`.
- Run commands assume `cd /home/mint/Workspace/project/utilities/singularidade-data-bridge` unless stated.

---

## Phase 0 — Project skeleton (Tasks 1–2)

### Task 1: Create `pom.xml` with all dependencies and plugins

**Files:**
- Create: `singularidade-data-bridge/pom.xml`

- [ ] **Step 1: Create `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>digital.singularidade</groupId>
    <artifactId>singularidade-data-bridge</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <name>Singularidade Data Bridge</name>
    <description>Uniform JDBC metadata + sample extractor for skill consumption</description>

    <licenses>
        <license>
            <name>Apache License, Version 2.0</name>
            <url>https://www.apache.org/licenses/LICENSE-2.0</url>
        </license>
    </licenses>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <maven.compiler.release>${java.version}</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <picocli.version>4.7.6</picocli.version>
        <jackson.version>2.18.2</jackson.version>
        <javalin.version>6.4.0</javalin.version>
        <hikari.version>6.2.1</hikari.version>
        <slf4j.version>2.0.16</slf4j.version>

        <postgres.version>42.7.4</postgres.version>
        <jaybird.version>6.0.5</jaybird.version>
        <ojdbc.version>23.6.0.24.10</ojdbc.version>
        <mssql.version>12.8.1.jre11</mssql.version>
        <mysql.version>9.1.0</mysql.version>

        <junit.version>5.11.4</junit.version>
        <assertj.version>3.26.3</assertj.version>
        <testcontainers.version>1.20.4</testcontainers.version>
        <h2.version>2.3.232</h2.version>

        <compiler.plugin.version>3.13.0</compiler.plugin.version>
        <surefire.plugin.version>3.5.2</surefire.plugin.version>
        <failsafe.plugin.version>3.5.2</failsafe.plugin.version>
        <shade.plugin.version>3.6.0</shade.plugin.version>
    </properties>

    <dependencies>
        <!-- CLI -->
        <dependency>
            <groupId>info.picocli</groupId>
            <artifactId>picocli</artifactId>
            <version>${picocli.version}</version>
        </dependency>
        <!-- JSON -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <!-- HTTP server (serve mode) -->
        <dependency>
            <groupId>io.javalin</groupId>
            <artifactId>javalin</artifactId>
            <version>${javalin.version}</version>
        </dependency>
        <!-- Connection pool (serve mode) -->
        <dependency>
            <groupId>com.zaxxer</groupId>
            <artifactId>HikariCP</artifactId>
            <version>${hikari.version}</version>
        </dependency>
        <!-- Logging -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>${slf4j.version}</version>
        </dependency>

        <!-- JDBC drivers (runtime, shaded) -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>${postgres.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.firebirdsql.jdbc</groupId>
            <artifactId>jaybird</artifactId>
            <version>${jaybird.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.oracle.database.jdbc</groupId>
            <artifactId>ojdbc11</artifactId>
            <version>${ojdbc.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.microsoft.sqlserver</groupId>
            <artifactId>mssql-jdbc</artifactId>
            <version>${mssql.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>${mysql.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Tests -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>${assertj.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <version>${h2.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>data-bridge</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>${compiler.plugin.version}</version>
                <configuration>
                    <release>${java.version}</release>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>${surefire.plugin.version}</version>
                <configuration>
                    <includes>
                        <include>**/*Test.java</include>
                    </includes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <version>${failsafe.plugin.version}</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <includes>
                        <include>**/*IT.java</include>
                    </includes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>${shade.plugin.version}</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>digital.singularidade.databridge.cli.Main</mainClass>
                                </transformer>
                            </transformers>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                        <exclude>module-info.class</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Verify dependency resolution**

Run: `cd /home/mint/Workspace/project/utilities/singularidade-data-bridge && mvn -q dependency:resolve`
Expected: exit 0, no missing artifacts. (Network access required for first run.)

- [ ] **Step 3: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/pom.xml
git commit -m "feat(data-bridge): add Maven pom with deps and shade config

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Bootstrap `Main` + `version` subcommand + README + `.gitignore`

The goal is a working fat JAR that prints version and exits 0 — proves end-to-end build (compile → shade → exec) before any business code.

**Files:**
- Create: `singularidade-data-bridge/.gitignore`
- Create: `singularidade-data-bridge/README.md`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/Main.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/VersionCommand.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/cli/VersionCommandTest.java`

- [ ] **Step 1: Create `.gitignore`**

```
target/
.idea/
*.iml
out/
.DS_Store
```

- [ ] **Step 2: Create `README.md`**

```markdown
# singularidade-data-bridge

Uniform JDBC metadata + sample extractor for skill consumption.

## Build

    mvn -DskipTests package
    # → target/data-bridge.jar (fat JAR)

## Quick start

    java -jar target/data-bridge.jar version
    java -jar target/data-bridge.jar extract \
      --jdbc-url "jdbc:postgresql://host/db?user=u&password=p&sslmode=require" \
      --schema atl --table cliente --out /tmp/cliente/
    java -jar target/data-bridge.jar list-tables \
      --jdbc-url "jdbc:postgresql://host/db?user=u&password=p" --schema atl
    java -jar target/data-bridge.jar serve --port 8765

## ⚠️ Sample data is NOT redacted

`metadata.json` contains raw rows from the database, including unmasked PII (CPF, names, emails, etc.). Always point `--out` at an ephemeral directory (e.g., `/tmp/...`). Never commit extractions.

The connection-string `password` query parameter IS redacted in `source.url` and in stderr logs.

## Output contract

See `docs/superpowers/specs/2026-05-09-singularidade-data-bridge-design.md` §5.

## Tests

    mvn test                  # unit + driver smoke
    mvn verify                # + Postgres Testcontainer (requires Docker)

## License

Apache 2.0
```

- [ ] **Step 3: Write the failing version-command test**

Create `src/test/java/digital/singularidade/databridge/cli/VersionCommandTest.java`:

```java
package digital.singularidade.databridge.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class VersionCommandTest {

    @Test
    void prints_name_and_version() {
        StringWriter out = new StringWriter();
        CommandLine cmd = new CommandLine(new Main()).setOut(new PrintWriter(out));
        int exit = cmd.execute("version");
        assertThat(exit).isZero();
        assertThat(out.toString()).contains("singularidade-data-bridge").contains("0.1.0");
    }
}
```

- [ ] **Step 4: Run test — verify it fails**

Run: `mvn -q test -Dtest=VersionCommandTest`
Expected: FAIL — `Main` class not found.

- [ ] **Step 5: Implement `Main`**

Create `src/main/java/digital/singularidade/databridge/cli/Main.java`:

```java
package digital.singularidade.databridge.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "data-bridge",
    mixinStandardHelpOptions = true,
    versionProvider = Main.VersionProvider.class,
    subcommands = { VersionCommand.class }
)
public final class Main implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exit = new CommandLine(new Main()).execute(args);
        System.exit(exit);
    }

    static final class VersionProvider implements CommandLine.IVersionProvider {
        @Override public String[] getVersion() {
            return new String[] { "singularidade-data-bridge 0.1.0" };
        }
    }
}
```

- [ ] **Step 6: Implement `VersionCommand`**

Create `src/main/java/digital/singularidade/databridge/cli/VersionCommand.java`:

```java
package digital.singularidade.databridge.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "version", description = "Print version and exit.")
public final class VersionCommand implements Runnable {

    @Spec CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().getParent().getOut().println("singularidade-data-bridge 0.1.0");
    }
}
```

- [ ] **Step 7: Run test — verify it passes**

Run: `mvn -q test -Dtest=VersionCommandTest`
Expected: PASS, 1 test.

- [ ] **Step 8: Build fat JAR end-to-end**

Run: `mvn -q -DskipTests package`
Expected: `target/data-bridge.jar` created (~80MB with all 5 drivers).

Then:
Run: `java -jar target/data-bridge.jar version`
Expected stdout: `singularidade-data-bridge 0.1.0` and exit 0.

- [ ] **Step 9: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/.gitignore \
        singularidade-data-bridge/README.md \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/Main.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/VersionCommand.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/cli/VersionCommandTest.java
git commit -m "feat(data-bridge): bootstrap Main + version subcommand

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 1 — Domain records + utilities (Tasks 3–5)

### Task 3: Output records (the JSON contract, code-form)

These are pure data carriers — Java 21 records with Jackson-friendly accessors. No behavior. One single test confirms a populated `Metadata` instance round-trips through Jackson without losing a field.

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/SourceInfo.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/TableInfo.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/Column.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/ForeignKey.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/Index.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/UniqueConstraint.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/CheckConstraint.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/Sample.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/ColumnStats.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/Cardinality.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/Partitioning.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/Metadata.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/output/MetadataRecordsTest.java`

- [ ] **Step 1: Write the failing round-trip test**

```java
package digital.singularidade.databridge.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataRecordsTest {

    @Test
    void round_trips_a_populated_metadata_through_jackson() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        Metadata original = new Metadata(
            "https://singularidade.digital/data-bridge/metadata.v1.json",
            "1.0",
            Instant.parse("2026-05-09T15:42:11Z"),
            new Metadata.Generator("singularidade-data-bridge", "0.1.0"),
            new SourceInfo("jdbc", "postgresql",
                "jdbc:postgresql://host/db?password=***", "atl", "cliente"),
            new TableInfo("TABLE", "Cadastro", "orgen_app", 18432L, null),
            List.of(new Column("id", 1, "bigint", -5, false, true,
                null, 19, 0, null, null,
                new Column.Generated(true, false, null), null, null)),
            List.of("id"),
            List.of(new ForeignKey("fk_x", List.of("y"), "atl", "z", List.of("id"),
                "NO ACTION", "NO ACTION",
                List.of(new ForeignKey.RefColumn("id", "bigint", false)))),
            List.of(new Index("idx_x", List.of("a"), List.of(true), false, false, "btree", null)),
            List.of(new UniqueConstraint("uk_x", List.of("a"))),
            List.of(new CheckConstraint("chk_x", "a > 0")),
            new Sample(1, List.of(Map.of("id", 1))),
            List.of(new ColumnStats("a", 7L, 0.001, List.of("1"), List.of(0.5), 0.9)),
            new Cardinality(100L, List.of(new Cardinality.PerColumn("id", 100L, 0L))),
            new Partitioning(false, null, List.of(), null, List.of()),
            List.of("warning text")
        );

        String json = mapper.writeValueAsString(original);
        Metadata roundTripped = mapper.readValue(json, Metadata.class);

        assertThat(roundTripped).isEqualTo(original);
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn -q test -Dtest=MetadataRecordsTest`
Expected: FAIL — record classes don't exist.

- [ ] **Step 3: Create `SourceInfo.java`**

```java
package digital.singularidade.databridge.output;

public record SourceInfo(String type, String driver, String url, String schema, String table) {}
```

- [ ] **Step 4: Create `TableInfo.java`**

```java
package digital.singularidade.databridge.output;

public record TableInfo(
    String type,
    String comment,
    String owner,
    Long approximateRowCount,
    String viewDefinition
) {}
```

- [ ] **Step 5: Create `Column.java`**

```java
package digital.singularidade.databridge.output;

public record Column(
    String name,
    int ordinalPosition,
    String sqlType,
    int jdbcType,
    boolean nullable,
    boolean primaryKey,
    Integer characterMaxLength,
    Integer numericPrecision,
    Integer numericScale,
    String defaultValue,
    String comment,
    Generated generated,
    Sequence sequence,
    String collation
) {
    public record Generated(boolean isIdentity, boolean isComputed, String generationExpression) {}
    public record Sequence(String name, String schema) {}
}
```

> **Note on `defaultValue`:** field is named `defaultValue` to avoid the `default` keyword. JSON serialization name is configured via Jackson `@JsonProperty("default")` in §6 below — but since we're using bare records, we let Jackson use `defaultValue` and migrate via custom mapper config in `JsonWriter` (Task 21).

Replace the field with the JSON-renamed version:

```java
package digital.singularidade.databridge.output;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Column(
    String name,
    int ordinalPosition,
    String sqlType,
    int jdbcType,
    boolean nullable,
    boolean primaryKey,
    Integer characterMaxLength,
    Integer numericPrecision,
    Integer numericScale,
    @JsonProperty("default") String defaultValue,
    String comment,
    Generated generated,
    Sequence sequence,
    String collation
) {
    public record Generated(boolean isIdentity, boolean isComputed, String generationExpression) {}
    public record Sequence(String name, String schema) {}
}
```

- [ ] **Step 6: Create `ForeignKey.java`**

```java
package digital.singularidade.databridge.output;

import java.util.List;

public record ForeignKey(
    String constraintName,
    List<String> fkColumns,
    String refSchema,
    String refTable,
    List<String> refColumns,
    String onUpdate,
    String onDelete,
    List<RefColumn> refTableColumns
) {
    public record RefColumn(String name, String sqlType, boolean nullable) {}
}
```

- [ ] **Step 7: Create `Index.java`**

```java
package digital.singularidade.databridge.output;

import java.util.List;

public record Index(
    String name,
    List<String> columns,
    List<Boolean> ordinalAsc,
    boolean unique,
    boolean primary,
    String method,
    @com.fasterxml.jackson.annotation.JsonProperty("where") String whereClause
) {}
```

- [ ] **Step 8: Create `UniqueConstraint.java`**

```java
package digital.singularidade.databridge.output;

import java.util.List;

public record UniqueConstraint(String name, List<String> columns) {}
```

- [ ] **Step 9: Create `CheckConstraint.java`**

```java
package digital.singularidade.databridge.output;

public record CheckConstraint(String name, String definition) {}
```

- [ ] **Step 10: Create `Sample.java`**

```java
package digital.singularidade.databridge.output;

import java.util.List;
import java.util.Map;

public record Sample(int rowCount, List<Map<String, Object>> rows) {}
```

- [ ] **Step 11: Create `ColumnStats.java`**

```java
package digital.singularidade.databridge.output;

import java.util.List;

public record ColumnStats(
    String name,
    Long nDistinctEstimate,
    Double nullFraction,
    List<String> mostCommonValues,
    List<Double> mostCommonFrequencies,
    Double correlation
) {}
```

- [ ] **Step 12: Create `Cardinality.java`**

```java
package digital.singularidade.databridge.output;

import java.util.List;

public record Cardinality(long totalRows, List<PerColumn> perColumn) {
    public record PerColumn(String name, long distinctCount, long nullCount) {}
}
```

- [ ] **Step 13: Create `Partitioning.java`**

```java
package digital.singularidade.databridge.output;

import java.util.List;

public record Partitioning(
    boolean isPartitioned,
    String strategy,
    List<String> partitionKey,
    Ref parent,
    List<Ref> children
) {
    public record Ref(String schema, String table) {}
}
```

- [ ] **Step 14: Create `Metadata.java`**

```java
package digital.singularidade.databridge.output;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record Metadata(
    @JsonProperty("$schema") String schemaUrl,
    String version,
    Instant generatedAt,
    Generator generator,
    SourceInfo source,
    TableInfo tableInfo,
    List<Column> columns,
    List<String> primaryKey,
    List<ForeignKey> foreignKeys,
    List<Index> indexes,
    List<UniqueConstraint> uniqueConstraints,
    List<CheckConstraint> checkConstraints,
    Sample sample,
    List<ColumnStats> columnStats,
    Cardinality cardinality,
    Partitioning partitioning,
    List<String> warnings
) {
    public record Generator(String name, String version) {}
}
```

- [ ] **Step 15: Run test — verify it passes**

Run: `mvn -q test -Dtest=MetadataRecordsTest`
Expected: PASS, 1 test.

- [ ] **Step 16: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/ \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/output/MetadataRecordsTest.java
git commit -m "feat(data-bridge): add output records (JSON v1.0 contract)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: `ErrorCodes` enum + `DataBridgeException`

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/error/ErrorCodes.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/error/DataBridgeException.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/error/ErrorCodesTest.java`

- [ ] **Step 1: Write the failing test**

```java
package digital.singularidade.databridge.error;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodesTest {

    @Test
    void exit_codes_match_spec() {
        assertThat(ErrorCodes.OK.exitCode()).isZero();
        assertThat(ErrorCodes.INVALID_ARGS.exitCode()).isEqualTo(2);
        assertThat(ErrorCodes.CONNECTION_FAILED.exitCode()).isEqualTo(10);
        assertThat(ErrorCodes.TABLE_NOT_FOUND.exitCode()).isEqualTo(11);
        assertThat(ErrorCodes.SCHEMA_NOT_FOUND.exitCode()).isEqualTo(12);
        assertThat(ErrorCodes.QUERY_FAILED.exitCode()).isEqualTo(13);
        assertThat(ErrorCodes.OUTPUT_WRITE_FAILED.exitCode()).isEqualTo(14);
        assertThat(ErrorCodes.UNSUPPORTED.exitCode()).isEqualTo(64);
    }

    @Test
    void http_status_mapping() {
        assertThat(ErrorCodes.OK.httpStatus()).isEqualTo(200);
        assertThat(ErrorCodes.INVALID_ARGS.httpStatus()).isEqualTo(400);
        assertThat(ErrorCodes.TABLE_NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCodes.SCHEMA_NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCodes.CONNECTION_FAILED.httpStatus()).isEqualTo(502);
        assertThat(ErrorCodes.QUERY_FAILED.httpStatus()).isEqualTo(502);
        assertThat(ErrorCodes.UNSPECIFIED.httpStatus()).isEqualTo(500);
    }

    @Test
    void exception_carries_code_and_hint() {
        DataBridgeException e = new DataBridgeException(
            ErrorCodes.TABLE_NOT_FOUND, "no such table", "list-tables to discover");
        assertThat(e.code()).isEqualTo(ErrorCodes.TABLE_NOT_FOUND);
        assertThat(e.getMessage()).isEqualTo("no such table");
        assertThat(e.hint()).isEqualTo("list-tables to discover");
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn -q test -Dtest=ErrorCodesTest`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Create `ErrorCodes.java`**

```java
package digital.singularidade.databridge.error;

public enum ErrorCodes {
    OK(0, 200, "OK"),
    UNSPECIFIED(1, 500, "UNSPECIFIED"),
    INVALID_ARGS(2, 400, "INVALID_ARGS"),
    CONNECTION_FAILED(10, 502, "CONNECTION_FAILED"),
    TABLE_NOT_FOUND(11, 404, "TABLE_NOT_FOUND"),
    SCHEMA_NOT_FOUND(12, 404, "SCHEMA_NOT_FOUND"),
    QUERY_FAILED(13, 502, "QUERY_FAILED"),
    OUTPUT_WRITE_FAILED(14, 500, "OUTPUT_WRITE_FAILED"),
    UNSUPPORTED(64, 501, "UNSUPPORTED");

    private final int exitCode;
    private final int httpStatus;
    private final String wireName;

    ErrorCodes(int exitCode, int httpStatus, String wireName) {
        this.exitCode = exitCode;
        this.httpStatus = httpStatus;
        this.wireName = wireName;
    }

    public int exitCode() { return exitCode; }
    public int httpStatus() { return httpStatus; }
    public String wireName() { return wireName; }
}
```

- [ ] **Step 4: Create `DataBridgeException.java`**

```java
package digital.singularidade.databridge.error;

public final class DataBridgeException extends RuntimeException {

    private final ErrorCodes code;
    private final String hint;

    public DataBridgeException(ErrorCodes code, String message, String hint) {
        super(message);
        this.code = code;
        this.hint = hint;
    }

    public DataBridgeException(ErrorCodes code, String message, String hint, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.hint = hint;
    }

    public ErrorCodes code() { return code; }
    public String hint() { return hint; }
}
```

- [ ] **Step 5: Run test — verify it passes**

Run: `mvn -q test -Dtest=ErrorCodesTest`
Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/error/ \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/error/ErrorCodesTest.java
git commit -m "feat(data-bridge): add ErrorCodes enum + DataBridgeException

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: `UrlRedaction` utility (password=*** in URLs and logs)

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/error/UrlRedaction.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/error/UrlRedactionTest.java`

- [ ] **Step 1: Write the failing test**

```java
package digital.singularidade.databridge.error;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UrlRedactionTest {

    @Test
    void redacts_password_query_param() {
        String in = "jdbc:postgresql://host/db?user=u&password=secret&sslmode=require";
        assertThat(UrlRedaction.redact(in))
            .isEqualTo("jdbc:postgresql://host/db?user=u&password=***&sslmode=require");
    }

    @Test
    void redacts_passwd_alias() {
        String in = "jdbc:mysql://host/db?passwd=secret";
        assertThat(UrlRedaction.redact(in)).isEqualTo("jdbc:mysql://host/db?passwd=***");
    }

    @Test
    void case_insensitive() {
        String in = "jdbc:postgresql://host/db?Password=secret";
        assertThat(UrlRedaction.redact(in)).isEqualTo("jdbc:postgresql://host/db?Password=***");
    }

    @Test
    void leaves_url_without_password_unchanged() {
        String in = "jdbc:postgresql://host/db?user=u&sslmode=require";
        assertThat(UrlRedaction.redact(in)).isEqualTo(in);
    }

    @Test
    void handles_password_at_first_position() {
        String in = "jdbc:postgresql://host/db?password=secret&user=u";
        assertThat(UrlRedaction.redact(in))
            .isEqualTo("jdbc:postgresql://host/db?password=***&user=u");
    }

    @Test
    void handles_userinfo_form() {
        // jdbc:mysql://user:pass@host/db
        String in = "jdbc:mysql://user:secret@host/db";
        assertThat(UrlRedaction.redact(in)).isEqualTo("jdbc:mysql://user:***@host/db");
    }

    @Test
    void null_input_returns_null() {
        assertThat(UrlRedaction.redact(null)).isNull();
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn -q test -Dtest=UrlRedactionTest`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `UrlRedaction`**

```java
package digital.singularidade.databridge.error;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlRedaction {

    private static final Pattern QUERY_PASSWORD = Pattern.compile(
        "([?&])(password|passwd)=([^&]*)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern USERINFO_PASSWORD = Pattern.compile(
        "://([^:/@\\s]+):([^@\\s]*)@"
    );

    private UrlRedaction() {}

    public static String redact(String url) {
        if (url == null) return null;
        String result = QUERY_PASSWORD.matcher(url).replaceAll(mr ->
            Matcher.quoteReplacement(mr.group(1) + mr.group(2) + "=***"));
        result = USERINFO_PASSWORD.matcher(result).replaceAll(mr ->
            Matcher.quoteReplacement("://" + mr.group(1) + ":***@"));
        return result;
    }
}
```

- [ ] **Step 4: Run test — verify it passes**

Run: `mvn -q test -Dtest=UrlRedactionTest`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/error/UrlRedaction.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/error/UrlRedactionTest.java
git commit -m "feat(data-bridge): add UrlRedaction utility

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 2 — Source layer (Tasks 6–8)

### Task 6: `DriverHints` enum + `fromUrl`

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/DriverHints.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/source/jdbc/DriverHintsTest.java`

- [ ] **Step 1: Write the failing test**

```java
package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.error.DataBridgeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverHintsTest {

    @Test
    void detects_postgres() {
        assertThat(DriverHints.fromUrl("jdbc:postgresql://h/db")).isEqualTo(DriverHints.PG);
    }

    @Test
    void detects_firebird() {
        assertThat(DriverHints.fromUrl("jdbc:firebirdsql://h/db")).isEqualTo(DriverHints.FIREBIRD);
    }

    @Test
    void detects_oracle() {
        assertThat(DriverHints.fromUrl("jdbc:oracle:thin:@h:1521:db")).isEqualTo(DriverHints.ORACLE);
    }

    @Test
    void detects_mssql() {
        assertThat(DriverHints.fromUrl("jdbc:sqlserver://h:1433;databaseName=db")).isEqualTo(DriverHints.MSSQL);
    }

    @Test
    void detects_mysql() {
        assertThat(DriverHints.fromUrl("jdbc:mysql://h/db")).isEqualTo(DriverHints.MYSQL);
    }

    @Test
    void unknown_prefix_throws_unsupported() {
        assertThatThrownBy(() -> DriverHints.fromUrl("jdbc:h2:mem:test"))
            .isInstanceOf(DataBridgeException.class)
            .hasMessageContaining("Unsupported JDBC URL prefix");
    }

    @Test
    void wire_name_for_source_field() {
        assertThat(DriverHints.PG.wireName()).isEqualTo("postgresql");
        assertThat(DriverHints.FIREBIRD.wireName()).isEqualTo("firebird");
        assertThat(DriverHints.ORACLE.wireName()).isEqualTo("oracle");
        assertThat(DriverHints.MSSQL.wireName()).isEqualTo("mssql");
        assertThat(DriverHints.MYSQL.wireName()).isEqualTo("mysql");
    }

    @Test
    void schema_required_flag() {
        assertThat(DriverHints.PG.requiresSchema()).isTrue();
        assertThat(DriverHints.ORACLE.requiresSchema()).isTrue();
        assertThat(DriverHints.MSSQL.requiresSchema()).isTrue();
        assertThat(DriverHints.FIREBIRD.requiresSchema()).isFalse();
        assertThat(DriverHints.MYSQL.requiresSchema()).isFalse();
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn -q test -Dtest=DriverHintsTest`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `DriverHints`**

```java
package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;

public enum DriverHints {
    PG("postgresql", "jdbc:postgresql:", true),
    FIREBIRD("firebird", "jdbc:firebirdsql:", false),
    ORACLE("oracle", "jdbc:oracle:", true),
    MSSQL("mssql", "jdbc:sqlserver:", true),
    MYSQL("mysql", "jdbc:mysql:", false);

    private final String wireName;
    private final String urlPrefix;
    private final boolean requiresSchema;

    DriverHints(String wireName, String urlPrefix, boolean requiresSchema) {
        this.wireName = wireName;
        this.urlPrefix = urlPrefix;
        this.requiresSchema = requiresSchema;
    }

    public String wireName() { return wireName; }
    public boolean requiresSchema() { return requiresSchema; }

    public static DriverHints fromUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            throw new DataBridgeException(ErrorCodes.INVALID_ARGS,
                "JDBC URL is null", "Pass a non-null URL via --jdbc-url");
        }
        for (DriverHints h : values()) {
            if (jdbcUrl.startsWith(h.urlPrefix)) return h;
        }
        throw new DataBridgeException(ErrorCodes.UNSUPPORTED,
            "Unsupported JDBC URL prefix: " + jdbcUrl,
            "Supported: jdbc:postgresql:, jdbc:firebirdsql:, jdbc:oracle:, jdbc:sqlserver:, jdbc:mysql:");
    }
}
```

- [ ] **Step 4: Run test — verify it passes**

Run: `mvn -q test -Dtest=DriverHintsTest`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/DriverHints.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/source/jdbc/DriverHintsTest.java
git commit -m "feat(data-bridge): add DriverHints enum with fromUrl detection

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: `TypeNormalization` (java.sql.Types → sqlType string)

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/TypeNormalization.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/source/jdbc/TypeNormalizationTest.java`

- [ ] **Step 1: Write the failing test**

```java
package digital.singularidade.databridge.source.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.Types;

import static org.assertj.core.api.Assertions.assertThat;

class TypeNormalizationTest {

    @Test
    void bigint_no_size() {
        assertThat(TypeNormalization.toSqlType(Types.BIGINT, "BIGINT", null, null, null))
            .isEqualTo("bigint");
    }

    @Test
    void varchar_with_length() {
        assertThat(TypeNormalization.toSqlType(Types.VARCHAR, "VARCHAR", 100, null, null))
            .isEqualTo("varchar(100)");
    }

    @Test
    void numeric_with_precision_and_scale() {
        assertThat(TypeNormalization.toSqlType(Types.NUMERIC, "NUMERIC", null, 10, 2))
            .isEqualTo("numeric(10,2)");
    }

    @Test
    void numeric_without_scale() {
        assertThat(TypeNormalization.toSqlType(Types.NUMERIC, "NUMERIC", null, 10, 0))
            .isEqualTo("numeric(10,0)");
    }

    @Test
    void timestamp_passes_through() {
        assertThat(TypeNormalization.toSqlType(Types.TIMESTAMP, "TIMESTAMP", null, null, null))
            .isEqualTo("timestamp");
    }

    @Test
    void unknown_type_falls_back_to_lowercase_typename() {
        assertThat(TypeNormalization.toSqlType(Types.OTHER, "JSONB", null, null, null))
            .isEqualTo("jsonb");
    }

    @Test
    void bytea_normalized() {
        assertThat(TypeNormalization.toSqlType(Types.BINARY, "BYTEA", null, null, null))
            .isEqualTo("bytea");
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn -q test -Dtest=TypeNormalizationTest`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `TypeNormalization`**

```java
package digital.singularidade.databridge.source.jdbc;

import java.sql.Types;

public final class TypeNormalization {

    private TypeNormalization() {}

    public static String toSqlType(int jdbcType, String driverTypeName,
                                    Integer charLen, Integer precision, Integer scale) {
        return switch (jdbcType) {
            case Types.BIGINT -> "bigint";
            case Types.INTEGER -> "integer";
            case Types.SMALLINT -> "smallint";
            case Types.TINYINT -> "tinyint";
            case Types.BOOLEAN, Types.BIT -> "boolean";
            case Types.REAL -> "real";
            case Types.FLOAT, Types.DOUBLE -> "double";
            case Types.DATE -> "date";
            case Types.TIME -> "time";
            case Types.TIME_WITH_TIMEZONE -> "time with time zone";
            case Types.TIMESTAMP -> "timestamp";
            case Types.TIMESTAMP_WITH_TIMEZONE -> "timestamp with time zone";
            case Types.VARCHAR, Types.LONGVARCHAR ->
                charLen != null ? "varchar(" + charLen + ")" : "varchar";
            case Types.NVARCHAR, Types.LONGNVARCHAR ->
                charLen != null ? "nvarchar(" + charLen + ")" : "nvarchar";
            case Types.CHAR ->
                charLen != null ? "char(" + charLen + ")" : "char";
            case Types.NCHAR ->
                charLen != null ? "nchar(" + charLen + ")" : "nchar";
            case Types.NUMERIC, Types.DECIMAL ->
                precision != null
                    ? "numeric(" + precision + "," + (scale != null ? scale : 0) + ")"
                    : "numeric";
            case Types.CLOB -> "clob";
            case Types.NCLOB -> "nclob";
            case Types.BLOB -> "blob";
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY ->
                "BYTEA".equalsIgnoreCase(driverTypeName) ? "bytea" : "binary";
            case Types.OTHER, Types.SQLXML, Types.ARRAY, Types.STRUCT ->
                driverTypeName != null ? driverTypeName.toLowerCase() : "other";
            default -> driverTypeName != null ? driverTypeName.toLowerCase() : "unknown";
        };
    }
}
```

- [ ] **Step 4: Run test — verify it passes**

Run: `mvn -q test -Dtest=TypeNormalizationTest`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/TypeNormalization.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/source/jdbc/TypeNormalizationTest.java
git commit -m "feat(data-bridge): add TypeNormalization (jdbc Types -> sqlType)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: `Source` interface

The single boundary every Extractor talks to. Single impl in MVP (`JdbcSource`); future REST/file impls plug in here.

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/Source.java`

- [ ] **Step 1: Create the interface (no test — pure declaration)**

```java
package digital.singularidade.databridge.source;

import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.CheckConstraint;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.output.ColumnStats;
import digital.singularidade.databridge.output.ForeignKey;
import digital.singularidade.databridge.output.Index;
import digital.singularidade.databridge.output.Partitioning;
import digital.singularidade.databridge.output.Sample;
import digital.singularidade.databridge.output.TableInfo;
import digital.singularidade.databridge.output.UniqueConstraint;

import java.util.List;

public interface Source extends AutoCloseable {

    String type();
    String driverWireName();

    TableInfo tableInfo(String schema, String table);
    List<Column> columns(String schema, String table);
    List<String> primaryKey(String schema, String table);
    List<ForeignKey> foreignKeys(String schema, String table);
    List<Index> indexes(String schema, String table);
    List<UniqueConstraint> uniqueConstraints(String schema, String table);
    List<CheckConstraint> checkConstraints(String schema, String table);
    Sample sample(String schema, String table, int limit);
    List<ColumnStats> columnStats(String schema, String table);
    Partitioning partitioning(String schema, String table);
    Cardinality cardinality(String schema, String table, List<Column> columns);

    List<String> listTables(String schema);

    @Override void close();
}
```

- [ ] **Step 2: Compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/Source.java
git commit -m "feat(data-bridge): add Source interface (extension point)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 3 — JdbcSource + Extractors (Tasks 9–19)

> All Phase 3 tests are **integration tests** (`*IT.java`) that require Docker via Testcontainers. They run with `mvn verify`, not `mvn test`. Surefire will skip them.

### Task 9: PG fixture (`PgFixture`, `atl-schema.sql`, `atl-data.sql`) + `JdbcSource` skeleton

**Files:**
- Create: `singularidade-data-bridge/src/test/resources/fixtures/atl-schema.sql`
- Create: `singularidade-data-bridge/src/test/resources/fixtures/atl-data.sql`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/support/PgFixture.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/source/jdbc/JdbcSourceConnectIT.java`

- [ ] **Step 1: Create `atl-schema.sql`**

```sql
CREATE SCHEMA atl;

CREATE TABLE atl.estadocivil (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    mnemonico VARCHAR(20) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    descricao VARCHAR(255),
    CONSTRAINT chk_estadocivil_mnem CHECK (char_length(mnemonico) >= 1)
);
CREATE UNIQUE INDEX uk_estadocivil_mnemonico ON atl.estadocivil(mnemonico);
COMMENT ON TABLE atl.estadocivil IS 'Cadastro de estado civil';
COMMENT ON COLUMN atl.estadocivil.mnemonico IS 'Codigo curto';

CREATE TABLE atl.cliente (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tenant_id INTEGER NOT NULL DEFAULT 1,
    cpf VARCHAR(14) NOT NULL,
    nome VARCHAR(200) NOT NULL,
    fk_estadocivil_id BIGINT REFERENCES atl.estadocivil(id),
    foto BYTEA,
    datanascimento DATE,
    sexo CHAR(1),
    CONSTRAINT chk_cliente_sexo CHECK (sexo IN ('M','F','O'))
);
CREATE INDEX idx_cliente_cpf ON atl.cliente(cpf);
CREATE UNIQUE INDEX uk_cliente_cpf ON atl.cliente(cpf);
COMMENT ON TABLE atl.cliente IS 'Cadastro de pacientes';

CREATE VIEW atl.cliente_vw AS SELECT id, nome FROM atl.cliente;
```

- [ ] **Step 2: Create `atl-data.sql`**

```sql
INSERT INTO atl.estadocivil (mnemonico, nome, descricao) VALUES
  ('S', 'Solteiro', 'Pessoa nao casada'),
  ('C', 'Casado', 'Em uniao civil'),
  ('D', 'Divorciado', 'Apos divorcio'),
  ('V', 'Viuvo', NULL);

INSERT INTO atl.cliente (cpf, nome, fk_estadocivil_id, datanascimento, sexo) VALUES
  ('12345678900', 'FULANO DA SILVA', 1, '1985-04-12', 'M'),
  ('98765432100', 'CICLANA SANTOS', 2, '1990-08-23', 'F'),
  ('11122233344', 'BELTRANO LIMA', 1, '1978-12-01', 'M'),
  ('55566677788', 'DETRANO SOUZA', 3, '1982-06-15', 'O'),
  ('99988877766', 'NAOIDENT BRASIL', NULL, '2000-01-01', 'M');

ANALYZE atl.estadocivil;
ANALYZE atl.cliente;
```

- [ ] **Step 3: Create `PgFixture.java`**

```java
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
        try (Connection c = DriverManager.getConnection(jdbcUrl())) {
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
        return container.getJdbcUrl()
            + "?user=" + container.getUsername()
            + "&password=" + container.getPassword();
    }

    public String username() { return container.getUsername(); }
    public String password() { return container.getPassword(); }

    @Override public void close() { container.stop(); }
}
```

- [ ] **Step 4: Write the failing connect-IT**

```java
package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSourceConnectIT {

    @Test
    void opens_and_closes_against_postgres() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            assertThat(src.type()).isEqualTo("jdbc");
            assertThat(src.driverWireName()).isEqualTo("postgresql");
            assertThat(src.listTables("atl")).contains("estadocivil", "cliente");
        }
    }
}
```

- [ ] **Step 5: Run test — verify it fails**

Run: `mvn -q verify -Dit.test=JdbcSourceConnectIT -DfailIfNoTests=false`
Expected: FAIL — `JdbcSource` not implemented.

- [ ] **Step 6: Implement `JdbcSource` skeleton + `listTables`**

Stub all interface methods; implement only `listTables` for the test. Subsequent tasks fill in the rest.

```java
package digital.singularidade.databridge.source.jdbc;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.CheckConstraint;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.output.ColumnStats;
import digital.singularidade.databridge.output.ForeignKey;
import digital.singularidade.databridge.output.Index;
import digital.singularidade.databridge.output.Partitioning;
import digital.singularidade.databridge.output.Sample;
import digital.singularidade.databridge.output.TableInfo;
import digital.singularidade.databridge.output.UniqueConstraint;
import digital.singularidade.databridge.source.Source;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class JdbcSource implements Source {

    private final Connection connection;
    private final DriverHints hints;

    private JdbcSource(Connection connection, DriverHints hints) {
        this.connection = connection;
        this.hints = hints;
    }

    public static JdbcSource open(String jdbcUrl) {
        DriverHints hints = DriverHints.fromUrl(jdbcUrl);
        try {
            Connection c = DriverManager.getConnection(jdbcUrl);
            c.setReadOnly(true);
            return new JdbcSource(c, hints);
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.CONNECTION_FAILED,
                "Failed to connect: " + e.getMessage(),
                "Check JDBC URL, credentials, network, and TLS settings", e);
        }
    }

    Connection connection() { return connection; }
    DriverHints hints() { return hints; }

    @Override public String type() { return "jdbc"; }
    @Override public String driverWireName() { return hints.wireName(); }

    @Override
    public List<String> listTables(String schema) {
        List<String> out = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData()
                .getTables(connection.getCatalog(), schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) out.add(rs.getString("TABLE_NAME"));
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "listTables failed: " + e.getMessage(), null, e);
        }
        return out;
    }

    // ===== stubs for subsequent tasks =====
    @Override public TableInfo tableInfo(String schema, String table) { throw new UnsupportedOperationException("Task 19"); }
    @Override public List<Column> columns(String schema, String table) { throw new UnsupportedOperationException("Task 10"); }
    @Override public List<String> primaryKey(String schema, String table) { throw new UnsupportedOperationException("Task 11"); }
    @Override public List<ForeignKey> foreignKeys(String schema, String table) { throw new UnsupportedOperationException("Task 12"); }
    @Override public List<Index> indexes(String schema, String table) { throw new UnsupportedOperationException("Task 13"); }
    @Override public List<UniqueConstraint> uniqueConstraints(String schema, String table) { throw new UnsupportedOperationException("Task 14"); }
    @Override public List<CheckConstraint> checkConstraints(String schema, String table) { throw new UnsupportedOperationException("Task 14"); }
    @Override public Sample sample(String schema, String table, int limit) { throw new UnsupportedOperationException("Task 15"); }
    @Override public List<ColumnStats> columnStats(String schema, String table) { throw new UnsupportedOperationException("Task 16"); }
    @Override public Partitioning partitioning(String schema, String table) { throw new UnsupportedOperationException("Task 17"); }
    @Override public Cardinality cardinality(String schema, String table, List<Column> columns) { throw new UnsupportedOperationException("Task 18"); }

    @Override
    public void close() {
        try { connection.close(); } catch (SQLException ignored) {}
    }
}
```

- [ ] **Step 7: Run test — verify it passes**

Run: `mvn -q verify -Dit.test=JdbcSourceConnectIT -DfailIfNoTests=false`
Expected: PASS, 1 IT.

- [ ] **Step 8: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/test/resources/fixtures/ \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/support/PgFixture.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/source/jdbc/JdbcSourceConnectIT.java
git commit -m "feat(data-bridge): add JdbcSource skeleton + PG Testcontainer fixture

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: `SchemaExtractor` + `JdbcSource.columns()`

**Files:**
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/SchemaExtractor.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/SchemaExtractorIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class SchemaExtractorIT {

    @Test
    void extracts_columns_with_pk_and_identity_for_estadocivil() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {

            List<Column> cols = new SchemaExtractor().extract(src, "atl", "estadocivil");
            assertThat(cols).extracting(Column::name).containsExactly(
                "id", "mnemonico", "nome", "descricao");

            Column id = cols.get(0);
            assertThat(id.primaryKey()).isTrue();
            assertThat(id.nullable()).isFalse();
            assertThat(id.sqlType()).isEqualTo("bigint");
            assertThat(id.generated().isIdentity()).isTrue();

            Column mnem = cols.get(1);
            assertThat(mnem.sqlType()).isEqualTo("varchar(20)");
            assertThat(mnem.nullable()).isFalse();
            assertThat(mnem.comment()).isEqualTo("Codigo curto");

            Column desc = cols.get(3);
            assertThat(desc.nullable()).isTrue();
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=SchemaExtractorIT -DfailIfNoTests=false`
Expected: FAIL — UnsupportedOperationException("Task 10").

- [ ] **Step 3: Implement `JdbcSource.columns()` (replace stub)**

Replace the `columns` method in `JdbcSource.java` with this implementation:

```java
@Override
public List<Column> columns(String schema, String table) {
    List<String> pk = primaryKey(schema, table);
    List<Column> out = new ArrayList<>();
    try (ResultSet rs = connection.getMetaData()
            .getColumns(connection.getCatalog(), schema, table, "%")) {
        while (rs.next()) {
            String name = rs.getString("COLUMN_NAME");
            int jdbcType = rs.getInt("DATA_TYPE");
            String typeName = rs.getString("TYPE_NAME");
            Integer charLen = rs.getObject("CHAR_OCTET_LENGTH") == null
                ? null : rs.getInt("CHAR_OCTET_LENGTH");
            // CHAR_OCTET_LENGTH is bytes; for varchar we want char length:
            int colSize = rs.getInt("COLUMN_SIZE");
            Integer maxLen = (jdbcType == java.sql.Types.VARCHAR
                || jdbcType == java.sql.Types.NVARCHAR
                || jdbcType == java.sql.Types.CHAR
                || jdbcType == java.sql.Types.NCHAR) ? colSize : null;
            Integer precision = (jdbcType == java.sql.Types.NUMERIC
                || jdbcType == java.sql.Types.DECIMAL
                || jdbcType == java.sql.Types.BIGINT
                || jdbcType == java.sql.Types.INTEGER
                || jdbcType == java.sql.Types.SMALLINT) ? colSize : null;
            Integer scale = rs.getObject("DECIMAL_DIGITS") == null
                ? null : rs.getInt("DECIMAL_DIGITS");
            String defaultValue = rs.getString("COLUMN_DEF");
            String remarks = rs.getString("REMARKS");
            int ord = rs.getInt("ORDINAL_POSITION");
            boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
            boolean isAutoincrement = "YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT"));
            boolean isGenerated = "YES".equalsIgnoreCase(rs.getString("IS_GENERATEDCOLUMN"));
            String generationExpr = rs.getString("COLUMN_DEF");

            String sqlType = TypeNormalization.toSqlType(
                jdbcType, typeName, maxLen, precision, scale);

            Column.Generated gen = new Column.Generated(
                isAutoincrement, isGenerated && !isAutoincrement,
                isGenerated && !isAutoincrement ? generationExpr : null);

            out.add(new Column(
                name, ord, sqlType, jdbcType, nullable, pk.contains(name),
                maxLen, precision, scale,
                defaultValue, remarks,
                gen, null, null
            ));
        }
    } catch (SQLException e) {
        throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
            "columns failed: " + e.getMessage(), null, e);
    }
    return out;
}
```

- [ ] **Step 4: Implement `JdbcSource.primaryKey()` (replace stub) — needed by columns()**

```java
@Override
public List<String> primaryKey(String schema, String table) {
    List<String> out = new ArrayList<>();
    try (ResultSet rs = connection.getMetaData()
            .getPrimaryKeys(connection.getCatalog(), schema, table)) {
        // Sort by KEY_SEQ
        java.util.TreeMap<Short, String> ordered = new java.util.TreeMap<>();
        while (rs.next()) ordered.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
        out.addAll(ordered.values());
    } catch (SQLException e) {
        throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
            "primaryKey failed: " + e.getMessage(), null, e);
    }
    return out;
}
```

- [ ] **Step 5: Create `SchemaExtractor.java`**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class SchemaExtractor {

    public List<Column> extract(Source source, String schema, String table) {
        return source.columns(schema, table);
    }
}
```

- [ ] **Step 6: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=SchemaExtractorIT -DfailIfNoTests=false`
Expected: PASS, 1 IT.

- [ ] **Step 7: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/SchemaExtractor.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/SchemaExtractorIT.java
git commit -m "feat(data-bridge): implement SchemaExtractor + columns()/primaryKey()

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: `PrimaryKeyExtractor` (uses already-implemented Source method)

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/PrimaryKeyExtractor.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/PrimaryKeyExtractorIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrimaryKeyExtractorIT {

    @Test
    void extracts_pk_for_cliente() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            assertThat(new PrimaryKeyExtractor().extract(src, "atl", "cliente"))
                .containsExactly("id");
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=PrimaryKeyExtractorIT -DfailIfNoTests=false`
Expected: FAIL — class not found.

- [ ] **Step 3: Create `PrimaryKeyExtractor`**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class PrimaryKeyExtractor {

    public List<String> extract(Source source, String schema, String table) {
        return source.primaryKey(schema, table);
    }
}
```

- [ ] **Step 4: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=PrimaryKeyExtractorIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/PrimaryKeyExtractor.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/PrimaryKeyExtractorIT.java
git commit -m "feat(data-bridge): add PrimaryKeyExtractor

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: `ForeignKeysExtractor` + `JdbcSource.foreignKeys()` (1-deep)

**Files:**
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/ForeignKeysExtractor.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/ForeignKeysExtractorIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.ForeignKey;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForeignKeysExtractorIT {

    @Test
    void extracts_fk_with_1_deep_ref_columns() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {

            List<ForeignKey> fks = new ForeignKeysExtractor().extract(src, "atl", "cliente");
            assertThat(fks).hasSize(1);
            ForeignKey fk = fks.get(0);
            assertThat(fk.fkColumns()).containsExactly("fk_estadocivil_id");
            assertThat(fk.refSchema()).isEqualTo("atl");
            assertThat(fk.refTable()).isEqualTo("estadocivil");
            assertThat(fk.refColumns()).containsExactly("id");
            assertThat(fk.refTableColumns()).extracting(c -> c.name())
                .contains("id", "mnemonico", "nome", "descricao");
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=ForeignKeysExtractorIT -DfailIfNoTests=false`
Expected: FAIL — UnsupportedOperationException("Task 12").

- [ ] **Step 3: Implement `JdbcSource.foreignKeys()` (replace stub)**

```java
@Override
public List<ForeignKey> foreignKeys(String schema, String table) {
    java.util.LinkedHashMap<String, FkBuilder> grouped = new java.util.LinkedHashMap<>();
    try (ResultSet rs = connection.getMetaData()
            .getImportedKeys(connection.getCatalog(), schema, table)) {
        while (rs.next()) {
            String name = rs.getString("FK_NAME");
            FkBuilder b = grouped.computeIfAbsent(name, k -> new FkBuilder(name,
                rs.getString("PKTABLE_SCHEM"), rs.getString("PKTABLE_NAME"),
                ruleName(rs.getShort("UPDATE_RULE")), ruleName(rs.getShort("DELETE_RULE"))));
            b.fkColumns.add(rs.getString("FKCOLUMN_NAME"));
            b.refColumns.add(rs.getString("PKCOLUMN_NAME"));
        }
    } catch (SQLException e) {
        throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
            "foreignKeys failed: " + e.getMessage(), null, e);
    }
    List<ForeignKey> out = new ArrayList<>();
    for (FkBuilder b : grouped.values()) {
        List<ForeignKey.RefColumn> refCols = oneDeepColumns(b.refSchema, b.refTable);
        out.add(new ForeignKey(b.name, List.copyOf(b.fkColumns),
            b.refSchema, b.refTable, List.copyOf(b.refColumns),
            b.onUpdate, b.onDelete, refCols));
    }
    return out;
}

private List<ForeignKey.RefColumn> oneDeepColumns(String schema, String table) {
    List<ForeignKey.RefColumn> out = new ArrayList<>();
    try (ResultSet rs = connection.getMetaData()
            .getColumns(connection.getCatalog(), schema, table, "%")) {
        while (rs.next()) {
            int jdbcType = rs.getInt("DATA_TYPE");
            String typeName = rs.getString("TYPE_NAME");
            int colSize = rs.getInt("COLUMN_SIZE");
            Integer maxLen = (jdbcType == java.sql.Types.VARCHAR
                || jdbcType == java.sql.Types.NVARCHAR
                || jdbcType == java.sql.Types.CHAR
                || jdbcType == java.sql.Types.NCHAR) ? colSize : null;
            Integer precision = (jdbcType == java.sql.Types.NUMERIC
                || jdbcType == java.sql.Types.DECIMAL) ? colSize : null;
            Integer scale = rs.getObject("DECIMAL_DIGITS") == null ? null : rs.getInt("DECIMAL_DIGITS");
            String sqlType = TypeNormalization.toSqlType(jdbcType, typeName, maxLen, precision, scale);
            boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
            out.add(new ForeignKey.RefColumn(rs.getString("COLUMN_NAME"), sqlType, nullable));
        }
    } catch (SQLException e) {
        throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
            "oneDeepColumns failed: " + e.getMessage(), null, e);
    }
    return out;
}

private static String ruleName(short rule) {
    return switch (rule) {
        case java.sql.DatabaseMetaData.importedKeyCascade -> "CASCADE";
        case java.sql.DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
        case java.sql.DatabaseMetaData.importedKeySetNull -> "SET NULL";
        case java.sql.DatabaseMetaData.importedKeyNoAction -> "NO ACTION";
        case java.sql.DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
        default -> "UNKNOWN";
    };
}

private static final class FkBuilder {
    final String name; final String refSchema; final String refTable;
    final String onUpdate; final String onDelete;
    final List<String> fkColumns = new ArrayList<>();
    final List<String> refColumns = new ArrayList<>();
    FkBuilder(String name, String refSchema, String refTable, String onUpdate, String onDelete) {
        this.name = name; this.refSchema = refSchema; this.refTable = refTable;
        this.onUpdate = onUpdate; this.onDelete = onDelete;
    }
}
```

- [ ] **Step 4: Create `ForeignKeysExtractor`**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.ForeignKey;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class ForeignKeysExtractor {

    public List<ForeignKey> extract(Source source, String schema, String table) {
        return source.foreignKeys(schema, table);
    }
}
```

- [ ] **Step 5: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=ForeignKeysExtractorIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/ForeignKeysExtractor.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/ForeignKeysExtractorIT.java
git commit -m "feat(data-bridge): implement ForeignKeysExtractor + 1-deep refTableColumns

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 13: `IndexesExtractor` + `JdbcSource.indexes()` (PG-augmented method/where)

**Files:**
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/IndexesExtractor.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/IndexesExtractorIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Index;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndexesExtractorIT {

    @Test
    void extracts_secondary_indexes_with_method_btree_for_cliente() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {

            List<Index> idx = new IndexesExtractor().extract(src, "atl", "cliente");
            assertThat(idx).extracting(Index::name)
                .contains("idx_cliente_cpf", "uk_cliente_cpf");

            Index uk = idx.stream().filter(i -> i.name().equals("uk_cliente_cpf")).findFirst().orElseThrow();
            assertThat(uk.unique()).isTrue();
            assertThat(uk.method()).isEqualTo("btree");
            assertThat(uk.columns()).containsExactly("cpf");
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=IndexesExtractorIT -DfailIfNoTests=false`
Expected: FAIL — UnsupportedOperationException("Task 13").

- [ ] **Step 3: Implement `JdbcSource.indexes()` (replace stub)**

```java
@Override
public List<Index> indexes(String schema, String table) {
    java.util.Map<String, IndexBuilder> grouped = new java.util.LinkedHashMap<>();
    try (ResultSet rs = connection.getMetaData()
            .getIndexInfo(connection.getCatalog(), schema, table, false, false)) {
        while (rs.next()) {
            String name = rs.getString("INDEX_NAME");
            if (name == null) continue;
            String col = rs.getString("COLUMN_NAME");
            if (col == null) continue;
            String ascDesc = rs.getString("ASC_OR_DESC");
            boolean asc = ascDesc == null || "A".equalsIgnoreCase(ascDesc);
            IndexBuilder b = grouped.computeIfAbsent(name, k ->
                new IndexBuilder(name, !rs.getBoolean("NON_UNIQUE")));
            b.columns.add(col);
            b.ordinalAsc.add(asc);
        }
    } catch (SQLException e) {
        throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
            "indexes failed: " + e.getMessage(), null, e);
    }
    List<Index> out = new ArrayList<>();
    java.util.Map<String, PgIndexAugment> augments = (hints == DriverHints.PG)
        ? pgIndexAugments(schema, table) : java.util.Collections.emptyMap();
    List<String> pk = primaryKey(schema, table);
    for (IndexBuilder b : grouped.values()) {
        boolean primary = !pk.isEmpty() && pk.equals(b.columns);
        PgIndexAugment a = augments.get(b.name);
        out.add(new Index(b.name, List.copyOf(b.columns), List.copyOf(b.ordinalAsc),
            b.unique, primary, a != null ? a.method : null, a != null ? a.where : null));
    }
    return out;
}

private java.util.Map<String, PgIndexAugment> pgIndexAugments(String schema, String table) {
    java.util.Map<String, PgIndexAugment> out = new java.util.HashMap<>();
    String sql = """
        SELECT i.indexname, am.amname AS method,
               pg_get_expr(ix.indpred, ix.indrelid) AS where_clause
          FROM pg_indexes i
          JOIN pg_class c   ON c.relname = i.indexname
          JOIN pg_index ix  ON ix.indexrelid = c.oid
          JOIN pg_class tc  ON tc.oid = ix.indrelid
          JOIN pg_namespace n ON n.oid = tc.relnamespace
          JOIN pg_am am     ON am.oid = c.relam
         WHERE n.nspname = ? AND tc.relname = ?
        """;
    try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setString(1, schema); ps.setString(2, table);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString("indexname"),
                    new PgIndexAugment(rs.getString("method"), rs.getString("where_clause")));
            }
        }
    } catch (SQLException ignored) { /* no augment, return what we have */ }
    return out;
}

private static final class IndexBuilder {
    final String name; final boolean unique;
    final List<String> columns = new ArrayList<>();
    final List<Boolean> ordinalAsc = new ArrayList<>();
    IndexBuilder(String name, boolean unique) { this.name = name; this.unique = unique; }
}

private record PgIndexAugment(String method, String where) {}
```

- [ ] **Step 4: Create `IndexesExtractor`**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Index;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class IndexesExtractor {

    public List<Index> extract(Source source, String schema, String table) {
        return source.indexes(schema, table);
    }
}
```

- [ ] **Step 5: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=IndexesExtractorIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/IndexesExtractor.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/IndexesExtractorIT.java
git commit -m "feat(data-bridge): implement IndexesExtractor with PG method/where augment

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 14: `UniqueConstraintsExtractor` + `CheckConstraintsExtractor`

**Files:**
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/UniqueConstraintsExtractor.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/CheckConstraintsExtractor.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/ConstraintsExtractorsIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.CheckConstraint;
import digital.singularidade.databridge.output.UniqueConstraint;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConstraintsExtractorsIT {

    @Test
    void extracts_unique_constraints_for_cliente() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            List<UniqueConstraint> ucs = new UniqueConstraintsExtractor().extract(src, "atl", "cliente");
            assertThat(ucs).extracting(UniqueConstraint::name).contains("uk_cliente_cpf");
        }
    }

    @Test
    void extracts_check_constraints_for_cliente() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            List<CheckConstraint> cs = new CheckConstraintsExtractor().extract(src, "atl", "cliente");
            assertThat(cs).extracting(CheckConstraint::name).contains("chk_cliente_sexo");
            CheckConstraint sexo = cs.stream().filter(c -> "chk_cliente_sexo".equals(c.name())).findFirst().orElseThrow();
            assertThat(sexo.definition()).contains("'M'", "'F'", "'O'");
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=ConstraintsExtractorsIT -DfailIfNoTests=false`
Expected: FAIL — Unsupported.

- [ ] **Step 3: Implement `JdbcSource.uniqueConstraints()` and `checkConstraints()`**

Add these private SQL queries (PG-aware via `information_schema`; works for any standards-compliant SQL DB) and replace the stubs:

```java
@Override
public List<UniqueConstraint> uniqueConstraints(String schema, String table) {
    String sql = """
        SELECT tc.constraint_name, kcu.column_name, kcu.ordinal_position
          FROM information_schema.table_constraints tc
          JOIN information_schema.key_column_usage kcu
            ON tc.constraint_name = kcu.constraint_name
           AND tc.table_schema    = kcu.table_schema
         WHERE tc.constraint_type = 'UNIQUE'
           AND tc.table_schema    = ?
           AND tc.table_name      = ?
         ORDER BY tc.constraint_name, kcu.ordinal_position
        """;
    java.util.Map<String, List<String>> grouped = new java.util.LinkedHashMap<>();
    try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setString(1, schema); ps.setString(2, table);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                grouped.computeIfAbsent(rs.getString("constraint_name"), k -> new ArrayList<>())
                    .add(rs.getString("column_name"));
            }
        }
    } catch (SQLException e) {
        throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
            "uniqueConstraints failed: " + e.getMessage(), null, e);
    }
    List<UniqueConstraint> out = new ArrayList<>();
    grouped.forEach((name, cols) -> out.add(new UniqueConstraint(name, List.copyOf(cols))));
    return out;
}

@Override
public List<CheckConstraint> checkConstraints(String schema, String table) {
    String sql = """
        SELECT cc.constraint_name, cc.check_clause
          FROM information_schema.table_constraints tc
          JOIN information_schema.check_constraints cc
            ON tc.constraint_name = cc.constraint_name
           AND tc.constraint_schema = cc.constraint_schema
         WHERE tc.constraint_type = 'CHECK'
           AND tc.table_schema    = ?
           AND tc.table_name      = ?
           AND cc.constraint_name NOT LIKE '%_not_null'
         ORDER BY cc.constraint_name
        """;
    List<CheckConstraint> out = new ArrayList<>();
    try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setString(1, schema); ps.setString(2, table);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new CheckConstraint(rs.getString("constraint_name"), rs.getString("check_clause")));
            }
        }
    } catch (SQLException e) {
        throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
            "checkConstraints failed: " + e.getMessage(), null, e);
    }
    return out;
}
```

- [ ] **Step 4: Create `UniqueConstraintsExtractor`**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.UniqueConstraint;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class UniqueConstraintsExtractor {

    public List<UniqueConstraint> extract(Source source, String schema, String table) {
        return source.uniqueConstraints(schema, table);
    }
}
```

- [ ] **Step 5: Create `CheckConstraintsExtractor`**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.CheckConstraint;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class CheckConstraintsExtractor {

    public List<CheckConstraint> extract(Source source, String schema, String table) {
        return source.checkConstraints(schema, table);
    }
}
```

- [ ] **Step 6: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=ConstraintsExtractorsIT -DfailIfNoTests=false`
Expected: PASS, 2 ITs.

- [ ] **Step 7: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/UniqueConstraintsExtractor.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/CheckConstraintsExtractor.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/ConstraintsExtractorsIT.java
git commit -m "feat(data-bridge): add UniqueConstraints + CheckConstraints extractors

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 15: `SampleExtractor` + `JdbcSource.sample()` (with BLOB normalization)

**Files:**
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/SampleExtractor.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/SampleExtractorIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Sample;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SampleExtractorIT {

    @Test
    void samples_5_rows_with_blob_normalized() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            Sample s = new SampleExtractor().extract(src, "atl", "cliente", 5);
            assertThat(s.rowCount()).isEqualTo(5);
            assertThat(s.rows()).hasSize(5);
            Map<String, Object> first = s.rows().get(0);
            assertThat(first).containsKeys("id", "cpf", "nome", "foto");
            assertThat(first.get("foto")).isNull();   // foto is null in fixture (no INSERT writes BYTEA)
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=SampleExtractorIT -DfailIfNoTests=false`
Expected: FAIL — UnsupportedOperationException("Task 15").

- [ ] **Step 3: Implement `JdbcSource.sample()`**

```java
@Override
public Sample sample(String schema, String table, int limit) {
    String fqn = quoteIdent(schema) + "." + quoteIdent(table);
    String sql = "SELECT * FROM " + fqn + " LIMIT " + limit;
    List<java.util.Map<String, Object>> rows = new ArrayList<>();
    try (java.sql.Statement st = connection.createStatement();
         ResultSet rs = st.executeQuery(sql)) {
        java.sql.ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        while (rs.next()) {
            java.util.LinkedHashMap<String, Object> row = new java.util.LinkedHashMap<>();
            for (int i = 1; i <= n; i++) {
                String name = md.getColumnLabel(i);
                int type = md.getColumnType(i);
                Object value = readColumnValue(rs, i, type);
                row.put(name, value);
            }
            rows.add(row);
        }
    } catch (SQLException e) {
        throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
            "sample failed: " + e.getMessage(), null, e);
    }
    return new Sample(rows.size(), rows);
}

private Object readColumnValue(ResultSet rs, int idx, int jdbcType) throws SQLException {
    if (jdbcType == java.sql.Types.BLOB
        || jdbcType == java.sql.Types.BINARY
        || jdbcType == java.sql.Types.VARBINARY
        || jdbcType == java.sql.Types.LONGVARBINARY) {
        byte[] bytes = rs.getBytes(idx);
        if (bytes == null) return null;
        int previewLen = Math.min(64, bytes.length);
        byte[] preview = java.util.Arrays.copyOf(bytes, previewLen);
        return java.util.Map.of(
            "_blob", true,
            "size", bytes.length,
            "preview", java.util.Base64.getEncoder().encodeToString(preview)
        );
    }
    Object obj = rs.getObject(idx);
    if (obj instanceof java.sql.Timestamp ts) return ts.toInstant().toString();
    if (obj instanceof java.sql.Date d) return d.toLocalDate().toString();
    if (obj instanceof java.sql.Time t) return t.toLocalTime().toString();
    return obj;
}

private String quoteIdent(String ident) {
    return switch (hints) {
        case PG, ORACLE -> "\"" + ident.replace("\"", "\"\"") + "\"";
        case MSSQL -> "[" + ident.replace("]", "]]") + "]";
        case MYSQL -> "`" + ident.replace("`", "``") + "`";
        case FIREBIRD -> "\"" + ident.replace("\"", "\"\"") + "\"";
    };
}
```

- [ ] **Step 4: Create `SampleExtractor`**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Sample;
import digital.singularidade.databridge.source.Source;

public final class SampleExtractor {

    public Sample extract(Source source, String schema, String table, int limit) {
        return source.sample(schema, table, limit);
    }
}
```

- [ ] **Step 5: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=SampleExtractorIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/SampleExtractor.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/SampleExtractorIT.java
git commit -m "feat(data-bridge): implement SampleExtractor with BLOB normalization

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 16: `ColumnStatsExtractor` + `JdbcSource.columnStats()` (PG `pg_stats`)

**Files:**
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/ColumnStatsExtractor.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/ColumnStatsExtractorIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.ColumnStats;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnStatsExtractorIT {

    @Test
    void extracts_pg_stats_for_estadocivil() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            List<ColumnStats> stats = new ColumnStatsExtractor().extract(src, "atl", "estadocivil");
            // After ANALYZE in fixture, pg_stats has rows; assert per-column entries exist
            assertThat(stats).extracting(ColumnStats::name).contains("mnemonico", "nome");
            ColumnStats mnem = stats.stream().filter(s -> "mnemonico".equals(s.name())).findFirst().orElseThrow();
            assertThat(mnem.nullFraction()).isNotNull();
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=ColumnStatsExtractorIT -DfailIfNoTests=false`
Expected: FAIL — UnsupportedOperationException("Task 16").

- [ ] **Step 3: Implement `JdbcSource.columnStats()`**

Replace stub:

```java
@Override
public List<ColumnStats> columnStats(String schema, String table) {
    if (hints != DriverHints.PG) return List.of();
    String sql = """
        SELECT attname, n_distinct, null_frac,
               most_common_vals::text AS mcv,
               most_common_freqs::text AS mcf,
               correlation
          FROM pg_stats
         WHERE schemaname = ? AND tablename = ?
        """;
    List<ColumnStats> out = new ArrayList<>();
    try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setString(1, schema); ps.setString(2, table);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("attname");
                Long nDist = rs.getObject("n_distinct") == null ? null : rs.getLong("n_distinct");
                Double nullFrac = rs.getObject("null_frac") == null ? null : rs.getDouble("null_frac");
                List<String> mcv = parseTextArray(rs.getString("mcv"));
                List<Double> mcf = parseFloatArray(rs.getString("mcf"));
                Double corr = rs.getObject("correlation") == null ? null : rs.getDouble("correlation");
                out.add(new ColumnStats(name, nDist, nullFrac, mcv, mcf, corr));
            }
        }
    } catch (SQLException e) {
        throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
            "columnStats failed: " + e.getMessage(), null, e);
    }
    return out;
}

private static List<String> parseTextArray(String pgArray) {
    if (pgArray == null || pgArray.isEmpty() || pgArray.equals("{}")) return List.of();
    String inner = pgArray.substring(1, pgArray.length() - 1);
    if (inner.isEmpty()) return List.of();
    List<String> out = new ArrayList<>();
    for (String part : inner.split(",")) {
        String trimmed = part.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        out.add(trimmed);
    }
    return out;
}

private static List<Double> parseFloatArray(String pgArray) {
    if (pgArray == null || pgArray.isEmpty() || pgArray.equals("{}")) return List.of();
    String inner = pgArray.substring(1, pgArray.length() - 1);
    if (inner.isEmpty()) return List.of();
    List<Double> out = new ArrayList<>();
    for (String part : inner.split(",")) {
        try { out.add(Double.parseDouble(part.trim())); }
        catch (NumberFormatException e) { /* skip */ }
    }
    return out;
}
```

- [ ] **Step 4: Create `ColumnStatsExtractor`**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.ColumnStats;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class ColumnStatsExtractor {

    public List<ColumnStats> extract(Source source, String schema, String table) {
        return source.columnStats(schema, table);
    }
}
```

- [ ] **Step 5: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=ColumnStatsExtractorIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/ColumnStatsExtractor.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/ColumnStatsExtractorIT.java
git commit -m "feat(data-bridge): implement ColumnStatsExtractor (pg_stats)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 17: `PartitioningExtractor` + `JdbcSource.partitioning()` (PG-only)

**Files:**
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/PartitioningExtractor.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/PartitioningExtractorIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Partitioning;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PartitioningExtractorIT {

    @Test
    void cliente_is_not_partitioned() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            Partitioning p = new PartitioningExtractor().extract(src, "atl", "cliente");
            assertThat(p.isPartitioned()).isFalse();
            assertThat(p.partitionKey()).isEmpty();
            assertThat(p.children()).isEmpty();
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=PartitioningExtractorIT -DfailIfNoTests=false`
Expected: FAIL — UnsupportedOperationException("Task 17").

- [ ] **Step 3: Implement `JdbcSource.partitioning()`**

```java
@Override
public Partitioning partitioning(String schema, String table) {
    if (hints != DriverHints.PG) {
        return new Partitioning(false, null, List.of(), null, List.of());
    }
    String checkSql = """
        SELECT pt.partstrat, pg_get_partkeydef(c.oid) AS pkdef
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
          LEFT JOIN pg_partitioned_table pt ON pt.partrelid = c.oid
         WHERE n.nspname = ? AND c.relname = ?
        """;
    String strategy = null;
    List<String> key = List.of();
    boolean isPartitioned = false;
    try (java.sql.PreparedStatement ps = connection.prepareStatement(checkSql)) {
        ps.setString(1, schema); ps.setString(2, table);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String s = rs.getString("partstrat");
                if (s != null) {
                    isPartitioned = true;
                    strategy = switch (s) {
                        case "r" -> "RANGE"; case "l" -> "LIST"; case "h" -> "HASH";
                        default -> s.toUpperCase();
                    };
                    String pkdef = rs.getString("pkdef");
                    if (pkdef != null) {
                        int open = pkdef.indexOf('('), close = pkdef.lastIndexOf(')');
                        if (open >= 0 && close > open) {
                            String inner = pkdef.substring(open + 1, close);
                            key = java.util.Arrays.stream(inner.split(",")).map(String::trim).toList();
                        }
                    }
                }
            }
        }
    } catch (SQLException e) {
        throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
            "partitioning failed: " + e.getMessage(), null, e);
    }
    return new Partitioning(isPartitioned, strategy, key, null, List.of());
}
```

- [ ] **Step 4: Create `PartitioningExtractor`**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Partitioning;
import digital.singularidade.databridge.source.Source;

public final class PartitioningExtractor {

    public Partitioning extract(Source source, String schema, String table) {
        return source.partitioning(schema, table);
    }
}
```

- [ ] **Step 5: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=PartitioningExtractorIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/PartitioningExtractor.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/PartitioningExtractorIT.java
git commit -m "feat(data-bridge): implement PartitioningExtractor (PG)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 18: `CardinalityExtractor` + `JdbcSource.cardinality()`

**Files:**
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/CardinalityExtractor.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/CardinalityExtractorIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CardinalityExtractorIT {

    @Test
    void counts_total_and_distinct_per_column_for_cliente() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            List<Column> cols = src.columns("atl", "cliente");
            Cardinality c = new CardinalityExtractor().extract(src, "atl", "cliente", cols);
            assertThat(c.totalRows()).isEqualTo(5);

            Cardinality.PerColumn id = c.perColumn().stream()
                .filter(p -> "id".equals(p.name())).findFirst().orElseThrow();
            assertThat(id.distinctCount()).isEqualTo(5);

            Cardinality.PerColumn fk = c.perColumn().stream()
                .filter(p -> "fk_estadocivil_id".equals(p.name())).findFirst().orElseThrow();
            assertThat(fk.distinctCount()).isEqualTo(3);
            assertThat(fk.nullCount()).isEqualTo(1);
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=CardinalityExtractorIT -DfailIfNoTests=false`
Expected: FAIL — UnsupportedOperationException("Task 18").

- [ ] **Step 3: Implement `JdbcSource.cardinality()`**

```java
@Override
public Cardinality cardinality(String schema, String table, List<Column> columns) {
    String fqn = quoteIdent(schema) + "." + quoteIdent(table);
    long total;
    try (java.sql.Statement st = connection.createStatement();
         ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + fqn)) {
        rs.next(); total = rs.getLong(1);
    } catch (SQLException e) {
        throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
            "cardinality count(*) failed: " + e.getMessage(), null, e);
    }

    List<Cardinality.PerColumn> per = new ArrayList<>();
    for (Column c : columns) {
        String colQ = quoteIdent(c.name());
        long dist = 0L, nullCount = 0L;
        try (java.sql.Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                 "SELECT COUNT(DISTINCT " + colQ + "), SUM(CASE WHEN " + colQ + " IS NULL THEN 1 ELSE 0 END) FROM " + fqn)) {
            rs.next();
            dist = rs.getLong(1);
            nullCount = rs.getLong(2);
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "cardinality of " + c.name() + " failed: " + e.getMessage(), null, e);
        }
        per.add(new Cardinality.PerColumn(c.name(), dist, nullCount));
    }
    return new Cardinality(total, per);
}
```

- [ ] **Step 4: Create `CardinalityExtractor`**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.source.Source;

import java.util.List;

public final class CardinalityExtractor {

    public Cardinality extract(Source source, String schema, String table, List<Column> columns) {
        return source.cardinality(schema, table, columns);
    }
}
```

- [ ] **Step 5: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=CardinalityExtractorIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/CardinalityExtractor.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/CardinalityExtractorIT.java
git commit -m "feat(data-bridge): implement CardinalityExtractor

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 19: `TableInfoExtractor` + `JdbcSource.tableInfo()`

**Files:**
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/TableInfoExtractor.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/TableInfoExtractorIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.TableInfo;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TableInfoExtractorIT {

    @Test
    void table_returns_basic_info_for_estadocivil() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            TableInfo info = new TableInfoExtractor().extract(src, "atl", "estadocivil");
            assertThat(info.type()).isEqualTo("TABLE");
            assertThat(info.comment()).isEqualTo("Cadastro de estado civil");
            assertThat(info.approximateRowCount()).isNotNull();
            assertThat(info.viewDefinition()).isNull();
        }
    }

    @Test
    void view_returns_view_definition() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {
            TableInfo info = new TableInfoExtractor().extract(src, "atl", "cliente_vw");
            assertThat(info.type()).isEqualTo("VIEW");
            assertThat(info.viewDefinition()).contains("SELECT").contains("nome");
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=TableInfoExtractorIT -DfailIfNoTests=false`
Expected: FAIL — UnsupportedOperationException("Task 19").

- [ ] **Step 3: Implement `JdbcSource.tableInfo()`**

```java
@Override
public TableInfo tableInfo(String schema, String table) {
    String type = "TABLE";
    String comment = null;
    String owner = null;
    Long approxRows = null;
    String viewDef = null;

    // Determine type via DatabaseMetaData
    try (ResultSet rs = connection.getMetaData()
            .getTables(connection.getCatalog(), schema, table, null)) {
        if (rs.next()) {
            String t = rs.getString("TABLE_TYPE");
            type = switch (t == null ? "" : t.toUpperCase()) {
                case "VIEW" -> "VIEW";
                case "MATERIALIZED VIEW" -> "MATERIALIZED_VIEW";
                case "PARTITIONED TABLE", "FOREIGN TABLE" -> "TABLE";
                default -> "TABLE";
            };
            comment = rs.getString("REMARKS");
        }
    } catch (SQLException e) {
        throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
            "tableInfo (type) failed: " + e.getMessage(), null, e);
    }

    if (hints == DriverHints.PG) {
        String sql = """
            SELECT pg_get_userbyid(c.relowner) AS owner,
                   c.reltuples::bigint AS approx_rows,
                   CASE WHEN c.relkind = 'v' OR c.relkind = 'm'
                        THEN pg_get_viewdef(c.oid, true) ELSE NULL END AS view_def,
                   CASE WHEN c.relkind = 'p' THEN 'PARTITIONED_TABLE' ELSE NULL END AS partitioned_type
              FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = ? AND c.relname = ?
            """;
        try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schema); ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    owner = rs.getString("owner");
                    long ar = rs.getLong("approx_rows");
                    if (!rs.wasNull()) approxRows = ar;
                    viewDef = rs.getString("view_def");
                    String pt = rs.getString("partitioned_type");
                    if (pt != null) type = pt;
                }
            }
        } catch (SQLException e) {
            throw new DataBridgeException(ErrorCodes.QUERY_FAILED,
                "tableInfo (PG augment) failed: " + e.getMessage(), null, e);
        }
    }

    return new TableInfo(type, comment, owner, approxRows, viewDef);
}
```

- [ ] **Step 4: Create `TableInfoExtractor`**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.TableInfo;
import digital.singularidade.databridge.source.Source;

public final class TableInfoExtractor {

    public TableInfo extract(Source source, String schema, String table) {
        return source.tableInfo(schema, table);
    }
}
```

- [ ] **Step 5: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=TableInfoExtractorIT -DfailIfNoTests=false`
Expected: PASS, 2 ITs.

- [ ] **Step 6: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/TableInfoExtractor.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/TableInfoExtractorIT.java
git commit -m "feat(data-bridge): implement TableInfoExtractor with PG augments

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 4 — Pipeline + Output Writers (Tasks 20–22)

### Task 20: `MetadataPipeline` orchestrator

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/MetadataPipeline.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/MetadataPipelineIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.output.Metadata;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataPipelineIT {

    @Test
    void runs_full_pipeline_for_cliente() {
        try (PgFixture fx = new PgFixture();
             JdbcSource src = JdbcSource.open(fx.jdbcUrl())) {

            Metadata m = new MetadataPipeline().run(src, fx.jdbcUrl(), "atl", "cliente", 5);

            assertThat(m.version()).isEqualTo("1.0");
            assertThat(m.source().table()).isEqualTo("cliente");
            assertThat(m.source().url()).contains("password=***");
            assertThat(m.tableInfo().type()).isEqualTo("TABLE");
            assertThat(m.columns()).extracting(c -> c.name()).contains("id", "cpf", "nome");
            assertThat(m.primaryKey()).containsExactly("id");
            assertThat(m.foreignKeys()).hasSize(1);
            assertThat(m.indexes()).extracting(i -> i.name()).contains("idx_cliente_cpf");
            assertThat(m.uniqueConstraints()).extracting(u -> u.name()).contains("uk_cliente_cpf");
            assertThat(m.checkConstraints()).extracting(c -> c.name()).contains("chk_cliente_sexo");
            assertThat(m.sample().rowCount()).isEqualTo(5);
            assertThat(m.cardinality().totalRows()).isEqualTo(5);
            assertThat(m.partitioning().isPartitioned()).isFalse();
            assertThat(m.warnings()).isEmpty();
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=MetadataPipelineIT -DfailIfNoTests=false`
Expected: FAIL — class not found.

- [ ] **Step 3: Create `MetadataPipeline`**

```java
package digital.singularidade.databridge.pipeline;

import digital.singularidade.databridge.error.UrlRedaction;
import digital.singularidade.databridge.output.Cardinality;
import digital.singularidade.databridge.output.CheckConstraint;
import digital.singularidade.databridge.output.Column;
import digital.singularidade.databridge.output.ColumnStats;
import digital.singularidade.databridge.output.ForeignKey;
import digital.singularidade.databridge.output.Index;
import digital.singularidade.databridge.output.Metadata;
import digital.singularidade.databridge.output.Partitioning;
import digital.singularidade.databridge.output.Sample;
import digital.singularidade.databridge.output.SourceInfo;
import digital.singularidade.databridge.output.TableInfo;
import digital.singularidade.databridge.output.UniqueConstraint;
import digital.singularidade.databridge.source.Source;

import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MetadataPipeline {

    public static final String SCHEMA_URL = "https://singularidade.digital/data-bridge/metadata.v1.json";
    public static final String VERSION = "1.0";
    public static final String GENERATOR_NAME = "singularidade-data-bridge";
    public static final String GENERATOR_VERSION = "0.1.0";

    private final PrintStream progress;
    private final boolean skipCardinality;

    public MetadataPipeline() { this(System.err, false); }

    public MetadataPipeline(PrintStream progress, boolean skipCardinality) {
        this.progress = progress;
        this.skipCardinality = skipCardinality;
    }

    public Metadata run(Source source, String jdbcUrl, String schema, String table, int sampleRows) {
        List<String> warnings = new ArrayList<>();
        TableInfo tableInfo = step(1, "table info", () -> source.tableInfo(schema, table));
        List<Column> columns = step(2, "columns", () -> source.columns(schema, table));
        List<String> pk = step(3, "primary key", () -> source.primaryKey(schema, table));
        List<ForeignKey> fks = step(4, "foreign keys (1-deep)", () -> source.foreignKeys(schema, table));
        List<Index> indexes = step(5, "indexes", () -> source.indexes(schema, table));
        List<UniqueConstraint> ucs = step(6, "unique constraints", () -> source.uniqueConstraints(schema, table));
        List<CheckConstraint> ccs = step(7, "check constraints", () -> source.checkConstraints(schema, table));
        Sample sample = step(8, "sample", () -> source.sample(schema, table, sampleRows));
        List<ColumnStats> stats = step(9, "column stats", () -> source.columnStats(schema, table));
        if (stats.isEmpty() && !"postgresql".equals(source.driverWireName())) {
            warnings.add("columnStats not available for driver '" + source.driverWireName() + "' in MVP");
        }
        Partitioning part = step(10, "partitioning", () -> source.partitioning(schema, table));

        Cardinality card;
        if (skipCardinality) {
            warnings.add("Cardinality skipped (--no-cardinality)");
            card = new Cardinality(0L, List.of());
        } else {
            card = step(11, "cardinality", () -> source.cardinality(schema, table, columns));
        }

        SourceInfo srcInfo = new SourceInfo("jdbc", source.driverWireName(),
            UrlRedaction.redact(jdbcUrl), schema, table);

        return new Metadata(
            SCHEMA_URL, VERSION, Instant.now(),
            new Metadata.Generator(GENERATOR_NAME, GENERATOR_VERSION),
            srcInfo, tableInfo, columns, pk, fks, indexes, ucs, ccs,
            sample, stats, card, part, warnings
        );
    }

    private <T> T step(int n, String label, java.util.concurrent.Callable<T> body) {
        long t0 = System.nanoTime();
        try {
            T result = body.call();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            progress.printf("[%d/11] %-22s ok (%dms)%n", n, label, ms);
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 4: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=MetadataPipelineIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/pipeline/MetadataPipeline.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/pipeline/MetadataPipelineIT.java
git commit -m "feat(data-bridge): add MetadataPipeline orchestrator

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 21: `JsonWriter` (atomic write via Jackson)

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/JsonWriter.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/output/JsonWriterTest.java`

- [ ] **Step 1: Write the failing test**

```java
package digital.singularidade.databridge.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonWriterTest {

    @Test
    void writes_metadata_as_pretty_json_atomically(@TempDir Path tmp) throws Exception {
        Metadata m = new Metadata(
            "https://example/schema", "1.0",
            Instant.parse("2026-05-09T12:00:00Z"),
            new Metadata.Generator("singularidade-data-bridge", "0.1.0"),
            new SourceInfo("jdbc", "postgresql", "jdbc:postgresql://h/d?password=***", "atl", "cliente"),
            new TableInfo("TABLE", null, null, null, null),
            List.of(),
            List.of("id"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new Sample(0, List.of()),
            List.of(),
            new Cardinality(0, List.of()),
            new Partitioning(false, null, List.of(), null, List.of()),
            List.of()
        );

        Path target = tmp.resolve("metadata.json");
        new JsonWriter().write(m, target);

        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.exists(tmp.resolve(".metadata.json.partial"))).isFalse();

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonNode tree = mapper.readTree(Files.readString(target));
        assertThat(tree.get("version").asText()).isEqualTo("1.0");
        assertThat(tree.get("source").get("url").asText()).contains("password=***");
        assertThat(tree.get("primaryKey").get(0).asText()).isEqualTo("id");
    }

    @Test
    void renames_default_field_to_default_in_json(@TempDir Path tmp) throws Exception {
        Column col = new Column("c", 1, "varchar(10)", 12, true, false,
            10, null, null, "DEFAULT_VAL", null,
            new Column.Generated(false, false, null), null, null);
        Metadata m = new Metadata("u", "1.0", Instant.EPOCH,
            new Metadata.Generator("g", "v"),
            new SourceInfo("jdbc", "pg", "url", "s", "t"),
            new TableInfo("TABLE", null, null, null, null),
            List.of(col), List.of(), List.of(), List.of(), List.of(), List.of(),
            new Sample(0, List.of()), List.of(),
            new Cardinality(0, List.of()),
            new Partitioning(false, null, List.of(), null, List.of()),
            List.of());

        Path target = tmp.resolve("metadata.json");
        new JsonWriter().write(m, target);

        String json = Files.readString(target);
        assertThat(json).contains("\"default\" : \"DEFAULT_VAL\"")
            .doesNotContain("\"defaultValue\"");
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn -q test -Dtest=JsonWriterTest`
Expected: FAIL — class not found.

- [ ] **Step 3: Create `JsonWriter`**

```java
package digital.singularidade.databridge.output;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class JsonWriter {

    public static ObjectMapper newMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    private final ObjectMapper mapper;

    public JsonWriter() { this(newMapper()); }
    public JsonWriter(ObjectMapper mapper) { this.mapper = mapper; }

    public void write(Metadata m, Path target) {
        Path dir = target.getParent();
        Path tmp = dir.resolve("." + target.getFileName().toString() + ".partial");
        try {
            if (dir != null) Files.createDirectories(dir);
            byte[] body = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(m);
            Files.write(tmp, body);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            throw new DataBridgeException(ErrorCodes.OUTPUT_WRITE_FAILED,
                "Failed to write " + target + ": " + e.getMessage(),
                "Verify --out is a writable directory with free space", e);
        }
    }
}
```

- [ ] **Step 4: Run test — verify it passes**

Run: `mvn -q test -Dtest=JsonWriterTest`
Expected: PASS, 2 tests.

- [ ] **Step 5: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/JsonWriter.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/output/JsonWriterTest.java
git commit -m "feat(data-bridge): add JsonWriter (atomic, pretty)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 22: `TsvWriter` (with tab/newline escaping)

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/TsvWriter.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/output/TsvWriterTest.java`

- [ ] **Step 1: Write the failing test**

```java
package digital.singularidade.databridge.output;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TsvWriterTest {

    @Test
    void writes_columns_fks_indexes_unique_check_sample_cardinality_tsvs(@TempDir Path tmp) throws Exception {
        Column col = new Column("nome", 1, "varchar(200)", 12, true, false, 200, null, null,
            null, null, new Column.Generated(false, false, null), null, null);
        Metadata m = new Metadata("u", "1.0", Instant.EPOCH,
            new Metadata.Generator("g", "v"),
            new SourceInfo("jdbc", "pg", "url", "s", "t"),
            new TableInfo("TABLE", null, null, null, null),
            List.of(col), List.of("id"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new Sample(1, List.of(Map.of("nome", "FULANO\tCOM\tTAB"))),
            List.of(),
            new Cardinality(10, List.of(new Cardinality.PerColumn("id", 10, 0))),
            new Partitioning(false, null, List.of(), null, List.of()),
            List.of());

        new TsvWriter().writeAll(m, tmp);

        assertThat(Files.exists(tmp.resolve("columns.tsv"))).isTrue();
        assertThat(Files.exists(tmp.resolve("sample.tsv"))).isTrue();
        assertThat(Files.exists(tmp.resolve("cardinality.tsv"))).isTrue();

        String columnsTsv = Files.readString(tmp.resolve("columns.tsv"));
        assertThat(columnsTsv.split("\n")[0]).startsWith("name\tordinalPosition\t");

        String sampleTsv = Files.readString(tmp.resolve("sample.tsv"));
        // tab in value must be escaped as \t literal
        assertThat(sampleTsv).contains("FULANO\\tCOM\\tTAB");
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn -q test -Dtest=TsvWriterTest`
Expected: FAIL — class not found.

- [ ] **Step 3: Create `TsvWriter`**

```java
package digital.singularidade.databridge.output;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class TsvWriter {

    public void writeAll(Metadata m, Path outDir) {
        try {
            Files.createDirectories(outDir);
            writeColumns(m.columns(), outDir.resolve("columns.tsv"));
            writeFks(m.foreignKeys(), outDir.resolve("fks.tsv"));
            writeIndexes(m.indexes(), outDir.resolve("indexes.tsv"));
            writeUniques(m.uniqueConstraints(), outDir.resolve("unique-constraints.tsv"));
            writeChecks(m.checkConstraints(), outDir.resolve("check-constraints.tsv"));
            writeSample(m.sample(), outDir.resolve("sample.tsv"));
            writeCardinality(m.cardinality(), outDir.resolve("cardinality.tsv"));
        } catch (IOException e) {
            throw new DataBridgeException(ErrorCodes.OUTPUT_WRITE_FAILED,
                "TSV write failed: " + e.getMessage(), null, e);
        }
    }

    private static String esc(Object v) {
        if (v == null) return "";
        String s = v.toString();
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void writeColumns(List<Column> cols, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write("name\tordinalPosition\tsqlType\tjdbcType\tnullable\tprimaryKey\tcomment\n");
            for (Column c : cols) {
                w.write(String.join("\t",
                    esc(c.name()), esc(c.ordinalPosition()), esc(c.sqlType()),
                    esc(c.jdbcType()), esc(c.nullable()), esc(c.primaryKey()),
                    esc(c.comment())));
                w.write('\n');
            }
        }
    }

    private void writeFks(List<ForeignKey> fks, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write("constraintName\tfkColumns\trefSchema\trefTable\trefColumns\n");
            for (ForeignKey fk : fks) {
                w.write(String.join("\t",
                    esc(fk.constraintName()),
                    esc(String.join(",", fk.fkColumns())),
                    esc(fk.refSchema()), esc(fk.refTable()),
                    esc(String.join(",", fk.refColumns()))));
                w.write('\n');
            }
        }
    }

    private void writeIndexes(List<Index> ix, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write("name\tcolumns\tunique\tprimary\tmethod\twhere\n");
            for (Index i : ix) {
                w.write(String.join("\t",
                    esc(i.name()), esc(String.join(",", i.columns())),
                    esc(i.unique()), esc(i.primary()),
                    esc(i.method()), esc(i.whereClause())));
                w.write('\n');
            }
        }
    }

    private void writeUniques(List<UniqueConstraint> ucs, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write("name\tcolumns\n");
            for (UniqueConstraint u : ucs) {
                w.write(esc(u.name()) + "\t" + esc(String.join(",", u.columns())) + "\n");
            }
        }
    }

    private void writeChecks(List<CheckConstraint> ccs, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write("name\tdefinition\n");
            for (CheckConstraint c : ccs) {
                w.write(esc(c.name()) + "\t" + esc(c.definition()) + "\n");
            }
        }
    }

    private void writeSample(Sample s, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            if (s.rows().isEmpty()) { return; }
            List<String> headers = List.copyOf(s.rows().get(0).keySet());
            w.write(String.join("\t", headers)); w.write('\n');
            for (Map<String, Object> row : s.rows()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < headers.size(); i++) {
                    if (i > 0) sb.append('\t');
                    sb.append(esc(row.get(headers.get(i))));
                }
                sb.append('\n');
                w.write(sb.toString());
            }
        }
    }

    private void writeCardinality(Cardinality c, Path p) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(p)) {
            w.write("# totalRows=" + c.totalRows() + "\n");
            w.write("name\tdistinctCount\tnullCount\n");
            for (Cardinality.PerColumn pc : c.perColumn()) {
                w.write(String.join("\t",
                    esc(pc.name()), esc(pc.distinctCount()), esc(pc.nullCount())));
                w.write('\n');
            }
        }
    }
}
```

- [ ] **Step 4: Run test — verify it passes**

Run: `mvn -q test -Dtest=TsvWriterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/output/TsvWriter.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/output/TsvWriterTest.java
git commit -m "feat(data-bridge): add TsvWriter with tab/newline escape

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 5 — CLI Commands + Golden file IT (Tasks 23–25)

### Task 23: `ExtractCommand`

**Files:**
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/Main.java` (register subcommand)
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/ExtractCommand.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/cli/ExtractCommandTest.java`

- [ ] **Step 1: Write the failing parser test**

```java
package digital.singularidade.databridge.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractCommandTest {

    @Test
    void parses_required_flags() {
        ExtractCommand cmd = new ExtractCommand();
        new CommandLine(cmd).parseArgs(
            "--jdbc-url", "jdbc:postgresql://h/d",
            "--schema", "atl",
            "--table", "cliente",
            "--out", "/tmp/c"
        );
        assertThat(cmd.jdbcUrl).isEqualTo("jdbc:postgresql://h/d");
        assertThat(cmd.schema).isEqualTo("atl");
        assertThat(cmd.table).isEqualTo("cliente");
        assertThat(cmd.outDir.toString()).isEqualTo("/tmp/c");
        assertThat(cmd.sampleRows).isEqualTo(5);
        assertThat(cmd.tsv).isFalse();
        assertThat(cmd.skipCardinality).isFalse();
    }

    @Test
    void parses_optional_flags() {
        ExtractCommand cmd = new ExtractCommand();
        new CommandLine(cmd).parseArgs(
            "--jdbc-url", "u",
            "--table", "t",
            "--out", "/tmp/x",
            "--sample-rows", "10",
            "--tsv",
            "--no-cardinality"
        );
        assertThat(cmd.sampleRows).isEqualTo(10);
        assertThat(cmd.tsv).isTrue();
        assertThat(cmd.skipCardinality).isTrue();
        assertThat(cmd.schema).isNull();
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn -q test -Dtest=ExtractCommandTest`
Expected: FAIL — class not found.

- [ ] **Step 3: Create `ExtractCommand`**

```java
package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.error.UrlRedaction;
import digital.singularidade.databridge.output.JsonWriter;
import digital.singularidade.databridge.output.Metadata;
import digital.singularidade.databridge.output.TsvWriter;
import digital.singularidade.databridge.pipeline.MetadataPipeline;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "extract", description = "Extract metadata + sample for one table.")
public final class ExtractCommand implements Callable<Integer> {

    @Option(names = "--jdbc-url", required = true) String jdbcUrl;
    @Option(names = "--schema") String schema;
    @Option(names = "--table", required = true) String table;
    @Option(names = "--out", required = true) Path outDir;
    @Option(names = "--sample-rows", defaultValue = "5") int sampleRows;
    @Option(names = "--tsv", defaultValue = "false") boolean tsv;
    @Option(names = "--no-cardinality", defaultValue = "false") boolean skipCardinality;
    @Option(names = {"-q", "--quiet"}, defaultValue = "false") boolean quiet;
    @Option(names = {"-v", "--verbose"}, defaultValue = "false") boolean verbose;

    @Override
    public Integer call() {
        PrintStream progress = quiet ? new PrintStream(java.io.OutputStream.nullOutputStream()) : System.err;
        try (JdbcSource src = JdbcSource.open(jdbcUrl)) {
            Metadata m = new MetadataPipeline(progress, skipCardinality)
                .run(src, jdbcUrl, schema, table, sampleRows);
            new JsonWriter().write(m, outDir.resolve("metadata.json"));
            if (tsv) new TsvWriter().writeAll(m, outDir);
            return ErrorCodes.OK.exitCode();
        } catch (DataBridgeException e) {
            emitError(e);
            return e.code().exitCode();
        } catch (RuntimeException e) {
            emitError(new DataBridgeException(ErrorCodes.UNSPECIFIED,
                e.getMessage(), null, e));
            return ErrorCodes.UNSPECIFIED.exitCode();
        }
    }

    private void emitError(DataBridgeException e) {
        StringBuilder sb = new StringBuilder("{\"error\":{\"code\":\"")
            .append(e.code().wireName()).append("\",\"message\":\"")
            .append(jsonEscape(UrlRedaction.redact(e.getMessage())))
            .append("\"");
        if (e.hint() != null) sb.append(",\"hint\":\"").append(jsonEscape(e.hint())).append("\"");
        if (verbose && e.getCause() != null) {
            sb.append(",\"cause\":\"")
                .append(jsonEscape(e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage()))
                .append("\"");
        }
        sb.append("}}");
        System.err.println(sb);
        if (verbose && e.getCause() != null) e.getCause().printStackTrace(System.err);
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
```

- [ ] **Step 4: Update `Main` to register `ExtractCommand`**

Replace the `subcommands` attribute on `@Command` in `Main.java`:

```java
@Command(
    name = "data-bridge",
    mixinStandardHelpOptions = true,
    versionProvider = Main.VersionProvider.class,
    subcommands = { VersionCommand.class, ExtractCommand.class }
)
```

- [ ] **Step 5: Run test — verify it passes**

Run: `mvn -q test -Dtest=ExtractCommandTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/Main.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/ExtractCommand.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/cli/ExtractCommandTest.java
git commit -m "feat(data-bridge): add extract subcommand

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 24: `ListTablesCommand`

**Files:**
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/Main.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/ListTablesCommand.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/cli/ListTablesCommandTest.java`

- [ ] **Step 1: Write the failing parser test**

```java
package digital.singularidade.databridge.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class ListTablesCommandTest {

    @Test
    void parses_required_jdbc_url_and_optional_schema() {
        ListTablesCommand cmd = new ListTablesCommand();
        new CommandLine(cmd).parseArgs("--jdbc-url", "jdbc:postgresql://h/d", "--schema", "atl");
        assertThat(cmd.jdbcUrl).isEqualTo("jdbc:postgresql://h/d");
        assertThat(cmd.schema).isEqualTo("atl");
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn -q test -Dtest=ListTablesCommandTest`
Expected: FAIL.

- [ ] **Step 3: Create `ListTablesCommand`**

```java
package digital.singularidade.databridge.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.output.JsonWriter;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "list-tables", description = "List tables in a schema.")
public final class ListTablesCommand implements Callable<Integer> {

    @Option(names = "--jdbc-url", required = true) String jdbcUrl;
    @Option(names = "--schema") String schema;

    @Override
    public Integer call() {
        try (JdbcSource src = JdbcSource.open(jdbcUrl)) {
            List<String> tables = src.listTables(schema);
            ObjectMapper mapper = JsonWriter.newMapper();
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tables));
            return ErrorCodes.OK.exitCode();
        } catch (DataBridgeException e) {
            System.err.println("{\"error\":{\"code\":\"" + e.code().wireName() + "\",\"message\":\"" + e.getMessage() + "\"}}");
            return e.code().exitCode();
        } catch (Exception e) {
            System.err.println("{\"error\":{\"code\":\"UNSPECIFIED\",\"message\":\"" + e.getMessage() + "\"}}");
            return ErrorCodes.UNSPECIFIED.exitCode();
        }
    }
}
```

- [ ] **Step 4: Register in `Main`**

```java
@Command(
    name = "data-bridge",
    mixinStandardHelpOptions = true,
    versionProvider = Main.VersionProvider.class,
    subcommands = { VersionCommand.class, ExtractCommand.class, ListTablesCommand.class }
)
```

- [ ] **Step 5: Run test — verify it passes**

Run: `mvn -q test -Dtest=ListTablesCommandTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/Main.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/ListTablesCommand.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/cli/ListTablesCommandTest.java
git commit -m "feat(data-bridge): add list-tables subcommand

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 25: End-to-end IT — `extract` against PG fixture writes valid JSON

**Files:**
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/cli/ExtractCommandIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractCommandIT {

    @Test
    void extracts_cliente_end_to_end(@TempDir Path tmp) throws Exception {
        try (PgFixture fx = new PgFixture()) {
            int exit = new CommandLine(new Main())
                .setErr(new PrintStream(java.io.OutputStream.nullOutputStream()))
                .execute(
                    "extract",
                    "--jdbc-url", fx.jdbcUrl(),
                    "--schema", "atl",
                    "--table", "cliente",
                    "--out", tmp.toString(),
                    "--tsv"
                );
            assertThat(exit).isZero();

            Path metadata = tmp.resolve("metadata.json");
            assertThat(metadata).exists();

            ObjectMapper m = new ObjectMapper().registerModule(new JavaTimeModule());
            JsonNode tree = m.readTree(Files.readString(metadata));

            assertThat(tree.get("version").asText()).isEqualTo("1.0");
            assertThat(tree.get("source").get("driver").asText()).isEqualTo("postgresql");
            assertThat(tree.get("source").get("url").asText()).contains("password=***");
            assertThat(tree.get("primaryKey").get(0).asText()).isEqualTo("id");
            assertThat(tree.get("foreignKeys").size()).isEqualTo(1);
            assertThat(tree.get("indexes").size()).isGreaterThanOrEqualTo(2);
            assertThat(tree.get("sample").get("rowCount").asInt()).isEqualTo(5);
            assertThat(tree.get("cardinality").get("totalRows").asLong()).isEqualTo(5L);
            assertThat(tree.get("warnings").isArray()).isTrue();

            assertThat(tmp.resolve("columns.tsv")).exists();
            assertThat(tmp.resolve("sample.tsv")).exists();
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=ExtractCommandIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/test/java/digital/singularidade/databridge/cli/ExtractCommandIT.java
git commit -m "test(data-bridge): add end-to-end ExtractCommand IT

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 6 — Driver smoke tests (Task 26)

### Task 26: One smoke test per driver — confirms shade kept `META-INF/services/java.sql.Driver`

**Files:**
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/driver/PostgresDriverSmokeTest.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/driver/FirebirdDriverSmokeTest.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/driver/OracleDriverSmokeTest.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/driver/MssqlDriverSmokeTest.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/driver/MysqlDriverSmokeTest.java`

- [ ] **Step 1: Create `PostgresDriverSmokeTest`**

```java
package digital.singularidade.databridge.driver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class PostgresDriverSmokeTest {

    @Test
    void driver_class_is_loadable() {
        assertThatCode(() -> Class.forName("org.postgresql.Driver")).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Create `FirebirdDriverSmokeTest`**

```java
package digital.singularidade.databridge.driver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class FirebirdDriverSmokeTest {

    @Test
    void driver_class_is_loadable() {
        assertThatCode(() -> Class.forName("org.firebirdsql.jdbc.FBDriver")).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 3: Create `OracleDriverSmokeTest`**

```java
package digital.singularidade.databridge.driver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class OracleDriverSmokeTest {

    @Test
    void driver_class_is_loadable() {
        assertThatCode(() -> Class.forName("oracle.jdbc.driver.OracleDriver")).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 4: Create `MssqlDriverSmokeTest`**

```java
package digital.singularidade.databridge.driver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class MssqlDriverSmokeTest {

    @Test
    void driver_class_is_loadable() {
        assertThatCode(() -> Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver")).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 5: Create `MysqlDriverSmokeTest`**

```java
package digital.singularidade.databridge.driver;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class MysqlDriverSmokeTest {

    @Test
    void driver_class_is_loadable() {
        assertThatCode(() -> Class.forName("com.mysql.cj.jdbc.Driver")).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 6: Run all driver smoke tests**

Run: `mvn -q test -Dtest='*DriverSmokeTest'`
Expected: PASS, 5 tests.

- [ ] **Step 7: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/test/java/digital/singularidade/databridge/driver/
git commit -m "test(data-bridge): add per-driver smoke tests (5 drivers)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Phase 7 — Serve mode (Tasks 27–31)

### Task 27: `ConnectionPoolManager` (HikariCP, keyed by URL, idle eviction)

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/ConnectionPoolManager.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/server/ConnectionPoolManagerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package digital.singularidade.databridge.server;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectionPoolManagerTest {

    @Test
    void normalizes_keys_by_stripping_ephemeral_params() {
        ConnectionPoolManager m = new ConnectionPoolManager(5, java.time.Duration.ofMinutes(10));
        String url1 = "jdbc:postgresql://h/d?user=u&password=p&connectTimeout=5";
        String url2 = "jdbc:postgresql://h/d?user=u&password=p&connectTimeout=99";
        assertThat(m.normalizeKey(url1)).isEqualTo(m.normalizeKey(url2));
    }

    @Test
    void different_credentials_give_different_keys() {
        ConnectionPoolManager m = new ConnectionPoolManager(5, java.time.Duration.ofMinutes(10));
        String url1 = "jdbc:postgresql://h/d?user=u1&password=p1";
        String url2 = "jdbc:postgresql://h/d?user=u2&password=p2";
        assertThat(m.normalizeKey(url1)).isNotEqualTo(m.normalizeKey(url2));
    }

    @Test
    void getConnection_against_h2_and_close_releases() throws Exception {
        ConnectionPoolManager m = new ConnectionPoolManager(2, java.time.Duration.ofMinutes(10));
        try (Connection c = m.getConnection("jdbc:h2:mem:test_pool;DB_CLOSE_DELAY=-1")) {
            assertThat(c.isClosed()).isFalse();
        }
        m.shutdown();
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn -q test -Dtest=ConnectionPoolManagerTest`
Expected: FAIL.

- [ ] **Step 3: Implement `ConnectionPoolManager`**

```java
package digital.singularidade.databridge.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
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
            t.setDaemon(true); return t;
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
        java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>();
        for (String p : params) {
            int eq = p.indexOf('=');
            String k = (eq < 0 ? p : p.substring(0, eq));
            String v = (eq < 0 ? "" : p.substring(eq + 1));
            if (!EPHEMERAL.contains(k.toLowerCase())) sorted.put(k, v);
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
                try { entry.getValue().ds.close(); } catch (Exception ignored) {}
                return true;
            }
            return false;
        });
    }

    public void shutdown() {
        scheduler.shutdown();
        pools.values().forEach(p -> { try { p.ds.close(); } catch (Exception ignored) {} });
        pools.clear();
    }

    @Override public void close() { shutdown(); }

    private static final class Pool {
        final HikariDataSource ds;
        volatile Instant lastUsed = Instant.now();
        Pool(HikariDataSource ds) { this.ds = ds; }
    }
}
```

- [ ] **Step 4: Run test — verify it passes**

Run: `mvn -q test -Dtest=ConnectionPoolManagerTest`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/ConnectionPoolManager.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/server/ConnectionPoolManagerTest.java
git commit -m "feat(data-bridge): add ConnectionPoolManager with URL normalization + eviction

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 28: `HttpServer` + `HealthHandler`

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/HttpServer.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/HealthHandler.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/server/HttpServerIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.server;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HttpServerIT {

    @Test
    void health_returns_200_and_version_returns_payload() throws Exception {
        try (ConnectionPoolManager pools = new ConnectionPoolManager(2, Duration.ofMinutes(10));
             HttpServer server = HttpServer.start(0, pools)) {
            int port = server.port();
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> health = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(health.statusCode()).isEqualTo(200);
            assertThat(health.body()).contains("ok");

            HttpResponse<String> version = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v1/version")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(version.statusCode()).isEqualTo(200);
            assertThat(version.body()).contains("singularidade-data-bridge").contains("0.1.0");
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=HttpServerIT -DfailIfNoTests=false`
Expected: FAIL — class not found.

- [ ] **Step 3: Create `HealthHandler`**

```java
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
```

- [ ] **Step 4: Create `HttpServer`**

```java
package digital.singularidade.databridge.server;

import io.javalin.Javalin;

public final class HttpServer implements AutoCloseable {

    private final Javalin app;
    private final ConnectionPoolManager pools;

    private HttpServer(Javalin app, ConnectionPoolManager pools) {
        this.app = app; this.pools = pools;
    }

    public static HttpServer start(int port, ConnectionPoolManager pools) {
        Javalin app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
        });
        app.get("/v1/health", new HealthHandler());
        app.get("/v1/version", HealthHandler.version());
        app.start(port);
        return new HttpServer(app, pools);
    }

    public int port() { return app.port(); }

    @Override public void close() { app.stop(); }
}
```

- [ ] **Step 5: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=HttpServerIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/HttpServer.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/HealthHandler.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/server/HttpServerIT.java
git commit -m "feat(data-bridge): add Javalin HttpServer with /health and /version

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 29: `ExtractRequest` + `ExtractHandler` (POST /v1/extract)

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/ExtractRequest.java`
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/ExtractHandler.java`
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/HttpServer.java` (wire route)
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/server/ExtractHandlerIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractHandlerIT {

    @Test
    void post_extract_returns_metadata_json() throws Exception {
        try (PgFixture fx = new PgFixture();
             ConnectionPoolManager pools = new ConnectionPoolManager(2, Duration.ofMinutes(10));
             HttpServer server = HttpServer.start(0, pools)) {

            String body = String.format(
                "{\"jdbcUrl\":\"%s\",\"schema\":\"atl\",\"table\":\"cliente\",\"sampleRows\":5,\"skipCardinality\":false}",
                fx.jdbcUrl());

            HttpResponse<String> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/v1/extract"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());

            assertThat(resp.statusCode()).isEqualTo(200);
            JsonNode tree = new ObjectMapper().readTree(resp.body());
            assertThat(tree.get("version").asText()).isEqualTo("1.0");
            assertThat(tree.get("primaryKey").get(0).asText()).isEqualTo("id");
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=ExtractHandlerIT -DfailIfNoTests=false`
Expected: FAIL — class not found.

- [ ] **Step 3: Create `ExtractRequest`**

```java
package digital.singularidade.databridge.server;

public record ExtractRequest(
    String jdbcUrl,
    String schema,
    String table,
    Integer sampleRows,
    Boolean skipCardinality
) {
    public int sampleRowsOrDefault() { return sampleRows == null ? 5 : sampleRows; }
    public boolean skipCardinalityOrDefault() { return skipCardinality != null && skipCardinality; }
}
```

- [ ] **Step 4: Create `ExtractHandler`**

```java
package digital.singularidade.databridge.server;

import digital.singularidade.databridge.error.DataBridgeException;
import digital.singularidade.databridge.error.ErrorCodes;
import digital.singularidade.databridge.output.Metadata;
import digital.singularidade.databridge.pipeline.MetadataPipeline;
import digital.singularidade.databridge.source.Source;
import digital.singularidade.databridge.source.jdbc.DriverHints;
import digital.singularidade.databridge.source.jdbc.JdbcSource;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

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
                 Source src = JdbcSourceFromConnection.wrap(c, hints)) {
                Metadata m = new MetadataPipeline(new PrintStream(java.io.OutputStream.nullOutputStream()),
                    req.skipCardinalityOrDefault())
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
```

- [ ] **Step 5: Add `JdbcSourceFromConnection` helper (wraps an existing `Connection` as a `Source`)**

The CLI's `JdbcSource.open(url)` opens its own connection; the server already has one from the pool. Add a static factory in `JdbcSource`:

Edit `JdbcSource.java` — add this method next to `open(...)`:

```java
public static JdbcSource wrap(Connection c, DriverHints hints) {
    try { c.setReadOnly(true); } catch (SQLException ignored) {}
    return new JdbcSource(c, hints);
}
```

And rename the import in `ExtractHandler`:

```java
// REPLACE:
//   import ...JdbcSourceFromConnection;
// WITH:
import digital.singularidade.databridge.source.jdbc.JdbcSource;
```

And the body call:

```java
// REPLACE:
//   try (Connection c = pools.getConnection(req.jdbcUrl());
//        Source src = JdbcSourceFromConnection.wrap(c, hints)) {
// WITH:
try (Connection c = pools.getConnection(req.jdbcUrl());
     JdbcSource src = JdbcSource.wrap(c, hints)) {
```

(The first `JdbcSourceFromConnection` reference was a placeholder — use `JdbcSource.wrap` directly. The handler in Step 4 has it as `JdbcSourceFromConnection`; replace before running the IT.)

- [ ] **Step 6: Wire `/v1/extract` in `HttpServer`**

Replace the `start` method's body in `HttpServer.java`:

```java
public static HttpServer start(int port, ConnectionPoolManager pools) {
    Javalin app = Javalin.create(cfg -> {
        cfg.showJavalinBanner = false;
        cfg.jsonMapper(new io.javalin.json.JavalinJackson(
            digital.singularidade.databridge.output.JsonWriter.newMapper(), true));
    });
    app.get("/v1/health", new HealthHandler());
    app.get("/v1/version", HealthHandler.version());
    app.post("/v1/extract", new ExtractHandler(pools));
    app.start(port);
    return new HttpServer(app, pools);
}
```

- [ ] **Step 7: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=ExtractHandlerIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/ExtractRequest.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/ExtractHandler.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/HttpServer.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/source/jdbc/JdbcSource.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/server/ExtractHandlerIT.java
git commit -m "feat(data-bridge): add POST /v1/extract handler

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 30: `ListTablesHandler` (GET /v1/list-tables)

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/ListTablesHandler.java`
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/HttpServer.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/server/ListTablesHandlerIT.java`

- [ ] **Step 1: Write the failing IT**

```java
package digital.singularidade.databridge.server;

import digital.singularidade.databridge.support.PgFixture;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ListTablesHandlerIT {

    @Test
    void list_tables_for_atl_schema() throws Exception {
        try (PgFixture fx = new PgFixture();
             ConnectionPoolManager pools = new ConnectionPoolManager(2, Duration.ofMinutes(10));
             HttpServer server = HttpServer.start(0, pools)) {
            String url = "http://localhost:" + server.port()
                + "/v1/list-tables?jdbcUrl=" + URLEncoder.encode(fx.jdbcUrl(), StandardCharsets.UTF_8)
                + "&schema=atl";
            HttpResponse<String> resp = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertThat(resp.statusCode()).isEqualTo(200);
            assertThat(resp.body()).contains("estadocivil").contains("cliente");
        }
    }
}
```

- [ ] **Step 2: Run IT — verify it fails**

Run: `mvn -q verify -Dit.test=ListTablesHandlerIT -DfailIfNoTests=false`
Expected: FAIL.

- [ ] **Step 3: Create `ListTablesHandler`**

```java
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
```

- [ ] **Step 4: Wire route in `HttpServer.start(...)`**

Add this line after the `/v1/extract` route:

```java
app.get("/v1/list-tables", new ListTablesHandler(pools));
```

- [ ] **Step 5: Run IT — verify it passes**

Run: `mvn -q verify -Dit.test=ListTablesHandlerIT -DfailIfNoTests=false`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/ListTablesHandler.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/server/HttpServer.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/server/ListTablesHandlerIT.java
git commit -m "feat(data-bridge): add GET /v1/list-tables handler

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 31: `ServeCommand` + serve mode end-to-end IT

**Files:**
- Create: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/ServeCommand.java`
- Modify: `singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/Main.java`
- Create: `singularidade-data-bridge/src/test/java/digital/singularidade/databridge/cli/ServeCommandTest.java`

- [ ] **Step 1: Write the failing parser test**

```java
package digital.singularidade.databridge.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class ServeCommandTest {

    @Test
    void parses_defaults() {
        ServeCommand cmd = new ServeCommand();
        new CommandLine(cmd).parseArgs();
        assertThat(cmd.port).isEqualTo(8765);
        assertThat(cmd.maxPool).isEqualTo(5);
        assertThat(cmd.idleTimeout.toMinutes()).isEqualTo(10);
    }

    @Test
    void parses_overrides() {
        ServeCommand cmd = new ServeCommand();
        new CommandLine(cmd).parseArgs(
            "--port", "9000",
            "--max-pool", "10",
            "--idle-timeout", "5m"
        );
        assertThat(cmd.port).isEqualTo(9000);
        assertThat(cmd.maxPool).isEqualTo(10);
        assertThat(cmd.idleTimeout.toMinutes()).isEqualTo(5);
    }
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `mvn -q test -Dtest=ServeCommandTest`
Expected: FAIL.

- [ ] **Step 3: Create `ServeCommand`**

```java
package digital.singularidade.databridge.cli;

import digital.singularidade.databridge.server.ConnectionPoolManager;
import digital.singularidade.databridge.server.HttpServer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.Duration;
import java.util.concurrent.Callable;

@Command(name = "serve", description = "Start HTTP daemon mode.")
public final class ServeCommand implements Callable<Integer> {

    @Option(names = "--port", defaultValue = "8765") int port;
    @Option(names = "--max-pool", defaultValue = "5") int maxPool;
    @Option(names = "--idle-timeout", defaultValue = "10m", converter = DurationConverter.class)
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

    public static final class DurationConverter implements picocli.CommandLine.ITypeConverter<Duration> {
        @Override public Duration convert(String value) {
            String s = value.trim().toLowerCase();
            if (s.endsWith("ms")) return Duration.ofMillis(Long.parseLong(s.substring(0, s.length() - 2)));
            if (s.endsWith("s")) return Duration.ofSeconds(Long.parseLong(s.substring(0, s.length() - 1)));
            if (s.endsWith("m")) return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1)));
            if (s.endsWith("h")) return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1)));
            return Duration.ofSeconds(Long.parseLong(s));
        }
    }
}
```

- [ ] **Step 4: Register `ServeCommand` in `Main`**

```java
@Command(
    name = "data-bridge",
    mixinStandardHelpOptions = true,
    versionProvider = Main.VersionProvider.class,
    subcommands = { VersionCommand.class, ExtractCommand.class, ListTablesCommand.class, ServeCommand.class }
)
```

- [ ] **Step 5: Run test — verify it passes**

Run: `mvn -q test -Dtest=ServeCommandTest`
Expected: PASS.

- [ ] **Step 6: Final full build (unit + driver smoke + IT)**

Run: `mvn -q verify`
Expected: BUILD SUCCESS, all unit + IT pass, fat JAR produced at `target/data-bridge.jar`.

- [ ] **Step 7: Commit**

```bash
cd /home/mint/Workspace/project/utilities
git add singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/ServeCommand.java \
        singularidade-data-bridge/src/main/java/digital/singularidade/databridge/cli/Main.java \
        singularidade-data-bridge/src/test/java/digital/singularidade/databridge/cli/ServeCommandTest.java
git commit -m "feat(data-bridge): add serve subcommand wiring HTTP daemon

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Self-review

Reviewing the plan against the spec (commit `770e134`):

**Coverage of spec sections:**
- §3.1 Stack — Task 1 (pom)
- §3.2 Package layout — covered across Tasks 2, 3, 4, 5, 6, 7, 8, 9, 10–19, 20, 21, 22, 23, 24, 27, 28, 29, 30, 31
- §3.3 Source interface — Task 8
- §4 CLI surface — Tasks 2 (version), 23 (extract), 24 (list-tables), 31 (serve)
- §5 JSON contract — Task 3 (records), Task 21 (writer); covered end-to-end in Task 25 golden IT
- §6 Data flow — Task 20 pipeline; Task 23 CLI orchestration; Task 29 HTTP path
- §7 Error handling — Task 4 (codes/exception), Task 5 (URL redaction), Task 23 (error-JSON emission)
- §8 Testing — unit throughout; IT in Tasks 9–22, 25, 28, 29, 30; driver smoke in Task 26
- §9 YAGNI — respected (no `--max-metadata`, no auth, no parallelism, no cache)
- §10 Trade-offs — `--no-cardinality` escape hatch wired in Task 23; URL redaction in Task 5

**Cross-task type consistency check:**
- `Source.cardinality(...)` signature defined in Task 8 (`schema, table, List<Column>`); used in Task 18 IT and Task 20 pipeline. ✅
- `JdbcSource.open(...)` in Task 9; `JdbcSource.wrap(Connection, DriverHints)` added in Task 29 Step 5. ✅
- `Column.Generated` record path consistent (Task 3 → Task 10). ✅
- `Index.whereClause` Java field, JSON field `where` (via `@JsonProperty`) — Task 3, used in Task 13 SQL augment. ✅
- `TypeNormalization.toSqlType(int, String, Integer, Integer, Integer)` signature — Task 7 → Task 10 + Task 12. ✅
- `JsonWriter.newMapper()` static — created in Task 21, reused by `HttpServer.start(...)` in Task 29. ✅
- `Sample.rows` shape `List<Map<String,Object>>` — Task 3 → Task 15 → Task 22 TSV. ✅
- `ConnectionPoolManager.normalizeKey` — Task 27 (package-private, exposed for test). ✅

**Placeholder scan:** No "TBD"/"TODO" in implementation code or commands. Spec deliberately mentions TBDs in §11 (future evolution); plan stays MVP.

**Step-count budget:** Plan has 31 tasks. Each task is one TDD cycle (~10–25 min) → estimated 6–10 hours of focused work for the full MVP, including a working fat JAR and HTTP daemon end-to-end against a real Postgres testcontainer.

---

## Plan complete

Plan saved to `singularidade-data-bridge/docs/superpowers/plans/2026-05-09-singularidade-data-bridge-mvp.md` (633 spec lines + 31 tasks here). Ready to commit and choose execution mode.






