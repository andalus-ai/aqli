#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-build-mobile-verify-only.XXXXXX")"
TMP_DIR="$(cd "$TMP_DIR" && pwd -P)"
OUTPUT="$TMP_DIR/output.txt"
CAPTURE="$TMP_DIR/dart-capture.txt"
PLUGIN_DIR="$TMP_DIR/aqli_edge"
EDGE_DIR="$TMP_DIR/edge models"
SUMMARY_JSON="$TMP_DIR/verify-summary.json"
FAKE_DART="$TMP_DIR/fake-dart"
FAKE_FLUTTER="$TMP_DIR/flutter/bin/flutter"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mkdir -p "$PLUGIN_DIR/.dart_tool" "$PLUGIN_DIR/tool" "$EDGE_DIR" "$(dirname "$FAKE_FLUTTER")"
printf '{"configVersion":2,"packages":[]}\n' >"$PLUGIN_DIR/.dart_tool/package_config.json"
touch "$PLUGIN_DIR/tool/verify_example.dart" "$FAKE_FLUTTER"

cat >"$FAKE_DART" <<'EOF_FAKE_DART'
#!/usr/bin/env bash
{
  printf 'pwd=%s\n' "$PWD"
  printf 'flutter=%s\n' "${AQLI_EDGE_FLUTTER_BIN:-}"
  printf 'args='
  for arg in "$@"; do
    printf '[%s]' "$arg"
  done
  printf '\n'
} >>"$CAPTURE"
EOF_FAKE_DART
chmod +x "$FAKE_DART"

require_file_contains() {
  local file="$1"
  local expected="$2"
  if ! grep -Fq "$expected" "$file"; then
    echo "Missing expected output: $expected" >&2
    echo "Captured output:" >&2
    cat "$file" >&2
    exit 1
  fi
}

require_file_not_contains() {
  local file="$1"
  local unexpected="$2"
  if grep -Fq "$unexpected" "$file"; then
    echo "Unexpected output: $unexpected" >&2
    echo "Captured output:" >&2
    cat "$file" >&2
    exit 1
  fi
}

CAPTURE="$CAPTURE" \
AQLI_EDGE_DART_BIN="$FAKE_DART" \
AQLI_EDGE_FLUTTER_BIN="$FAKE_FLUTTER" \
RUNTIME_TARGET=mobile \
MOBILE_PLUGIN_DIR="$PLUGIN_DIR" \
MOBILE_FORMAT_TARGETS="litert,onnx" \
MOBILE_FEATURES="text,vision" \
  bash "$ROOT_DIR/scripts/build-mobile.sh" \
    --verify-only \
    --edge-model-dir "$EDGE_DIR" \
    --require-edge-models \
    --require-edge-model-format litert \
    --require-edge-model-format onnx \
    --summary-json "$SUMMARY_JSON" \
    >"$OUTPUT"

require_file_contains "$OUTPUT" "Verify only:"
require_file_contains "$OUTPUT" "skipped native mobile runtime and bundle audit"
require_file_contains "$OUTPUT" "skipped Gradle packaging"
require_file_contains "$OUTPUT" "Verifying Aqli Edge example:"
require_file_not_contains "$OUTPUT" "Building Aqli Edge native runtime"
require_file_not_contains "$OUTPUT" "dry-run:"

require_file_contains "$CAPTURE" "pwd=$PLUGIN_DIR"
require_file_contains "$CAPTURE" "flutter=$FAKE_FLUTTER"
require_file_contains "$CAPTURE" "[--summary-json][$SUMMARY_JSON]"
require_file_contains "$CAPTURE" "[--edge-model-dir][$EDGE_DIR]"
require_file_contains "$CAPTURE" "[--require-edge-model-dir]"
require_file_contains "$CAPTURE" "[--require-edge-model-format][litert]"
require_file_contains "$CAPTURE" "[--require-edge-model-format][onnx]"
require_file_contains "$CAPTURE" "[--require-linked-runtime][litert]"
require_file_contains "$CAPTURE" "[--require-linked-runtime][onnx]"

printf 'Mobile build verify-only test passed\n'
