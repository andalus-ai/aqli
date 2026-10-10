#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: bench-gguf-engine-compare.sh [options]

Run the installed GGUF engine comparison path. This verifies that the Java-native
GGUF loader/probe still works, while generation is explicitly served by the
llama.cpp fallback until Java-native generation is enabled.

Options:
  --model ID              Model id or local alias (default: b71c9d)
  --prompt TEXT           Prompt to run (default: "where is jakarta")
  --expected REGEX        Expected answer regex (default: Indonesia|Jakarta)
  --max-tokens N          Max generated tokens (default: 10)
  --aqli-bin PATH       Aqli executable (default: ~/.local/bin/aqli)
  --threshold-ms N        Fail if fallback generation exceeds this (default: 10000)
  --java-ready-regex RE   Required Java-native status regex (default: row-dot-primitives-ready)
  --java-config-regex RE  Required Java-native config regex (default: positive core dimensions)
  --java-probe-regex RE   Required Java-native tensor probe regex (default: real prepared mat-vec probe)
  --java-matvec-threshold-ms N
                          Fail if Java probe parallelMatVec exceeds this (default: 50)
  --fallback-prefill-threshold-ms N
                          Fail if measured llama.cpp fallback prefill exceeds this
  --fallback-decode-threshold-ms N
                          Fail if measured llama.cpp fallback decode exceeds this
  --verify-java-refusal   Verify --engine java refuses llama.cpp fallback (default)
  --no-verify-java-refusal
  --require-metal         Require Metal backend
  --no-require-metal      Do not require Metal backend
  --summary-file PATH     Write machine-readable TSV metrics for the comparison
  --help                  Show this help
USAGE
}

MODEL_ID="b71c9d"
PROMPT="where is jakarta"
EXPECTED_REGEX="Indonesia|Jakarta"
MAX_TOKENS=10
AQLI_BIN="${HOME}/.local/bin/aqli"
THRESHOLD_MS=10000
JAVA_READY_REGEX="row-dot-primitives-ready"
JAVA_CONFIG_REGEX='type=[^,]+, layers=[1-9][0-9]*, hidden=[1-9][0-9]*, heads=[1-9][0-9]*/[1-9][0-9]*, headDim=[1-9][0-9]*, context=[1-9][0-9]*, vocab=[1-9][0-9]*'
JAVA_PROBE_REGEX='tensor=[^,]+, type=[^,]+, rows=[1-9][0-9]*, cols=[1-9][0-9]*, sampledRows=[1-9][0-9]*, .*matVecRows=[1-9][0-9]*, cache=[0-9]+([,.][0-9]+)?ms, preparedMatVecReady=true, parallelMatVec=[0-9]+([,.][0-9]+)?ms, .*cachedGenericMatVec=[0-9]+([,.][0-9]+)?ms'
JAVA_MATVEC_THRESHOLD_MS=50
FALLBACK_PREFILL_THRESHOLD_MS=""
FALLBACK_DECODE_THRESHOLD_MS=""
VERIFY_JAVA_REFUSAL=1
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
    --threshold-ms) THRESHOLD_MS="$2"; shift 2 ;;
    --java-ready-regex) JAVA_READY_REGEX="$2"; shift 2 ;;
    --java-config-regex) JAVA_CONFIG_REGEX="$2"; shift 2 ;;
    --java-probe-regex) JAVA_PROBE_REGEX="$2"; shift 2 ;;
    --java-matvec-threshold-ms) JAVA_MATVEC_THRESHOLD_MS="$2"; shift 2 ;;
    --fallback-prefill-threshold-ms) FALLBACK_PREFILL_THRESHOLD_MS="$2"; shift 2 ;;
    --fallback-decode-threshold-ms) FALLBACK_DECODE_THRESHOLD_MS="$2"; shift 2 ;;
    --verify-java-refusal) VERIFY_JAVA_REFUSAL=1; shift ;;
    --no-verify-java-refusal) VERIFY_JAVA_REFUSAL=0; shift ;;
    --require-metal) REQUIRE_METAL=1; shift ;;
    --no-require-metal) REQUIRE_METAL=0; shift ;;
    --summary-file) SUMMARY_FILE="$2"; shift 2 ;;
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

