#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: bench-onnx-profile.sh --model MODEL [options]

Run a repeatable local benchmark for ONNX text inference using the installed
`aqli` CLI with -Daqli.profile=true, then collect raw logs plus a compact
TSV summary of benchmark and ONNX runner profile timings.

Options:
  --model ID              Model id or local alias (required)
  --prompt TEXT           Prompt to run (default: "where is jakarta")
  --max-tokens N          Max generated tokens per run (default: 8)
  --runs N                Measured runs after warmup (default: 1)
  --warmup-runs N         Warmup runs before measured checks (default: 0)
  --aqli-bin PATH       Aqli executable (default: ~/.local/bin/aqli)
  --out-dir PATH          Output root (default: aqli/ops/benchmarks/onnx)
  --label NAME            Optional run label
  --require-profile       Fail if ONNX profile lines are missing (default)
  --no-require-profile    Allow successful runs without ONNX profile lines
  --expect-backend NAME    Fail if a successful run reports another ONNX backend
  --help                  Show this help

Examples:
  ./aqli/scripts/bench-onnx-profile.sh --model 6b6e13 --max-tokens 4
  ./aqli/scripts/bench-onnx-profile.sh --model onnx-community/gemma-4-E2B-it-ONNX --runs 3
USAGE
}

MODEL_ID=""
PROMPT="${AQLI_BENCH_ONNX_PROMPT:-where is jakarta}"
MAX_TOKENS="${AQLI_BENCH_ONNX_MAX_TOKENS:-8}"
RUNS="${AQLI_BENCH_ONNX_RUNS:-1}"
WARMUP_RUNS="${AQLI_BENCH_ONNX_WARMUP_RUNS:-0}"
AQLI_BIN="${HOME}/.local/bin/aqli"
OUT_DIR="aqli/ops/benchmarks/onnx"
LABEL=""
REQUIRE_PROFILE=1
EXPECT_BACKEND="${AQLI_BENCH_ONNX_EXPECT_BACKEND:-${AQLI_ONNX_EXPECT_BACKEND:-}}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model) MODEL_ID="$2"; shift 2 ;;
    --model=*) MODEL_ID="${1#*=}"; shift ;;
    --prompt) PROMPT="$2"; shift 2 ;;
    --prompt=*) PROMPT="${1#*=}"; shift ;;
    --max-tokens) MAX_TOKENS="$2"; shift 2 ;;
    --max-tokens=*) MAX_TOKENS="${1#*=}"; shift ;;
    --runs) RUNS="$2"; shift 2 ;;
    --runs=*) RUNS="${1#*=}"; shift ;;
    --warmup-runs) WARMUP_RUNS="$2"; shift 2 ;;
    --warmup-runs=*) WARMUP_RUNS="${1#*=}"; shift ;;
    --aqli-bin) AQLI_BIN="$2"; shift 2 ;;
    --aqli-bin=*) AQLI_BIN="${1#*=}"; shift ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --out-dir=*) OUT_DIR="${1#*=}"; shift ;;
    --label) LABEL="$2"; shift 2 ;;
    --label=*) LABEL="${1#*=}"; shift ;;
    --require-profile) REQUIRE_PROFILE=1; shift ;;
    --no-require-profile) REQUIRE_PROFILE=0; shift ;;
    --expect-backend) EXPECT_BACKEND="$2"; shift 2 ;;
    --expect-backend=*) EXPECT_BACKEND="${1#*=}"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$MODEL_ID" ]]; then
  echo "--model is required" >&2
  usage
  exit 2
fi
if [[ ! "$MAX_TOKENS" =~ ^[0-9]+$ ]] || (( MAX_TOKENS < 1 )); then
  echo "Invalid --max-tokens value: $MAX_TOKENS" >&2
  exit 2
fi
if [[ ! "$RUNS" =~ ^[0-9]+$ ]] || (( RUNS < 1 )); then
  echo "Invalid --runs value: $RUNS" >&2
  exit 2
fi
if [[ ! "$WARMUP_RUNS" =~ ^[0-9]+$ ]]; then
  echo "Invalid --warmup-runs value: $WARMUP_RUNS" >&2
  exit 2
fi

for cmd in awk date grep mkdir sed tail tee; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

