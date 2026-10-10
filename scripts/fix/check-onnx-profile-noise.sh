#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: check-onnx-profile-noise.sh --aggregate SUMMARY --max-noise-percent N [options]

Check a summarized ONNX profile aggregate for unstable timing/throughput
spread. The input should contain label-mean, label-best, and label-worst rows
from summarize-onnx-profile-summary.sh.

Options:
  --aggregate PATH             Aggregate TSV summary (required)
  --max-noise-percent N        Maximum allowed best/worst spread percent (required)
  --metrics CSV                Metrics to check
  --label NAME                 Aggregate row prefix (default: measured)
  --out PATH                   Output TSV (default: summary-dir/noise.tsv)
  --summary-dir DIR            Output directory (default: temp dir)
  --help                       Show this help
USAGE
}

AGGREGATE=""
MAX_NOISE_PERCENT=""
METRICS="${AQLI_ONNX_PROFILE_NOISE_METRICS:-durationMs,generationTps,decodeTps,ttftMs,tokenLatencyMs,onnxPrefillRunMs,onnxDecodeRunMs,onnxOrtRunMs}"
LABEL="${AQLI_ONNX_PROFILE_AGGREGATE_LABEL:-measured}"
OUT=""
SUMMARY_DIR=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --aggregate) AGGREGATE="$2"; shift 2 ;;
    --aggregate=*) AGGREGATE="${1#*=}"; shift ;;
    --max-noise-percent) MAX_NOISE_PERCENT="$2"; shift 2 ;;
    --max-noise-percent=*) MAX_NOISE_PERCENT="${1#*=}"; shift ;;
    --metrics) METRICS="$2"; shift 2 ;;
    --metrics=*) METRICS="${1#*=}"; shift ;;
    --label) LABEL="$2"; shift 2 ;;
    --label=*) LABEL="${1#*=}"; shift ;;
    --out) OUT="$2"; shift 2 ;;
    --out=*) OUT="${1#*=}"; shift ;;
    --summary-dir) SUMMARY_DIR="$2"; shift 2 ;;
    --summary-dir=*) SUMMARY_DIR="${1#*=}"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$AGGREGATE" || -z "$MAX_NOISE_PERCENT" ]]; then
  echo "--aggregate and --max-noise-percent are required" >&2
  usage
  exit 2
fi
if [[ ! -f "$AGGREGATE" ]]; then
  echo "Aggregate summary not found: $AGGREGATE" >&2
  exit 2
fi
if [[ ! "$MAX_NOISE_PERCENT" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "Invalid --max-noise-percent value: $MAX_NOISE_PERCENT" >&2
  exit 2
fi
if [[ -z "$METRICS" ]]; then
  echo "--metrics must not be empty" >&2
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
  SUMMARY_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-onnx-profile-noise.XXXXXX")"
else
  mkdir -p "$SUMMARY_DIR"
fi
if [[ -z "$OUT" ]]; then
  OUT="${SUMMARY_DIR}/noise.tsv"
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
config_row aggregate "$AGGREGATE"
config_row summaryDir "$SUMMARY_DIR"
config_row output "$OUT"
config_row label "$LABEL"
config_row metrics "$METRICS"
config_row maxNoisePercent "$MAX_NOISE_PERCENT"

awk \
  -v metricsCsv="$METRICS" \
  -v label="$LABEL" \
  -v maxNoisePercent="$MAX_NOISE_PERCENT" '
  BEGIN {
    FS = OFS = "\t"
    metricCount = split(metricsCsv, metrics, ",")
    for (i = 1; i <= metricCount; i++) {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", metrics[i])
    }
    print "metric", "mean", "best", "worst", "noisePercent", "maxNoisePercent", "status", "reason"
  }
  function abs(value) {
    return value < 0 ? -value : value
  }
  function numeric(value) {
    return value ~ /^-?[0-9]+([.][0-9]+)?$/
  }
  function row_kind(caseName) {
    if (caseName == label "-mean") {
      return "mean"
    }
    if (caseName == label "-best") {
      return "best"
    }
    if (caseName == label "-worst") {
      return "worst"
    }
    return ""
  }
  function value_for(kind, metric) {
    return values[kind SUBSEP metric]
  }
  function emit(metric, meanValue, bestValue, worstValue, noise, status, reason) {
    meanValue = value_for("mean", metric)
    bestValue = value_for("best", metric)
    worstValue = value_for("worst", metric)
    reason = ""
    if (!numeric(meanValue) || !numeric(bestValue) || !numeric(worstValue)) {
      print metric, meanValue, bestValue, worstValue, "", maxNoisePercent, "skip", "missing-metric"
      return
    }
    if (meanValue + 0 == 0) {
      noise = (bestValue + 0 == worstValue + 0) ? 0 : 1000000000
      if (noise > 0) {
        reason = "mean-zero"
      }
    } else {
      noise = (abs((worstValue + 0) - (bestValue + 0)) / abs(meanValue + 0)) * 100
    }
    status = noise <= maxNoisePercent + 0 ? "pass" : "fail"
    printf "%s\t%s\t%s\t%s\t%.3f\t%s\t%s\t%s\n", metric, meanValue, bestValue, worstValue, noise, maxNoisePercent, status, reason
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      column[$i] = i
    }
    next
  }
  NF > 0 {
    kind = row_kind($column["case"])
    if (kind == "") {
      next
    }
    for (i = 1; i <= metricCount; i++) {
      metric = metrics[i]
      if (metric != "" && column[metric]) {
        values[kind SUBSEP metric] = $column[metric]
      }
    }
  }
  END {
    for (i = 1; i <= metricCount; i++) {
      metric = metrics[i]
      if (metric != "") {
        emit(metric)
      }
    }
  }
' "$AGGREGATE" > "$OUT"

FAILURES="$(awk 'BEGIN { FS = "\t" } NR > 1 && $7 == "fail" { count++ } END { print count + 0 }' "$OUT")"
SKIPS="$(awk 'BEGIN { FS = "\t" } NR > 1 && $7 == "skip" { count++ } END { print count + 0 }' "$OUT")"

{
  echo "Aqli ONNX profile noise check"
  echo "summaryDir=$SUMMARY_DIR"
  echo "artifacts.config=$CONFIG"
  echo "artifacts.noise=$OUT"
  echo
  echo "failures=$FAILURES"
  echo "skips=$SKIPS"
  if [[ "$FAILURES" -gt 0 ]]; then
    echo "noise failures:"
    awk 'BEGIN { FS = "\t" } NR > 1 && $7 == "fail" { printf "  %s mean=%s best=%s worst=%s noise=%s%% limit=%s reason=%s\n", $1, $2, $3, $4, $5, $6, $8 }' "$OUT"
  fi
} | tee "$REPORT"

if [[ "$FAILURES" -gt 0 ]]; then
  exit 42
fi
