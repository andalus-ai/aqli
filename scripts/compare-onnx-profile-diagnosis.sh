#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: compare-onnx-profile-diagnosis.sh --baseline-stages STAGES --current-stages STAGES [options]

Compare two diagnose-onnx-profile-summary.sh stages.tsv artifacts and show
which ONNX runtime stages got faster, slower, or shifted priority.

Options:
  --baseline-stages PATH  Baseline stages.tsv (required)
  --current-stages PATH   Current stages.tsv (required)
  --summary-dir DIR       Directory for artifacts (default: temp dir)
  --out PATH              Stage comparison TSV (default: summary-dir/comparison.tsv)
  --summary-out PATH      Key/value summary TSV (default: summary-dir/summary.tsv)
  --table-out PATH        Human-readable table (default: summary-dir/comparison-table.txt)
  --help                  Show this help
USAGE
}

BASELINE_STAGES=""
CURRENT_STAGES=""
SUMMARY_DIR=""
OUT=""
SUMMARY_OUT=""
TABLE_OUT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --baseline-stages) BASELINE_STAGES="$2"; shift 2 ;;
    --baseline-stages=*) BASELINE_STAGES="${1#*=}"; shift ;;
    --current-stages) CURRENT_STAGES="$2"; shift 2 ;;
    --current-stages=*) CURRENT_STAGES="${1#*=}"; shift ;;
    --summary-dir) SUMMARY_DIR="$2"; shift 2 ;;
    --summary-dir=*) SUMMARY_DIR="${1#*=}"; shift ;;
    --out) OUT="$2"; shift 2 ;;
    --out=*) OUT="${1#*=}"; shift ;;
    --summary-out) SUMMARY_OUT="$2"; shift 2 ;;
    --summary-out=*) SUMMARY_OUT="${1#*=}"; shift ;;
    --table-out) TABLE_OUT="$2"; shift 2 ;;
    --table-out=*) TABLE_OUT="${1#*=}"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$BASELINE_STAGES" || -z "$CURRENT_STAGES" ]]; then
  echo "--baseline-stages and --current-stages are required" >&2
  usage
  exit 2
fi
if [[ ! -f "$BASELINE_STAGES" ]]; then
  echo "Baseline stages not found: $BASELINE_STAGES" >&2
  exit 2
fi
if [[ ! -f "$CURRENT_STAGES" ]]; then
  echo "Current stages not found: $CURRENT_STAGES" >&2
  exit 2
fi
for cmd in awk date mkdir mktemp tee; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

if [[ -z "$SUMMARY_DIR" ]]; then
  SUMMARY_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-onnx-diagnosis-compare.XXXXXX")"
else
  mkdir -p "$SUMMARY_DIR"
fi
OUT="${OUT:-${SUMMARY_DIR}/comparison.tsv}"
SUMMARY_OUT="${SUMMARY_OUT:-${SUMMARY_DIR}/summary.tsv}"
TABLE_OUT="${TABLE_OUT:-${SUMMARY_DIR}/comparison-table.txt}"
CONFIG="${SUMMARY_DIR}/config.tsv"
REPORT="${SUMMARY_DIR}/report.txt"

for artifact in "$OUT" "$SUMMARY_OUT" "$TABLE_OUT"; do
  parent="${artifact%/*}"
  if [[ "$parent" != "$artifact" ]]; then
    mkdir -p "$parent"
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
config_row baselineStages "$BASELINE_STAGES"
config_row currentStages "$CURRENT_STAGES"
config_row summaryDir "$SUMMARY_DIR"
config_row comparison "$OUT"
config_row summary "$SUMMARY_OUT"
config_row table "$TABLE_OUT"

