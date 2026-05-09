# singularidade-data-bridge

[![CI](https://github.com/singularidade-digital-dev/singularidade-data-bridge/actions/workflows/ci.yml/badge.svg)](https://github.com/singularidade-digital-dev/singularidade-data-bridge/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/singularidade-digital-dev/singularidade-data-bridge?label=release)](https://github.com/singularidade-digital-dev/singularidade-data-bridge/releases/latest)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-21-orange.svg)](https://adoptium.net/temurin/releases/?version=21)

A uniform JDBC introspection tool. Point it at any supported database, name a table, and get back a single, versioned JSON document describing the table's schema, constraints, indexes, sample rows, column statistics, partitioning, and cardinality — plus optional human-friendly TSV dumps.

Runs as a one-shot **CLI** for ad-hoc extraction, or as a long-running **HTTP daemon** with HikariCP-pooled connections for repeated calls from local tooling.

---

## Why this exists

Code-generation skills, schema-aware LLM tools, ETL planners, and migration scripts all need the same thing: a complete, structured snapshot of a relational table. Each tends to grow its own ad-hoc introspection — `psql \d`, `information_schema` queries, JDBC `DatabaseMetaData` boilerplate, vendor-specific quirks. This tool does it once, well, against a stable JSON contract, so callers can stop reinventing it.

Designed to be:

- **Driver-uniform.** One JSON shape regardless of whether the source is PostgreSQL, Firebird, Oracle, SQL Server, or MySQL. Per-driver quirks live behind one enum.
- **Boundary-clean.** A `Source` interface decouples extractors from JDBC. Future REST/GraphQL/file sources plug in without changing pipeline code.
- **Hermetic.** A single fat JAR with all five JDBC drivers shaded in. No system installs, no driver hunting, no classpath surprises.
- **Cheap to embed.** Either run the CLI per call, or start the HTTP daemon once and reuse pooled connections across hundreds of calls.

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
| Disk | ~80 MB for the fat JAR |
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
  --out /tmp/customers/
```

Result: `/tmp/customers/metadata.json`. Add `--tsv` for companion TSV files.

### List tables in a schema

```bash
java -jar data-bridge.jar list-tables \
  --jdbc-url "jdbc:postgresql://host/mydb?user=alice&password=s3cret" \
  --schema public
```

Prints a JSON array on stdout.

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
    "table": "customers",
    "sampleRows": 5
  }' | jq .

curl -s "http://localhost:8765/v1/list-tables?jdbcUrl=$(printf %s 'jdbc:postgresql://host/mydb?user=alice&password=s3cret' | jq -sRr @uri)&schema=public"
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
| `--out` | yes | — | Output directory. Created if missing. **Always use an ephemeral path** — see the PII note below. |
| `--sample-rows` | no | `5` | Number of sample rows to read via `SELECT * FROM <t> LIMIT N`. |
| `--tsv` | no | off | Also emit `columns.tsv`, `fks.tsv`, `indexes.tsv`, `unique-constraints.tsv`, `check-constraints.tsv`, `sample.tsv`, `cardinality.tsv`. |
| `--no-cardinality` | no | off | Skip `COUNT(*)` and per-column `COUNT(DISTINCT)`. Use on huge tables (see [Known limitations](#known-limitations)). |
| `-q`, `--quiet` | no | off | Suppress per-step progress on stderr. Errors still reported. |
| `-v`, `--verbose` | no | off | Include exception class + message in error JSON; print stack traces. |

### `list-tables` — list table names in a schema

```text
data-bridge list-tables --jdbc-url <url> [--schema <name>]
```

Prints a JSON array of table names to stdout.

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
# → singularidade-data-bridge 0.1.0
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
| `GET` | `/v1/version` | — | `200 {"name":"singularidade-data-bridge","version":"0.1.0"}` |
| `GET` | `/v1/list-tables` | query: `jdbcUrl` (required), `schema` (optional) | `200 ["table1","table2",…]` |
| `POST` | `/v1/extract` | body: `ExtractRequest` (see below) | `200` `metadata.json` body |

`ExtractRequest`:

```json
{
  "jdbcUrl": "jdbc:postgresql://host/db?user=u&password=p",
  "schema": "public",
  "table": "customers",
  "sampleRows": 5,
  "skipCardinality": false
}
```

`sampleRows` defaults to `5`, `skipCardinality` to `false`, `schema` may be omitted for single-schema drivers.

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
  "generator": { "name": "singularidade-data-bridge", "version": "0.1.0" },
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
  "sample":            { "rowCount": 5,
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
- **Sample data is NOT redacted.** `metadata.json` may contain unmasked CPFs, names, emails, and any other PII present in the table. **Always point `--out` at an ephemeral directory (`/tmp/...`). Never commit extractions to git.** The `password` query parameter in the source URL **is** redacted (replaced with `***`) in `metadata.json`, in stderr logs, and in error messages.
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

```bash
git tag v0.2.0
git push origin v0.2.0
```

The release workflow takes it from there. (For now, the version string in `pom.xml`, `Main.VersionProvider`, and `MetadataPipeline.GENERATOR_VERSION` is updated by hand before tagging — automation for that is on the post-MVP list.)

### Project conventions

- **Java 21, no Lombok**, records for DTOs.
- **Constructor injection only.** No reflection except where Jackson does it for us.
- **Imports at the top.** No inline FQNs except for genuine name collisions.
- **No comments unless they explain a *why* that the code can't.** Names carry the *what*.
- **TDD where it pays.** All extractors landed test-first against a real Postgres in a Testcontainer.

---

## License

[Apache License 2.0](LICENSE) © singularidade.digital
