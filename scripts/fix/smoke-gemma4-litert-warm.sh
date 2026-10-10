#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: smoke-gemma4-litert-warm.sh [options] [-- bench-litert-fast-run options]

Run the installed Aqli CLI against the cached Gemma4 mobile QAT -> LiteRT-LM
route and require a real warm daemon cache hit on the measured run.

Options:
  --model ID              Gemma4 model id or alias (default: 0576e9)
  --prompt TEXT           Prompt to run (default: "what is jakarta")
  --expected REGEX        Expected answer regex (default: Jakarta|Indonesia)
  --max-tokens N          Max generated tokens (default: 30)
  --warm-threshold-ms N   Warm measured duration threshold (default: 1000)
  --warm-first-chunk-threshold-ms N
                          Warm profile firstChunk threshold (default: 250)
  --warm-profile-total-threshold-ms N
                          Warm profile total threshold (default: 1000)
  --aqli-bin PATH       Aqli executable (default: ~/.local/bin/aqli)
  --bench PATH            LiteRT bench script path
  --require-metal         Require Metal backend (default)
  --no-require-metal      Do not require Metal backend
  --keep-daemon           Leave the warmed daemon running after smoke
  --help                  Show this help

All arguments after -- are forwarded to bench-litert-fast-run.sh.
USAGE
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BENCH="${AQLI_GEMMA4_LITERT_BENCH:-${SCRIPT_DIR}/bench-litert-fast-run.sh}"
MODEL_ID="${AQLI_GEMMA4_LITERT_SMOKE_MODEL:-0576e9}"
PROMPT="${AQLI_GEMMA4_LITERT_SMOKE_PROMPT:-what is jakarta}"
EXPECTED_REGEX="${AQLI_GEMMA4_LITERT_SMOKE_EXPECTED:-Jakarta|Indonesia}"
MAX_TOKENS="${AQLI_GEMMA4_LITERT_SMOKE_MAX_TOKENS:-30}"
AQLI_BIN="${AQLI_BIN:-${HOME}/.local/bin/aqli}"
WARM_THRESHOLD_MS="${AQLI_GEMMA4_LITERT_WARM_THRESHOLD_MS:-1000}"
WARM_FIRST_CHUNK_THRESHOLD_MS="${AQLI_GEMMA4_LITERT_WARM_FIRST_CHUNK_THRESHOLD_MS:-250}"
WARM_PROFILE_TOTAL_THRESHOLD_MS="${AQLI_GEMMA4_LITERT_WARM_TOTAL_THRESHOLD_MS:-1000}"
KEEP_DAEMON=0
REQUIRE_METAL=1
EXTRA_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model) MODEL_ID="$2"; shift 2 ;;
    --prompt) PROMPT="$2"; shift 2 ;;
    --expected) EXPECTED_REGEX="$2"; shift 2 ;;
    --max-tokens) MAX_TOKENS="$2"; shift 2 ;;
    --warm-threshold-ms) WARM_THRESHOLD_MS="$2"; shift 2 ;;
    --warm-first-chunk-threshold-ms) WARM_FIRST_CHUNK_THRESHOLD_MS="$2"; shift 2 ;;
    --warm-profile-total-threshold-ms) WARM_PROFILE_TOTAL_THRESHOLD_MS="$2"; shift 2 ;;
    --aqli-bin) AQLI_BIN="$2"; shift 2 ;;
    --bench) BENCH="$2"; shift 2 ;;
    --require-metal) REQUIRE_METAL=1; shift ;;
    --no-require-metal) REQUIRE_METAL=0; shift ;;
    --keep-daemon) KEEP_DAEMON=1; shift ;;
    --help) usage; exit 0 ;;
    --) shift; EXTRA_ARGS+=("$@"); break ;;
    *) EXTRA_ARGS+=("$1"); shift ;;
  esac
done

if [[ ! -x "$BENCH" ]]; then
  echo "LiteRT bench script not found or not executable: $BENCH" >&2
  exit 2
fi

BENCH_ARGS=(
  --model "$MODEL_ID"
  --prompt "$PROMPT"
  --expected "$EXPECTED_REGEX"
  --max-tokens "$MAX_TOKENS"
  --aqli-bin "$AQLI_BIN"
  --warm-threshold-ms "$WARM_THRESHOLD_MS"
  --warm-first-chunk-threshold-ms "$WARM_FIRST_CHUNK_THRESHOLD_MS"
  --warm-profile-total-threshold-ms "$WARM_PROFILE_TOTAL_THRESHOLD_MS"
  --require-warm-engine
)

if [[ "$REQUIRE_METAL" -eq 1 ]]; then
  BENCH_ARGS+=(--require-metal)
else
  BENCH_ARGS+=(--no-require-metal)
fi

if [[ "$KEEP_DAEMON" -eq 1 ]]; then
  BENCH_ARGS+=(--keep-daemon)
fi

if [[ "${#EXTRA_ARGS[@]}" -gt 0 ]]; then
  BENCH_ARGS+=("${EXTRA_ARGS[@]}")
fi

exec "$BENCH" "${BENCH_ARGS[@]}"
