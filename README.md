# singularidade-data-bridge

[![CI](https://github.com/singularidade-digital-dev/singularidade-data-bridge/actions/workflows/ci.yml/badge.svg)](https://github.com/singularidade-digital-dev/singularidade-data-bridge/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/singularidade-digital-dev/singularidade-data-bridge?label=release)](https://github.com/singularidade-digital-dev/singularidade-data-bridge/releases/latest)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-21-orange.svg)](https://adoptium.net/temurin/releases/?version=21)

A uniform JDBC introspection tool. Point it at any supported database, name a table, and get back a single, versioned JSON document describing the table's schema, constraints, indexes, sample rows, column statistics, partitioning, and cardinality — plus optional human-friendly TSV dumps.

Runs as a one-shot **CLI** for ad-hoc extraction, or as a long-running **HTTP daemon** with HikariCP-pooled connections for repeated calls from local tooling.

---

## Why this exists

LLMs and code-generation agents increasingly need to **understand or query** relational databases — to plan ETL jobs, to generate MCP tools or migrations, to validate a hypothesis against real data, to draft a query before sending it for review. Today, that's normally done via per-database **MCP servers** (`mcp-server-postgres`, `mcp-server-mysql`, vendor-specific Firebird/Oracle servers, etc.). It works, but at a cost:

| Pain | Why it bites |
|---|---|
| **Heterogeneous APIs** | Each MCP server exposes a different tool surface (`pg_execute_sql`, `query`, `list-tables`, `analyze-missing-indexes`, ...). The agent has to learn N flavors. |
| **Heavy install** | Each server needs its own runtime (Node, Python, Go), its own dependency tree, its own process. Five DBs = five servers. |
| **Unsafe defaults** | Many expose destructive operations as first-class tools (`pg_execute_mutation`, `truncate`, `drop-index`). One LLM hallucination away from data loss. |
| **TLS / sandbox friction** | Connecting to remote managed databases via JS/Python tooling regularly hits chain rejection, cert pinning, sandboxed-process port restrictions. |
| **Output drift** | Same conceptual operation ("describe this table") returns different shapes per server. Agent can't write generic post-processing. |

`singularidade-data-bridge` is a **deliberately reduced** alternative aimed at the 80% of cases where the LLM only needs to **inspect or query**, not administer:

- **One fat JAR** (~24 MB) with five JDBC drivers shaded in (PostgreSQL, Firebird, Oracle, SQL Server, MySQL). One install, all five backends.
- **One JSON contract** for `extract` (per-table metadata), `extract-all` (whole-schema snapshot + summary index), and `query` (free-form SELECT with column metadata). Same shape regardless of driver.
- **Read-only by design.** `extract` collects no sample rows by default (PII-safe). `query` rejects DML/DDL unless `--writable`, and rejects DDL outright even then.
- **Hermetic.** No daemon required (CLI per call); optional `serve` mode if you want connection pooling for repeated calls.
- **Stable contracts** under semver — versioned JSON schemas, documented exit codes, no surprises.

It is **not** a full database admin tool. It will never manage indexes, copy data between databases, run backups, or modify schemas. For those, use the database-specific MCP server, `psql`/`sqlcmd`/`mysql`, Liquibase, Flyway, etc. The point is the **smaller surface**: one JAR, one contract, read-only-by-default.

---

## For LLM agents reading this

If you're an AI agent deciding whether and how to use this tool:

**Use `data-bridge` when:**
- The user has a JDBC URL and you need to understand a table's schema (columns, types, FKs, indexes, constraints, statistics) — call `extract` or `extract-all`.
- You need to test a SELECT query against the database before generating an MCP tool / ETL job / migration / report — call `query`.
- You need a snapshot of an entire schema for diff-style comparison (CI drift detection, "schema-as-code") — call `extract-all` and commit `_index.json` + per-table folders.

**Don't use `data-bridge` when:**
- You need to modify schema (CREATE / DROP / ALTER) → use Liquibase, Flyway, or a database-specific migration tool. data-bridge will reject DDL even with `--writable`.
- You need full DBA capabilities (manage indexes, RLS, copy between DBs, vacuum/analyze tuning) → use the DB-specific MCP server, `psql`, etc.
- You need result streaming for large tables → data-bridge returns full result sets in one response (capped by `--limit`).

**Default recipe for "understand + query" workflows:**
1. Get a JDBC URL from the user (must include credentials inline: `jdbc:postgresql://host/db?user=u&password=p`).
2. Discover what's there: `data-bridge list-tables --jdbc-url ... --schema X`.
3. Get full structure for the relevant table: `data-bridge extract --jdbc-url ... --schema X --table Y --out /tmp/work/`. Read `/tmp/work/X.Y/metadata.json`.
4. Test queries: `data-bridge query --jdbc-url ... --sql "SELECT ... LIMIT 10"`. Pipe stdout into your JSON parser.
5. (Optional) For repeated calls, start `data-bridge serve --port 8765` once and use the HTTP API.

**Safety rules already in effect — you don't have to enforce them yourself:**
- `extract` collects **zero sample rows** unless you pass `--sample-rows N`. Default output is safe to commit alongside source.
- `query` is **read-only** unless you pass `--writable`. SELECT/WITH/EXPLAIN/SHOW only.
- DDL is **always rejected** (DROP, CREATE, ALTER, TRUNCATE, GRANT, REVOKE), even with `--writable`.
- Multi-statement SQL is **always rejected** (`SELECT 1; DROP TABLE x` style injection patterns).
- `password` in the JDBC URL is **auto-redacted** (`password=***`) in all output and error messages.

**Output is stable JSON.** Every command writes JSON with documented schema (see [Output contracts](#output-contract-metadatajson)). Errors are uniform: `{"error": {"code": "...", "message": "...", "hint": "..."}}` on stderr (CLI) or response body (HTTP), with documented exit codes (CLI) and HTTP status (HTTP).

---

## Install

### Option A — Download the release JAR (recommended)

Grab the latest fat JAR (≈ 80 MB; bundles all five JDBC drivers):

```bash
LATEST=$(curl -s https://api.github.com/repos/singularidade-digital-dev/singularidade-data-bridge/releases/latest | jq -r .tag_name)
curl -fLo data-bridge.jar \
  "https://github.com/singularidade-digital-dev/singularidade-data-bridge/releases/download/${LATEST}/data-bridge-${LATEST}.jar"
java -jar data-bridge.jar version
```

Each release also publishes a `.sha256` file next to the JAR — verify with `sha256sum -c data-bridge-${LATEST}.jar.sha256`.

### Option B — Build from source

Requires JDK 21 and Maven. Docker is needed only if you want to run the integration tests.

```bash
git clone https://github.com/singularidade-digital-dev/singularidade-data-bridge.git
cd singularidade-data-bridge
mvn -DskipTests package
java -jar target/data-bridge.jar version
```

### Requirements

| | |
|---|---|
| Java runtime | 21+ (Temurin recommended) |
| Disk | ~24 MB for the fat JAR |
| Network | Only what your `--jdbc-url` needs |
| Privileges | None — read-only operations only |

The tool runs every connection with `setReadOnly(true)`. It never issues anything beyond `SELECT` and metadata queries.

---

## Quick start

### Extract a single table

```bash
java -jar data-bridge.jar extract \
  --jdbc-url "jdbc:postgresql://host:5432/mydb?user=alice&password=s3cret&sslmode=require" \
  --schema public \
  --table customers \
  --out ./snapshot/
```

Result: `./snapshot/public.customers/metadata.json`. Add `--tsv` for companion TSV files in the same directory. Add `--sample-rows 5` if you want 5 real rows under `sample` in the JSON (off by default — see [Known limitations](#known-limitations) about PII).

### Extract every table in a schema

```bash
java -jar data-bridge.jar extract-all \
  --jdbc-url "jdbc:postgresql://host:5432/mydb?user=alice&password=s3cret" \
  --schema public \
  --out ./snapshot/
```

Iterates every table in the schema, writes one subdirectory per table (`./snapshot/public.<table>/metadata.json`), plus a top-level `./snapshot/_index.json` summary you can quickly diff or browse. Useful for the "schema-as-code" pattern — commit `./snapshot/` alongside your application's source so PRs surface schema drift.

### List tables in a schema

```bash
java -jar data-bridge.jar list-tables \
  --jdbc-url "jdbc:postgresql://host/mydb?user=alice&password=s3cret" \
  --schema public
```

Prints a JSON array on stdout.

### Run a free-form SELECT

```bash
java -jar data-bridge.jar query \
  --jdbc-url "jdbc:postgresql://host:5432/mydb?user=alice&password=s3cret" \
  --sql "SELECT id, name FROM customers WHERE created_at > now() - interval '7 days' LIMIT 10"
```

Prints JSON on stdout: `{ "kind": "query", "rowCount": 10, "columns": [...], "rows": [...] }`. Defaults are read-only (SELECT/WITH/EXPLAIN/SHOW only), `--limit 100`, `--timeout-sec 30`. Pass `--writable` to permit INSERT/UPDATE/DELETE; DDL is rejected unconditionally.

### Run as an HTTP daemon

```bash
java -jar data-bridge.jar serve --port 8765
```

Then from any client:

```bash
curl -s http://localhost:8765/v1/health

curl -s -X POST http://localhost:8765/v1/extract \
  -H 'Content-Type: application/json' \
  -d '{
    "jdbcUrl": "jdbc:postgresql://host/mydb?user=alice&password=s3cret",
    "schema": "public",
    "table": "customers"
  }' | jq .   # add "sampleRows": 5 in the body if you want sample data

curl -s "http://localhost:8765/v1/list-tables?jdbcUrl=$(printf %s 'jdbc:postgresql://host/mydb?user=alice&password=s3cret' | jq -sRr @uri)&schema=public"

curl -s -X POST http://localhost:8765/v1/query \
  -H 'Content-Type: application/json' \
  -d '{
    "jdbcUrl": "jdbc:postgresql://host/mydb?user=alice&password=s3cret",
    "sql": "SELECT id, name FROM customers LIMIT 10",
    "limit": 100
  }' | jq .   # add "writable": true to permit DML; DDL is rejected always
```

The daemon keeps one HikariCP pool per normalized JDBC URL. Idle pools are evicted after `--idle-timeout` (default 10 min). The JVM shutdown hook closes everything cleanly on `SIGTERM`.

---

## CLI reference

### `extract` — full metadata + sample for one table

```text
data-bridge extract --jdbc-url <url> [--schema <name>] --table <name> --out <dir>
                    [--sample-rows N] [--tsv] [--no-cardinality]
                    [-q|--quiet] [-v|--verbose]
```

| Flag | Required | Default | Purpose |
|---|---|---|---|
| `--jdbc-url` | yes | — | Full JDBC URL. Driver auto-detected from the prefix. Credentials and SSL params are part of the URL. |
| `--schema` | depends on driver | — | Required for PostgreSQL/Oracle/SQL Server. Ignored on Firebird/MySQL (single-schema). |
| `--table` | yes | — | Table or view name. |
| `--out` | yes | — | Output **parent** directory. The actual files are written under `<out>/<schema>.<table>/`. Created if missing. |
| `--sample-rows` | no | `0` | Sample row count. **Default 0 = no sample collected.** Pass e.g. `--sample-rows 5` to include 5 real rows under `sample` in the JSON (PII-bearing — see limitations). |
| `--tsv` | no | off | Also emit `columns.tsv`, `fks.tsv`, `indexes.tsv`, `unique-constraints.tsv`, `check-constraints.tsv`, `sample.tsv`, `cardinality.tsv`. |
| `--no-cardinality` | no | off | Skip `COUNT(*)` and per-column `COUNT(DISTINCT)`. Use on huge tables (see [Known limitations](#known-limitations)). |
| `-q`, `--quiet` | no | off | Suppress per-step progress on stderr. Errors still reported. |
| `-v`, `--verbose` | no | off | Include exception class + message in error JSON; print stack traces. |

### `extract-all` — extract every table in a schema

```text
data-bridge extract-all --jdbc-url <url> --schema <name> --out <dir>
                        [--exclude TABLE]... [--include-views]
                        [--sample-rows N] [--no-cardinality] [--tsv]
                        [-q|--quiet] [-v|--verbose]
```

Iterates every table in the schema (sequentially, single connection) and runs the same pipeline as `extract` for each. Writes:

```
<out>/
├── _index.json                       ← schema-level summary (timestamp, source, table list with row/column counts)
├── <schema>.<table_a>/
│   ├── metadata.json
│   └── *.tsv (if --tsv)
└── <schema>.<table_b>/
    └── ...
```

| Flag | Required | Default | Purpose |
|---|---|---|---|
| `--jdbc-url` | yes | — | Same as `extract`. |
| `--schema` | yes | — | Required (one schema per invocation; loop externally for multi-schema). |
| `--out` | yes | — | Parent directory for the per-table subdirs and `_index.json`. |
| `--exclude TABLE` | no | — | Skip a table by exact name. Repeatable: `--exclude audit_log --exclude staging`. |
| `--include-views` | no | off | Also iterate views and materialized views (PG). Off by default = TABLE only. |
| `--sample-rows N` | no | `0` | Same semantics as `extract`. Applies to every table. |
| `--no-cardinality` | no | off | Same as `extract`. **Highly recommended** for whole-schema extracts of large DBs. |
| `--tsv` | no | off | Companion TSVs in each per-table directory. |

The `_index.json` shape:

```json
{
  "$schema":     "https://singularidade.digital/data-bridge/extract-all-index.v1.json",
  "version":     "1.0",
  "generatedAt": "2026-05-10T12:00:00Z",
  "generator":   { "name": "singularidade-data-bridge", "version": "0.4.0" },
  "source":      { "type": "jdbc", "driver": "postgresql",
                   "url": "jdbc:postgresql://...?password=***", "schema": "public" },
  "tableCount":  12,
  "tables": [
    { "schema": "public", "table": "customers", "type": "TABLE",
      "columnCount": 9, "primaryKey": ["id"], "totalRows": 18432 }
  ]
}
```

CI tip: diff two `_index.json` snapshots (today vs. previous deploy) to surface row-count drift, new tables, dropped columns, etc., without parsing every per-table `metadata.json`.

### `query` — execute one SQL statement

```text
data-bridge query --jdbc-url <url> --sql "<SQL>"
                  [--limit 100] [--timeout-sec 30] [--writable]
                  [--out file.json]
                  [-q|--quiet] [-v|--verbose]
```

Defense-in-depth, in order:

1. **Multi-statement input is rejected** (`SELECT 1; DROP TABLE x` style).
2. **DDL is rejected unconditionally** (`DROP`, `CREATE`, `ALTER`, `TRUNCATE`, `GRANT`, `REVOKE`) — even with `--writable`. Use Liquibase/Flyway for schema changes.
3. **Without `--writable`, only SELECT / WITH / EXPLAIN / SHOW** are accepted (read-only mode also flips `connection.setReadOnly(true)`, which the database driver enforces where supported).
4. With `--writable`, **DML is allowed** (INSERT/UPDATE/DELETE) but DDL still rejected.
5. **Recommended on top of all of the above:** point `--jdbc-url` at a database role with `GRANT SELECT` only — the only defense that actually binds against an adversary, vs. the layers above which guard against accidents.

| Flag | Required | Default | Purpose |
|---|---|---|---|
| `--jdbc-url` | yes | — | Full JDBC URL with credentials inline. |
| `--sql` | yes | — | The SQL to execute. Must be a single statement. |
| `--limit` | no | `100` | Max rows returned. The driver gets `setMaxRows(limit+1)` so the result is marked `truncated=true` if more rows exist. |
| `--timeout-sec` | no | `30` | JDBC query timeout in seconds. `0` = no timeout. |
| `--writable` | no | off | Permit INSERT/UPDATE/DELETE. DDL still rejected. |
| `--out` | no | stdout | Write JSON result to this file path instead of stdout. Use `-` for explicit stdout. |
| `-q`, `--quiet` | no | off | Suppress "wrote /path" message on stderr when `--out` is used. |
| `-v`, `--verbose` | no | off | Include exception class + message in error JSON; print stack traces. |

Output shape (always JSON):

```json
{
  "kind": "query",
  "rowCount": 10,
  "truncated": false,
  "columns": [
    { "name": "id",   "sqlType": "bigint",       "jdbcType": -5 },
    { "name": "name", "sqlType": "varchar(200)", "jdbcType": 12 }
  ],
  "rows": [
    { "id": 1, "name": "ALICE" }
  ],
  "executionTimeMs": 13,
  "warnings": []
}
```

For `--writable` UPDATE/INSERT/DELETE statements, the shape is:

```json
{
  "kind": "update",
  "updateCount": 5,
  "executionTimeMs": 22,
  "warnings": []
}
```

### `list-tables` — list table names in a schema

```text
data-bridge list-tables --jdbc-url <url> [--schema <name>]
```

Prints a JSON array of table names to stdout. Always TABLE only (no views).

### `serve` — start the HTTP daemon

```text
data-bridge serve [--port 8765] [--max-pool 5] [--idle-timeout 10m]
```

| Flag | Default | Purpose |
|---|---|---|
| `--port` | `8765` | TCP port to listen on. Use `0` for an ephemeral port (printed to stderr). |
| `--max-pool` | `5` | Max HikariCP connections per unique JDBC URL. |
| `--idle-timeout` | `10m` | Pools idle longer than this are closed and evicted. Accepts `ms`, `s`, `m`, `h` suffixes. |

### `version` — print version and exit

```text
data-bridge version
# → singularidade-data-bridge 0.4.0
```

### Exit codes

| Code | Meaning |
|---|---|
| `0` | Success |
| `1` | Unspecified / unclassified error |
| `2` | Invalid arguments (rejected by picocli) |
| `10` | Connection failed (URL, driver, auth, network, TLS) |
| `11` | Table not found |
| `12` | Schema not found |
| `13` | Query failed mid-pipeline (timeout, permission, syntax) |
| `14` | Output write failed (disk full, permission) |
| `64` | Unsupported (driver-specific feature unavailable in MVP) |

When something fails, a single JSON document is written to **stderr** and the process exits with the matching code:

```json
{
  "error": {
    "code": "TABLE_NOT_FOUND",
    "message": "Table 'public.customers' not found",
    "hint": "Run `data-bridge list-tables ...` to discover available tables"
  }
}
```

---

## HTTP API

Same pipeline as the CLI; same JSON output. Ideal for repeated calls because credentials and connections are pooled.

| Method | Path | Body / Query | Response |
|---|---|---|---|
| `GET` | `/v1/health` | — | `200 {"status":"ok"}` |
| `GET` | `/v1/version` | — | `200 {"name":"singularidade-data-bridge","version":"0.4.0"}` |
| `GET` | `/v1/list-tables` | query: `jdbcUrl` (required), `schema` (optional) | `200 ["table1","table2",…]` |
| `POST` | `/v1/extract` | body: `ExtractRequest` (see below) | `200` `metadata.json` body |
| `POST` | `/v1/query` | body: `QueryRequest` (see below) | `200` `QueryResult` body |

`ExtractRequest`:

```json
{
  "jdbcUrl": "jdbc:postgresql://host/db?user=u&password=p",
  "schema": "public",
  "table": "customers",
  "sampleRows": 0,
  "skipCardinality": false
}
```

`sampleRows` defaults to `0` (no sample collected — see [Known limitations](#known-limitations)), `skipCardinality` to `false`, `schema` may be omitted for single-schema drivers.

`QueryRequest`:

```json
{
  "jdbcUrl": "jdbc:postgresql://host/db?user=u&password=p",
  "sql": "SELECT id, name FROM customers LIMIT 10",
  "limit": 100,
  "timeoutSec": 30,
  "writable": false
}
```

All fields except `jdbcUrl` and `sql` are optional with the documented defaults. Same defense layers as the CLI: multi-statement / DDL rejected always; non-SELECT only when `writable=true`. The response is identical to what the CLI writes (`{kind, rowCount, columns, rows, ...}` for queries; `{kind, updateCount, ...}` for updates).

### HTTP status code mapping

| Status | Meaning |
|---|---|
| `200` | Success |
| `400` | Invalid request body / missing required field |
| `404` | Table or schema not found |
| `500` | Internal data-bridge error |
| `502` | Connection or query failed downstream |

---

## Output contract (`metadata.json`)

The full schema is documented in [`docs/superpowers/specs/2026-05-09-singularidade-data-bridge-design.md`](docs/superpowers/specs/2026-05-09-singularidade-data-bridge-design.md) §5. Top-level shape:

```json
{
  "$schema":  "https://singularidade.digital/data-bridge/metadata.v1.json",
  "version":  "1.0",
  "generatedAt": "2026-05-09T15:42:11Z",
  "generator": { "name": "singularidade-data-bridge", "version": "0.4.0" },
  "source":   { "type": "jdbc", "driver": "postgresql",
                "url": "jdbc:postgresql://...?password=***",
                "schema": "public", "table": "customers" },
  "tableInfo":         { "type": "TABLE", "comment": "...", "owner": "...",
                         "approximateRowCount": 18432, "viewDefinition": null },
  "columns":           [ /* ordinal-ordered, with sqlType, nullability,
                            primaryKey flag, default, comment, generated info */ ],
  "primaryKey":        ["id"],
  "foreignKeys":       [ /* each FK includes a 1-deep snapshot of the referenced
                            table's columns under refTableColumns */ ],
  "indexes":           [ /* with method (btree/hash/...) and partial WHERE for PG */ ],
  "uniqueConstraints": [ ],
  "checkConstraints":  [ ],
  "sample":            { "rowCount": 5,             /* 0 unless --sample-rows N */
                         "rows":     [ { "col1": ..., "col2": ... }, ... ] },
  "columnStats":       [ /* pg_stats; empty array on non-PG drivers */ ],
  "cardinality":       { "totalRows": 18432,
                         "perColumn": [ { "name": "id",
                                          "distinctCount": 18432,
                                          "nullCount":     0 } ] },
  "partitioning":      { "isPartitioned": false, "strategy": null,
                         "partitionKey": [], "parent": null, "children": [] },
  "warnings":          [ /* free-form; e.g. when columnStats is unavailable */ ]
}
```

**Versioning.** Additive changes (new fields) bump to `1.x`; removals or renames bump to `2.0`. Producers and consumers must tolerate `additionalProperties`. BLOB/binary cells are normalized to `{ "_blob": true, "size": N, "preview": "<base64 of first 64 bytes>" }`.

**Atomic writes.** The CLI writes to `.metadata.json.partial` first, then renames atomically. You will never see a half-written `metadata.json`.

---

## Driver matrix

| Driver | URL prefix | Status in MVP | Notes |
|---|---|---|---|
| PostgreSQL | `jdbc:postgresql:` | ✅ Full IT coverage (Postgres Testcontainer) | `pg_stats`, `pg_partitioned_table`, `pg_indexes` augments wired in |
| Firebird | `jdbc:firebirdsql:` | ⚠️ Driver loads; pipeline runs; no live IT yet | Jaybird 6.0.5 |
| Oracle | `jdbc:oracle:` | ⚠️ Driver loads; pipeline runs; no live IT yet | ojdbc11 23.6 |
| SQL Server | `jdbc:sqlserver:` | ⚠️ Driver loads; pipeline runs; no live IT yet | mssql-jdbc 12.8.1.jre11 |
| MySQL | `jdbc:mysql:` | ⚠️ Driver loads; pipeline runs; no live IT yet | mysql-connector-j 9.1.0 |

All five drivers are shaded into the fat JAR and verified loadable via per-driver smoke tests. Live integration tests against each non-Postgres driver land as those connectors are exercised in production.

For drivers that don't expose a feature (e.g. `pg_stats` is PG-only), the corresponding section of `metadata.json` is empty and a `warnings` entry explains why.

---

## Known limitations

- **Cardinality cost.** `COUNT(*)` and `COUNT(DISTINCT col)` are exact and run sequentially. On a 50 M-row table with 30 columns, a single `extract` can take tens of minutes. Use `--no-cardinality` to skip it entirely. Future work: opt-in approximation via `pg_stats.n_distinct`.
- **No `--where` filter (yet).** Sample and cardinality reflect the entire table. The caller is responsible for scoping (e.g. point at a single-tenant database, or wait for `--where` post-MVP).
- **Sample data is opt-in and NOT redacted.** Sample collection is off by default (`--sample-rows 0`). When you opt in (`--sample-rows N`), the resulting `metadata.json` contains real database rows — including any PII present (CPFs, names, emails, etc.). For the "schema-as-code" workflow (committing `_index.json`/per-table `metadata.json` alongside source code), keep `--sample-rows 0` and the output is safe to version-control. For ad-hoc inspection (`--sample-rows 5`), point `--out` at an ephemeral directory and don't commit. The `password` query parameter in the source URL **is** redacted (replaced with `***`) in `metadata.json`, in stderr logs, and in error messages.
- **No authentication on `serve` mode.** Bind to `localhost` only (the default), or front the daemon with a reverse proxy if you need TLS or auth.
- **Read-only enforcement is best-effort.** The tool issues `setReadOnly(true)` on every connection and only ever runs metadata queries and `SELECT`s, but it does not run with a database role that mechanically forbids writes. If you want hard guarantees, give it a read-only DB user.

---

## Architecture

```
   ┌──────────────┐     ┌──────────────┐
   │  CLI (picocli)│     │ HTTP (Javalin)│
   └──────┬───────┘     └──────┬───────┘
          │                    │
          └─────────┬──────────┘
                    │
            ┌───────▼────────┐
            │ MetadataPipeline│   11 sequential extractors
            │  (orchestrator) │
            └───────┬────────┘
                    │
            ┌───────▼────────┐
            │     Source     │   interface (extension point:
            │   (interface)  │    REST / GraphQL / file later)
            └───────┬────────┘
                    │
            ┌───────▼────────┐
            │   JdbcSource   │   single JDBC impl, per-driver
            │ + DriverHints  │   quirks via DriverHints enum
            └────────────────┘
```

Eleven small `Extractor` classes — one per metadata concept (columns, primary key, foreign keys, indexes, unique/check constraints, sample, column stats, partitioning, cardinality, table info) — each delegates to the matching `Source` method. The pipeline runs them sequentially against a single connection and assembles the result into the `Metadata` record. The `JsonWriter` and (optional) `TsvWriter` consume that record graph.

The HTTP layer reuses the same pipeline and the same record graph; the only difference is that HTTP gets its `Connection` from a HikariCP pool keyed by a normalized URL (ephemeral params like `connectTimeout` are stripped before keying so they don't fragment pools).

Full design rationale lives in [`docs/superpowers/specs/2026-05-09-singularidade-data-bridge-design.md`](docs/superpowers/specs/2026-05-09-singularidade-data-bridge-design.md). Implementation history (31 TDD tasks) lives in [`docs/superpowers/plans/2026-05-09-singularidade-data-bridge-mvp.md`](docs/superpowers/plans/2026-05-09-singularidade-data-bridge-mvp.md).

---

## Development

```bash
mvn test                 # unit tests + driver-load smoke (no Docker required)
mvn verify               # adds Postgres Testcontainer ITs (Docker required)
mvn -DskipTests package  # produces target/data-bridge.jar
```

The build is conventional Maven. Surefire picks up `*Test.java`; Failsafe picks up `*IT.java`. Tests follow `should_<result>_when_<condition>` naming where it reads naturally.

CI runs the full `mvn -B verify` on every push and pull request — see [`.github/workflows/ci.yml`](.github/workflows/ci.yml). Tagged releases (`v*`) trigger [`.github/workflows/release.yml`](.github/workflows/release.yml), which republishes the fat JAR and its SHA-256 to a GitHub Release.

### Cutting a release

Recommended:

```bash
scripts/release.sh 0.4.0
```

This bumps `pom.xml`, runs the full test suite, commits, tags `v0.4.0`, pushes, and bumps to the next development SNAPSHOT — all in one go. The tag push triggers `release.yml`, which builds the fat JAR, computes its SHA-256, and publishes both as a GitHub Release.

Alternatives — manual `git tag` and clicking the **Run workflow** button on the GitHub UI — are documented in [`RELEASING.md`](RELEASING.md) along with versioning policy and recovery procedures.

### Project conventions

- **Java 21, no Lombok**, records for DTOs.
- **Constructor injection only.** No reflection except where Jackson does it for us.
- **Imports at the top.** No inline FQNs except for genuine name collisions.
- **No comments unless they explain a *why* that the code can't.** Names carry the *what*.
- **TDD where it pays.** All extractors landed test-first against a real Postgres in a Testcontainer.

---

## License

[Apache License 2.0](LICENSE) © singularidade.digital
