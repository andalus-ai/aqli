#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<'USAGE'
Usage: bench-safetensor-inference.sh --model MODEL [options]

Run a repeatable local benchmark for safetensor text inference using the
installed `aqli` CLI and emit raw logs plus a compact JSON/CSV summary.
When profile rows are available, also emits diagnosis/diagnosis.tsv,
diagnosis/stages.tsv, and diagnosis/report.txt to identify the slowest stage.

Options:
  --model ID              Model id or local id alias (required)
  --aqli-bin PATH       Aqli executable (default: ~/.local/bin/aqli)
  --out-dir PATH          Output root (default: aqli/ops/benchmarks/safetensor)
  --label NAME            Optional run label
  --summary-file PATH     Optional gate-friendly TSV summary path
  --det-prompt TEXT       Deterministic prompt
                          (default: "where is jakarta")
  --normal-prompt TEXT    Natural prompt
                          (default: "Tell me briefly where Jakarta is located.")
  --max-tokens N          Max tokens per run (default: 3)
  --preset NAME           Apply reusable benchmark defaults
                          (examples: m4-smoke, m4-greedy-10, m4-gemma4-12b-smoke,
                          m4-gemma4-12b-row-prefill-ab)
  --list-presets          List safetensor benchmark presets and exit
  --cases LIST            Comma-separated case ids to run
                          (example: metal-deterministic,cpu-deterministic)
  --quick                 Deterministic-only benchmark plan
                          (metal first, plus CPU/turbo variants if requested)
  --java-opt OPT          Append a JVM/system property to AQLI_JAVA_OPTS for
                          each run; repeatable for A/B flags
  --profile               Enable -Daqli.profile=true (default: off)
                          Also emits profile.tsv with normalized slowest-stage
                          and backend-path evidence when profile output exists.
  --no-profile            Disable profiling even if a preset enables it
  --require-profile       Fail a case if no [PROFILE] line is emitted
  --no-require-profile    Do not fail when profile output is missing
  --require-ffn-strategy STRATEGY[,STRATEGY...]
                          Fail a case unless profile ffn_strategy matches one
                          of the expected strategies
  --no-require-ffn-strategy
                          Do not require a specific FFN strategy
  --require-metal         Fail non-CPU cases unless profile backend proves Metal
                          (falls back to logs only when profile is unavailable)
  --no-require-metal      Do not require Metal backend proof
  --require-metal-paths   Fail non-CPU cases unless profile path evidence proves
                          Metal/GPU kernels for linear, logits, FFN, and attention work
  --no-require-metal-paths
                          Do not require Metal/GPU path evidence
  --reject-fallback-paths Fail non-CPU cases when profile path evidence contains
                          CPU/Java/Accelerate/fallback/skip/reject markers
  --allow-fallback-paths  Allow fallback path markers for diagnostics
  --require-answer-regex REGEX
                          Fail a case when extracted answer does not match REGEX
  --max-repeated-token-run N
                          Fail when the same answer token repeats more than N times
  --min-speed-tps N       Fail a case when measured CLI speed is below N tokens/sec
  --min-chunks N          Fail a case when stream/chunk count is below N
  --max-top-stage-ms N    Fail a case when the slowest profiled stage exceeds N
  --max-prefill-ms N      Fail a case when profiled prefill time exceeds N
  --max-decode-ms N       Fail a case when profiled decode time exceeds N
  --max-tpot-ms N         Fail a case when profiled token latency exceeds N
  --max-sampling-ms N     Fail a case when profiled sampling time exceeds N
  --max-argmax-ms N       Fail a case when profiled greedy argmax time exceeds N
  --max-attention-ms N    Fail a case when profiled attention time exceeds N
  --max-ffn-ms N          Fail a case when profiled FFN time exceeds N
  --max-logits-ms N       Fail a case when profiled logits time exceeds N
  --min-core-metal-coverage N
                          Fail a non-CPU case unless at least N of core path
                          events use Metal, where N is a ratio from 0 to 1
  --min-linear-metal-coverage N
                          Fail a non-CPU case unless linear path coverage is N
  --min-logits-metal-coverage N
                          Fail a non-CPU case unless logits path coverage is N
  --min-ffn-metal-coverage N
                          Fail a non-CPU case unless FFN path coverage is N
  --min-attention-metal-coverage N
                          Fail a non-CPU case unless attention path coverage is N
  --min-argmax-metal-coverage N
                          Fail a non-CPU case unless greedy argmax path coverage is N
  --with-cpu              Include CPU reference run
  --with-quantize-turbo   Include Metal and CPU turbo-quantized runs
  --help                  Show this help

Environment:
  AQLI_BENCH_SAFETENSOR_PRESET         Default preset name
  AQLI_BENCH_SAFETENSOR_PRESET_SCRIPT  Preset registry script
  AQLI_BENCH_SAFETENSOR_JAVA_OPTS      Extra JVM/system properties for runs
  AQLI_JAVA_OPTS                       Preserved and prepended to run opts

Examples:
  ./aqli/scripts/bench-safetensor-inference.sh --model 6f469a --with-cpu
  ./aqli/scripts/bench-safetensor-inference.sh --model 1a008d --with-cpu --with-quantize-turbo
  ./aqli/scripts/bench-safetensor-inference.sh --model 7c51c9 --quick --preset m4-greedy-10
  ./aqli/scripts/bench-safetensor-inference.sh --model 53f473 --quick --preset m4-gemma4-12b-smoke
  ./aqli/scripts/bench-safetensor-inference.sh --model 53f473 --quick --preset m4-gemma4-12b-row-prefill-ab
USAGE
}

