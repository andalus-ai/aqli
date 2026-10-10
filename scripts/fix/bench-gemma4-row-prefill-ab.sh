#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<'USAGE'
Usage: bench-gemma4-row-prefill-ab.sh --model MODEL [options]

Run a repeatable Gemma4 12B safetensor A/B benchmark: fused GEGLU prefill as
the baseline, row-prefill matvec FFN as the candidate, then compare both
summary.json artifacts with optional regression gates.

Options:
  --model ID                    Model id or local id alias (required)
  --aqli-bin PATH             Aqli executable (default: ~/.local/bin/aqli)
  --out-dir PATH                Output directory for A/B artifacts
                                (default: aqli/ops/benchmarks/safetensor/gemma4-row-prefill-ab)
  --case CASE                   Benchmark case to run and compare (default: metal-deterministic)
  --label-prefix NAME           Prefix for generated labels (default: gemma4-12b)
  --baseline-label NAME         Baseline label (default: label-prefix-fused)
  --current-label NAME          Current label (default: label-prefix-row-prefill)
  --baseline-preset NAME        Baseline preset (default: m4-gemma4-12b-smoke)
  --current-preset NAME         Current preset (default: m4-gemma4-12b-row-prefill-ab)
  --max-tokens N                Override preset max tokens for both runs
  --det-prompt TEXT             Override deterministic prompt for both runs
  --normal-prompt TEXT          Override natural prompt for both runs
  --gate-metrics CSV            Metrics to gate in the comparison
                                (default: ttftMs,prefillMs,ffnMs,topStageMs)
  --max-regression-percent N    Fail when a gated metric regresses by more than N percent
  --max-regression-ms N         Fail when a gated latency metric regresses by more than N ms
  --min-baseline-value N        Ignore percent gate below this baseline value
  --bench-arg ARG               Extra argument passed to both benchmark runs; repeatable
  --bench-script PATH           Benchmark script path
  --compare-script PATH         Comparison script path
  --help                        Show this help

Environment:
  AQLI_GEMMA4_ROW_PREFILL_AB_BENCH_SCRIPT    Override benchmark script path
  AQLI_GEMMA4_ROW_PREFILL_AB_COMPARE_SCRIPT  Override comparison script path

Examples:
  ./scripts/bench-gemma4-row-prefill-ab.sh --model 53f473
  ./scripts/bench-gemma4-row-prefill-ab.sh --model 53f473 \
    --max-regression-ms 250 --gate-metrics ttftMs,ffnMs
USAGE
}

MODEL_ID=""
AQLI_BIN="${HOME}/.local/bin/aqli"
OUT_DIR="aqli/ops/benchmarks/safetensor/gemma4-row-prefill-ab"
CASE_NAME="metal-deterministic"
LABEL_PREFIX="gemma4-12b"
BASELINE_LABEL=""
CURRENT_LABEL=""
BASELINE_PRESET="m4-gemma4-12b-smoke"
CURRENT_PRESET="m4-gemma4-12b-row-prefill-ab"
MAX_TOKENS=""
DET_PROMPT=""
NORMAL_PROMPT=""
GATE_METRICS="ttftMs,prefillMs,ffnMs,topStageMs"
MAX_REGRESSION_PERCENT=""
MAX_REGRESSION_MS=""
MIN_BASELINE_VALUE=""
BENCH_SCRIPT="${AQLI_GEMMA4_ROW_PREFILL_AB_BENCH_SCRIPT:-${SCRIPT_DIR}/bench-safetensor-inference.sh}"
COMPARE_SCRIPT="${AQLI_GEMMA4_ROW_PREFILL_AB_COMPARE_SCRIPT:-${SCRIPT_DIR}/compare-safetensor-benchmark-summary.sh}"
BENCH_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model) MODEL_ID="$2"; shift 2 ;;
    --model=*) MODEL_ID="${1#*=}"; shift ;;
    --aqli-bin) AQLI_BIN="$2"; shift 2 ;;
    --aqli-bin=*) AQLI_BIN="${1#*=}"; shift ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --out-dir=*) OUT_DIR="${1#*=}"; shift ;;
    --case) CASE_NAME="$2"; shift 2 ;;
    --case=*) CASE_NAME="${1#*=}"; shift ;;
    --label-prefix) LABEL_PREFIX="$2"; shift 2 ;;
    --label-prefix=*) LABEL_PREFIX="${1#*=}"; shift ;;
    --baseline-label) BASELINE_LABEL="$2"; shift 2 ;;
    --baseline-label=*) BASELINE_LABEL="${1#*=}"; shift ;;
    --current-label) CURRENT_LABEL="$2"; shift 2 ;;
    --current-label=*) CURRENT_LABEL="${1#*=}"; shift ;;
    --baseline-preset) BASELINE_PRESET="$2"; shift 2 ;;
    --baseline-preset=*) BASELINE_PRESET="${1#*=}"; shift ;;
    --current-preset) CURRENT_PRESET="$2"; shift 2 ;;
    --current-preset=*) CURRENT_PRESET="${1#*=}"; shift ;;
    --max-tokens) MAX_TOKENS="$2"; shift 2 ;;
    --max-tokens=*) MAX_TOKENS="${1#*=}"; shift ;;
    --det-prompt) DET_PROMPT="$2"; shift 2 ;;
    --det-prompt=*) DET_PROMPT="${1#*=}"; shift ;;
    --normal-prompt) NORMAL_PROMPT="$2"; shift 2 ;;
    --normal-prompt=*) NORMAL_PROMPT="${1#*=}"; shift ;;
    --gate-metrics) GATE_METRICS="$2"; shift 2 ;;
    --gate-metrics=*) GATE_METRICS="${1#*=}"; shift ;;
    --max-regression-percent) MAX_REGRESSION_PERCENT="$2"; shift 2 ;;
    --max-regression-percent=*) MAX_REGRESSION_PERCENT="${1#*=}"; shift ;;
    --max-regression-ms) MAX_REGRESSION_MS="$2"; shift 2 ;;
    --max-regression-ms=*) MAX_REGRESSION_MS="${1#*=}"; shift ;;
    --min-baseline-value) MIN_BASELINE_VALUE="$2"; shift 2 ;;
    --min-baseline-value=*) MIN_BASELINE_VALUE="${1#*=}"; shift ;;
    --bench-arg) BENCH_ARGS+=("$2"); shift 2 ;;
    --bench-arg=*) BENCH_ARGS+=("${1#*=}"); shift ;;
    --bench-script) BENCH_SCRIPT="$2"; shift 2 ;;
    --bench-script=*) BENCH_SCRIPT="${1#*=}"; shift ;;
    --compare-script) COMPARE_SCRIPT="$2"; shift 2 ;;
    --compare-script=*) COMPARE_SCRIPT="${1#*=}"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$MODEL_ID" ]]; then
  echo "--model is required" >&2
  usage
  exit 2
