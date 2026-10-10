#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

usage() {
  cat <<'USAGE'
Usage: compare-onnx-profile-summary.sh --baseline SUMMARY --current SUMMARY [options]

Compare two bench-onnx-profile.sh summary.tsv files and fail when selected
metrics regress by more than the allowed percentage. Lower-latency metrics are
treated as better when smaller; throughput metrics are better when larger.

Options:
  --baseline PATH              Baseline summary.tsv (required)
  --current PATH               Current summary.tsv (required)
  --summary-dir DIR            Directory for artifacts (default: temp dir)
  --table-out PATH             Human-readable table artifact (default: summary-dir/comparison-table.txt)
  --metric-summary-out PATH    Per-metric summary artifact (default: summary-dir/metric-summary.tsv)
  --decision-out PATH          Decision summary artifact (default: summary-dir/decision.tsv)
  --decision-script PATH       Override decision summary helper
  --bundle-script PATH         Override artifact bundle helper
  --max-regression-percent N   Allowed regression percent (default: 10)
  --metrics CSV                Metric columns to compare (default: common ONNX profile metrics)
  --include-warmup             Include warmup-* rows (default: measured runs only)
  --fail-missing-metric        Fail when a selected metric is blank/missing
  --help                       Show this help

Examples:
  ./aqli/scripts/compare-onnx-profile-summary.sh \
    --baseline ops/benchmarks/onnx/baseline/summary.tsv \
    --current ops/benchmarks/onnx/current/summary.tsv \
    --max-regression-percent 15
USAGE
}

BASELINE=""
CURRENT=""
SUMMARY_DIR=""
TABLE_OUT=""
METRIC_SUMMARY_OUT=""
DECISION_OUT=""
DECISION_SCRIPT="${ROOT_DIR}/scripts/onnx-performance-decision.sh"
BUNDLE_SCRIPT="${ROOT_DIR}/scripts/onnx-performance-bundle.sh"
MAX_REGRESSION_PERCENT="${AQLI_ONNX_PROFILE_MAX_REGRESSION_PERCENT:-10}"
METRICS="${AQLI_ONNX_PROFILE_COMPARE_METRICS:-durationMs,generationTps,decodeTps,ttftMs,tokenLatencyMs,onnxTokenizeMs,onnxInputPrepMs,onnxPrefillRunMs,onnxDecodeRunMs,onnxOrtRunMs,onnxLogitsSelectMs,onnxSamplingMs}"
INCLUDE_WARMUP=0
FAIL_MISSING_METRIC=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --baseline) BASELINE="$2"; shift 2 ;;
    --baseline=*) BASELINE="${1#*=}"; shift ;;
    --current) CURRENT="$2"; shift 2 ;;
    --current=*) CURRENT="${1#*=}"; shift ;;
    --summary-dir) SUMMARY_DIR="$2"; shift 2 ;;
    --summary-dir=*) SUMMARY_DIR="${1#*=}"; shift ;;
    --table-out) TABLE_OUT="$2"; shift 2 ;;
    --table-out=*) TABLE_OUT="${1#*=}"; shift ;;
    --metric-summary-out) METRIC_SUMMARY_OUT="$2"; shift 2 ;;
    --metric-summary-out=*) METRIC_SUMMARY_OUT="${1#*=}"; shift ;;
    --decision-out) DECISION_OUT="$2"; shift 2 ;;
    --decision-out=*) DECISION_OUT="${1#*=}"; shift ;;
    --decision-script) DECISION_SCRIPT="$2"; shift 2 ;;
    --decision-script=*) DECISION_SCRIPT="${1#*=}"; shift ;;
    --bundle-script) BUNDLE_SCRIPT="$2"; shift 2 ;;
    --bundle-script=*) BUNDLE_SCRIPT="${1#*=}"; shift ;;
    --max-regression-percent) MAX_REGRESSION_PERCENT="$2"; shift 2 ;;
    --max-regression-percent=*) MAX_REGRESSION_PERCENT="${1#*=}"; shift ;;
    --metrics) METRICS="$2"; shift 2 ;;
    --metrics=*) METRICS="${1#*=}"; shift ;;
    --include-warmup) INCLUDE_WARMUP=1; shift ;;
    --fail-missing-metric) FAIL_MISSING_METRIC=1; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$BASELINE" || -z "$CURRENT" ]]; then
  echo "--baseline and --current are required" >&2
  usage
  exit 2