RUN_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-gguf-engine-compare.XXXXXX")"
OUTPUT="$RUN_DIR/compare.log"
JAVA_OUTPUT="$RUN_DIR/java-only.log"
if [[ -n "$SUMMARY_FILE" ]]; then
  printf 'case\tdurationMs\tbackend\twarmSession\tpromptCache\tmodelLoadMs\ttokenizeMs\tprefillMs\tdecodeMs\tjavaStatus\tloaderReady\tdecoderTensorsReady\trowDotReady\tgenerationReady\tbenchmarkParallelMatVecMs\tjavaOnlyParallelMatVecMs\tbestParallelMatVecMs\tjavaMatVecThresholdMs\tjavaFallbackRefusal\tlog\n' > "$SUMMARY_FILE"
fi

strip_ansi() {
  sed -E $'s/\x1B\\[[0-9;?]*[ -/]*[@-~]//g'
}

extract_duration_ms() {
  local raw
  raw="$(strip_ansi < "$OUTPUT" \
    | { grep -oE 'Duration: [0-9]+([,.][0-9]+)?s' || true; } \
    | tail -n 1 \
    | sed -E 's/^Duration: //; s/s$//; s/,/./')"
  if [[ -z "$raw" ]]; then
    echo ""
    return 0
  fi
  awk -v seconds="$raw" 'BEGIN { printf "%.0f", seconds * 1000 }'
}

extract_backend() {
  strip_ansi < "$OUTPUT" \
    | { grep -oE 'backend=[^,)]+(\([^)]*\))?' || true; } \
    | tail -n 1 \
    | sed 's/^backend=//'
}

is_metal_backend() {
  local backend="$1"
  [[ "$backend" == *Metal* || "$backend" == *MTL* ]]
}

extract_java_status_from() {
  local file="$1"
  strip_ansi < "$file" \
    | { grep -oE 'status=[^.]+[.]' || true; } \
    | head -n 1 \
    | sed -E 's/^status=//; s/[.]$//'
}

extract_java_status() {
  extract_java_status_from "$OUTPUT"
}

extract_java_config_from() {
  local file="$1"
  strip_ansi < "$file" \
    | { grep -oE 'Java-native GGUF loader config: [^.]+[.]' || true; } \
    | head -n 1 \
    | sed -E 's/^Java-native GGUF loader config: //; s/[.]$//'
}

extract_java_config() {
  extract_java_config_from "$OUTPUT"
}

extract_probe_line_from() {
  local file="$1"
  strip_ansi < "$file" \
    | { grep -E 'Java-native GGUF loader tensor probe:' || true; } \
    | head -n 1
}

extract_probe_line() {
  extract_probe_line_from "$OUTPUT"
}

extract_probe_metric_ms_from() {
  local file="$1"
  local key="$2"
  strip_ansi < "$file" \
    | { grep -oE "${key}=[0-9]+([,.][0-9]+)?ms" || true; } \
    | head -n 1 \
    | sed -E "s/^${key}=//; s/ms$//; s/,/./"
}

extract_probe_metric_ms() {
  extract_probe_metric_ms_from "$OUTPUT" "$1"
}

extract_native_metric_value_from() {
  local file="$1"
  local key="$2"
  strip_ansi < "$file" \
    | { grep -oE "${key}=[^,)]+" || true; } \
    | tail -n 1 \
    | sed -E "s/^${key}=//"
}

extract_native_metric_ms_from() {
  local file="$1"
  local key="$2"
  strip_ansi < "$file" \
    | { grep -oE "${key}=[0-9]+([,.][0-9]+)?ms" || true; } \
    | tail -n 1 \
    | sed -E "s/^${key}=//; s/ms$//; s/,/./"
}

extract_readiness_value_from() {
  local file="$1"
  local key="$2"
  strip_ansi < "$file" \
    | { grep -oE "${key}=(true|false)" || true; } \
    | head -n 1 \
    | sed -E "s/^${key}=//"
}

extract_readiness_value() {
  extract_readiness_value_from "$OUTPUT" "$1"
}

metric_exceeds() {
  local actual="$1"
  local threshold="$2"
  awk -v actual="$actual" -v threshold="$threshold" 'BEGIN { exit !(actual + 0 > threshold + 0) }'
}

metric_min() {
  local first="$1"
  local second="$2"
  awk -v first="$first" -v second="$second" 'BEGIN { printf "%.3f", (first <= second) ? first : second }'
}

