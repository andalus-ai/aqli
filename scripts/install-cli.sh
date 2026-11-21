#!/usr/bin/env bash
# AQLI CLI installer. The source installer is executed from a complete checkout.
# Usage: scripts/install-cli.sh [install-local-runtime.sh options]

set -euo pipefail

AQLI_REPO="${AQLI_REPO:-andalus-ai/aqli}"
AQLI_BRANCH="${AQLI_BRANCH:-main}"

# When invoked from a checkout, use that checkout directly.
SCRIPT_SOURCE="${BASH_SOURCE[0]:-}"
if [[ -n "$SCRIPT_SOURCE" && -f "$SCRIPT_SOURCE" ]]; then
    SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_SOURCE")" && pwd)"
    if [[ -f "$SCRIPT_DIR/install-local-runtime.sh" && -x "$SCRIPT_DIR/../gradlew" ]]; then
        exec bash "$SCRIPT_DIR/install-local-runtime.sh" "$@"
    fi
fi

for command in curl tar mktemp; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "Required command not found: $command" >&2
        exit 1
    fi
done

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-install.XXXXXX")"
cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

ARCHIVE_URL="https://codeload.github.com/${AQLI_REPO}/tar.gz/refs/heads/${AQLI_BRANCH}"
echo "Downloading AQLI source from ${AQLI_REPO}@${AQLI_BRANCH}..."
curl -fsSL "$ARCHIVE_URL" | tar -xz -C "$TMP_DIR" --strip-components=1

if [[ ! -x "$TMP_DIR/gradlew" || ! -f "$TMP_DIR/scripts/install-local-runtime.sh" ]]; then
    echo "The downloaded source archive does not contain the AQLI Gradle wrapper and local installer." >&2
    exit 1
fi

echo "Building and installing AQLI from source..."
bash "$TMP_DIR/scripts/install-local-runtime.sh" "$@"
