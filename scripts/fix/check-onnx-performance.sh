#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: check-onnx-performance.sh --model MODEL [--baseline SUMMARY | --use-latest-baseline | --capture-baseline] [options]

Run the appropriate ONNX performance workflow from one entrypoint:
  - gate mode:       no --baseline, no --capture-baseline
  - regression mode: --baseline SUMMARY
  - capture mode:    --capture-baseline
  - compare mode:    --compare-baselines BASELINE_A BASELINE_B
  - bundle mode:     --verify-bundle BUNDLE
  - diagnose mode:   --diagnose-summary SUMMARY

This script is intentionally a thin facade. It delegates to the focused
benchmark gate, regression verifier, baseline capture, artifact verifier, or
diagnosis analyzer so those building blocks stay reusable in CI and local profiling.

Mode selection:
  --model ID                    Model id or local alias (required)
  --baseline PATH               Run regression verification against a baseline
  --baseline latest|auto        Resolve latest baseline from --baseline-root/--name
  --use-latest-baseline         Resolve latest baseline from --baseline-root/--name
  --capture-baseline            Capture/update a reusable baseline
  --compare-baselines A B       Compare two stored baselines without running a model
  --verify-bundle PATH          Validate a bundle.tsv without running a model
  --diagnose-summary PATH       Analyze summary.tsv/aggregate.tsv without running a model

Common forwarded options:
  --prompt TEXT
  --max-tokens N
  --runs N
  --warmup-runs N
  --aqli-bin PATH
  --expect-backend NAME
  --require-profile
  --no-require-profile
  --decision-out PATH

Regression/capture forwarded options:
  --max-noise-percent N
  --noise-metrics CSV

Regression forwarded options:
  --baseline-manifest PATH
  --summary-dir DIR
  --max-regression-percent N
  --metrics CSV

Compare forwarded options:
  --summary-dir DIR
  --table-out PATH
  --metric-summary-out PATH
  --max-regression-percent N
  --metrics CSV
  --fail-missing-metric

Bundle verification forwarded options:
  --bundle-summary-out PATH     Write bundle verification summary TSV

Diagnosis forwarded options:
  --diagnosis-summary-dir DIR   Directory for diagnosis artifacts
  --diagnosis-out PATH          Write diagnosis key/value TSV
  --diagnosis-stages-out PATH   Write stage ranking TSV
  --diagnosis-case NAME         Diagnose a specific case row
  --diagnosis-include-warmup    Include warmup-* rows when averaging raw summaries

Capture forwarded options:
  --run-label NAME
  --no-update-latest

Baseline discovery/capture options:
  --baseline-root DIR           Baseline root (default: ops/benchmarks/onnx/baselines)
  --name NAME                   Baseline model directory name (default: sanitized model id)
  --list-baselines              Print discovered latest baselines as TSV and exit
  --format tsv|table            Baseline list format (default: tsv)
  --sort FIELD                  Baseline list sort field (default: name)

Presets:
  --preset quick                Light smoke run: 1 token, 1 run, no warmup
  --preset stable               Stable run: 5 runs, 1 warmup, noise gate for regression/capture
  --preset coreml-stable        Stable preset plus --expect-backend CoreML
  --list-presets                Print preset registry as TSV and exit

Gate forwarded options:
  --max-duration-ms N
  --min-generation-tps N
  --min-decode-tps N
  --max-ttft-ms N
  --max-token-latency-ms N
  --max-onnx-ort-run-ms N

Script overrides:
  --gate-script PATH            Override verify-onnx-profile-gates.sh
  --regression-script PATH      Override verify-onnx-profile-regression.sh
  --capture-script PATH         Override capture-onnx-profile-baseline.sh
  --compare-script PATH         Override compare-onnx-profile-summary.sh
  --bundle-verify-script PATH   Override verify-onnx-performance-bundle.sh
  --diagnose-script PATH        Override diagnose-onnx-profile-summary.sh
  --preset-script PATH          Override preset registry helper
  --baseline-script PATH        Override baseline registry helper
  --bundle-script PATH          Override artifact bundle helper for all workflow modes
  --decision-script PATH        Override decision summary helper for all workflow modes

