# singularidade-data-bridge — Design Spec

**Status:** Proposed
**Author:** Brainstorming session, 2026-05-09
**Owners:** singularidade.digital
**Repo:** `singularidade-digital-dev/utilities`, subpath `singularidade-data-bridge/`
**Group:** `digital.singularidade` · **Artifact:** `singularidade-data-bridge` · **Version:** `0.1.0`

---

## 1. Why this exists

Skills in the `mcp-tool-from-table-<connector>` family (current member: `mcp-tool-from-table-orgen-ai`; planned: `-sgh-ai`, etc.) need to introspect a relational table and emit metadata + samples to drive code generation of MCP tools. The first iteration used per-connector MCP servers (e.g., `mcp__postgres-orgen-master-remoto__*`) for introspection. That approach hit a cascade of environmental issues — TLS chain rejection, sandbox-blocked TCP, npm-trust concerns — and required opening sandbox holes to function.

This spec proposes replacing the per-connector MCP introspection with a single uniform JDBC tool that:

- Lives in the team's existing stack (Java 21 + Maven, drivers already vetted in `integras-digital`).
- Speaks JDBC for any RDBMS (Postgres now; Firebird/Oracle/SQLServer/MySQL ready).
- Emits a stable, versioned JSON document consumed by skills.
- Runs as one-shot CLI **and** optional local HTTP daemon (same pipeline behind both).
- Generalizes beyond JDBC later via a `Source` interface (REST/GraphQL/file in future phases).

The MVP scope is to unblock skill-eval iteration 2 of `mcp-tool-from-table-orgen-ai` with maximum metadata richness, while not painting the architecture into a JDBC-only corner.

---

## 2. Decision log (from brainstorming)

