# Changelog

All notable changes to this project. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project uses [Semantic Versioning](https://semver.org/) (before 1.0.0, a minor release may break things;
those changes are listed under **Changed** or **Removed**).

## [Unreleased]

## [0.9.0] - 2026-10-22

### Added
- `--none-charset CHARSET` (every command): fallback for Firebird `CHARACTER SET NONE` text that is not valid UTF-8. Default `WIN1252`; Firebird or Java charset names.
- Release supply chain: a CycloneDX SBOM (`data-bridge-vX.Y.Z.cdx.json`) and build-provenance plus SBOM attestations for both JARs (`gh attestation verify`).
- Dependabot for Maven and GitHub Actions; `SECURITY.md`; this changelog.

### Changed
- Firebird connections without a character set in the URL now use `encoding=UTF8`. Before, Jaybird decoded `CHARACTER SET NONE` text with the JVM's default charset, so the same database could give different output on different machines.
- Firebird `CHARACTER SET NONE` text is decoded by a fixed rule: UTF-8 when the stored bytes are valid UTF-8, otherwise `--none-charset`. Bytes with no character in the fallback keep their value (U+0080–U+009F), so nothing is lost. With an explicit `encoding` in the URL, Jaybird keeps decoding NONE text with that charset.
- JSON output always uses `\n` line breaks (Jackson used the platform separator).

## [0.8.0] - 2026-10-22

### Added
- Firebird metadata from the catalog: UNIQUE and CHECK constraints, `triggers[]`, and per column `nativeType`, `charset`, `collation`, `domain` and `sequence` (identity or trigger-assigned generator). Contract `1.1`.
- `extract` takes several tables (`--table` repeatable, `--tables-file`); names resolve before anything is written.
- `--stable` output for versioning in git (byte-identical across runs).
- Credentials outside the URL: `--user`, `--password-env`, `--password-file`.
- `run-query`: named, parameterized queries from a file.
- `data-bridge-catalog.jar`: the CLI without the free-form `query` command.

### Changed
- Firebird `ddl.sql` rewritten from the catalog and replayable with `isql` (NUMERIC precision, key order, domains, sequences, DESC/expression indexes, no `RESTRICT`); `triggers.sql` carries event, state and position.
- `query` guard runs on SQL with comments and literals blanked out; allowlisted first keyword; `EXECUTE`/`CALL`/`DO`/`SET` rejected even with `--writable`; read-only rejects data-modifying CTEs and Firebird generator increments.
- Missing table is exit 11, missing schema exit 12. Firebird text BLOBs report `sqlType: text`.
- Dependencies without known CVEs: pgjdbc 42.7.14, Jaybird 6.0.7, mssql-jdbc 12.10.2, mysql-connector-j 9.7.0, Jackson 2.18.12.

### Removed
- `serve` and the HTTP API (with Javalin, Jetty 11 and HikariCP).

### Fixed
- `--sample-rows` on Firebird (`SELECT FIRST n`).

## [0.7.2] - 2026-10-03

### Changed
- `serve` binds to loopback by default; `--host` to choose another interface.

## [0.7.1] - 2026-05-12

### Fixed
- `extract-all --schema` is optional on Firebird; UNIQUE/CHECK queries no longer hit `information_schema` on other drivers.

## [0.7.0] - 2026-05-11

### Added
- `ddl.sql` per table (`--include-ddl`, on by default) and `triggers.sql` (`--include-triggers`) for PostgreSQL, MySQL, Oracle and Firebird; MSSQL placeholder.
- `--column-stats-mode` and `--source-url-redaction`.

### Changed
- `columnStats` default `histogram-only`; `source.url` default `host-port` redaction.
- Fewer PostgreSQL queries per table; PostgreSQL sessions run in `BEGIN READ ONLY`.

## [0.6.0] - 2026-05-11

### Added
- Firebird approximate cardinality from index statistics.
- PostgreSQL connections set `application_name=data-bridge/<version>`.

### Fixed
- Crash parsing malformed PostgreSQL arrays in column statistics.

## [0.5.0] - 2026-05-11

### Added
- `--cardinality-mode exact|approximate|skip`; `extract-all` defaults to `approximate`.

## [0.4.0] - 2026-05-11

### Added
- `query` command (and `POST /v1/query`): one SQL statement, read-only unless `--writable`.

## [0.3.0] - 2026-05-11

### Fixed
- CI smoke check no longer hardcodes the version.

## [0.2.0] - 2026-05-10

### Added
- `extract-all` with `_index.json`, `--include-views` and `--exclude`.

### Changed
- Output nested as `<schema>.<table>/`; `--sample-rows` defaults to 0.

## [0.1.0] - 2026-05-10

### Added
- First release: `extract`, `list-tables`, `serve` (HTTP); `metadata.json` and TSV output; PostgreSQL, Firebird, Oracle, SQL Server and MySQL drivers in one JAR.

[Unreleased]: https://github.com/singularidade-digital-dev/singularidade-data-bridge/compare/v0.9.0...HEAD
[0.9.0]: https://github.com/singularidade-digital-dev/singularidade-data-bridge/compare/v0.8.0...v0.9.0
[0.8.0]: https://github.com/singularidade-digital-dev/singularidade-data-bridge/compare/v0.7.2...v0.8.0
[0.7.2]: https://github.com/singularidade-digital-dev/singularidade-data-bridge/compare/v0.7.1...v0.7.2
[0.7.1]: https://github.com/singularidade-digital-dev/singularidade-data-bridge/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/singularidade-digital-dev/singularidade-data-bridge/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/singularidade-digital-dev/singularidade-data-bridge/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/singularidade-digital-dev/singularidade-data-bridge/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/singularidade-digital-dev/singularidade-data-bridge/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/singularidade-digital-dev/singularidade-data-bridge/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/singularidade-digital-dev/singularidade-data-bridge/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/singularidade-digital-dev/singularidade-data-bridge/releases/tag/v0.1.0
