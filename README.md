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
