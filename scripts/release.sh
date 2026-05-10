#!/usr/bin/env bash
#
# Cut a release of singularidade-data-bridge.
#
# Usage:
#   scripts/release.sh <version> [next-snapshot]
#
# Examples:
#   scripts/release.sh 0.2.0                  # next dev defaults to 0.2.1-SNAPSHOT
#   scripts/release.sh 0.2.0 0.3.0-SNAPSHOT   # explicit next dev version
#   DRY_RUN=1 scripts/release.sh 0.2.0        # do everything except git push
#
# What it does, in order:
#   1. Sanity-checks: clean working tree, on main, in sync with origin/main.
#   2. Bumps pom.xml from <X-SNAPSHOT> to <version>.
#   3. Runs `mvn -B verify` (release-blocking — fails the run if tests don't pass).
#   4. Commits the bump.
#   5. Tags v<version>.
#   6. Bumps pom.xml to <next-snapshot> (default: patch+1 -SNAPSHOT).
#   7. Commits the next-dev bump.
#   8. Pushes main + the new tag.
#   9. The tag push triggers .github/workflows/release.yml, which builds
#      the fat JAR + sha256 and publishes them as a GitHub Release.
#
# Anything fails => the script exits non-zero with the original error.
# If failure happens AFTER the local tag is created but BEFORE push, you can
# clean up with: `git tag -d v<version> && git reset --hard origin/main`.

set -euo pipefail

# -- args ---------------------------------------------------------------------

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
    echo "usage: $0 <version> [next-snapshot]" >&2
    echo "  version        e.g. 0.2.0  (no leading 'v')" >&2
    echo "  next-snapshot  e.g. 0.2.1-SNAPSHOT  (defaults to <version>+patch+1 -SNAPSHOT)" >&2
    exit 64
fi

VERSION="$1"
NEXT_SNAPSHOT="${2:-}"
TAG="v${VERSION}"

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?$ ]]; then
    echo "error: '$VERSION' is not a valid semver (X.Y.Z[-suffix])" >&2
    exit 64
fi
if [[ "$VERSION" == *-SNAPSHOT ]]; then
    echo "error: release version must not end in -SNAPSHOT" >&2
    exit 64
fi

# Compute default next-snapshot = bump patch
if [ -z "$NEXT_SNAPSHOT" ]; then
    IFS='.' read -r MAJ MIN PATCH <<< "${VERSION%%-*}"
    NEXT_SNAPSHOT="${MAJ}.${MIN}.$((PATCH + 1))-SNAPSHOT"
fi
if ! [[ "$NEXT_SNAPSHOT" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.]+)?-SNAPSHOT$ ]] && \
   ! [[ "$NEXT_SNAPSHOT" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]]; then
    echo "error: '$NEXT_SNAPSHOT' is not a valid -SNAPSHOT version" >&2
    exit 64
fi

DRY_RUN="${DRY_RUN:-0}"

# -- locate repo root + sanity checks ----------------------------------------

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

if [ ! -f pom.xml ]; then
    echo "error: pom.xml not found in $REPO_ROOT" >&2
    exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
    echo "error: working tree is not clean. Commit or stash first:" >&2
    git status --short >&2
    exit 1
fi

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "$CURRENT_BRANCH" != "main" ]; then
    echo "error: not on main (currently on '$CURRENT_BRANCH')" >&2
    exit 1
fi

git fetch --quiet origin main
LOCAL="$(git rev-parse @)"
REMOTE="$(git rev-parse '@{u}')"
BASE="$(git merge-base @ '@{u}')"
if [ "$LOCAL" != "$REMOTE" ] && [ "$BASE" != "$REMOTE" ]; then
    echo "error: local main is diverged from origin/main. Pull/rebase first." >&2
    exit 1
fi
if [ "$LOCAL" != "$REMOTE" ]; then
    echo "warn: local main is ahead of origin by $(git rev-list --count "${REMOTE}..@") commit(s)" >&2
fi