Planning:
  --dry-run                     Resolve mode and argv, but do not execute
  --plan-out PATH               Write resolved plan TSV before execution/dry-run

Examples:
  ./scripts/check-onnx-performance.sh --model 6b6e13 --expect-backend CoreML --max-duration-ms 30000
  ./scripts/check-onnx-performance.sh --model 6b6e13 --use-latest-baseline --name webworld --max-noise-percent 10
  ./scripts/check-onnx-performance.sh --model 6b6e13 --capture-baseline --expect-backend CoreML --max-noise-percent 15
  ./scripts/check-onnx-performance.sh --model 6b6e13 --use-latest-baseline --name webworld --preset coreml-stable
  ./scripts/check-onnx-performance.sh --compare-baselines webworld webworld-after --baseline-root ops/benchmarks/onnx/baselines
  ./scripts/check-onnx-performance.sh --verify-bundle ops/benchmarks/onnx/current/bundle.tsv
  ./scripts/check-onnx-performance.sh --diagnose-summary ops/benchmarks/onnx/current/aggregate.tsv
USAGE
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODEL_ID=""
BASELINE=""
COMPARE_BASELINE=""
COMPARE_CURRENT=""
VERIFY_BUNDLE=""
BUNDLE_SUMMARY_OUT=""
DIAGNOSE_SUMMARY=""
DIAGNOSIS_SUMMARY_DIR=""
DIAGNOSIS_OUT=""
DIAGNOSIS_STAGES_OUT=""
DIAGNOSIS_CASE=""
DIAGNOSIS_INCLUDE_WARMUP=0
DIAGNOSE_SCRIPT_OVERRIDDEN=0
CAPTURE_BASELINE=0
USE_LATEST_BASELINE=0
BASELINE_ROOT="ops/benchmarks/onnx/baselines"
BASELINE_NAME=""
DRY_RUN=0
PLAN_OUT=""
PRESET=""
LIST_PRESETS=0
LIST_BASELINES=0
BASELINE_LIST_FORMAT="tsv"
BASELINE_SORT="name"
GATE_SCRIPT="${ROOT_DIR}/scripts/verify-onnx-profile-gates.sh"
REGRESSION_SCRIPT="${ROOT_DIR}/scripts/verify-onnx-profile-regression.sh"
CAPTURE_SCRIPT="${ROOT_DIR}/scripts/capture-onnx-profile-baseline.sh"
COMPARE_SCRIPT="${ROOT_DIR}/scripts/compare-onnx-profile-summary.sh"
BUNDLE_VERIFY_SCRIPT="${ROOT_DIR}/scripts/verify-onnx-performance-bundle.sh"
DIAGNOSE_SCRIPT="${ROOT_DIR}/scripts/diagnose-onnx-profile-summary.sh"
PRESET_SCRIPT="${ROOT_DIR}/scripts/onnx-performance-presets.sh"
BASELINE_SCRIPT="${ROOT_DIR}/scripts/onnx-performance-baselines.sh"
declare -a FORWARDED_ARGS=()
declare -a DELEGATED_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model)
      MODEL_ID="$2"
      FORWARDED_ARGS+=("$1" "$2")
      shift 2
      ;;
    --model=*)
      MODEL_ID="${1#*=}"
      FORWARDED_ARGS+=("$1")
      shift
      ;;
    --baseline)
      BASELINE="$2"
      if [[ "$BASELINE" == "latest" || "$BASELINE" == "auto" ]]; then
        BASELINE=""
        USE_LATEST_BASELINE=1
      else
        FORWARDED_ARGS+=("$1" "$2")
      fi
      shift 2
      ;;
    --baseline=*)
      BASELINE="${1#*=}"
      if [[ "$BASELINE" == "latest" || "$BASELINE" == "auto" ]]; then
        BASELINE=""
        USE_LATEST_BASELINE=1
      else
        FORWARDED_ARGS+=("$1")
      fi
      shift
      ;;
    --use-latest-baseline)
      USE_LATEST_BASELINE=1
      shift
      ;;
    --capture-baseline)
      CAPTURE_BASELINE=1
      shift
      ;;
    --compare-baselines)
      COMPARE_BASELINE="$2"
      COMPARE_CURRENT="$3"
      shift 3
      ;;
    --compare-baselines=*)
      echo "--compare-baselines requires two separate values" >&2
      exit 2
      ;;
    --verify-bundle)
      VERIFY_BUNDLE="$2"
      shift 2
      ;;
    --verify-bundle=*)
      VERIFY_BUNDLE="${1#*=}"
      shift
      ;;
    --bundle-summary-out)
      BUNDLE_SUMMARY_OUT="$2"
      shift 2
      ;;
    --bundle-summary-out=*)
      BUNDLE_SUMMARY_OUT="${1#*=}"
      shift
      ;;
    --diagnose-summary)
      DIAGNOSE_SUMMARY="$2"
      shift 2
      ;;
    --diagnose-summary=*)
      DIAGNOSE_SUMMARY="${1#*=}"
      shift
      ;;
    --diagnosis-summary-dir)
      DIAGNOSIS_SUMMARY_DIR="$2"
      shift 2
      ;;
    --diagnosis-summary-dir=*)
      DIAGNOSIS_SUMMARY_DIR="${1#*=}"
      shift
      ;;
    --diagnosis-out)
      DIAGNOSIS_OUT="$2"
      shift 2
      ;;
    --diagnosis-out=*)
      DIAGNOSIS_OUT="${1#*=}"
      shift
      ;;
    --diagnosis-stages-out)
      DIAGNOSIS_STAGES_OUT="$2"
      shift 2
      ;;
    --diagnosis-stages-out=*)
      DIAGNOSIS_STAGES_OUT="${1#*=}"
      shift
      ;;
    --diagnosis-case)
      DIAGNOSIS_CASE="$2"
      shift 2
      ;;
    --diagnosis-case=*)
      DIAGNOSIS_CASE="${1#*=}"
      shift
      ;;
    --diagnosis-include-warmup)
      DIAGNOSIS_INCLUDE_WARMUP=1
      shift
      ;;
    --baseline-root)
      BASELINE_ROOT="$2"
      shift 2
      ;;
    --baseline-root=*)
      BASELINE_ROOT="${1#*=}"
      shift
      ;;
    --name)
      BASELINE_NAME="$2"
      shift 2
      ;;
    --name=*)
      BASELINE_NAME="${1#*=}"
      shift
      ;;
    --preset)
      PRESET="$2"
      shift 2
      ;;
    --preset=*)
      PRESET="${1#*=}"
      shift
      ;;
    --list-presets)
      LIST_PRESETS=1
      shift
      ;;
    --list-baselines)
      LIST_BASELINES=1
      shift
      ;;
    --format)
      BASELINE_LIST_FORMAT="$2"
      shift 2
      ;;
    --format=*)
      BASELINE_LIST_FORMAT="${1#*=}"
      shift
      ;;
    --baseline-format)
      BASELINE_LIST_FORMAT="$2"
      shift 2
      ;;
    --baseline-format=*)
      BASELINE_LIST_FORMAT="${1#*=}"
      shift
      ;;
    --sort)
      BASELINE_SORT="$2"
      shift 2
      ;;
    --sort=*)
      BASELINE_SORT="${1#*=}"
      shift
      ;;
    --sort-baselines)
      BASELINE_SORT="$2"
      shift 2
      ;;
    --sort-baselines=*)
      BASELINE_SORT="${1#*=}"
      shift
      ;;
    --gate-script)
      GATE_SCRIPT="$2"
      shift 2
      ;;
    --gate-script=*)
      GATE_SCRIPT="${1#*=}"
      shift
      ;;
    --regression-script)
      REGRESSION_SCRIPT="$2"
      shift 2
      ;;
    --regression-script=*)
      REGRESSION_SCRIPT="${1#*=}"
      shift
      ;;
    --capture-script)
      CAPTURE_SCRIPT="$2"
      shift 2
      ;;
    --capture-script=*)
      CAPTURE_SCRIPT="${1#*=}"
      shift
      ;;
    --compare-script)
      COMPARE_SCRIPT="$2"
      shift 2
      ;;
    --compare-script=*)
      COMPARE_SCRIPT="${1#*=}"
      shift
      ;;
    --bundle-verify-script)
      BUNDLE_VERIFY_SCRIPT="$2"
      shift 2
      ;;
    --bundle-verify-script=*)
      BUNDLE_VERIFY_SCRIPT="${1#*=}"
      shift
      ;;
    --diagnose-script)
      DIAGNOSE_SCRIPT="$2"
      DIAGNOSE_SCRIPT_OVERRIDDEN=1
      shift 2
      ;;
    --diagnose-script=*)
      DIAGNOSE_SCRIPT="${1#*=}"
      DIAGNOSE_SCRIPT_OVERRIDDEN=1
      shift
      ;;
    --preset-script)
      PRESET_SCRIPT="$2"
      shift 2
      ;;
    --preset-script=*)
      PRESET_SCRIPT="${1#*=}"
      shift
      ;;
    --baseline-script)
      BASELINE_SCRIPT="$2"
      shift 2
      ;;
    --baseline-script=*)
      BASELINE_SCRIPT="${1#*=}"
      shift
      ;;
    --bundle-script)
      FORWARDED_ARGS+=("$1" "$2")
      shift 2
      ;;
    --bundle-script=*)
      FORWARDED_ARGS+=("$1")
      shift
      ;;
    --decision-script)
      FORWARDED_ARGS+=("$1" "$2")
      shift 2
      ;;
    --decision-script=*)
      FORWARDED_ARGS+=("$1")
      shift
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --plan-out)
      PLAN_OUT="$2"
      shift 2
      ;;
    --plan-out=*)
      PLAN_OUT="${1#*=}"
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      FORWARDED_ARGS+=("$1")
      shift
      ;;
  esac
