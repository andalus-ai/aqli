#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-gemma4-litert-smoke-test.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

FAKE_BENCH="$TMP_DIR/fake-bench.sh"
CAPTURE="$TMP_DIR/args.txt"

cat >"$FAKE_BENCH" <<'EOF_FAKE_BENCH'
#!/usr/bin/env bash
printf '%s\n' "$@" >"$CAPTURE"
EOF_FAKE_BENCH
chmod +x "$FAKE_BENCH"

CAPTURE="$CAPTURE" AQLI_GEMMA4_LITERT_BENCH="$FAKE_BENCH" \
  bash "$ROOT_DIR/scripts/smoke-gemma4-litert-warm.sh" \
    --aqli-bin /tmp/aqli \
    --warm-threshold-ms 900 \
    --keep-daemon \
    -- --summary-file /tmp/summary.tsv

require_arg() {
  local expected="$1"
  if ! grep -Fxq -- "$expected" "$CAPTURE"; then
    echo "Missing expected argument: $expected" >&2
    echo "Captured args:" >&2
    cat "$CAPTURE" >&2
    exit 1
  fi
}

require_arg "--model"
require_arg "0576e9"
require_arg "--prompt"
require_arg "what is jakarta"
require_arg "--expected"
require_arg "Jakarta|Indonesia"
require_arg "--max-tokens"
require_arg "30"
require_arg "--aqli-bin"
require_arg "/tmp/aqli"
require_arg "--warm-threshold-ms"
require_arg "900"
require_arg "--warm-first-chunk-threshold-ms"
require_arg "250"
require_arg "--warm-profile-total-threshold-ms"
require_arg "1000"
require_arg "--require-metal"
require_arg "--require-warm-engine"
require_arg "--keep-daemon"
require_arg "--summary-file"
require_arg "/tmp/summary.tsv"

echo "PASS: smoke-gemma4-litert-warm forwards strict Gemma4 LiteRT warm defaults"
