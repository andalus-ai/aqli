#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: capture-onnx-profile-baseline.sh --model MODEL [options]

Run an ONNX profile benchmark, aggregate the measured rows, and store a
reusable baseline under ops/benchmarks/onnx/baselines by default. A copy of
the aggregate is also written as latest.tsv for simple regression checks.

Options:
  --model ID                    Model id or local alias (required)
  --prompt TEXT                 Prompt to run (default: "where is jakarta")
  --max-tokens N                Max generated tokens per run (default: 8)
  --runs N                      Measured runs after warmup (default: 5)
  --warmup-runs N               Warmup runs before measured checks (default: 1)
  --aqli-bin PATH             Aqli executable (default: ~/.local/bin/aqli)
  --baseline-root DIR           Baseline root (default: ops/benchmarks/onnx/baselines)
  --name NAME                   Baseline model directory name (default: sanitized model id)
  --run-label NAME              Baseline run directory name (default: timestamp)
  --aggregate-label NAME        Aggregate row prefix (default: measured)
  --include-warmup-aggregate    Include warmup rows in aggregate
  --require-profile             Require ONNX profile lines (default)
  --no-require-profile          Allow benchmark rows without ONNX profile lines
  --expect-backend NAME         Require each benchmark run to report this ONNX backend
  --allow-mixed-backend         Allow blank or mixed measured backend in baseline
  --max-noise-percent N         Reject baseline when best/worst spread exceeds N percent
  --noise-metrics CSV           Metrics checked by the noise gate
  --no-update-latest            Do not copy aggregate.tsv to latest.tsv
  --bench-script PATH           Override bench script
  --summarize-script PATH       Override summary aggregation script
  --noise-script PATH           Override noise stability checker
  --env-script PATH             Override environment fingerprint helper
  --bundle-script PATH          Override artifact bundle helper
  --diagnose-script PATH        Override profile diagnosis helper
  --decision-script PATH        Override decision summary helper
  --help                        Show this help

Examples:
  ./aqli/scripts/capture-onnx-profile-baseline.sh --model 6b6e13 --runs 5
  ./aqli/scripts/verify-onnx-profile-regression.sh --model 6b6e13 --baseline ops/benchmarks/onnx/baselines/6b6e13/latest.tsv
