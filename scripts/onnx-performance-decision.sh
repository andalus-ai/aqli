#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: onnx-performance-decision.sh --config CONFIG --results RESULTS --out OUT [options]

Write a compact key/value decision summary for an ONNX performance workflow.
The output is intended for CI annotations and humans who need the final status
without opening every detailed artifact.

Options:
  --config PATH          Workflow config.tsv (required)
  --results PATH         Workflow results.tsv (required)
  --out PATH             Decision TSV output (required)
  --json-out PATH        Decision JSON output (default: beside TSV)
  --comparison PATH      Row-level comparison.tsv
  --metric-summary PATH  Per-metric summary TSV
  --contracts PATH       Gate contracts TSV
  --noise PATH           Noise summary TSV
  --bundle PATH          Artifact bundle TSV
  --diagnosis PATH       Single workflow diagnosis TSV
  --current-diagnosis PATH
                         Current run diagnosis TSV
  --baseline-diagnosis PATH
                         Baseline diagnosis TSV
  --diagnosis-diff-summary PATH
                         Diagnosis comparison summary TSV
  --help                 Show this help
USAGE
}

CONFIG=""
RESULTS=""
OUT=""
JSON_OUT=""
COMPARISON=""
METRIC_SUMMARY=""
CONTRACTS=""
NOISE=""
BUNDLE=""
DIAGNOSIS=""
CURRENT_DIAGNOSIS=""
BASELINE_DIAGNOSIS=""
DIAGNOSIS_DIFF_SUMMARY=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --config) CONFIG="$2"; shift 2 ;;
    --config=*) CONFIG="${1#*=}"; shift ;;
    --results) RESULTS="$2"; shift 2 ;;
    --results=*) RESULTS="${1#*=}"; shift ;;
    --out) OUT="$2"; shift 2 ;;
    --out=*) OUT="${1#*=}"; shift ;;
    --json-out) JSON_OUT="$2"; shift 2 ;;
    --json-out=*) JSON_OUT="${1#*=}"; shift ;;
    --comparison) COMPARISON="$2"; shift 2 ;;
    --comparison=*) COMPARISON="${1#*=}"; shift ;;
    --metric-summary) METRIC_SUMMARY="$2"; shift 2 ;;
    --metric-summary=*) METRIC_SUMMARY="${1#*=}"; shift ;;
    --contracts) CONTRACTS="$2"; shift 2 ;;
    --contracts=*) CONTRACTS="${1#*=}"; shift ;;
    --noise) NOISE="$2"; shift 2 ;;
    --noise=*) NOISE="${1#*=}"; shift ;;
    --bundle) BUNDLE="$2"; shift 2 ;;
    --bundle=*) BUNDLE="${1#*=}"; shift ;;
    --diagnosis) DIAGNOSIS="$2"; shift 2 ;;
    --diagnosis=*) DIAGNOSIS="${1#*=}"; shift ;;
    --current-diagnosis) CURRENT_DIAGNOSIS="$2"; shift 2 ;;
    --current-diagnosis=*) CURRENT_DIAGNOSIS="${1#*=}"; shift ;;
    --baseline-diagnosis) BASELINE_DIAGNOSIS="$2"; shift 2 ;;
    --baseline-diagnosis=*) BASELINE_DIAGNOSIS="${1#*=}"; shift ;;
    --diagnosis-diff-summary) DIAGNOSIS_DIFF_SUMMARY="$2"; shift 2 ;;
    --diagnosis-diff-summary=*) DIAGNOSIS_DIFF_SUMMARY="${1#*=}"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$CONFIG" || -z "$RESULTS" || -z "$OUT" ]]; then
  echo "--config, --results, and --out are required" >&2
  usage
  exit 2
fi
if [[ ! -f "$CONFIG" ]]; then
  echo "Config TSV not found: $CONFIG" >&2
  exit 2
fi
if [[ ! -f "$RESULTS" ]]; then
  echo "Results TSV not found: $RESULTS" >&2
  exit 2
fi
for cmd in awk date mkdir; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