MODEL_ID=""
AQLI_BIN="${HOME}/.local/bin/aqli"
OUT_DIR="aqli/ops/benchmarks/safetensor"
LABEL=""
SUMMARY_FILE=""
DET_PROMPT="where is jakarta"
NORMAL_PROMPT="Tell me briefly where Jakarta is located."
MAX_TOKENS=3
SAFETENSOR_PRESET="${AQLI_BENCH_SAFETENSOR_PRESET:-}"
SAFETENSOR_PRESET_SCRIPT="${AQLI_BENCH_SAFETENSOR_PRESET_SCRIPT:-${SCRIPT_DIR}/safetensor-performance-presets.sh}"
BASE_AQLI_JAVA_OPTS="${AQLI_JAVA_OPTS:-}"
BENCH_JAVA_OPTS_ENV="${AQLI_BENCH_SAFETENSOR_JAVA_OPTS:-}"
PROFILE_ENABLED=0
REQUIRE_PROFILE=0
REQUIRE_FFN_STRATEGY=""
REQUIRE_METAL=0
REQUIRE_METAL_PATHS=0
REJECT_FALLBACK_PATHS=0
REQUIRE_ANSWER_REGEX=""
MAX_REPEATED_TOKEN_RUN=""
MIN_SPEED_TPS=""
MIN_CHUNKS=""
MAX_TOP_STAGE_MS=""
MAX_PREFILL_MS=""
MAX_DECODE_MS=""
MAX_TPOT_MS=""
MAX_SAMPLING_MS=""
MAX_ARGMAX_MS=""
MAX_ATTENTION_MS=""
MAX_FFN_MS=""
MAX_LOGITS_MS=""
MIN_CORE_METAL_COVERAGE=""
MIN_LINEAR_METAL_COVERAGE=""
MIN_LOGITS_METAL_COVERAGE=""
MIN_FFN_METAL_COVERAGE=""
MIN_ATTENTION_METAL_COVERAGE=""
MIN_ARGMAX_METAL_COVERAGE=""
WITH_CPU=0
WITH_QUANT_TURBO=0
HEARTBEAT_SECONDS=10
CASES_FILTER=""
QUICK_MODE=0
LIST_PRESETS=0
FINAL_EXIT_CODE=0
BENCH_JAVA_OPTS=()
BENCH_JAVA_OPTS_SET=0
PROFILE_EXPLICIT=0
REQUIRE_PROFILE_EXPLICIT=0
REQUIRE_FFN_STRATEGY_EXPLICIT=0
MAX_TOKENS_EXPLICIT=0
REQUIRE_METAL_EXPLICIT=0
REQUIRE_METAL_PATHS_EXPLICIT=0
REJECT_FALLBACK_PATHS_EXPLICIT=0
REQUIRE_ANSWER_REGEX_EXPLICIT=0
MAX_REPEATED_TOKEN_RUN_EXPLICIT=0
MIN_SPEED_TPS_EXPLICIT=0
MIN_CHUNKS_EXPLICIT=0
MAX_TOP_STAGE_MS_EXPLICIT=0
MAX_PREFILL_MS_EXPLICIT=0
MAX_DECODE_MS_EXPLICIT=0
MAX_TPOT_MS_EXPLICIT=0
MAX_SAMPLING_MS_EXPLICIT=0
MAX_ARGMAX_MS_EXPLICIT=0
MAX_ATTENTION_MS_EXPLICIT=0
MAX_FFN_MS_EXPLICIT=0
MAX_LOGITS_MS_EXPLICIT=0
MIN_CORE_METAL_COVERAGE_EXPLICIT=0
MIN_LINEAR_METAL_COVERAGE_EXPLICIT=0
MIN_LOGITS_METAL_COVERAGE_EXPLICIT=0
MIN_FFN_METAL_COVERAGE_EXPLICIT=0
MIN_ATTENTION_METAL_COVERAGE_EXPLICIT=0
MIN_ARGMAX_METAL_COVERAGE_EXPLICIT=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model) MODEL_ID="$2"; shift 2 ;;
    --aqli-bin) AQLI_BIN="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --label) LABEL="$2"; shift 2 ;;
    --summary-file) SUMMARY_FILE="$2"; shift 2 ;;
    --det-prompt) DET_PROMPT="$2"; shift 2 ;;
    --normal-prompt) NORMAL_PROMPT="$2"; shift 2 ;;
    --max-tokens) MAX_TOKENS="$2"; MAX_TOKENS_EXPLICIT=1; shift 2 ;;
    --preset) SAFETENSOR_PRESET="$2"; shift 2 ;;
    --preset=*) SAFETENSOR_PRESET="${1#*=}"; shift ;;
    --list-presets) LIST_PRESETS=1; shift ;;
    --cases) CASES_FILTER="$2"; shift 2 ;;
    --quick) QUICK_MODE=1; shift ;;
    --java-opt) BENCH_JAVA_OPTS+=( "$2" ); BENCH_JAVA_OPTS_SET=1; shift 2 ;;
    --java-opt=*) BENCH_JAVA_OPTS+=( "${1#*=}" ); BENCH_JAVA_OPTS_SET=1; shift ;;
    --profile) PROFILE_ENABLED=1; PROFILE_EXPLICIT=1; shift ;;
    --no-profile) PROFILE_ENABLED=0; PROFILE_EXPLICIT=1; shift ;;
    --require-profile) REQUIRE_PROFILE=1; REQUIRE_PROFILE_EXPLICIT=1; shift ;;
    --no-require-profile) REQUIRE_PROFILE=0; REQUIRE_PROFILE_EXPLICIT=1; shift ;;
    --require-ffn-strategy) REQUIRE_FFN_STRATEGY="$2"; REQUIRE_FFN_STRATEGY_EXPLICIT=1; shift 2 ;;
    --require-ffn-strategy=*) REQUIRE_FFN_STRATEGY="${1#*=}"; REQUIRE_FFN_STRATEGY_EXPLICIT=1; shift ;;
    --no-require-ffn-strategy) REQUIRE_FFN_STRATEGY=""; REQUIRE_FFN_STRATEGY_EXPLICIT=1; shift ;;
    --require-metal) REQUIRE_METAL=1; REQUIRE_METAL_EXPLICIT=1; shift ;;
    --no-require-metal) REQUIRE_METAL=0; REQUIRE_METAL_EXPLICIT=1; shift ;;
    --require-metal-paths) REQUIRE_METAL_PATHS=1; REQUIRE_METAL_PATHS_EXPLICIT=1; shift ;;
    --no-require-metal-paths) REQUIRE_METAL_PATHS=0; REQUIRE_METAL_PATHS_EXPLICIT=1; shift ;;
    --reject-fallback-paths) REJECT_FALLBACK_PATHS=1; REJECT_FALLBACK_PATHS_EXPLICIT=1; shift ;;
    --allow-fallback-paths) REJECT_FALLBACK_PATHS=0; REJECT_FALLBACK_PATHS_EXPLICIT=1; shift ;;
    --require-answer-regex) REQUIRE_ANSWER_REGEX="$2"; REQUIRE_ANSWER_REGEX_EXPLICIT=1; shift 2 ;;
    --max-repeated-token-run) MAX_REPEATED_TOKEN_RUN="$2"; MAX_REPEATED_TOKEN_RUN_EXPLICIT=1; shift 2 ;;
    --min-speed-tps) MIN_SPEED_TPS="$2"; MIN_SPEED_TPS_EXPLICIT=1; shift 2 ;;
    --min-chunks) MIN_CHUNKS="$2"; MIN_CHUNKS_EXPLICIT=1; shift 2 ;;
    --max-top-stage-ms) MAX_TOP_STAGE_MS="$2"; MAX_TOP_STAGE_MS_EXPLICIT=1; shift 2 ;;
    --max-prefill-ms) MAX_PREFILL_MS="$2"; MAX_PREFILL_MS_EXPLICIT=1; shift 2 ;;
    --max-decode-ms) MAX_DECODE_MS="$2"; MAX_DECODE_MS_EXPLICIT=1; shift 2 ;;
    --max-tpot-ms) MAX_TPOT_MS="$2"; MAX_TPOT_MS_EXPLICIT=1; shift 2 ;;
    --max-sampling-ms) MAX_SAMPLING_MS="$2"; MAX_SAMPLING_MS_EXPLICIT=1; shift 2 ;;
    --max-argmax-ms) MAX_ARGMAX_MS="$2"; MAX_ARGMAX_MS_EXPLICIT=1; shift 2 ;;
    --max-attention-ms) MAX_ATTENTION_MS="$2"; MAX_ATTENTION_MS_EXPLICIT=1; shift 2 ;;
    --max-ffn-ms) MAX_FFN_MS="$2"; MAX_FFN_MS_EXPLICIT=1; shift 2 ;;
    --max-logits-ms) MAX_LOGITS_MS="$2"; MAX_LOGITS_MS_EXPLICIT=1; shift 2 ;;
    --min-core-metal-coverage) MIN_CORE_METAL_COVERAGE="$2"; MIN_CORE_METAL_COVERAGE_EXPLICIT=1; shift 2 ;;
    --min-linear-metal-coverage) MIN_LINEAR_METAL_COVERAGE="$2"; MIN_LINEAR_METAL_COVERAGE_EXPLICIT=1; shift 2 ;;
    --min-logits-metal-coverage) MIN_LOGITS_METAL_COVERAGE="$2"; MIN_LOGITS_METAL_COVERAGE_EXPLICIT=1; shift 2 ;;
    --min-ffn-metal-coverage) MIN_FFN_METAL_COVERAGE="$2"; MIN_FFN_METAL_COVERAGE_EXPLICIT=1; shift 2 ;;
    --min-attention-metal-coverage) MIN_ATTENTION_METAL_COVERAGE="$2"; MIN_ATTENTION_METAL_COVERAGE_EXPLICIT=1; shift 2 ;;
    --min-argmax-metal-coverage) MIN_ARGMAX_METAL_COVERAGE="$2"; MIN_ARGMAX_METAL_COVERAGE_EXPLICIT=1; shift 2 ;;
    --with-cpu) WITH_CPU=1; shift ;;
    --with-quantize-turbo) WITH_QUANT_TURBO=1; shift ;;
    --help) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if (( LIST_PRESETS == 1 )); then
  if [[ ! -f "${SAFETENSOR_PRESET_SCRIPT}" ]]; then
    echo "Safetensor preset script not found: ${SAFETENSOR_PRESET_SCRIPT}" >&2
    exit 2
  fi
  bash "${SAFETENSOR_PRESET_SCRIPT}" list
  exit 0
fi

if [[ -z "${MODEL_ID}" ]]; then
  echo "--model is required" >&2
  exit 2
fi

preset_truthy() {
  case "$1" in
    1|true|TRUE|yes|YES|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}

valid_coverage_threshold() {
  [[ "$1" =~ ^(0([.][0-9]+)?|1([.]0+)?)$ ]]
}

trim_value() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "${value}"
}

