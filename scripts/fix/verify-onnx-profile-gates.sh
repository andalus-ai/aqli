#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: verify-onnx-profile-gates.sh --model MODEL [options]

Run the ONNX profile benchmark and evaluate optional performance thresholds
against its TSV summary. The benchmark remains the raw measurement tool; this
script is the regression gate that produces pass/fail contracts.

Options:
  --model ID                    Model id or local alias (required)
  --prompt TEXT                 Prompt to run (default: "where is jakarta")
  --max-tokens N                Max generated tokens per run (default: 8)
  --runs N                      Measured runs after warmup (default: 1)
  --warmup-runs N               Warmup runs before measured checks (default: 0)
  --aqli-bin PATH             Aqli executable (default: ~/.local/bin/aqli)
  --bench-script PATH           Benchmark script (default: scripts/bench-onnx-profile.sh)
  --summary-dir DIR             Output directory for gate artifacts (default: temp dir)
  --decision-out PATH           Decision summary artifact (default: summary-dir/decision.tsv)
  --decision-script PATH        Override decision summary helper
  --bundle-script PATH          Override artifact bundle helper
  --require-profile             Require ONNX profile lines (default)
  --no-require-profile          Allow successful benchmark rows without profile lines
  --expect-backend NAME         Require each benchmark run to report this ONNX backend

Thresholds:
  --max-duration-ms N
  --min-generation-tps N
  --min-decode-tps N
  --max-ttft-ms N
  --max-token-latency-ms N
  --max-onnx-tokenize-ms N
  --max-onnx-input-prep-ms N
  --max-onnx-prefill-run-ms N
  --max-onnx-decode-run-ms N
  --max-onnx-ort-run-ms N
  --max-onnx-logits-select-ms N
  --max-onnx-sampling-ms N

Other:
  --include-warmup-contracts    Evaluate warmup rows too
  --slowest-limit N             Number of slowest rows to report (default: 5)
  --help                        Show this help

Examples:
  ./aqli/scripts/verify-onnx-profile-gates.sh --model 6b6e13 --max-tokens 4
  ./aqli/scripts/verify-onnx-profile-gates.sh --model 6b6e13 --max-onnx-decode-run-ms 200 --min-generation-tps 2
USAGE
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_ID=""
PROMPT="${AQLI_VERIFY_ONNX_PROMPT:-where is jakarta}"
MAX_TOKENS="${AQLI_VERIFY_ONNX_MAX_TOKENS:-8}"
RUNS="${AQLI_VERIFY_ONNX_RUNS:-1}"
WARMUP_RUNS="${AQLI_VERIFY_ONNX_WARMUP_RUNS:-0}"
AQLI_BIN="${HOME}/.local/bin/aqli"
BENCH_SCRIPT="${ROOT_DIR}/scripts/bench-onnx-profile.sh"
SUMMARY_DIR=""
DECISION_OUT=""
DECISION_SCRIPT="${ROOT_DIR}/scripts/onnx-performance-decision.sh"
BUNDLE_SCRIPT="${ROOT_DIR}/scripts/onnx-performance-bundle.sh"
REQUIRE_PROFILE=1
EXPECT_BACKEND="${AQLI_VERIFY_ONNX_EXPECT_BACKEND:-${AQLI_ONNX_EXPECT_BACKEND:-}}"
INCLUDE_WARMUP_CONTRACTS=0
SLOWEST_LIMIT="${AQLI_VERIFY_ONNX_SLOWEST_LIMIT:-5}"