fi
if [[ ! -f "$BASELINE" ]]; then
  echo "Baseline summary not found: $BASELINE" >&2
  exit 2
fi
if [[ ! -f "$CURRENT" ]]; then
  echo "Current summary not found: $CURRENT" >&2
  exit 2
fi
if [[ ! "$MAX_REGRESSION_PERCENT" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "Invalid --max-regression-percent value: $MAX_REGRESSION_PERCENT" >&2
  exit 2
fi
if [[ -z "$METRICS" ]]; then
  echo "--metrics must not be empty" >&2
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
for cmd in awk date mkdir mktemp tee; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

# shellcheck source=/dev/null
source "$BUNDLE_SCRIPT"

if [[ -z "$SUMMARY_DIR" ]]; then
  SUMMARY_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-onnx-profile-compare.XXXXXX")"
else
  mkdir -p "$SUMMARY_DIR"
fi

CONFIG="${SUMMARY_DIR}/config.tsv"
RESULTS="${SUMMARY_DIR}/results.tsv"
COMPARISON="${SUMMARY_DIR}/comparison.tsv"
TABLE="${TABLE_OUT:-${SUMMARY_DIR}/comparison-table.txt}"
METRIC_SUMMARY="${METRIC_SUMMARY_OUT:-${SUMMARY_DIR}/metric-summary.tsv}"
DECISION="${DECISION_OUT:-${SUMMARY_DIR}/decision.tsv}"
BUNDLE="${SUMMARY_DIR}/bundle.tsv"
BUNDLE_JSON="${SUMMARY_DIR}/bundle.json"
if [[ "$DECISION" == *.tsv ]]; then
  DECISION_JSON="${DECISION%.tsv}.json"
else
  DECISION_JSON="${DECISION}.json"
fi
REPORT="${SUMMARY_DIR}/report.txt"

if [[ -n "$TABLE_OUT" ]]; then
  table_parent="${TABLE%/*}"
  if [[ "$table_parent" != "$TABLE" ]]; then
    mkdir -p "$table_parent"
  fi
fi
if [[ -n "$METRIC_SUMMARY_OUT" ]]; then
  metric_summary_parent="${METRIC_SUMMARY%/*}"
  if [[ "$metric_summary_parent" != "$METRIC_SUMMARY" ]]; then
    mkdir -p "$metric_summary_parent"
  fi
fi
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

{
  printf 'key\tvalue\n'
} > "$CONFIG"
config_row generatedAt "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
config_row baseline "$BASELINE"
config_row current "$CURRENT"
config_row summaryDir "$SUMMARY_DIR"
config_row results "$RESULTS"
config_row comparison "$COMPARISON"
config_row table "$TABLE"
config_row metricSummary "$METRIC_SUMMARY"
config_row decision "$DECISION"
config_row decisionJson "$DECISION_JSON"
config_row decisionScript "$DECISION_SCRIPT"
config_row bundle "$BUNDLE"
config_row bundleJson "$BUNDLE_JSON"
config_row bundleScript "$BUNDLE_SCRIPT"
config_row rowOrder "current-summary"
config_row maxRegressionPercent "$MAX_REGRESSION_PERCENT"
config_row metrics "$METRICS"
config_row includeWarmup "$INCLUDE_WARMUP"
config_row failMissingMetric "$FAIL_MISSING_METRIC"

write_compare_bundle() {
  aqli_onnx_performance_bundle_init "$BUNDLE"
  aqli_onnx_performance_bundle_add "$BUNDLE" config "$CONFIG" required "Comparison configuration"
  aqli_onnx_performance_bundle_add "$BUNDLE" results "$RESULTS" required "Stage results"
  aqli_onnx_performance_bundle_add "$BUNDLE" comparison "$COMPARISON" required "Row-level baseline comparison"
  aqli_onnx_performance_bundle_add "$BUNDLE" comparisonTable "$TABLE" optional "Human-readable baseline comparison"
  aqli_onnx_performance_bundle_add "$BUNDLE" metricSummary "$METRIC_SUMMARY" optional "Per-metric comparison summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" decision "$DECISION" required "Comparison decision summary"
  aqli_onnx_performance_bundle_add "$BUNDLE" decisionJson "$DECISION_JSON" optional "Comparison decision JSON"
  aqli_onnx_performance_bundle_write_json "$BUNDLE" "$BUNDLE_JSON"
}

write_compare_decision() {
  "$DECISION_SCRIPT" \
    --config "$CONFIG" \
    --results "$RESULTS" \
    --out "$DECISION" \
    --json-out "$DECISION_JSON" \
    --comparison "$COMPARISON" \
    --metric-summary "$METRIC_SUMMARY" \
    --bundle "$BUNDLE" >/dev/null
}

awk \
  -v metricsCsv="$METRICS" \
  -v maxRegressionPercent="$MAX_REGRESSION_PERCENT" \
  -v includeWarmup="$INCLUDE_WARMUP" \
  -v failMissingMetric="$FAIL_MISSING_METRIC" '
  BEGIN {
    FS = OFS = "\t"
    metricCount = split(metricsCsv, metrics, ",")
    for (i = 1; i <= metricCount; i++) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", metrics[i])
      selected[metrics[i]] = 1
    }
    print "case", "metric", "baseline", "current", "direction", "regressionPercent", "maxRegressionPercent", "status", "reason"
  }
  function numeric(value) {
    return value ~ /^-?[0-9]+([.][0-9]+)?$/
  }
  function direction_for(metric) {
    if (metric == "promptEvalTps" || metric == "generationTps" || metric == "decodeTps") {
      return "higher-is-better"
    }
    return "lower-is-better"
  }
  function should_compare_case(caseName) {
    return includeWarmup == "1" || caseName !~ /^warmup-/
  }
  function key(caseName, metric) {
    return caseName SUBSEP metric
  }
  function regression_percent(metric, baselineValue, currentValue, direction) {
    direction = direction_for(metric)
    if (baselineValue + 0 == 0) {
      if (currentValue + 0 == 0) {
        return 0
      }
      return 1000000000
    }
    if (direction == "higher-is-better") {
      return ((baselineValue - currentValue) / baselineValue) * 100
    }
    return ((currentValue - baselineValue) / baselineValue) * 100
  }
  function compare(caseName, metric, baselineValue, currentValue, direction, regression, status, reason) {
    direction = direction_for(metric)
    reason = ""
    if (!numeric(baselineValue) || !numeric(currentValue)) {
      if (failMissingMetric == "1") {
        print caseName, metric, baselineValue, currentValue, direction, "", maxRegressionPercent, "fail", "missing-metric"
      } else {
        print caseName, metric, baselineValue, currentValue, direction, "", maxRegressionPercent, "skip", "missing-metric"
      }
      return
    }
    regression = regression_percent(metric, baselineValue + 0, currentValue + 0)
    status = regression <= maxRegressionPercent + 0 ? "pass" : "fail"
    if (baselineValue + 0 == 0 && currentValue + 0 != 0) {
      reason = "baseline-zero"
    }
    printf "%s\t%s\t%s\t%s\t%s\t%.3f\t%s\t%s\t%s\n", caseName, metric, baselineValue, currentValue, direction, regression, maxRegressionPercent, status, reason
  }
  FNR == 1 {
    fileIndex++
    delete column
    for (i = 1; i <= NF; i++) {
      column[$i] = i
    }
    next
  }
  fileIndex == 1 && NF > 0 {
    caseName = $column["case"]
    if (!should_compare_case(caseName)) {
      next
    }
    baselineCases[caseName] = 1
    for (i = 1; i <= metricCount; i++) {
      metric = metrics[i]
      if (metric == "") {
        continue
      }
      baseline[key(caseName, metric)] = column[metric] ? $column[metric] : ""
    }
    next
  }
  fileIndex == 2 && NF > 0 {
    caseName = $column["case"]
    if (!should_compare_case(caseName)) {
      next
    }
    if (!(caseName in currentCases)) {
      currentCaseOrder[++currentCaseCount] = caseName
    }
    currentCases[caseName] = 1
    for (i = 1; i <= metricCount; i++) {
      metric = metrics[i]
      if (metric == "") {
        continue
      }
      current[key(caseName, metric)] = column[metric] ? $column[metric] : ""
    }
    next
  }
  END {
    for (caseIndex = 1; caseIndex <= currentCaseCount; caseIndex++) {
      caseName = currentCaseOrder[caseIndex]
      if (!(caseName in baselineCases)) {
        for (i = 1; i <= metricCount; i++) {
          metric = metrics[i]
          if (metric != "") {
            missingStatus = (failMissingMetric == "1") ? "fail" : "skip"
            print caseName, metric, "", current[key(caseName, metric)], direction_for(metric), "", maxRegressionPercent, missingStatus, "missing-baseline-case"
          }
        }
        continue
      }
      for (i = 1; i <= metricCount; i++) {
        metric = metrics[i]
        if (metric != "") {
          compare(caseName, metric, baseline[key(caseName, metric)], current[key(caseName, metric)])
        }
      }
    }
  }
' "$BASELINE" "$CURRENT" > "$COMPARISON"

awk '
  BEGIN {
    FS = "\t"
    source[1] = "case"; label[1] = "case"
    source[2] = "metric"; label[2] = "metric"
    source[3] = "baseline"; label[3] = "baseline"
    source[4] = "current"; label[4] = "current"
    source[5] = "regressionPercent"; label[5] = "regression%"
    source[6] = "maxRegressionPercent"; label[6] = "limit%"
    source[7] = "status"; label[7] = "status"
    source[8] = "reason"; label[8] = "reason"
    selectedCount = 8
    for (i = 1; i <= selectedCount; i++) {
      width[i] = length(label[i])
    }
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      column[$i] = i
    }
    next
  }
  {
    rowCount++
    for (i = 1; i <= selectedCount; i++) {
      value = column[source[i]] ? $(column[source[i]]) : ""
      cell[rowCount, i] = value
      if (length(value) > width[i]) {
        width[i] = length(value)
      }
    }
  }
  function emit_value(value, columnIndex) {
    printf "%-*s%s", width[columnIndex], value, (columnIndex == selectedCount ? "\n" : "  ")
  }
  function emit_separator(columnIndex, j) {
    for (j = 1; j <= width[columnIndex]; j++) {
      printf "-"
    }
    printf "%s", (columnIndex == selectedCount ? "\n" : "  ")
  }
  END {
    for (i = 1; i <= selectedCount; i++) {
      emit_value(label[i], i)
    }
    for (i = 1; i <= selectedCount; i++) {
      emit_separator(i)
    }
    for (row = 1; row <= rowCount; row++) {
      for (i = 1; i <= selectedCount; i++) {
        emit_value(cell[row, i], i)
      }
    }
  }