done

if [[ ! -f "$PRESET_SCRIPT" ]]; then
  echo "Preset registry helper not found: $PRESET_SCRIPT" >&2
  exit 2
fi
if [[ ! -f "$BASELINE_SCRIPT" ]]; then
  echo "Baseline registry helper not found: $BASELINE_SCRIPT" >&2
  exit 2
fi
# shellcheck source=/dev/null
source "$PRESET_SCRIPT"
# shellcheck source=/dev/null
source "$BASELINE_SCRIPT"

if [[ "$LIST_PRESETS" -eq 1 ]]; then
  aqli_onnx_performance_preset_list_tsv
  exit 0
fi
if [[ "$LIST_BASELINES" -eq 1 ]]; then
  aqli_onnx_performance_baseline_list "$BASELINE_ROOT" "$BASELINE_NAME" "$BASELINE_LIST_FORMAT" "$BASELINE_SORT"
  exit 0
fi

if [[ -n "$VERIFY_BUNDLE" && ( -n "$MODEL_ID" || -n "$BASELINE" || "$USE_LATEST_BASELINE" -eq 1 || "$CAPTURE_BASELINE" -eq 1 || -n "$COMPARE_BASELINE" || -n "$DIAGNOSE_SUMMARY" ) ]]; then
  echo "--verify-bundle is mutually exclusive with model, baseline, capture, compare, and diagnose modes" >&2
  usage
  exit 2
