#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: verify-onnx-profile-regression.sh --model MODEL --baseline SUMMARY [options]

Run an ONNX profile benchmark, aggregate its measured runs, and compare the
aggregate against a baseline summary. If the baseline is a raw benchmark
summary, it is aggregated first; if it already has measured-mean/best/worst
rows, it is used directly.

Options:
  --model ID                    Model id or local alias (required)
  --baseline PATH               Baseline raw or aggregate summary.tsv (required)
  --baseline-manifest PATH      Baseline manifest.tsv/latest-manifest.tsv
  --prompt TEXT                 Prompt to run (default: "where is jakarta")
  --max-tokens N                Max generated tokens per run (default: 8)
  --runs N                      Measured runs after warmup (default: 3)
  --warmup-runs N               Warmup runs before measured checks (default: 1)
  --aqli-bin PATH             Aqli executable (default: ~/.local/bin/aqli)
  --summary-dir DIR             Output directory for artifacts (default: temp dir)
  --max-regression-percent N    Allowed regression percent (default: 10)
  --max-noise-percent N         Reject current benchmark when best/worst spread exceeds N percent
  --metrics CSV                 Metrics to compare (default: comparator default)
  --noise-metrics CSV           Metrics checked by the noise gate
  --aggregate-label NAME        Aggregate row prefix (default: measured)
  --include-warmup-aggregate    Include warmup rows in current/baseline aggregation
  --require-profile             Require ONNX profile lines (default)
  --no-require-profile          Allow benchmark rows without ONNX profile lines
  --expect-backend NAME         Require current benchmark runs to report this ONNX backend
                                Defaults to baseline manifest/backend summary when available
  --fail-missing-metric         Fail when selected metrics are blank/missing
  --skip-baseline-metadata-check
                                Do not validate baseline manifest compatibility
  --skip-backend-check          Do not require current/baseline ONNX backends to match
  --strict-environment-check    Fail when baseline/current environment fingerprints differ
  --bench-script PATH           Override bench script
  --summarize-script PATH       Override summary aggregation script
  --compare-script PATH         Override comparison script
  --noise-script PATH           Override noise stability checker
  --env-script PATH             Override environment fingerprint helper
  --bundle-script PATH          Override artifact bundle helper
  --diagnose-script PATH        Override profile diagnosis helper
  --diagnosis-compare-script PATH
                                Override diagnosis stage comparison helper
  --decision-script PATH        Override decision summary helper
  --help                        Show this help

Examples:
  ./aqli/scripts/verify-onnx-profile-regression.sh \
    --model 6b6e13 \
    --baseline ops/benchmarks/onnx/baseline/aggregate.tsv \
    --max-regression-percent 15
USAGE
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_ID=""
BASELINE=""
BASELINE_MANIFEST=""
PROMPT="${AQLI_VERIFY_ONNX_PROMPT:-where is jakarta}"
MAX_TOKENS="${AQLI_VERIFY_ONNX_MAX_TOKENS:-8}"
RUNS="${AQLI_VERIFY_ONNX_RUNS:-3}"
WARMUP_RUNS="${AQLI_VERIFY_ONNX_WARMUP_RUNS:-1}"
AQLI_BIN="${HOME}/.local/bin/aqli"
SUMMARY_DIR=""
MAX_REGRESSION_PERCENT="${AQLI_ONNX_PROFILE_MAX_REGRESSION_PERCENT:-10}"
MAX_NOISE_PERCENT="${AQLI_VERIFY_ONNX_MAX_NOISE_PERCENT:-${AQLI_ONNX_PROFILE_MAX_NOISE_PERCENT:-}}"
METRICS="${AQLI_ONNX_PROFILE_COMPARE_METRICS:-}"
NOISE_METRICS="${AQLI_VERIFY_ONNX_NOISE_METRICS:-${AQLI_ONNX_PROFILE_NOISE_METRICS:-}}"
AGGREGATE_LABEL="${AQLI_ONNX_PROFILE_AGGREGATE_LABEL:-measured}"
INCLUDE_WARMUP_AGGREGATE=0
REQUIRE_PROFILE=1
EXPECT_BACKEND="${AQLI_VERIFY_ONNX_EXPECT_BACKEND:-${AQLI_ONNX_EXPECT_BACKEND:-}}"
FAIL_MISSING_METRIC=0
CHECK_BASELINE_METADATA=1
CHECK_BACKEND=1
STRICT_ENVIRONMENT_CHECK=0
BENCH_SCRIPT="${ROOT_DIR}/scripts/bench-onnx-profile.sh"
SUMMARIZE_SCRIPT="${ROOT_DIR}/scripts/summarize-onnx-profile-summary.sh"
COMPARE_SCRIPT="${ROOT_DIR}/scripts/compare-onnx-profile-summary.sh"
NOISE_SCRIPT="${ROOT_DIR}/scripts/check-onnx-profile-noise.sh"
ENV_SCRIPT="${ROOT_DIR}/scripts/onnx-profile-env.sh"
BUNDLE_SCRIPT="${ROOT_DIR}/scripts/onnx-performance-bundle.sh"
DIAGNOSE_SCRIPT="${ROOT_DIR}/scripts/diagnose-onnx-profile-summary.sh"
DIAGNOSIS_COMPARE_SCRIPT="${ROOT_DIR}/scripts/compare-onnx-profile-diagnosis.sh"
DECISION_SCRIPT="${ROOT_DIR}/scripts/onnx-performance-decision.sh"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model) MODEL_ID="$2"; shift 2 ;;
    --model=*) MODEL_ID="${1#*=}"; shift ;;
    --baseline) BASELINE="$2"; shift 2 ;;
    --baseline=*) BASELINE="${1#*=}"; shift ;;
    --baseline-manifest) BASELINE_MANIFEST="$2"; shift 2 ;;
    --baseline-manifest=*) BASELINE_MANIFEST="${1#*=}"; shift ;;
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
    --summary-dir) SUMMARY_DIR="$2"; shift 2 ;;
    --summary-dir=*) SUMMARY_DIR="${1#*=}"; shift ;;
    --max-regression-percent) MAX_REGRESSION_PERCENT="$2"; shift 2 ;;
    --max-regression-percent=*) MAX_REGRESSION_PERCENT="${1#*=}"; shift ;;
    --max-noise-percent) MAX_NOISE_PERCENT="$2"; shift 2 ;;
    --max-noise-percent=*) MAX_NOISE_PERCENT="${1#*=}"; shift ;;
    --metrics) METRICS="$2"; shift 2 ;;
    --metrics=*) METRICS="${1#*=}"; shift ;;
    --noise-metrics) NOISE_METRICS="$2"; shift 2 ;;
    --noise-metrics=*) NOISE_METRICS="${1#*=}"; shift ;;
    --aggregate-label) AGGREGATE_LABEL="$2"; shift 2 ;;
    --aggregate-label=*) AGGREGATE_LABEL="${1#*=}"; shift ;;
    --include-warmup-aggregate) INCLUDE_WARMUP_AGGREGATE=1; shift ;;
    --require-profile) REQUIRE_PROFILE=1; shift ;;
    --no-require-profile) REQUIRE_PROFILE=0; shift ;;
    --expect-backend) EXPECT_BACKEND="$2"; shift 2 ;;
    --expect-backend=*) EXPECT_BACKEND="${1#*=}"; shift ;;
    --fail-missing-metric) FAIL_MISSING_METRIC=1; shift ;;
    --skip-baseline-metadata-check) CHECK_BASELINE_METADATA=0; shift ;;
    --skip-backend-check) CHECK_BACKEND=0; shift ;;
    --strict-environment-check) STRICT_ENVIRONMENT_CHECK=1; shift ;;
    --bench-script) BENCH_SCRIPT="$2"; shift 2 ;;
    --bench-script=*) BENCH_SCRIPT="${1#*=}"; shift ;;
    --summarize-script) SUMMARIZE_SCRIPT="$2"; shift 2 ;;
    --summarize-script=*) SUMMARIZE_SCRIPT="${1#*=}"; shift ;;
    --compare-script) COMPARE_SCRIPT="$2"; shift 2 ;;
    --compare-script=*) COMPARE_SCRIPT="${1#*=}"; shift ;;
    --noise-script) NOISE_SCRIPT="$2"; shift 2 ;;
    --noise-script=*) NOISE_SCRIPT="${1#*=}"; shift ;;
    --env-script) ENV_SCRIPT="$2"; shift 2 ;;
    --env-script=*) ENV_SCRIPT="${1#*=}"; shift ;;
    --bundle-script) BUNDLE_SCRIPT="$2"; shift 2 ;;
    --bundle-script=*) BUNDLE_SCRIPT="${1#*=}"; shift ;;
    --diagnose-script) DIAGNOSE_SCRIPT="$2"; shift 2 ;;
    --diagnose-script=*) DIAGNOSE_SCRIPT="${1#*=}"; shift ;;
    --diagnosis-compare-script) DIAGNOSIS_COMPARE_SCRIPT="$2"; shift 2 ;;
    --diagnosis-compare-script=*) DIAGNOSIS_COMPARE_SCRIPT="${1#*=}"; shift ;;
    --decision-script) DECISION_SCRIPT="$2"; shift 2 ;;
    --decision-script=*) DECISION_SCRIPT="${1#*=}"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$MODEL_ID" || -z "$BASELINE" ]]; then
  echo "--model and --baseline are required" >&2
  usage
  exit 2
