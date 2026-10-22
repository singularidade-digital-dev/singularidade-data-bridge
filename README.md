# singularidade-data-bridge

[![CI](https://github.com/singularidade-digital-dev/singularidade-data-bridge/actions/workflows/ci.yml/badge.svg)](https://github.com/singularidade-digital-dev/singularidade-data-bridge/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/singularidade-digital-dev/singularidade-data-bridge?label=release)](https://github.com/singularidade-digital-dev/singularidade-data-bridge/releases/latest)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-21-orange.svg)](https://adoptium.net/temurin/releases/?version=21)

A uniform JDBC introspection tool. Point it at any supported database, name one or more tables, and get back a versioned JSON document per table describing its schema, constraints, indexes, triggers, sample rows, column statistics, partitioning and cardinality — plus the table's DDL in the database's own dialect and optional TSV dumps.

It is a one-shot **CLI**: every call opens one connection, does its job and exits.

---

## Why this exists

LLMs and code-generation agents increasingly need to **understand or query** relational databases — to plan ETL jobs, to generate MCP tools, entities or migrations, to validate a hypothesis against real data. Today that is normally done through per-database **MCP servers**. It works, but at a cost:

| Pain | Why it bites |
|---|---|
| **Heterogeneous APIs** | Each MCP server exposes a different tool surface (`pg_execute_sql`, `query`, `describe-table`, `analyze-missing-indexes`, ...). The agent has to learn N flavors. |
| **Heavy install** | Each server needs its own runtime (Node, Python, Go), its own dependency tree, its own process. Five DBs = five servers. |
| **Unsafe defaults** | Many expose destructive operations as first-class tools. One LLM hallucination away from data loss. |
| **TLS / sandbox friction** | Connecting to remote managed databases via JS/Python tooling regularly hits chain rejection, cert pinning, sandboxed-process port restrictions. |
| **Output drift** | Same conceptual operation ("describe this table") returns different shapes per server. Agents can't write generic post-processing, and nothing is stable enough to commit. |

`singularidade-data-bridge` is a **deliberately reduced** alternative aimed at the cases where the agent only needs to **inspect or query**, not administer:

- **One fat JAR** (~19 MB) with five JDBC drivers shaded in (PostgreSQL, Firebird, Oracle, SQL Server, MySQL), plus a **catalog edition** without free-form SQL.
- **One JSON contract** for `extract` / `extract-all` (per-table metadata + a summary index) and `query` / `run-query` (rows with column metadata). Same shape regardless of driver.
- **Read-only by design.** No sample rows by default (PII-safe). `query` accepts one allowlisted statement and rejects DML unless `--writable`, and DDL always.
- **Versionable.** `--stable` writes files you can commit and diff: no timestamps, no versions, no database-generated names, every list in a fixed order.
- **Stable contracts** under semver — versioned JSON schemas, documented exit codes.

It is **not** a database admin tool. It will never manage indexes, copy data between databases, run backups or modify schemas. For those, use `psql`/`isql`/`sqlcmd`/`mysql`, Liquibase, Flyway, or the database-specific tooling.

---

## For LLM agents reading this

**Use `data-bridge` when:**
- You need the structure of one or more tables (columns, exact types, domains, keys, FKs, indexes, constraints, triggers, generators) — call `extract` (several `--table` in one call) or `extract-all`.
- You need to test a SELECT before generating an MCP tool / ETL job / entity / report — call `query`.
- You need a fixed lookup with parameters, such as reading a data dictionary table — put it in a queries file and call `run-query`.
- You need a schema snapshot to commit for diffing (CI drift detection, "schema-as-code") — call `extract-all --stable` (or `extract --stable` for a list of tables).

**Don't use `data-bridge` when:**
- You need to change the schema (CREATE / DROP / ALTER) → Liquibase, Flyway or a migration tool. data-bridge rejects DDL even with `--writable`.
- You need DBA work (manage indexes, RLS, vacuum, copy between databases) → the database's own tools.
- You need result streaming for large result sets → data-bridge returns the result in one document (capped by `--limit`).

**Default recipe:**
1. Keep credentials out of the URL: `--jdbc-url jdbc:firebirdsql://host:3050//path/db.fdb --user ALICE --password-env DB_PASSWORD` (or `--password-file ~/.secrets/db`).
2. Discover what's there: `data-bridge list-tables ...`.
3. Get the structure: `data-bridge extract ... --table A --table B --out /tmp/work/ --cardinality-mode skip`. Read `/tmp/work/<table>/metadata.json` (and `ddl.sql`).
4. Test queries: `data-bridge query ... --sql "SELECT ..."`, or fixed ones with `data-bridge run-query ... --file queries.sql --name colunas --param tabela=A`.

**Safety rules already in effect — you don't have to enforce them yourself:**
- `extract` collects **zero sample rows** unless you pass `--sample-rows N`.
- `query` runs **one statement**, checked after comments and string literals are blanked out, so nothing can hide behind a comment.
- Without `--writable`, only `SELECT` / `WITH` / `EXPLAIN` / `SHOW` run, and data-modifying clauses anywhere in them are rejected. On Firebird, `GEN_ID(g, n)` with `n ≠ 0` and `NEXT VALUE FOR` are rejected too (generators advance even inside read-only transactions).
- DDL (`CREATE`, `ALTER`, `DROP`, `RECREATE`, `TRUNCATE`, `GRANT`, `REVOKE`, `COMMENT`, ...) and procedural / session statements (`EXECUTE BLOCK`, `EXECUTE`, `EXEC`, `CALL`, `DO`, `SET`) are **always rejected**, even with `--writable`.
- `run-query` is always read-only and goes through the same guard.
- The `password` in a JDBC URL is **redacted** (`password=***`) in all output and error messages. `source.url` additionally hides host and port by default.
- `columnStats` defaults to **`histogram-only`** — `mostCommonValues` and `mostCommonFrequencies` (PII risk) are dropped unless `--column-stats-mode=full`.
- The catalog JAR (`data-bridge-catalog-*.jar`) has **no `query` command at all**.

**Output is stable JSON.** Errors are uniform: `{"error": {"code": "...", "message": "...", "hint": "..."}}` on stderr, with documented exit codes.

---

## Install

### Option A — Download a release JAR (recommended)

Each release publishes two fat JARs (~19 MB each; both bundle the five JDBC drivers):

| JAR | Commands | When |
|---|---|---|
| `data-bridge-vX.Y.Z.jar` | `extract`, `extract-all`, `list-tables`, `query`, `run-query`, `version` | General use. |
| `data-bridge-catalog-vX.Y.Z.jar` | `extract`, `extract-all`, `list-tables`, `run-query`, `version` | Catalog work where free-form SQL must not be possible. `version` prints `... (catalog)`. |

```bash
LATEST=$(curl -s https://api.github.com/repos/singularidade-digital-dev/singularidade-data-bridge/releases/latest | jq -r .tag_name)
BASE="https://github.com/singularidade-digital-dev/singularidade-data-bridge/releases/download/${LATEST}"
curl -fL -O "${BASE}/data-bridge-${LATEST}.jar" -O "${BASE}/data-bridge-${LATEST}.jar.sha256"
sha256sum -c "data-bridge-${LATEST}.jar.sha256"
java -jar "data-bridge-${LATEST}.jar" version
```

Replace `data-bridge-` with `data-bridge-catalog-` for the catalog edition.

### Option B — Build from source

Requires JDK 21 and Maven. Docker is needed only for the integration tests.

```bash
git clone https://github.com/singularidade-digital-dev/singularidade-data-bridge.git
cd singularidade-data-bridge
mvn -DskipTests package          # target/data-bridge.jar and target/data-bridge-catalog.jar
java -jar target/data-bridge.jar version
```

### Requirements

| | |
|---|---|
| Java runtime | 21+ (Temurin recommended) |
| Disk | ~19 MB per JAR |
| Network | Only what your `--jdbc-url` needs |
| Privileges | Read-only. A database user with SELECT-only grants is the recommended setup. |

Every connection runs with `setReadOnly(true)` (PostgreSQL also gets `BEGIN READ ONLY`).

---

## Quick start

The credentials in the examples (`alice` / `DB_PASSWORD`) are placeholders. Use a read-only database user of your own.

### Extract tables

```bash
export DB_PASSWORD='...'
java -jar data-bridge.jar extract \
  --jdbc-url "jdbc:postgresql://host:5432/mydb?sslmode=require" \
  --user alice --password-env DB_PASSWORD \
  --schema public \
  --table customers --table orders \
  --out ./snapshot/
```

Result: `./snapshot/public.customers/{metadata.json,ddl.sql}` and the same for `orders`. Names are resolved before anything is written: an exact match first, then the database's case for unquoted identifiers (`customers` finds `CUSTOMERS` on Firebird/Oracle). If any table is missing the run fails with exit 11 and lists them all. Use `--tables-file list.txt` for long lists (one name per line, `#` comments).

### Extract every table, ready to commit

```bash
java -jar data-bridge.jar extract-all \
  --jdbc-url "jdbc:firebirdsql://host:3050//data/sgh.fdb?encoding=WIN1252" \
  --user SYSDBA --password-file ~/.secrets/sgh \
  --include-triggers --stable \
  --out ./db-snapshot/
```

Writes one directory per table plus `./db-snapshot/_index.json`. With `--stable`, two runs against an unchanged database produce byte-identical files — see [Stable output](#stable-output---stable).

### List tables

```bash
java -jar data-bridge.jar list-tables --jdbc-url "jdbc:postgresql://host/mydb" --user alice --password-env DB_PASSWORD --schema public
```

Prints a JSON array on stdout (tables only, no views).

### Run a free-form SELECT

```bash
java -jar data-bridge.jar query \
  --jdbc-url "jdbc:postgresql://host:5432/mydb" --user alice --password-env DB_PASSWORD \
  --sql "SELECT id, name FROM customers WHERE created_at > now() - interval '7 days' LIMIT 10"
```

Prints `{ "kind": "query", "rowCount": 10, "columns": [...], "rows": [...] }`.

### Run a fixed, parameterized query from a file

```sql
-- dictionary.sql (versioned in your repository)
-- name: tabela
SELECT NOME, MODULO, DESCRICAO FROM TBSGHTABELAS WHERE NOME = :tabela

-- name: colunas
-- official column descriptions
SELECT COLUNA, DESCRICAO FROM TBSGHCOLUNAS WHERE TABELA = :tabela ORDER BY COLUNA;
```

```bash
java -jar data-bridge-catalog.jar run-query \
  --jdbc-url "jdbc:firebirdsql://host:3050//data/sghlog.gdb" --user SYSDBA --password-env SGH_PASSWORD \
  --file dictionary.sql --name colunas --param tabela=PRITENSESQ
```

Same JSON as `query`. Placeholders are bound as JDBC parameters, so values never become SQL text.

---

## CLI reference

### Connection options (every command)

| Flag | Purpose |
|---|---|
| `--jdbc-url` | Required. JDBC URL; the driver is detected from the prefix. |
| `--user` | Database user, when it is not in the URL. |
| `--password-env VAR` | Read the password from environment variable `VAR`. |
| `--password-file PATH` | Read the password from a file. Only a final line break is removed; everything else, spaces included, is part of the password. |

Credentials given as flags are passed to the driver as connection properties. Giving the same credential in the URL **and** in a flag is an error (exit 2), as is giving both `--password-env` and `--password-file`, an unset variable or an empty file. A user in the URL with the password from a flag is fine.

### `extract` — metadata (+ DDL) for one or more tables

```text
data-bridge extract --jdbc-url <url> [credentials] [--schema <name>]
                    (--table <name>)... [--tables-file <path>] --out <dir>
                    [--sample-rows N] [--tsv] [--cardinality-mode exact|approximate|skip]
                    [--column-stats-mode full|histogram-only|off] [--source-url-redaction none|host-port|full]
                    [--include-ddl] [--include-triggers] [--stable] [-q|--quiet] [-v|--verbose]
```

| Flag | Required | Default | Purpose |
|---|---|---|---|
| `--schema` | depends on driver | — | Required for PostgreSQL/Oracle/SQL Server. Ignored on Firebird/MySQL (single-schema). A missing schema is exit 12. |
| `--table` | one of `--table` / `--tables-file` | — | Table or view. Repeatable: `--table A --table B`. One JVM, one connection for all of them. |
| `--tables-file` | | — | File with one table per line; blank lines and `#` comments ignored. Merged with `--table`, duplicates dropped. |
| `--out` | yes | — | Output **parent** directory. Files go under `<out>/<schema>.<table>/` (or `<out>/<table>/` without schema), using the name as stored in the database. |
| `--sample-rows` | no | `0` | Sample row count. **Default 0 = no sample collected.** PII-bearing when on. |
| `--tsv` | no | off | Also emit `columns.tsv`, `fks.tsv`, `indexes.tsv`, `unique-constraints.tsv`, `check-constraints.tsv`, `sample.tsv`, `cardinality.tsv`. |
| `--cardinality-mode` | no | `exact` | `exact`: `COUNT(*)` + `COUNT(DISTINCT col)` per column — slow, authoritative. `approximate`: pre-computed statistics (`pg_class`/`pg_stats` on PG; `RDB$INDICES.RDB$STATISTICS` on Firebird). `skip`: empty cardinality. BLOB / CLOB / BYTEA / text-BLOB columns are always omitted. |
| `--column-stats-mode` | no | `histogram-only` | `full` keeps `mostCommonValues` + `mostCommonFrequencies` (PII risk); `off` empties `columnStats`. |
| `--source-url-redaction` | no | `host-port` | `none` keeps host:port, `host-port` replaces them with `[redacted-host]`, `full` keeps only the JDBC scheme. |
| `--include-ddl` | no | on | Write `ddl.sql` next to `metadata.json`. |
| `--include-triggers` | no | off | Also write `triggers.sql`. |
| `--stable` | no | off | Versionable output (see [Stable output](#stable-output---stable)). Implies no cardinality and no column statistics; cannot be combined with `--sample-rows`. |
| `--no-cardinality` | no | off | Legacy alias for `--cardinality-mode skip`. |
| `-q` / `-v` | no | off | Quiet progress / verbose errors (exception class + stack trace). |

### `extract-all` — every table of a schema

```text
data-bridge extract-all --jdbc-url <url> [credentials] [--schema <name>] --out <dir>
                        [--exclude TABLE]... [--include-views] [same output flags as extract]
```

Runs the `extract` pipeline for every table (sequentially, one connection) and writes `<out>/_index.json`:

```json
{
  "$schema":    "https://singularidade.digital/data-bridge/extract-all-index.v1.json",
  "version":    "1.0",
  "generatedAt": "2026-10-22T12:00:00Z",
  "generator":  { "name": "singularidade-data-bridge", "version": "0.8.0" },
  "source":     { "type": "jdbc", "driver": "postgresql", "url": "jdbc:postgresql://[redacted-host]/mydb", "schema": "public" },
  "tableCount": 12,
  "tables": [ { "schema": "public", "table": "customers", "type": "TABLE", "columnCount": 9, "primaryKey": ["id"], "totalRows": 18432 } ]
}
```

Differences from `extract`: `--schema` is required except on Firebird; `--cardinality-mode` defaults to **`approximate`**; `--exclude` skips tables by exact name (repeatable); `--include-views` adds views and materialized views.

### `query` — execute one SQL statement

```text
data-bridge query --jdbc-url <url> [credentials] --sql "<SQL>"
                  [--limit 100] [--timeout-sec 30] [--writable] [--out file.json] [-q] [-v]
```

The statement guard works on a copy of the SQL in which comments, string literals, quoted identifiers and PostgreSQL dollar-quoted bodies are blanked out — so a keyword or a `;` inside them neither hides a statement nor causes a false alarm:

1. **One statement.** A second statement anywhere is rejected; one trailing `;` is fine.
2. **Allowlisted first keyword.** `SELECT`, `WITH`, `EXPLAIN`, `SHOW`; with `--writable` also `INSERT`, `UPDATE`, `DELETE`, `MERGE`, `UPSERT`, `REPLACE`. DDL keeps its own message; anything else (`EXECUTE BLOCK`, `EXECUTE`, `EXEC`, `CALL`, `DO`, `SET`, ...) is rejected even with `--writable`.
3. **Read-only means read-only.** Without `--writable`, a data-modifying clause anywhere (e.g. `WITH x AS (...) DELETE ...`) is rejected, and on Firebird so are `GEN_ID(g, n)` with `n ≠ 0` and `NEXT VALUE FOR`. Use `GEN_ID(g, 0)` to read a generator.
4. **The connection is read-only too** (`setReadOnly(true)`; PostgreSQL `BEGIN READ ONLY`). Firebird enforces it: a selectable procedure that writes fails.
5. **Recommended on top:** a database user with SELECT-only grants — the only layer that binds against an adversary.

| Flag | Default | Purpose |
|---|---|---|
| `--sql` | — | Required. One statement. |
| `--limit` | `100` | Max rows returned. The driver gets `setMaxRows(limit+1)`, so `truncated=true` tells you more rows exist. |
| `--timeout-sec` | `30` | JDBC query timeout; `0` = none. |
| `--writable` | off | Permit DML. DDL and procedural statements are still rejected. |
| `--out` | stdout | Write the JSON to a file (`-` = stdout). |

Output:

```json
{
  "kind": "query", "rowCount": 1, "truncated": false,
  "columns": [ { "name": "id", "sqlType": "bigint", "jdbcType": -5 } ],
  "rows": [ { "id": 1 } ],
  "executionTimeMs": 13, "warnings": []
}
```

With `--writable` DML the shape is `{ "kind": "update", "updateCount": 5, "executionTimeMs": 22, "warnings": [] }`.

### `run-query` — a named query from a file

```text
data-bridge run-query --jdbc-url <url> [credentials] --file <queries.sql> --name <id>
                      [--param KEY=VALUE]... [--limit 100] [--timeout-sec 30] [--out file.json] [-q] [-v]
```

- **File format:** blocks introduced by a line `-- name: <id>`; comment lines right after it are a description; one statement per block; a trailing `;` is optional. Only comments and blank lines may precede the first block. Duplicate names and empty blocks are errors.
- **Placeholders:** `:key` anywhere in code. Inside string literals, comments and quoted identifiers they are text, and PostgreSQL's `::type` cast is left alone. A name used twice is bound twice.
- **Values** are converted to the type the driver reports for each placeholder (integers, decimals, booleans, `yyyy-mm-dd` dates, `yyyy-mm-dd hh:mm:ss` timestamps, text otherwise). A value that does not convert is exit 2.
- **Always read-only**, through the same guard as `query`. Missing or unknown parameters and unknown names are exit 2 (the error lists the available names).
- Keep the queries file in the consumer's repository: it is reviewed like code, which is what makes it safer than free-form SQL.

### `list-tables`

```text
data-bridge list-tables --jdbc-url <url> [credentials] [--schema <name>] [-v]
```

Prints a JSON array of table names (no views). A missing schema is exit 12.

### `version`

```text
data-bridge version
# → singularidade-data-bridge 0.8.0            (full JAR)
# → singularidade-data-bridge 0.8.0 (catalog)  (catalog JAR)
```

### Exit codes

| Code | Meaning |
|---|---|
| `0` | Success |
| `1` | Unspecified / unclassified error |
| `2` | Invalid arguments: bad flags, conflicting credentials, rejected SQL, bad `run-query` file or parameters |
| `10` | Connection failed (URL, driver, auth, network, TLS) |
| `11` | Table not found — every missing name is listed; nothing is written |
| `12` | Schema not found |
| `13` | Query failed mid-pipeline (timeout, permission, syntax) |
| `14` | Output write failed (disk full, permission) |
| `64` | Unsupported (driver-specific feature unavailable) |

Errors go to **stderr** as one JSON document:

```json
{ "error": { "code": "TABLE_NOT_FOUND",
             "message": "table(s) not found: NAOEXISTE, OUTRA",
             "hint": "Run `data-bridge list-tables` to see the available tables; names match exactly, then in the database's default case" } }
```

---

## Output contract (`metadata.json`)

Contract version **1.1**. Additive changes bump the minor version; removals or renames bump to `2.0`. Consumers must tolerate unknown fields.

```json
{
  "$schema":  "https://singularidade.digital/data-bridge/metadata.v1.json",
  "version":  "1.1",
  "generatedAt": "2026-10-22T15:42:11Z",
  "generator": { "name": "singularidade-data-bridge", "version": "0.8.0" },
  "source":   { "type": "jdbc", "driver": "firebird", "url": "jdbc:firebirdsql://[redacted-host]//data/sgh.fdb",
                "schema": null, "table": "PRITENSESQ" },
  "tableInfo":         { "type": "TABLE", "comment": "...", "owner": null, "approximateRowCount": null, "viewDefinition": null },
  "columns":           [ /* by position — see below */ ],
  "primaryKey":        ["ID"],
  "foreignKeys":       [ /* fkColumns, refTable, refColumns, onUpdate, onDelete, refTableColumns (1-deep snapshot) */ ],
  "indexes":           [ /* columns, ordinalAsc, unique, primary, method, where, expression, active, constraint */ ],
  "uniqueConstraints": [ { "name": "UK_PRITENSESQ_EXT", "columns": ["CODEXT", "EMPRESA"] } ],
  "checkConstraints":  [ { "name": "CK_PRITENSESQ_PERC", "definition": "(PERC BETWEEN 0 AND 100)" } ],
  "triggers":          [ { "name": "TRG_PRITENSESQ_BIU", "timing": "BEFORE", "events": ["INSERT", "UPDATE"],
                           "position": 0, "active": true, "body": "AS\nBEGIN\n ...\nEND",
                           "generators": ["GEN_IDITENSESQ"] } ],
  "sample":            { "rowCount": 0, "rows": [] },
  "columnStats":       [ /* pg_stats; empty on other drivers */ ],
  "cardinality":       { "totalRows": 18432, "perColumn": [ { "name": "ID", "distinctCount": 18432, "nullCount": 0 } ] },
  "partitioning":      { "isPartitioned": false, "strategy": null, "partitionKey": [], "parent": null, "children": [] },
  "warnings":          [ ]
}
```

A column:

```json
{
  "name": "ATIVO", "ordinalPosition": 5,
  "sqlType": "char(1)", "jdbcType": 1, "nullable": false, "primaryKey": false,
  "characterMaxLength": 1, "numericPrecision": null, "numericScale": null,
  "default": "'F'", "comment": null,
  "generated": { "isIdentity": false, "isComputed": false, "generationExpression": null },
  "sequence":  null,
  "collation": "ISO8859_1",
  "nativeType": "CHAR(1)",
  "charset":   "ISO8859_1",
  "domain":    { "name": "DM_FLAG", "notNull": true, "default": "'F'", "check": "(VALUE IN ('T','F'))" }
}
```

Fields added in 1.1 — filled by the Firebird source, `null` / `[]` on other drivers:

| Field | Meaning |
|---|---|
| `columns[].nativeType` | Type exactly as declared: `NUMERIC(15,2)`, `DECIMAL(4,2)`, `VARCHAR(60)` (characters, not bytes), `BLOB SUB_TYPE TEXT`. |
| `columns[].charset` / `collation` | Character set and effective collation of text columns. |
| `columns[].domain` | The named domain behind the column, with its own NOT NULL, DEFAULT and CHECK. `nullable` and `default` remain the effective values. |
| `columns[].sequence` | Generator that fills the column: `{"name", "schema", "source": "identity" or "trigger", "trigger"}`. Only identity columns and triggers that assign `NEW.<col> = GEN_ID(<gen>, ...)` / `NEXT VALUE FOR <gen>` count — never a guess by name. |
| `columns[].generated.generationExpression` | `COMPUTED BY` expression. |
| `indexes[].expression` / `active` / `constraint` | Expression indexes (with `columns: []`), inactive indexes, and the PK/UNIQUE/FK an index backs. |
| `triggers[]` | Timing, events in order, position, active flag, body, and the generators the trigger depends on. |
| `uniqueConstraints[]` / `checkConstraints[]` | Now filled on Firebird (were PostgreSQL-only). |

On Firebird a text BLOB has `sqlType` `text` and other BLOBs `blob`.

BLOB/binary sample cells are normalized to `{ "_blob": true, "size": N, "preview": "<base64 of first 64 bytes>" }`. Files are written atomically (`.metadata.json.partial` then rename).

### Stable output (`--stable`)

For committing snapshots. The same contract, minus what changes between runs or databases:

- removed: `generatedAt`, `generator`, `source.url`, `warnings`, `sample`, `columnStats`, `cardinality`, `tableInfo.approximateRowCount`, `foreignKeys[].refTableColumns`;
- names the database generated (Firebird `INTEG_n`, `RDB$PRIMARYn`, `RDB$FOREIGNn`, `RDB$n`) become `null`;
- every list in a fixed order: columns by position, foreign keys by (refTable, fkColumns), indexes by (name, columns), unique constraints by columns, checks by definition, triggers by (timing, position, name);
- `_index.json` drops `generatedAt`, `generator`, `source.url` and `totalRows`;
- `ddl.sql` / `triggers.sql` headers drop version, source, server and timestamp.

Two runs against an unchanged database produce byte-identical files.

---

## DDL output (`ddl.sql`)

With `--include-ddl` (default) each table directory gets a `ddl.sql` in the database's own dialect, with a provenance header listing what was included and skipped.

| Driver | Source | Notes |
|---|---|---|
| PostgreSQL | `pg_get_indexdef` / `pg_get_constraintdef` + reconstruction | columns, defaults, IDENTITY, PK/UNIQUE inline, CHECK/FK, indexes, comments, views; triggers with `--include-triggers` |
| Firebird | the `RDB$` catalog (same reader as `metadata.json`) | see below — **replays with `isql`** |
| MySQL | `SHOW CREATE TABLE` / `SHOW CREATE VIEW` | |
| Oracle | `DBMS_METADATA.GET_DDL` | |
| SQL Server | placeholder file | |

**Firebird**, in this order:

1. Shared objects the table uses — `CREATE DOMAIN` (with charset, default, NOT NULL, CHECK, collation) and `CREATE SEQUENCE` for generators its triggers use. They are shared by the whole database: create each once when replaying several tables.
2. `CREATE TABLE` — exact types (`NUMERIC(p,s)`, text lengths in characters), domain-based columns by domain name, `CHARACTER SET` when it differs from the database default, `COLLATE` when it differs from the charset default, `COMPUTED BY`, `GENERATED BY DEFAULT AS IDENTITY` (FB3) / `ALWAYS` (FB4+), PK and UNIQUE inline in key order (with `USING [DESC] INDEX` when the index is named differently or descending).
3. `CREATE [UNIQUE] [DESCENDING] INDEX` for indexes that back no constraint (expression indexes as `COMPUTED BY`), then `ALTER INDEX ... INACTIVE` for inactive ones.
4. Foreign keys, with `ON DELETE` / `ON UPDATE` only for CASCADE, SET NULL and SET DEFAULT (`RESTRICT` is Firebird's stored default and is not valid DDL).
5. CHECK constraints; then `COMMENT ON TABLE` / `COLUMN`.

Names the database generated are never written, so replaying lets Firebird assign fresh ones. `triggers.sql` is a `SET TERM ^ ;` block of `CREATE OR ALTER TRIGGER "T" FOR "TABLE" ACTIVE|INACTIVE BEFORE INSERT OR UPDATE POSITION n`. The test suite replays every fixture table into an empty Firebird 3 and Firebird 5 database and requires the copy's catalog to match the original's.

---

## Driver matrix

| Driver | URL prefix | Integration tests | `approximate` cardinality | DDL | Notes |
|---|---|---|---|---|---|
| PostgreSQL | `jdbc:postgresql:` | ✅ Testcontainer (`postgres:16`) | ✅ `pg_class.reltuples` + `pg_stats` | ✅ | floor PG 11; PG 12+ for GENERATED columns |
| Firebird | `jdbc:firebirdsql:` | ✅ Testcontainer (`firebirdsql/firebird:3.0.14`, smoke on `5.0.4`) with an SGH-like schema | ✅ `RDB$INDICES.RDB$STATISTICS` — only **indexed** columns | ✅ replayable | **Firebird 3+**; domains, charsets, triggers, generators in `metadata.json` |
| Oracle | `jdbc:oracle:` | ⚠️ driver-load smoke | ❌ | ✅ `DBMS_METADATA` | floor 12c |
| SQL Server | `jdbc:sqlserver:` | ⚠️ driver-load smoke | ❌ | ⚠️ placeholder | floor 2017 |
| MySQL | `jdbc:mysql:` | ⚠️ driver-load smoke | ❌ | ✅ `SHOW CREATE TABLE` | floor 5.7 |

Where a driver does not expose a feature, the section is empty and a `warnings` entry explains why.

### Operator visibility (PG)

PostgreSQL connections set `application_name=data-bridge/<version>`:

```sql
SELECT application_name, state, query_start FROM pg_stat_activity WHERE application_name LIKE 'data-bridge%';
```

Each invocation uses exactly one connection for the whole run.

---

## Known limitations

- **Cardinality cost.** `--cardinality-mode exact` runs `COUNT(*)` plus `COUNT(DISTINCT col)` per column, sequentially — minutes on large tables. Prefer `approximate` or `skip` (`extract-all` defaults to `approximate`; `--stable` skips it).
- **Generators used only by the application are invisible.** Firebird records which generators a trigger uses, not which ones client code calls. A column filled by `SELECT GEN_ID(...)` in application code has `sequence: null`; a trigger that assigns through a variable or a procedure lists the generator under `triggers[].generators` but links no column.
- **Sample syntax.** `--sample-rows` uses `LIMIT` (PostgreSQL, MySQL) or `FIRST` (Firebird); Oracle and SQL Server are not covered yet.
- **Sample data is opt-in and NOT redacted.** When you pass `--sample-rows N`, `metadata.json` contains real rows, PII included. Keep it off for anything you commit.
- **The SQL guard protects against accidents, not adversaries.** A `SELECT` can still call a function that writes on drivers without read-only transactions. Use a SELECT-only database user for real enforcement.
- **No HTTP daemon.** `serve` was removed in 0.8.0; every command is a one-shot CLI call.

---

## Architecture

```
            ┌────────────────────────┐
            │  CLI (picocli)          │  Main (full) / CatalogMain (no free-form query)
            │  ConnectionOptions,     │
            │  CliErrors, SqlGuard    │
            └───────────┬────────────┘
                        │
            ┌───────────▼────────────┐
            │   MetadataPipeline      │  12 sequential steps
            └───────────┬────────────┘
                        │
            ┌───────────▼────────────┐        ┌──────────────────────┐
            │  Source / JdbcSource    │───────▶│ FirebirdCatalog       │  RDB$ reader → FbTable
            │  + DriverHints          │        └──────────┬───────────┘
            └───────────┬────────────┘                   │
                        │                     ┌──────────▼───────────┐
            ┌───────────▼────────────┐        │ FirebirdDdlBuilder    │  ddl.sql / triggers.sql
            │ JsonWriter / StableOutput│       └──────────────────────┘
            │ TsvWriter / SqlFileWriter│
            └────────────────────────┘
```

The pipeline runs one step per metadata concept (table info, columns, primary key, foreign keys, indexes, unique and check constraints, triggers, sample, column stats, partitioning, cardinality) against a single connection and assembles the `Metadata` record. On Firebird, `FirebirdCatalog` reads the table once and feeds both `metadata.json` and the DDL builder, so the two cannot disagree.

Design history: [`docs/superpowers/specs/`](docs/superpowers/specs/) (original design, and the v0.8.0 Firebird fidelity design) and [`docs/superpowers/plans/`](docs/superpowers/plans/).

---

## Development

```bash
mvn test                 # unit tests + driver-load smoke (no Docker required)
mvn verify               # adds the Postgres + Firebird 3/5 Testcontainer ITs (Docker required)
mvn -DskipTests package  # target/data-bridge.jar and target/data-bridge-catalog.jar
```

Surefire picks up `*Test.java`; Failsafe picks up `*IT.java`. CI runs `mvn -B verify` on every push to `main` and every pull request, and smoke-checks both JARs — see [`.github/workflows/ci.yml`](.github/workflows/ci.yml). Tags `v*` trigger [`.github/workflows/release.yml`](.github/workflows/release.yml), which publishes both JARs and their SHA-256.

### Cutting a release

```bash
scripts/release.sh 0.8.1
```

Bumps `pom.xml`, runs the full test suite, smoke-checks both JARs, commits, tags, pushes and bumps to the next SNAPSHOT. Alternatives, versioning policy and recovery are in [`RELEASING.md`](RELEASING.md).

### Project conventions

- **Java 21, no Lombok**, records for DTOs.
- **Imports at the top.** No inline FQNs except for genuine name collisions.
- **No comments unless they explain a *why* that the code can't.** Names carry the *what*.
- **TDD.** Firebird work is tested against a real Firebird 3 container with an SGH-like schema, including a DDL replay check.

---

## License

[Apache License 2.0](LICENSE) © singularidade.digital