safe_tsv_field() {
  local value="${1:-}"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

config_value() {
  local key="$1"
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
  ' "$CONFIG"
}

key_value_from_file() {
  local file="$1"
  local key="$2"
  if [[ -z "$file" || ! -f "$file" ]]; then
    return 1
  fi
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

diagnosis_value() {
  key_value_from_file "$1" "$2" 2>/dev/null || true
}

result_field() {
  local stage="$1"
  local field="$2"
  awk -v stage="$stage" -v field="$field" '
    BEGIN { FS = "\t" }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        column[$i] = i
      }
      next
    }
    $1 == stage {
      if (column[field]) {
        print $(column[field])
      }
      found = 1
      exit
    }
    END { exit found ? 0 : 1 }
  ' "$RESULTS"
}

if [[ -z "$COMPARISON" ]]; then
  COMPARISON="$(result_field compare artifact 2>/dev/null || true)"
fi
if [[ -z "$METRIC_SUMMARY" && -n "$COMPARISON" ]]; then
  comparison_dir="${COMPARISON%/*}"
  if [[ "$comparison_dir" != "$COMPARISON" ]]; then
    METRIC_SUMMARY="${comparison_dir}/metric-summary.tsv"
  fi
fi
if [[ -z "$NOISE" ]]; then
  NOISE="$(config_value currentNoise 2>/dev/null || true)"
fi
if [[ -z "$CONTRACTS" ]]; then
  CONTRACTS="$(config_value contracts 2>/dev/null || true)"
fi
if [[ -z "$BUNDLE" ]]; then
  BUNDLE="$(config_value bundle 2>/dev/null || true)"
fi
if [[ -z "$DIAGNOSIS" ]]; then
  DIAGNOSIS="$(config_value diagnosis 2>/dev/null || true)"
fi
if [[ -z "$CURRENT_DIAGNOSIS" ]]; then
  CURRENT_DIAGNOSIS="$(config_value currentDiagnosis 2>/dev/null || true)"
fi
if [[ -z "$BASELINE_DIAGNOSIS" ]]; then
  BASELINE_DIAGNOSIS="$(config_value baselineDiagnosis 2>/dev/null || true)"
fi
if [[ -z "$DIAGNOSIS_DIFF_SUMMARY" ]]; then
  DIAGNOSIS_DIFF_SUMMARY="$(config_value diagnosisDiffSummary 2>/dev/null || true)"
fi

parent="${OUT%/*}"
if [[ "$parent" != "$OUT" ]]; then
  mkdir -p "$parent"
fi
if [[ -z "$JSON_OUT" ]]; then
  if [[ "$OUT" == *.tsv ]]; then
    JSON_OUT="${OUT%.tsv}.json"
  else
    JSON_OUT="${OUT}.json"
  fi
fi
json_parent="${JSON_OUT%/*}"
if [[ "$json_parent" != "$JSON_OUT" ]]; then
  mkdir -p "$json_parent"
fi
DECISION_FIELD_SEP=$'\034'

readarray_result() {
  local awk_script="$1"
  awk "$awk_script" "$RESULTS"
}

FAILED_STAGES="$(readarray_result 'BEGIN { FS = "\t" } NR > 1 && $2 == "fail" { count++ } END { print count + 0 }')"
SKIPPED_STAGES="$(readarray_result 'BEGIN { FS = "\t" } NR > 1 && $2 == "skip" { count++ } END { print count + 0 }')"
LAST_STAGE="$(readarray_result 'BEGIN { FS = "\t" } NR > 1 { stage = $1 } END { print stage }')"
FAILURE_STAGE="$(readarray_result 'BEGIN { FS = "\t" } NR > 1 && $2 == "fail" { print $1; exit }')"
FAILURE_EXIT_CODE="$(readarray_result 'BEGIN { FS = "\t" } NR > 1 && $2 == "fail" { print $3; exit }')"
FAILURE_REASON="$(readarray_result 'BEGIN { FS = "\t" } NR > 1 && $2 == "fail" { print $7; exit }')"

STATUS="pass"
if [[ "$FAILED_STAGES" -gt 0 ]]; then
  STATUS="fail"
elif [[ -z "$LAST_STAGE" ]]; then
  STATUS="incomplete"
fi