fi
if [[ ! -f "$BASELINE" ]]; then
  echo "Baseline summary not found: $BASELINE" >&2
  exit 2
fi
for script in "$BENCH_SCRIPT" "$SUMMARIZE_SCRIPT" "$COMPARE_SCRIPT"; do
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
if [[ ! -x "$DIAGNOSIS_COMPARE_SCRIPT" ]]; then
  echo "Required diagnosis comparison helper not found or not executable: $DIAGNOSIS_COMPARE_SCRIPT" >&2
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
if [[ ! "$MAX_REGRESSION_PERCENT" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "Invalid --max-regression-percent value: $MAX_REGRESSION_PERCENT" >&2
  exit 2
fi
if [[ -n "$MAX_NOISE_PERCENT" && ! "$MAX_NOISE_PERCENT" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "Invalid --max-noise-percent value: $MAX_NOISE_PERCENT" >&2
  exit 2
fi
if [[ -z "$AGGREGATE_LABEL" ]]; then
  echo "--aggregate-label must not be empty" >&2
  exit 2
fi
for cmd in awk cp date mkdir mktemp tee; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

# shellcheck source=/dev/null
source "$ENV_SCRIPT"
# shellcheck source=/dev/null
source "$BUNDLE_SCRIPT"

if [[ -z "$SUMMARY_DIR" ]]; then
  SUMMARY_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-onnx-profile-regression.XXXXXX")"
else
  mkdir -p "$SUMMARY_DIR"
fi

CONFIG="${SUMMARY_DIR}/config.tsv"
RESULTS="${SUMMARY_DIR}/results.tsv"
REPORT="${SUMMARY_DIR}/report.txt"
BUNDLE="${SUMMARY_DIR}/bundle.tsv"
BUNDLE_JSON="${SUMMARY_DIR}/bundle.json"
DECISION="${SUMMARY_DIR}/decision.tsv"
BASELINE_METADATA="${SUMMARY_DIR}/baseline-metadata.tsv"
BACKEND_METADATA="${SUMMARY_DIR}/backend-metadata.tsv"
ENVIRONMENT="${SUMMARY_DIR}/environment.tsv"
ENVIRONMENT_METADATA="${SUMMARY_DIR}/environment-metadata.tsv"
BASELINE_ENVIRONMENT="${SUMMARY_DIR}/baseline-environment.tsv"
BENCH_OUT_DIR="${SUMMARY_DIR}/bench"
BENCH_LABEL="current"
CURRENT_RAW="${BENCH_OUT_DIR}/${BENCH_LABEL}/summary.tsv"
CURRENT_AGGREGATE="${SUMMARY_DIR}/current-aggregate.tsv"
BASELINE_AGGREGATE="${SUMMARY_DIR}/baseline-aggregate.tsv"
COMPARE_DIR="${SUMMARY_DIR}/compare"
CURRENT_SUMMARY_DIR="${SUMMARY_DIR}/current-summary"
BASELINE_SUMMARY_DIR="${SUMMARY_DIR}/baseline-summary"
CURRENT_DIAGNOSIS_DIR="${SUMMARY_DIR}/current-diagnosis"
CURRENT_DIAGNOSIS="${CURRENT_DIAGNOSIS_DIR}/diagnosis.tsv"
CURRENT_DIAGNOSIS_STAGES="${CURRENT_DIAGNOSIS_DIR}/stages.tsv"
CURRENT_DIAGNOSIS_REPORT="${CURRENT_DIAGNOSIS_DIR}/report.txt"
BASELINE_DIAGNOSIS_DIR="${SUMMARY_DIR}/baseline-diagnosis"
BASELINE_DIAGNOSIS="${BASELINE_DIAGNOSIS_DIR}/diagnosis.tsv"
BASELINE_DIAGNOSIS_STAGES="${BASELINE_DIAGNOSIS_DIR}/stages.tsv"
BASELINE_DIAGNOSIS_REPORT="${BASELINE_DIAGNOSIS_DIR}/report.txt"
DIAGNOSIS_DIFF_DIR="${SUMMARY_DIR}/diagnosis-diff"
DIAGNOSIS_DIFF="${DIAGNOSIS_DIFF_DIR}/comparison.tsv"
DIAGNOSIS_DIFF_SUMMARY="${DIAGNOSIS_DIFF_DIR}/summary.tsv"
DIAGNOSIS_DIFF_TABLE="${DIAGNOSIS_DIFF_DIR}/comparison-table.txt"
DIAGNOSIS_DIFF_REPORT="${DIAGNOSIS_DIFF_DIR}/report.txt"
BENCH_STDOUT="${SUMMARY_DIR}/bench.stdout.log"
BENCH_STDERR="${SUMMARY_DIR}/bench.stderr.log"
CURRENT_SUMMARY_STDOUT="${SUMMARY_DIR}/current-summary.stdout.log"
CURRENT_SUMMARY_STDERR="${SUMMARY_DIR}/current-summary.stderr.log"
BASELINE_SUMMARY_STDOUT="${SUMMARY_DIR}/baseline-summary.stdout.log"
BASELINE_SUMMARY_STDERR="${SUMMARY_DIR}/baseline-summary.stderr.log"
CURRENT_DIAGNOSIS_STDOUT="${SUMMARY_DIR}/current-diagnosis.stdout.log"
CURRENT_DIAGNOSIS_STDERR="${SUMMARY_DIR}/current-diagnosis.stderr.log"
BASELINE_DIAGNOSIS_STDOUT="${SUMMARY_DIR}/baseline-diagnosis.stdout.log"
BASELINE_DIAGNOSIS_STDERR="${SUMMARY_DIR}/baseline-diagnosis.stderr.log"
DIAGNOSIS_DIFF_STDOUT="${SUMMARY_DIR}/diagnosis-diff.stdout.log"
DIAGNOSIS_DIFF_STDERR="${SUMMARY_DIR}/diagnosis-diff.stderr.log"
COMPARE_STDOUT="${SUMMARY_DIR}/compare.stdout.log"
COMPARE_STDERR="${SUMMARY_DIR}/compare.stderr.log"
NOISE="${SUMMARY_DIR}/current-noise.tsv"
NOISE_DIR="${SUMMARY_DIR}/current-noise"
NOISE_STDOUT="${SUMMARY_DIR}/current-noise.stdout.log"
NOISE_STDERR="${SUMMARY_DIR}/current-noise.stderr.log"

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

resolve_manifest_path() {
  local manifest="$1"
  local candidate="$2"
  if [[ -z "$candidate" ]]; then
    printf ''
    return 0
  fi
  if [[ -f "$candidate" ]]; then
    printf '%s' "$candidate"
    return 0
  fi
  local manifest_dir
  manifest_dir="${manifest%/*}"
  if [[ "$manifest_dir" == "$manifest" ]]; then
    manifest_dir="."
  fi
  if [[ -f "${manifest_dir}/${candidate}" ]]; then
    printf '%s' "${manifest_dir}/${candidate}"
    return 0
  fi
  printf '%s' "$candidate"
}

infer_baseline_manifest() {
  if [[ -n "$BASELINE_MANIFEST" ]]; then
    printf '%s' "$BASELINE_MANIFEST"
    return 0
  fi
  local baseline_dir baseline_file
  baseline_dir="${BASELINE%/*}"
  baseline_file="${BASELINE##*/}"
  if [[ "$baseline_dir" == "$BASELINE" ]]; then
    baseline_dir="."
  fi
  if [[ "$baseline_file" == "latest.tsv" && -f "${baseline_dir}/latest-manifest.tsv" ]]; then
    printf '%s' "${baseline_dir}/latest-manifest.tsv"
    return 0
  fi
  if [[ "$baseline_file" == "aggregate.tsv" && -f "${baseline_dir}/manifest.tsv" ]]; then
    printf '%s' "${baseline_dir}/manifest.tsv"
    return 0
  fi
  printf ''
}

manifest_value() {
  local manifest="$1"
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
  ' "$manifest"
}

infer_expect_backend() {
  local manifest="$1"
  local candidate=""
  if [[ -n "$manifest" && -f "$manifest" ]] \
      && candidate="$(manifest_value "$manifest" expectBackend)" \
      && [[ -n "$candidate" ]]; then
    printf '%s' "$candidate"
    return 0
  fi
  if [[ -n "$manifest" && -f "$manifest" ]] \
      && candidate="$(manifest_value "$manifest" onnxBackend)" \
      && [[ -n "$candidate" ]]; then
    printf '%s' "$candidate"
    return 0
  fi
  if candidate="$(infer_summary_backend "$BASELINE" "$AGGREGATE_LABEL" "$INCLUDE_WARMUP_AGGREGATE")" \
      && [[ -n "$candidate" ]]; then
    printf '%s' "$candidate"
    return 0
  fi
  printf ''
}

expect_backend_source() {
  local manifest="$1"
  local configured="$2"
  local candidate=""
  if [[ -n "$configured" ]]; then
    printf 'configured'
    return 0
  fi
  if [[ -n "$manifest" && -f "$manifest" ]] \
      && candidate="$(manifest_value "$manifest" expectBackend)" \
      && [[ -n "$candidate" ]]; then
    printf 'baseline-manifest:expectBackend'
    return 0
  fi
  if [[ -n "$manifest" && -f "$manifest" ]] \
      && candidate="$(manifest_value "$manifest" onnxBackend)" \
      && [[ -n "$candidate" ]]; then
    printf 'baseline-manifest:onnxBackend'
    return 0
  fi
  if candidate="$(infer_summary_backend "$BASELINE" "$AGGREGATE_LABEL" "$INCLUDE_WARMUP_AGGREGATE")" \
      && [[ -n "$candidate" ]]; then
    printf 'baseline-summary:onnxBackend'
    return 0
  fi
  printf 'none'
}

infer_summary_backend() {
  local summary="$1"
  local label="$2"
  local include_warmup="$3"
  if [[ -z "$summary" || ! -f "$summary" ]]; then
    printf ''
    return 0
  fi
  awk -v label="$label" -v includeWarmup="$include_warmup" '
    BEGIN { FS = "\t"; aggregateCase = label "-mean" }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        column[$i] = i
      }
      next
    }
    NF > 0 && column["onnxBackend"] {
      caseName = $column["case"]
      backendValue = $column["onnxBackend"]
      if (backendValue == "" || backendValue == "mixed") {
        next
      }
      if (caseName == aggregateCase) {
        print backendValue
        foundAggregate = 1
        exit
      }
      if (includeWarmup != "1" && caseName ~ /^warmup-/) {
        next
      }
      if (caseName ~ /-(mean|best|worst)$/) {
        next
      }
      if (backend == "") {
        backend = backendValue
      } else if (backend != backendValue) {
        mixed = 1
      }
    }
    END {
      if (!foundAggregate && backend != "" && !mixed) {
        print backend
      }
    }
  ' "$summary"
}

environment_value() {
  local environment="$1"
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
  ' "$environment"
}

infer_baseline_environment() {
  local manifest="$1"
  local candidate baseline_dir baseline_file
  if [[ -n "$manifest" && -f "$manifest" ]]; then
    if candidate="$(manifest_value "$manifest" latestEnvironment)"; then
      resolve_manifest_path "$manifest" "$candidate"
      return 0
    fi
    if candidate="$(manifest_value "$manifest" environment)"; then
      resolve_manifest_path "$manifest" "$candidate"
      return 0
    fi
  fi
  baseline_dir="${BASELINE%/*}"
  baseline_file="${BASELINE##*/}"
  if [[ "$baseline_dir" == "$BASELINE" ]]; then
    baseline_dir="."
  fi
  if [[ "$baseline_file" == "latest.tsv" && -f "${baseline_dir}/latest-environment.tsv" ]]; then
    printf '%s' "${baseline_dir}/latest-environment.tsv"
    return 0
  fi
  if [[ "$baseline_file" == "aggregate.tsv" && -f "${baseline_dir}/environment.tsv" ]]; then
    printf '%s' "${baseline_dir}/environment.tsv"
    return 0
  fi
  printf ''
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

metadata_row() {
  printf '%s\t%s\t%s\t%s\n' \
    "$(safe_tsv_field "$1")" \
    "$(safe_tsv_field "${2:-}")" \
    "$(safe_tsv_field "${3:-}")" \
    "$(safe_tsv_field "$4")" >> "$BASELINE_METADATA"
}

check_baseline_metadata() {
  local manifest="$1"
  local failures=0
  local actual
  printf 'field\texpected\tactual\tstatus\n' > "$BASELINE_METADATA"
  if [[ "$CHECK_BASELINE_METADATA" -eq 0 ]]; then
    metadata_row check disabled disabled skip
    record_result baseline-metadata skip 0 "$BASELINE_METADATA" "" "" disabled
    return 0
  fi
  if [[ -z "$manifest" ]]; then
    metadata_row manifest optional missing skip
    record_result baseline-metadata skip 0 "$BASELINE_METADATA" "" "" manifest-not-found
    return 0
  fi
  if [[ ! -f "$manifest" ]]; then
    metadata_row manifest "$manifest" missing fail
    record_result baseline-metadata fail 2 "$BASELINE_METADATA" "" "" manifest-not-found
    return 2
  fi

  for field in model prompt maxTokens aggregateLabel includeWarmupAggregate; do
    case "$field" in
      model) expected="$MODEL_ID" ;;
      prompt) expected="$PROMPT" ;;
      maxTokens) expected="$MAX_TOKENS" ;;
      aggregateLabel) expected="$AGGREGATE_LABEL" ;;
      includeWarmupAggregate) expected="$INCLUDE_WARMUP_AGGREGATE" ;;
      *) expected="" ;;
    esac
    if actual="$(manifest_value "$manifest" "$field")"; then
      if [[ "$actual" == "$expected" ]]; then
        metadata_row "$field" "$expected" "$actual" pass
      else
        metadata_row "$field" "$expected" "$actual" fail
        failures=$((failures + 1))
      fi
    else
      metadata_row "$field" "$expected" missing fail
      failures=$((failures + 1))
    fi
  done

  if [[ "$failures" -gt 0 ]]; then
    record_result baseline-metadata fail 43 "$BASELINE_METADATA" "" "" incompatible-baseline
    return 43
  fi
  record_result baseline-metadata pass 0 "$BASELINE_METADATA" "" "" "$manifest"
  return 0
}