if [[ ! -x "$AQLI_BIN" ]]; then
  echo "aqli executable not found or not executable: $AQLI_BIN" >&2
  exit 2
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
if [[ -z "$LABEL" ]]; then
  LABEL="onnx-${MODEL_ID//\//_}-${timestamp}"
fi
RUN_DIR="${OUT_DIR%/}/${LABEL}"
LOG_DIR="${RUN_DIR}/logs"
SUMMARY_TSV="${RUN_DIR}/summary.tsv"
CONFIG_TSV="${RUN_DIR}/config.tsv"
REPORT_TXT="${RUN_DIR}/report.txt"
ARGV_TXT="${RUN_DIR}/argv.txt"
mkdir -p "$LOG_DIR"

strip_ansi() {
  sed -E $'s/\x1B\\[[0-9;?]*[ -/]*[@-~]//g'
}

extract_metric() {
  local file="$1"
  local label="$2"
  strip_ansi < "$file" \
    | awk -v label="$label" '
        index($0, label) {
          value = $0
          sub(/^.*=[[:space:]]*/, "", value)
          sub(/[[:space:]]+(ms\/token|ms|t\/s).*$/, "", value)
          gsub(",", ".", value)
          print value
          exit
        }
      '
}

extract_text_metric() {
  local file="$1"
  local label="$2"
  strip_ansi < "$file" \
    | awk -v label="$label" '
        index($0, label) {
          value = $0
          sub(/^.*=[[:space:]]*/, "", value)
          gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
          print value
          exit
        }
      '
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

profile_present() {
  local file="$1"
  strip_ansi < "$file" | grep -q 'onnx profile:'
}

combined_log() {
  local stdout_file="$1"
  local stderr_file="$2"
  local combined_file="$3"
  {
    echo "### stdout"
    cat "$stdout_file"
    echo
    echo "### stderr"
    cat "$stderr_file"
  } > "$combined_file"
}

write_row() {
  local case_name="$1"
  local status="$2"
  local exit_code="$3"
  local stdout_file="$4"
  local stderr_file="$5"
  local combined_file="$6"
  local duration_ms prompt_eval_tps generation_tps decode_tps ttft_ms tpot_ms
  local backend tokenize_ms input_prep_ms prefill_run_ms decode_run_ms ort_run_ms logits_ms sampling_ms steps

  duration_ms="$(extract_duration_ms "$stdout_file")"
  prompt_eval_tps="$(extract_metric "$stdout_file" "prompt eval")"
  generation_tps="$(extract_metric "$stdout_file" "generation")"
  decode_tps="$(extract_metric "$stdout_file" "decode         =")"
  ttft_ms="$(extract_metric "$stdout_file" "latency (ttft)")"
  tpot_ms="$(extract_metric "$stdout_file" "token latency")"
  backend="$(extract_text_metric "$stdout_file" "backend       =")"
  tokenize_ms="$(extract_metric "$stdout_file" "tokenize      =")"
  input_prep_ms="$(extract_metric "$stdout_file" "input prep    =")"
  prefill_run_ms="$(extract_metric "$stdout_file" "prefill run   =")"
  decode_run_ms="$(extract_metric "$stdout_file" "decode run    =")"
  ort_run_ms="$(extract_metric "$stdout_file" "ort run       =")"
  logits_ms="$(extract_metric "$stdout_file" "logits select =")"
  sampling_ms="$(extract_metric "$stdout_file" "sampling      =")"
  steps="$(extract_text_metric "$stdout_file" "steps         =")"

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$case_name" "$status" "$exit_code" "${duration_ms:-}" "${prompt_eval_tps:-}" \
    "${generation_tps:-}" "${decode_tps:-}" "${ttft_ms:-}" "${tpot_ms:-}" \
    "${backend:-}" "${tokenize_ms:-}" "${input_prep_ms:-}" "${prefill_run_ms:-}" \
    "${decode_run_ms:-}" "${ort_run_ms:-}" "${logits_ms:-}" "${sampling_ms:-}" \
    "${steps:-}" "$combined_file" "$stderr_file" >> "$SUMMARY_TSV"
}

profile_java_opts() {
  local opts="${AQLI_JAVA_OPTS:-}"
  case " $opts " in
    *" -Daqli.profile=true "*) printf '%s' "$opts" ;;
    *) printf '%s%s-Daqli.profile=true' "$opts" "${opts:+ }" ;;
  esac
}

