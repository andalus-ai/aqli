#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: capture-safetensor-profile-baseline.sh --model MODEL [options]

Run a safetensor profile benchmark and store the diagnosis stages as a reusable
baseline for verify-fast-speed-gates.sh regression checks.

Options:
  --model ID                  Model id or local alias (required)
  --aqli-bin PATH           Aqli executable (default: ~/.local/bin/aqli)
  --baseline-root DIR         Baseline root (default: aqli/ops/benchmarks/safetensor/baselines)
  --name NAME                 Baseline model directory name (default: sanitized model id)
  --run-label NAME            Baseline run directory name (default: UTC timestamp)
  --prompt TEXT               Deterministic prompt (default: "where is jakarta")
  --max-tokens N              Max tokens per run (default: 2)
  --quick                     Deterministic-only benchmark plan (default)
  --full                      Run the benchmark script default plan
  --with-cpu                  Include CPU reference runs
  --with-quantize-turbo       Include turbo quantized runs
  --cases LIST                Comma-separated benchmark cases to run
  --require-metal             Require Metal proof for non-CPU cases
  --no-require-metal          Do not require Metal proof
  --require-profile           Require [PROFILE] output (default)
  --no-require-profile        Allow missing profile output
  --max-top-stage-ms N        Pass slowest-stage threshold to benchmark
  --max-prefill-ms N          Pass prefill threshold to benchmark
  --max-decode-ms N           Pass decode threshold to benchmark
  --max-tpot-ms N             Pass token latency threshold to benchmark
  --max-attention-ms N        Pass attention threshold to benchmark
  --max-ffn-ms N              Pass FFN threshold to benchmark
  --max-logits-ms N           Pass logits threshold to benchmark
  --no-update-latest          Do not update latest-* pointers
  --bench-script PATH         Override bench-safetensor-inference.sh
  --help                      Show this help

Example:
  ./aqli/scripts/capture-safetensor-profile-baseline.sh --model 6f469a --require-metal
  ./aqli/scripts/verify-fast-speed-gates.sh --only safetensor --safetensor-baseline-stages latest --safetensor-baseline-name 6f469a
USAGE
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_ID=""
AQLI_BIN="${HOME}/.local/bin/aqli"
BASELINE_ROOT="${AQLI_CAPTURE_SAFETENSOR_BASELINE_ROOT:-${ROOT_DIR}/ops/benchmarks/safetensor/baselines}"
BASELINE_NAME=""
RUN_LABEL=""
PROMPT="${AQLI_VERIFY_PROMPT:-where is jakarta}"
MAX_TOKENS="${AQLI_VERIFY_SAFETENSOR_MAX_TOKENS:-2}"
QUICK_MODE=1
WITH_CPU=0
WITH_QUANT_TURBO=0
CASES_FILTER=""
REQUIRE_PROFILE=1
case "$(uname -s)" in
  Darwin) REQUIRE_METAL=1 ;;
  *) REQUIRE_METAL=0 ;;