MAX_DURATION_MS="${AQLI_VERIFY_ONNX_MAX_DURATION_MS:-}"
MIN_GENERATION_TPS="${AQLI_VERIFY_ONNX_MIN_GENERATION_TPS:-}"
MIN_DECODE_TPS="${AQLI_VERIFY_ONNX_MIN_DECODE_TPS:-}"
MAX_TTFT_MS="${AQLI_VERIFY_ONNX_MAX_TTFT_MS:-}"
MAX_TOKEN_LATENCY_MS="${AQLI_VERIFY_ONNX_MAX_TOKEN_LATENCY_MS:-}"
MAX_ONNX_TOKENIZE_MS="${AQLI_VERIFY_ONNX_MAX_TOKENIZE_MS:-}"
MAX_ONNX_INPUT_PREP_MS="${AQLI_VERIFY_ONNX_MAX_INPUT_PREP_MS:-}"
MAX_ONNX_PREFILL_RUN_MS="${AQLI_VERIFY_ONNX_MAX_PREFILL_RUN_MS:-}"
MAX_ONNX_DECODE_RUN_MS="${AQLI_VERIFY_ONNX_MAX_DECODE_RUN_MS:-}"
MAX_ONNX_ORT_RUN_MS="${AQLI_VERIFY_ONNX_MAX_ORT_RUN_MS:-}"
MAX_ONNX_LOGITS_SELECT_MS="${AQLI_VERIFY_ONNX_MAX_LOGITS_SELECT_MS:-}"
MAX_ONNX_SAMPLING_MS="${AQLI_VERIFY_ONNX_MAX_SAMPLING_MS:-}"

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
    --bench-script) BENCH_SCRIPT="$2"; shift 2 ;;
    --bench-script=*) BENCH_SCRIPT="${1#*=}"; shift ;;
    --summary-dir) SUMMARY_DIR="$2"; shift 2 ;;
    --summary-dir=*) SUMMARY_DIR="${1#*=}"; shift ;;
    --decision-out) DECISION_OUT="$2"; shift 2 ;;
    --decision-out=*) DECISION_OUT="${1#*=}"; shift ;;
    --decision-script) DECISION_SCRIPT="$2"; shift 2 ;;
    --decision-script=*) DECISION_SCRIPT="${1#*=}"; shift ;;
    --bundle-script) BUNDLE_SCRIPT="$2"; shift 2 ;;
    --bundle-script=*) BUNDLE_SCRIPT="${1#*=}"; shift ;;
    --require-profile) REQUIRE_PROFILE=1; shift ;;
    --no-require-profile) REQUIRE_PROFILE=0; shift ;;
    --expect-backend) EXPECT_BACKEND="$2"; shift 2 ;;
    --expect-backend=*) EXPECT_BACKEND="${1#*=}"; shift ;;
    --include-warmup-contracts) INCLUDE_WARMUP_CONTRACTS=1; shift ;;
    --slowest-limit) SLOWEST_LIMIT="$2"; shift 2 ;;
    --slowest-limit=*) SLOWEST_LIMIT="${1#*=}"; shift ;;
    --max-duration-ms) MAX_DURATION_MS="$2"; shift 2 ;;
    --max-duration-ms=*) MAX_DURATION_MS="${1#*=}"; shift ;;
    --min-generation-tps) MIN_GENERATION_TPS="$2"; shift 2 ;;
    --min-generation-tps=*) MIN_GENERATION_TPS="${1#*=}"; shift ;;
    --min-decode-tps) MIN_DECODE_TPS="$2"; shift 2 ;;
    --min-decode-tps=*) MIN_DECODE_TPS="${1#*=}"; shift ;;
    --max-ttft-ms) MAX_TTFT_MS="$2"; shift 2 ;;
    --max-ttft-ms=*) MAX_TTFT_MS="${1#*=}"; shift ;;
    --max-token-latency-ms) MAX_TOKEN_LATENCY_MS="$2"; shift 2 ;;
    --max-token-latency-ms=*) MAX_TOKEN_LATENCY_MS="${1#*=}"; shift ;;
    --max-onnx-tokenize-ms) MAX_ONNX_TOKENIZE_MS="$2"; shift 2 ;;
    --max-onnx-tokenize-ms=*) MAX_ONNX_TOKENIZE_MS="${1#*=}"; shift ;;
    --max-onnx-input-prep-ms) MAX_ONNX_INPUT_PREP_MS="$2"; shift 2 ;;
    --max-onnx-input-prep-ms=*) MAX_ONNX_INPUT_PREP_MS="${1#*=}"; shift ;;
    --max-onnx-prefill-run-ms) MAX_ONNX_PREFILL_RUN_MS="$2"; shift 2 ;;
    --max-onnx-prefill-run-ms=*) MAX_ONNX_PREFILL_RUN_MS="${1#*=}"; shift ;;
    --max-onnx-decode-run-ms) MAX_ONNX_DECODE_RUN_MS="$2"; shift 2 ;;
    --max-onnx-decode-run-ms=*) MAX_ONNX_DECODE_RUN_MS="${1#*=}"; shift ;;
    --max-onnx-ort-run-ms) MAX_ONNX_ORT_RUN_MS="$2"; shift 2 ;;
    --max-onnx-ort-run-ms=*) MAX_ONNX_ORT_RUN_MS="${1#*=}"; shift ;;
    --max-onnx-logits-select-ms) MAX_ONNX_LOGITS_SELECT_MS="$2"; shift 2 ;;
    --max-onnx-logits-select-ms=*) MAX_ONNX_LOGITS_SELECT_MS="${1#*=}"; shift ;;
    --max-onnx-sampling-ms) MAX_ONNX_SAMPLING_MS="$2"; shift 2 ;;
    --max-onnx-sampling-ms=*) MAX_ONNX_SAMPLING_MS="${1#*=}"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$MODEL_ID" ]]; then
  echo "--model is required" >&2
  usage
  exit 2