backend_row() {
  printf '%s\t%s\t%s\t%s\n' \
    "$(safe_tsv_field "$1")" \
    "$(safe_tsv_field "${2:-}")" \
    "$(safe_tsv_field "${3:-}")" \
    "$(safe_tsv_field "$4")" >> "$BACKEND_METADATA"
}

check_backend_metadata() {
  local baseline_summary="$1"
  local current_summary="$2"
  local case_name="${AGGREGATE_LABEL}-mean"
  local baseline_backend current_backend
  printf 'field\tbaseline\tcurrent\tstatus\n' > "$BACKEND_METADATA"
  if [[ "$CHECK_BACKEND" -eq 0 ]]; then
    backend_row onnxBackend disabled disabled skip
    record_result backend-metadata skip 0 "$BACKEND_METADATA" "" "" disabled
    return 0
  fi

  baseline_backend="$(summary_value "$baseline_summary" "$case_name" onnxBackend)"
  current_backend="$(summary_value "$current_summary" "$case_name" onnxBackend)"
  if [[ -z "$baseline_backend" || -z "$current_backend" ]]; then
    backend_row onnxBackend "$baseline_backend" "$current_backend" skip
    record_result backend-metadata skip 0 "$BACKEND_METADATA" "" "" missing-backend
    return 0
  fi
  if [[ "$baseline_backend" != "$current_backend" ]]; then
    backend_row onnxBackend "$baseline_backend" "$current_backend" fail
    record_result backend-metadata fail 44 "$BACKEND_METADATA" "" "" backend-mismatch
    return 44
  fi

  backend_row onnxBackend "$baseline_backend" "$current_backend" pass
  record_result backend-metadata pass 0 "$BACKEND_METADATA" "" "" "$baseline_backend"
  return 0
}

