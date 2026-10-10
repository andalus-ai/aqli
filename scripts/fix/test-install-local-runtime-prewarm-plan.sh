#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-install-prewarm-plan.XXXXXX")"
cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

export HOME="$TMP_DIR/home"
mkdir -p "$HOME/.aqli/models" \
    "$HOME/.aqli/models/blobs/litert-good" \
    "$HOME/.aqli/models/blobs/gguf-good"
touch "$HOME/.aqli/models/blobs/litert-good/gemma-4-E2B-it.litertlm"
touch "$HOME/.aqli/models/blobs/gguf-good/model.gguf"

cat > "$HOME/.aqli/models/index.json" <<JSON
[ {
  "id" : "example/stale-tflite",
  "shortId" : "stale-tflite",
  "name" : "stale-tflite",
  "format" : "litert",
  "runnable" : true,
  "path" : "$HOME/.aqli/models/blobs/stale/model.tflite",
  "source" : "local"
}, {
  "id" : "example/missing-litertlm",
  "shortId" : "missing-lm",
  "name" : "missing-litertlm",
  "format" : "litert",
  "runnable" : true,
  "path" : "$HOME/.aqli/models/blobs/missing/model.litertlm",
  "source" : "local"
}, {
  "id" : "example/litert-good",
  "shortId" : "litert-good",
  "name" : "litert-good",
  "format" : "litert",
  "runnable" : true,
  "path" : "$HOME/.aqli/models/blobs/litert-good",
  "source" : "local"
}, {
  "id" : "example/gguf-good",
  "shortId" : "gguf-good",
  "name" : "gguf-good",
  "format" : "gguf",
  "runnable" : true,
  "path" : "$HOME/.aqli/models/blobs/gguf-good/model.gguf",
  "source" : "local"
}, {
  "id" : "example/non-runnable-gguf",
  "shortId" : "nonrun-gguf",
  "name" : "non-runnable-gguf",
  "format" : "gguf",
  "runnable" : false,
  "path" : "$HOME/.aqli/models/blobs/gguf-good/model.gguf",
  "source" : "local"
} ]
JSON

PLAN="$("$ROOT_DIR/scripts/install-local-runtime.sh" --prewarm-plan auto)"
EXPECTED="$(printf 'litert-good\ngguf-good')"
if [ "$PLAN" != "$EXPECTED" ]; then
    echo "Unexpected auto prewarm plan" >&2
    echo "Expected:" >&2
    printf '%s\n' "$EXPECTED" >&2
    echo "Actual:" >&2
    printf '%s\n' "$PLAN" >&2
    exit 1
fi

WRAPPER_PLAN="$("$ROOT_DIR/scripts/install-local-macos.sh" --prewarm-plan auto)"
if [ "$WRAPPER_PLAN" != "$EXPECTED" ]; then
    echo "Unexpected macOS wrapper auto prewarm plan" >&2
    echo "Expected:" >&2
    printf '%s\n' "$EXPECTED" >&2
    echo "Actual:" >&2
    printf '%s\n' "$WRAPPER_PLAN" >&2
    exit 1
fi

LIMIT_ONE_PLAN="$(AQLI_INSTALL_PREWARM_AUTO_LIMIT=1 "$ROOT_DIR/scripts/install-local-runtime.sh" --prewarm-plan auto)"
if [ "$LIMIT_ONE_PLAN" != "litert-good" ]; then
    echo "Expected auto prewarm limit override to select the first fast model only" >&2
    echo "Expected:" >&2
    printf '%s\n' "litert-good" >&2
    echo "Actual:" >&2
    printf '%s\n' "$LIMIT_ONE_PLAN" >&2
    exit 1
fi

GGUF_PLAN="$("$ROOT_DIR/scripts/install-local-runtime.sh" --prewarm-plan auto:gguf)"
if [ "$GGUF_PLAN" != "gguf-good" ]; then
    echo "Expected auto:gguf to select only gguf-good, got: $GGUF_PLAN" >&2
    exit 1
fi

LITERT_PLAN="$("$ROOT_DIR/scripts/install-local-runtime.sh" --prewarm-plan auto:litert)"
if [ "$LITERT_PLAN" != "litert-good" ]; then
    echo "Expected auto:litert to select only litert-good, got: $LITERT_PLAN" >&2
    exit 1
fi

echo "install-local-runtime prewarm plan test passed"