fi
if [[ -n "$DIAGNOSE_SUMMARY" && ( -n "$MODEL_ID" || -n "$BASELINE" || "$USE_LATEST_BASELINE" -eq 1 || "$CAPTURE_BASELINE" -eq 1 || -n "$COMPARE_BASELINE" || -n "$VERIFY_BUNDLE" ) ]]; then
  echo "--diagnose-summary is mutually exclusive with model, baseline, capture, compare, and bundle modes" >&2
  usage
  exit 2
fi
if [[ -z "$MODEL_ID" && -z "$COMPARE_BASELINE" && -z "$VERIFY_BUNDLE" && -z "$DIAGNOSE_SUMMARY" ]]; then
  echo "--model is required" >&2
  usage
  exit 2
fi
if [[ -z "$BASELINE_ROOT" ]]; then
  echo "--baseline-root must not be empty" >&2
  exit 2
fi
if [[ -n "$PLAN_OUT" && "$PLAN_OUT" == */ ]]; then
  echo "--plan-out must be a file path, not a directory: $PLAN_OUT" >&2
  exit 2
fi
if [[ -n "$BUNDLE_SUMMARY_OUT" && "$BUNDLE_SUMMARY_OUT" == */ ]]; then
  echo "--bundle-summary-out must be a file path, not a directory: $BUNDLE_SUMMARY_OUT" >&2
  exit 2
