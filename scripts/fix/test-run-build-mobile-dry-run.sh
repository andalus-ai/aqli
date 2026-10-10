#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-build-mobile-dry-run.XXXXXX")"
OUTPUT="$TMP_DIR/output.txt"
CLI_OUTPUT="$TMP_DIR/cli-output.txt"
VERIFY_ONLY_OUTPUT="$TMP_DIR/verify-only-output.txt"
PLUGIN_DIR="$TMP_DIR/aqli_edge"
EDGE_DIR="$TMP_DIR/edge models"
CLI_PLUGIN_DIR="$TMP_DIR/aqli_edge_cli"
CLI_EDGE_DIR="$TMP_DIR/cli edge models"
CLI_SUMMARY_JSON="$TMP_DIR/cli-summary.json"
VERIFY_ONLY_PLUGIN_DIR="$TMP_DIR/aqli_edge_verify_only"
VERIFY_ONLY_EDGE_DIR="$TMP_DIR/verify only edge models"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mkdir -p "$PLUGIN_DIR" "$EDGE_DIR" "$CLI_PLUGIN_DIR" "$CLI_EDGE_DIR" "$VERIFY_ONLY_PLUGIN_DIR" "$VERIFY_ONLY_EDGE_DIR"

require_output_contains() {
  local file="$1"
  local expected="$2"
  if ! grep -Fq "$expected" "$file"; then
    echo "Missing expected output: $expected" >&2
    echo "Captured output:" >&2
    cat "$file" >&2
    exit 1
  fi
}

require_env_output_contains() {
  local expected="$1"
  require_output_contains "$OUTPUT" "$expected"
}

require_cli_output_contains() {
  local expected="$1"
  require_output_contains "$CLI_OUTPUT" "$expected"
}

require_verify_only_output_contains() {
  local expected="$1"
  require_output_contains "$VERIFY_ONLY_OUTPUT" "$expected"
}

require_verify_only_output_not_contains() {
  local unexpected="$1"
  if grep -Fq "$unexpected" "$VERIFY_ONLY_OUTPUT"; then
    echo "Unexpected output: $unexpected" >&2
    echo "Captured output:" >&2
    cat "$VERIFY_ONLY_OUTPUT" >&2
    exit 1
  fi
}

AQLI_MOBILE_BUILD_DRY_RUN=true \
MOBILE_PROFILE=true \
RUNTIME_TARGET=mobile \
MOBILE_PLUGIN_DIR="$PLUGIN_DIR" \
MOBILE_FORMAT_TARGETS="litert,onnx" \
MOBILE_FEATURES="text,vision" \
AQLI_EDGE_DIR="$EDGE_DIR" \
AQLI_EDGE_VERIFY_EDGE_MODELS=true \
  bash "$ROOT_DIR/scripts/build-mobile.sh" >"$OUTPUT"

require_env_output_contains "commands will be printed instead of executed"
require_env_output_contains "dry-run: native-runtime [bash] [$ROOT_DIR/scripts/build-mobile-runtime.sh]"
require_env_output_contains "dry-run: gradle [./gradlew]"
require_env_output_contains "dry-run: verify-mobile-edge-example [AQLI_MOBILE_PLUGIN_DIR=$PLUGIN_DIR] [$ROOT_DIR/scripts/verify-mobile-edge-example.sh]"
require_env_output_contains "[--edge-model-dir] [$EDGE_DIR]"
require_env_output_contains "[--require-edge-model-dir]"
require_env_output_contains "[--require-edge-model-format] [litert]"
require_env_output_contains "[--require-edge-model-format] [onnx]"
require_env_output_contains "[--require-linked-runtime] [litert]"
require_env_output_contains "[--require-linked-runtime] [onnx]"

MOBILE_PROFILE=true \
RUNTIME_TARGET=mobile \
MOBILE_PLUGIN_DIR="$CLI_PLUGIN_DIR" \
MOBILE_FORMAT_TARGETS="litert,onnx" \
MOBILE_FEATURES="text,vision" \
  bash "$ROOT_DIR/scripts/build-mobile.sh" \
    --dry-run \
    --edge-model-dir "$CLI_EDGE_DIR" \
    --require-edge-models \
    --require-edge-model-format litert \
    --require-edge-model-format onnx \
    --summary-json "$CLI_SUMMARY_JSON" \
    >"$CLI_OUTPUT"

require_cli_output_contains "Edge model directory:"
require_cli_output_contains "$CLI_EDGE_DIR"
require_cli_output_contains "commands will be printed instead of executed"
require_cli_output_contains "dry-run: verify-mobile-edge-example [AQLI_MOBILE_PLUGIN_DIR=$CLI_PLUGIN_DIR] [AQLI_EDGE_VERIFY_SUMMARY_JSON=$CLI_SUMMARY_JSON] [$ROOT_DIR/scripts/verify-mobile-edge-example.sh]"
require_cli_output_contains "[--edge-model-dir] [$CLI_EDGE_DIR]"
require_cli_output_contains "[--require-edge-model-dir]"
require_cli_output_contains "[--require-edge-model-format] [litert]"
require_cli_output_contains "[--require-edge-model-format] [onnx]"

RUNTIME_TARGET=mobile \
MOBILE_PLUGIN_DIR="$VERIFY_ONLY_PLUGIN_DIR" \
MOBILE_FORMAT_TARGETS="litert,onnx" \
MOBILE_FEATURES="text,vision" \
  bash "$ROOT_DIR/scripts/build-mobile.sh" \
    --verify-only \
    --dry-run \
    --edge-model-dir "$VERIFY_ONLY_EDGE_DIR" \
    --require-edge-models \
    >"$VERIFY_ONLY_OUTPUT"

require_verify_only_output_contains "Verify only:"
require_verify_only_output_contains "skipped native mobile runtime and bundle audit"
require_verify_only_output_contains "skipped Gradle packaging"
require_verify_only_output_contains "dry-run: verify-mobile-edge-example [AQLI_MOBILE_PLUGIN_DIR=$VERIFY_ONLY_PLUGIN_DIR] [$ROOT_DIR/scripts/verify-mobile-edge-example.sh]"
require_verify_only_output_contains "[--edge-model-dir] [$VERIFY_ONLY_EDGE_DIR]"
require_verify_only_output_contains "[--require-edge-model-dir]"
require_verify_only_output_not_contains "dry-run: native-runtime"
require_verify_only_output_not_contains "dry-run: mobile-bundle-audit"
require_verify_only_output_not_contains "dry-run: gradle"

printf 'Mobile build dry-run test passed\n'