fi
for value_name in MAX_TOKENS RUNS WARMUP_RUNS SLOWEST_LIMIT; do
  value="${!value_name}"
  if [[ ! "$value" =~ ^[0-9]+$ ]] || { [[ "$value_name" != "WARMUP_RUNS" ]] && (( value < 1 )); }; then
    echo "Invalid ${value_name} value: $value" >&2
    exit 2
  fi
done
for threshold_name in \
  MAX_DURATION_MS MIN_GENERATION_TPS MIN_DECODE_TPS MAX_TTFT_MS MAX_TOKEN_LATENCY_MS \
  MAX_ONNX_TOKENIZE_MS MAX_ONNX_INPUT_PREP_MS MAX_ONNX_PREFILL_RUN_MS \
  MAX_ONNX_DECODE_RUN_MS MAX_ONNX_ORT_RUN_MS MAX_ONNX_LOGITS_SELECT_MS MAX_ONNX_SAMPLING_MS; do
  threshold="${!threshold_name}"
  if [[ -n "$threshold" && ! "$threshold" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "Invalid ${threshold_name} value: $threshold" >&2
    exit 2
  fi
done
if [[ ! -x "$BENCH_SCRIPT" ]]; then
  echo "Benchmark script not found or not executable: $BENCH_SCRIPT" >&2
  exit 2
fi
if [[ ! -x "$DECISION_SCRIPT" ]]; then
  echo "Required decision summary helper not found or not executable: $DECISION_SCRIPT" >&2
  exit 2
fi
if [[ ! -f "$BUNDLE_SCRIPT" ]]; then
  echo "Required artifact bundle helper not found: $BUNDLE_SCRIPT" >&2
  exit 2
fi
if [[ ! -x "$AQLI_BIN" ]]; then
  echo "aqli executable not found or not executable: $AQLI_BIN" >&2
  exit 2
fi
for cmd in awk date mkdir mktemp tee; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

# shellcheck source=/dev/null
source "$BUNDLE_SCRIPT"

if [[ -z "$SUMMARY_DIR" ]]; then
  SUMMARY_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-onnx-profile-gates.XXXXXX")"
else
  mkdir -p "$SUMMARY_DIR"
fi

BENCH_OUT_DIR="${SUMMARY_DIR}/bench"
BENCH_LABEL="profile"
BENCH_RUN_DIR="${BENCH_OUT_DIR}/${BENCH_LABEL}"
BENCH_SUMMARY="${BENCH_RUN_DIR}/summary.tsv"
CONFIG="${SUMMARY_DIR}/config.tsv"
RESULTS="${SUMMARY_DIR}/results.tsv"
CONTRACTS="${SUMMARY_DIR}/contracts.tsv"
SLOWEST="${SUMMARY_DIR}/slowest.tsv"
DECISION="${DECISION_OUT:-${SUMMARY_DIR}/decision.tsv}"
BUNDLE="${SUMMARY_DIR}/bundle.tsv"
BUNDLE_JSON="${SUMMARY_DIR}/bundle.json"
if [[ "$DECISION" == *.tsv ]]; then
  DECISION_JSON="${DECISION%.tsv}.json"
else
  DECISION_JSON="${DECISION}.json"
fi
REPORT="${SUMMARY_DIR}/report.txt"
BENCH_STDOUT="${SUMMARY_DIR}/bench.stdout.log"
BENCH_STDERR="${SUMMARY_DIR}/bench.stderr.log"
BENCH_ARGV="${SUMMARY_DIR}/bench.argv.tsv"

if [[ -n "$DECISION_OUT" ]]; then
  decision_parent="${DECISION%/*}"
  if [[ "$decision_parent" != "$DECISION" ]]; then
    mkdir -p "$decision_parent"
  fi
fi

safe_tsv_field() {
  local value="${1:-}"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

config_row() {
  printf '%s\t%s\n' "$(safe_tsv_field "$1")" "$(safe_tsv_field "${2:-}")" >> "$CONFIG"
}

write_argv_log() {
  local index=0
  printf 'index\targ\n' > "$BENCH_ARGV"
  while [[ $# -gt 0 ]]; do
    printf '%s\t%s\n' "$index" "$(safe_tsv_field "$1")" >> "$BENCH_ARGV"
    index=$((index + 1))
    shift
  done
}

write_decision() {
  "$DECISION_SCRIPT" \
    --config "$CONFIG" \
    --results "$RESULTS" \
    --out "$DECISION" \
    --json-out "$DECISION_JSON" \
    --contracts "$CONTRACTS" \
    --bundle "$BUNDLE" >/dev/null
}

write_gate_bundle() {
  aqli_onnx_performance_bundle_init "$BUNDLE"
  aqli_onnx_performance_bundle_add "$BUNDLE" config "$CONFIG" required "Gate configuration"
  aqli_onnx_performance_bundle_add "$BUNDLE" results "$RESULTS" required "Stage results"
  aqli_onnx_performance_bundle_add "$BUNDLE" benchSummary "$BENCH_SUMMARY" required "Raw benchmark summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" contracts "$CONTRACTS" required "Threshold contract evidence"
  aqli_onnx_performance_bundle_add "$BUNDLE" slowest "$SLOWEST" optional "Slowest benchmark rows"
  aqli_onnx_performance_bundle_add "$BUNDLE" decision "$DECISION" required "Gate decision summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" decisionJson "$DECISION_JSON" optional "Gate decision JSON"
  aqli_onnx_performance_bundle_add "$BUNDLE" benchStdout "$BENCH_STDOUT" optional "Benchmark stdout"
  aqli_onnx_performance_bundle_add "$BUNDLE" benchStderr "$BENCH_STDERR" optional "Benchmark stderr"
  aqli_onnx_performance_bundle_add "$BUNDLE" benchArgv "$BENCH_ARGV" optional "Benchmark argv"
  aqli_onnx_performance_bundle_write_json "$BUNDLE" "$BUNDLE_JSON"
}

declare -a BENCH_ARGS=(
  "$BENCH_SCRIPT"
  "--model" "$MODEL_ID"
  "--prompt" "$PROMPT"
  "--max-tokens" "$MAX_TOKENS"
  "--runs" "$RUNS"
  "--warmup-runs" "$WARMUP_RUNS"
  "--aqli-bin" "$AQLI_BIN"
  "--out-dir" "$BENCH_OUT_DIR"
  "--label" "$BENCH_LABEL"
)
if [[ "$REQUIRE_PROFILE" -eq 1 ]]; then
  BENCH_ARGS+=("--require-profile")
else
  BENCH_ARGS+=("--no-require-profile")
fi
if [[ -n "$EXPECT_BACKEND" ]]; then
  BENCH_ARGS+=("--expect-backend" "$EXPECT_BACKEND")
fi

{
  printf 'key\tvalue\n'
} > "$CONFIG"
config_row generatedAt "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
config_row rootDir "$ROOT_DIR"
config_row model "$MODEL_ID"
config_row prompt "$PROMPT"
config_row maxTokens "$MAX_TOKENS"
config_row runs "$RUNS"
config_row warmupRuns "$WARMUP_RUNS"
config_row aqliBin "$AQLI_BIN"
config_row benchScript "$BENCH_SCRIPT"
config_row benchSummary "$BENCH_SUMMARY"
config_row results "$RESULTS"
config_row contracts "$CONTRACTS"
config_row slowest "$SLOWEST"
config_row decision "$DECISION"
config_row decisionJson "$DECISION_JSON"
config_row decisionScript "$DECISION_SCRIPT"
config_row bundle "$BUNDLE"
config_row bundleJson "$BUNDLE_JSON"
config_row bundleScript "$BUNDLE_SCRIPT"
config_row requireProfile "$REQUIRE_PROFILE"
config_row expectBackend "$EXPECT_BACKEND"
config_row includeWarmupContracts "$INCLUDE_WARMUP_CONTRACTS"
config_row slowestLimit "$SLOWEST_LIMIT"
config_row maxDurationMs "$MAX_DURATION_MS"
config_row minGenerationTps "$MIN_GENERATION_TPS"
config_row minDecodeTps "$MIN_DECODE_TPS"
config_row maxTtftMs "$MAX_TTFT_MS"
config_row maxTokenLatencyMs "$MAX_TOKEN_LATENCY_MS"
config_row maxOnnxTokenizeMs "$MAX_ONNX_TOKENIZE_MS"
config_row maxOnnxInputPrepMs "$MAX_ONNX_INPUT_PREP_MS"
config_row maxOnnxPrefillRunMs "$MAX_ONNX_PREFILL_RUN_MS"
config_row maxOnnxDecodeRunMs "$MAX_ONNX_DECODE_RUN_MS"
config_row maxOnnxOrtRunMs "$MAX_ONNX_ORT_RUN_MS"
config_row maxOnnxLogitsSelectMs "$MAX_ONNX_LOGITS_SELECT_MS"
config_row maxOnnxSamplingMs "$MAX_ONNX_SAMPLING_MS"

write_argv_log "${BENCH_ARGS[@]}"
printf 'stage\tstatus\texitCode\tartifact\tstdout\tstderr\treason\n' > "$RESULTS"

set +e
"${BENCH_ARGS[@]}" >"$BENCH_STDOUT" 2>"$BENCH_STDERR"
BENCH_EXIT=$?
set -e
if [[ "$BENCH_EXIT" -eq 0 ]]; then
  printf 'benchmark\tpass\t0\t%s\t%s\t%s\t\n' "$BENCH_SUMMARY" "$BENCH_STDOUT" "$BENCH_STDERR" >> "$RESULTS"
else
  printf 'benchmark\tfail\t%s\t%s\t%s\t%s\tbenchmark-failed\n' "$BENCH_EXIT" "$BENCH_SUMMARY" "$BENCH_STDOUT" "$BENCH_STDERR" >> "$RESULTS"
fi

if [[ ! -f "$BENCH_SUMMARY" ]]; then
  write_decision
  write_gate_bundle
  write_decision
  {
    echo "ONNX profile benchmark summary was not produced."
    echo "summaryDir=$SUMMARY_DIR"
    echo "artifacts.config=$CONFIG"
    echo "artifacts.results=$RESULTS"
    echo "artifacts.decision=$DECISION"
    echo "artifacts.decisionJson=$DECISION_JSON"
    echo "artifacts.bundle=$BUNDLE"
    echo "artifacts.bundleJson=$BUNDLE_JSON"
    echo "artifacts.benchStdout=$BENCH_STDOUT"
    echo "artifacts.benchStderr=$BENCH_STDERR"
  } | tee "$REPORT" >&2
  exit "$([[ "$BENCH_EXIT" -eq 0 ]] && printf 3 || printf '%s' "$BENCH_EXIT")"
fi

awk \
  -v includeWarmup="$INCLUDE_WARMUP_CONTRACTS" \
  -v maxDurationMs="$MAX_DURATION_MS" \
  -v minGenerationTps="$MIN_GENERATION_TPS" \
  -v minDecodeTps="$MIN_DECODE_TPS" \
  -v maxTtftMs="$MAX_TTFT_MS" \
  -v maxTokenLatencyMs="$MAX_TOKEN_LATENCY_MS" \
  -v maxOnnxTokenizeMs="$MAX_ONNX_TOKENIZE_MS" \
  -v maxOnnxInputPrepMs="$MAX_ONNX_INPUT_PREP_MS" \
  -v maxOnnxPrefillRunMs="$MAX_ONNX_PREFILL_RUN_MS" \
  -v maxOnnxDecodeRunMs="$MAX_ONNX_DECODE_RUN_MS" \
  -v maxOnnxOrtRunMs="$MAX_ONNX_ORT_RUN_MS" \
  -v maxOnnxLogitsSelectMs="$MAX_ONNX_LOGITS_SELECT_MS" \
  -v maxOnnxSamplingMs="$MAX_ONNX_SAMPLING_MS" '
  BEGIN {
    FS = OFS = "\t"
    print "case", "metric", "actual", "operator", "threshold", "status", "reason"
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      column[$i] = i
    }
    next
  }
  function numeric(value) {
    return value ~ /^-?[0-9]+([.][0-9]+)?$/
  }
  function gate(caseName, metric, columnName, op, threshold, actual, ok, reason) {
    if (threshold == "") {
      return
    }
    actual = $(column[columnName])
    reason = ""
    if (!numeric(actual)) {
      ok = 0
      reason = "missing-metric"
    } else if (op == "<=") {
      ok = (actual + 0 <= threshold + 0)
    } else {
      ok = (actual + 0 >= threshold + 0)
    }
    print caseName, metric, actual, op, threshold, ok ? "pass" : "fail", reason
  }
  NF > 0 {
    caseName = $1
    if (includeWarmup != "1" && caseName ~ /^warmup-/) {
      next
    }
    if ($(column["status"]) != "pass") {
      print caseName, "benchmarkStatus", $(column["status"]), "=", "pass", "fail", "benchmark-row-failed"
    }
    gate(caseName, "durationMs", "durationMs", "<=", maxDurationMs)
    gate(caseName, "generationTps", "generationTps", ">=", minGenerationTps)
    gate(caseName, "decodeTps", "decodeTps", ">=", minDecodeTps)
    gate(caseName, "ttftMs", "ttftMs", "<=", maxTtftMs)
    gate(caseName, "tokenLatencyMs", "tokenLatencyMs", "<=", maxTokenLatencyMs)
    gate(caseName, "onnxTokenizeMs", "onnxTokenizeMs", "<=", maxOnnxTokenizeMs)
    gate(caseName, "onnxInputPrepMs", "onnxInputPrepMs", "<=", maxOnnxInputPrepMs)
    gate(caseName, "onnxPrefillRunMs", "onnxPrefillRunMs", "<=", maxOnnxPrefillRunMs)
    gate(caseName, "onnxDecodeRunMs", "onnxDecodeRunMs", "<=", maxOnnxDecodeRunMs)
    gate(caseName, "onnxOrtRunMs", "onnxOrtRunMs", "<=", maxOnnxOrtRunMs)
    gate(caseName, "onnxLogitsSelectMs", "onnxLogitsSelectMs", "<=", maxOnnxLogitsSelectMs)
    gate(caseName, "onnxSamplingMs", "onnxSamplingMs", "<=", maxOnnxSamplingMs)
  }
' "$BENCH_SUMMARY" > "$CONTRACTS"

awk -v limit="$SLOWEST_LIMIT" '
  BEGIN {
    FS = OFS = "\t"
    print "rank", "case", "durationMs", "generationTps", "onnxBackend", "onnxOrtRunMs", "onnxDecodeRunMs", "combinedLog"
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      column[$i] = i
    }
    next
  }
  NF > 0 && $(column["durationMs"]) ~ /^[0-9]+([.][0-9]+)?$/ {
    count++
    duration[count] = $(column["durationMs"]) + 0
    row[count] = $0
  }
  END {
    maxRank = (count < limit) ? count : limit
    for (rank = 1; rank <= maxRank; rank++) {
      best = 0
      for (i = 1; i <= count; i++) {
        if (used[i]) {
          continue
        }
        if (best == 0 || duration[i] > duration[best]) {
          best = i
        }
      }
      if (best == 0) {
        break
      }
      used[best] = 1
      split(row[best], fields, FS)
      print rank, fields[column["case"]], fields[column["durationMs"]], fields[column["generationTps"]], fields[column["onnxBackend"]], fields[column["onnxOrtRunMs"]], fields[column["onnxDecodeRunMs"]], fields[column["combinedLog"]]
    }
  }
' "$BENCH_SUMMARY" > "$SLOWEST"

CONTRACT_FAILURES="$(awk 'BEGIN { FS = "\t" } NR > 1 && $6 != "pass" { count++ } END { print count + 0 }' "$CONTRACTS")"
FINAL_EXIT="$BENCH_EXIT"
if [[ "$CONTRACT_FAILURES" -gt 0 && "$FINAL_EXIT" -eq 0 ]]; then
  FINAL_EXIT=42
fi
if [[ "$FINAL_EXIT" -eq 0 ]]; then
  printf 'contracts\tpass\t0\t%s\t%s\t%s\t\n' "$CONTRACTS" "$BENCH_STDOUT" "$BENCH_STDERR" >> "$RESULTS"
else
  printf 'contracts\tfail\t%s\t%s\t%s\t%s\tcontract-failures=%s\n' "$FINAL_EXIT" "$CONTRACTS" "$BENCH_STDOUT" "$BENCH_STDERR" "$CONTRACT_FAILURES" >> "$RESULTS"
fi
write_decision
write_gate_bundle
write_decision

{
  echo "Aqli ONNX profile gates"
  echo "summaryDir=$SUMMARY_DIR"
  echo "artifacts.config=$CONFIG"
  echo "artifacts.results=$RESULTS"
  echo "artifacts.decision=$DECISION"
  echo "artifacts.decisionJson=$DECISION_JSON"
  echo "artifacts.bundle=$BUNDLE"
  echo "artifacts.bundleJson=$BUNDLE_JSON"
  echo "artifacts.contracts=$CONTRACTS"
  echo "artifacts.slowest=$SLOWEST"
  echo "artifacts.benchSummary=$BENCH_SUMMARY"
  echo "artifacts.benchStdout=$BENCH_STDOUT"
  echo "artifacts.benchStderr=$BENCH_STDERR"
  echo
  if [[ "$CONTRACT_FAILURES" -gt 0 ]]; then
    echo "contract failures:"
    awk 'BEGIN { FS = "\t" } NR > 1 && $6 != "pass" { printf "  %s %s actual=%s %s %s reason=%s\n", $1, $2, $3, $4, $5, $7 }' "$CONTRACTS"
  else
    echo "contracts: pass"
  fi
  if awk 'NR > 1 { found = 1 } END { exit found ? 0 : 1 }' "$SLOWEST"; then
    echo "slowest:"
    awk 'BEGIN { FS = "\t" } NR > 1 { printf "  #%s %s duration=%sms generation=%s backend=%s ort=%s decode=%s log=%s\n", $1, $2, $3, $4, $5, $6, $7, $8 }' "$SLOWEST"
  fi
} | tee "$REPORT"

exit "$FINAL_EXIT"