assert_metric_under() {
  local name="$1"
  local actual="$2"
  local threshold="$3"
  local output="$4"
  if [[ -z "$threshold" ]]; then
    return 0
  fi
  if [[ -z "$actual" ]]; then
    echo "FAIL: $name metric was not reported" >&2
    tail -n 100 "$output" >&2
    exit 1
  fi
  if metric_exceeds "$actual" "$threshold"; then
    echo "FAIL: $name took ${actual}ms, threshold is ${threshold}ms" >&2
    tail -n 100 "$output" >&2
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
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
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
    "$(summary_field "${14}")" \
    "$(summary_field "${15}")" \
    "$(summary_field "${16}")" \
    "$(summary_field "${17}")" \
    "$(summary_field "${18}")" \
    "$(summary_field "${19}")" \
    "$(summary_field "${20}")" >> "$SUMMARY_FILE"
}

AQLI_GGUF_FAST_RUN_TIMING=true "$AQLI_BIN" run \
  --model "$MODEL_ID" \
  --prompt "$PROMPT" \
  --max-tokens "$MAX_TOKENS" \
  --temperature 0 \
  --top-k 1 \
  --top-p 1 \
  --provider gguf \
  --engine benchmark >"$OUTPUT" 2>&1

if grep -E 'GGML_ASSERT|FATAL:|Exception|ERROR:' "$OUTPUT" >/dev/null; then
  echo "FAIL: GGUF engine comparison produced runtime errors" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if ! strip_ansi < "$OUTPUT" | grep -q 'GGUF engine benchmark: Java-native loader/probe vs llama.cpp generation fallback'; then
  echo "FAIL: GGUF engine comparison did not enter benchmark mode" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if ! strip_ansi < "$OUTPUT" | grep -q 'Java-native GGUF loader:'; then
  echo "FAIL: Java-native GGUF loader profile was not reported" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if ! strip_ansi < "$OUTPUT" | grep -q 'Java-native GGUF loader tensor probe:'; then
  echo "FAIL: Java-native GGUF tensor probe was not reported" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if ! strip_ansi < "$OUTPUT" | grep -q 'Using llama.cpp GGUF'; then
  echo "FAIL: llama.cpp fallback generation was not reported" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if ! strip_ansi < "$OUTPUT" | grep -Eiq "$EXPECTED_REGEX"; then
  echo "FAIL: fallback answer did not match expected regex: $EXPECTED_REGEX" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi

backend="$(extract_backend)"
if [[ "$REQUIRE_METAL" -eq 1 ]] && ! is_metal_backend "$backend"; then
  echo "FAIL: llama.cpp fallback did not use Metal backend (backend=${backend:-missing})" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi

duration_ms="$(extract_duration_ms)"
if [[ -z "$duration_ms" ]]; then
  echo "FAIL: llama.cpp fallback did not report duration" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if (( duration_ms > THRESHOLD_MS )); then
  echo "FAIL: llama.cpp fallback took ${duration_ms}ms, threshold is ${THRESHOLD_MS}ms" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
fallback_warm_session="$(extract_native_metric_value_from "$OUTPUT" warmSession || true)"
fallback_prompt_cache="$(extract_native_metric_value_from "$OUTPUT" promptCache || true)"
fallback_model_load_ms="$(extract_native_metric_ms_from "$OUTPUT" modelLoad || true)"
fallback_tokenize_ms="$(extract_native_metric_ms_from "$OUTPUT" tokenize || true)"
fallback_prefill_ms="$(extract_native_metric_ms_from "$OUTPUT" prefill || true)"
fallback_decode_ms="$(extract_native_metric_ms_from "$OUTPUT" decode || true)"
assert_metric_under "llama.cpp fallback prefill" "$fallback_prefill_ms" "$FALLBACK_PREFILL_THRESHOLD_MS" "$OUTPUT"
assert_metric_under "llama.cpp fallback decode" "$fallback_decode_ms" "$FALLBACK_DECODE_THRESHOLD_MS" "$OUTPUT"