environment_row() {
  printf '%s\t%s\t%s\t%s\n' \
    "$(safe_tsv_field "$1")" \
    "$(safe_tsv_field "${2:-}")" \
    "$(safe_tsv_field "${3:-}")" \
    "$(safe_tsv_field "$4")" >> "$ENVIRONMENT_METADATA"
}

check_environment_metadata() {
  local baseline_environment="$1"
  local failures=0
  local field baseline_value current_value
  printf 'field\tbaseline\tcurrent\tstatus\n' > "$ENVIRONMENT_METADATA"
  if [[ "$STRICT_ENVIRONMENT_CHECK" -eq 0 ]]; then
    environment_row check advisory advisory skip
    record_result environment-metadata skip 0 "$ENVIRONMENT_METADATA" "" "" advisory
    return 0
  fi
  if [[ -z "$baseline_environment" || ! -f "$baseline_environment" ]]; then
    environment_row environment "$baseline_environment" missing fail
    record_result environment-metadata fail 45 "$ENVIRONMENT_METADATA" "" "" baseline-environment-not-found
    return 45
  fi

  cp "$baseline_environment" "$BASELINE_ENVIRONMENT"
  for field in rootDir aqliVersion gitCommit gitDirty hostOs hostArch javaVersion aqliJavaOpts aqliBin; do
    baseline_value=""
    current_value=""
    if ! baseline_value="$(environment_value "$BASELINE_ENVIRONMENT" "$field")"; then
      baseline_value="missing"
    fi
    if ! current_value="$(environment_value "$ENVIRONMENT" "$field")"; then
      current_value="missing"
    fi
    if [[ "$baseline_value" == "$current_value" ]]; then
      environment_row "$field" "$baseline_value" "$current_value" pass
    else
      environment_row "$field" "$baseline_value" "$current_value" fail
      failures=$((failures + 1))
    fi
  done

  if [[ "$failures" -gt 0 ]]; then
    record_result environment-metadata fail 45 "$ENVIRONMENT_METADATA" "" "" environment-mismatch
    return 45
  fi
  record_result environment-metadata pass 0 "$ENVIRONMENT_METADATA" "" "" "$baseline_environment"
  return 0
}

aggregate_args() {
  local input="$1"
  local out="$2"
  local out_dir="$3"
  printf '%s\n' "$SUMMARIZE_SCRIPT" "--summary" "$input" "--summary-dir" "$out_dir" "--out" "$out" "--label" "$AGGREGATE_LABEL"
  if [[ "$INCLUDE_WARMUP_AGGREGATE" -eq 1 ]]; then
    printf '%s\n' "--include-warmup"
  fi
}

baseline_is_aggregate() {
  awk -v label="$AGGREGATE_LABEL" '
    BEGIN { FS = "\t"; wanted = label "-mean" }
    NR > 1 && $1 == wanted { found = 1 }
    END { exit found ? 0 : 1 }
  ' "$BASELINE"
}

{
  printf 'key\tvalue\n'
} > "$CONFIG"
config_row generatedAt "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
config_row rootDir "$ROOT_DIR"
config_row model "$MODEL_ID"
config_row baseline "$BASELINE"
RESOLVED_BASELINE_MANIFEST="$(infer_baseline_manifest)"
CONFIGURED_EXPECT_BACKEND="$EXPECT_BACKEND"
EXPECT_BACKEND_SOURCE="$(expect_backend_source "$RESOLVED_BASELINE_MANIFEST" "$CONFIGURED_EXPECT_BACKEND")"
if [[ -z "$EXPECT_BACKEND" ]]; then
  EXPECT_BACKEND="$(infer_expect_backend "$RESOLVED_BASELINE_MANIFEST")"
