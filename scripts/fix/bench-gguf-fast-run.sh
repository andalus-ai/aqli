#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: bench-gguf-fast-run.sh [options]

Run a focused smoke/benchmark for the installed GGUF fast path. The check
starts from a cold daemon, verifies the backend, then verifies warm latency.

Options:
  --model ID              Model id or local alias (default: b71c9d)
  --prompt TEXT           Prompt to run (default: "where is jakarta")
  --expected REGEX        Expected answer regex (default: Indonesia|Jakarta)
  --max-tokens N          Max generated tokens (default: 10)
  --aqli-bin PATH       Aqli executable (default: ~/.local/bin/aqli)
  --warm-threshold-ms N   Fail if warm duration exceeds this (default: 1500)
  --warm-tokenize-threshold-ms N
                          Fail if measured warm native tokenize exceeds this
  --warm-prefill-threshold-ms N
                          Fail if measured warm native prefill exceeds this
  --warm-decode-threshold-ms N
                          Fail if measured warm native decode exceeds this
  --cold-threshold-ms N   Fail if cold duration exceeds this (default: 60000)
  --warmup-runs N         Stabilization runs before measured warm check (default: 1)
  --require-metal         Require Metal backend
  --no-require-metal      Do not require Metal backend
  --require-prompt-cache  Require exact/prefix prompt-cache hits on warm checks
  --require-first-repeat-cache
                          Require the first repeat to hit prompt cache when --require-prompt-cache is set (default)
  --no-require-first-repeat-cache
                          Allow warmup runs to promote prompt cache before the measured warm check
  --warm-only             Skip daemon reset/cold run and require an already-warm daemon
  --summary-file PATH     Write machine-readable TSV metrics for each case
  --keep-daemon           Leave daemon running after benchmark
  --help                  Show this help
USAGE
}

MODEL_ID="b71c9d"
PROMPT="where is jakarta"
EXPECTED_REGEX="Indonesia|Jakarta"
MAX_TOKENS=10
AQLI_BIN="${HOME}/.local/bin/aqli"
WARM_THRESHOLD_MS=1500
WARM_TOKENIZE_THRESHOLD_MS=""
WARM_PREFILL_THRESHOLD_MS=""
WARM_DECODE_THRESHOLD_MS=""
COLD_THRESHOLD_MS=60000
WARMUP_RUNS=1
KEEP_DAEMON=0
REQUIRE_PROMPT_CACHE=0
REQUIRE_FIRST_REPEAT_CACHE=1
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
    --warm-tokenize-threshold-ms) WARM_TOKENIZE_THRESHOLD_MS="$2"; shift 2 ;;
    --warm-prefill-threshold-ms) WARM_PREFILL_THRESHOLD_MS="$2"; shift 2 ;;
    --warm-decode-threshold-ms) WARM_DECODE_THRESHOLD_MS="$2"; shift 2 ;;
    --cold-threshold-ms) COLD_THRESHOLD_MS="$2"; shift 2 ;;
    --warmup-runs) WARMUP_RUNS="$2"; shift 2 ;;
    --require-metal) REQUIRE_METAL=1; shift ;;
    --no-require-metal) REQUIRE_METAL=0; shift ;;
    --require-prompt-cache) REQUIRE_PROMPT_CACHE=1; shift ;;
    --require-first-repeat-cache) REQUIRE_FIRST_REPEAT_CACHE=1; shift ;;
    --no-require-first-repeat-cache) REQUIRE_FIRST_REPEAT_CACHE=0; shift ;;
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

RUN_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-gguf-fast-bench.XXXXXX")"
if [[ -n "$SUMMARY_FILE" ]]; then
  printf 'case\tdurationMs\tbackend\twarmSession\ttokenizeCache\tpromptCache\tpromptCacheEagerShort\toutputBytes\tjavaRetries\tmodelLoadMs\ttokenizeMs\tprefillMs\tdecodeMs\tlog\n' > "$SUMMARY_FILE"