REGRESSION_SUMMARY="$(awk '
  BEGIN {
    FS = "\t"
    OFS = "\034"
    failures = 0
    skips = 0
    compared = 0
    worstMetric = ""
    worstCase = ""
    worstPercent = ""
    worstStatus = ""
  }
  function numeric(value) {
    return value ~ /^-?[0-9]+([.][0-9]+)?$/
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      column[$i] = i
    }
    next
  }
  NF > 0 {
    status = column["status"] ? $(column["status"]) : ""
    regression = column["regressionPercent"] ? $(column["regressionPercent"]) : ""
    if (status == "fail") {
      failures++
    } else if (status == "skip") {
      skips++
    }
    if (numeric(regression)) {
      compared++
      if (worstPercent == "" || regression + 0 > worstPercent + 0) {
        worstPercent = regression
        worstMetric = column["metric"] ? $(column["metric"]) : ""
        worstCase = column["case"] ? $(column["case"]) : ""
        worstStatus = status
      }
    }
  }
  END {
    print failures, skips, compared, worstMetric, worstCase, worstPercent, worstStatus
  }
' "${COMPARISON:-/dev/null}" 2>/dev/null || printf '0%s0%s0%s%s%s%s' "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP")"
IFS="$DECISION_FIELD_SEP" read -r REGRESSION_FAILURES REGRESSION_SKIPS REGRESSION_COMPARED WORST_REGRESSION_METRIC WORST_REGRESSION_CASE WORST_REGRESSION_PERCENT WORST_REGRESSION_STATUS <<< "$REGRESSION_SUMMARY"

CONTRACT_SUMMARY="$(awk '
  BEGIN {
    FS = "\t"
    OFS = "\034"
    failures = 0
    passes = 0
    skips = 0
    checked = 0
    firstFailureCase = ""
    firstFailureMetric = ""
    firstFailureReason = ""
    worstCase = ""
    worstMetric = ""
    worstActual = ""
    worstOperator = ""
    worstThreshold = ""
    worstMagnitude = ""
  }
  function numeric(value) {
    return value ~ /^-?[0-9]+([.][0-9]+)?$/
  }
  function abs(value) {
    return value < 0 ? -value : value
  }
  function contract_magnitude(actual, operator, threshold) {
    if (!numeric(actual) || !numeric(threshold)) {
      return ""
    }
    if (operator == "<=") {
      if (threshold + 0 == 0) {
        return actual + 0 == 0 ? 0 : 1000000000
      }
      return ((actual - threshold) / abs(threshold + 0)) * 100
    }
    if (operator == ">=") {
      if (threshold + 0 == 0) {
        return actual + 0 == 0 ? 0 : 1000000000
      }
      return ((threshold - actual) / abs(threshold + 0)) * 100
    }
    return ""
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      column[$i] = i
    }
    next
  }
  NF > 0 {
    status = column["status"] ? $(column["status"]) : ""
    if (status == "pass") {
      passes++
    } else if (status == "fail") {
      failures++
    } else if (status == "skip") {
      skips++
    }
    checked++
    if (status != "fail") {
      next
    }
    caseName = column["case"] ? $(column["case"]) : ""
    metric = column["metric"] ? $(column["metric"]) : ""
    actual = column["actual"] ? $(column["actual"]) : ""
    operator = column["operator"] ? $(column["operator"]) : ""
    threshold = column["threshold"] ? $(column["threshold"]) : ""
    reason = column["reason"] ? $(column["reason"]) : ""
    if (firstFailureCase == "") {
      firstFailureCase = caseName
      firstFailureMetric = metric
      firstFailureReason = reason
    }
    magnitude = contract_magnitude(actual, operator, threshold)
    if (magnitude != "" && (worstMagnitude == "" || magnitude + 0 > worstMagnitude + 0)) {
      worstMagnitude = magnitude
      worstCase = caseName
      worstMetric = metric
      worstActual = actual
      worstOperator = operator
      worstThreshold = threshold
    }
  }
  END {
    worstText = worstMagnitude == "" ? "" : sprintf("%.3f", worstMagnitude)
    print failures, passes, skips, checked, firstFailureCase, firstFailureMetric, firstFailureReason, worstCase, worstMetric, worstActual, worstOperator, worstThreshold, worstText
  }