fi
aqli_onnx_profile_env_write "$ENVIRONMENT" "$AQLI_BIN"
RESOLVED_BASELINE_ENVIRONMENT="$(infer_baseline_environment "$RESOLVED_BASELINE_MANIFEST")"
config_row baselineManifest "$RESOLVED_BASELINE_MANIFEST"
config_row baselineMetadataCheck "$CHECK_BASELINE_METADATA"
config_row backendCheck "$CHECK_BACKEND"
config_row strictEnvironmentCheck "$STRICT_ENVIRONMENT_CHECK"
config_row environment "$ENVIRONMENT"
config_row baselineEnvironment "$RESOLVED_BASELINE_ENVIRONMENT"
config_row prompt "$PROMPT"
config_row maxTokens "$MAX_TOKENS"
config_row runs "$RUNS"
config_row warmupRuns "$WARMUP_RUNS"
config_row aqliBin "$AQLI_BIN"
config_row summaryDir "$SUMMARY_DIR"
config_row benchScript "$BENCH_SCRIPT"
config_row summarizeScript "$SUMMARIZE_SCRIPT"
config_row compareScript "$COMPARE_SCRIPT"
config_row noiseScript "$NOISE_SCRIPT"
config_row envScript "$ENV_SCRIPT"
config_row bundleScript "$BUNDLE_SCRIPT"
config_row diagnoseScript "$DIAGNOSE_SCRIPT"
config_row diagnosisCompareScript "$DIAGNOSIS_COMPARE_SCRIPT"
config_row decisionScript "$DECISION_SCRIPT"
config_row bundle "$BUNDLE"
config_row bundleJson "$BUNDLE_JSON"
config_row decision "$DECISION"
config_row currentRaw "$CURRENT_RAW"
config_row currentAggregate "$CURRENT_AGGREGATE"
config_row currentDiagnosis "$CURRENT_DIAGNOSIS"
config_row currentDiagnosisStages "$CURRENT_DIAGNOSIS_STAGES"
config_row currentDiagnosisReport "$CURRENT_DIAGNOSIS_REPORT"
config_row currentNoise "$NOISE"
config_row baselineAggregate "$BASELINE_AGGREGATE"
config_row baselineDiagnosis "$BASELINE_DIAGNOSIS"
config_row baselineDiagnosisStages "$BASELINE_DIAGNOSIS_STAGES"
config_row baselineDiagnosisReport "$BASELINE_DIAGNOSIS_REPORT"
config_row diagnosisDiff "$DIAGNOSIS_DIFF"
config_row diagnosisDiffSummary "$DIAGNOSIS_DIFF_SUMMARY"
config_row diagnosisDiffTable "$DIAGNOSIS_DIFF_TABLE"
config_row diagnosisDiffReport "$DIAGNOSIS_DIFF_REPORT"
config_row maxRegressionPercent "$MAX_REGRESSION_PERCENT"
config_row maxNoisePercent "$MAX_NOISE_PERCENT"
config_row metrics "$METRICS"
config_row noiseMetrics "$NOISE_METRICS"
config_row aggregateLabel "$AGGREGATE_LABEL"
config_row includeWarmupAggregate "$INCLUDE_WARMUP_AGGREGATE"
config_row requireProfile "$REQUIRE_PROFILE"
config_row configuredExpectBackend "$CONFIGURED_EXPECT_BACKEND"
config_row expectBackend "$EXPECT_BACKEND"
config_row expectBackendSource "$EXPECT_BACKEND_SOURCE"
config_row failMissingMetric "$FAIL_MISSING_METRIC"

printf 'stage\tstatus\texitCode\tartifact\tstdout\tstderr\treason\n' > "$RESULTS"
record_result environment pass 0 "$ENVIRONMENT" "" ""

write_regression_bundle() {
  aqli_onnx_performance_bundle_init "$BUNDLE"
  aqli_onnx_performance_bundle_add "$BUNDLE" config "$CONFIG" required "Regression configuration"
  aqli_onnx_performance_bundle_add "$BUNDLE" results "$RESULTS" required "Stage results"
  aqli_onnx_performance_bundle_add "$BUNDLE" decision "$DECISION" required "Regression decision summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" environment "$ENVIRONMENT" required "Current runtime and build fingerprint"
  aqli_onnx_performance_bundle_add "$BUNDLE" environmentMetadata "$ENVIRONMENT_METADATA" required "Environment compatibility evidence"
  aqli_onnx_performance_bundle_add "$BUNDLE" baselineEnvironment "$BASELINE_ENVIRONMENT" optional "Resolved baseline environment copy"
  aqli_onnx_performance_bundle_add "$BUNDLE" baselineMetadata "$BASELINE_METADATA" required "Baseline manifest compatibility evidence"
  aqli_onnx_performance_bundle_add "$BUNDLE" backendMetadata "$BACKEND_METADATA" required "Backend compatibility evidence"
  aqli_onnx_performance_bundle_add "$BUNDLE" currentRaw "$CURRENT_RAW" required "Current raw benchmark summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" currentAggregate "$CURRENT_AGGREGATE" required "Current aggregate summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" currentDiagnosis "$CURRENT_DIAGNOSIS" optional "Current performance diagnosis"
  aqli_onnx_performance_bundle_add "$BUNDLE" currentDiagnosisStages "$CURRENT_DIAGNOSIS_STAGES" optional "Current diagnosed stage ranking"
  aqli_onnx_performance_bundle_add "$BUNDLE" currentDiagnosisReport "$CURRENT_DIAGNOSIS_REPORT" optional "Current human-readable diagnosis report"
  aqli_onnx_performance_bundle_add "$BUNDLE" currentNoise "$NOISE" optional "Current noise stability summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" baselineAggregate "$BASELINE_AGGREGATE" required "Baseline aggregate used for comparison"
  aqli_onnx_performance_bundle_add "$BUNDLE" baselineDiagnosis "$BASELINE_DIAGNOSIS" optional "Baseline performance diagnosis"
  aqli_onnx_performance_bundle_add "$BUNDLE" baselineDiagnosisStages "$BASELINE_DIAGNOSIS_STAGES" optional "Baseline diagnosed stage ranking"
  aqli_onnx_performance_bundle_add "$BUNDLE" baselineDiagnosisReport "$BASELINE_DIAGNOSIS_REPORT" optional "Baseline human-readable diagnosis report"
  aqli_onnx_performance_bundle_add "$BUNDLE" diagnosisDiff "$DIAGNOSIS_DIFF" optional "Current versus baseline diagnosis comparison"
  aqli_onnx_performance_bundle_add "$BUNDLE" diagnosisDiffSummary "$DIAGNOSIS_DIFF_SUMMARY" optional "Diagnosis comparison summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" diagnosisDiffTable "$DIAGNOSIS_DIFF_TABLE" optional "Human-readable diagnosis comparison"
  aqli_onnx_performance_bundle_add "$BUNDLE" diagnosisDiffReport "$DIAGNOSIS_DIFF_REPORT" optional "Diagnosis comparison report"
  aqli_onnx_performance_bundle_add "$BUNDLE" comparison "${COMPARE_DIR}/comparison.tsv" required "Row-level regression comparison"
  aqli_onnx_performance_bundle_add "$BUNDLE" comparisonTable "${COMPARE_DIR}/comparison-table.txt" optional "Human-readable regression comparison"
  aqli_onnx_performance_bundle_add "$BUNDLE" metricSummary "${COMPARE_DIR}/metric-summary.tsv" optional "Per-metric regression summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" compareReport "${COMPARE_DIR}/report.txt" optional "Comparator report"
  aqli_onnx_performance_bundle_add "$BUNDLE" benchStdout "$BENCH_STDOUT" optional "Benchmark stdout"
  aqli_onnx_performance_bundle_add "$BUNDLE" benchStderr "$BENCH_STDERR" optional "Benchmark stderr"
  aqli_onnx_performance_bundle_add "$BUNDLE" currentSummaryStdout "$CURRENT_SUMMARY_STDOUT" optional "Current aggregation stdout"
  aqli_onnx_performance_bundle_add "$BUNDLE" currentSummaryStderr "$CURRENT_SUMMARY_STDERR" optional "Current aggregation stderr"
  aqli_onnx_performance_bundle_add "$BUNDLE" baselineSummaryStdout "$BASELINE_SUMMARY_STDOUT" optional "Baseline aggregation stdout"
  aqli_onnx_performance_bundle_add "$BUNDLE" baselineSummaryStderr "$BASELINE_SUMMARY_STDERR" optional "Baseline aggregation stderr"
  aqli_onnx_performance_bundle_add "$BUNDLE" currentDiagnosisStdout "$CURRENT_DIAGNOSIS_STDOUT" optional "Current diagnosis stdout"
  aqli_onnx_performance_bundle_add "$BUNDLE" currentDiagnosisStderr "$CURRENT_DIAGNOSIS_STDERR" optional "Current diagnosis stderr"
  aqli_onnx_performance_bundle_add "$BUNDLE" baselineDiagnosisStdout "$BASELINE_DIAGNOSIS_STDOUT" optional "Baseline diagnosis stdout"
  aqli_onnx_performance_bundle_add "$BUNDLE" baselineDiagnosisStderr "$BASELINE_DIAGNOSIS_STDERR" optional "Baseline diagnosis stderr"
  aqli_onnx_performance_bundle_add "$BUNDLE" diagnosisDiffStdout "$DIAGNOSIS_DIFF_STDOUT" optional "Diagnosis comparison stdout"
  aqli_onnx_performance_bundle_add "$BUNDLE" diagnosisDiffStderr "$DIAGNOSIS_DIFF_STDERR" optional "Diagnosis comparison stderr"
  aqli_onnx_performance_bundle_add "$BUNDLE" compareStdout "$COMPARE_STDOUT" optional "Comparator stdout"
  aqli_onnx_performance_bundle_add "$BUNDLE" compareStderr "$COMPARE_STDERR" optional "Comparator stderr"
  aqli_onnx_performance_bundle_add "$BUNDLE" noiseStdout "$NOISE_STDOUT" optional "Noise checker stdout"
  aqli_onnx_performance_bundle_add "$BUNDLE" noiseStderr "$NOISE_STDERR" optional "Noise checker stderr"
  aqli_onnx_performance_bundle_write_json "$BUNDLE" "$BUNDLE_JSON"
}

