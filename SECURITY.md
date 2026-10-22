# Security policy

## Supported versions

Only the latest minor release line gets fixes (currently **0.9.x**). Older versions are not patched — upgrade.

## Reporting a vulnerability

Please report privately through GitHub: **Security → Report a vulnerability**
(<https://github.com/singularidade-digital-dev/singularidade-data-bridge/security/advisories/new>).
Do not open a public issue for a vulnerability.

Include the data-bridge version (`data-bridge version`), the command and flags, the database and driver, and the
steps to reproduce. We aim to acknowledge reports within 5 business days. Confirmed issues are fixed in a patch
release and published as a GitHub Security Advisory, crediting the reporter unless asked otherwise.

## Scope

- The CLI and both release JARs, including the five shaded JDBC drivers and the other bundled libraries.
  Dependabot watches the dependencies weekly.
- The `query` / `run-query` statement guard protects against accidents, not against an adversary: a `SELECT` can
  still call a function that writes on drivers without read-only transactions. For hard guarantees, connect with a
  database user that only has SELECT grants.
- Output files contain what the database returns. Sample rows (`--sample-rows N`) can contain personal data.

## Verifying a release

Every release publishes, for both JARs, a SHA-256 file, a GitHub build-provenance attestation and an SBOM
attestation, plus the CycloneDX SBOM itself (`data-bridge-vX.Y.Z.cdx.json`):

```bash
sha256sum -c data-bridge-vX.Y.Z.jar.sha256
gh attestation verify data-bridge-vX.Y.Z.jar --repo singularidade-digital-dev/singularidade-data-bridge
gh attestation verify data-bridge-vX.Y.Z.jar --repo singularidade-digital-dev/singularidade-data-bridge \
  --predicate-type https://cyclonedx.org/bom
```

The provenance attestation proves the JAR was built by this repository's release workflow from the tagged commit.
