#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: summarize-onnx-profile-summary.sh --summary SUMMARY [options]

Aggregate a bench-onnx-profile.sh summary.tsv into stable measured-mean,
measured-best, and measured-worst rows while preserving the original summary
schema. The output can be passed directly to compare-onnx-profile-summary.sh.

Options:
  --summary PATH       Input bench-onnx-profile.sh summary.tsv (required)
  --out PATH           Output aggregate TSV (default: summary-dir/aggregate.tsv)
  --summary-dir DIR    Directory for artifacts (default: temp dir)
  --label NAME         Aggregate row prefix (default: measured)
  --include-warmup     Include warmup-* rows (default: measured runs only)
  --help               Show this help

Examples:
  ./aqli/scripts/summarize-onnx-profile-summary.sh --summary ops/benchmarks/onnx/current/summary.tsv
  ./aqli/scripts/compare-onnx-profile-summary.sh --baseline baseline-aggregate.tsv --current current-aggregate.tsv
USAGE
}

SUMMARY=""
SUMMARY_DIR=""
OUT=""
LABEL="${AQLI_ONNX_PROFILE_AGGREGATE_LABEL:-measured}"
INCLUDE_WARMUP=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --summary) SUMMARY="$2"; shift 2 ;;
    --summary=*) SUMMARY="${1#*=}"; shift ;;
    --out) OUT="$2"; shift 2 ;;
    --out=*) OUT="${1#*=}"; shift ;;
    --summary-dir) SUMMARY_DIR="$2"; shift 2 ;;
    --summary-dir=*) SUMMARY_DIR="${1#*=}"; shift ;;
    --label) LABEL="$2"; shift 2 ;;
    --label=*) LABEL="${1#*=}"; shift ;;
    --include-warmup) INCLUDE_WARMUP=1; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$SUMMARY" ]]; then
  echo "--summary is required" >&2
  usage
  exit 2
fi
if [[ ! -f "$SUMMARY" ]]; then
  echo "Summary file not found: $SUMMARY" >&2
  exit 2
fi
if [[ -z "$LABEL" ]]; then
  echo "--label must not be empty" >&2
  exit 2
fi
for cmd in awk date mkdir mktemp tee; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

if [[ -z "$SUMMARY_DIR" ]]; then
  SUMMARY_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-onnx-profile-summary.XXXXXX")"
else
  mkdir -p "$SUMMARY_DIR"
fi
if [[ -z "$OUT" ]]; then
  OUT="${SUMMARY_DIR}/aggregate.tsv"
fi

CONFIG="${SUMMARY_DIR}/config.tsv"
REPORT="${SUMMARY_DIR}/report.txt"

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
config_row summary "$SUMMARY"
config_row summaryDir "$SUMMARY_DIR"
config_row output "$OUT"
config_row label "$LABEL"
config_row includeWarmup "$INCLUDE_WARMUP"

awk \
  -v includeWarmup="$INCLUDE_WARMUP" \
  -v label="$LABEL" \
  -v sourceSummary="$SUMMARY" '
  BEGIN {
    FS = OFS = "\t"
    aggregateMetrics["durationMs"] = 1
    aggregateMetrics["promptEvalTps"] = 1
    aggregateMetrics["generationTps"] = 1
    aggregateMetrics["decodeTps"] = 1
    aggregateMetrics["ttftMs"] = 1
    aggregateMetrics["tokenLatencyMs"] = 1
    aggregateMetrics["onnxTokenizeMs"] = 1
    aggregateMetrics["onnxInputPrepMs"] = 1
    aggregateMetrics["onnxPrefillRunMs"] = 1
    aggregateMetrics["onnxDecodeRunMs"] = 1
    aggregateMetrics["onnxOrtRunMs"] = 1
    aggregateMetrics["onnxLogitsSelectMs"] = 1
    aggregateMetrics["onnxSamplingMs"] = 1
    aggregateMetrics["onnxSteps"] = 1
  }
  function numeric(value) {
    return value ~ /^-?[0-9]+([.][0-9]+)?$/
  }
  function higher_is_better(metric) {
    return metric == "promptEvalTps" || metric == "generationTps" || metric == "decodeTps"
  }
  function should_use(caseName) {
    return includeWarmup == "1" || caseName !~ /^warmup-/
  }
  function update_metric(metric, value, key, numericValue) {
    key = metric
    numericValue = value + 0
    sum[key] += numericValue
    count[key]++
    if (!(key in best) || (higher_is_better(metric) ? numericValue > best[key] : numericValue < best[key])) {
      best[key] = numericValue
    }
    if (!(key in worst) || (higher_is_better(metric) ? numericValue < worst[key] : numericValue > worst[key])) {
      worst[key] = numericValue
    }
  }
  function remember_backend(value) {
    if (value == "") {
      return
    }
    if (backend == "") {
      backend = value
    } else if (backend != value) {
      backend = "mixed"
    }
  }
  function metric_value(rowKind, metric, key) {
    key = metric
    if (!(key in count) || count[key] == 0) {
      return ""
    }
    if (rowKind == "mean") {
      return sprintf("%.3f", sum[key] / count[key])
    }
    if (rowKind == "best") {
      return sprintf("%.3f", best[key])
    }
    if (rowKind == "worst") {
      return sprintf("%.3f", worst[key])
    }
    return ""
  }
  function row_value(rowKind, columnName) {
    if (columnName == "case") {
      return label "-" rowKind
    }
    if (columnName == "status") {
      return failedRows > 0 ? "fail" : "pass"
    }
    if (columnName == "exitCode") {
      return ""
    }
    if (columnName == "onnxBackend") {
      return backend
    }
    if (columnName == "combinedLog") {
      return sourceSummary
    }
    if (columnName == "stderrLog") {
      return ""
    }
    if (columnName in aggregateMetrics) {
      return metric_value(rowKind, columnName)
    }
    return ""
  }
  NR == 1 {
    headerLine = $0
    headerCount = NF
    for (i = 1; i <= NF; i++) {
      headers[i] = $i
      column[$i] = i
    }
    print headerLine
    next
  }
  NF > 0 {
    caseName = $column["case"]
    if (!should_use(caseName)) {
      skippedRows++
      next
    }
    usedRows++
    if (column["status"] && $column["status"] != "pass") {
      failedRows++
    }
    if (column["onnxBackend"]) {
      remember_backend($column["onnxBackend"])
    }
    for (i = 1; i <= NF; i++) {
      metric = headers[i]
      if ((metric in aggregateMetrics) && numeric($i)) {
        update_metric(metric, $i)
      }
    }
  }
  END {
    if (usedRows == 0) {
      print "No ONNX profile rows selected for aggregation" > "/dev/stderr"
      exit 3
    }
    for (rowIndex = 1; rowIndex <= 3; rowIndex++) {
      rowKind = rowIndex == 1 ? "mean" : (rowIndex == 2 ? "best" : "worst")
      line = ""
      for (i = 1; i <= headerCount; i++) {
        value = row_value(rowKind, headers[i])
        line = line (i == 1 ? "" : OFS) value
      }
      print line
    }
  }
' "$SUMMARY" > "$OUT"

USED_ROWS="$(awk 'BEGIN { FS = "\t" } NR > 1 { count++ } END { print count + 0 }' "$OUT")"

{
  echo "Aqli ONNX profile summary aggregation"
  echo "summaryDir=$SUMMARY_DIR"
  echo "artifacts.config=$CONFIG"
  echo "artifacts.aggregate=$OUT"
  echo "input=$SUMMARY"
  echo "rows=$USED_ROWS"
} | tee "$REPORT"