merged_java_opts() {
  local -a opts=()
  if [[ -n "${BASE_AQLI_JAVA_OPTS}" ]]; then
    opts+=( "${BASE_AQLI_JAVA_OPTS}" )
  fi
  if [[ -n "${BENCH_JAVA_OPTS_ENV}" ]]; then
    opts+=( "${BENCH_JAVA_OPTS_ENV}" )
  fi
  if (( BENCH_JAVA_OPTS_SET == 1 )); then
    opts+=( "${BENCH_JAVA_OPTS[@]}" )
  fi
  if (( $# > 0 )); then
    opts+=( "$@" )
  fi
  if (( ${#opts[@]} == 0 )); then
    return 0
  fi
  local IFS=' '
  printf '%s' "${opts[*]}"
}

apply_safetensor_preset() {
  if [[ -z "${SAFETENSOR_PRESET}" ]]; then
    return 0
  fi
  if [[ ! -f "${SAFETENSOR_PRESET_SCRIPT}" ]]; then
    echo "Safetensor preset script not found: ${SAFETENSOR_PRESET_SCRIPT}" >&2
    exit 2
  fi

  # shellcheck source=/dev/null
  source "${SAFETENSOR_PRESET_SCRIPT}"
  if ! aqli_safetensor_performance_preset_validate "${SAFETENSOR_PRESET}"; then
    local available
    available="$(aqli_safetensor_performance_preset_names | awk 'BEGIN { sep="" } { printf "%s%s", sep, $0; sep=", " }')"
    echo "Unknown safetensor preset: ${SAFETENSOR_PRESET}. Available: ${available}" >&2
    exit 2
  fi

  local key value
  while IFS=$'\t' read -r key value; do
    case "${key}" in
      profile)
        if (( PROFILE_EXPLICIT == 0 )); then
          if preset_truthy "${value}"; then PROFILE_ENABLED=1; else PROFILE_ENABLED=0; fi
        fi
        ;;
      requireProfile)
        if (( REQUIRE_PROFILE_EXPLICIT == 0 )); then
          if preset_truthy "${value}"; then REQUIRE_PROFILE=1; else REQUIRE_PROFILE=0; fi
        fi
        ;;
      requireFfnStrategy)
        if (( REQUIRE_FFN_STRATEGY_EXPLICIT == 0 )); then REQUIRE_FFN_STRATEGY="${value}"; fi
        ;;
      javaOpt)
        BENCH_JAVA_OPTS+=( "${value}" )
        BENCH_JAVA_OPTS_SET=1
        ;;
      requireMetal)
        if (( REQUIRE_METAL_EXPLICIT == 0 )); then
          if preset_truthy "${value}"; then REQUIRE_METAL=1; else REQUIRE_METAL=0; fi
        fi
        ;;
      requireMetalPaths)
        if (( REQUIRE_METAL_PATHS_EXPLICIT == 0 )); then
          if preset_truthy "${value}"; then REQUIRE_METAL_PATHS=1; else REQUIRE_METAL_PATHS=0; fi
        fi
        ;;
      rejectFallbackPaths)
        if (( REJECT_FALLBACK_PATHS_EXPLICIT == 0 )); then
          if preset_truthy "${value}"; then REJECT_FALLBACK_PATHS=1; else REJECT_FALLBACK_PATHS=0; fi
        fi
        ;;
      requireAnswerRegex)
        if (( REQUIRE_ANSWER_REGEX_EXPLICIT == 0 )); then REQUIRE_ANSWER_REGEX="${value}"; fi
        ;;
      maxRepeatedTokenRun)
        if (( MAX_REPEATED_TOKEN_RUN_EXPLICIT == 0 )); then MAX_REPEATED_TOKEN_RUN="${value}"; fi
        ;;
      minSpeedTps)
        if (( MIN_SPEED_TPS_EXPLICIT == 0 )); then MIN_SPEED_TPS="${value}"; fi
        ;;
      minChunks)
        if (( MIN_CHUNKS_EXPLICIT == 0 )); then MIN_CHUNKS="${value}"; fi
        ;;
      maxTokens)
        if (( MAX_TOKENS_EXPLICIT == 0 )); then MAX_TOKENS="${value}"; fi
        ;;
      topStageThresholdMs)
        if (( MAX_TOP_STAGE_MS_EXPLICIT == 0 )); then MAX_TOP_STAGE_MS="${value}"; fi
        ;;
      prefillThresholdMs)
        if (( MAX_PREFILL_MS_EXPLICIT == 0 )); then MAX_PREFILL_MS="${value}"; fi
        ;;
      decodeThresholdMs)
        if (( MAX_DECODE_MS_EXPLICIT == 0 )); then MAX_DECODE_MS="${value}"; fi
        ;;
      tpotThresholdMs)
        if (( MAX_TPOT_MS_EXPLICIT == 0 )); then MAX_TPOT_MS="${value}"; fi
        ;;
      samplingThresholdMs)
        if (( MAX_SAMPLING_MS_EXPLICIT == 0 )); then MAX_SAMPLING_MS="${value}"; fi
        ;;
      argmaxThresholdMs)
        if (( MAX_ARGMAX_MS_EXPLICIT == 0 )); then MAX_ARGMAX_MS="${value}"; fi
        ;;
      attentionThresholdMs)
        if (( MAX_ATTENTION_MS_EXPLICIT == 0 )); then MAX_ATTENTION_MS="${value}"; fi
        ;;
      ffnThresholdMs)
        if (( MAX_FFN_MS_EXPLICIT == 0 )); then MAX_FFN_MS="${value}"; fi
        ;;
      logitsThresholdMs)
        if (( MAX_LOGITS_MS_EXPLICIT == 0 )); then MAX_LOGITS_MS="${value}"; fi
        ;;
      minCoreMetalCoverage)
        if (( MIN_CORE_METAL_COVERAGE_EXPLICIT == 0 )); then MIN_CORE_METAL_COVERAGE="${value}"; fi
        ;;
      minLinearMetalCoverage)
        if (( MIN_LINEAR_METAL_COVERAGE_EXPLICIT == 0 )); then MIN_LINEAR_METAL_COVERAGE="${value}"; fi
        ;;
      minLogitsMetalCoverage)
        if (( MIN_LOGITS_METAL_COVERAGE_EXPLICIT == 0 )); then MIN_LOGITS_METAL_COVERAGE="${value}"; fi
        ;;
      minFfnMetalCoverage)
        if (( MIN_FFN_METAL_COVERAGE_EXPLICIT == 0 )); then MIN_FFN_METAL_COVERAGE="${value}"; fi
        ;;
      minAttentionMetalCoverage)
        if (( MIN_ATTENTION_METAL_COVERAGE_EXPLICIT == 0 )); then MIN_ATTENTION_METAL_COVERAGE="${value}"; fi
        ;;
      minArgmaxMetalCoverage)
        if (( MIN_ARGMAX_METAL_COVERAGE_EXPLICIT == 0 )); then MIN_ARGMAX_METAL_COVERAGE="${value}"; fi
        ;;
    esac
  done < <(aqli_safetensor_performance_preset_defaults "${SAFETENSOR_PRESET}")
}

apply_safetensor_preset

for cmd in date mkdir mktemp awk sed grep jq tr; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "Missing required command: ${cmd}" >&2
    exit 127
  fi
done