write_regression_decision() {
  "$DECISION_SCRIPT" \
    --config "$CONFIG" \
    --results "$RESULTS" \
    --out "$DECISION" \
    --comparison "${COMPARE_DIR}/comparison.tsv" \
    --metric-summary "${COMPARE_DIR}/metric-summary.tsv" \
    --noise "$NOISE" \
    --bundle "$BUNDLE" >/dev/null
}

write_regression_artifacts() {
  write_regression_decision
  write_regression_bundle
  write_regression_decision
}

run_regression_diagnosis() {
  local stage="$1"
  local summary="$2"
  local out_dir="$3"
  local out="$4"
  local stages_out="$5"
  local stdout_log="$6"
  local stderr_log="$7"

  declare -a diagnosis_args=(
    "$DIAGNOSE_SCRIPT"
    "--summary" "$summary"
    "--summary-dir" "$out_dir"
    "--out" "$out"
    "--stages-out" "$stages_out"
    "--case" "${AGGREGATE_LABEL}-mean"
  )
  if [[ "$INCLUDE_WARMUP_AGGREGATE" -eq 1 ]]; then
    diagnosis_args+=("--include-warmup")
  fi

  set +e
  "${diagnosis_args[@]}" >"$stdout_log" 2>"$stderr_log"
  local diagnosis_exit=$?
  set -e
  if [[ "$diagnosis_exit" -ne 0 || ! -f "$out" ]]; then
    record_result "$stage" skip "$diagnosis_exit" "$out" "$stdout_log" "$stderr_log" diagnosis-unavailable
    return 0
  fi
  record_result "$stage" pass 0 "$out" "$stdout_log" "$stderr_log"
}

run_regression_diagnosis_diff() {
  if [[ ! -f "$BASELINE_DIAGNOSIS_STAGES" || ! -f "$CURRENT_DIAGNOSIS_STAGES" ]]; then
    record_result diagnosis-diff skip 0 "$DIAGNOSIS_DIFF" "$DIAGNOSIS_DIFF_STDOUT" "$DIAGNOSIS_DIFF_STDERR" missing-diagnosis-stages
    return 0
  fi

  declare -a diagnosis_diff_args=(
    "$DIAGNOSIS_COMPARE_SCRIPT"
    "--baseline-stages" "$BASELINE_DIAGNOSIS_STAGES"
    "--current-stages" "$CURRENT_DIAGNOSIS_STAGES"
    "--summary-dir" "$DIAGNOSIS_DIFF_DIR"
    "--out" "$DIAGNOSIS_DIFF"
    "--summary-out" "$DIAGNOSIS_DIFF_SUMMARY"
    "--table-out" "$DIAGNOSIS_DIFF_TABLE"
  )

  set +e
  "${diagnosis_diff_args[@]}" >"$DIAGNOSIS_DIFF_STDOUT" 2>"$DIAGNOSIS_DIFF_STDERR"
  local diagnosis_diff_exit=$?
  set -e
  if [[ "$diagnosis_diff_exit" -ne 0 || ! -f "$DIAGNOSIS_DIFF" ]]; then
    record_result diagnosis-diff skip "$diagnosis_diff_exit" "$DIAGNOSIS_DIFF" "$DIAGNOSIS_DIFF_STDOUT" "$DIAGNOSIS_DIFF_STDERR" diagnosis-diff-unavailable
    return 0
  fi
  record_result diagnosis-diff pass 0 "$DIAGNOSIS_DIFF" "$DIAGNOSIS_DIFF_STDOUT" "$DIAGNOSIS_DIFF_STDERR"
}