' "${CONTRACTS:-/dev/null}" 2>/dev/null || printf '0%s0%s0%s0%s%s%s%s%s%s%s%s%s' "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP")"
IFS="$DECISION_FIELD_SEP" read -r CONTRACT_FAILURES CONTRACT_PASSES CONTRACT_SKIPS CONTRACT_CHECKS FIRST_CONTRACT_FAILURE_CASE FIRST_CONTRACT_FAILURE_METRIC FIRST_CONTRACT_FAILURE_REASON WORST_CONTRACT_CASE WORST_CONTRACT_METRIC WORST_CONTRACT_ACTUAL WORST_CONTRACT_OPERATOR WORST_CONTRACT_THRESHOLD WORST_CONTRACT_PERCENT <<< "$CONTRACT_SUMMARY"

NOISE_SUMMARY="$(awk '
  BEGIN {
    FS = "\t"
    OFS = "\034"
    failures = 0
    skips = 0
    checked = 0
    worstMetric = ""
    worstPercent = ""
    worstStatus = ""
  }
  function numeric(value) {
    return value ~ /^-?[0-9]+([.][0-9]+)?$/
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      column[$i] = i
    }
    next
  }
  NF > 0 {
    status = column["status"] ? $(column["status"]) : ""
    noise = column["noisePercent"] ? $(column["noisePercent"]) : ""
    if (status == "fail") {
      failures++
    } else if (status == "skip") {
      skips++
    }
    if (numeric(noise)) {
      checked++
      if (worstPercent == "" || noise + 0 > worstPercent + 0) {
        worstPercent = noise
        worstMetric = column["metric"] ? $(column["metric"]) : ""
        worstStatus = status
      }
    }
  }
  END {
    print failures, skips, checked, worstMetric, worstPercent, worstStatus
  }
' "${NOISE:-/dev/null}" 2>/dev/null || printf '0%s0%s0%s%s%s' "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP")"
IFS="$DECISION_FIELD_SEP" read -r NOISE_FAILURES NOISE_SKIPS NOISE_CHECKED WORST_NOISE_METRIC WORST_NOISE_PERCENT WORST_NOISE_STATUS <<< "$NOISE_SUMMARY"

BUNDLE_SUMMARY="$(if [[ -n "$BUNDLE" && -f "$BUNDLE" ]]; then
  awk '
    BEGIN {
      FS = "\t"
      OFS = "\034"
      artifacts = 0
      firstMissingKind = ""
      firstMissingPath = ""
    }
    NR == 1 {
      next
    }
    NF > 0 {
      artifacts++
      status = $4
      totals[status]++
      if ($3 == "required" && status != "present") {
        requiredMissing++
        if (firstMissingKind == "") {
          firstMissingKind = $1
          firstMissingPath = $2
        }
      }
    }
    END {
      print artifacts, totals["present"] + 0, totals["missing"] + 0, totals["blank"] + 0, requiredMissing + 0, firstMissingKind, firstMissingPath
    }
  ' "$BUNDLE"
elif [[ -n "$BUNDLE" ]]; then
  printf '0%s0%s1%s0%s1%sbundle%s%s\n' "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$BUNDLE"
else
  printf '0%s0%s0%s0%s0%s%s\n' "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP" "$DECISION_FIELD_SEP"
fi)"
IFS="$DECISION_FIELD_SEP" read -r BUNDLE_ARTIFACTS BUNDLE_PRESENT BUNDLE_MISSING BUNDLE_BLANK BUNDLE_REQUIRED_MISSING FIRST_BUNDLE_MISSING_KIND FIRST_BUNDLE_MISSING_PATH <<< "$BUNDLE_SUMMARY"

PRIMARY_DIAGNOSIS="$DIAGNOSIS"
if [[ -z "$PRIMARY_DIAGNOSIS" && -n "$CURRENT_DIAGNOSIS" ]]; then
  PRIMARY_DIAGNOSIS="$CURRENT_DIAGNOSIS"
