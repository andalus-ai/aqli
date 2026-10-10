#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT_DIR/scripts/mobile-edge-verify-args.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-mobile-edge-verify-args.XXXXXX")"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

require_output() {
  local name="$1"
  local actual="$TMP_DIR/${name}.actual"
  local expected="$TMP_DIR/${name}.expected"

  shift
  "$@" >"$actual"

  if ! diff -u "$expected" "$actual"; then
    echo "Mobile edge verify args case failed: $name" >&2
    exit 1
  fi
}

DEFAULT_EXPECTED="$TMP_DIR/default.expected"
cat >"$DEFAULT_EXPECTED" <<'EOF_DEFAULT'
--skip-bundle-audit
--skip-android
--require-capability-assets
--require-mobile-runtime
--require-format
gguf
--require-format
litert
--require-format
onnx
--require-format
safetensor
--require-feature
text
--require-android-abi
arm64-v8a
--require-linked-runtime
litert
--require-linked-runtime
onnx
EOF_DEFAULT
require_output default \
  env -i PATH="$PATH" FORMAT_TARGETS="gguf, litert, onnx, safetensor" bash "$SCRIPT"

EDGE_DIR="$TMP_DIR/edge models"
EDGE_EXPECTED="$TMP_DIR/edge-models.expected"
cat >"$EDGE_EXPECTED" <<EOF_EDGE
--skip-bundle-audit
--skip-android
--require-capability-assets
--require-mobile-runtime
--require-format
gguf
--require-format
litert
--require-format
onnx
--require-format
safetensor
--require-feature
text
--require-android-abi
arm64-v8a
--require-linked-runtime
litert
--require-linked-runtime
onnx
--edge-model-dir
$EDGE_DIR
--require-edge-model-dir
--require-edge-model-format
litert
--require-edge-model-format
onnx
EOF_EDGE
require_output edge-models \
  env -i PATH="$PATH" \
    FORMAT_TARGETS="gguf,litert,onnx,safetensor" \
    AQLI_EDGE_DIR="$EDGE_DIR" \
    AQLI_EDGE_VERIFY_EDGE_MODELS=true \
    bash "$SCRIPT"

CUSTOM_EDGE_DIR="$TMP_DIR/custom edge"
CUSTOM_EXPECTED="$TMP_DIR/custom-edge.expected"
cat >"$CUSTOM_EXPECTED" <<EOF_CUSTOM
--edge-model-dir
$CUSTOM_EDGE_DIR
--require-edge-model-dir
--require-edge-model-format
onnx
--summary-json
/private/tmp/aqli-edge-verify-summary.json
EOF_CUSTOM
require_output custom-edge \
  env -i PATH="$PATH" \
    AQLI_EDGE_VERIFY_ANDROID=true \
    AQLI_EDGE_VERIFY_BUNDLE_AUDIT=true \
    AQLI_EDGE_VERIFY_CAPABILITIES=false \
    AQLI_EDGE_VERIFY_EDGE_MODEL_DIR="$CUSTOM_EDGE_DIR" \
    AQLI_EDGE_VERIFY_REQUIRED_EDGE_MODEL_FORMATS=onnx \
    AQLI_EDGE_VERIFY_ARGS="--summary-json /private/tmp/aqli-edge-verify-summary.json" \
    bash "$SCRIPT"

printf 'Mobile edge verify args test passed\n'