fi
if [[ ! -f "$BENCH_SCRIPT" ]]; then
  echo "Benchmark script not found: $BENCH_SCRIPT" >&2
  exit 2
fi
if [[ ! -f "$COMPARE_SCRIPT" ]]; then
  echo "Comparison script not found: $COMPARE_SCRIPT" >&2
  exit 2
fi
for value_name in MAX_REGRESSION_PERCENT MAX_REGRESSION_MS MIN_BASELINE_VALUE; do
  value="${!value_name}"
  if [[ -n "$value" && ! "$value" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "Invalid ${value_name} value: $value" >&2
    exit 2
  fi
done

BASELINE_LABEL="${BASELINE_LABEL:-${LABEL_PREFIX}-fused}"
CURRENT_LABEL="${CURRENT_LABEL:-${LABEL_PREFIX}-row-prefill}"
RUNS_DIR="${OUT_DIR%/}/runs"
COMPARE_DIR="${OUT_DIR%/}/compare"
REPORT="${OUT_DIR%/}/report.txt"
BASELINE_STDOUT="${OUT_DIR%/}/baseline.stdout.log"
CURRENT_STDOUT="${OUT_DIR%/}/current.stdout.log"
COMPARE_STDOUT="${OUT_DIR%/}/compare.stdout.log"
BASELINE_RUN_DIR="${RUNS_DIR}/${BASELINE_LABEL}"
CURRENT_RUN_DIR="${RUNS_DIR}/${CURRENT_LABEL}"
BASELINE_SUMMARY="${BASELINE_RUN_DIR}/summary.json"
CURRENT_SUMMARY="${CURRENT_RUN_DIR}/summary.json"

mkdir -p "$OUT_DIR" "$RUNS_DIR" "$COMPARE_DIR"

run_benchmark() {
  local label="$1"
  local preset="$2"
  local stdout_file="$3"
  local run_name="$4"
  local -a cmd
  cmd=(
    bash "$BENCH_SCRIPT"
    --model "$MODEL_ID"
    --aqli-bin "$AQLI_BIN"
    --out-dir "$RUNS_DIR"
    --label "$label"
    --quick
    --cases "$CASE_NAME"
    --preset "$preset"
  )
  if [[ -n "$MAX_TOKENS" ]]; then
    cmd+=(--max-tokens "$MAX_TOKENS")
  fi
  if [[ -n "$DET_PROMPT" ]]; then
    cmd+=(--det-prompt "$DET_PROMPT")
  fi
  if [[ -n "$NORMAL_PROMPT" ]]; then
    cmd+=(--normal-prompt "$NORMAL_PROMPT")
  fi
  if (( ${#BENCH_ARGS[@]} > 0 )); then
    cmd+=("${BENCH_ARGS[@]}")
  fi

  echo "[ab] running ${run_name}: preset=${preset} label=${label}"
  if ! "${cmd[@]}" > "$stdout_file" 2>&1; then
    echo "[ab] ${run_name} benchmark failed; stdout/stderr follows:" >&2
    cat "$stdout_file" >&2
    exit 1
  fi
}

run_benchmark "$BASELINE_LABEL" "$BASELINE_PRESET" "$BASELINE_STDOUT" "baseline"
run_benchmark "$CURRENT_LABEL" "$CURRENT_PRESET" "$CURRENT_STDOUT" "current"

compare_cmd=(
  bash "$COMPARE_SCRIPT"
  --baseline "$BASELINE_RUN_DIR"
  --current "$CURRENT_RUN_DIR"
  --summary-dir "$COMPARE_DIR"
  --case "$CASE_NAME"
)
if [[ -n "$GATE_METRICS" ]]; then
  compare_cmd+=(--gate-metrics "$GATE_METRICS")
fi
if [[ -n "$MAX_REGRESSION_PERCENT" ]]; then
  compare_cmd+=(--max-regression-percent "$MAX_REGRESSION_PERCENT")
fi
if [[ -n "$MAX_REGRESSION_MS" ]]; then
  compare_cmd+=(--max-regression-ms "$MAX_REGRESSION_MS")
fi
if [[ -n "$MIN_BASELINE_VALUE" ]]; then
  compare_cmd+=(--min-baseline-value "$MIN_BASELINE_VALUE")
fi

compare_status=0
set +e
"${compare_cmd[@]}" > "$COMPARE_STDOUT" 2>&1
compare_status=$?
set -e

cat "$COMPARE_STDOUT"

summary_value() {
  local key="$1"
  local file="${COMPARE_DIR}/summary.tsv"
  if [[ -f "$file" ]]; then
    awk -v key="$key" 'BEGIN { FS = "\t" } $1 == key { print $2; exit }' "$file"
  fi
}

STATUS="$(summary_value status)"
REASON="$(summary_value reason)"
RECOMMENDATION="$(summary_value recommendation)"
BASELINE_STRATEGY="$(summary_value baselineFfnStrategy)"
CURRENT_STRATEGY="$(summary_value currentFfnStrategy)"
BASELINE_ROW="$(summary_value baselineRowPrefill)"
CURRENT_ROW="$(summary_value currentRowPrefill)"
GATED_METRICS="$(summary_value gatedMetrics)"
FAILED_METRICS="$(summary_value failedMetrics)"
LARGEST_IMPROVEMENT_METRIC="$(summary_value largestImprovementMetric)"
LARGEST_IMPROVEMENT_PERCENT="$(summary_value largestImprovementPercent)"
LARGEST_REGRESSION_METRIC="$(summary_value largestRegressionMetric)"
LARGEST_REGRESSION_PERCENT="$(summary_value largestRegressionPercent)"

{
  echo "Aqli Gemma4 safetensor row-prefill A/B"
  echo "outDir=$OUT_DIR"
  echo "runsDir=$RUNS_DIR"
  echo "compareDir=$COMPARE_DIR"
  echo "case=$CASE_NAME"
  echo "baselinePreset=$BASELINE_PRESET"
  echo "currentPreset=$CURRENT_PRESET"
  echo "artifacts.baselineSummary=$BASELINE_SUMMARY"
  echo "artifacts.currentSummary=$CURRENT_SUMMARY"
  echo "artifacts.baselineLog=$BASELINE_STDOUT"
  echo "artifacts.currentLog=$CURRENT_STDOUT"
  echo "artifacts.compareLog=$COMPARE_STDOUT"
  echo "artifacts.compareSummary=${COMPARE_DIR}/summary.tsv"
  echo "artifacts.compareTable=${COMPARE_DIR}/comparison-table.txt"
  echo "artifacts.compareMarkdown=${COMPARE_DIR}/summary.md"
  echo "artifacts.compareDecision=${COMPARE_DIR}/decision.json"
  echo "status=${STATUS:-unknown}"
  echo "reason=${REASON:-}"
  echo "recommendation=${RECOMMENDATION:-unknown}"
  echo "gatedMetrics=${GATED_METRICS:-0}"
  echo "failedMetrics=${FAILED_METRICS:-0}"
  echo "largestImprovement=${LARGEST_IMPROVEMENT_METRIC:-}:${LARGEST_IMPROVEMENT_PERCENT:-}%"
  echo "largestRegression=${LARGEST_REGRESSION_METRIC:-}:${LARGEST_REGRESSION_PERCENT:-}%"
  echo "ffnStrategyTransition=${BASELINE_STRATEGY:-unknown}->${CURRENT_STRATEGY:-unknown}"
  echo "rowPrefillTransition=${BASELINE_ROW:-unknown}->${CURRENT_ROW:-unknown}"
} | tee "$REPORT"

exit "$compare_status"