' "$COMPARISON" > "$TABLE"

awk \
  -v metricsCsv="$METRICS" \
  -v maxRegressionPercent="$MAX_REGRESSION_PERCENT" '
  BEGIN {
    FS = OFS = "\t"
    metricCount = split(metricsCsv, metrics, ",")
    for (i = 1; i <= metricCount; i++) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", metrics[i])
    }
    print "metric", "cases", "compared", "passes", "failures", "skips", "worstCase", "worstRegressionPercent", "maxRegressionPercent", "status"
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
  {
    metric = $column["metric"]
    caseName = $column["case"]
    status = $column["status"]
    regression = $column["regressionPercent"]
    cases[metric]++
    if (status == "pass") {
      passes[metric]++
    } else if (status == "fail") {
      failures[metric]++
    } else if (status == "skip") {
      skips[metric]++
    }
    if (numeric(regression)) {
      compared[metric]++
      if (!(metric in hasWorst) || regression + 0 > worstRegression[metric]) {
        hasWorst[metric] = 1
        worstRegression[metric] = regression + 0
        worstCase[metric] = caseName
      }
    }
  }
  END {
    for (i = 1; i <= metricCount; i++) {
      metric = metrics[i]
      if (metric == "") {
        continue
      }
      metricStatus = "pass"
      if (failures[metric] + 0 > 0) {
        metricStatus = "fail"
      } else if (compared[metric] + 0 == 0 && skips[metric] + 0 > 0) {
        metricStatus = "skip"
      } else if (cases[metric] + 0 == 0) {
        metricStatus = "missing"
      }
      worstText = hasWorst[metric] ? sprintf("%.3f", worstRegression[metric]) : ""
      printf "%s\t%d\t%d\t%d\t%d\t%d\t%s\t%s\t%s\t%s\n", metric, cases[metric] + 0, compared[metric] + 0, passes[metric] + 0, failures[metric] + 0, skips[metric] + 0, worstCase[metric], worstText, maxRegressionPercent, metricStatus
    }
  }