USAGE
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_ID=""
PROMPT="${AQLI_VERIFY_ONNX_PROMPT:-where is jakarta}"
MAX_TOKENS="${AQLI_VERIFY_ONNX_MAX_TOKENS:-8}"
RUNS="${AQLI_CAPTURE_ONNX_RUNS:-5}"
WARMUP_RUNS="${AQLI_CAPTURE_ONNX_WARMUP_RUNS:-1}"
AQLI_BIN="${HOME}/.local/bin/aqli"
BASELINE_ROOT="ops/benchmarks/onnx/baselines"
BASELINE_NAME=""
RUN_LABEL=""
AGGREGATE_LABEL="${AQLI_ONNX_PROFILE_AGGREGATE_LABEL:-measured}"
INCLUDE_WARMUP_AGGREGATE=0
REQUIRE_PROFILE=1
EXPECT_BACKEND="${AQLI_CAPTURE_ONNX_EXPECT_BACKEND:-${AQLI_ONNX_EXPECT_BACKEND:-}}"
ALLOW_MIXED_BACKEND=0
MAX_NOISE_PERCENT="${AQLI_CAPTURE_ONNX_MAX_NOISE_PERCENT:-}"
NOISE_METRICS="${AQLI_CAPTURE_ONNX_NOISE_METRICS:-${AQLI_ONNX_PROFILE_NOISE_METRICS:-}}"
UPDATE_LATEST=1
BENCH_SCRIPT="${ROOT_DIR}/scripts/bench-onnx-profile.sh"
SUMMARIZE_SCRIPT="${ROOT_DIR}/scripts/summarize-onnx-profile-summary.sh"
NOISE_SCRIPT="${ROOT_DIR}/scripts/check-onnx-profile-noise.sh"
ENV_SCRIPT="${ROOT_DIR}/scripts/onnx-profile-env.sh"
BUNDLE_SCRIPT="${ROOT_DIR}/scripts/onnx-performance-bundle.sh"
DIAGNOSE_SCRIPT="${ROOT_DIR}/scripts/diagnose-onnx-profile-summary.sh"
DECISION_SCRIPT="${ROOT_DIR}/scripts/onnx-performance-decision.sh"

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
    --baseline-root) BASELINE_ROOT="$2"; shift 2 ;;
    --baseline-root=*) BASELINE_ROOT="${1#*=}"; shift ;;
    --name) BASELINE_NAME="$2"; shift 2 ;;
    --name=*) BASELINE_NAME="${1#*=}"; shift ;;
    --run-label) RUN_LABEL="$2"; shift 2 ;;
    --run-label=*) RUN_LABEL="${1#*=}"; shift ;;
    --aggregate-label) AGGREGATE_LABEL="$2"; shift 2 ;;
    --aggregate-label=*) AGGREGATE_LABEL="${1#*=}"; shift ;;
    --include-warmup-aggregate) INCLUDE_WARMUP_AGGREGATE=1; shift ;;
    --require-profile) REQUIRE_PROFILE=1; shift ;;
    --no-require-profile) REQUIRE_PROFILE=0; shift ;;
    --expect-backend) EXPECT_BACKEND="$2"; shift 2 ;;
    --expect-backend=*) EXPECT_BACKEND="${1#*=}"; shift ;;
    --allow-mixed-backend) ALLOW_MIXED_BACKEND=1; shift ;;
    --max-noise-percent) MAX_NOISE_PERCENT="$2"; shift 2 ;;
    --max-noise-percent=*) MAX_NOISE_PERCENT="${1#*=}"; shift ;;
    --noise-metrics) NOISE_METRICS="$2"; shift 2 ;;
    --noise-metrics=*) NOISE_METRICS="${1#*=}"; shift ;;
    --no-update-latest) UPDATE_LATEST=0; shift ;;
    --bench-script) BENCH_SCRIPT="$2"; shift 2 ;;
    --bench-script=*) BENCH_SCRIPT="${1#*=}"; shift ;;
    --summarize-script) SUMMARIZE_SCRIPT="$2"; shift 2 ;;
    --summarize-script=*) SUMMARIZE_SCRIPT="${1#*=}"; shift ;;
    --noise-script) NOISE_SCRIPT="$2"; shift 2 ;;
    --noise-script=*) NOISE_SCRIPT="${1#*=}"; shift ;;
    --env-script) ENV_SCRIPT="$2"; shift 2 ;;
    --env-script=*) ENV_SCRIPT="${1#*=}"; shift ;;
    --bundle-script) BUNDLE_SCRIPT="$2"; shift 2 ;;
    --bundle-script=*) BUNDLE_SCRIPT="${1#*=}"; shift ;;
    --diagnose-script) DIAGNOSE_SCRIPT="$2"; shift 2 ;;
    --diagnose-script=*) DIAGNOSE_SCRIPT="${1#*=}"; shift ;;
    --decision-script) DECISION_SCRIPT="$2"; shift 2 ;;
    --decision-script=*) DECISION_SCRIPT="${1#*=}"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$MODEL_ID" ]]; then
  echo "--model is required" >&2
  usage
  exit 2
fi
for script in "$BENCH_SCRIPT" "$SUMMARIZE_SCRIPT"; do
  if [[ ! -x "$script" ]]; then
    echo "Required script not found or not executable: $script" >&2
    exit 2
  fi
done
if [[ -n "$MAX_NOISE_PERCENT" && ! -x "$NOISE_SCRIPT" ]]; then
  echo "Required noise checker not found or not executable: $NOISE_SCRIPT" >&2
  exit 2
fi
if [[ ! -f "$ENV_SCRIPT" ]]; then
  echo "Required environment helper not found: $ENV_SCRIPT" >&2
  exit 2
fi
if [[ ! -f "$BUNDLE_SCRIPT" ]]; then
  echo "Required artifact bundle helper not found: $BUNDLE_SCRIPT" >&2
  exit 2
fi
if [[ ! -x "$DIAGNOSE_SCRIPT" ]]; then
  echo "Required profile diagnosis helper not found or not executable: $DIAGNOSE_SCRIPT" >&2
  exit 2
