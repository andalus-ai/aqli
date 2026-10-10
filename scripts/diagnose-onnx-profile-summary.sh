#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: diagnose-onnx-profile-summary.sh --summary SUMMARY [options]

Analyze an ONNX profile summary.tsv or aggregate.tsv and identify the dominant
runtime stage. This turns benchmark timing columns into a small action-oriented
diagnosis for performance work.

Options:
  --summary PATH       Input ONNX profile summary.tsv or aggregate.tsv (required)
  --summary-dir DIR    Directory for artifacts (default: temp dir)
  --out PATH           Diagnosis key/value TSV (default: summary-dir/diagnosis.tsv)
  --stages-out PATH    Stage ranking TSV (default: summary-dir/stages.tsv)
  --case NAME          Diagnose a specific case row
  --include-warmup     Include warmup-* rows when averaging raw summaries
  --help               Show this help
USAGE
}

SUMMARY=""
SUMMARY_DIR=""
OUT=""
STAGES_OUT=""
CASE_NAME=""
INCLUDE_WARMUP=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --summary) SUMMARY="$2"; shift 2 ;;
    --summary=*) SUMMARY="${1#*=}"; shift ;;
    --summary-dir) SUMMARY_DIR="$2"; shift 2 ;;
    --summary-dir=*) SUMMARY_DIR="${1#*=}"; shift ;;
    --out) OUT="$2"; shift 2 ;;
    --out=*) OUT="${1#*=}"; shift ;;
    --stages-out) STAGES_OUT="$2"; shift 2 ;;
    --stages-out=*) STAGES_OUT="${1#*=}"; shift ;;
    --case) CASE_NAME="$2"; shift 2 ;;
    --case=*) CASE_NAME="${1#*=}"; shift ;;
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
for cmd in awk date mkdir mktemp tee; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

if [[ -z "$SUMMARY_DIR" ]]; then
  SUMMARY_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-onnx-profile-diagnosis.XXXXXX")"
else
  mkdir -p "$SUMMARY_DIR"
fi
OUT="${OUT:-${SUMMARY_DIR}/diagnosis.tsv}"
STAGES_OUT="${STAGES_OUT:-${SUMMARY_DIR}/stages.tsv}"
CONFIG="${SUMMARY_DIR}/config.tsv"
REPORT="${SUMMARY_DIR}/report.txt"

for artifact in "$OUT" "$STAGES_OUT"; do
  artifact_parent="${artifact%/*}"
  if [[ "$artifact_parent" != "$artifact" ]]; then
    mkdir -p "$artifact_parent"
  fi
done

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
config_row diagnosis "$OUT"
config_row stages "$STAGES_OUT"
config_row case "$CASE_NAME"
config_row includeWarmup "$INCLUDE_WARMUP"