fi
PRIMARY_STAGE="$(diagnosis_value "$PRIMARY_DIAGNOSIS" primaryStage)"
PRIMARY_METRIC="$(diagnosis_value "$PRIMARY_DIAGNOSIS" primaryMetric)"
PRIMARY_VALUE_MS="$(diagnosis_value "$PRIMARY_DIAGNOSIS" primaryValueMs)"
PRIMARY_SHARE_OF_ORT_PERCENT="$(diagnosis_value "$PRIMARY_DIAGNOSIS" primaryShareOfOrtPercent)"
PRIMARY_SHARE_OF_DURATION_PERCENT="$(diagnosis_value "$PRIMARY_DIAGNOSIS" primaryShareOfDurationPercent)"
PRIMARY_RECOMMENDATION="$(diagnosis_value "$PRIMARY_DIAGNOSIS" recommendation)"

CURRENT_PRIMARY_STAGE="$(diagnosis_value "$CURRENT_DIAGNOSIS" primaryStage)"
CURRENT_PRIMARY_METRIC="$(diagnosis_value "$CURRENT_DIAGNOSIS" primaryMetric)"
CURRENT_PRIMARY_VALUE_MS="$(diagnosis_value "$CURRENT_DIAGNOSIS" primaryValueMs)"
CURRENT_PRIMARY_SHARE_OF_ORT_PERCENT="$(diagnosis_value "$CURRENT_DIAGNOSIS" primaryShareOfOrtPercent)"
CURRENT_PRIMARY_SHARE_OF_DURATION_PERCENT="$(diagnosis_value "$CURRENT_DIAGNOSIS" primaryShareOfDurationPercent)"
CURRENT_RECOMMENDATION="$(diagnosis_value "$CURRENT_DIAGNOSIS" recommendation)"

BASELINE_PRIMARY_STAGE="$(diagnosis_value "$BASELINE_DIAGNOSIS" primaryStage)"
BASELINE_PRIMARY_METRIC="$(diagnosis_value "$BASELINE_DIAGNOSIS" primaryMetric)"
BASELINE_PRIMARY_VALUE_MS="$(diagnosis_value "$BASELINE_DIAGNOSIS" primaryValueMs)"
BASELINE_PRIMARY_SHARE_OF_ORT_PERCENT="$(diagnosis_value "$BASELINE_DIAGNOSIS" primaryShareOfOrtPercent)"
BASELINE_PRIMARY_SHARE_OF_DURATION_PERCENT="$(diagnosis_value "$BASELINE_DIAGNOSIS" primaryShareOfDurationPercent)"
BASELINE_RECOMMENDATION="$(diagnosis_value "$BASELINE_DIAGNOSIS" recommendation)"

DIAGNOSIS_STAGE_CHANGED=""
if [[ -n "$CURRENT_PRIMARY_STAGE" && -n "$BASELINE_PRIMARY_STAGE" ]]; then
  if [[ "$CURRENT_PRIMARY_STAGE" == "$BASELINE_PRIMARY_STAGE" ]]; then
    DIAGNOSIS_STAGE_CHANGED="false"
  else
    DIAGNOSIS_STAGE_CHANGED="true"
  fi
fi
DIAGNOSIS_DIFF_COMPARED_STAGES="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" comparedStages)"
DIAGNOSIS_DIFF_FASTER_STAGES="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" fasterStages)"
DIAGNOSIS_DIFF_SLOWER_STAGES="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" slowerStages)"
DIAGNOSIS_DIFF_SAME_STAGES="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" sameStages)"
DIAGNOSIS_DIFF_SKIPPED_STAGES="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" skippedStages)"
DIAGNOSIS_DIFF_PRIMARY_CHANGED="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" primaryStageChanged)"
DIAGNOSIS_DIFF_LARGEST_SLOWDOWN_STAGE="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" largestSlowdownStage)"
DIAGNOSIS_DIFF_LARGEST_SLOWDOWN_METRIC="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" largestSlowdownMetric)"
DIAGNOSIS_DIFF_LARGEST_SLOWDOWN_MS="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" largestSlowdownMs)"
DIAGNOSIS_DIFF_LARGEST_SLOWDOWN_PERCENT="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" largestSlowdownPercent)"
DIAGNOSIS_DIFF_LARGEST_SPEEDUP_STAGE="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" largestSpeedupStage)"
DIAGNOSIS_DIFF_LARGEST_SPEEDUP_METRIC="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" largestSpeedupMetric)"
DIAGNOSIS_DIFF_LARGEST_SPEEDUP_MS="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" largestSpeedupMs)"
DIAGNOSIS_DIFF_LARGEST_SPEEDUP_PERCENT="$(diagnosis_value "$DIAGNOSIS_DIFF_SUMMARY" largestSpeedupPercent)"