java_status="$(extract_java_status)"
java_config="$(extract_java_config)"
java_probe="$(extract_probe_line)"
matvec_ms="$(extract_probe_metric_ms 'parallelMatVec')"
java_only_matvec_ms=""
loader_ready="$(extract_readiness_value 'loaderReady')"
decoder_ready="$(extract_readiness_value 'decoderTensorsReady')"
row_dot_ready="$(extract_readiness_value 'rowDotReady')"
generation_ready="$(extract_readiness_value 'generationReady')"
if [[ -z "$java_status" ]]; then
  echo "FAIL: Java-native GGUF status was not reported" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if ! printf '%s\n' "$java_status" | grep -Eiq "$JAVA_READY_REGEX"; then
  echo "FAIL: Java-native GGUF status did not match required regex: $JAVA_READY_REGEX (status=$java_status)" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if [[ -z "$java_config" ]]; then
  echo "FAIL: Java-native GGUF mapped config was not reported" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if ! printf '%s\n' "$java_config" | grep -Eiq "$JAVA_CONFIG_REGEX"; then
  echo "FAIL: Java-native GGUF mapped config did not match required regex: $JAVA_CONFIG_REGEX (config=$java_config)" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if [[ -z "$java_probe" ]]; then
  echo "FAIL: Java-native GGUF tensor probe line was not reported" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if ! printf '%s\n' "$java_probe" | grep -Eiq "$JAVA_PROBE_REGEX"; then
  echo "FAIL: Java-native GGUF tensor probe did not match required regex: $JAVA_PROBE_REGEX (probe=$java_probe)" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if [[ "$loader_ready" != "true" || "$decoder_ready" != "true" || "$row_dot_ready" != "true" ]]; then
  echo "FAIL: Java-native GGUF readiness is incomplete (loaderReady=${loader_ready:-missing}, decoderTensorsReady=${decoder_ready:-missing}, rowDotReady=${row_dot_ready:-missing})" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if [[ "$generation_ready" != "false" ]]; then
  echo "FAIL: Java-native GGUF generation readiness changed unexpectedly (generationReady=${generation_ready:-missing})" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
if [[ -z "$matvec_ms" ]]; then
  echo "FAIL: Java-native GGUF parallelMatVec metric was not reported" >&2
  tail -n 100 "$OUTPUT" >&2
  exit 1
fi
assert_metric_under "Java-native GGUF benchmark parallelMatVec" "$matvec_ms" "$JAVA_MATVEC_THRESHOLD_MS" "$OUTPUT"
best_matvec_ms="$matvec_ms"