run_one() {
  local case_name="$1"
  local stdout_file="${LOG_DIR}/${case_name}.stdout.log"
  local stderr_file="${LOG_DIR}/${case_name}.stderr.log"
  local combined_file="${LOG_DIR}/${case_name}.combined.log"
  local cmd=("$AQLI_BIN" run --model "$MODEL_ID" --prompt "$PROMPT" --max-tokens "$MAX_TOKENS")
  local detected_backend=""
  local exit_code=0

  AQLI_JAVA_OPTS="$(profile_java_opts)" "${cmd[@]}" > "$stdout_file" 2> "$stderr_file" || exit_code=$?
  combined_log "$stdout_file" "$stderr_file" "$combined_file"
  detected_backend="$(extract_text_metric "$stdout_file" "backend       =")"

  if [[ "$exit_code" -eq 0 && "$REQUIRE_PROFILE" -eq 1 ]] && ! profile_present "$stdout_file"; then
    echo "ONNX profile output missing for ${case_name}; see $combined_file" >&2
    exit_code=3
  fi
  if [[ "$exit_code" -eq 0 && -n "$EXPECT_BACKEND" ]]; then
    if [[ -z "$detected_backend" ]]; then
      echo "Expected ONNX backend '${EXPECT_BACKEND}' for ${case_name}, but backend was not reported; see $combined_file" >&2
      exit_code=4
    elif [[ "$detected_backend" != "$EXPECT_BACKEND" ]]; then
      echo "Expected ONNX backend '${EXPECT_BACKEND}' for ${case_name}, got '${detected_backend}'; see $combined_file" >&2
      exit_code=4
    fi
  fi

  local status="pass"
  if [[ "$exit_code" -ne 0 ]]; then
    status="fail"
  fi
  write_row "$case_name" "$status" "$exit_code" "$stdout_file" "$stderr_file" "$combined_file"
  return "$exit_code"
}

{
  printf 'key\tvalue\n'
  printf 'generated_at\t%s\n' "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  printf 'model\t%s\n' "$MODEL_ID"
  printf 'prompt\t%s\n' "$PROMPT"
  printf 'max_tokens\t%s\n' "$MAX_TOKENS"
  printf 'runs\t%s\n' "$RUNS"
  printf 'warmup_runs\t%s\n' "$WARMUP_RUNS"
  printf 'aqli_bin\t%s\n' "$AQLI_BIN"
  printf 'require_profile\t%s\n' "$REQUIRE_PROFILE"
  printf 'expect_backend\t%s\n' "$EXPECT_BACKEND"
  printf 'aqli_java_opts\t%s\n' "$(profile_java_opts)"
} > "$CONFIG_TSV"

{
  printf '%q ' "$AQLI_BIN" run --model "$MODEL_ID" --prompt "$PROMPT" --max-tokens "$MAX_TOKENS"
  printf '\n'
} > "$ARGV_TXT"

printf 'case\tstatus\texitCode\tdurationMs\tpromptEvalTps\tgenerationTps\tdecodeTps\tttftMs\ttokenLatencyMs\tonnxBackend\tonnxTokenizeMs\tonnxInputPrepMs\tonnxPrefillRunMs\tonnxDecodeRunMs\tonnxOrtRunMs\tonnxLogitsSelectMs\tonnxSamplingMs\tonnxSteps\tcombinedLog\tstderrLog\n' > "$SUMMARY_TSV"

FINAL_EXIT=0
for ((i = 1; i <= WARMUP_RUNS; i++)); do
  run_one "warmup-${i}" || FINAL_EXIT=$?
done
for ((i = 1; i <= RUNS; i++)); do
  run_one "run-${i}" || FINAL_EXIT=$?
done

{
  echo "Aqli ONNX profile benchmark"
  echo "Run dir: $RUN_DIR"
  echo "Model: $MODEL_ID"
  echo "Prompt: $PROMPT"
  echo
  awk -F '\t' 'BEGIN { OFS = "\t" } { for (i = 1; i <= NF; i++) if ($i == "") $i = "-"; print }' "$SUMMARY_TSV" \
    | { column -t -s $'\t' 2>/dev/null || cat; }
} | tee "$REPORT_TXT"

exit "$FINAL_EXIT"
