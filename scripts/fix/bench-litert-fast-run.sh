#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: bench-litert-fast-run.sh [options]

Run a focused smoke/benchmark for the installed LiteRT-LM fast path. The check
starts from a cold daemon, verifies the backend, then verifies warm latency.

Options:
  --model ID              Model id or local alias (default: 7c51c9)
  --prompt TEXT           Prompt to run (default: "where is jakarta")
  --expected REGEX        Expected answer regex (default: Jakarta|Indonesia)
  --max-tokens N          Max generated tokens (default: 10)
  --aqli-bin PATH       Aqli executable (default: ~/.local/bin/aqli)
  --warm-threshold-ms N   Fail if warm duration exceeds this (default: 1500)
  --warm-engine-init-threshold-ms N
                          Fail if measured warm profile engineInit exceeds this
  --warm-first-chunk-threshold-ms N
                          Fail if measured warm profile firstChunk exceeds this
  --warm-profile-total-threshold-ms N
                          Fail if measured warm profile total exceeds this
  --cold-threshold-ms N   Fail if cold duration exceeds this (default: 60000)
  --warmup-runs N         Stabilization runs before measured warm check (default: 1)
  --require-metal         Require Metal backend
  --no-require-metal      Do not require Metal backend
  --require-warm-engine   Require daemon engine reuse on warm runs (default)
  --no-require-warm-engine
  --warm-only             Skip daemon reset/cold run and require an already-warm daemon
  --summary-file PATH     Write machine-readable TSV metrics for each case
  --keep-daemon           Leave daemon running after benchmark
  --help                  Show this help
USAGE
}

MODEL_ID="7c51c9"
PROMPT="where is jakarta"
EXPECTED_REGEX="Jakarta|Indonesia"
MAX_TOKENS=10
AQLI_BIN="${HOME}/.local/bin/aqli"
WARM_THRESHOLD_MS=1500
WARM_ENGINE_INIT_THRESHOLD_MS=""
WARM_FIRST_CHUNK_THRESHOLD_MS=""
WARM_PROFILE_TOTAL_THRESHOLD_MS=""
COLD_THRESHOLD_MS=60000
WARMUP_RUNS=1
KEEP_DAEMON=0
REQUIRE_WARM_ENGINE=1
WARM_ONLY=0
SUMMARY_FILE=""
case "$(uname -s)" in
  Darwin) REQUIRE_METAL=1 ;;
  *) REQUIRE_METAL=0 ;;
esac

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model) MODEL_ID="$2"; shift 2 ;;
    --prompt) PROMPT="$2"; shift 2 ;;
    --expected) EXPECTED_REGEX="$2"; shift 2 ;;
    --max-tokens) MAX_TOKENS="$2"; shift 2 ;;
    --aqli-bin) AQLI_BIN="$2"; shift 2 ;;
    --warm-threshold-ms) WARM_THRESHOLD_MS="$2"; shift 2 ;;
    --warm-engine-init-threshold-ms) WARM_ENGINE_INIT_THRESHOLD_MS="$2"; shift 2 ;;
    --warm-first-chunk-threshold-ms) WARM_FIRST_CHUNK_THRESHOLD_MS="$2"; shift 2 ;;
    --warm-profile-total-threshold-ms) WARM_PROFILE_TOTAL_THRESHOLD_MS="$2"; shift 2 ;;
    --cold-threshold-ms) COLD_THRESHOLD_MS="$2"; shift 2 ;;
    --warmup-runs) WARMUP_RUNS="$2"; shift 2 ;;
    --require-metal) REQUIRE_METAL=1; shift ;;
    --no-require-metal) REQUIRE_METAL=0; shift ;;
    --require-warm-engine) REQUIRE_WARM_ENGINE=1; shift ;;
    --no-require-warm-engine) REQUIRE_WARM_ENGINE=0; shift ;;
    --warm-only) WARM_ONLY=1; shift ;;
    --summary-file) SUMMARY_FILE="$2"; shift 2 ;;
    --keep-daemon) KEEP_DAEMON=1; shift ;;
    --help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

for cmd in awk grep mktemp sed tail; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

if [[ ! -x "$AQLI_BIN" ]]; then
  echo "aqli executable not found or not executable: $AQLI_BIN" >&2
  exit 2
fi

RUN_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-litert-fast-bench.XXXXXX")"
if [[ -n "$SUMMARY_FILE" ]]; then
  printf 'case\tdurationMs\tbackend\twarmEngine\tengineInitMs\tfirstChunkMs\ttotalMs\trouteResolveMs\trouteCacheHit\tselectedArtifact\trouteSource\tlog\n' > "$SUMMARY_FILE"