awk \
  -v caseName="$CASE_NAME" \
  -v includeWarmup="$INCLUDE_WARMUP" \
  -v stagesOut="$STAGES_OUT" \
  -v diagnosisOut="$OUT" '
  BEGIN {
    FS = OFS = "\t"
    metricCount = 8
    primaryMetricCount = 6
    stageName[1] = "tokenize"; metricName[1] = "onnxTokenizeMs"
    stageName[2] = "inputPrep"; metricName[2] = "onnxInputPrepMs"
    stageName[3] = "prefillRun"; metricName[3] = "onnxPrefillRunMs"
    stageName[4] = "decodeRun"; metricName[4] = "onnxDecodeRunMs"
    stageName[5] = "logitsSelect"; metricName[5] = "onnxLogitsSelectMs"
    stageName[6] = "sampling"; metricName[6] = "onnxSamplingMs"
    stageName[7] = "ttft"; metricName[7] = "ttftMs"
    stageName[8] = "tokenLatency"; metricName[8] = "tokenLatencyMs"
    print "stage", "metric", "valueMs", "shareOfOrtPercent", "shareOfDurationPercent", "priority", "recommendation" > stagesOut
  }
  function numeric(value) {
    return value ~ /^-?[0-9]+([.][0-9]+)?$/
  }
  function should_use(rowCase) {
    return includeWarmup == "1" || rowCase !~ /^warmup-/
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
  function add_value(metric, value) {
    if (numeric(value)) {
      sum[metric] += value + 0
      count[metric]++
    }
  }
  function capture_row(rowCase, i, metric) {
    selectedCase = rowCase
    selectedRows = 1
    for (i = 1; i <= metricCount; i++) {
      metric = metricName[i]
      selected[metric] = column[metric] ? $(column[metric]) : ""
    }
    selected["durationMs"] = column["durationMs"] ? $(column["durationMs"]) : ""
    selected["onnxOrtRunMs"] = column["onnxOrtRunMs"] ? $(column["onnxOrtRunMs"]) : ""
    selectedBackend = column["onnxBackend"] ? $(column["onnxBackend"]) : ""
  }
  function mean_value(metric) {
    return count[metric] > 0 ? sprintf("%.3f", sum[metric] / count[metric]) : ""
  }
  function recommendation(stage) {
    if (stage == "decodeRun") {
      return "Focus decode loop, KV cache feeds, provider placement, and tensor reuse."
    }
    if (stage == "prefillRun") {
      return "Focus prefill graph execution, prompt length, session warmup, and provider coverage."
    }
    if (stage == "tokenize") {
      return "Focus tokenizer/template caching and prompt construction overhead."
    }
    if (stage == "inputPrep") {
      return "Focus input tensor allocation, feed-map reuse, and shape preparation."
    }
    if (stage == "logitsSelect") {
      return "Focus logits extraction, vocabulary scan, and top-k/top-p preprocessing."
    }
    if (stage == "sampling") {
      return "Focus sampler allocation, random state reuse, and candidate filtering."
    }
    if (stage == "ttft") {
      return "Focus first-token latency: model load, session warmup, prefill, and tokenizer setup."
    }
    if (stage == "tokenLatency") {
      return "Focus per-token latency: decode run, sampling, logits extraction, and cache reuse."
    }
    return "Inspect detailed profile logs for this stage."
  }
  function write_diag_row(key, value) {
    print key, value >> diagnosisOut
  }
  function percent(value, denominator) {
    if (!numeric(value) || !numeric(denominator) || denominator + 0 == 0) {
      return ""
    }
    return sprintf("%.3f", (value + 0) * 100 / (denominator + 0))
  }
  function stage_priority(value, bestValue) {
    if (!numeric(value) || !numeric(bestValue) || bestValue + 0 == 0) {
      return "normal"
    }
    if (value + 0 >= bestValue * 0.90) {
      return "primary"
    }
    if (value + 0 >= bestValue * 0.50) {
      return "secondary"
    }
    return "normal"
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      column[$i] = i
    }
    next
  }
  NF > 0 {
    rowCase = column["case"] ? $(column["case"]) : ""
    if (caseName != "" && rowCase == caseName) {
      capture_row(rowCase)
      foundRequested = 1
      next
    }
    if (caseName == "" && selectedCase == "" && rowCase ~ /-mean$/) {
      capture_row(rowCase)
      foundAggregate = 1
    }
    if (caseName == "" && should_use(rowCase)) {
      rawRows++
      remember_backend(column["onnxBackend"] ? $(column["onnxBackend"]) : "")
      add_value("durationMs", column["durationMs"] ? $(column["durationMs"]) : "")
      add_value("onnxOrtRunMs", column["onnxOrtRunMs"] ? $(column["onnxOrtRunMs"]) : "")
      for (i = 1; i <= metricCount; i++) {
        add_value(metricName[i], column[metricName[i]] ? $(column[metricName[i]]) : "")
      }
    }
  }
  END {
    if (caseName != "" && !foundRequested) {
      print "Requested case not found: " caseName > "/dev/stderr"
      exit 3
    }
    if (selectedCase == "" && rawRows == 0) {
      print "No ONNX profile rows selected for diagnosis" > "/dev/stderr"
      exit 3
    }
    if (selectedCase == "") {
      selectedCase = "mean"
      selectedRows = rawRows
      selectedBackend = backend
      selected["durationMs"] = mean_value("durationMs")
      selected["onnxOrtRunMs"] = mean_value("onnxOrtRunMs")
      for (i = 1; i <= metricCount; i++) {
        selected[metricName[i]] = mean_value(metricName[i])
      }
    }
    duration = selected["durationMs"]
    ort = selected["onnxOrtRunMs"]
    bestStage = ""
    bestMetric = ""
    bestValue = ""
    for (i = 1; i <= primaryMetricCount; i++) {
      metric = metricName[i]
      value = selected[metric]
      if (numeric(value) && (bestValue == "" || value + 0 > bestValue + 0)) {
        bestStage = stageName[i]
        bestMetric = metric
        bestValue = value
      }
    }
    if (bestStage == "") {
      print "No numeric ONNX profile metrics found for diagnosis" > "/dev/stderr"
      exit 3
    }
    for (i = 1; i <= metricCount; i++) {
      metric = metricName[i]
      value = selected[metric]
      if (!numeric(value)) {
        continue
      }
      priority = i <= primaryMetricCount ? stage_priority(value + 0, bestValue + 0) : "context"
      print stageName[i], metric, sprintf("%.3f", value + 0), percent(value, ort), percent(value, duration), priority, recommendation(stageName[i]) >> stagesOut
    }
    print "key", "value" > diagnosisOut
    write_diag_row("status", "pass")
    write_diag_row("selectedCase", selectedCase)
    write_diag_row("selectedRows", selectedRows)
    write_diag_row("backend", selectedBackend)
    write_diag_row("durationMs", duration)
    write_diag_row("onnxOrtRunMs", ort)
    write_diag_row("primaryStage", bestStage)
    write_diag_row("primaryMetric", bestMetric)
    write_diag_row("primaryValueMs", sprintf("%.3f", bestValue + 0))
    write_diag_row("primaryShareOfOrtPercent", percent(bestValue, ort))
    write_diag_row("primaryShareOfDurationPercent", percent(bestValue, duration))
    write_diag_row("recommendation", recommendation(bestStage))
  }
' "$SUMMARY"

PRIMARY_STAGE="$(awk 'BEGIN { FS = "\t" } $1 == "primaryStage" { print $2; exit }' "$OUT")"
PRIMARY_METRIC="$(awk 'BEGIN { FS = "\t" } $1 == "primaryMetric" { print $2; exit }' "$OUT")"
PRIMARY_VALUE="$(awk 'BEGIN { FS = "\t" } $1 == "primaryValueMs" { print $2; exit }' "$OUT")"
RECOMMENDATION="$(awk 'BEGIN { FS = "\t" } $1 == "recommendation" { value = $0; sub(/^[^\t]*\t/, "", value); print value; exit }' "$OUT")"

{
  echo "Aqli ONNX profile diagnosis"
  echo "summaryDir=$SUMMARY_DIR"
  echo "artifacts.config=$CONFIG"
  echo "artifacts.diagnosis=$OUT"
  echo "artifacts.stages=$STAGES_OUT"
  echo "primaryStage=$PRIMARY_STAGE"
  echo "primaryMetric=$PRIMARY_METRIC"
  echo "primaryValueMs=$PRIMARY_VALUE"
  echo "recommendation=$RECOMMENDATION"
} | tee "$REPORT"
