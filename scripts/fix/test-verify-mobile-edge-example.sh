#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-verify-mobile-edge-example.XXXXXX")"
TMP_DIR="$(cd "$TMP_DIR" && pwd -P)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

FAKE_DART="$TMP_DIR/fake-dart"
FAKE_FLUTTER="$TMP_DIR/flutter/bin/flutter"
mkdir -p "$(dirname "$FAKE_FLUTTER")"

cat >"$FAKE_DART" <<'EOF_FAKE_DART'
#!/usr/bin/env bash
{
  printf 'pwd=%s\n' "$PWD"
  printf 'home=%s\n' "${HOME:-}"
  printf 'flutter=%s\n' "${AQLI_EDGE_FLUTTER_BIN:-}"
  printf 'suppress=%s\n' "${DART_SUPPRESS_ANALYTICS:-}"
  printf 'args='
  for arg in "$@"; do
    printf '[%s]' "$arg"
  done
  printf '\n'
} >>"$CAPTURE"
exit "${FAKE_DART_EXIT_CODE:-0}"
EOF_FAKE_DART
chmod +x "$FAKE_DART"
touch "$FAKE_FLUTTER"

require_line() {
  local file="$1"
  local expected="$2"
  if ! grep -Fxq "$expected" "$file"; then
    echo "Missing expected line: $expected" >&2
    echo "Captured output:" >&2
    cat "$file" >&2
    exit 1
  fi
}

DIRECT_PLUGIN="$TMP_DIR/plugin-direct"
DIRECT_CAPTURE="$TMP_DIR/direct.txt"
mkdir -p "$DIRECT_PLUGIN/.dart_tool" "$DIRECT_PLUGIN/tool"
printf '{"configVersion":2,"packages":[]}\n' >"$DIRECT_PLUGIN/.dart_tool/package_config.json"
touch "$DIRECT_PLUGIN/tool/verify_example.dart"

CAPTURE="$DIRECT_CAPTURE" \
AQLI_MOBILE_PLUGIN_DIR="$DIRECT_PLUGIN" \
AQLI_EDGE_DART_BIN="$FAKE_DART" \
AQLI_EDGE_FLUTTER_BIN="$FAKE_FLUTTER" \
  bash "$ROOT_DIR/scripts/verify-mobile-edge-example.sh" --dry-run --skip-android

require_line "$DIRECT_CAPTURE" "pwd=$DIRECT_PLUGIN"
require_line "$DIRECT_CAPTURE" "flutter=$FAKE_FLUTTER"
require_line "$DIRECT_CAPTURE" "suppress="
require_line "$DIRECT_CAPTURE" "args=[--packages=$DIRECT_PLUGIN/.dart_tool/package_config.json][$DIRECT_PLUGIN/tool/verify_example.dart][--dry-run][--skip-android]"

SUMMARY_PLUGIN="$TMP_DIR/plugin-summary"
SUMMARY_CAPTURE="$TMP_DIR/summary.txt"
SUMMARY_JSON="$SUMMARY_PLUGIN/build/verify-summary.json"
mkdir -p "$SUMMARY_PLUGIN/.dart_tool" "$SUMMARY_PLUGIN/tool"
printf '{"configVersion":2,"packages":[]}\n' >"$SUMMARY_PLUGIN/.dart_tool/package_config.json"
touch "$SUMMARY_PLUGIN/tool/verify_example.dart"

CAPTURE="$SUMMARY_CAPTURE" \
AQLI_MOBILE_PLUGIN_DIR="$SUMMARY_PLUGIN" \
AQLI_EDGE_DART_BIN="$FAKE_DART" \
AQLI_EDGE_FLUTTER_BIN="$FAKE_FLUTTER" \
AQLI_EDGE_VERIFY_SUMMARY_JSON="$SUMMARY_JSON" \
  bash "$ROOT_DIR/scripts/verify-mobile-edge-example.sh" --dry-run --skip-android

require_line "$SUMMARY_CAPTURE" "pwd=$SUMMARY_PLUGIN"
require_line "$SUMMARY_CAPTURE" "args=[--packages=$SUMMARY_PLUGIN/.dart_tool/package_config.json][$SUMMARY_PLUGIN/tool/verify_example.dart][--summary-json][$SUMMARY_JSON][--dry-run][--skip-android]"

FALLBACK_PLUGIN="$TMP_DIR/plugin-fallback"
FALLBACK_CAPTURE="$TMP_DIR/fallback.txt"
FALLBACK_HOME="$FALLBACK_PLUGIN/.dart_tool/aqli_edge_verify_home"
mkdir -p "$FALLBACK_PLUGIN/tool"
touch "$FALLBACK_PLUGIN/tool/verify_example.dart"

CAPTURE="$FALLBACK_CAPTURE" \
AQLI_MOBILE_PLUGIN_DIR="$FALLBACK_PLUGIN" \
AQLI_EDGE_DART_BIN="$FAKE_DART" \
AQLI_EDGE_FLUTTER_BIN="$FAKE_FLUTTER" \
  bash "$ROOT_DIR/scripts/verify-mobile-edge-example.sh" --dry-run --skip-android

require_line "$FALLBACK_CAPTURE" "pwd=$FALLBACK_PLUGIN"
require_line "$FALLBACK_CAPTURE" "home=$FALLBACK_HOME"
require_line "$FALLBACK_CAPTURE" "flutter=$FAKE_FLUTTER"
require_line "$FALLBACK_CAPTURE" "suppress=true"
require_line "$FALLBACK_CAPTURE" "args=[run][tool/verify_example.dart][--dry-run][--skip-android]"

USAGE_PLUGIN="$TMP_DIR/plugin-usage"
USAGE_CAPTURE="$TMP_DIR/usage.txt"
mkdir -p "$USAGE_PLUGIN/.dart_tool" "$USAGE_PLUGIN/tool"
printf '{"configVersion":2,"packages":[]}\n' >"$USAGE_PLUGIN/.dart_tool/package_config.json"
touch "$USAGE_PLUGIN/tool/verify_example.dart"

set +e
CAPTURE="$USAGE_CAPTURE" \
FAKE_DART_EXIT_CODE=64 \
AQLI_MOBILE_PLUGIN_DIR="$USAGE_PLUGIN" \
AQLI_EDGE_DART_BIN="$FAKE_DART" \
AQLI_EDGE_FLUTTER_BIN="$FAKE_FLUTTER" \
  bash "$ROOT_DIR/scripts/verify-mobile-edge-example.sh" --bad-option >/dev/null 2>&1
usage_status=$?
set -e

if [[ "$usage_status" != "64" ]]; then
  echo "Expected usage errors to exit with 64, got $usage_status" >&2
  cat "$USAGE_CAPTURE" >&2
  exit 1
fi

printf 'Mobile edge example verifier wrapper test passed\n'