esac
MAX_TOP_STAGE_MS="${AQLI_VERIFY_SAFETENSOR_TOP_STAGE_THRESHOLD_MS:-}"
MAX_PREFILL_MS="${AQLI_VERIFY_SAFETENSOR_PREFILL_THRESHOLD_MS:-}"
MAX_DECODE_MS="${AQLI_VERIFY_SAFETENSOR_DECODE_THRESHOLD_MS:-}"
MAX_TPOT_MS="${AQLI_VERIFY_SAFETENSOR_TPOT_THRESHOLD_MS:-}"
MAX_ATTENTION_MS="${AQLI_VERIFY_SAFETENSOR_ATTENTION_THRESHOLD_MS:-}"
MAX_FFN_MS="${AQLI_VERIFY_SAFETENSOR_FFN_THRESHOLD_MS:-}"
MAX_LOGITS_MS="${AQLI_VERIFY_SAFETENSOR_LOGITS_THRESHOLD_MS:-}"
UPDATE_LATEST=1
BENCH_SCRIPT="${ROOT_DIR}/scripts/bench-safetensor-inference.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model) MODEL_ID="$2"; shift 2 ;;
    --model=*) MODEL_ID="${1#*=}"; shift ;;
    --aqli-bin) AQLI_BIN="$2"; shift 2 ;;
    --aqli-bin=*) AQLI_BIN="${1#*=}"; shift ;;
    --baseline-root) BASELINE_ROOT="$2"; shift 2 ;;
    --baseline-root=*) BASELINE_ROOT="${1#*=}"; shift ;;
    --name) BASELINE_NAME="$2"; shift 2 ;;
    --name=*) BASELINE_NAME="${1#*=}"; shift ;;
    --run-label) RUN_LABEL="$2"; shift 2 ;;
    --run-label=*) RUN_LABEL="${1#*=}"; shift ;;
    --prompt) PROMPT="$2"; shift 2 ;;
    --prompt=*) PROMPT="${1#*=}"; shift ;;
    --max-tokens) MAX_TOKENS="$2"; shift 2 ;;
    --max-tokens=*) MAX_TOKENS="${1#*=}"; shift ;;
    --quick) QUICK_MODE=1; shift ;;
    --full) QUICK_MODE=0; shift ;;
    --with-cpu) WITH_CPU=1; shift ;;
    --with-quantize-turbo) WITH_QUANT_TURBO=1; shift ;;
    --cases) CASES_FILTER="$2"; shift 2 ;;
    --cases=*) CASES_FILTER="${1#*=}"; shift ;;
    --require-metal) REQUIRE_METAL=1; shift ;;
    --no-require-metal) REQUIRE_METAL=0; shift ;;
    --require-profile) REQUIRE_PROFILE=1; shift ;;
    --no-require-profile) REQUIRE_PROFILE=0; shift ;;
    --max-top-stage-ms) MAX_TOP_STAGE_MS="$2"; shift 2 ;;
    --max-top-stage-ms=*) MAX_TOP_STAGE_MS="${1#*=}"; shift ;;
    --max-prefill-ms) MAX_PREFILL_MS="$2"; shift 2 ;;
    --max-prefill-ms=*) MAX_PREFILL_MS="${1#*=}"; shift ;;
    --max-decode-ms) MAX_DECODE_MS="$2"; shift 2 ;;
    --max-decode-ms=*) MAX_DECODE_MS="${1#*=}"; shift ;;
    --max-tpot-ms) MAX_TPOT_MS="$2"; shift 2 ;;
    --max-tpot-ms=*) MAX_TPOT_MS="${1#*=}"; shift ;;
    --max-attention-ms) MAX_ATTENTION_MS="$2"; shift 2 ;;
    --max-attention-ms=*) MAX_ATTENTION_MS="${1#*=}"; shift ;;
    --max-ffn-ms) MAX_FFN_MS="$2"; shift 2 ;;
    --max-ffn-ms=*) MAX_FFN_MS="${1#*=}"; shift ;;
    --max-logits-ms) MAX_LOGITS_MS="$2"; shift 2 ;;
    --max-logits-ms=*) MAX_LOGITS_MS="${1#*=}"; shift ;;
    --no-update-latest) UPDATE_LATEST=0; shift ;;
    --bench-script) BENCH_SCRIPT="$2"; shift 2 ;;
    --bench-script=*) BENCH_SCRIPT="${1#*=}"; shift ;;
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
for value_name in MAX_TOP_STAGE_MS MAX_PREFILL_MS MAX_DECODE_MS MAX_TPOT_MS MAX_ATTENTION_MS MAX_FFN_MS MAX_LOGITS_MS; do
  value="${!value_name}"
  if [[ -n "$value" && ! "$value" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "Invalid ${value_name} value: $value" >&2
    exit 2
  fi
done
if [[ ! -f "$BENCH_SCRIPT" ]]; then
  echo "Required benchmark script not found: $BENCH_SCRIPT" >&2
  exit 2
fi
if [[ ! -x "$AQLI_BIN" ]]; then
  echo "aqli executable not found or not executable: $AQLI_BIN" >&2
  exit 2
fi
for cmd in cp date mkdir tee tr; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

slugify() {
  local value="$1"
  local slug
  slug="$(printf '%s' "$value" | tr '/:[:space:]' '___' | tr -cd 'A-Za-z0-9._-')"
  if [[ -z "$slug" ]]; then
    slug="model"
  fi
  printf '%s' "$slug"
}

safe_tsv_field() {
  local value="${1:-}"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

write_kv_row() {
  printf '%s\t%s\n' "$(safe_tsv_field "$1")" "$(safe_tsv_field "${2:-}")"
}

record_result() {
  local stage="$1"
  local status="$2"
  local exit_code="$3"
  local artifact="$4"
  local stdout_log="$5"
  local stderr_log="$6"
  local reason="${7:-}"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$stage" "$status" "$exit_code" "$artifact" "$stdout_log" "$stderr_log" "$(safe_tsv_field "$reason")" >> "$RESULTS"
}

if [[ -z "$BASELINE_NAME" ]]; then
  BASELINE_NAME="$(slugify "$MODEL_ID")"
fi
if [[ -z "$RUN_LABEL" ]]; then
  RUN_LABEL="$(date -u +"%Y%m%d-%H%M%S")"
fi

BASELINE_PARENT="${BASELINE_ROOT%/}/${BASELINE_NAME}"
BASELINE_DIR="${BASELINE_PARENT}/${RUN_LABEL}"
BENCH_OUT_DIR="${BASELINE_DIR}/bench"
BENCH_LABEL="profile"
BENCH_RUN_DIR="${BENCH_OUT_DIR}/${BENCH_LABEL}"
GATE_TSV="${BASELINE_DIR}/gate.tsv"
PROFILE_TSV="${BASELINE_DIR}/profile.tsv"
SUMMARY_JSON="${BASELINE_DIR}/summary.json"
SUMMARY_CSV="${BASELINE_DIR}/summary.csv"
BENCH_REPORT="${BASELINE_DIR}/bench-report.txt"
DIAGNOSIS="${BASELINE_DIR}/diagnosis.tsv"
STAGES="${BASELINE_DIR}/stages.tsv"
DIAGNOSIS_REPORT="${BASELINE_DIR}/diagnosis-report.txt"
CONFIG="${BASELINE_DIR}/config.tsv"
MANIFEST="${BASELINE_DIR}/manifest.tsv"
RESULTS="${BASELINE_DIR}/results.tsv"
REPORT="${BASELINE_DIR}/report.txt"
BENCH_STDOUT="${BASELINE_DIR}/bench.stdout.log"
BENCH_STDERR="${BASELINE_DIR}/bench.stderr.log"
LATEST_GATE="${BASELINE_PARENT}/latest-gate.tsv"
LATEST_PROFILE="${BASELINE_PARENT}/latest-profile.tsv"
LATEST_DIAGNOSIS="${BASELINE_PARENT}/latest-diagnosis.tsv"
LATEST_STAGES="${BASELINE_PARENT}/latest-stages.tsv"
LATEST_MANIFEST="${BASELINE_PARENT}/latest-manifest.tsv"

mkdir -p "$BASELINE_DIR"
printf 'stage\tstatus\texitCode\tartifact\tstdout\tstderr\treason\n' > "$RESULTS"
{
  printf 'key\tvalue\n'
  write_kv_row generatedAt "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  write_kv_row rootDir "$ROOT_DIR"
  write_kv_row model "$MODEL_ID"
  write_kv_row baselineRoot "$BASELINE_ROOT"
  write_kv_row baselineName "$BASELINE_NAME"
  write_kv_row runLabel "$RUN_LABEL"
  write_kv_row baselineDir "$BASELINE_DIR"
  write_kv_row prompt "$PROMPT"
  write_kv_row maxTokens "$MAX_TOKENS"
  write_kv_row quickMode "$QUICK_MODE"
  write_kv_row withCpu "$WITH_CPU"
  write_kv_row withQuantizeTurbo "$WITH_QUANT_TURBO"
  write_kv_row cases "$CASES_FILTER"
  write_kv_row requireMetal "$REQUIRE_METAL"
  write_kv_row requireProfile "$REQUIRE_PROFILE"
  write_kv_row updateLatest "$UPDATE_LATEST"
  write_kv_row benchScript "$BENCH_SCRIPT"
  write_kv_row aqliBin "$AQLI_BIN"
  write_kv_row stages "$STAGES"
  write_kv_row latestStages "$LATEST_STAGES"
} > "$CONFIG"

cmd=(
  bash "$BENCH_SCRIPT"
  --aqli-bin "$AQLI_BIN"
  --model "$MODEL_ID"
  --out-dir "$BENCH_OUT_DIR"
  --label "$BENCH_LABEL"
  --det-prompt "$PROMPT"
  --max-tokens "$MAX_TOKENS"
  --profile
  --summary-file "$GATE_TSV"
)
if [[ "$QUICK_MODE" -eq 1 ]]; then
  cmd+=(--quick)
fi
if [[ "$WITH_CPU" -eq 1 ]]; then
  cmd+=(--with-cpu)
fi
if [[ "$WITH_QUANT_TURBO" -eq 1 ]]; then
  cmd+=(--with-quantize-turbo)
fi
if [[ -n "$CASES_FILTER" ]]; then
  cmd+=(--cases "$CASES_FILTER")
fi
if [[ "$REQUIRE_PROFILE" -eq 1 ]]; then
  cmd+=(--require-profile)
fi
if [[ "$REQUIRE_METAL" -eq 1 ]]; then
  cmd+=(--require-metal)
fi
if [[ -n "$MAX_TOP_STAGE_MS" ]]; then cmd+=(--max-top-stage-ms "$MAX_TOP_STAGE_MS"); fi
if [[ -n "$MAX_PREFILL_MS" ]]; then cmd+=(--max-prefill-ms "$MAX_PREFILL_MS"); fi
if [[ -n "$MAX_DECODE_MS" ]]; then cmd+=(--max-decode-ms "$MAX_DECODE_MS"); fi
if [[ -n "$MAX_TPOT_MS" ]]; then cmd+=(--max-tpot-ms "$MAX_TPOT_MS"); fi
if [[ -n "$MAX_ATTENTION_MS" ]]; then cmd+=(--max-attention-ms "$MAX_ATTENTION_MS"); fi
if [[ -n "$MAX_FFN_MS" ]]; then cmd+=(--max-ffn-ms "$MAX_FFN_MS"); fi
if [[ -n "$MAX_LOGITS_MS" ]]; then cmd+=(--max-logits-ms "$MAX_LOGITS_MS"); fi

set +e
"${cmd[@]}" >"$BENCH_STDOUT" 2>"$BENCH_STDERR"
bench_exit=$?
set -e
if [[ "$bench_exit" -ne 0 ]]; then
  record_result benchmark fail "$bench_exit" "$GATE_TSV" "$BENCH_STDOUT" "$BENCH_STDERR" "benchmark-failed"
  echo "Safetensor baseline capture failed; stderr=$BENCH_STDERR" >&2
  exit "$bench_exit"
fi
record_result benchmark pass 0 "$GATE_TSV" "$BENCH_STDOUT" "$BENCH_STDERR"

required_stages="${BENCH_RUN_DIR}/diagnosis/stages.tsv"
required_diagnosis="${BENCH_RUN_DIR}/diagnosis/diagnosis.tsv"
if [[ ! -f "$required_stages" || ! -f "$required_diagnosis" ]]; then
  record_result diagnosis fail 3 "$required_stages" "$BENCH_STDOUT" "$BENCH_STDERR" "missing-diagnosis"
  echo "Safetensor diagnosis artifacts were not generated: $required_stages" >&2
  exit 3
fi

cp "$required_stages" "$STAGES"
cp "$required_diagnosis" "$DIAGNOSIS"
if [[ -f "${BENCH_RUN_DIR}/diagnosis/report.txt" ]]; then cp "${BENCH_RUN_DIR}/diagnosis/report.txt" "$DIAGNOSIS_REPORT"; fi
if [[ -f "${BENCH_RUN_DIR}/profile.tsv" ]]; then cp "${BENCH_RUN_DIR}/profile.tsv" "$PROFILE_TSV"; fi
if [[ -f "${BENCH_RUN_DIR}/summary.json" ]]; then cp "${BENCH_RUN_DIR}/summary.json" "$SUMMARY_JSON"; fi
if [[ -f "${BENCH_RUN_DIR}/summary.csv" ]]; then cp "${BENCH_RUN_DIR}/summary.csv" "$SUMMARY_CSV"; fi
if [[ -f "${BENCH_RUN_DIR}/report.txt" ]]; then cp "${BENCH_RUN_DIR}/report.txt" "$BENCH_REPORT"; fi
record_result diagnosis pass 0 "$STAGES" "$BENCH_STDOUT" "$BENCH_STDERR"

{
  printf 'key\tvalue\n'
  write_kv_row model "$MODEL_ID"
  write_kv_row baselineName "$BASELINE_NAME"
  write_kv_row runLabel "$RUN_LABEL"
  write_kv_row prompt "$PROMPT"
  write_kv_row maxTokens "$MAX_TOKENS"
  write_kv_row quickMode "$QUICK_MODE"
  write_kv_row requireMetal "$REQUIRE_METAL"
  write_kv_row requireProfile "$REQUIRE_PROFILE"
  write_kv_row gate "$GATE_TSV"
  write_kv_row profile "$PROFILE_TSV"
  write_kv_row diagnosis "$DIAGNOSIS"
  write_kv_row stages "$STAGES"
  write_kv_row latestStages "$LATEST_STAGES"
} > "$MANIFEST"

if [[ "$UPDATE_LATEST" -eq 1 ]]; then
  cp "$GATE_TSV" "$LATEST_GATE"
  cp "$STAGES" "$LATEST_STAGES"
  cp "$DIAGNOSIS" "$LATEST_DIAGNOSIS"
  cp "$MANIFEST" "$LATEST_MANIFEST"
  if [[ -f "$PROFILE_TSV" ]]; then cp "$PROFILE_TSV" "$LATEST_PROFILE"; fi
  record_result latest pass 0 "$LATEST_STAGES" "" ""
  VERIFY_STAGES="$LATEST_STAGES"
else
  record_result latest skip 0 "$LATEST_STAGES" "" "" "disabled"
  VERIFY_STAGES="$STAGES"
fi

{
  echo "Aqli safetensor profile baseline captured"
  echo "baselineDir=$BASELINE_DIR"
  echo "artifacts.config=$CONFIG"
  echo "artifacts.results=$RESULTS"
  echo "artifacts.manifest=$MANIFEST"
  echo "artifacts.gate=$GATE_TSV"
  echo "artifacts.profile=$PROFILE_TSV"
  echo "artifacts.diagnosis=$DIAGNOSIS"
  echo "artifacts.stages=$STAGES"
  if [[ "$UPDATE_LATEST" -eq 1 ]]; then
    echo "artifacts.latestStages=$LATEST_STAGES"
  fi
  echo "next.verify=./scripts/verify-fast-speed-gates.sh --only safetensor --safetensor-model $MODEL_ID --safetensor-baseline-stages ${VERIFY_STAGES}"
} | tee "$REPORT"