awk \
  -v baselineFile="$BASELINE_STAGES" \
  -v currentFile="$CURRENT_STAGES" \
  -v summaryOut="$SUMMARY_OUT" \
  -v tableOut="$TABLE_OUT" '
  BEGIN {
    FS = OFS = "\t"
    print "stage", "metric", "baselineMs", "currentMs", "deltaMs", "deltaPercent", "baselinePriority", "currentPriority", "status", "reason"
  }
  function numeric(value) {
    return value ~ /^-?[0-9]+([.][0-9]+)?$/
  }
  function key(stage) {
    return stage
  }
  function remember_order(stage) {
    if (stage == "" || seenOrder[stage]) {
      return
    }
    seenOrder[stage] = 1
    order[++orderCount] = stage
  }
  function delta_percent(baseline, current) {
    if (!numeric(baseline) || !numeric(current)) {
      return ""
    }
    if (baseline + 0 == 0) {
      return current + 0 == 0 ? 0 : 1000000000
    }
    return ((current - baseline) / baseline) * 100
  }
  function status_for(delta) {
    if (!numeric(delta)) {
      return "skip"
    }
    if (delta + 0 > 0) {
      return "slower"
    }
    if (delta + 0 < 0) {
      return "faster"
    }
    return "same"
  }
  function write_summary_row(key, value) {
    print key, value >> summaryOut
  }
  function abs(value) {
    return value < 0 ? -value : value
  }
  FILENAME == baselineFile && FNR == 1 {
    for (i = 1; i <= NF; i++) {
      bcol[$i] = i
    }
    next
  }
  FILENAME == baselineFile && FNR > 1 {
    stage = bcol["stage"] ? $(bcol["stage"]) : $1
    remember_order(stage)
    baselineMetric[stage] = bcol["metric"] ? $(bcol["metric"]) : ""
    baselineValue[stage] = bcol["valueMs"] ? $(bcol["valueMs"]) : ""
    baselinePriority[stage] = bcol["priority"] ? $(bcol["priority"]) : ""
    if (baselinePrimaryStage == "" && baselinePriority[stage] == "primary") {
      baselinePrimaryStage = stage
      baselinePrimaryMetric = baselineMetric[stage]
      baselinePrimaryValue = baselineValue[stage]
    }
    next
  }
  FILENAME == currentFile && FNR == 1 {
    for (i = 1; i <= NF; i++) {
      ccol[$i] = i
    }
    next
  }
  FILENAME == currentFile && FNR > 1 {
    stage = ccol["stage"] ? $(ccol["stage"]) : $1
    remember_order(stage)
    currentMetric[stage] = ccol["metric"] ? $(ccol["metric"]) : ""
    currentValue[stage] = ccol["valueMs"] ? $(ccol["valueMs"]) : ""
    currentPriority[stage] = ccol["priority"] ? $(ccol["priority"]) : ""
    if (currentPrimaryStage == "" && currentPriority[stage] == "primary") {
      currentPrimaryStage = stage
      currentPrimaryMetric = currentMetric[stage]
      currentPrimaryValue = currentValue[stage]
    }
    next
  }
  END {
    print "key", "value" > summaryOut
    printf "%-16s %-24s %12s %12s %12s %12s %-10s %-10s\n", "stage", "metric", "baselineMs", "currentMs", "deltaMs", "delta%", "base", "current" > tableOut
    printf "%-16s %-24s %12s %12s %12s %12s %-10s %-10s\n", "----------------", "------------------------", "------------", "------------", "------------", "------------", "----------", "----------" >> tableOut

    for (i = 1; i <= orderCount; i++) {
      stage = order[i]
      metric = currentMetric[stage] != "" ? currentMetric[stage] : baselineMetric[stage]
      b = baselineValue[stage]
      c = currentValue[stage]
      reason = ""
      if (!numeric(b) || !numeric(c)) {
        skipped++
        status = "skip"
        if (!numeric(b) && !numeric(c)) {
          reason = "missing-baseline-and-current"
        } else if (!numeric(b)) {
          reason = "missing-baseline"
        } else {
          reason = "missing-current"
        }
        delta = ""
        percent = ""
      } else {
        compared++
        delta = c - b
        percent = delta_percent(b, c)
        status = status_for(delta)
        if (status == "slower") {
          slower++
          if (largestSlowdownMs == "" || delta + 0 > largestSlowdownMs + 0) {
            largestSlowdownStage = stage
            largestSlowdownMetric = metric
            largestSlowdownMs = delta
            largestSlowdownPercent = percent
          }
        } else if (status == "faster") {
          faster++
          speedupMagnitude = abs(delta)
          if (largestSpeedupMs == "" || speedupMagnitude + 0 > largestSpeedupMs + 0) {
            largestSpeedupStage = stage
            largestSpeedupMetric = metric
            largestSpeedupMs = delta
            largestSpeedupPercent = percent
          }
        } else {
          same++
        }
      }
      print stage, metric, numeric(b) ? sprintf("%.3f", b + 0) : b, numeric(c) ? sprintf("%.3f", c + 0) : c, numeric(delta) ? sprintf("%.3f", delta + 0) : delta, numeric(percent) ? sprintf("%.3f", percent + 0) : percent, baselinePriority[stage], currentPriority[stage], status, reason
      printf "%-16s %-24s %12s %12s %12s %12s %-10s %-10s\n", stage, metric, numeric(b) ? sprintf("%.3f", b + 0) : b, numeric(c) ? sprintf("%.3f", c + 0) : c, numeric(delta) ? sprintf("%.3f", delta + 0) : delta, numeric(percent) ? sprintf("%.3f", percent + 0) : percent, baselinePriority[stage], currentPriority[stage] >> tableOut
    }

    primaryChanged = ""
    if (baselinePrimaryStage != "" && currentPrimaryStage != "") {
      primaryChanged = baselinePrimaryStage == currentPrimaryStage ? "false" : "true"
    }
    write_summary_row("comparedStages", compared + 0)
    write_summary_row("fasterStages", faster + 0)
    write_summary_row("slowerStages", slower + 0)
    write_summary_row("sameStages", same + 0)
    write_summary_row("skippedStages", skipped + 0)
    write_summary_row("baselinePrimaryStage", baselinePrimaryStage)
    write_summary_row("baselinePrimaryMetric", baselinePrimaryMetric)
    write_summary_row("baselinePrimaryValueMs", numeric(baselinePrimaryValue) ? sprintf("%.3f", baselinePrimaryValue + 0) : baselinePrimaryValue)
    write_summary_row("currentPrimaryStage", currentPrimaryStage)
    write_summary_row("currentPrimaryMetric", currentPrimaryMetric)
    write_summary_row("currentPrimaryValueMs", numeric(currentPrimaryValue) ? sprintf("%.3f", currentPrimaryValue + 0) : currentPrimaryValue)
    write_summary_row("primaryStageChanged", primaryChanged)
    write_summary_row("largestSlowdownStage", largestSlowdownStage)
    write_summary_row("largestSlowdownMetric", largestSlowdownMetric)
    write_summary_row("largestSlowdownMs", numeric(largestSlowdownMs) ? sprintf("%.3f", largestSlowdownMs + 0) : largestSlowdownMs)
    write_summary_row("largestSlowdownPercent", numeric(largestSlowdownPercent) ? sprintf("%.3f", largestSlowdownPercent + 0) : largestSlowdownPercent)
    write_summary_row("largestSpeedupStage", largestSpeedupStage)
    write_summary_row("largestSpeedupMetric", largestSpeedupMetric)
    write_summary_row("largestSpeedupMs", numeric(largestSpeedupMs) ? sprintf("%.3f", largestSpeedupMs + 0) : largestSpeedupMs)
    write_summary_row("largestSpeedupPercent", numeric(largestSpeedupPercent) ? sprintf("%.3f", largestSpeedupPercent + 0) : largestSpeedupPercent)
  }
' "$BASELINE_STAGES" "$CURRENT_STAGES" > "$OUT"

{
  echo "Aqli ONNX diagnosis comparison"
  echo "summaryDir=$SUMMARY_DIR"
  echo "artifacts.config=$CONFIG"
  echo "artifacts.comparison=$OUT"
  echo "artifacts.summary=$SUMMARY_OUT"
  echo "artifacts.table=$TABLE_OUT"
  awk 'BEGIN { FS = "\t" } NR > 1 { printf "%s=%s\n", $1, $2 }' "$SUMMARY_OUT"
} | tee "$REPORT"