fi
if [[ ! -x "$DECISION_SCRIPT" ]]; then
  echo "Required decision summary helper not found or not executable: $DECISION_SCRIPT" >&2
  exit 2
fi
if [[ ! -x "$AQLI_BIN" ]]; then
  echo "aqli executable not found or not executable: $AQLI_BIN" >&2
  exit 2
fi
for value_name in MAX_TOKENS RUNS WARMUP_RUNS; do
  value="${!value_name}"
  if [[ ! "$value" =~ ^[0-9]+$ ]] || { [[ "$value_name" != "WARMUP_RUNS" ]] && (( value < 1 )); }; then
    echo "Invalid ${value_name} value: $value" >&2
    exit 2
  fi
done
if [[ -z "$AGGREGATE_LABEL" ]]; then
  echo "--aggregate-label must not be empty" >&2
  exit 2
fi
if [[ -n "$MAX_NOISE_PERCENT" && ! "$MAX_NOISE_PERCENT" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "Invalid --max-noise-percent value: $MAX_NOISE_PERCENT" >&2
  exit 2
fi
for cmd in awk cp date mkdir tee tr; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

# shellcheck source=/dev/null
source "$ENV_SCRIPT"
# shellcheck source=/dev/null
source "$BUNDLE_SCRIPT"

slugify() {
  local value="$1"
  local slug
  slug="$(printf '%s' "$value" | tr '/:[:space:]' '___' | tr -cd 'A-Za-z0-9._-')"
  if [[ -z "$slug" ]]; then
    slug="model"
  fi
  printf '%s' "$slug"
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
RAW_SUMMARY="${BENCH_OUT_DIR}/${BENCH_LABEL}/summary.tsv"
SUMMARY_STAGE_DIR="${BASELINE_DIR}/summary"
AGGREGATE="${BASELINE_DIR}/aggregate.tsv"
LATEST="${BASELINE_PARENT}/latest.tsv"
LATEST_MANIFEST="${BASELINE_PARENT}/latest-manifest.tsv"
LATEST_ENVIRONMENT="${BASELINE_PARENT}/latest-environment.tsv"
CONFIG="${BASELINE_DIR}/config.tsv"
RESULTS="${BASELINE_DIR}/results.tsv"
MANIFEST="${BASELINE_DIR}/manifest.tsv"
ENVIRONMENT="${BASELINE_DIR}/environment.tsv"
BUNDLE="${BASELINE_DIR}/bundle.tsv"
BUNDLE_JSON="${BASELINE_DIR}/bundle.json"
DIAGNOSIS_DIR="${BASELINE_DIR}/diagnosis"
DIAGNOSIS="${DIAGNOSIS_DIR}/diagnosis.tsv"
DIAGNOSIS_STAGES="${DIAGNOSIS_DIR}/stages.tsv"
DIAGNOSIS_REPORT="${DIAGNOSIS_DIR}/report.txt"
DECISION="${BASELINE_DIR}/decision.tsv"
REPORT="${BASELINE_DIR}/report.txt"
BENCH_STDOUT="${BASELINE_DIR}/bench.stdout.log"
BENCH_STDERR="${BASELINE_DIR}/bench.stderr.log"
SUMMARY_STDOUT="${BASELINE_DIR}/summary.stdout.log"
SUMMARY_STDERR="${BASELINE_DIR}/summary.stderr.log"
DIAGNOSIS_STDOUT="${BASELINE_DIR}/diagnosis.stdout.log"
DIAGNOSIS_STDERR="${BASELINE_DIR}/diagnosis.stderr.log"
NOISE="${BASELINE_DIR}/noise.tsv"
NOISE_STAGE_DIR="${BASELINE_DIR}/noise"
NOISE_STDOUT="${BASELINE_DIR}/noise.stdout.log"
NOISE_STDERR="${BASELINE_DIR}/noise.stderr.log"

safe_tsv_field() {
  local value="${1:-}"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

config_row() {
  printf '%s\t%s\n' "$(safe_tsv_field "$1")" "$(safe_tsv_field "${2:-}")" >> "$CONFIG"
}

record_result() {
  local stage="$1"
  local status="$2"
  local exit_code="$3"
  local artifact="$4"
  local stdout_log="$5"
  local stderr_log="$6"
  local reason="${7:-}"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$stage" "$status" "$exit_code" "$artifact" "$stdout_log" "$stderr_log" "$reason" >> "$RESULTS"
}

summary_value() {
  local file="$1"
  local case_name="$2"
  local column_name="$3"
  awk -v caseName="$case_name" -v columnName="$column_name" '
    BEGIN { FS = "\t" }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        column[$i] = i
      }
      next
    }
    $1 == caseName {
      if (column[columnName]) {
        print $column[columnName]
      }
      exit
    }
  ' "$file"
}

environment_value() {
  local file="$1"
  local key="$2"
  awk -v key="$key" '
    BEGIN { FS = "\t" }
    NR > 1 && $1 == key {
      value = $0
      sub(/^[^\t]*\t/, "", value)
      print value
      found = 1
      exit
    }
    END { exit found ? 0 : 1 }
  ' "$file"
}

mkdir -p "$BASELINE_DIR" "$BASELINE_PARENT"
aqli_onnx_profile_env_write "$ENVIRONMENT" "$AQLI_BIN"

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
config_row baselineRoot "$BASELINE_ROOT"
config_row baselineName "$BASELINE_NAME"
config_row runLabel "$RUN_LABEL"
config_row baselineDir "$BASELINE_DIR"
config_row rawSummary "$RAW_SUMMARY"
config_row aggregate "$AGGREGATE"
config_row latest "$LATEST"
config_row latestEnvironment "$LATEST_ENVIRONMENT"
config_row environment "$ENVIRONMENT"
config_row bundle "$BUNDLE"
config_row bundleJson "$BUNDLE_JSON"
config_row diagnosis "$DIAGNOSIS"
config_row diagnosisStages "$DIAGNOSIS_STAGES"
config_row diagnosisReport "$DIAGNOSIS_REPORT"
config_row decision "$DECISION"
config_row aggregateLabel "$AGGREGATE_LABEL"
config_row includeWarmupAggregate "$INCLUDE_WARMUP_AGGREGATE"
config_row requireProfile "$REQUIRE_PROFILE"
config_row expectBackend "$EXPECT_BACKEND"
config_row allowMixedBackend "$ALLOW_MIXED_BACKEND"
config_row maxNoisePercent "$MAX_NOISE_PERCENT"
config_row noiseMetrics "$NOISE_METRICS"
config_row updateLatest "$UPDATE_LATEST"
config_row benchScript "$BENCH_SCRIPT"
config_row summarizeScript "$SUMMARIZE_SCRIPT"
config_row noiseScript "$NOISE_SCRIPT"
config_row envScript "$ENV_SCRIPT"
config_row bundleScript "$BUNDLE_SCRIPT"
config_row diagnoseScript "$DIAGNOSE_SCRIPT"
config_row decisionScript "$DECISION_SCRIPT"

printf 'stage\tstatus\texitCode\tartifact\tstdout\tstderr\treason\n' > "$RESULTS"
record_result environment pass 0 "$ENVIRONMENT" "" ""
printf 'key\tvalue\n' > "$MANIFEST"

write_capture_bundle() {
  aqli_onnx_performance_bundle_init "$BUNDLE"
  aqli_onnx_performance_bundle_add "$BUNDLE" config "$CONFIG" required "Capture configuration"
  aqli_onnx_performance_bundle_add "$BUNDLE" results "$RESULTS" required "Stage results"
  aqli_onnx_performance_bundle_add "$BUNDLE" decision "$DECISION" required "Capture decision summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" manifest "$MANIFEST" required "Reusable baseline manifest"
  aqli_onnx_performance_bundle_add "$BUNDLE" environment "$ENVIRONMENT" required "Runtime and build fingerprint"
  aqli_onnx_performance_bundle_add "$BUNDLE" rawSummary "$RAW_SUMMARY" required "Raw benchmark summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" aggregate "$AGGREGATE" required "Aggregated baseline summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" diagnosis "$DIAGNOSIS" optional "Aggregate performance diagnosis"
  aqli_onnx_performance_bundle_add "$BUNDLE" diagnosisStages "$DIAGNOSIS_STAGES" optional "Diagnosed stage ranking"
  aqli_onnx_performance_bundle_add "$BUNDLE" diagnosisReport "$DIAGNOSIS_REPORT" optional "Human-readable diagnosis report"
  aqli_onnx_performance_bundle_add "$BUNDLE" benchStdout "$BENCH_STDOUT" optional "Benchmark stdout"
  aqli_onnx_performance_bundle_add "$BUNDLE" benchStderr "$BENCH_STDERR" optional "Benchmark stderr"
  aqli_onnx_performance_bundle_add "$BUNDLE" summaryStdout "$SUMMARY_STDOUT" optional "Aggregator stdout"
  aqli_onnx_performance_bundle_add "$BUNDLE" summaryStderr "$SUMMARY_STDERR" optional "Aggregator stderr"
  aqli_onnx_performance_bundle_add "$BUNDLE" diagnosisStdout "$DIAGNOSIS_STDOUT" optional "Diagnosis stdout"
  aqli_onnx_performance_bundle_add "$BUNDLE" diagnosisStderr "$DIAGNOSIS_STDERR" optional "Diagnosis stderr"
  aqli_onnx_performance_bundle_add "$BUNDLE" noise "$NOISE" optional "Noise stability summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" noiseStdout "$NOISE_STDOUT" optional "Noise checker stdout"
  aqli_onnx_performance_bundle_add "$BUNDLE" noiseStderr "$NOISE_STDERR" optional "Noise checker stderr"
  aqli_onnx_performance_bundle_add "$BUNDLE" latest "$LATEST" optional "Latest baseline pointer"
  aqli_onnx_performance_bundle_add "$BUNDLE" latestManifest "$LATEST_MANIFEST" optional "Latest manifest pointer"
  aqli_onnx_performance_bundle_add "$BUNDLE" latestEnvironment "$LATEST_ENVIRONMENT" optional "Latest environment pointer"
  aqli_onnx_performance_bundle_write_json "$BUNDLE" "$BUNDLE_JSON"
}

write_capture_decision() {
  "$DECISION_SCRIPT" \
    --config "$CONFIG" \
    --results "$RESULTS" \
    --out "$DECISION" \
    --noise "$NOISE" \
    --bundle "$BUNDLE" >/dev/null
}

write_capture_artifacts() {
  write_capture_decision
  write_capture_bundle
  write_capture_decision
}

run_capture_diagnosis() {
  declare -a diagnosis_args=(
    "$DIAGNOSE_SCRIPT"
    "--summary" "$AGGREGATE"
    "--summary-dir" "$DIAGNOSIS_DIR"
    "--out" "$DIAGNOSIS"
    "--stages-out" "$DIAGNOSIS_STAGES"
    "--case" "${AGGREGATE_LABEL}-mean"
  )
  if [[ "$INCLUDE_WARMUP_AGGREGATE" -eq 1 ]]; then
    diagnosis_args+=("--include-warmup")
  fi

  set +e
  "${diagnosis_args[@]}" >"$DIAGNOSIS_STDOUT" 2>"$DIAGNOSIS_STDERR"
  local diagnosis_exit=$?
  set -e
  if [[ "$diagnosis_exit" -ne 0 || ! -f "$DIAGNOSIS" ]]; then
    record_result diagnosis skip "$diagnosis_exit" "$DIAGNOSIS" "$DIAGNOSIS_STDOUT" "$DIAGNOSIS_STDERR" diagnosis-unavailable
    return 0
  fi
  record_result diagnosis pass 0 "$DIAGNOSIS" "$DIAGNOSIS_STDOUT" "$DIAGNOSIS_STDERR"
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

set +e
"${BENCH_ARGS[@]}" >"$BENCH_STDOUT" 2>"$BENCH_STDERR"
BENCH_EXIT=$?
set -e
if [[ "$BENCH_EXIT" -ne 0 || ! -f "$RAW_SUMMARY" ]]; then
  record_result benchmark fail "$BENCH_EXIT" "$RAW_SUMMARY" "$BENCH_STDOUT" "$BENCH_STDERR" benchmark-failed
  write_capture_artifacts
  {
    echo "ONNX profile baseline benchmark failed; artifacts are in $BASELINE_DIR"
    echo "baselineDir=$BASELINE_DIR"
    echo "artifacts.bundle=$BUNDLE"
    echo "artifacts.bundleJson=$BUNDLE_JSON"
    echo "artifacts.decision=$DECISION"
  } | tee "$REPORT" >&2
  exit "$([[ "$BENCH_EXIT" -eq 0 ]] && printf 3 || printf '%s' "$BENCH_EXIT")"
fi
record_result benchmark pass 0 "$RAW_SUMMARY" "$BENCH_STDOUT" "$BENCH_STDERR"

declare -a SUMMARY_ARGS=(
  "$SUMMARIZE_SCRIPT"
  "--summary" "$RAW_SUMMARY"
  "--summary-dir" "$SUMMARY_STAGE_DIR"
  "--out" "$AGGREGATE"
  "--label" "$AGGREGATE_LABEL"
)
if [[ "$INCLUDE_WARMUP_AGGREGATE" -eq 1 ]]; then
  SUMMARY_ARGS+=("--include-warmup")
fi

set +e
"${SUMMARY_ARGS[@]}" >"$SUMMARY_STDOUT" 2>"$SUMMARY_STDERR"
SUMMARY_EXIT=$?
set -e
if [[ "$SUMMARY_EXIT" -ne 0 || ! -f "$AGGREGATE" ]]; then
  record_result aggregate fail "$SUMMARY_EXIT" "$AGGREGATE" "$SUMMARY_STDOUT" "$SUMMARY_STDERR" aggregate-failed
  write_capture_artifacts
  {
    echo "ONNX profile baseline aggregation failed; artifacts are in $BASELINE_DIR"
    echo "baselineDir=$BASELINE_DIR"
    echo "artifacts.bundle=$BUNDLE"
    echo "artifacts.bundleJson=$BUNDLE_JSON"
    echo "artifacts.decision=$DECISION"
  } | tee "$REPORT" >&2
  exit "$([[ "$SUMMARY_EXIT" -eq 0 ]] && printf 3 || printf '%s' "$SUMMARY_EXIT")"
fi
record_result aggregate pass 0 "$AGGREGATE" "$SUMMARY_STDOUT" "$SUMMARY_STDERR"
run_capture_diagnosis
BASELINE_BACKEND="$(summary_value "$AGGREGATE" "${AGGREGATE_LABEL}-mean" onnxBackend)"
if [[ "$ALLOW_MIXED_BACKEND" -ne 1 && ( -z "$BASELINE_BACKEND" || "$BASELINE_BACKEND" == "mixed" ) ]]; then
  record_result baseline-backend fail 46 "$AGGREGATE" "$SUMMARY_STDOUT" "$SUMMARY_STDERR" mixed-or-missing-backend
  write_capture_artifacts
  {
    echo "ONNX profile baseline backend is not stable; artifacts are in $BASELINE_DIR"
    echo "baselineDir=$BASELINE_DIR"
    echo "artifacts.bundle=$BUNDLE"
    echo "artifacts.bundleJson=$BUNDLE_JSON"
    echo "artifacts.decision=$DECISION"
    echo "artifacts.config=$CONFIG"
    echo "artifacts.results=$RESULTS"
    echo "artifacts.rawSummary=$RAW_SUMMARY"
    echo "artifacts.aggregate=$AGGREGATE"
    echo "backend=${BASELINE_BACKEND:-missing}"
    echo
    echo "Use --expect-backend to require a specific backend, or --allow-mixed-backend for exploratory baselines."
  } | tee "$REPORT" >&2
  exit 46
fi
if [[ -z "$BASELINE_BACKEND" || "$BASELINE_BACKEND" == "mixed" ]]; then
  record_result baseline-backend skip 0 "$AGGREGATE" "$SUMMARY_STDOUT" "$SUMMARY_STDERR" mixed-or-missing-backend-allowed
else
  record_result baseline-backend pass 0 "$AGGREGATE" "$SUMMARY_STDOUT" "$SUMMARY_STDERR" "$BASELINE_BACKEND"
fi

if [[ -n "$MAX_NOISE_PERCENT" ]]; then
  declare -a NOISE_ARGS=(
    "$NOISE_SCRIPT"
    "--aggregate" "$AGGREGATE"
    "--summary-dir" "$NOISE_STAGE_DIR"
    "--out" "$NOISE"
    "--label" "$AGGREGATE_LABEL"
    "--max-noise-percent" "$MAX_NOISE_PERCENT"
  )
  if [[ -n "$NOISE_METRICS" ]]; then
    NOISE_ARGS+=("--metrics" "$NOISE_METRICS")
  fi

  set +e
  "${NOISE_ARGS[@]}" >"$NOISE_STDOUT" 2>"$NOISE_STDERR"
  NOISE_EXIT=$?
  set -e
  if [[ "$NOISE_EXIT" -ne 0 || ! -f "$NOISE" ]]; then
    record_result noise fail 47 "$NOISE" "$NOISE_STDOUT" "$NOISE_STDERR" noise-failed
    write_capture_artifacts
    {
      echo "ONNX profile baseline noise check failed; artifacts are in $BASELINE_DIR"
      echo "baselineDir=$BASELINE_DIR"
      echo "artifacts.bundle=$BUNDLE"
      echo "artifacts.bundleJson=$BUNDLE_JSON"
      echo "artifacts.decision=$DECISION"
      echo "artifacts.config=$CONFIG"
      echo "artifacts.results=$RESULTS"
      echo "artifacts.rawSummary=$RAW_SUMMARY"
      echo "artifacts.aggregate=$AGGREGATE"
      echo "artifacts.noise=$NOISE"
      echo "artifacts.noiseStdout=$NOISE_STDOUT"
      echo "artifacts.noiseStderr=$NOISE_STDERR"
      echo "maxNoisePercent=$MAX_NOISE_PERCENT"
    } | tee "$REPORT" >&2
    exit 47
  fi
  record_result noise pass 0 "$NOISE" "$NOISE_STDOUT" "$NOISE_STDERR"
fi

REGRESSION_BASELINE="$AGGREGATE"
if [[ "$UPDATE_LATEST" -eq 1 ]]; then
  cp "$AGGREGATE" "$LATEST"
  cp "$ENVIRONMENT" "$LATEST_ENVIRONMENT"
  record_result latest pass 0 "$LATEST" "" ""
  record_result latest-environment pass 0 "$LATEST_ENVIRONMENT" "" ""
  REGRESSION_BASELINE="$LATEST"
fi

{
  printf 'key\tvalue\n'
  printf 'model\t%s\n' "$(safe_tsv_field "$MODEL_ID")"
  printf 'prompt\t%s\n' "$(safe_tsv_field "$PROMPT")"
  printf 'maxTokens\t%s\n' "$(safe_tsv_field "$MAX_TOKENS")"
  printf 'runs\t%s\n' "$(safe_tsv_field "$RUNS")"
  printf 'warmupRuns\t%s\n' "$(safe_tsv_field "$WARMUP_RUNS")"
  printf 'baselineName\t%s\n' "$(safe_tsv_field "$BASELINE_NAME")"
  printf 'runLabel\t%s\n' "$(safe_tsv_field "$RUN_LABEL")"
  printf 'baselineDir\t%s\n' "$(safe_tsv_field "$BASELINE_DIR")"
  printf 'rawSummary\t%s\n' "$(safe_tsv_field "$RAW_SUMMARY")"
  printf 'aggregate\t%s\n' "$(safe_tsv_field "$AGGREGATE")"
  if [[ -f "$DIAGNOSIS" ]]; then
    printf 'diagnosis\t%s\n' "$(safe_tsv_field "$DIAGNOSIS")"
    printf 'diagnosisStages\t%s\n' "$(safe_tsv_field "$DIAGNOSIS_STAGES")"
    printf 'diagnosisReport\t%s\n' "$(safe_tsv_field "$DIAGNOSIS_REPORT")"
  fi
  printf 'environment\t%s\n' "$(safe_tsv_field "$ENVIRONMENT")"
  printf 'aqliVersion\t%s\n' "$(safe_tsv_field "$(environment_value "$ENVIRONMENT" aqliVersion || true)")"
  printf 'gitCommit\t%s\n' "$(safe_tsv_field "$(environment_value "$ENVIRONMENT" gitCommit || true)")"
  printf 'gitDirty\t%s\n' "$(safe_tsv_field "$(environment_value "$ENVIRONMENT" gitDirty || true)")"
  printf 'aggregateLabel\t%s\n' "$(safe_tsv_field "$AGGREGATE_LABEL")"
  printf 'onnxBackend\t%s\n' "$(safe_tsv_field "$BASELINE_BACKEND")"
  printf 'expectBackend\t%s\n' "$(safe_tsv_field "$EXPECT_BACKEND")"
  printf 'allowMixedBackend\t%s\n' "$(safe_tsv_field "$ALLOW_MIXED_BACKEND")"
  printf 'maxNoisePercent\t%s\n' "$(safe_tsv_field "$MAX_NOISE_PERCENT")"
  printf 'noiseMetrics\t%s\n' "$(safe_tsv_field "$NOISE_METRICS")"
  if [[ -f "$NOISE" ]]; then
    printf 'noise\t%s\n' "$(safe_tsv_field "$NOISE")"
  fi
  printf 'includeWarmupAggregate\t%s\n' "$(safe_tsv_field "$INCLUDE_WARMUP_AGGREGATE")"
  printf 'requireProfile\t%s\n' "$(safe_tsv_field "$REQUIRE_PROFILE")"
  if [[ "$UPDATE_LATEST" -eq 1 ]]; then
    printf 'latest\t%s\n' "$(safe_tsv_field "$LATEST")"
    printf 'latestEnvironment\t%s\n' "$(safe_tsv_field "$LATEST_ENVIRONMENT")"
  fi
  printf 'config\t%s\n' "$(safe_tsv_field "$CONFIG")"
  printf 'results\t%s\n' "$(safe_tsv_field "$RESULTS")"
  printf 'bundle\t%s\n' "$(safe_tsv_field "$BUNDLE")"
  printf 'bundleJson\t%s\n' "$(safe_tsv_field "$BUNDLE_JSON")"
  printf 'decision\t%s\n' "$(safe_tsv_field "$DECISION")"
} > "$MANIFEST"
if [[ "$UPDATE_LATEST" -eq 1 ]]; then
  cp "$MANIFEST" "$LATEST_MANIFEST"
fi
write_capture_artifacts

{
  echo "Aqli ONNX profile baseline capture"
  echo "baselineDir=$BASELINE_DIR"
  echo "artifacts.config=$CONFIG"
  echo "artifacts.results=$RESULTS"
  echo "artifacts.manifest=$MANIFEST"
  echo "artifacts.environment=$ENVIRONMENT"
  echo "artifacts.bundle=$BUNDLE"
  echo "artifacts.bundleJson=$BUNDLE_JSON"
  echo "artifacts.decision=$DECISION"
  echo "artifacts.rawSummary=$RAW_SUMMARY"
  echo "artifacts.aggregate=$AGGREGATE"
  if [[ -f "$DIAGNOSIS" ]]; then
    echo "artifacts.diagnosis=$DIAGNOSIS"
    echo "artifacts.diagnosisStages=$DIAGNOSIS_STAGES"
    echo "artifacts.diagnosisReport=$DIAGNOSIS_REPORT"
  fi
  if [[ -f "$NOISE" ]]; then
    echo "artifacts.noise=$NOISE"
  fi
  if [[ "$UPDATE_LATEST" -eq 1 ]]; then
    echo "artifacts.latest=$LATEST"
    echo "artifacts.latestManifest=$LATEST_MANIFEST"
    echo "artifacts.latestEnvironment=$LATEST_ENVIRONMENT"
  fi
  echo
  echo "Regression gate:"
  echo "  ./scripts/verify-onnx-profile-regression.sh --model ${MODEL_ID} --baseline ${REGRESSION_BASELINE}"
} | tee "$REPORT"