if git rev-parse --verify --quiet "refs/tags/${TAG}" >/dev/null; then
    echo "error: tag ${TAG} already exists locally. Pick a different version." >&2
    exit 1
fi
if git ls-remote --tags origin "refs/tags/${TAG}" | grep -q "${TAG}"; then
    echo "error: tag ${TAG} already exists on origin. Pick a different version." >&2
    exit 1
fi

CURRENT_POM_VERSION="$(mvn -B -ntp -q help:evaluate -Dexpression=project.version -DforceStdout)"
echo
echo "============================================================"
echo "  Releasing ${TAG}"
echo "============================================================"
echo "  current pom.xml version : ${CURRENT_POM_VERSION}"
echo "  release version         : ${VERSION}"
echo "  next dev version        : ${NEXT_SNAPSHOT}"
echo "  branch                  : ${CURRENT_BRANCH}"
echo "  HEAD                    : $(git rev-parse --short HEAD)"
echo "  origin                  : $(git remote get-url origin)"
echo "  dry run                 : ${DRY_RUN}"
echo "============================================================"
read -rp "Proceed? [y/N] " ACK
case "$ACK" in
    y|Y|yes|YES) ;;
    *) echo "aborted." >&2; exit 1 ;;
esac

# -- step 1: bump to release version -----------------------------------------

echo
echo "→ bumping pom.xml: ${CURRENT_POM_VERSION} → ${VERSION}"
mvn -B -ntp versions:set -DnewVersion="${VERSION}" -DgenerateBackupPoms=false

# -- step 2: full verify --------------------------------------------------------

echo
echo "→ running mvn -B verify (release-blocking) ..."
mvn -B -ntp verify

# -- step 3: smoke the fat JAR ------------------------------------------------

echo
echo "→ smoke-checking the fat JAR ..."
test -f target/data-bridge.jar
JAR_SAYS="$(java -jar target/data-bridge.jar version)"
EXPECTED="singularidade-data-bridge ${VERSION}"
if [ "$JAR_SAYS" != "$EXPECTED" ]; then
    echo "error: jar reports '$JAR_SAYS', expected '$EXPECTED'" >&2
    git checkout -- pom.xml
    exit 1
fi
echo "  ok: $JAR_SAYS"

# -- step 4: commit + tag -----------------------------------------------------

echo
echo "→ committing and tagging ${TAG} ..."
git add pom.xml
git commit -m "chore: release ${TAG}

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
git tag -a "${TAG}" -m "Release ${TAG}"

# -- step 5: bump to next snapshot -------------------------------------------

echo
echo "→ bumping pom.xml: ${VERSION} → ${NEXT_SNAPSHOT}"
mvn -B -ntp versions:set -DnewVersion="${NEXT_SNAPSHOT}" -DgenerateBackupPoms=false
git add pom.xml
git commit -m "chore: bump to ${NEXT_SNAPSHOT} for next dev cycle

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"

# -- step 6: push -------------------------------------------------------------

if [ "$DRY_RUN" = "1" ]; then
    echo
    echo "DRY_RUN=1 — skipping push. Would have pushed:"
    echo "  - branch main with 2 new commits"
    echo "  - tag ${TAG}"
    echo "Inspect with: git log --oneline -3 ; git show ${TAG}"
    echo "To complete manually:  git push origin main && git push origin ${TAG}"
    exit 0
fi

echo
echo "→ pushing main + tag ${TAG} ..."
git push origin main
git push origin "${TAG}"

REPO_HTTPS="$(git remote get-url origin | sed -e 's#git@github\.com:#https://github.com/#' -e 's#\.git$##')"
echo
echo "============================================================"
echo "  ✓ Released ${TAG}"
echo "============================================================"
echo "  Tag:      ${REPO_HTTPS}/releases/tag/${TAG}"
echo "  Workflow: ${REPO_HTTPS}/actions"
echo
echo "  Watch the release.yml run; the GitHub Release will appear"
echo "  at the URL above as soon as the workflow finishes."
echo "============================================================"