' "$COMPARISON" > "$METRIC_SUMMARY"

FAILURES="$(awk 'BEGIN { FS = "\t" } NR > 1 && $8 == "fail" { count++ } END { print count + 0 }' "$COMPARISON")"
SKIPS="$(awk 'BEGIN { FS = "\t" } NR > 1 && $8 == "skip" { count++ } END { print count + 0 }' "$COMPARISON")"

{
  printf 'stage\tstatus\texitCode\tartifact\tstdout\tstderr\treason\n'
  if [[ "$FAILURES" -gt 0 ]]; then
    printf 'compare\tfail\t42\t%s\t\t\tregression-detected\n' "$(safe_tsv_field "$COMPARISON")"
  else
    printf 'compare\tpass\t0\t%s\t\t\t\n' "$(safe_tsv_field "$COMPARISON")"
  fi
} > "$RESULTS"

write_compare_decision
write_compare_bundle
write_compare_decision

{
  echo "Aqli ONNX profile baseline comparison"
  echo "summaryDir=$SUMMARY_DIR"
  echo "artifacts.config=$CONFIG"
  echo "artifacts.results=$RESULTS"
  echo "artifacts.comparison=$COMPARISON"
  echo "artifacts.table=$TABLE"
  echo "artifacts.metricSummary=$METRIC_SUMMARY"
  echo "artifacts.decision=$DECISION"
  echo "artifacts.decisionJson=$DECISION_JSON"
  echo "artifacts.bundle=$BUNDLE"
  echo "artifacts.bundleJson=$BUNDLE_JSON"
  echo
  echo "failures=$FAILURES"
  echo "skips=$SKIPS"
  if [[ "$FAILURES" -gt 0 ]]; then
    echo "failingMetrics:"
    awk 'BEGIN { FS = "\t" } NR > 1 && $10 == "fail" { printf "  %s failures=%s worst=%s%% worstCase=%s\n", $1, $5, $8, $7 }' "$METRIC_SUMMARY"
    echo "regressions:"
    awk 'BEGIN { FS = "\t" } NR > 1 && $8 == "fail" { printf "  %s %s baseline=%s current=%s regression=%s%% limit=%s reason=%s\n", $1, $2, $3, $4, $6, $7, $9 }' "$COMPARISON"
  fi
} | tee "$REPORT"

if [[ "$FAILURES" -gt 0 ]]; then
  exit 42
fi