fi
if [[ -n "$DIAGNOSIS_OUT" && "$DIAGNOSIS_OUT" == */ ]]; then
  echo "--diagnosis-out must be a file path, not a directory: $DIAGNOSIS_OUT" >&2
  exit 2
fi
if [[ -n "$DIAGNOSIS_STAGES_OUT" && "$DIAGNOSIS_STAGES_OUT" == */ ]]; then
  echo "--diagnosis-stages-out must be a file path, not a directory: $DIAGNOSIS_STAGES_OUT" >&2
  exit 2
fi
if ! aqli_onnx_performance_preset_validate "$PRESET"; then
  echo "Unknown --preset: $PRESET" >&2
  echo "Available presets: $(aqli_onnx_performance_preset_names_csv)" >&2
  exit 2
fi
if [[ -n "$COMPARE_BASELINE" && -z "$COMPARE_CURRENT" ]]; then
  echo "--compare-baselines requires two values" >&2
  exit 2
fi
if [[ -n "$COMPARE_BASELINE" && ( "$CAPTURE_BASELINE" -eq 1 || -n "$BASELINE" || "$USE_LATEST_BASELINE" -eq 1 ) ]]; then
  echo "--compare-baselines is mutually exclusive with run/capture baseline modes" >&2
  usage
  exit 2
fi
if [[ "$CAPTURE_BASELINE" -eq 1 && ( -n "$BASELINE" || "$USE_LATEST_BASELINE" -eq 1 ) ]]; then
  echo "--baseline/--use-latest-baseline and --capture-baseline are mutually exclusive" >&2
  usage
  exit 2
fi
if [[ "$USE_LATEST_BASELINE" -eq 1 && -n "$BASELINE" ]]; then
  echo "--baseline and --use-latest-baseline are mutually exclusive" >&2
  usage
  exit 2
fi

slugify() {
  local value="$1"
  local slug
  slug="$(printf '%s' "$value" | tr '/:[:space:]' '___' | tr -cd 'A-Za-z0-9._-')"
  if [[ -z "$slug" ]]; then
    slug="model"
  fi
  printf '%s' "$slug"
}

resolve_baseline_reference() {
  local ref="$1"
  local candidate
  if [[ -f "$ref" ]]; then
    printf '%s' "$ref"
    return 0
  fi
  if [[ -d "$ref" && -f "${ref%/}/latest.tsv" ]]; then
    printf '%s' "${ref%/}/latest.tsv"
    return 0
  fi
  candidate="${BASELINE_ROOT%/}/${ref}/latest.tsv"
  if [[ -f "$candidate" ]]; then
    printf '%s' "$candidate"
    return 0
  fi
  return 1
}