for threshold in "${MIN_SPEED_TPS}" "${MAX_TOP_STAGE_MS}" "${MAX_PREFILL_MS}" "${MAX_DECODE_MS}" "${MAX_TPOT_MS}" "${MAX_SAMPLING_MS}" "${MAX_ARGMAX_MS}" "${MAX_ATTENTION_MS}" "${MAX_FFN_MS}" "${MAX_LOGITS_MS}"; do
  if [[ -n "${threshold}" && ! "${threshold}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "Invalid threshold value: ${threshold}" >&2
    exit 2
  fi
done

if [[ -n "${REQUIRE_ANSWER_REGEX}" ]]; then
  set +e
  printf '\n' | grep -Eiq -- "${REQUIRE_ANSWER_REGEX}" >/dev/null 2>&1
  answer_regex_status=$?
  set -e
  if (( answer_regex_status == 2 )); then
    echo "Invalid required answer regex: ${REQUIRE_ANSWER_REGEX}" >&2
    exit 2
  fi
fi

if [[ -n "${MAX_REPEATED_TOKEN_RUN}" && ! "${MAX_REPEATED_TOKEN_RUN}" =~ ^[1-9][0-9]*$ ]]; then
  echo "Invalid max repeated token run value: ${MAX_REPEATED_TOKEN_RUN}" >&2
  exit 2
fi
if [[ -n "${REQUIRE_FFN_STRATEGY}" && -z "$(trim_value "${REQUIRE_FFN_STRATEGY//,/}")" ]]; then
  echo "Invalid required FFN strategy value: ${REQUIRE_FFN_STRATEGY}" >&2
  exit 2
fi
if (( BENCH_JAVA_OPTS_SET == 1 )); then
  for java_opt in "${BENCH_JAVA_OPTS[@]}"; do
    if [[ -z "$(trim_value "${java_opt}")" ]]; then
      echo "Invalid empty java opt" >&2
      exit 2
    fi
  done
fi
if [[ -n "${MIN_CHUNKS}" && ! "${MIN_CHUNKS}" =~ ^[1-9][0-9]*$ ]]; then
  echo "Invalid min chunks value: ${MIN_CHUNKS}" >&2
  exit 2
fi

for threshold in "${MIN_CORE_METAL_COVERAGE}" "${MIN_LINEAR_METAL_COVERAGE}" "${MIN_LOGITS_METAL_COVERAGE}" "${MIN_FFN_METAL_COVERAGE}" "${MIN_ATTENTION_METAL_COVERAGE}" "${MIN_ARGMAX_METAL_COVERAGE}"; do
  if [[ -n "${threshold}" ]] && ! valid_coverage_threshold "${threshold}"; then
    echo "Invalid Metal coverage threshold value: ${threshold}" >&2
    exit 2
  fi
done

if [[ ! -x "${AQLI_BIN}" ]]; then
  echo "aqli executable not found or not executable: ${AQLI_BIN}" >&2
  exit 2
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
if [[ -z "${LABEL}" ]]; then
  LABEL="safetensor-${MODEL_ID//\//_}-${timestamp}"
fi
RUN_DIR="${OUT_DIR%/}/${LABEL}"
mkdir -p "${RUN_DIR}/logs"

jsonl_file="$(mktemp)"
touch "${jsonl_file}"
SUMMARY_JSON="${RUN_DIR}/summary.json"
SUMMARY_CSV="${RUN_DIR}/summary.csv"
PROFILE_TSV="${RUN_DIR}/profile.tsv"
GATE_TSV="${SUMMARY_FILE:-${RUN_DIR}/gate.tsv}"
REPORT_TXT="${RUN_DIR}/report.txt"
DIAGNOSIS_DIR="${RUN_DIR}/diagnosis"
DIAGNOSIS_TSV="${DIAGNOSIS_DIR}/diagnosis.tsv"
DIAGNOSIS_STAGES_TSV="${DIAGNOSIS_DIR}/stages.tsv"
DIAGNOSIS_PATHS_TSV="${DIAGNOSIS_DIR}/paths.tsv"
DIAGNOSIS_REPORT="${DIAGNOSIS_DIR}/report.txt"
GATE_TSV_DIR="${GATE_TSV%/*}"
if [[ "${GATE_TSV_DIR}" != "${GATE_TSV}" ]]; then
  mkdir -p "${GATE_TSV_DIR}"
fi

jsonl_objects_only() {
  local file="$1"
  grep -E '^[[:space:]]*\{' "${file}" || true
}

generate_outputs() {
  local objects_file
  objects_file="$(mktemp)"
  jsonl_objects_only "${jsonl_file}" > "${objects_file}"

  jq -s \
    --arg generated_at "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
    --arg model "${MODEL_ID}" \
    --arg label "${LABEL}" \
    '{
      generated_at_utc: $generated_at,
      label: $label,
      model: $model,
      runs: .
    }' "${objects_file}" > "${SUMMARY_JSON}"

  {
    echo "case_id,mode,prompt_kind,quantize,status,exit_code,duration_s,speed_tps,chunks,answer,answer_repeat_run,profile_backend,profile_metal,profile_core_path_status,profile_linear_path_status,profile_logits_path_status,profile_ffn_path_status,profile_attention_path_status,profile_argmax_path_status,profile_core_path_coverage,profile_linear_path_coverage,profile_logits_path_coverage,profile_ffn_path_coverage,profile_attention_path_coverage,profile_argmax_path_coverage,profile_top_stage,profile_top_stage_ms,profile_prefill_ms,profile_decode_ms,profile_tpot_ms,profile_sampling_ms,profile_argmax_ms,profile_attention_ms,profile_ffn_ms,profile_logits_ms,profile_linear_paths,profile_logits_paths,profile_ffn_paths,profile_attention_paths,profile_argmax_paths,profile_ffn_strategy,profile_ffn_row_prefill_native_rows,profile_ffn_row_prefill_variant,profile_summary,fatal_line,log_file"
    jq -s -r '.[] | [
      .case_id,
      .mode,
      .prompt_kind,
      .quantize,
      .status,
      .exit_code,
      (.duration_s // ""),
      (.speed_tps // ""),
      (.chunks // ""),
      (.answer // "" | gsub("\""; "\"\"")),
      (.answer_repeat_run // ""),
      (.profile_backend // ""),
      (.profile_metal // ""),
      (.profile_core_path_status // ""),
      (.profile_linear_path_status // ""),
      (.profile_logits_path_status // ""),
      (.profile_ffn_path_status // ""),
      (.profile_attention_path_status // ""),
      (.profile_argmax_path_status // ""),
      (.profile_core_path_coverage // ""),
      (.profile_linear_path_coverage // ""),
      (.profile_logits_path_coverage // ""),
      (.profile_ffn_path_coverage // ""),
      (.profile_attention_path_coverage // ""),
      (.profile_argmax_path_coverage // ""),
      (.profile_top_stage // ""),
      (.profile_top_stage_ms // ""),
      (.profile_prefill_ms // ""),
      (.profile_decode_ms // ""),
      (.profile_tpot_ms // ""),
      (.profile_sampling_ms // ""),
      (.profile_argmax_ms // ""),
      (.profile_attention_ms // ""),
      (.profile_ffn_ms // ""),
      (.profile_logits_ms // ""),
      (.profile_linear_paths // "" | gsub("\""; "\"\"")),
      (.profile_logits_paths // "" | gsub("\""; "\"\"")),
      (.profile_ffn_paths // "" | gsub("\""; "\"\"")),
      (.profile_attention_paths // "" | gsub("\""; "\"\"")),
      (.profile_argmax_paths // "" | gsub("\""; "\"\"")),
      (.profile_ffn_strategy // ""),
      (.profile_ffn_row_prefill_native_rows // ""),
      (.profile_ffn_row_prefill_variant // ""),
      (.profile_summary // "" | gsub("\""; "\"\"")),
      (.fatal_line // "" | gsub("\""; "\"\"")),
      .log_file
    ] | @csv' "${objects_file}"
  } > "${SUMMARY_CSV}"

  {
    echo "case_id	status	backend	metal	corePathStatus	linearPathStatus	logitsPathStatus	ffnPathStatus	attentionPathStatus	argmaxPathStatus	corePathCoverage	linearPathCoverage	logitsPathCoverage	ffnPathCoverage	attentionPathCoverage	argmaxPathCoverage	topStage	topStageMs	prefillMs	decodeMs	tpotMs	samplingMs	argmaxMs	attentionMs	ffnMs	logitsMs	linearPaths	logitsPaths	ffnPaths	attentionPaths	argmaxPaths	ffnStrategy	ffnRowPrefillNativeRows	ffnRowPrefillVariant	logFile"
    jq -s -r '.[] | [
      .case_id,
      .status,
      (.profile_backend // "n/a"),
      (.profile_metal // "n/a"),
      (.profile_core_path_status // "n/a"),
      (.profile_linear_path_status // "n/a"),
      (.profile_logits_path_status // "n/a"),
      (.profile_ffn_path_status // "n/a"),
      (.profile_attention_path_status // "n/a"),
      (.profile_argmax_path_status // "n/a"),
      (.profile_core_path_coverage // "n/a"),
      (.profile_linear_path_coverage // "n/a"),
      (.profile_logits_path_coverage // "n/a"),
      (.profile_ffn_path_coverage // "n/a"),
      (.profile_attention_path_coverage // "n/a"),
      (.profile_argmax_path_coverage // "n/a"),
      (.profile_top_stage // "n/a"),
      (.profile_top_stage_ms // "n/a"),
      (.profile_prefill_ms // "n/a"),
      (.profile_decode_ms // "n/a"),
      (.profile_tpot_ms // "n/a"),
      (.profile_sampling_ms // "n/a"),
      (.profile_argmax_ms // "n/a"),
      (.profile_attention_ms // "n/a"),
      (.profile_ffn_ms // "n/a"),
      (.profile_logits_ms // "n/a"),
      (.profile_linear_paths // "n/a"),
      (.profile_logits_paths // "n/a"),
      (.profile_ffn_paths // "n/a"),
      (.profile_attention_paths // "n/a"),
      (.profile_argmax_paths // "n/a"),
      (.profile_ffn_strategy // "n/a"),
      (.profile_ffn_row_prefill_native_rows // "n/a"),
      (.profile_ffn_row_prefill_variant // "n/a"),
      .log_file
    ] | @tsv' "${objects_file}"
  } > "${PROFILE_TSV}"

  {
    echo "case	durationMs	speedTps	chunks	answerRepeatRun	backend	profileMetal	status	corePathStatus	linearPathStatus	logitsPathStatus	ffnPathStatus	attentionPathStatus	argmaxPathStatus	corePathCoverage	linearPathCoverage	logitsPathCoverage	ffnPathCoverage	attentionPathCoverage	argmaxPathCoverage	topStage	topStageMs	prefillMs	decodeMs	tpotMs	samplingMs	argmaxMs	attentionMs	ffnMs	logitsMs	linearPaths	logitsPaths	ffnPaths	attentionPaths	argmaxPaths	ffnStrategy	ffnRowPrefillNativeRows	ffnRowPrefillVariant	log"
    jq -s -r '.[] | [
      .case_id,
      (if .duration_s == null then "n/a" else (.duration_s * 1000 | round | tostring) end),
      (.speed_tps // "n/a"),
      (.chunks // "n/a"),
      (.answer_repeat_run // "n/a"),
      (.profile_backend // "n/a"),
      (.profile_metal // "n/a"),
      .status,
      (.profile_core_path_status // "n/a"),
      (.profile_linear_path_status // "n/a"),
      (.profile_logits_path_status // "n/a"),
      (.profile_ffn_path_status // "n/a"),
      (.profile_attention_path_status // "n/a"),
      (.profile_argmax_path_status // "n/a"),
      (.profile_core_path_coverage // "n/a"),
      (.profile_linear_path_coverage // "n/a"),
      (.profile_logits_path_coverage // "n/a"),
      (.profile_ffn_path_coverage // "n/a"),
      (.profile_attention_path_coverage // "n/a"),
      (.profile_argmax_path_coverage // "n/a"),
      (.profile_top_stage // "n/a"),
      (.profile_top_stage_ms // "n/a"),
      (.profile_prefill_ms // "n/a"),
      (.profile_decode_ms // "n/a"),
      (.profile_tpot_ms // "n/a"),
      (.profile_sampling_ms // "n/a"),
      (.profile_argmax_ms // "n/a"),
      (.profile_attention_ms // "n/a"),
      (.profile_ffn_ms // "n/a"),
      (.profile_logits_ms // "n/a"),
      (.profile_linear_paths // "n/a" | gsub(","; ";")),
      (.profile_logits_paths // "n/a" | gsub(","; ";")),
      (.profile_ffn_paths // "n/a" | gsub(","; ";")),
      (.profile_attention_paths // "n/a" | gsub(","; ";")),
      (.profile_argmax_paths // "n/a" | gsub(","; ";")),
      (.profile_ffn_strategy // "n/a"),
      (.profile_ffn_row_prefill_native_rows // "n/a"),
      (.profile_ffn_row_prefill_variant // "n/a"),
      .log_file
    ] | @tsv' "${objects_file}"
  } > "${GATE_TSV}"

  {
    echo "label=${LABEL}"
    echo "generated_at_utc=$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo "model=${MODEL_ID}"
    echo
    printf "%-26s %-8s %-14s %-10s %-8s %-12s %-10s %-8s %s\n" \
      "case_id" "mode" "prompt_kind" "quantize" "status" "duration_s" "speed_tps" "chunks" "answer"
    printf "%-26s %-8s %-14s %-10s %-8s %-12s %-10s %-8s %s\n" \
      "--------------------------" "--------" "--------------" "----------" "--------" "------------" "----------" "--------" "------"
    jq -s -r '.[] | [
      .case_id,
      .mode,
      .prompt_kind,
      .quantize,
      .status,
      (.duration_s // "n/a"),
      (.speed_tps // "n/a"),
      (.chunks // "n/a"),
      (.answer // "")
    ] | @tsv' "${objects_file}" | while IFS=$'\t' read -r case_id mode prompt_kind quantize status duration speed chunks answer; do
      printf "%-26s %-8s %-14s %-10s %-8s %-12s %-10s %-8s %s\n" \
        "${case_id}" "${mode}" "${prompt_kind}" "${quantize}" "${status}" "${duration}" "${speed}" "${chunks}" "${answer}"
    done
    echo
    printf "%-26s %-8s %-7s %-10s %-10s %-14s %-12s %-12s %-12s %-10s %-10s %-10s %s\n" \
      "case_id" "backend" "metal" "core_path" "coverage" "top_stage" "top_stage_ms" "decode_ms" "sampling" "argmax" "ffn_ms" "attn_ms" "paths"
    printf "%-26s %-8s %-7s %-10s %-10s %-14s %-12s %-12s %-12s %-10s %-10s %-10s %s\n" \
      "--------------------------" "--------" "-------" "----------" "----------" "--------------" "------------" "------------" "------------" "----------" "----------" "----------" "-----"
    jq -s -r '.[] | [
      .case_id,
      (.profile_backend // "n/a"),
      (.profile_metal // "n/a"),
      (.profile_core_path_status // "n/a"),
      (.profile_core_path_coverage // "n/a"),
      (.profile_top_stage // "n/a"),
      (.profile_top_stage_ms // "n/a"),
      (.profile_decode_ms // "n/a"),
      (.profile_sampling_ms // "n/a"),
      (.profile_argmax_ms // "n/a"),
      (.profile_ffn_ms // "n/a"),
      (.profile_attention_ms // "n/a"),
      ((.profile_linear_paths // "") + " " + (.profile_logits_paths // "") + " " + (.profile_ffn_paths // "") + " " + (.profile_attention_paths // "") + " " + (.profile_argmax_paths // "") | gsub("^[[:space:]]+|[[:space:]]+$"; ""))
    ] | @tsv' "${objects_file}" | while IFS=$'\t' read -r case_id backend metal core_path core_coverage top_stage top_stage_ms decode_ms sampling_ms argmax_ms ffn_ms attention_ms paths; do
      printf "%-26s %-8s %-7s %-10s %-10s %-14s %-12s %-12s %-12s %-10s %-10s %-10s %s\n" \
        "${case_id}" "${backend}" "${metal}" "${core_path}" "${core_coverage}" "${top_stage}" "${top_stage_ms}" "${decode_ms}" "${sampling_ms}" "${argmax_ms}" "${ffn_ms}" "${attention_ms}" "${paths:-n/a}"
    done
  } > "${REPORT_TXT}"

  rm -f "${objects_file}"
}

cleanup() {
  generate_outputs
  rm -f "${jsonl_file}"
}

generate_diagnosis() {
  local diagnosis_script="${SCRIPT_DIR}/diagnose-safetensor-profile-summary.sh"
  if [[ ! -f "${diagnosis_script}" ]]; then
    echo "[bench] safetensor diagnosis script not found: ${diagnosis_script}" >&2
    return 0
  fi
  mkdir -p "${DIAGNOSIS_DIR}"
  if ! bash "${diagnosis_script}" \
      --summary "${GATE_TSV}" \
      --summary-dir "${DIAGNOSIS_DIR}" > "${DIAGNOSIS_DIR}/diagnosis.stdout.log" 2> "${DIAGNOSIS_DIR}/diagnosis.stderr.log"; then
    echo "[bench] safetensor diagnosis skipped; profile metrics were not sufficient" >&2
    echo "[bench] diagnosis stderr: ${DIAGNOSIS_DIR}/diagnosis.stderr.log" >&2
    return 0
  fi
}

on_interrupt() {
  echo
  echo "[bench] interrupted; partial results saved:"
  echo "[bench]   - ${REPORT_TXT}"
  echo "[bench]   - ${SUMMARY_JSON}"
  echo "[bench]   - ${SUMMARY_CSV}"
  exit 130
}

trap on_interrupt INT TERM
trap cleanup EXIT

extract_duration_seconds() {
  local file="$1"
  local raw
  raw="$(strip_ansi < "${file}" | grep -oE 'Duration: [0-9]+,[0-9]+s|Duration: [0-9]+\.[0-9]+s|Duration: [0-9]+s' || true)"
  raw="$(printf '%s\n' "${raw}" | tail -n 1 | sed -E 's/^Duration: //; s/s$//')"
  raw="${raw/,/.}"
  if [[ -n "${raw}" ]]; then
    printf '%s' "${raw}"
  fi
}

extract_speed() {
  local file="$1"
  local raw
  raw="$(strip_ansi < "${file}" | grep -oE 'Speed: [0-9]+,[0-9]+ t/s|Speed: [0-9]+\.[0-9]+ t/s|Speed: [0-9]+ t/s' || true)"
  raw="$(printf '%s\n' "${raw}" | tail -n 1 | sed -E 's/^Speed: //; s/ t\/s$//')"
  raw="${raw/,/.}"
  if [[ -n "${raw}" ]]; then
    printf '%s' "${raw}"
  fi
}

extract_chunks() {
  local file="$1"
  local raw
  raw="$(strip_ansi < "${file}" | grep -oE 'Chunks: [0-9]+' || true)"
  raw="$(printf '%s\n' "${raw}" | tail -n 1 | awk '{print $2}')"
  if [[ -n "${raw}" ]]; then
    printf '%s' "${raw}"
    return
  fi
  raw="$(strip_ansi < "${file}" | grep -oE 'Stream updates: [0-9]+' || true)"
  printf '%s\n' "${raw}" | tail -n 1 | awk '{print $3}'
}

extract_profile_summary() {
  local file="$1"
  local raw
  raw="$(strip_ansi < "${file}" | grep '^\[PROFILE\]' || true)"
  printf '%s\n' "${raw}" | tail -n 1 | sed 's/^\[PROFILE\] //'
}

profile_field() {
  local summary="$1"
  local key="$2"
  printf '%s\n' "${summary}" \
    | grep -oE "(^| )${key}=[^ ]+" \
    | tail -n 1 \
    | sed -E "s/^ ?${key}=//" \
    || true
}

profile_metric_ms() {
  local summary="$1"
  local key="$2"
  printf '%s\n' "${summary}" \
    | grep -oE "(^| )${key}=[0-9]+([.][0-9]+)?ms" \
    | tail -n 1 \
    | sed -E "s/^ ?${key}=//; s/ms$//" \
    || true
}

profile_block() {
  local summary="$1"
  local key="$2"
  printf '%s\n' "${summary}" | sed -nE "s/.*${key}=\\{([^}]*)\\}.*/\\1/p"
}

profile_block_field() {
  local block="$1"
  local key="$2"
  printf '%s\n' "${block}" \
    | tr ',' '\n' \
    | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' \
    | grep -E "^${key}=" \
    | tail -n 1 \
    | sed -E "s/^${key}=//" \
    || true
}

profile_path_attribute() {
  local block="$1"
  local key="$2"
  printf '%s\n' "${block}" \
    | grep -oE "(^|[:,[:space:]])${key}=[^:=,[:space:]]+" \
    | tail -n 1 \
    | sed -E "s/^[:,[:space:]]*${key}=//" \
    || true
}

profile_top_stage() {
  local summary="$1"
  local stage value
  for stage in load tokenize session prefill decode sampling argmax attention ffn logits logits_copy; do
    value="$(profile_metric_ms "${summary}" "${stage}")"
    if [[ -n "${value}" ]]; then
      printf '%s\t%s\n' "${stage}" "${value}"
    fi
  done | awk '
    BEGIN { best = ""; bestv = -1 }
    {
      value = $2 + 0
      if (value > bestv) {
        best = $1
        bestv = value
      }
    }
    END {
      if (best != "") {
        printf "%s\t%.2f", best, bestv
      }
    }
  '
}

profile_metal_status() {
  local backend="$1"
  local file="$2"
  local profile="${3:-}"
  local lower_backend
  lower_backend="$(printf '%s' "${backend}" | tr '[:upper:]' '[:lower:]')"
  if [[ "${lower_backend}" == *metal* || "${lower_backend}" == *mtl* || "${lower_backend}" == *gpu* ]]; then
    printf 'true'
    return
  fi
  if [[ -n "${backend}" ]]; then
    printf 'false'
    return
  fi
  if [[ -n "${profile}" ]]; then
    printf 'false'
    return
  fi
  if strip_ansi < "${file}" | grep -Eq 'GPU acceleration enabled|Platform: Metal|backend=metal|MTL[0-9]'; then
    printf 'true'
  else
    printf 'false'
  fi
}

decimal_gt() {
  local left="$1"
  local right="$2"
  awk -v left="${left}" -v right="${right}" 'BEGIN { exit ((left + 0) > (right + 0)) ? 0 : 1 }'
}

decimal_lt() {
  local left="$1"
  local right="$2"
  awk -v left="${left}" -v right="${right}" 'BEGIN { exit ((left + 0) < (right + 0)) ? 0 : 1 }'
}

append_gate_failure() {
  local existing="$1"
  local next="$2"
  if [[ -z "${existing}" ]]; then
    printf '%s' "${next}"
  else
    printf '%s; %s' "${existing}" "${next}"
  fi
}

append_metric_threshold_failure() {
  local existing="$1"
  local metric="$2"
  local value="$3"
  local threshold="$4"
  if [[ -n "${threshold}" && -n "${value}" ]] && decimal_gt "${value}" "${threshold}"; then
    append_gate_failure "${existing}" "${metric}=${value}ms exceeded ${threshold}ms"
  else
    printf '%s' "${existing}"
  fi
}

append_min_metric_threshold_failure() {
  local existing="$1"
  local metric="$2"
  local value="$3"
  local threshold="$4"
  local suffix="$5"
  if [[ -z "${threshold}" ]]; then
    printf '%s' "${existing}"
  elif [[ -z "${value}" ]]; then
    append_gate_failure "${existing}" "${metric} was required but not measured"
  elif decimal_lt "${value}" "${threshold}"; then
    append_gate_failure "${existing}" "${metric}=${value}${suffix} below ${threshold}${suffix}"
  else
    printf '%s' "${existing}"
  fi
}

ffn_strategy_allowed() {
  local actual="$1"
  local required="$2"
  local expected
  IFS=',' read -r -a expected_strategies <<< "${required}"
  for expected in "${expected_strategies[@]}"; do
    expected="$(trim_value "${expected}")"
    if [[ -n "${expected}" && "${actual}" == "${expected}" ]]; then
      return 0
    fi
  done
  return 1
}

answer_matches_required_regex() {
  local answer="$1"
  if [[ -z "${REQUIRE_ANSWER_REGEX}" ]]; then
    return 0
  fi
  printf '%s\n' "${answer}" | grep -Eiq -- "${REQUIRE_ANSWER_REGEX}"
}

answer_repeated_token_run() {
  local answer="$1"
  printf '%s\n' "${answer}" | awk '
    BEGIN {
      best = 0
      run = 0
      previous = ""
    }
    {
      for (i = 1; i <= NF; i++) {
        token = tolower($i)
        gsub(/^[^[:alnum:]]+/, "", token)
        gsub(/[^[:alnum:]]+$/, "", token)
        if (token == "") {
          continue
        }
        if (token == previous) {
          run++
        } else {
          previous = token
          run = 1
        }
        if (run > best) {
          best = run
        }
      }
    }
    END {
      print best
    }
  '
}

coverage_ratio() {
  local coverage="$1"
  if [[ "${coverage}" =~ ^([0-9]+)/([0-9]+)$ ]]; then
    local metal="${BASH_REMATCH[1]}"
    local total="${BASH_REMATCH[2]}"
    if (( total > 0 )); then
      awk -v metal="${metal}" -v total="${total}" 'BEGIN { printf "%.6f", metal / total }'
    fi
  fi
}

format_ratio_threshold() {
  local threshold="$1"
  awk -v threshold="${threshold}" 'BEGIN { printf "%.3f", threshold + 0 }'
}

append_coverage_threshold_failure() {
  local existing="$1"
  local label="$2"
  local coverage="$3"
  local threshold="$4"
  local ratio
  if [[ -z "${threshold}" ]] || ! decimal_gt "${threshold}" "0"; then
    printf '%s' "${existing}"
    return
  fi
  ratio="$(coverage_ratio "${coverage}")"
  if [[ -z "${ratio}" ]]; then
    append_gate_failure "${existing}" "${label} Metal coverage was required but not measured (${coverage:-missing})"
    return
  fi
  if decimal_lt "${ratio}" "${threshold}"; then
    append_gate_failure "${existing}" \
      "${label} Metal coverage ${coverage} below $(format_ratio_threshold "${threshold}")"
  else
    printf '%s' "${existing}"
  fi
}

metal_path_proven() {
  local value="$1"
  local lower_value
  lower_value="$(printf '%s' "${value}" | tr '[:upper:]' '[:lower:]')"
  [[ "${lower_value}" == *metal* \
    || "${lower_value}" == *mtl* \
    || "${lower_value}" == *mps* \
    || "${lower_value}" == *gpu* \
    || "${lower_value}" == *fused-gated-ffn:accept* \
    || "${lower_value}" == *matvec-gated-ffn-prefill-rows:accept* \
    || "${lower_value}" == *native_bf16=true* \
    || "${lower_value}" == *native_argmax* ]]
}

fallback_path_present() {
  local value="$1"
  local lower_value
  lower_value="$(printf '%s' "${value}" | tr '[:upper:]' '[:lower:]')"
  lower_value="${lower_value//matvec-gated-ffn-prefill-rows:skip:strategy_prefers_fused_geglu_prefill/}"
  [[ "${lower_value}" == *cpu* \
    || "${lower_value}" == *java* \
    || "${lower_value}" == *accelerate* \
    || "${lower_value}" == *fallback* \
    || "${lower_value}" == *skip* \
    || "${lower_value}" == *reject* \
    || "${lower_value}" == *unavailable* ]]
}

append_metal_path_failure() {
  local existing="$1"
  local label="$2"
  local value="$3"
  if metal_path_proven "${value}"; then
    printf '%s' "${existing}"
  else
    append_gate_failure "${existing}" "Metal ${label} path was required but not proven (${value:-missing})"
  fi
}

append_fallback_path_failure() {
  local existing="$1"
  local label="$2"
  local value="$3"
  if fallback_path_present "${value}"; then
    append_gate_failure "${existing}" "Fallback ${label} path was rejected (${value})"
  else
    printf '%s' "${existing}"
  fi
}

extract_fatal_line() {
  local file="$1"
  local raw
  raw="$(strip_ansi < "${file}" | grep '^\[FATAL\]' || true)"
  printf '%s\n' "${raw}" | tail -n 1
}

extract_answer() {
  local file="$1"
  strip_ansi < "${file}" | awk '
    /--------------------------------------------------/ {capture=1; next}
    capture && /^\[PROFILE\]/ {capture=0}
    capture && /^\[Chunks:/ {capture=0}
    capture && /^Platform: / {next}
    capture && /^✓ GPU acceleration enabled/ {next}
    capture && /^⚡ / {next}
    capture && /^   / {next}
    capture && /^Add --quantize turbo/ {next}
    capture && /^Example: / {next}
    capture && /^Using local model: / {next}
    capture && /^Model: / {next}
    capture && /^Provider: / {next}
    capture && NF {print}
  ' | sed '/^[[:space:]]*$/d' | tr '\n' ' ' | sed 's/[[:space:]]\+/ /g; s/^ //; s/ $//'
}

strip_ansi() {
  sed -E $'s/\x1B\\[[0-9;]*[A-Za-z]//g'
}

run_case() {
  local case_id="$1"
  local mode="$2"
  local prompt_kind="$3"
  local prompt_text="$4"
  local quant_strategy="${5:-}"
  local log_file="${RUN_DIR}/logs/${case_id}.log"
  local status="passed"
  local exit_code=0
  local heartbeat_pid=""

  local -a cmd=( "${AQLI_BIN}" )
  local java_opts=()
  if (( PROFILE_ENABLED == 1 )); then
    java_opts+=( "-Daqli.profile=true" )
  fi
  local effective_java_opts
  effective_java_opts="$(merged_java_opts "${java_opts[@]}")"

  if [[ "${mode}" == "cpu" ]]; then
    cmd+=( "--use-cpu" )
  fi

  cmd+=( run --model "${MODEL_ID}" --prompt "${prompt_text}" --max-tokens "${MAX_TOKENS}" --temperature 0 --top-k 1 --top-p 1 --direct )

  if [[ -n "${quant_strategy}" ]]; then
    cmd+=( --quantize "${quant_strategy}" )
  fi

  echo "[bench] running ${case_id} ..."
  echo "[bench] log: ${log_file}"
  set +e
  (
    local elapsed=0
    while true; do
      sleep "${HEARTBEAT_SECONDS}" || exit 0
      elapsed=$((elapsed + HEARTBEAT_SECONDS))
      echo "[bench] ${case_id} still running (${elapsed}s elapsed)"
    done
  ) &
  heartbeat_pid=$!
  {
    echo "case_id=${case_id}"
    echo "mode=${mode}"
    echo "prompt_kind=${prompt_kind}"
    echo "quantize=${quant_strategy:-none}"
    echo "command=${cmd[*]}"
    echo "aqli_java_opts=${effective_java_opts:-none}"
    echo
    if [[ -n "${effective_java_opts}" ]]; then
      AQLI_JAVA_OPTS="${effective_java_opts}" "${cmd[@]}"
    else
      "${cmd[@]}"
    fi
  } > "${log_file}" 2>&1
  exit_code=$?
  if [[ -n "${heartbeat_pid}" ]]; then
    kill "${heartbeat_pid}" >/dev/null 2>&1 || true
    wait "${heartbeat_pid}" 2>/dev/null || true
  fi
  set -e

  if (( exit_code != 0 )); then
    status="failed"
  fi

  local duration speed chunks answer answer_repeat_run profile fatal_line
  local profile_backend profile_metal profile_top profile_top_stage profile_top_stage_ms
  local profile_prefill_ms profile_decode_ms profile_tpot_ms profile_sampling_ms profile_argmax_ms
  local profile_attention_ms profile_ffn_ms profile_logits_ms
  local profile_linear_paths profile_logits_paths profile_ffn_paths profile_attention_paths profile_argmax_paths
  local profile_ffn_strategy profile_ffn_row_prefill_native_rows profile_ffn_row_prefill_variant
  local profile_path_status profile_core_path_status profile_linear_path_status profile_logits_path_status
  local profile_ffn_path_status profile_attention_path_status profile_argmax_path_status
  local profile_path_coverage profile_core_path_coverage profile_linear_path_coverage profile_logits_path_coverage
  local profile_ffn_path_coverage profile_attention_path_coverage profile_argmax_path_coverage
  duration="$(extract_duration_seconds "${log_file}")"
  speed="$(extract_speed "${log_file}")"
  chunks="$(extract_chunks "${log_file}")"
  answer="$(extract_answer "${log_file}")"
  answer_repeat_run="$(answer_repeated_token_run "${answer}")"
  profile="$(extract_profile_summary "${log_file}")"
  fatal_line="$(extract_fatal_line "${log_file}")"
  profile_backend="$(profile_field "${profile}" backend)"
  profile_metal="$(profile_metal_status "${profile_backend}" "${log_file}" "${profile}")"
  profile_top="$(profile_top_stage "${profile}")"
  profile_top_stage="${profile_top%%$'\t'*}"
  profile_top_stage_ms="${profile_top#*$'\t'}"
  if [[ "${profile_top_stage_ms}" == "${profile_top}" ]]; then
    profile_top_stage_ms=""
  fi
  profile_prefill_ms="$(profile_metric_ms "${profile}" prefill)"
  profile_decode_ms="$(profile_metric_ms "${profile}" decode)"
  profile_tpot_ms="$(profile_metric_ms "${profile}" tpot)"
  profile_sampling_ms="$(profile_metric_ms "${profile}" sampling)"
  profile_argmax_ms="$(profile_metric_ms "${profile}" argmax)"
  profile_attention_ms="$(profile_metric_ms "${profile}" attention)"
  profile_ffn_ms="$(profile_metric_ms "${profile}" ffn)"
  profile_logits_ms="$(profile_metric_ms "${profile}" logits)"
  profile_linear_paths="$(profile_block "${profile}" linear_paths)"
  profile_logits_paths="$(profile_block "${profile}" logits_paths)"
  profile_ffn_paths="$(profile_block "${profile}" ffn_paths)"
  profile_attention_paths="$(profile_block "${profile}" attention_paths)"
  profile_argmax_paths="$(profile_block "${profile}" argmax_paths)"
  profile_ffn_strategy="$(profile_field "${profile}" ffn_strategy)"
  profile_ffn_row_prefill_native_rows="$(profile_path_attribute "${profile_ffn_paths}" native_rows)"
  profile_ffn_row_prefill_variant="$(profile_path_attribute "${profile_ffn_paths}" variant)"
  profile_path_status="$(profile_block "${profile}" path_status)"
  profile_core_path_status="$(profile_block_field "${profile_path_status}" core)"
  profile_linear_path_status="$(profile_block_field "${profile_path_status}" linear)"
  profile_logits_path_status="$(profile_block_field "${profile_path_status}" logits)"
  profile_ffn_path_status="$(profile_block_field "${profile_path_status}" ffn)"
  profile_attention_path_status="$(profile_block_field "${profile_path_status}" attention)"
  profile_argmax_path_status="$(profile_block_field "${profile_path_status}" argmax)"
  profile_path_coverage="$(profile_block "${profile}" path_coverage)"
  profile_core_path_coverage="$(profile_block_field "${profile_path_coverage}" core)"
  profile_linear_path_coverage="$(profile_block_field "${profile_path_coverage}" linear)"
  profile_logits_path_coverage="$(profile_block_field "${profile_path_coverage}" logits)"
  profile_ffn_path_coverage="$(profile_block_field "${profile_path_coverage}" ffn)"
  profile_attention_path_coverage="$(profile_block_field "${profile_path_coverage}" attention)"
  profile_argmax_path_coverage="$(profile_block_field "${profile_path_coverage}" argmax)"

  local gate_failure=""
  if (( REQUIRE_PROFILE == 1 )) && [[ -z "${profile}" ]]; then
    gate_failure="$(append_gate_failure "${gate_failure}" "missing safetensor profile output")"
  fi
  if [[ -n "${REQUIRE_FFN_STRATEGY}" ]]; then
    if [[ -z "${profile_ffn_strategy}" ]]; then
      gate_failure="$(append_gate_failure "${gate_failure}" \
        "ffn_strategy was required but not measured (${REQUIRE_FFN_STRATEGY})")"
    elif ! ffn_strategy_allowed "${profile_ffn_strategy}" "${REQUIRE_FFN_STRATEGY}"; then
      gate_failure="$(append_gate_failure "${gate_failure}" \
        "ffn_strategy=${profile_ffn_strategy} did not match required ${REQUIRE_FFN_STRATEGY}")"
    fi
  fi
  if (( REQUIRE_METAL == 1 )) && [[ "${mode}" != "cpu" && "${profile_metal}" != "true" ]]; then
    gate_failure="$(append_gate_failure "${gate_failure}" "Metal backend was required but not proven")"
  fi
  if (( REQUIRE_METAL_PATHS == 1 )) && [[ "${mode}" != "cpu" ]]; then
    gate_failure="$(append_metal_path_failure "${gate_failure}" "linear" "${profile_linear_paths}")"
    gate_failure="$(append_metal_path_failure "${gate_failure}" "logits" "${profile_logits_paths}")"
    gate_failure="$(append_metal_path_failure "${gate_failure}" "FFN" "${profile_ffn_paths}")"
    gate_failure="$(append_metal_path_failure "${gate_failure}" "attention" "${profile_attention_paths}")"
    gate_failure="$(append_metal_path_failure "${gate_failure}" "argmax" "${profile_argmax_paths}")"
  fi
  if (( REJECT_FALLBACK_PATHS == 1 )) && [[ "${mode}" != "cpu" ]]; then
    gate_failure="$(append_fallback_path_failure "${gate_failure}" "linear" "${profile_linear_paths}")"
    gate_failure="$(append_fallback_path_failure "${gate_failure}" "logits" "${profile_logits_paths}")"
    gate_failure="$(append_fallback_path_failure "${gate_failure}" "FFN" "${profile_ffn_paths}")"
    gate_failure="$(append_fallback_path_failure "${gate_failure}" "attention" "${profile_attention_paths}")"
    gate_failure="$(append_fallback_path_failure "${gate_failure}" "argmax" "${profile_argmax_paths}")"
  fi
  if ! answer_matches_required_regex "${answer}"; then
    gate_failure="$(append_gate_failure "${gate_failure}" \
      "answer did not match required regex (${REQUIRE_ANSWER_REGEX})")"
  fi
  if [[ -n "${MAX_REPEATED_TOKEN_RUN}" && -n "${answer_repeat_run}" ]] \
      && (( answer_repeat_run > MAX_REPEATED_TOKEN_RUN )); then
    gate_failure="$(append_gate_failure "${gate_failure}" \
      "answer repeated token run ${answer_repeat_run} exceeded ${MAX_REPEATED_TOKEN_RUN}")"
  fi
  if [[ -n "${MIN_CHUNKS}" && -z "${chunks}" ]]; then
    gate_failure="$(append_gate_failure "${gate_failure}" "chunks were required but not measured")"
  elif [[ -n "${MIN_CHUNKS}" ]] && (( chunks < MIN_CHUNKS )); then
    gate_failure="$(append_gate_failure "${gate_failure}" \
      "chunks=${chunks} below ${MIN_CHUNKS}")"
  fi
  if [[ "${mode}" != "cpu" ]]; then
    gate_failure="$(append_coverage_threshold_failure "${gate_failure}" "core" "${profile_core_path_coverage}" "${MIN_CORE_METAL_COVERAGE}")"
    gate_failure="$(append_coverage_threshold_failure "${gate_failure}" "linear" "${profile_linear_path_coverage}" "${MIN_LINEAR_METAL_COVERAGE}")"
    gate_failure="$(append_coverage_threshold_failure "${gate_failure}" "logits" "${profile_logits_path_coverage}" "${MIN_LOGITS_METAL_COVERAGE}")"
    gate_failure="$(append_coverage_threshold_failure "${gate_failure}" "FFN" "${profile_ffn_path_coverage}" "${MIN_FFN_METAL_COVERAGE}")"
    gate_failure="$(append_coverage_threshold_failure "${gate_failure}" "attention" "${profile_attention_path_coverage}" "${MIN_ATTENTION_METAL_COVERAGE}")"
    gate_failure="$(append_coverage_threshold_failure "${gate_failure}" "argmax" "${profile_argmax_path_coverage}" "${MIN_ARGMAX_METAL_COVERAGE}")"
  fi
  if [[ -n "${MAX_TOP_STAGE_MS}" && -n "${profile_top_stage_ms}" ]] \
      && decimal_gt "${profile_top_stage_ms}" "${MAX_TOP_STAGE_MS}"; then
    gate_failure="$(append_gate_failure "${gate_failure}" \
      "top stage ${profile_top_stage:-unknown}=${profile_top_stage_ms}ms exceeded ${MAX_TOP_STAGE_MS}ms")"
  fi
  gate_failure="$(append_min_metric_threshold_failure "${gate_failure}" "speed" "${speed}" "${MIN_SPEED_TPS}" " t/s")"
  gate_failure="$(append_metric_threshold_failure "${gate_failure}" "prefill" "${profile_prefill_ms}" "${MAX_PREFILL_MS}")"
  gate_failure="$(append_metric_threshold_failure "${gate_failure}" "decode" "${profile_decode_ms}" "${MAX_DECODE_MS}")"
  gate_failure="$(append_metric_threshold_failure "${gate_failure}" "tpot" "${profile_tpot_ms}" "${MAX_TPOT_MS}")"
  gate_failure="$(append_metric_threshold_failure "${gate_failure}" "sampling" "${profile_sampling_ms}" "${MAX_SAMPLING_MS}")"
  gate_failure="$(append_metric_threshold_failure "${gate_failure}" "argmax" "${profile_argmax_ms}" "${MAX_ARGMAX_MS}")"
  gate_failure="$(append_metric_threshold_failure "${gate_failure}" "attention" "${profile_attention_ms}" "${MAX_ATTENTION_MS}")"
  gate_failure="$(append_metric_threshold_failure "${gate_failure}" "ffn" "${profile_ffn_ms}" "${MAX_FFN_MS}")"
  gate_failure="$(append_metric_threshold_failure "${gate_failure}" "logits" "${profile_logits_ms}" "${MAX_LOGITS_MS}")"
  if [[ -n "${gate_failure}" ]]; then
    status="failed"
    if (( exit_code == 0 )); then
      exit_code=1
    fi
    if [[ -z "${fatal_line}" ]]; then
      fatal_line="[FATAL] ${gate_failure}"
    fi
  fi

  if [[ "${status}" == "failed" ]]; then
    echo "[bench] ${case_id} failed (exit=${exit_code})"
    if [[ -n "${fatal_line}" ]]; then
      echo "[bench] ${fatal_line}"
    fi
    echo "[bench] log: ${log_file}"
  else
    echo "[bench] ${case_id} done: duration=${duration:-n/a}s speed=${speed:-n/a} t/s"
  fi
  if (( exit_code != 0 && FINAL_EXIT_CODE == 0 )); then
    FINAL_EXIT_CODE="${exit_code}"
  fi

  jq -nc \
    --arg case_id "${case_id}" \
    --arg model "${MODEL_ID}" \
    --arg mode "${mode}" \
    --arg prompt_kind "${prompt_kind}" \
    --arg prompt "${prompt_text}" \
    --arg quantize "${quant_strategy:-none}" \
    --arg duration_s "${duration:-}" \
    --arg speed_tps "${speed:-}" \
    --arg chunks "${chunks:-}" \
    --arg answer "${answer:-}" \
    --arg answer_repeat_run "${answer_repeat_run:-}" \
    --arg profile "${profile:-}" \
    --arg profile_backend "${profile_backend:-}" \
    --arg profile_metal "${profile_metal:-}" \
    --arg profile_core_path_status "${profile_core_path_status:-}" \
    --arg profile_linear_path_status "${profile_linear_path_status:-}" \
    --arg profile_logits_path_status "${profile_logits_path_status:-}" \
    --arg profile_ffn_path_status "${profile_ffn_path_status:-}" \
    --arg profile_attention_path_status "${profile_attention_path_status:-}" \
    --arg profile_argmax_path_status "${profile_argmax_path_status:-}" \
    --arg profile_core_path_coverage "${profile_core_path_coverage:-}" \
    --arg profile_linear_path_coverage "${profile_linear_path_coverage:-}" \
    --arg profile_logits_path_coverage "${profile_logits_path_coverage:-}" \
    --arg profile_ffn_path_coverage "${profile_ffn_path_coverage:-}" \
    --arg profile_attention_path_coverage "${profile_attention_path_coverage:-}" \
    --arg profile_argmax_path_coverage "${profile_argmax_path_coverage:-}" \
    --arg profile_top_stage "${profile_top_stage:-}" \
    --arg profile_top_stage_ms "${profile_top_stage_ms:-}" \
    --arg profile_prefill_ms "${profile_prefill_ms:-}" \
    --arg profile_decode_ms "${profile_decode_ms:-}" \
    --arg profile_tpot_ms "${profile_tpot_ms:-}" \
    --arg profile_sampling_ms "${profile_sampling_ms:-}" \
    --arg profile_argmax_ms "${profile_argmax_ms:-}" \
    --arg profile_attention_ms "${profile_attention_ms:-}" \
    --arg profile_ffn_ms "${profile_ffn_ms:-}" \
    --arg profile_logits_ms "${profile_logits_ms:-}" \
    --arg profile_linear_paths "${profile_linear_paths:-}" \
    --arg profile_logits_paths "${profile_logits_paths:-}" \
    --arg profile_ffn_paths "${profile_ffn_paths:-}" \
    --arg profile_attention_paths "${profile_attention_paths:-}" \
    --arg profile_argmax_paths "${profile_argmax_paths:-}" \
    --arg profile_ffn_strategy "${profile_ffn_strategy:-}" \
    --arg profile_ffn_row_prefill_native_rows "${profile_ffn_row_prefill_native_rows:-}" \
    --arg profile_ffn_row_prefill_variant "${profile_ffn_row_prefill_variant:-}" \
    --arg status "${status}" \
    --arg fatal_line "${fatal_line:-}" \
    --argjson exit_code "${exit_code}" \
    --arg log_file "${log_file}" \
    '{
      case_id: $case_id,
      model: $model,
      mode: $mode,
      prompt_kind: $prompt_kind,
      prompt: $prompt,
      quantize: $quantize,
      status: $status,
      exit_code: $exit_code,
      duration_s: (if $duration_s == "" then null else ($duration_s | tonumber) end),
      speed_tps: (if $speed_tps == "" then null else ($speed_tps | tonumber) end),
      chunks: (if $chunks == "" then null else ($chunks | tonumber) end),
      answer: $answer,
      answer_repeat_run: (if $answer_repeat_run == "" then null else ($answer_repeat_run | tonumber) end),
      profile_summary: $profile,
      profile_backend: (if $profile_backend == "" then null else $profile_backend end),
      profile_metal: (if $profile_metal == "" then null else $profile_metal end),
      profile_core_path_status: (if $profile_core_path_status == "" then null else $profile_core_path_status end),
      profile_linear_path_status: (if $profile_linear_path_status == "" then null else $profile_linear_path_status end),
      profile_logits_path_status: (if $profile_logits_path_status == "" then null else $profile_logits_path_status end),
      profile_ffn_path_status: (if $profile_ffn_path_status == "" then null else $profile_ffn_path_status end),
      profile_attention_path_status: (if $profile_attention_path_status == "" then null else $profile_attention_path_status end),
      profile_argmax_path_status: (if $profile_argmax_path_status == "" then null else $profile_argmax_path_status end),
      profile_core_path_coverage: (if $profile_core_path_coverage == "" then null else $profile_core_path_coverage end),
      profile_linear_path_coverage: (if $profile_linear_path_coverage == "" then null else $profile_linear_path_coverage end),
      profile_logits_path_coverage: (if $profile_logits_path_coverage == "" then null else $profile_logits_path_coverage end),
      profile_ffn_path_coverage: (if $profile_ffn_path_coverage == "" then null else $profile_ffn_path_coverage end),
      profile_attention_path_coverage: (if $profile_attention_path_coverage == "" then null else $profile_attention_path_coverage end),
      profile_argmax_path_coverage: (if $profile_argmax_path_coverage == "" then null else $profile_argmax_path_coverage end),
      profile_top_stage: (if $profile_top_stage == "" then null else $profile_top_stage end),
      profile_top_stage_ms: (if $profile_top_stage_ms == "" then null else ($profile_top_stage_ms | tonumber) end),
      profile_prefill_ms: (if $profile_prefill_ms == "" then null else ($profile_prefill_ms | tonumber) end),
      profile_decode_ms: (if $profile_decode_ms == "" then null else ($profile_decode_ms | tonumber) end),
      profile_tpot_ms: (if $profile_tpot_ms == "" then null else ($profile_tpot_ms | tonumber) end),
      profile_sampling_ms: (if $profile_sampling_ms == "" then null else ($profile_sampling_ms | tonumber) end),
      profile_argmax_ms: (if $profile_argmax_ms == "" then null else ($profile_argmax_ms | tonumber) end),
      profile_attention_ms: (if $profile_attention_ms == "" then null else ($profile_attention_ms | tonumber) end),
      profile_ffn_ms: (if $profile_ffn_ms == "" then null else ($profile_ffn_ms | tonumber) end),
      profile_logits_ms: (if $profile_logits_ms == "" then null else ($profile_logits_ms | tonumber) end),
      profile_linear_paths: (if $profile_linear_paths == "" then null else $profile_linear_paths end),
      profile_logits_paths: (if $profile_logits_paths == "" then null else $profile_logits_paths end),
      profile_ffn_paths: (if $profile_ffn_paths == "" then null else $profile_ffn_paths end),
      profile_attention_paths: (if $profile_attention_paths == "" then null else $profile_attention_paths end),
      profile_argmax_paths: (if $profile_argmax_paths == "" then null else $profile_argmax_paths end),
      profile_ffn_strategy: (if $profile_ffn_strategy == "" then null else $profile_ffn_strategy end),
      profile_ffn_row_prefill_native_rows: (if $profile_ffn_row_prefill_native_rows == "" then null else ($profile_ffn_row_prefill_native_rows | tonumber) end),
      profile_ffn_row_prefill_variant: (if $profile_ffn_row_prefill_variant == "" then null else $profile_ffn_row_prefill_variant end),
      fatal_line: $fatal_line,
      log_file: $log_file
    }' >> "${jsonl_file}"

  generate_outputs
}

declare -a CASE_PLAN=()

if (( QUICK_MODE == 1 )); then
  CASE_PLAN+=(
    "metal-deterministic|metal|deterministic|${DET_PROMPT}|"
  )
  if (( WITH_CPU == 1 )); then
    CASE_PLAN+=(
      "cpu-deterministic|cpu|deterministic|${DET_PROMPT}|"
    )
  fi
  if (( WITH_QUANT_TURBO == 1 )); then
    CASE_PLAN+=(
      "metal-deterministic-turbo|metal|deterministic|${DET_PROMPT}|turbo"
    )
    if (( WITH_CPU == 1 )); then
      CASE_PLAN+=(
        "cpu-deterministic-turbo|cpu|deterministic|${DET_PROMPT}|turbo"
      )
    fi
  fi
else
  CASE_PLAN+=(
    "metal-deterministic|metal|deterministic|${DET_PROMPT}|"
    "metal-normal|metal|normal|${NORMAL_PROMPT}|"
  )

  if (( WITH_CPU == 1 )); then
    CASE_PLAN+=(
      "cpu-deterministic|cpu|deterministic|${DET_PROMPT}|"
      "cpu-normal|cpu|normal|${NORMAL_PROMPT}|"
    )
  fi

  if (( WITH_QUANT_TURBO == 1 )); then
    CASE_PLAN+=(
      "metal-deterministic-turbo|metal|deterministic|${DET_PROMPT}|turbo"
      "metal-normal-turbo|metal|normal|${NORMAL_PROMPT}|turbo"
    )
    if (( WITH_CPU == 1 )); then
      CASE_PLAN+=(
        "cpu-deterministic-turbo|cpu|deterministic|${DET_PROMPT}|turbo"
        "cpu-normal-turbo|cpu|normal|${NORMAL_PROMPT}|turbo"
      )
    fi
  fi
fi

echo "[bench] label: ${LABEL}"
echo "[bench] output: ${RUN_DIR}"
echo "[bench] cases: ${#CASE_PLAN[@]}"
echo "[bench] quick_mode: $([[ ${QUICK_MODE} -eq 1 ]] && echo on || echo off)"
echo "[bench] note: large Gemma runs can take several minutes per case"
for case_spec in "${CASE_PLAN[@]}"; do
  IFS='|' read -r case_id mode prompt_kind _prompt_text quant_strategy <<< "${case_spec}"
  echo "[bench] plan: ${case_id} mode=${mode} prompt=${prompt_kind} quantize=${quant_strategy:-none}"
done

if [[ -n "${CASES_FILTER}" ]]; then
  IFS=',' read -r -a allowed_cases <<< "${CASES_FILTER}"
  declare -a FILTERED_CASE_PLAN=()
  for case_spec in "${CASE_PLAN[@]}"; do
    IFS='|' read -r case_id _mode _prompt_kind _prompt_text _quant_strategy <<< "${case_spec}"
    for allowed_case in "${allowed_cases[@]}"; do
      if [[ "${case_id}" == "${allowed_case}" ]]; then
        FILTERED_CASE_PLAN+=( "${case_spec}" )
        break
      fi
    done
  done
  CASE_PLAN=( "${FILTERED_CASE_PLAN[@]}" )
fi

if (( ${#CASE_PLAN[@]} == 0 )); then
  echo "No cases selected to run" >&2
  exit 2
fi

echo "[bench] selected cases: ${#CASE_PLAN[@]}"
for case_spec in "${CASE_PLAN[@]}"; do
  IFS='|' read -r case_id mode prompt_kind _prompt_text quant_strategy <<< "${case_spec}"
  echo "[bench] selected: ${case_id} mode=${mode} prompt=${prompt_kind} quantize=${quant_strategy:-none}"
done

for case_spec in "${CASE_PLAN[@]}"; do
  IFS='|' read -r case_id mode prompt_kind prompt_text quant_strategy <<< "${case_spec}"
  run_case "${case_id}" "${mode}" "${prompt_kind}" "${prompt_text}" "${quant_strategy}"
done
generate_diagnosis

echo "Safetensor benchmark generated:"
echo "  - ${REPORT_TXT}"
echo "  - ${SUMMARY_JSON}"
echo "  - ${SUMMARY_CSV}"
echo "  - ${PROFILE_TSV}"
echo "  - ${GATE_TSV}"
if [[ -f "${DIAGNOSIS_REPORT}" ]]; then
  echo "  - ${DIAGNOSIS_REPORT}"
  echo "  - ${DIAGNOSIS_TSV}"
  echo "  - ${DIAGNOSIS_STAGES_TSV}"
  echo "  - ${DIAGNOSIS_PATHS_TSV}"
fi
exit "${FINAL_EXIT_CODE}"