| # | Question | Decision |
|---|---|---|
| 1 | Output format | **JSON canonical + TSV human-friendly opt-in** (`--tsv`) |
| 2 | CLI shape | Single command `extract` + helpers `list-tables`, `version` (+ `serve` per #8) |
| 3 | Filter for sample/cardinality | **No filter in MVP.** Caller assumes scope responsibility (sample/cardinality reflect entire table) |
| 4 | FK following depth | **1-deep, schema-only** of referenced tables (column names + types) |
| 5 | Cardinality coverage | **Always all columns** (consciously accepting time cost on huge tables; see §10 known trade-offs) |
| 6 | Connection input | **Full JDBC URL only**, driver auto-detected from prefix |
| 7 | Test strategy | **Tiered:** unit + Postgres Testcontainer + per-driver smoke (no Firebird/Oracle/MSSQL/MySQL Testcontainer in MVP) |
| 8 | Runtime model | **Hybrid:** one-shot CLI (default) + optional `serve` mode (HTTP daemon with HikariCP pool) |
| 9 | Internal decomposition | **Approach A — Layered:** `Extractor`s + `Source`s separately. Designed for future non-JDBC sources. |
| 10 | Java target / build | **Java 21 + Maven**, fat JAR via maven-shade-plugin. No Spring (overkill for CLI). |
| 11 | Driver matrix | **Postgres + Firebird (Jaybird 6.0.5) + Oracle + SQLServer + MySQL** included in fat JAR |

---

## 3. Architecture

### 3.1 Stack

- **Language:** Java 21
- **Build:** Maven, fat JAR via `maven-shade-plugin`
- **CLI parsing:** picocli
- **JSON:** Jackson (databind + jsr310)
- **HTTP (serve mode):** Javalin
- **Connection pool (serve mode):** HikariCP
- **JDBC drivers (runtime scope, shaded):**
  - `org.postgresql:postgresql`
  - `org.firebirdsql.jdbc:jaybird:6.0.5`
  - `com.oracle.database.jdbc:ojdbc11`
  - `com.microsoft.sqlserver:mssql-jdbc`
  - `com.mysql:mysql-connector-j`
- **Test:** JUnit 5, AssertJ, Testcontainers (Postgres only in MVP), H2 (in-memory unit fixtures)

### 3.2 Package layout

```
src/main/java/digital/singularidade/databridge/
├─ cli/
│   ├─ Main.java                     ← @Command(name="data-bridge") root
│   ├─ ExtractCommand.java
│   ├─ ListTablesCommand.java
│   ├─ ServeCommand.java
│   └─ VersionCommand.java
├─ source/
│   ├─ Source.java                   ← interface (extension point for REST/GraphQL/file in future)
│   └─ jdbc/
│       ├─ JdbcSource.java           ← single JDBC impl covers 5 drivers
│       └─ DriverHints.java          ← enum: PG | FIREBIRD | ORACLE | MSSQL | MYSQL — per-driver quirks
├─ pipeline/
│   ├─ MetadataPipeline.java         ← orchestrator
│   ├─ TableInfoExtractor.java
│   ├─ SchemaExtractor.java
│   ├─ PrimaryKeyExtractor.java
│   ├─ ForeignKeysExtractor.java     ← + 1-deep schema for ref tables
│   ├─ IndexesExtractor.java
│   ├─ UniqueConstraintsExtractor.java
│   ├─ CheckConstraintsExtractor.java
│   ├─ SampleExtractor.java
│   ├─ ColumnStatsExtractor.java     ← driver-aware (PG: pg_stats; null elsewhere in MVP)
│   ├─ PartitioningExtractor.java    ← PG-only in MVP
│   └─ CardinalityExtractor.java     ← runs last (most expensive)
├─ output/
│   ├─ Metadata.java                 ← root record (the JSON contract)
│   ├─ JsonWriter.java               ← Jackson, pretty-printed, atomic write
│   └─ TsvWriter.java                ← opt-in via --tsv
├─ server/
│   ├─ HttpServer.java               ← Javalin bootstrap
│   ├─ ExtractHandler.java
│   ├─ ListTablesHandler.java
│   ├─ HealthHandler.java
│   └─ ConnectionPoolManager.java    ← HikariCP keyed by normalized jdbcUrl
└─ error/
    ├─ DataBridgeException.java
    └─ ErrorCodes.java               ← exit codes + HTTP status mapping
```

### 3.3 Source interface (extension point)

```java
public interface Source extends AutoCloseable {
  String type();                                            // "jdbc"|"rest"|"graphql"|"file"
  TableInfo describeTable(String schema, String table);
  List<Column> columns(String schema, String table);
  List<String> primaryKey(String schema, String table);
  List<ForeignKey> foreignKeys(String schema, String table);
  List<Index> indexes(String schema, String table);
  List<UniqueConstraint> uniqueConstraints(String schema, String table);
  List<CheckConstraint> checkConstraints(String schema, String table);
  List<Map<String,Object>> sample(String schema, String table, int limit);
  List<ColumnStats> columnStats(String schema, String table);   // optional → empty list
  Partitioning partitioning(String schema, String table);       // null if N/A
  long countRows(String schema, String table);
  long countDistinct(String schema, String table, String column);
  List<String> listTables(String schema);
}
```

`JdbcSource` is the only impl in MVP. Each method dispatches via `DriverHints` for driver-specific details (e.g., `pg_stats` query is PG-only; identity columns differ between PG and Oracle).

---

## 4. CLI surface

### 4.1 Subcommands

```
data-bridge extract \
  --jdbc-url "jdbc:postgresql://host/db?user=u&password=p&sslmode=require" \
  --schema atl \
  --table cliente \
  --out /tmp/cliente/ \
  [--sample-rows 5] \
  [--tsv] \
  [--no-cardinality] \
  [-q|--quiet] [-v|--verbose]

data-bridge list-tables \
  --jdbc-url "..." \
  [--schema atl]

data-bridge serve \
  [--port 8765] \
  [--max-pool 5] \
  [--idle-timeout 10m]

data-bridge version
```

### 4.2 Flag semantics

- `--jdbc-url` is the universal connection input. Driver detected from prefix.
- `--schema` is optional: required for sources that have schemas (PG, Oracle, MSSQL); ignored for those that don't (Firebird, MySQL — single-schema). `JdbcSource` validates per-driver and rejects ambiguity.
- `--out` creates the directory if missing.
- `--sample-rows` defaults to 5.
- `--tsv` produces additional human-friendly `*.tsv` files alongside `metadata.json`.
- `--no-cardinality` skips the slowest extractor (escape hatch for huge tables).
- `-q` suppresses progress messages on stderr.
- `-v` prints SQL executed and full stack traces on error.

### 4.3 Outputs in `--out=/tmp/cliente/`

```
metadata.json                                      ← canonical, the contract
columns.tsv  fks.tsv  sample.tsv  cardinality.tsv  ← only if --tsv
indexes.tsv  unique-constraints.tsv  check-constraints.tsv  ← only if --tsv
```

---

## 5. JSON output contract (`metadata.json`)

This is the **most important section** of the spec. It is the contract between data-bridge and any consumer (skills, future tooling). Consumers must tolerate additional fields (`additionalProperties: true`); producers must not remove/rename fields without bumping the major version (`"version": "2.0"`).

### 5.1 Top-level shape

```json
{
  "$schema": "https://singularidade.digital/data-bridge/metadata.v1.json",
  "version": "1.0",
  "generatedAt": "2026-05-09T15:42:11Z",
  "generator": { "name": "singularidade-data-bridge", "version": "0.1.0" },
  "source": {
    "type": "jdbc",
    "driver": "postgresql",
    "url": "jdbc:postgresql://orgen-producao.../orgen?password=***&sslmode=require",
    "schema": "atl",
    "table": "cliente"
  },
  "tableInfo":         { ... },
  "columns":           [ ... ],
  "primaryKey":        ["id"],
  "foreignKeys":       [ ... ],
  "indexes":           [ ... ],
  "uniqueConstraints": [ ... ],
  "checkConstraints":  [ ... ],
  "sample":            { "rowCount": 5, "rows": [ ... ] },
  "columnStats":       [ ... ],
  "cardinality":       { "totalRows": 18432, "perColumn": [ ... ] },
  "partitioning":      { ... },
  "warnings":          [ ... ]
}
```

### 5.2 Field schemas

#### `source`

```json
{
  "type": "jdbc",                       // "jdbc"|"rest"|"graphql"|"file" (future)
  "driver": "postgresql",               // postgresql|firebird|oracle|mssql|mysql
  "url": "jdbc:...?password=***&...",   // password-redacted (see §7.4)
  "schema": "atl",                      // null if source has no schema concept
  "table": "cliente"
}
```

#### `tableInfo`

```json
{
  "type": "TABLE",                      // TABLE|VIEW|MATERIALIZED_VIEW|PARTITIONED_TABLE
  "comment": "Cadastro de pacientes",
  "owner": "orgen_app",
  "approximateRowCount": 18432,         // pg_class.reltuples (PG); RDB$RELATIONS for FB; null if driver doesn't expose
  "viewDefinition": null                // SQL of view if type is VIEW/MVIEW
}
```

#### `columns[i]`

```json
{
  "name": "fk_estadocivil_id",
  "ordinalPosition": 7,
  "sqlType": "bigint",                  // normalized name
  "jdbcType": -5,                        // raw java.sql.Types
  "nullable": true,
  "primaryKey": false,
  "characterMaxLength": null,
  "numericPrecision": 19,
  "numericScale": 0,
  "default": null,
  "comment": null,
  "generated": {
    "isIdentity": false,
    "isComputed": false,
    "generationExpression": null
  },
  "sequence": null,                     // {name, schema} if column is autoinc-backed
  "collation": null
}
```

#### `foreignKeys[i]`

```json
{
  "constraintName": "fk_cliente_estadocivil",
  "fkColumns": ["fk_estadocivil_id"],
  "refSchema": "atl",
  "refTable": "estadocivil",
  "refColumns": ["id"],
  "onUpdate": "NO ACTION",
  "onDelete": "NO ACTION",
  "refTableColumns": [
    { "name": "id", "sqlType": "bigint", "nullable": false },
    { "name": "mnemonico", "sqlType": "varchar(20)", "nullable": true },
    { "name": "nome", "sqlType": "varchar(100)", "nullable": true },
    { "name": "descricao", "sqlType": "varchar(255)", "nullable": true }
  ]
}
```

#### `indexes[i]`

```json
{
  "name": "idx_cliente_cpf",
  "columns": ["cpf"],
  "ordinalAsc": [true],
  "unique": true,
  "primary": false,
  "method": "btree",                    // btree|hash|gin|gist|brin (PG); null on drivers that don't expose
  "where": null                          // partial index predicate (PG); null elsewhere
}
```

#### `uniqueConstraints[i]`

```json
{ "name": "uk_cliente_cpf", "columns": ["cpf"] }
```

#### `checkConstraints[i]`

```json
{ "name": "chk_cliente_sexo", "definition": "sexo IN ('M','F','O')" }
```

#### `sample`

```json
{
  "rowCount": 5,
  "rows": [
    {
      "id": 1,
      "nome": "FULANO DA SILVA",
      "cpf": "123.456.789-00",
      "fk_estadocivil_id": 3,
      "datanascimento": "1985-04-12",
      "foto": { "_blob": true, "size": 12345, "preview": "iVBORw0KGgo..." }
    }
  ]
}
```

- Rows are **objects keyed by column name**, not arrays. (Trade-off: ~30% larger JSON; gain: `jq '.sample.rows[0].nome'` works without index lookups.)
- BLOB/binary columns are normalized to `{_blob, size, preview}` where `preview` is base64 of the first 64 bytes.
- `null` is preserved as JSON null. Absent column key is **never** emitted (always present, possibly null).

#### `columnStats[i]` (optional, driver-aware)

```json
{
  "name": "fk_estadocivil_id",
  "nDistinctEstimate": 7,
  "nullFraction": 0.0006,
  "mostCommonValues": ["1", "2", "3"],
  "mostCommonFrequencies": [0.42, 0.31, 0.18],
  "correlation": 0.98
}
```

Populated from `pg_stats` for Postgres. Empty array for drivers that don't expose this in MVP.

#### `cardinality`

```json
{
  "totalRows": 18432,
  "perColumn": [
    { "name": "id", "distinctCount": 18432, "nullCount": 0 },
    { "name": "fk_estadocivil_id", "distinctCount": 7, "nullCount": 12 }
  ]
}
```

`totalRows` is exact `COUNT(*)`. Each `distinctCount` is exact `COUNT(DISTINCT col)`. Both are expensive on huge tables — see §10.

#### `partitioning`

```json
{
  "isPartitioned": false,
  "strategy": null,                     // RANGE | LIST | HASH
  "partitionKey": [],
  "parent": null,                        // {schema, table} if this is a partition
  "children": []                         // [{schema, table}, ...] if this is the parent
}
```

PG-aware in MVP. `null` for drivers without partitioning concept.

#### `warnings[]`

Free-form strings. Examples:
- `"columnStats not available for driver 'firebird' in MVP"`
- `"Cardinality of column 'foto' skipped (BLOB type)"`
- `"approximateRowCount unavailable; pg_class.reltuples returned 0"`

---

## 6. Data flow

### 6.1 CLI extract path

```
1. picocli parse args                                    [exit 2 if invalid]
2. DriverHints.fromUrl(jdbcUrl) → driver enum             [exit 64 if unknown prefix]
3. JdbcSource.open(url) → java.sql.Connection             [exit 10 on connect fail]
4. MetadataPipeline.run(schema, table):
     ├─ TableInfoExtractor
     ├─ SchemaExtractor                  (DatabaseMetaData.getColumns + driver augments)
     ├─ PrimaryKeyExtractor              (DatabaseMetaData.getPrimaryKeys)
     ├─ ForeignKeysExtractor             (getImportedKeys + 1-deep getColumns per ref)
     ├─ IndexesExtractor                 (getIndexInfo + driver-specific augment for method/where; PG: pg_indexes)
     ├─ UniqueConstraintsExtractor       (information_schema.table_constraints)
     ├─ CheckConstraintsExtractor        (information_schema.check_constraints)
     ├─ SampleExtractor                  (SELECT * FROM t LIMIT N; BLOB normalization)
     ├─ ColumnStatsExtractor             (driver-aware; PG: pg_stats; null elsewhere)
     ├─ PartitioningExtractor            (PG: pg_partitioned_table; null elsewhere)
     └─ CardinalityExtractor             (COUNT(*); COUNT(DISTINCT col) per column)
5. JsonWriter.write(metadata, outDir/metadata.json)       [atomic via tmp + rename]
6. TsvWriter.writeAll(metadata, outDir) if --tsv          [exit 14 on IO fail]
7. JdbcSource.close (in finally)
8. exit 0
```

**Decisions:**
- **Sequential, single connection.** No parallelization in MVP.
- **Cardinality runs last** (most expensive). If cheaper extractors fail, fail fast before paying the cardinality cost.
- **Atomic output:** write to `outDir/.metadata.json.partial`, rename to `metadata.json` only on full success. No partial files visible.
- **Progress on stderr:** `[1/11] table info... ok (12ms)`. Suppressible with `-q`.

### 6.2 Serve mode path

```
ServeCommand → Javalin.start(port)
  ├─ POST /v1/extract       body: ExtractRequest      → MetadataPipeline.run()
  ├─ GET  /v1/list-tables   query: jdbcUrl, schema    → ListTablesPipeline.run()
  ├─ GET  /v1/health        → 200 if reachable
  └─ GET  /v1/version       → {name, version}

ConnectionPoolManager:
  - ConcurrentHashMap<jdbcUrlNormalized, HikariDataSource>
  - On request: get-or-create pool for the URL (cap = --max-pool)
  - Background scheduler: pools idle > --idle-timeout get .close()'d and removed
  - JVM shutdown hook closes all pools
```

The HTTP handlers delegate to the **same `MetadataPipeline`** the CLI uses — no duplicated logic.

`jdbcUrlNormalized` strips ephemeral query params for pool keying — specifically `connectTimeout`, `socketTimeout`, `applicationName`. Credentials and SSL params remain part of the key (different creds → different pool; different SSL config → different pool). Implementation: parse query string, drop the listed keys, sort remaining keys alphabetically, reassemble.

---

## 7. Error handling

### 7.1 Exit codes (CLI)

| Code | Meaning |
|---|---|
| 0 | Success |
| 1 | Unspecified / unclassified bug |
| 2 | Invalid args (picocli rejection) |
| 10 | Connection failed (URL, driver, auth, network, TLS) |
| 11 | Table not found |
| 12 | Schema not found |
| 13 | Query failed mid-pipeline (timeout, permission, syntax) |
| 14 | Output write failed (disk full, permission) |
| 64 | Unsupported (driver-specific feature unavailable in MVP) |

### 7.2 HTTP status codes (serve mode)

| Status | Meaning |
|---|---|
| 200 | Success |
| 400 | Invalid request body / missing required field |
| 404 | Table or schema not found (maps to exit 11/12) |
| 500 | Internal data-bridge error |
| 502 | Connection or query failed downstream (maps to exit 10/13) |

### 7.3 Error JSON body (CLI stderr and HTTP body)

```json
{
  "error": {
    "code": "TABLE_NOT_FOUND",
    "message": "Table 'atl.cliente' not found in schema 'atl'",
    "hint": "Run `data-bridge list-tables --jdbc-url=... --schema=atl` to discover available tables",
    "cause": "<exception class + message; only with --verbose or HTTP X-Verbose: true>"
  }
}
```

### 7.4 Logging and redaction

- Default: 1 progress line per pipeline step on stderr.
- `-q/--quiet`: errors only.
- `-v/--verbose`: SQL executed + full stack traces.
- Stdout is **clean** — only emits the error JSON if extraction fails. Progress always on stderr.
- **URL redaction:** before any logging or JSON output, query params named `password` or `passwd` (case-insensitive) are replaced with `***`. Applied to:
  - `metadata.json` `source.url` field
  - All log lines mentioning the URL
  - Error messages

### 7.5 Sample data redaction

**Sample data is NOT redacted.** It comes raw from the database. PII (CPF, names, emails, etc.) appears unmasked in `metadata.json`. This is documented prominently in the README. `--out` should always point to an ephemeral directory (`/tmp/...`), never to a tracked location.

---

## 8. Testing strategy

### 8.1 Tiers

| Tier | Files | Runs via | Requires |
|---|---|---|---|
| Unit | `*Test.java` | `mvn test` (Surefire) | nothing |
| Integration | `*IT.java` | `mvn verify` (Failsafe) | Docker (Testcontainers) |
| Driver smoke | `*DriverSmokeTest.java` | `mvn test` | nothing (just `Class.forName`) |

### 8.2 Coverage targets

**Unit:**
- CLI parser (picocli command + flag combinations)
- `DriverHints.fromUrl` (URL prefix → driver enum)
- URL redaction (password=foo → password=***)
- `JsonWriter` golden file (record → JSON)
- `TsvWriter` (sample data with embedded tabs/newlines correctly escaped)
- BLOB normalization (`bytea` → `{_blob, size, preview}`)
- `Source` interface contract verification using H2 in-memory fake
- Error code mapping (exception → exit code)
- Type normalization (`java.sql.Types` → `sqlType` string)

**Integration (Postgres Testcontainer):**
- Each Extractor against a fixture schema covering all features (FKs, indexes, check constraints, sequences, identity, partitioned tables, views, comments)
- End-to-end `MetadataPipelineIT` produces JSON matching golden file (with `generatedAt` masked)
- `list-tables` returns expected set
- `pg_stats` extraction for `columnStats`
- Sample with BLOB column
- Cardinality on small fixture (~100 rows)
- Server `HttpServerIT`: `/v1/health` → 200; `/v1/extract` end-to-end
- `ConnectionPoolManager` mock-clock tests for idle eviction

**Driver smoke (one per driver):**
```java
@Test void driver_class_is_loadable() {
    assertDoesNotThrow(() -> Class.forName("org.postgresql.Driver"));
}
```
Confirms `maven-shade-plugin` correctly merged `META-INF/services/java.sql.Driver`. ~10ms each.

### 8.3 Test fixtures

`src/test/resources/fixtures/`:

```
atl-schema.sql          ← CREATE TABLE + indexes + constraints + comments + view + partition
atl-data.sql            ← Realistic INSERTs (faker-style PII for cardinality variety)
pg-stats-trigger.sql    ← VACUUM ANALYZE to populate pg_stats
golden/cliente.json     ← Expected JSON output
```

### 8.4 What's NOT in MVP testing

- ❌ Firebird/Oracle/MSSQL/MySQL Testcontainers (only driver-load smoke). Add when the SGH-AI sister skill is built.
- ❌ Mutation testing.
- ❌ Performance benchmarks (add when we have real-table baselines).

---

## 9. Out of scope (YAGNI)

These were considered and explicitly deferred. Bring them back when there's concrete demand:

- **Filter clause for sample/cardinality.** Skill must scope before calling, or accept whole-table results. (Decision #3.)
- **Subcommands per concept** (e.g., `data-bridge schema`, `data-bridge fks`). The `extract` command does it all; subcommands proliferate without value.
- **Multi-module Maven (lib + cli).** Single-module fat JAR. If integras-digital wants to embed the lib later, refactor then.
- **Spring Boot.** Plain `Main` + picocli. Spring overhead is ~10MB and ~3s startup for a CLI.
- **GraalVM native image.** Reflection-heavy with JDBC drivers; toolchain complexity not worth MVP gain.
- **Authentication on serve mode.** Localhost-only binding by default; arbitrary loopback consumers trusted. No user/role/token system.
- **TLS termination on serve mode.** Plain HTTP. Caller and server are colocated; no ciphertext required.
- **Parallel pipeline execution.** Single connection, sequential queries. Add parallelism with pool-aware execution if profiling shows wins.
- **Caching `metadata.json` between calls.** Stateless. Daemon mode amortizes connection cost; that's enough.
- **Schema diff / drift detection.** Single-shot snapshot only.
- **REST/GraphQL/file `Source` impls.** Designed for; not built.
- **Mutation operations.** Read-only by design. CLI never executes anything beyond SELECTs and metadata queries.

---

## 10. Known trade-offs and risks

### 10.1 Cardinality on huge tables

`COUNT(DISTINCT col)` on a 50M-row table takes 30s–2min per column. With 30 columns, a single `extract` of `atl.atendimento` could exceed 30 minutes. This was consciously accepted (decision #5).

**Mitigations available if it bites:**
- `--no-cardinality` skips entirely.
- Future: `--cardinality-cols=a,b,c` to scope; auto-skip BLOB/CLOB/`varchar(>500)`.
- Future: prefer `pg_stats.n_distinct` over exact count when `--cardinality=approximate`.

### 10.2 No tenant scoping

Sample and cardinality run against the entire table. For ORGEN tables shared across tenants in dev, sample may return rows from a non-target tenant; cardinality reflects all tenants combined.

**Caller's responsibility** to either:
- Point at a single-tenant DB.
- Accept whole-table semantics (which is fine for inference of value patterns and roles).
- Wait for a future `--where` flag (post-MVP).

### 10.3 Sample data contains PII

`metadata.json` may contain unmasked CPFs, names, emails. README documents this; `--out` should be ephemeral.

### 10.4 Daemon lifecycle in serve mode

Long-running daemon means:
- Connection pools hold credentials in memory across requests.
- Orphaned daemon (parent shell exits, daemon survives) requires manual `kill`.
- Port 8765 conflicts if two users share a host.

**Mitigations:**
- Default `--idle-timeout=10m` shrinks idle pools.
- JVM shutdown hook closes pools cleanly on `kill -TERM`.
- README documents `data-bridge serve` lifecycle.

### 10.5 JSON contract evolution

The contract in §5 is `version: 1.0`. Additive changes (new fields) bump to `1.1`; removals/renames bump to `2.0`. Producers and consumers must both tolerate `additionalProperties`.

---

## 11. Future evolution (non-binding sketch)

- **`mcp-tool-from-table-orgen-ai` skill rewrite (Etapa 1):** replace MCP tool calls with `data-bridge extract`. Adds one prerequisite (data-bridge installed) but eliminates MCP dependency.
- **`mcp-tool-from-table-sgh-ai`** (sister skill): uses same `data-bridge extract` against a Firebird URL. No new code in data-bridge; only needs the smoke test promoted to integration when SGH-AI is real.
- **Tenant scoping**: `--where` flag (raw, parameterized, or DSL — TBD).
- **REST `Source` impl**: for tabular API endpoints (e.g., introspecting a third-party API that returns array-of-objects).
- **File `Source` impl**: CSV/Parquet introspection.
- **`data-bridge diff`**: snapshot-vs-snapshot to detect drift between environments.
- **Vaadin admin preview**: integras-digital admin UI calling `data-bridge serve` to preview tables before activating modules.

---

## 12. References

- Brainstorming session transcript (this conversation, 2026-05-09)
- `singularidade-commons-java` (`pom.xml`) — group/version conventions, Apache 2.0 license
- `integras-digital/pom.xml` — driver versions (Postgres, Jaybird 6.0.5, Testcontainers)
- `mcp-tool-from-table-orgen-ai/SKILL.md` — original MCP-based introspection (replaced by this tool's Etapa 1)
- `~/.claude/projects/.../memory/skill_eval_results.md` — Iteration 2 blocked by MCP/sandbox issues that motivated this project