ENVIRONMENT_EXIT=0
check_environment_metadata "$RESOLVED_BASELINE_ENVIRONMENT" || ENVIRONMENT_EXIT=$?
if [[ "$ENVIRONMENT_EXIT" -ne 0 ]]; then
  write_regression_artifacts
  {
    echo "ONNX profile environment metadata is incompatible; artifacts are in $SUMMARY_DIR"
    echo "summaryDir=$SUMMARY_DIR"
    echo "artifacts.bundle=$BUNDLE"
    echo "artifacts.bundleJson=$BUNDLE_JSON"
    echo "artifacts.decision=$DECISION"
    echo "artifacts.config=$CONFIG"
    echo "artifacts.results=$RESULTS"
    echo "artifacts.environment=$ENVIRONMENT"
    echo "artifacts.environmentMetadata=$ENVIRONMENT_METADATA"
    echo "artifacts.baselineEnvironment=$BASELINE_ENVIRONMENT"
    echo
    awk 'BEGIN { FS = "\t" } NR > 1 && $4 == "fail" { printf "  %s baseline=%s current=%s\n", $1, $2, $3 }' "$ENVIRONMENT_METADATA"
  } | tee "$REPORT" >&2
  exit "$ENVIRONMENT_EXIT"
fi

METADATA_EXIT=0
check_baseline_metadata "$RESOLVED_BASELINE_MANIFEST" || METADATA_EXIT=$?
if [[ "$METADATA_EXIT" -ne 0 ]]; then
  write_regression_artifacts
  {
    echo "ONNX profile baseline metadata is incompatible; artifacts are in $SUMMARY_DIR"
    echo "summaryDir=$SUMMARY_DIR"
    echo "artifacts.bundle=$BUNDLE"
    echo "artifacts.bundleJson=$BUNDLE_JSON"
    echo "artifacts.decision=$DECISION"
    echo "artifacts.config=$CONFIG"
    echo "artifacts.results=$RESULTS"
    echo "artifacts.environment=$ENVIRONMENT"
    echo "artifacts.environmentMetadata=$ENVIRONMENT_METADATA"
    echo "artifacts.baselineMetadata=$BASELINE_METADATA"
    echo
    awk 'BEGIN { FS = "\t" } NR > 1 && $4 == "fail" { printf "  %s expected=%s actual=%s\n", $1, $2, $3 }' "$BASELINE_METADATA"
  } | tee "$REPORT" >&2
  exit "$METADATA_EXIT"
fi

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
if [[ "$BENCH_EXIT" -ne 0 || ! -f "$CURRENT_RAW" ]]; then
  record_result benchmark fail "$BENCH_EXIT" "$CURRENT_RAW" "$BENCH_STDOUT" "$BENCH_STDERR" benchmark-failed
  write_regression_artifacts
  {
    echo "ONNX profile regression benchmark failed; artifacts are in $SUMMARY_DIR"
    echo "summaryDir=$SUMMARY_DIR"
    echo "artifacts.bundle=$BUNDLE"
    echo "artifacts.bundleJson=$BUNDLE_JSON"
    echo "artifacts.decision=$DECISION"
  } | tee "$REPORT" >&2
  exit "$([[ "$BENCH_EXIT" -eq 0 ]] && printf 3 || printf '%s' "$BENCH_EXIT")"
fi
record_result benchmark pass 0 "$CURRENT_RAW" "$BENCH_STDOUT" "$BENCH_STDERR"

mapfile_current=()
while IFS= read -r arg; do
  mapfile_current+=("$arg")
done < <(aggregate_args "$CURRENT_RAW" "$CURRENT_AGGREGATE" "$CURRENT_SUMMARY_DIR")
set +e
"${mapfile_current[@]}" >"$CURRENT_SUMMARY_STDOUT" 2>"$CURRENT_SUMMARY_STDERR"
CURRENT_SUMMARY_EXIT=$?
set -e
if [[ "$CURRENT_SUMMARY_EXIT" -ne 0 || ! -f "$CURRENT_AGGREGATE" ]]; then
  record_result current-aggregate fail "$CURRENT_SUMMARY_EXIT" "$CURRENT_AGGREGATE" "$CURRENT_SUMMARY_STDOUT" "$CURRENT_SUMMARY_STDERR" aggregate-failed
  write_regression_artifacts
  {
    echo "ONNX profile current aggregation failed; artifacts are in $SUMMARY_DIR"
    echo "summaryDir=$SUMMARY_DIR"
    echo "artifacts.bundle=$BUNDLE"
    echo "artifacts.bundleJson=$BUNDLE_JSON"
    echo "artifacts.decision=$DECISION"
  } | tee "$REPORT" >&2
  exit "$([[ "$CURRENT_SUMMARY_EXIT" -eq 0 ]] && printf 3 || printf '%s' "$CURRENT_SUMMARY_EXIT")"
fi
record_result current-aggregate pass 0 "$CURRENT_AGGREGATE" "$CURRENT_SUMMARY_STDOUT" "$CURRENT_SUMMARY_STDERR"
run_regression_diagnosis current-diagnosis "$CURRENT_AGGREGATE" "$CURRENT_DIAGNOSIS_DIR" "$CURRENT_DIAGNOSIS" "$CURRENT_DIAGNOSIS_STAGES" "$CURRENT_DIAGNOSIS_STDOUT" "$CURRENT_DIAGNOSIS_STDERR"