safe_tsv_field() {
  local value="${1:-}"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

write_plan() {
  local out="$1"
  local parent index
  if [[ -z "$out" ]]; then
    return 0
  fi
  parent="${out%/*}"
  if [[ "$parent" != "$out" ]]; then
    mkdir -p "$parent"
  fi
  {
    printf 'section\tkey\tvalue\n'
    printf 'config\trootDir\t%s\n' "$(safe_tsv_field "$ROOT_DIR")"
    printf 'config\tmode\t%s\n' "$(safe_tsv_field "$MODE")"
    printf 'config\tscript\t%s\n' "$(safe_tsv_field "$SCRIPT")"
    printf 'config\tmodel\t%s\n' "$(safe_tsv_field "$MODEL_ID")"
    printf 'config\tbaseline\t%s\n' "$(safe_tsv_field "$BASELINE")"
    printf 'config\tcompareBaseline\t%s\n' "$(safe_tsv_field "$COMPARE_BASELINE")"
    printf 'config\tcompareCurrent\t%s\n' "$(safe_tsv_field "$COMPARE_CURRENT")"
    printf 'config\tverifyBundle\t%s\n' "$(safe_tsv_field "$VERIFY_BUNDLE")"
    printf 'config\tbundleSummaryOut\t%s\n' "$(safe_tsv_field "$BUNDLE_SUMMARY_OUT")"
    printf 'config\tdiagnoseSummary\t%s\n' "$(safe_tsv_field "$DIAGNOSE_SUMMARY")"
    printf 'config\tdiagnoseScript\t%s\n' "$(safe_tsv_field "$DIAGNOSE_SCRIPT")"
    printf 'config\tdiagnosisSummaryDir\t%s\n' "$(safe_tsv_field "$DIAGNOSIS_SUMMARY_DIR")"
    printf 'config\tdiagnosisOut\t%s\n' "$(safe_tsv_field "$DIAGNOSIS_OUT")"
    printf 'config\tdiagnosisStagesOut\t%s\n' "$(safe_tsv_field "$DIAGNOSIS_STAGES_OUT")"
    printf 'config\tdiagnosisCase\t%s\n' "$(safe_tsv_field "$DIAGNOSIS_CASE")"
    printf 'config\tdiagnosisIncludeWarmup\t%s\n' "$(safe_tsv_field "$DIAGNOSIS_INCLUDE_WARMUP")"
    printf 'config\tbaselineRoot\t%s\n' "$(safe_tsv_field "$BASELINE_ROOT")"
    printf 'config\tbaselineName\t%s\n' "$(safe_tsv_field "$BASELINE_NAME")"
    printf 'config\tbaselineScript\t%s\n' "$(safe_tsv_field "$BASELINE_SCRIPT")"
    printf 'config\tpreset\t%s\n' "$(safe_tsv_field "$PRESET")"
    printf 'config\tpresetScript\t%s\n' "$(safe_tsv_field "$PRESET_SCRIPT")"
    printf 'config\tdryRun\t%s\n' "$(safe_tsv_field "$DRY_RUN")"
    printf 'argv\t0\t%s\n' "$(safe_tsv_field "$SCRIPT")"
    index=1
    for arg in "${DELEGATED_ARGS[@]}"; do
      printf 'argv\t%s\t%s\n' "$index" "$(safe_tsv_field "$arg")"
      index=$((index + 1))
    done
  } > "$out"
}

MODE="gate"
SCRIPT="$GATE_SCRIPT"
if [[ -n "$DIAGNOSE_SUMMARY" ]]; then
  MODE="diagnose"
  SCRIPT="$DIAGNOSE_SCRIPT"
elif [[ -n "$VERIFY_BUNDLE" ]]; then
  MODE="verify-bundle"
  SCRIPT="$BUNDLE_VERIFY_SCRIPT"
elif [[ -n "$COMPARE_BASELINE" ]]; then
  MODE="compare-baselines"
  SCRIPT="$COMPARE_SCRIPT"
  compare_baseline_ref="$COMPARE_BASELINE"
  compare_current_ref="$COMPARE_CURRENT"
  if ! COMPARE_BASELINE="$(resolve_baseline_reference "$compare_baseline_ref")"; then
    echo "Compare baseline not found: $compare_baseline_ref" >&2
    exit 2
  fi
  if ! COMPARE_CURRENT="$(resolve_baseline_reference "$compare_current_ref")"; then
    echo "Compare current baseline not found: $compare_current_ref" >&2
    exit 2
  fi
elif [[ "$CAPTURE_BASELINE" -eq 1 ]]; then
  MODE="capture"
  SCRIPT="$CAPTURE_SCRIPT"
elif [[ "$USE_LATEST_BASELINE" -eq 1 ]]; then
  MODE="regression"
  SCRIPT="$REGRESSION_SCRIPT"
  if [[ -z "$BASELINE_NAME" ]]; then
    BASELINE_NAME="$(slugify "$MODEL_ID")"
  fi
  BASELINE="${BASELINE_ROOT%/}/${BASELINE_NAME}/latest.tsv"
  if [[ ! -f "$BASELINE" ]]; then
    echo "Latest ONNX baseline not found: $BASELINE" >&2
    echo "Capture it first with --capture-baseline, or pass --baseline PATH." >&2
    exit 2
  fi
elif [[ -n "$BASELINE" ]]; then
  MODE="regression"
  SCRIPT="$REGRESSION_SCRIPT"
fi
if [[ -n "$BUNDLE_SUMMARY_OUT" && "$MODE" != "verify-bundle" ]]; then
  echo "--bundle-summary-out is only supported with --verify-bundle" >&2
  exit 2
fi
if [[ ( -n "$DIAGNOSIS_SUMMARY_DIR" || -n "$DIAGNOSIS_OUT" || -n "$DIAGNOSIS_STAGES_OUT" || -n "$DIAGNOSIS_CASE" || "$DIAGNOSIS_INCLUDE_WARMUP" -eq 1 ) && "$MODE" != "diagnose" ]]; then
  echo "--diagnosis-* options are only supported with --diagnose-summary" >&2
  exit 2
fi
if [[ "$DIAGNOSE_SCRIPT_OVERRIDDEN" -eq 1 && "$MODE" != "diagnose" && "$MODE" != "capture" && "$MODE" != "regression" ]]; then
  echo "--diagnose-script is only supported with --diagnose-summary, --capture-baseline, or regression modes" >&2
  exit 2
fi

declare -a PRESET_ARGS=()
if [[ "$MODE" == "diagnose" && -n "$PRESET" ]]; then
  echo "--preset is not supported with --diagnose-summary" >&2
  exit 2
fi
if [[ "$MODE" == "compare-baselines" && -n "$PRESET" ]]; then
  echo "--preset is not supported with --compare-baselines" >&2
  exit 2
fi
if [[ "$MODE" == "verify-bundle" && -n "$PRESET" ]]; then
  echo "--preset is not supported with --verify-bundle" >&2
  exit 2
fi
if [[ -n "$PRESET" ]]; then
  while IFS= read -r preset_arg; do
    PRESET_ARGS+=("$preset_arg")
  done < <(aqli_onnx_performance_preset_args "$PRESET" "$MODE")
fi

DELEGATED_ARGS=()
if [[ -n "$PRESET" && "${#PRESET_ARGS[@]}" -gt 0 ]]; then
  DELEGATED_ARGS+=("${PRESET_ARGS[@]}")
fi
if [[ "${#FORWARDED_ARGS[@]}" -gt 0 ]]; then
  DELEGATED_ARGS+=("${FORWARDED_ARGS[@]}")
fi
if [[ "$DIAGNOSE_SCRIPT_OVERRIDDEN" -eq 1 && ( "$MODE" == "capture" || "$MODE" == "regression" ) ]]; then
  DELEGATED_ARGS+=("--diagnose-script" "$DIAGNOSE_SCRIPT")
fi
if [[ "$CAPTURE_BASELINE" -eq 1 ]]; then
  DELEGATED_ARGS+=("--baseline-root" "$BASELINE_ROOT")
  if [[ -n "$BASELINE_NAME" ]]; then
    DELEGATED_ARGS+=("--name" "$BASELINE_NAME")
  fi
elif [[ "$USE_LATEST_BASELINE" -eq 1 ]]; then
  DELEGATED_ARGS+=("--baseline" "$BASELINE")
elif [[ "$MODE" == "compare-baselines" ]]; then
  DELEGATED_ARGS+=("--baseline" "$COMPARE_BASELINE" "--current" "$COMPARE_CURRENT")
elif [[ "$MODE" == "verify-bundle" ]]; then
  DELEGATED_ARGS+=("--bundle" "$VERIFY_BUNDLE")
  if [[ -n "$BUNDLE_SUMMARY_OUT" ]]; then
    DELEGATED_ARGS+=("--summary-out" "$BUNDLE_SUMMARY_OUT")
  fi
elif [[ "$MODE" == "diagnose" ]]; then
  DELEGATED_ARGS+=("--summary" "$DIAGNOSE_SUMMARY")
  if [[ -n "$DIAGNOSIS_SUMMARY_DIR" ]]; then
    DELEGATED_ARGS+=("--summary-dir" "$DIAGNOSIS_SUMMARY_DIR")
  fi
  if [[ -n "$DIAGNOSIS_OUT" ]]; then
    DELEGATED_ARGS+=("--out" "$DIAGNOSIS_OUT")
  fi
  if [[ -n "$DIAGNOSIS_STAGES_OUT" ]]; then
    DELEGATED_ARGS+=("--stages-out" "$DIAGNOSIS_STAGES_OUT")
  fi
  if [[ -n "$DIAGNOSIS_CASE" ]]; then
    DELEGATED_ARGS+=("--case" "$DIAGNOSIS_CASE")
  fi
  if [[ "$DIAGNOSIS_INCLUDE_WARMUP" -eq 1 ]]; then
    DELEGATED_ARGS+=("--include-warmup")
  fi
fi

if [[ ! -x "$SCRIPT" ]]; then
  echo "Selected ONNX performance script is not executable: $SCRIPT" >&2
  exit 2
fi

if [[ -n "$PLAN_OUT" ]]; then
  write_plan "$PLAN_OUT"
fi

echo "Aqli ONNX performance check"
echo "mode=$MODE"
echo "script=$SCRIPT"
if [[ -n "$PRESET" ]]; then
  echo "preset=$PRESET"
fi
if [[ -n "$BASELINE" ]]; then
  echo "baseline=$BASELINE"
fi
if [[ "$MODE" == "compare-baselines" ]]; then
  echo "baseline=$COMPARE_BASELINE"
  echo "current=$COMPARE_CURRENT"
fi
if [[ "$MODE" == "verify-bundle" ]]; then
  echo "bundle=$VERIFY_BUNDLE"
  if [[ -n "$BUNDLE_SUMMARY_OUT" ]]; then
    echo "bundleSummary=$BUNDLE_SUMMARY_OUT"
  fi
fi
if [[ "$MODE" == "diagnose" ]]; then
  echo "summary=$DIAGNOSE_SUMMARY"
  if [[ -n "$DIAGNOSIS_OUT" ]]; then
    echo "diagnosis=$DIAGNOSIS_OUT"
  fi
  if [[ -n "$DIAGNOSIS_STAGES_OUT" ]]; then
    echo "stages=$DIAGNOSIS_STAGES_OUT"
  fi
fi
if [[ -n "$PLAN_OUT" ]]; then
  echo "plan=$PLAN_OUT"
fi
if [[ "$DRY_RUN" -eq 1 ]]; then
  echo "dryRun=true"
  exit 0
fi

exec "$SCRIPT" "${DELEGATED_ARGS[@]}"