java_refusal_status="skipped"
if [[ "$VERIFY_JAVA_REFUSAL" -eq 1 ]]; then
  set +e
  "$AQLI_BIN" run \
    --model "$MODEL_ID" \
    --prompt "$PROMPT" \
    --max-tokens "$MAX_TOKENS" \
    --temperature 0 \
    --top-k 1 \
    --top-p 1 \
    --provider gguf \
    --engine java >"$JAVA_OUTPUT" 2>&1
  java_exit=$?
  set -e

  if [[ "$java_exit" -eq 0 ]]; then
    echo "FAIL: --engine java unexpectedly succeeded before Java-native generation is enabled" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  if ! strip_ansi < "$JAVA_OUTPUT" | grep -q 'Java-native GGUF loader:'; then
    echo "FAIL: --engine java did not report the Java-native loader profile" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  if ! strip_ansi < "$JAVA_OUTPUT" | grep -q 'Java-native GGUF loader tensor probe:'; then
    echo "FAIL: --engine java did not report the Java-native tensor probe" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  java_only_status="$(extract_java_status_from "$JAVA_OUTPUT")"
  java_only_config="$(extract_java_config_from "$JAVA_OUTPUT")"
  java_only_probe="$(extract_probe_line_from "$JAVA_OUTPUT")"
  java_only_loader_ready="$(extract_readiness_value_from "$JAVA_OUTPUT" 'loaderReady')"
  java_only_decoder_ready="$(extract_readiness_value_from "$JAVA_OUTPUT" 'decoderTensorsReady')"
  java_only_row_dot_ready="$(extract_readiness_value_from "$JAVA_OUTPUT" 'rowDotReady')"
  java_only_generation_ready="$(extract_readiness_value_from "$JAVA_OUTPUT" 'generationReady')"
  java_only_matvec_ms="$(extract_probe_metric_ms_from "$JAVA_OUTPUT" 'parallelMatVec')"
  if [[ -z "$java_only_status" ]]; then
    echo "FAIL: --engine java did not report Java-native status" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  if ! printf '%s\n' "$java_only_status" | grep -Eiq "$JAVA_READY_REGEX"; then
    echo "FAIL: --engine java status did not match required regex: $JAVA_READY_REGEX (status=$java_only_status)" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  if [[ -z "$java_only_config" ]]; then
    echo "FAIL: --engine java did not report mapped config" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  if [[ "$java_only_config" != "$java_config" ]]; then
    echo "FAIL: --engine java mapped config differed from benchmark mode (benchmark=$java_config, java=$java_only_config)" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  if ! printf '%s\n' "$java_only_config" | grep -Eiq "$JAVA_CONFIG_REGEX"; then
    echo "FAIL: --engine java mapped config did not match required regex: $JAVA_CONFIG_REGEX (config=$java_only_config)" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  if [[ -z "$java_only_probe" ]]; then
    echo "FAIL: --engine java did not report the Java-native tensor probe line" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  if ! printf '%s\n' "$java_only_probe" | grep -Eiq "$JAVA_PROBE_REGEX"; then
    echo "FAIL: --engine java tensor probe did not match required regex: $JAVA_PROBE_REGEX (probe=$java_only_probe)" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  if [[ "$java_only_loader_ready" != "true" || "$java_only_decoder_ready" != "true" || "$java_only_row_dot_ready" != "true" ]]; then
    echo "FAIL: --engine java readiness is incomplete (loaderReady=${java_only_loader_ready:-missing}, decoderTensorsReady=${java_only_decoder_ready:-missing}, rowDotReady=${java_only_row_dot_ready:-missing})" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  if [[ "$java_only_generation_ready" != "false" ]]; then
    echo "FAIL: --engine java generation readiness changed unexpectedly (generationReady=${java_only_generation_ready:-missing})" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  if [[ -z "$java_only_matvec_ms" ]]; then
    echo "FAIL: --engine java parallelMatVec metric was not reported" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  assert_metric_under "Java-native GGUF --engine java parallelMatVec" "$java_only_matvec_ms" "$JAVA_MATVEC_THRESHOLD_MS" "$JAVA_OUTPUT"
  best_matvec_ms="$(metric_min "$best_matvec_ms" "$java_only_matvec_ms")"
  if ! strip_ansi < "$JAVA_OUTPUT" | grep -q 'refusing to silently use llama.cpp'; then
    echo "FAIL: --engine java did not explicitly refuse llama.cpp fallback" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  if strip_ansi < "$JAVA_OUTPUT" | grep -q 'Using llama.cpp GGUF'; then
    echo "FAIL: --engine java silently used llama.cpp fallback" >&2
    tail -n 100 "$JAVA_OUTPUT" >&2
    exit 1
  fi
  java_refusal_status="checked"
fi

java_config_summary="$(printf '%s' "$java_config" | sed -E 's/, /;/g')"
write_summary_row \
  compare \
  "$duration_ms" \
  "${backend:-unknown}" \
  "${fallback_warm_session:-n/a}" \
  "${fallback_prompt_cache:-n/a}" \
  "${fallback_model_load_ms:-n/a}" \
  "${fallback_tokenize_ms:-n/a}" \
  "${fallback_prefill_ms:-n/a}" \
  "${fallback_decode_ms:-n/a}" \
  "$java_status" \
  "$loader_ready" \
  "$decoder_ready" \
  "$row_dot_ready" \
  "$generation_ready" \
  "$matvec_ms" \
  "${java_only_matvec_ms:-n/a}" \
  "$best_matvec_ms" \
  "$JAVA_MATVEC_THRESHOLD_MS" \
  "$java_refusal_status" \
  "$OUTPUT"
printf 'compare duration=%sms backend=%s javaStatus=%s javaConfig=%s loaderReady=%s decoderTensorsReady=%s rowDotReady=%s generationReady=%s parallelMatVec=%sms javaMatVecThreshold=%sms javaFallbackRefusal=%s log=%s\n' \
  "$duration_ms" "${backend:-unknown}" "$java_status" "$java_config_summary" "$loader_ready" "$decoder_ready" "$row_dot_ready" "$generation_ready" "$best_matvec_ms" "$JAVA_MATVEC_THRESHOLD_MS" "$java_refusal_status" "$OUTPUT"
echo "PASS: GGUF Java-native probe vs llama.cpp fallback comparison passed"
