# Releasing

Cutting a release of `singularidade-data-bridge` means: tagging a commit on `main` with `vX.Y.Z`, building a fat JAR from that tag, and publishing it as a GitHub Release. The CI workflow (`.github/workflows/release.yml`) does the build and publish automatically — your job is to produce a valid tag.

There are three ways to produce the tag, in increasing order of automation. Pick whichever matches your context.

---

## TL;DR

```bash
scripts/release.sh 0.2.0          # most cases
```

That's it. The script bumps `pom.xml`, runs the full test suite, commits, tags, pushes, and bumps to the next dev SNAPSHOT. The tag push triggers `release.yml`, which builds and publishes the GitHub Release.

---

## Path 1 — `scripts/release.sh` (recommended)

The local helper script. Most correct path because it (a) blocks on a full `mvn verify`, (b) keeps `pom.xml` in sync with the released version, and (c) automatically prepares the next development cycle.

### Usage

```bash
scripts/release.sh <version> [next-snapshot]
```

| Argument | Required | Default | Example |
|---|---|---|---|
| `<version>` | yes | — | `0.2.0` (no `v` prefix) |
| `[next-snapshot]` | no | `<version>` with patch+1 + `-SNAPSHOT` | `0.3.0-SNAPSHOT` |

### What it does

1. Validates: clean working tree, on `main`, in sync with `origin/main`, target tag does not exist locally or on origin.
2. Confirms the plan with you (`y/N` prompt).
3. Bumps `pom.xml` from current `-SNAPSHOT` to release version via `mvn versions:set`.
4. Runs `mvn -B verify` — if any test fails, the release aborts and `pom.xml` is restored.
5. Smoke-checks the fat JAR by running `data-bridge version` and asserting the expected output.
6. Commits the bump as `chore: release v<version>`.
7. Tags `v<version>` on that commit.
8. Bumps `pom.xml` to the next-snapshot version.
9. Commits as `chore: bump to <next-snapshot> for next dev cycle`.
10. Pushes `main` (2 new commits) and the tag.

The tag push triggers [`.github/workflows/release.yml`](.github/workflows/release.yml), which rebuilds the JAR, computes its SHA-256, and creates a GitHub Release with both files attached and auto-generated release notes.

### Dry run

```bash
DRY_RUN=1 scripts/release.sh 0.2.0
```

Goes through every step except the final `git push`. Lets you inspect the local state with `git log --oneline -3` and `git show v0.2.0` before publishing. Either push manually (`git push origin main && git push origin v0.2.0`) or `git reset --hard origin/main && git tag -d v0.2.0` to discard.

---

## Path 2 — `workflow_dispatch` button (release without local Maven)

[`release.yml`](.github/workflows/release.yml) accepts manual triggers from the GitHub UI. Useful when you don't have a local Java/Maven setup or when releasing from a non-developer machine.

1. Go to **Actions → Release** on GitHub.
2. Click **Run workflow**.
3. Pick the branch (`main`).
4. Enter the **version** as `vX.Y.Z` (with the `v` prefix this time).
5. Click **Run workflow**.

The workflow:

1. Validates the version format.
2. Tags the current `main` HEAD with that version.
3. Pushes the tag.
4. Builds + verifies + publishes the release.

> **Trade-off:** This path does **not** update `pom.xml`. The released JAR's MANIFEST will say `<version>-SNAPSHOT` (whatever `pom.xml` currently has). The CLI prints the version from `build-info.properties`, which is generated from `pom.xml` at build time, so `data-bridge version` will say `<version>-SNAPSHOT` too. For a clean release, prefer Path 1, or push a `pom.xml` bump commit before clicking the button.

---

## Path 3 — manual `git tag` (escape hatch)

If you've already updated `pom.xml` and committed it (or you know what you're doing):

```bash
git tag -a v0.2.0 -m 'Release v0.2.0'
git push origin v0.2.0
```

The tag must match `vMAJOR.MINOR.PATCH[-suffix]` — `release.yml` rejects anything else.

---

## Versioning policy

`singularidade-data-bridge` follows [Semantic Versioning 2.0](https://semver.org). Bump:

| Component | When |
|---|---|
| **MAJOR** (`X.y.z → (X+1).0.0`) | Backwards-incompatible change to the JSON output contract (§5 of the design spec), CLI surface, or HTTP API. The contract `version` field also bumps. |
| **MINOR** (`x.Y.z → x.(Y+1).0`) | Additive feature: new field in the JSON contract, new CLI subcommand or flag, new HTTP endpoint, support for a new driver. Backward-compatible — existing consumers keep working. |
| **PATCH** (`x.y.Z → x.y.(Z+1)`) | Bug fix, perf improvement, dependency bump that doesn't change behavior. |
| **`-SNAPSHOT`** | Always trails the next intended release in `pom.xml` on `main`. Never released. |

Pre-1.0.0 caveat: until `1.0.0`, MINOR bumps may include breaking changes (semver permits this). Document loudly in the commit + release notes when this happens. Aim to get to `1.0.0` once the JSON contract has had a real consumer in production.

---

## Recovery — something went wrong

### Pre-push (the script aborted, or `DRY_RUN=1`)

Local state to clean up:

```bash
git tag -d v<version>                  # if a tag was created
git reset --hard origin/main           # if commits were created
```

### Post-push (tag pushed but `release.yml` failed)

The tag exists on origin but no GitHub Release was created. Options:

```bash
# 1. Re-run the release workflow on the same tag (Actions → Release → Re-run all jobs).
# 2. If the tag itself is wrong: delete it locally and on origin, fix, retry.
git push --delete origin v<version>
git tag -d v<version>
# then run scripts/release.sh again with a different version (don't reuse a published tag)
```

### Post-release (GitHub Release exists but is broken)

Mark the release as **draft** or **pre-release** on GitHub, ship a patch (`X.Y.(Z+1)`), and use the new release notes to acknowledge the broken artifact. Don't delete the bad release if anyone might have downloaded it — leave a tombstone.

---

## What gets published

For a release `vX.Y.Z`, the GitHub Release page will host:

| Asset | Size | Purpose |
|---|---|---|
| `data-bridge-vX.Y.Z.jar` | ~80 MB | Fat JAR with all 5 JDBC drivers shaded in. Download this. |
| `data-bridge-vX.Y.Z.jar.sha256` | small | SHA-256 of the JAR. Verify with `sha256sum -c data-bridge-vX.Y.Z.jar.sha256`. |
| Auto-generated release notes | — | List of merged PRs / commits since the previous tag. |

Consumers download via the GitHub Releases API (see the [README install section](README.md#option-a--download-the-release-jar-recommended)) or directly from the release page.

---

## Post-release checklist

- [ ] GitHub Release page shows the new tag, JAR, and SHA-256.
- [ ] `release.yml` run is green.
- [ ] `mvn -DskipTests dependency:tree` against the released artifact (if depended on by another project) resolves cleanly.
- [ ] If a downstream consumer (e.g. `mcp-tool-from-table-orgen-ai`) pins to a specific version, bump its pin in a follow-up commit.
- [ ] Update `MEMORY.md` / project notes if the release introduced anything callers should know about.