if [[ -n "$MAX_NOISE_PERCENT" ]]; then
  declare -a NOISE_ARGS=(
    "$NOISE_SCRIPT"
    "--aggregate" "$CURRENT_AGGREGATE"
    "--summary-dir" "$NOISE_DIR"
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
    record_result current-noise fail 47 "$NOISE" "$NOISE_STDOUT" "$NOISE_STDERR" noise-failed
    write_regression_artifacts
    {
      echo "ONNX profile current benchmark noise check failed; artifacts are in $SUMMARY_DIR"
      echo "summaryDir=$SUMMARY_DIR"
      echo "artifacts.bundle=$BUNDLE"
      echo "artifacts.bundleJson=$BUNDLE_JSON"
      echo "artifacts.decision=$DECISION"
      echo "artifacts.config=$CONFIG"
      echo "artifacts.results=$RESULTS"
      echo "artifacts.environment=$ENVIRONMENT"
      echo "artifacts.currentRaw=$CURRENT_RAW"
      echo "artifacts.currentAggregate=$CURRENT_AGGREGATE"
      echo "artifacts.currentNoise=$NOISE"
      echo "artifacts.currentNoiseStdout=$NOISE_STDOUT"
      echo "artifacts.currentNoiseStderr=$NOISE_STDERR"
      echo "maxNoisePercent=$MAX_NOISE_PERCENT"
    } | tee "$REPORT" >&2
    exit 47
  fi
  record_result current-noise pass 0 "$NOISE" "$NOISE_STDOUT" "$NOISE_STDERR"
fi

BASELINE_FOR_COMPARE="$BASELINE"
if baseline_is_aggregate; then
  cp "$BASELINE" "$BASELINE_AGGREGATE"
  BASELINE_FOR_COMPARE="$BASELINE_AGGREGATE"
  record_result baseline-aggregate pass 0 "$BASELINE_FOR_COMPARE" "" "" already-aggregate
else
  mapfile_baseline=()
  while IFS= read -r arg; do
    mapfile_baseline+=("$arg")
  done < <(aggregate_args "$BASELINE" "$BASELINE_AGGREGATE" "$BASELINE_SUMMARY_DIR")
  set +e
  "${mapfile_baseline[@]}" >"$BASELINE_SUMMARY_STDOUT" 2>"$BASELINE_SUMMARY_STDERR"
  BASELINE_SUMMARY_EXIT=$?
  set -e
  if [[ "$BASELINE_SUMMARY_EXIT" -ne 0 || ! -f "$BASELINE_AGGREGATE" ]]; then
    record_result baseline-aggregate fail "$BASELINE_SUMMARY_EXIT" "$BASELINE_AGGREGATE" "$BASELINE_SUMMARY_STDOUT" "$BASELINE_SUMMARY_STDERR" aggregate-failed
    write_regression_artifacts
    {
      echo "ONNX profile baseline aggregation failed; artifacts are in $SUMMARY_DIR"
      echo "summaryDir=$SUMMARY_DIR"
      echo "artifacts.bundle=$BUNDLE"
      echo "artifacts.bundleJson=$BUNDLE_JSON"
      echo "artifacts.decision=$DECISION"
    } | tee "$REPORT" >&2
    exit "$([[ "$BASELINE_SUMMARY_EXIT" -eq 0 ]] && printf 3 || printf '%s' "$BASELINE_SUMMARY_EXIT")"
  fi
  BASELINE_FOR_COMPARE="$BASELINE_AGGREGATE"
  record_result baseline-aggregate pass 0 "$BASELINE_FOR_COMPARE" "$BASELINE_SUMMARY_STDOUT" "$BASELINE_SUMMARY_STDERR"
fi
run_regression_diagnosis baseline-diagnosis "$BASELINE_FOR_COMPARE" "$BASELINE_DIAGNOSIS_DIR" "$BASELINE_DIAGNOSIS" "$BASELINE_DIAGNOSIS_STAGES" "$BASELINE_DIAGNOSIS_STDOUT" "$BASELINE_DIAGNOSIS_STDERR"
run_regression_diagnosis_diff

BACKEND_EXIT=0
check_backend_metadata "$BASELINE_FOR_COMPARE" "$CURRENT_AGGREGATE" || BACKEND_EXIT=$?
if [[ "$BACKEND_EXIT" -ne 0 ]]; then
  write_regression_artifacts
  {
    echo "ONNX profile backend metadata is incompatible; artifacts are in $SUMMARY_DIR"
    echo "summaryDir=$SUMMARY_DIR"
    echo "artifacts.bundle=$BUNDLE"
    echo "artifacts.bundleJson=$BUNDLE_JSON"
    echo "artifacts.decision=$DECISION"
    echo "artifacts.config=$CONFIG"
    echo "artifacts.results=$RESULTS"
    echo "artifacts.environment=$ENVIRONMENT"
    echo "artifacts.environmentMetadata=$ENVIRONMENT_METADATA"
    echo "artifacts.baselineMetadata=$BASELINE_METADATA"
    echo "artifacts.backendMetadata=$BACKEND_METADATA"
    echo "artifacts.currentAggregate=$CURRENT_AGGREGATE"
    echo "artifacts.baselineAggregate=$BASELINE_FOR_COMPARE"
    echo
    awk 'BEGIN { FS = "\t" } NR > 1 && $4 == "fail" { printf "  %s baseline=%s current=%s\n", $1, $2, $3 }' "$BACKEND_METADATA"
  } | tee "$REPORT" >&2
  exit "$BACKEND_EXIT"
fi

declare -a COMPARE_ARGS=(
  "$COMPARE_SCRIPT"
  "--baseline" "$BASELINE_FOR_COMPARE"
  "--current" "$CURRENT_AGGREGATE"
  "--summary-dir" "$COMPARE_DIR"
  "--max-regression-percent" "$MAX_REGRESSION_PERCENT"
)
if [[ -n "$METRICS" ]]; then
  COMPARE_ARGS+=("--metrics" "$METRICS")
fi
if [[ "$FAIL_MISSING_METRIC" -eq 1 ]]; then
  COMPARE_ARGS+=("--fail-missing-metric")
fi

set +e
"${COMPARE_ARGS[@]}" >"$COMPARE_STDOUT" 2>"$COMPARE_STDERR"
COMPARE_EXIT=$?
set -e
if [[ "$COMPARE_EXIT" -eq 0 ]]; then
  record_result compare pass 0 "${COMPARE_DIR}/comparison.tsv" "$COMPARE_STDOUT" "$COMPARE_STDERR"
else
  record_result compare fail "$COMPARE_EXIT" "${COMPARE_DIR}/comparison.tsv" "$COMPARE_STDOUT" "$COMPARE_STDERR" regression-detected
fi
write_regression_artifacts

{
  echo "Aqli ONNX profile regression gate"
  echo "summaryDir=$SUMMARY_DIR"
  echo "artifacts.config=$CONFIG"
  echo "artifacts.results=$RESULTS"
  echo "artifacts.bundle=$BUNDLE"
  echo "artifacts.bundleJson=$BUNDLE_JSON"
  echo "artifacts.decision=$DECISION"
  echo "artifacts.environment=$ENVIRONMENT"
  echo "artifacts.environmentMetadata=$ENVIRONMENT_METADATA"
  echo "artifacts.baselineMetadata=$BASELINE_METADATA"
  echo "artifacts.backendMetadata=$BACKEND_METADATA"
  echo "artifacts.currentRaw=$CURRENT_RAW"
  echo "artifacts.currentAggregate=$CURRENT_AGGREGATE"
  if [[ -f "$CURRENT_DIAGNOSIS" ]]; then
    echo "artifacts.currentDiagnosis=$CURRENT_DIAGNOSIS"
    echo "artifacts.currentDiagnosisStages=$CURRENT_DIAGNOSIS_STAGES"
    echo "artifacts.currentDiagnosisReport=$CURRENT_DIAGNOSIS_REPORT"
  fi
  if [[ -f "$NOISE" ]]; then
    echo "artifacts.currentNoise=$NOISE"
  fi
  echo "artifacts.baselineAggregate=$BASELINE_FOR_COMPARE"
  if [[ -f "$BASELINE_DIAGNOSIS" ]]; then
    echo "artifacts.baselineDiagnosis=$BASELINE_DIAGNOSIS"
    echo "artifacts.baselineDiagnosisStages=$BASELINE_DIAGNOSIS_STAGES"
    echo "artifacts.baselineDiagnosisReport=$BASELINE_DIAGNOSIS_REPORT"
  fi
  if [[ -f "$DIAGNOSIS_DIFF" ]]; then
    echo "artifacts.diagnosisDiff=$DIAGNOSIS_DIFF"
    echo "artifacts.diagnosisDiffSummary=$DIAGNOSIS_DIFF_SUMMARY"
    echo "artifacts.diagnosisDiffTable=$DIAGNOSIS_DIFF_TABLE"
    echo "artifacts.diagnosisDiffReport=$DIAGNOSIS_DIFF_REPORT"
  fi
  echo "artifacts.comparison=${COMPARE_DIR}/comparison.tsv"
  echo "artifacts.compareReport=${COMPARE_DIR}/report.txt"
  echo
  if [[ "$COMPARE_EXIT" -eq 0 ]]; then
    echo "regression: pass"
  else
    echo "regression: fail"
    if [[ -f "${COMPARE_DIR}/report.txt" ]]; then
      awk 'NR > 1 { print }' "${COMPARE_DIR}/report.txt"
    fi
  fi
} | tee "$REPORT"

exit "$COMPARE_EXIT"