fi
cleanup() {
  if [[ "$KEEP_DAEMON" -ne 1 ]]; then
    "$AQLI_BIN" __gguf-daemon-stop >/dev/null 2>&1 || true
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

extract_native_metric_ms() {
  local file="$1"
  local key="$2"
  strip_ansi < "$file" \
    | { grep -oE "${key}=[0-9]+([.][0-9]+)?ms" || true; } \
    | tail -n 1 \
    | sed -E "s/^${key}=//; s/ms$//"
}

extract_native_metric_value() {
  local file="$1"
  local key="$2"
  strip_ansi < "$file" \
    | { grep -oE "${key}=[^,)} ]+" || true; } \
    | tail -n 1 \
    | sed -E "s/^${key}=//"
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

is_prompt_cache_hit() {
  local prompt_cache="$1"
  [[ "$prompt_cache" == "hit" || "$prompt_cache" == "prefix-hit" ]]
}

metric_exceeds() {
  local actual="$1"
  local limit="$2"
  awk -v actual="$actual" -v limit="$limit" 'BEGIN { exit !(actual + 0 > limit + 0) }'
}

assert_native_metric_under() {
  local name="$1"
  local label="$2"
  local actual="$3"
  local limit="$4"
  local output="$5"
  if [[ -z "$limit" ]]; then
    return 0
  fi
  if [[ -z "$actual" ]]; then
    echo "FAIL: $name did not report native $label timing" >&2
    tail -n 80 "$output" >&2
    exit 1
  fi
  if metric_exceeds "$actual" "$limit"; then
    echo "FAIL: $name native $label took ${actual}ms, threshold is ${limit}ms" >&2
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
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
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
    "$(summary_field "${12}")" \
    "$(summary_field "${13}")" \
    "$(summary_field "${14}")" >> "$SUMMARY_FILE"
}

run_case() {
  local name="$1"
  local output="$RUN_DIR/${name}.log"
  AQLI_GGUF_FAST_RUN_TIMING=true "$AQLI_BIN" run \
    --model "$MODEL_ID" \
    --prompt "$PROMPT" \
    --max-tokens "$MAX_TOKENS" \
    --temperature 0 \
    --top-k 1 \
    --top-p 1 \
    --provider gguf \
    --engine auto >"$output" 2>&1
  RUN_CASE_OUTPUT="$output"
}

assert_case() {
  local name="$1"
  local output="$2"
  local threshold_ms="$3"
  local require_warm_state="${4:-0}"
  local duration_ms backend model_load_ms tokenize_ms prefill_ms decode_ms warm_session prompt_cache prompt_cache_eager tokenize_cache output_bytes java_retries

  if grep -E 'GGML_ASSERT|FATAL:|Exception|ERROR:' "$output" >/dev/null; then
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

  if [[ "$require_warm_state" -eq 1 ]]; then
    warm_session="$(extract_native_metric_value "$output" warmSession || true)"
    prompt_cache="$(extract_native_metric_value "$output" promptCache || true)"
    if [[ "$warm_session" != "true" ]]; then
      echo "FAIL: $name did not reuse the GGUF daemon session (warmSession=$warm_session)" >&2
      tail -n 80 "$output" >&2
      exit 1
    fi
    if [[ "$REQUIRE_PROMPT_CACHE" -eq 1 ]] && ! is_prompt_cache_hit "$prompt_cache"; then
      echo "FAIL: $name did not hit the GGUF prompt cache (promptCache=$prompt_cache)" >&2
      tail -n 80 "$output" >&2
      exit 1
    fi
    if [[ "$prompt_cache" == *failed* ]]; then
      echo "FAIL: $name reported a GGUF prompt-cache failure (promptCache=$prompt_cache)" >&2
      tail -n 80 "$output" >&2
      exit 1
    fi
  fi

  java_retries="$(extract_native_metric_value "$output" javaOutputRetries || true)"
  if [[ -n "$java_retries" && "$java_retries" =~ ^[0-9]+$ && "$java_retries" -gt 0 ]]; then
    echo "FAIL: $name retried generation after output-buffer overflow (javaOutputRetries=$java_retries)" >&2
    tail -n 80 "$output" >&2
    exit 1
  fi

  model_load_ms="$(extract_native_metric_ms "$output" modelLoad || true)"
  tokenize_ms="$(extract_native_metric_ms "$output" tokenize || true)"
  prefill_ms="$(extract_native_metric_ms "$output" prefill || true)"
  decode_ms="$(extract_native_metric_ms "$output" decode || true)"
  if [[ "$name" == "warm" ]]; then
    assert_native_metric_under "$name" "tokenize" "$tokenize_ms" "$WARM_TOKENIZE_THRESHOLD_MS" "$output"
    assert_native_metric_under "$name" "prefill" "$prefill_ms" "$WARM_PREFILL_THRESHOLD_MS" "$output"
    assert_native_metric_under "$name" "decode" "$decode_ms" "$WARM_DECODE_THRESHOLD_MS" "$output"
  fi
  warm_session="$(extract_native_metric_value "$output" warmSession || true)"
  prompt_cache="$(extract_native_metric_value "$output" promptCache || true)"
  prompt_cache_eager="$(extract_native_metric_value "$output" promptCacheEagerShort || true)"
  tokenize_cache="$(extract_native_metric_value "$output" tokenizeCache || true)"
  output_bytes="$(extract_native_metric_value "$output" outputBytes || true)"
  write_summary_row \
    "$name" \
    "$duration_ms" \
    "${backend:-unknown}" \
    "${warm_session:-n/a}" \
    "${tokenize_cache:-n/a}" \
    "${prompt_cache:-n/a}" \
    "${prompt_cache_eager:-n/a}" \
    "${output_bytes:-n/a}" \
    "${java_retries:-n/a}" \
    "${model_load_ms:-n/a}" \
    "${tokenize_ms:-n/a}" \
    "${prefill_ms:-n/a}" \
    "${decode_ms:-n/a}" \
    "$output"
  printf '%-8s duration=%sms backend=%s warmSession=%s tokenizeCache=%s promptCache=%s eagerShort=%s outputBytes=%s javaRetries=%s modelLoad=%sms tokenize=%sms prefill=%sms decode=%sms log=%s\n' \
    "$name" "$duration_ms" "${backend:-unknown}" "${warm_session:-n/a}" "${tokenize_cache:-n/a}" "${prompt_cache:-n/a}" "${prompt_cache_eager:-n/a}" "${output_bytes:-n/a}" "${java_retries:-n/a}" "${model_load_ms:-n/a}" "${tokenize_ms:-n/a}" "${prefill_ms:-n/a}" "${decode_ms:-n/a}" "$output"
}

if [[ "$WARM_ONLY" -ne 1 ]]; then
  "$AQLI_BIN" __gguf-daemon-stop >/dev/null 2>&1 || true

  cold_log=""
  RUN_CASE_OUTPUT=""
  run_case cold
  cold_log="$RUN_CASE_OUTPUT"
  assert_case cold "$cold_log" "$COLD_THRESHOLD_MS"
fi

for ((i = 1; i <= WARMUP_RUNS; i++)); do
  RUN_CASE_OUTPUT=""
  run_case "warmup-${i}"
  warmup_log="$RUN_CASE_OUTPUT"
  warmup_require_warm_state=0
  warmup_threshold="$COLD_THRESHOLD_MS"
  if [[ "$WARM_ONLY" -eq 1 ]]; then
    warmup_require_warm_state=1
  fi
  if [[ "$i" -eq 1 && "$REQUIRE_PROMPT_CACHE" -eq 1 && "$REQUIRE_FIRST_REPEAT_CACHE" -eq 1 ]]; then
    warmup_require_warm_state=1
  fi
  assert_case "warmup${i}" "$warmup_log" "$warmup_threshold" "$warmup_require_warm_state"
done

RUN_CASE_OUTPUT=""
run_case warm
warm_log="$RUN_CASE_OUTPUT"
assert_case warm "$warm_log" "$WARM_THRESHOLD_MS" 1

echo "PASS: GGUF fast path benchmark passed"