write_row() {
  printf '%s\t%s\n' "$(safe_tsv_field "$1")" "$(safe_tsv_field "${2:-}")"
}

{
  printf 'key\tvalue\n'
  write_row generatedAt "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  write_row status "$STATUS"
  write_row failedStages "$FAILED_STAGES"
  write_row skippedStages "$SKIPPED_STAGES"
  write_row lastStage "$LAST_STAGE"
  write_row failureStage "$FAILURE_STAGE"
  write_row failureExitCode "$FAILURE_EXIT_CODE"
  write_row failureReason "$FAILURE_REASON"
  write_row primaryStage "$PRIMARY_STAGE"
  write_row primaryMetric "$PRIMARY_METRIC"
  write_row primaryValueMs "$PRIMARY_VALUE_MS"
  write_row primaryShareOfOrtPercent "$PRIMARY_SHARE_OF_ORT_PERCENT"
  write_row primaryShareOfDurationPercent "$PRIMARY_SHARE_OF_DURATION_PERCENT"
  write_row recommendation "$PRIMARY_RECOMMENDATION"
  write_row currentPrimaryStage "$CURRENT_PRIMARY_STAGE"
  write_row currentPrimaryMetric "$CURRENT_PRIMARY_METRIC"
  write_row currentPrimaryValueMs "$CURRENT_PRIMARY_VALUE_MS"
  write_row currentPrimaryShareOfOrtPercent "$CURRENT_PRIMARY_SHARE_OF_ORT_PERCENT"
  write_row currentPrimaryShareOfDurationPercent "$CURRENT_PRIMARY_SHARE_OF_DURATION_PERCENT"
  write_row currentRecommendation "$CURRENT_RECOMMENDATION"
  write_row baselinePrimaryStage "$BASELINE_PRIMARY_STAGE"
  write_row baselinePrimaryMetric "$BASELINE_PRIMARY_METRIC"
  write_row baselinePrimaryValueMs "$BASELINE_PRIMARY_VALUE_MS"
  write_row baselinePrimaryShareOfOrtPercent "$BASELINE_PRIMARY_SHARE_OF_ORT_PERCENT"
  write_row baselinePrimaryShareOfDurationPercent "$BASELINE_PRIMARY_SHARE_OF_DURATION_PERCENT"
  write_row baselineRecommendation "$BASELINE_RECOMMENDATION"
  write_row diagnosisStageChanged "$DIAGNOSIS_STAGE_CHANGED"
  write_row diagnosisDiffComparedStages "$DIAGNOSIS_DIFF_COMPARED_STAGES"
  write_row diagnosisDiffFasterStages "$DIAGNOSIS_DIFF_FASTER_STAGES"
  write_row diagnosisDiffSlowerStages "$DIAGNOSIS_DIFF_SLOWER_STAGES"
  write_row diagnosisDiffSameStages "$DIAGNOSIS_DIFF_SAME_STAGES"
  write_row diagnosisDiffSkippedStages "$DIAGNOSIS_DIFF_SKIPPED_STAGES"
  write_row diagnosisDiffPrimaryStageChanged "$DIAGNOSIS_DIFF_PRIMARY_CHANGED"
  write_row diagnosisDiffLargestSlowdownStage "$DIAGNOSIS_DIFF_LARGEST_SLOWDOWN_STAGE"
  write_row diagnosisDiffLargestSlowdownMetric "$DIAGNOSIS_DIFF_LARGEST_SLOWDOWN_METRIC"
  write_row diagnosisDiffLargestSlowdownMs "$DIAGNOSIS_DIFF_LARGEST_SLOWDOWN_MS"
  write_row diagnosisDiffLargestSlowdownPercent "$DIAGNOSIS_DIFF_LARGEST_SLOWDOWN_PERCENT"
  write_row diagnosisDiffLargestSpeedupStage "$DIAGNOSIS_DIFF_LARGEST_SPEEDUP_STAGE"
  write_row diagnosisDiffLargestSpeedupMetric "$DIAGNOSIS_DIFF_LARGEST_SPEEDUP_METRIC"
  write_row diagnosisDiffLargestSpeedupMs "$DIAGNOSIS_DIFF_LARGEST_SPEEDUP_MS"
  write_row diagnosisDiffLargestSpeedupPercent "$DIAGNOSIS_DIFF_LARGEST_SPEEDUP_PERCENT"
  write_row regressionFailures "$REGRESSION_FAILURES"
  write_row regressionSkips "$REGRESSION_SKIPS"
  write_row regressionCompared "$REGRESSION_COMPARED"
  write_row worstRegressionMetric "$WORST_REGRESSION_METRIC"
  write_row worstRegressionCase "$WORST_REGRESSION_CASE"
  write_row worstRegressionPercent "$WORST_REGRESSION_PERCENT"
  write_row worstRegressionStatus "$WORST_REGRESSION_STATUS"
  write_row contractFailures "$CONTRACT_FAILURES"
  write_row contractPasses "$CONTRACT_PASSES"
  write_row contractSkips "$CONTRACT_SKIPS"
  write_row contractChecks "$CONTRACT_CHECKS"
  write_row firstContractFailureCase "$FIRST_CONTRACT_FAILURE_CASE"
  write_row firstContractFailureMetric "$FIRST_CONTRACT_FAILURE_METRIC"
  write_row firstContractFailureReason "$FIRST_CONTRACT_FAILURE_REASON"
  write_row worstContractCase "$WORST_CONTRACT_CASE"
  write_row worstContractMetric "$WORST_CONTRACT_METRIC"
  write_row worstContractActual "$WORST_CONTRACT_ACTUAL"
  write_row worstContractOperator "$WORST_CONTRACT_OPERATOR"
  write_row worstContractThreshold "$WORST_CONTRACT_THRESHOLD"
  write_row worstContractPercent "$WORST_CONTRACT_PERCENT"
  write_row noiseFailures "$NOISE_FAILURES"
  write_row noiseSkips "$NOISE_SKIPS"
  write_row noiseChecked "$NOISE_CHECKED"
  write_row worstNoiseMetric "$WORST_NOISE_METRIC"
  write_row worstNoisePercent "$WORST_NOISE_PERCENT"
  write_row worstNoiseStatus "$WORST_NOISE_STATUS"
  write_row bundleArtifacts "$BUNDLE_ARTIFACTS"
  write_row bundlePresent "$BUNDLE_PRESENT"
  write_row bundleMissing "$BUNDLE_MISSING"
  write_row bundleBlank "$BUNDLE_BLANK"
  write_row bundleRequiredMissing "$BUNDLE_REQUIRED_MISSING"
  write_row firstBundleMissingKind "$FIRST_BUNDLE_MISSING_KIND"
  write_row firstBundleMissingPath "$FIRST_BUNDLE_MISSING_PATH"
  write_row config "$CONFIG"
  write_row results "$RESULTS"
  write_row bundle "$BUNDLE"
  write_row diagnosis "$PRIMARY_DIAGNOSIS"
  write_row currentDiagnosis "$CURRENT_DIAGNOSIS"
  write_row baselineDiagnosis "$BASELINE_DIAGNOSIS"
  write_row diagnosisDiffSummary "$DIAGNOSIS_DIFF_SUMMARY"
  write_row comparison "$COMPARISON"
  write_row metricSummary "$METRIC_SUMMARY"
  write_row contracts "$CONTRACTS"
  write_row noise "$NOISE"
  write_row json "$JSON_OUT"
} > "$OUT"

awk '
  BEGIN {
    FS = "\t"
    print "{"
  }
  function json_escape(value) {
    gsub(/\\/, "\\\\", value)
    gsub(/"/, "\\\"", value)
    gsub(/\r/, "\\r", value)
    gsub(/\n/, "\\n", value)
    gsub(/\t/, "\\t", value)
    return value
  }
  NR > 1 {
    key = $1
    value = $0
    sub(/^[^\t]*\t/, "", value)
    printf "%s  \"%s\": \"%s\"", separator, json_escape(key), json_escape(value)
    separator = ",\n"
  }
  END {
    if (NR > 1) {
      print ""
    }
    print "}"
  }
' "$OUT" > "$JSON_OUT"