fi
cleanup() {
  if [[ "$KEEP_DAEMON" -ne 1 ]]; then
    "$AQLI_BIN" __daemon-stop >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

strip_ansi() {
  sed -E $'s/\x1B\\[[0-9;?]*[ -/]*[@-~]//g'
}

extract_duration_ms() {
  local file="$1"
  local raw
  raw="$(strip_ansi < "$file" \
    | { grep -oE 'Duration: [0-9]+([,.][0-9]+)?s' || true; } \
    | tail -n 1 \
    | sed -E 's/^Duration: //; s/s$//; s/,/./')"
  if [[ -z "$raw" ]]; then
    echo ""
    return 0
  fi
  awk -v seconds="$raw" 'BEGIN { printf "%.0f", seconds * 1000 }'
}

extract_profile_metric_ms() {
  local file="$1"
  local key="$2"
  strip_ansi < "$file" \
    | { grep -oE "${key}=[0-9]+([.][0-9]+)?s" || true; } \
    | tail -n 1 \
    | sed -E "s/^${key}=//; s/s$//" \
    | awk 'NF { printf "%.0f", $1 * 1000 }'
}

extract_backend() {
  local file="$1"
  strip_ansi < "$file" \
    | { grep -oE 'backend=[^,)]+(\([^)]*\))?' || true; } \
    | head -n 1 \
    | sed 's/^backend=//'
}

is_metal_backend() {
  local backend="$1"
  [[ "$backend" == *Metal* || "$backend" == *MTL* ]]
}

extract_warm_engine() {
  local file="$1"
  strip_ansi < "$file" \
    | { grep -oE 'warmEngine=(true|false)' || true; } \
    | tail -n 1 \
    | sed 's/^warmEngine=//'
}

extract_profile_value() {
  local file="$1"
  local key="$2"
  strip_ansi < "$file" \
    | { grep -oE "${key}=[^[:space:],)]+" || true; } \
    | tail -n 1 \
    | sed -E "s/^${key}=//"
}

metric_exceeds() {
  local actual="$1"
  local limit="$2"
  awk -v actual="$actual" -v limit="$limit" 'BEGIN { exit !(actual + 0 > limit + 0) }'
}

assert_profile_metric_under() {
  local name="$1"
  local label="$2"
  local actual="$3"
  local limit="$4"
  local output="$5"
  if [[ -z "$limit" ]]; then
    return 0
  fi
  if [[ -z "$actual" ]]; then
    echo "FAIL: $name did not report LiteRT profile $label timing" >&2
    tail -n 80 "$output" >&2
    exit 1
  fi
  if metric_exceeds "$actual" "$limit"; then
    echo "FAIL: $name LiteRT profile $label took ${actual}ms, threshold is ${limit}ms" >&2
    tail -n 80 "$output" >&2
    exit 1
  fi
}

summary_field() {
  local value="${1:-}"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

write_summary_row() {
  if [[ -z "$SUMMARY_FILE" ]]; then
    return 0
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$(summary_field "$1")" \
    "$(summary_field "$2")" \
    "$(summary_field "$3")" \
    "$(summary_field "$4")" \
    "$(summary_field "$5")" \
    "$(summary_field "$6")" \
    "$(summary_field "$7")" \
    "$(summary_field "$8")" \
    "$(summary_field "$9")" \
    "$(summary_field "${10}")" \
    "$(summary_field "${11}")" \
    "$(summary_field "${12}")" >> "$SUMMARY_FILE"
}

run_case() {
  local name="$1"
  local output="$RUN_DIR/${name}.log"
  if AQLI_JAVA_OPTS="${AQLI_JAVA_OPTS:-} -Daqli.litert.fast_run.profile=true" \
      "$AQLI_BIN" run \
        --model "$MODEL_ID" \
        --prompt "$PROMPT" \
        --max-tokens "$MAX_TOKENS" \
        --temperature 0 \
        --top-k 1 \
        --top-p 1 >"$output" 2>&1; then
    RUN_CASE_EXIT=0
  else
    RUN_CASE_EXIT=$?
  fi
  RUN_CASE_OUTPUT="$output"
}

assert_case() {
  local name="$1"
  local output="$2"
  local threshold_ms="$3"
  local require_warm_engine="${4:-0}"
  local run_exit="${5:-0}"
  local duration_ms backend engine_ms first_chunk_ms total_ms warm_engine route_ms route_cache_hit selected_artifact route_source

  if [[ "$run_exit" -ne 0 ]]; then
    write_summary_row "$name" "n/a" "unknown" "n/a" "n/a" "n/a" "n/a" "n/a" "n/a" "n/a" "n/a" "$output"
    echo "FAIL: $name command exited with status ${run_exit}" >&2
    tail -n 80 "$output" >&2
    exit "$run_exit"
  fi

  if grep -E 'DISPATCH_OP|GGML_ASSERT|FATAL:|Exception|ERROR:' "$output" >/dev/null; then
    echo "FAIL: $name produced runtime errors" >&2
    tail -n 80 "$output" >&2
    exit 1
  fi
  if ! strip_ansi < "$output" | grep -Eiq "$EXPECTED_REGEX"; then
    echo "FAIL: $name answer did not match expected regex: $EXPECTED_REGEX" >&2
    tail -n 80 "$output" >&2
    exit 1
  fi

  backend="$(extract_backend "$output")"
  if [[ "$REQUIRE_METAL" -eq 1 ]] && ! is_metal_backend "$backend"; then
    echo "FAIL: $name did not use Metal backend (backend=${backend:-missing})" >&2
    tail -n 80 "$output" >&2
    exit 1
  fi

  warm_engine="$(extract_warm_engine "$output")"
  if [[ "$require_warm_engine" -eq 1 && "$REQUIRE_WARM_ENGINE" -eq 1 && "$warm_engine" != "true" ]]; then
    echo "FAIL: $name did not reuse the LiteRT daemon engine (warmEngine=${warm_engine:-missing})" >&2
    tail -n 80 "$output" >&2
    exit 1
  fi

  duration_ms="$(extract_duration_ms "$output")"
  if [[ -z "$duration_ms" ]]; then
    echo "FAIL: $name did not report duration" >&2
    tail -n 80 "$output" >&2
    exit 1
  fi
  if (( duration_ms > threshold_ms )); then
    echo "FAIL: $name took ${duration_ms}ms, threshold is ${threshold_ms}ms" >&2
    tail -n 80 "$output" >&2
    exit 1
  fi

  engine_ms="$(extract_profile_metric_ms "$output" engineInit || true)"
  first_chunk_ms="$(extract_profile_metric_ms "$output" firstChunk || true)"
  total_ms="$(extract_profile_metric_ms "$output" total || true)"
  route_ms="$(extract_profile_metric_ms "$output" routeResolve || true)"
  route_cache_hit="$(extract_profile_value "$output" routeCacheHit || true)"
  selected_artifact="$(extract_profile_value "$output" selectedArtifact || true)"
  route_source="$(extract_profile_value "$output" routeSource || true)"
  if [[ "$name" == "warm" ]]; then
    assert_profile_metric_under "$name" "engineInit" "$engine_ms" "$WARM_ENGINE_INIT_THRESHOLD_MS" "$output"
    assert_profile_metric_under "$name" "firstChunk" "$first_chunk_ms" "$WARM_FIRST_CHUNK_THRESHOLD_MS" "$output"
    assert_profile_metric_under "$name" "total" "$total_ms" "$WARM_PROFILE_TOTAL_THRESHOLD_MS" "$output"
  fi
  write_summary_row \
    "$name" \
    "$duration_ms" \
    "${backend:-unknown}" \
    "${warm_engine:-n/a}" \
    "${engine_ms:-n/a}" \
    "${first_chunk_ms:-n/a}" \
    "${total_ms:-n/a}" \
    "${route_ms:-n/a}" \
    "${route_cache_hit:-n/a}" \
    "${selected_artifact:-n/a}" \
    "${route_source:-n/a}" \
    "$output"
  printf '%-7s duration=%sms backend=%s warmEngine=%s engineInit=%sms firstChunk=%sms total=%sms routeResolve=%sms routeCacheHit=%s selectedArtifact=%s routeSource=%s log=%s\n' \
    "$name" "$duration_ms" "${backend:-unknown}" "${warm_engine:-n/a}" "${engine_ms:-n/a}" "${first_chunk_ms:-n/a}" "${total_ms:-n/a}" "${route_ms:-n/a}" "${route_cache_hit:-n/a}" "${selected_artifact:-n/a}" "${route_source:-n/a}" "$output"
}

if [[ "$WARM_ONLY" -ne 1 ]]; then
  "$AQLI_BIN" __daemon-stop >/dev/null 2>&1 || true

  cold_log=""
  RUN_CASE_OUTPUT=""
  RUN_CASE_EXIT=0
  run_case cold
  cold_log="$RUN_CASE_OUTPUT"
  assert_case cold "$cold_log" "$COLD_THRESHOLD_MS" 0 "$RUN_CASE_EXIT"
fi

for ((i = 1; i <= WARMUP_RUNS; i++)); do
  RUN_CASE_OUTPUT=""
  RUN_CASE_EXIT=0
  run_case "warmup-${i}"
  warmup_log="$RUN_CASE_OUTPUT"
  # Warmup runs are stabilizers: after install or daemon replacement they may
  # be the request that repopulates the engine. The measured warm run below is
  # the hard steady-state daemon-reuse contract.
  warmup_require_warm_engine=0
  if [[ "$WARM_ONLY" -eq 1 ]]; then
    warmup_require_warm_engine=1
  fi
  assert_case "warmup${i}" "$warmup_log" "$COLD_THRESHOLD_MS" "$warmup_require_warm_engine" "$RUN_CASE_EXIT"
done

RUN_CASE_OUTPUT=""
RUN_CASE_EXIT=0
run_case warm
warm_log="$RUN_CASE_OUTPUT"
assert_case warm "$warm_log" "$WARM_THRESHOLD_MS" 1 "$RUN_CASE_EXIT"

echo "PASS: LiteRT-LM fast path benchmark passed"
