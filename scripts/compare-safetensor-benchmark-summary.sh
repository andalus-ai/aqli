#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: compare-safetensor-benchmark-summary.sh --baseline SUMMARY --current SUMMARY [options]

Compare two bench-safetensor-inference.sh summary.json artifacts, with
Gemma4-friendly FFN strategy context for fused-vs-row-prefill A/B runs.
By default this is diagnostic-only; pass a regression threshold to make it a
failing gate.

Options:
  --baseline PATH       Baseline run directory or summary.json file (required)
  --current PATH        Current run directory or summary.json file (required)
  --case CASE           Compare one case id (default: all common cases)
  --summary-dir DIR     Directory for artifacts (default: temp dir)
  --out PATH            Metric comparison TSV (default: summary-dir/comparison.tsv)
  --summary-out PATH    Key/value summary TSV (default: summary-dir/summary.tsv)
  --table-out PATH      Human-readable table (default: summary-dir/comparison-table.txt)
  --max-regression-percent N
                        Fail when a gated metric regresses by more than N percent
  --max-regression-ms N Fail when a gated latency metric regresses by more than N ms
  --min-baseline-value N
                        Ignore percent gate below this baseline value (default: 1)
  --gate-metrics CSV    Metrics to gate (default: latency metrics; use all for all metrics)
  --help                Show this help

Examples:
  ./scripts/compare-safetensor-benchmark-summary.sh \
    --baseline ops/benchmarks/safetensor/fused \
    --current ops/benchmarks/safetensor/row-prefill \
    --case metal-deterministic
USAGE
}

BASELINE=""
CURRENT=""
CASE_NAME=""
SUMMARY_DIR=""
OUT=""
SUMMARY_OUT=""
TABLE_OUT=""
MAX_REGRESSION_PERCENT=""
MAX_REGRESSION_MS=""
MIN_BASELINE_VALUE="1"
GATE_METRICS=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --baseline) BASELINE="$2"; shift 2 ;;
    --baseline=*) BASELINE="${1#*=}"; shift ;;
    --current) CURRENT="$2"; shift 2 ;;
    --current=*) CURRENT="${1#*=}"; shift ;;
    --case) CASE_NAME="$2"; shift 2 ;;
    --case=*) CASE_NAME="${1#*=}"; shift ;;
    --summary-dir) SUMMARY_DIR="$2"; shift 2 ;;
    --summary-dir=*) SUMMARY_DIR="${1#*=}"; shift ;;
    --out) OUT="$2"; shift 2 ;;
    --out=*) OUT="${1#*=}"; shift ;;
    --summary-out) SUMMARY_OUT="$2"; shift 2 ;;
    --summary-out=*) SUMMARY_OUT="${1#*=}"; shift ;;
    --table-out) TABLE_OUT="$2"; shift 2 ;;
    --table-out=*) TABLE_OUT="${1#*=}"; shift ;;
    --max-regression-percent) MAX_REGRESSION_PERCENT="$2"; shift 2 ;;
    --max-regression-percent=*) MAX_REGRESSION_PERCENT="${1#*=}"; shift ;;
    --max-regression-ms) MAX_REGRESSION_MS="$2"; shift 2 ;;
    --max-regression-ms=*) MAX_REGRESSION_MS="${1#*=}"; shift ;;
    --min-baseline-value) MIN_BASELINE_VALUE="$2"; shift 2 ;;
    --min-baseline-value=*) MIN_BASELINE_VALUE="${1#*=}"; shift ;;
    --gate-metrics) GATE_METRICS="$2"; shift 2 ;;
    --gate-metrics=*) GATE_METRICS="${1#*=}"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$BASELINE" || -z "$CURRENT" ]]; then
  echo "--baseline and --current are required" >&2
  usage
  exit 2
fi
for value_name in MAX_REGRESSION_PERCENT MAX_REGRESSION_MS MIN_BASELINE_VALUE; do
  value="${!value_name}"
  if [[ -n "$value" && ! "$value" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "Invalid ${value_name} value: $value" >&2
    exit 2
  fi
done

known_metric() {
  case "$1" in
    durationMs|speedTps|chunks|ttftMs|topStageMs|prefillMs|decodeMs|tpotMs|samplingMs|argmaxMs|attentionMs|ffnMs|logitsMs) return 0 ;;
    *) return 1 ;;
  esac
}

validate_gate_metrics() {
  local metrics="$1"
  local metric
  metrics="${metrics//[[:space:]]/}"
  if [[ -z "$metrics" || "$metrics" == "all" ]]; then
    return 0
  fi
  local previous_ifs="$IFS"
  IFS=","
  for metric in $metrics; do
    if ! known_metric "$metric"; then
      IFS="$previous_ifs"
      echo "Unknown gated benchmark metric: $metric" >&2
      return 2
    fi
  done
  IFS="$previous_ifs"
}
validate_gate_metrics "$GATE_METRICS"

for cmd in awk date jq mkdir mktemp tee; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

resolve_summary_file() {
  local path="$1"
  if [[ -d "$path" ]]; then
    printf '%s\n' "${path%/}/summary.json"
  else
    printf '%s\n' "$path"
  fi
}

BASELINE_SUMMARY="$(resolve_summary_file "$BASELINE")"
CURRENT_SUMMARY="$(resolve_summary_file "$CURRENT")"
if [[ ! -f "$BASELINE_SUMMARY" ]]; then
  echo "Baseline summary not found: $BASELINE_SUMMARY" >&2
  exit 2
fi
if [[ ! -f "$CURRENT_SUMMARY" ]]; then
  echo "Current summary not found: $CURRENT_SUMMARY" >&2
  exit 2
fi

if [[ -z "$SUMMARY_DIR" ]]; then
  SUMMARY_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-safetensor-benchmark-compare.XXXXXX")"
else
  mkdir -p "$SUMMARY_DIR"
fi
OUT="${OUT:-${SUMMARY_DIR}/comparison.tsv}"
SUMMARY_OUT="${SUMMARY_OUT:-${SUMMARY_DIR}/summary.tsv}"
TABLE_OUT="${TABLE_OUT:-${SUMMARY_DIR}/comparison-table.txt}"
SUMMARY_MD="${SUMMARY_DIR}/summary.md"
DECISION_JSON="${SUMMARY_DIR}/decision.json"
CONFIG="${SUMMARY_DIR}/config.tsv"
REPORT="${SUMMARY_DIR}/report.txt"
BASELINE_TSV="${SUMMARY_DIR}/baseline-runs.tsv"
CURRENT_TSV="${SUMMARY_DIR}/current-runs.tsv"

for artifact in "$OUT" "$SUMMARY_OUT" "$TABLE_OUT" "$SUMMARY_MD" "$DECISION_JSON" "$BASELINE_TSV" "$CURRENT_TSV"; do
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
config_row baseline "$BASELINE_SUMMARY"
config_row current "$CURRENT_SUMMARY"
config_row case "$CASE_NAME"
config_row summaryDir "$SUMMARY_DIR"
config_row comparison "$OUT"
config_row summary "$SUMMARY_OUT"
config_row table "$TABLE_OUT"
config_row markdown "$SUMMARY_MD"
config_row decision "$DECISION_JSON"
config_row baselineRuns "$BASELINE_TSV"
config_row currentRuns "$CURRENT_TSV"
config_row maxRegressionPercent "$MAX_REGRESSION_PERCENT"
config_row maxRegressionMs "$MAX_REGRESSION_MS"
config_row minBaselineValue "$MIN_BASELINE_VALUE"
config_row gateMetrics "$GATE_METRICS"

normalize_summary() {
  local source="$1"
  local out="$2"
  jq -r '
    def ms_from_summary($name):
      try ((.profile_summary // "") | capture("(^| )" + $name + "=(?<value>[0-9]+([.][0-9]+)?)ms").value | tonumber) catch null;
    def value_or_empty($value):
      if $value == null then "" else ($value | tostring) end;
    ([
      "case",
      "status",
      "durationMs",
      "speedTps",
      "chunks",
      "ttftMs",
      "topStageMs",
      "prefillMs",
      "decodeMs",
      "tpotMs",
      "samplingMs",
      "argmaxMs",
      "attentionMs",
      "ffnMs",
      "logitsMs",
      "ffnStrategy",
      "ffnRowPrefillNativeRows",
      "ffnRowPrefillVariant",
      "logFile"
    ] | @tsv),
    (.runs[] | [
      .case_id,
      .status,
      (if .duration_s == null then null else (.duration_s * 1000) end),
      .speed_tps,
      .chunks,
      ms_from_summary("ttft"),
      .profile_top_stage_ms,
      .profile_prefill_ms,
      .profile_decode_ms,
      .profile_tpot_ms,
      .profile_sampling_ms,
      .profile_argmax_ms,
      .profile_attention_ms,
      .profile_ffn_ms,
      .profile_logits_ms,
      .profile_ffn_strategy,
      .profile_ffn_row_prefill_native_rows,
      .profile_ffn_row_prefill_variant,
      .log_file
    ] | map(value_or_empty(.)) | @tsv)
  ' "$source" > "$out"
}

normalize_summary "$BASELINE_SUMMARY" "$BASELINE_TSV"
normalize_summary "$CURRENT_SUMMARY" "$CURRENT_TSV"

BASELINE_LABEL="$(jq -r '.label // "baseline"' "$BASELINE_SUMMARY")"
CURRENT_LABEL="$(jq -r '.label // "current"' "$CURRENT_SUMMARY")"

awk \
  -v baselineFile="$BASELINE_TSV" \
  -v currentFile="$CURRENT_TSV" \
  -v baselineLabel="$BASELINE_LABEL" \
  -v currentLabel="$CURRENT_LABEL" \
  -v caseName="$CASE_NAME" \
  -v summaryOut="$SUMMARY_OUT" \
  -v tableOut="$TABLE_OUT" \
  -v maxRegressionPercent="$MAX_REGRESSION_PERCENT" \
  -v maxRegressionMs="$MAX_REGRESSION_MS" \
  -v minBaselineValue="$MIN_BASELINE_VALUE" \
  -v gateMetrics="$GATE_METRICS" '
  BEGIN {
    FS = OFS = "\t"
    gateConfigured = (maxRegressionPercent != "" || maxRegressionMs != "")
    metricCount = 13
    metric[1] = "durationMs"; direction[metric[1]] = "lower"
    metric[2] = "speedTps"; direction[metric[2]] = "higher"
    metric[3] = "chunks"; direction[metric[3]] = "higher"
    metric[4] = "ttftMs"; direction[metric[4]] = "lower"
    metric[5] = "topStageMs"; direction[metric[5]] = "lower"
    metric[6] = "prefillMs"; direction[metric[6]] = "lower"
    metric[7] = "decodeMs"; direction[metric[7]] = "lower"
    metric[8] = "tpotMs"; direction[metric[8]] = "lower"
    metric[9] = "samplingMs"; direction[metric[9]] = "lower"
    metric[10] = "argmaxMs"; direction[metric[10]] = "lower"
    metric[11] = "attentionMs"; direction[metric[11]] = "lower"
    metric[12] = "ffnMs"; direction[metric[12]] = "lower"
    metric[13] = "logitsMs"; direction[metric[13]] = "lower"
    parse_gate_metrics(gateMetrics)
    print "case", "metric", "baseline", "current", "delta", "deltaPercent", "direction", "trend", "baselineFfnStrategy", "currentFfnStrategy", "baselineRowPrefill", "currentRowPrefill", "gateStatus", "gateReason"
    printf "%-24s %-12s %12s %12s %12s %12s %-8s %-8s %-14s %-32s %-32s\n", "case", "metric", "baseline", "current", "delta", "delta%", "dir", "trend", "gate", "baselineStrategy", "currentStrategy" > tableOut
    printf "%-24s %-12s %12s %12s %12s %12s %-8s %-8s %-14s %-32s %-32s\n", "------------------------", "------------", "------------", "------------", "------------", "------------", "--------", "--------", "--------------", "--------------------------------", "--------------------------------" >> tableOut
  }
  function numeric(value) {
    return value ~ /^-?[0-9]+([.][0-9]+)?$/
  }
  function parse_gate_metrics(metrics, defaultMetrics, count, parts, i, name) {
    defaultMetrics = "durationMs,ttftMs,topStageMs,prefillMs,decodeMs,tpotMs,samplingMs,argmaxMs,attentionMs,ffnMs,logitsMs"
    gsub(/[[:space:]]+/, "", metrics)
    if (metrics == "") {
      metrics = defaultMetrics
    }
    if (metrics == "all") {
      for (i = 1; i <= metricCount; i++) {
        gateMetric[metric[i]] = 1
      }
      return
    }
    count = split(metrics, parts, ",")
    for (i = 1; i <= count; i++) {
      name = parts[i]
      if (name != "") {
        gateMetric[name] = 1
      }
    }
  }
  function fmt(value) {
    return numeric(value) ? sprintf("%.3f", value + 0) : ""
  }
  function remember_case(caseId) {
    if (caseId == "" || seenCase[caseId]) {
      return
    }
    seenCase[caseId] = 1
    caseOrder[++caseCount] = caseId
  }
  function better_or_worse(delta, dir) {
    if (!numeric(delta)) {
      return "skip"
    }
    if (delta + 0 == 0) {
      return "same"
    }
    if (dir == "higher") {
      return delta + 0 > 0 ? "better" : "worse"
    }
    return delta + 0 < 0 ? "better" : "worse"
  }
  function delta_percent(base, current) {
    if (!numeric(base) || !numeric(current)) {
      return ""
    }
    if (base + 0 == 0) {
      return current + 0 == 0 ? 0 : 1000000000
    }
    return ((current - base) / base) * 100
  }
  function append_reason(existing, nextReason) {
    return existing == "" ? nextReason : existing "; " nextReason
  }
  function abs(value) {
    return value < 0 ? -value : value
  }
  function percent_magnitude(percent) {
    return numeric(percent) ? abs(percent + 0) : 0
  }
  function gate_result(name, base, currentValue, delta, percent, dir, reason, regression, percentRegression) {
    if (!gateConfigured) {
      return "not-configured\t"
    }
    if (!gateMetric[name]) {
      return "skip\tmetric-not-gated"
    }
    if (!numeric(base) || !numeric(currentValue) || !numeric(delta)) {
      return "skip\tmissing-metric"
    }
    regression = dir == "higher" ? -delta : delta
    percentRegression = dir == "higher" ? -percent : percent
    reason = ""
    if (numeric(maxRegressionPercent) && numeric(percentRegression) && base + 0 >= minBaselineValue + 0 && percentRegression + 0 > maxRegressionPercent + 0) {
      reason = append_reason(reason, "deltaPercent=" sprintf("%.3f", percentRegression + 0) " exceeded " maxRegressionPercent)
    }
    if (numeric(maxRegressionMs) && name ~ /Ms$/ && regression + 0 > maxRegressionMs + 0) {
      reason = append_reason(reason, "deltaMs=" sprintf("%.3f", regression + 0) " exceeded " maxRegressionMs)
    }
    if (reason != "") {
      return "fail\t" reason
    }
    return "pass\t"
  }
  function row_prefill(rows, variant) {
    rows = rows == "" ? "n/a" : rows
    variant = variant == "" ? "n/a" : variant
    return rows "/" variant
  }
  function recommendation(status, failed, better, worse) {
    if (status == "fail" || failed + 0 > 0) {
      return "reject-current"
    }
    if (better + 0 > 0 && worse + 0 == 0) {
      return "promote-current"
    }
    if (better + 0 > worse + 0) {
      return "promote-current-with-watchlist"
    }
    if (better + 0 == 0 && worse + 0 > 0) {
      return "hold-current"
    }
    return "collect-more-samples"
  }
  function write_summary_row(key, value) {
    print key, value >> summaryOut
  }
  FILENAME == baselineFile && FNR == 1 {
    for (i = 1; i <= NF; i++) {
      bcol[$i] = i
    }
    next
  }
  FILENAME == baselineFile && FNR > 1 {
    caseId = $(bcol["case"])
    if (caseName != "" && caseId != caseName) {
      next
    }
    remember_case(caseId)
    baselineSeen[caseId] = 1
    for (i = 1; i <= metricCount; i++) {
      name = metric[i]
      baseline[caseId, name] = bcol[name] ? $(bcol[name]) : ""
    }
    baselineStrategy[caseId] = bcol["ffnStrategy"] ? $(bcol["ffnStrategy"]) : ""
    baselineRows[caseId] = bcol["ffnRowPrefillNativeRows"] ? $(bcol["ffnRowPrefillNativeRows"]) : ""
    baselineVariant[caseId] = bcol["ffnRowPrefillVariant"] ? $(bcol["ffnRowPrefillVariant"]) : ""
    next
  }
  FILENAME == currentFile && FNR == 1 {
    for (i = 1; i <= NF; i++) {
      ccol[$i] = i
    }
    next
  }
  FILENAME == currentFile && FNR > 1 {
    caseId = $(ccol["case"])
    if (caseName != "" && caseId != caseName) {
      next
    }
    remember_case(caseId)
    currentSeen[caseId] = 1
    for (i = 1; i <= metricCount; i++) {
      name = metric[i]
      current[caseId, name] = ccol[name] ? $(ccol[name]) : ""
    }
    currentStrategy[caseId] = ccol["ffnStrategy"] ? $(ccol["ffnStrategy"]) : ""
    currentRows[caseId] = ccol["ffnRowPrefillNativeRows"] ? $(ccol["ffnRowPrefillNativeRows"]) : ""
    currentVariant[caseId] = ccol["ffnRowPrefillVariant"] ? $(ccol["ffnRowPrefillVariant"]) : ""
    next
  }
  END {
    print "key", "value" > summaryOut
    firstComparedCase = ""
    for (caseIndex = 1; caseIndex <= caseCount; caseIndex++) {
      caseId = caseOrder[caseIndex]
      if (!baselineSeen[caseId] || !currentSeen[caseId]) {
        skippedCases++
        continue
      }
      comparedCases++
      if (firstComparedCase == "") {
        firstComparedCase = caseId
      }
      for (i = 1; i <= metricCount; i++) {
        name = metric[i]
        b = baseline[caseId, name]
        c = current[caseId, name]
        split(gate_result(name, b, c, "", "", direction[name]), gateParts, "\t")
        gate = gateParts[1]
        reason = gateParts[2]
        if (!numeric(b) || !numeric(c)) {
          skippedMetrics++
          if (gate != "not-configured" && gate != "skip") {
            gatedMetrics++
          }
          print caseId, name, fmt(b), fmt(c), "", "", direction[name], "skip", baselineStrategy[caseId], currentStrategy[caseId], row_prefill(baselineRows[caseId], baselineVariant[caseId]), row_prefill(currentRows[caseId], currentVariant[caseId]), gate, reason
          continue
        }
        delta = c - b
        percent = delta_percent(b, c)
        trend = better_or_worse(delta, direction[name])
        split(gate_result(name, b, c, delta, percent, direction[name]), gateParts, "\t")
        gate = gateParts[1]
        reason = gateParts[2]
        if (gate == "pass" || gate == "fail") {
          gatedMetrics++
        }
        if (gate == "fail") {
          failedMetrics++
        }
        if (trend == "better") {
          betterMetrics++
          improvement = percent_magnitude(percent)
          if (largestImprovementMetric == "" || improvement + 0 > largestImprovementPercent + 0) {
            largestImprovementCase = caseId
            largestImprovementMetric = name
            largestImprovementDelta = delta
            largestImprovementPercent = improvement
          }
        } else if (trend == "worse") {
          worseMetrics++
          regression = percent_magnitude(percent)
          if (largestRegressionMetric == "" || regression + 0 > largestRegressionPercent + 0) {
            largestRegressionCase = caseId
            largestRegressionMetric = name
            largestRegressionDelta = delta
            largestRegressionPercent = regression
          }
        } else {
          sameMetrics++
        }
        comparedMetrics++
        print caseId, name, fmt(b), fmt(c), sprintf("%.3f", delta), sprintf("%.3f", percent), direction[name], trend, baselineStrategy[caseId], currentStrategy[caseId], row_prefill(baselineRows[caseId], baselineVariant[caseId]), row_prefill(currentRows[caseId], currentVariant[caseId]), gate, reason
        printf "%-24s %-12s %12.3f %12.3f %12.3f %12.3f %-8s %-8s %-14s %-32s %-32s\n", caseId, name, b + 0, c + 0, delta, percent, direction[name], trend, gate, baselineStrategy[caseId], currentStrategy[caseId] >> tableOut
      }
    }
    if (caseName != "" && comparedCases == 0) {
      print "Requested case not found in both summaries: " caseName > "/dev/stderr"
      exit 3
    }
    if (comparedCases == 0) {
      print "No common safetensor benchmark cases found for comparison" > "/dev/stderr"
      exit 3
    }
    failedReason = failedMetrics > 0 ? "metric-regression" : ""
    status = failedReason == "" ? "pass" : "fail"
    decision = recommendation(status, failedMetrics, betterMetrics, worseMetrics)
    write_summary_row("status", status)
    write_summary_row("reason", failedReason)
    write_summary_row("recommendation", decision)
    write_summary_row("baselineLabel", baselineLabel)
    write_summary_row("currentLabel", currentLabel)
    write_summary_row("case", firstComparedCase)
    write_summary_row("comparedCases", comparedCases)
    write_summary_row("skippedCases", skippedCases + 0)
    write_summary_row("comparedMetrics", comparedMetrics + 0)
    write_summary_row("betterMetrics", betterMetrics + 0)
    write_summary_row("worseMetrics", worseMetrics + 0)
    write_summary_row("sameMetrics", sameMetrics + 0)
    write_summary_row("skippedMetrics", skippedMetrics + 0)
    write_summary_row("gatedMetrics", gatedMetrics + 0)
    write_summary_row("failedMetrics", failedMetrics + 0)
    write_summary_row("largestImprovementCase", largestImprovementCase)
    write_summary_row("largestImprovementMetric", largestImprovementMetric)
    write_summary_row("largestImprovementDelta", numeric(largestImprovementDelta) ? sprintf("%.3f", largestImprovementDelta + 0) : largestImprovementDelta)
    write_summary_row("largestImprovementPercent", numeric(largestImprovementPercent) ? sprintf("%.3f", largestImprovementPercent + 0) : largestImprovementPercent)
    write_summary_row("largestRegressionCase", largestRegressionCase)
    write_summary_row("largestRegressionMetric", largestRegressionMetric)
    write_summary_row("largestRegressionDelta", numeric(largestRegressionDelta) ? sprintf("%.3f", largestRegressionDelta + 0) : largestRegressionDelta)
    write_summary_row("largestRegressionPercent", numeric(largestRegressionPercent) ? sprintf("%.3f", largestRegressionPercent + 0) : largestRegressionPercent)
    write_summary_row("baselineFfnStrategy", baselineStrategy[firstComparedCase])
    write_summary_row("currentFfnStrategy", currentStrategy[firstComparedCase])
    write_summary_row("baselineRowPrefill", row_prefill(baselineRows[firstComparedCase], baselineVariant[firstComparedCase]))
    write_summary_row("currentRowPrefill", row_prefill(currentRows[firstComparedCase], currentVariant[firstComparedCase]))
  }
' "$BASELINE_TSV" "$CURRENT_TSV" > "$OUT"

STATUS="$(awk 'BEGIN { FS = "\t" } $1 == "status" { print $2; exit }' "$SUMMARY_OUT")"
REASON="$(awk 'BEGIN { FS = "\t" } $1 == "reason" { print $2; exit }' "$SUMMARY_OUT")"
RECOMMENDATION="$(awk 'BEGIN { FS = "\t" } $1 == "recommendation" { print $2; exit }' "$SUMMARY_OUT")"
COMPARED_CASES="$(awk 'BEGIN { FS = "\t" } $1 == "comparedCases" { print $2; exit }' "$SUMMARY_OUT")"
COMPARED_METRICS="$(awk 'BEGIN { FS = "\t" } $1 == "comparedMetrics" { print $2; exit }' "$SUMMARY_OUT")"
BETTER_METRICS="$(awk 'BEGIN { FS = "\t" } $1 == "betterMetrics" { print $2; exit }' "$SUMMARY_OUT")"
WORSE_METRICS="$(awk 'BEGIN { FS = "\t" } $1 == "worseMetrics" { print $2; exit }' "$SUMMARY_OUT")"
GATED_METRICS="$(awk 'BEGIN { FS = "\t" } $1 == "gatedMetrics" { print $2; exit }' "$SUMMARY_OUT")"
FAILED_METRICS="$(awk 'BEGIN { FS = "\t" } $1 == "failedMetrics" { print $2; exit }' "$SUMMARY_OUT")"
LARGEST_IMPROVEMENT_METRIC="$(awk 'BEGIN { FS = "\t" } $1 == "largestImprovementMetric" { print $2; exit }' "$SUMMARY_OUT")"
LARGEST_IMPROVEMENT_PERCENT="$(awk 'BEGIN { FS = "\t" } $1 == "largestImprovementPercent" { print $2; exit }' "$SUMMARY_OUT")"
LARGEST_REGRESSION_METRIC="$(awk 'BEGIN { FS = "\t" } $1 == "largestRegressionMetric" { print $2; exit }' "$SUMMARY_OUT")"
LARGEST_REGRESSION_PERCENT="$(awk 'BEGIN { FS = "\t" } $1 == "largestRegressionPercent" { print $2; exit }' "$SUMMARY_OUT")"
BASELINE_STRATEGY="$(awk 'BEGIN { FS = "\t" } $1 == "baselineFfnStrategy" { print $2; exit }' "$SUMMARY_OUT")"
CURRENT_STRATEGY="$(awk 'BEGIN { FS = "\t" } $1 == "currentFfnStrategy" { print $2; exit }' "$SUMMARY_OUT")"
BASELINE_ROW="$(awk 'BEGIN { FS = "\t" } $1 == "baselineRowPrefill" { print $2; exit }' "$SUMMARY_OUT")"
CURRENT_ROW="$(awk 'BEGIN { FS = "\t" } $1 == "currentRowPrefill" { print $2; exit }' "$SUMMARY_OUT")"

{
  echo "# Aqli Safetensor Benchmark Comparison"
  echo
  echo "| Field | Value |"
  echo "| --- | --- |"
  echo "| Status | ${STATUS} |"
  echo "| Recommendation | ${RECOMMENDATION} |"
  echo "| Reason | ${REASON:-n/a} |"
  echo "| Compared Cases | ${COMPARED_CASES} |"
  echo "| Compared Metrics | ${COMPARED_METRICS} |"
  echo "| Better Metrics | ${BETTER_METRICS} |"
  echo "| Worse Metrics | ${WORSE_METRICS} |"
  echo "| Gated Metrics | ${GATED_METRICS} |"
  echo "| Failed Metrics | ${FAILED_METRICS} |"
  echo "| Largest Improvement | ${LARGEST_IMPROVEMENT_METRIC:-n/a}:${LARGEST_IMPROVEMENT_PERCENT:-n/a}% |"
  echo "| Largest Regression | ${LARGEST_REGRESSION_METRIC:-n/a}:${LARGEST_REGRESSION_PERCENT:-n/a}% |"
  echo "| FFN Strategy Transition | ${BASELINE_STRATEGY:-unknown}->${CURRENT_STRATEGY:-unknown} |"
  echo "| Row-Prefill Transition | ${BASELINE_ROW:-unknown}->${CURRENT_ROW:-unknown} |"
  echo
  echo "## Metrics"
  echo
  awk '
    BEGIN {
      FS = "\t"
      print "| Case | Metric | Baseline | Current | Delta | Delta % | Trend | Gate | Reason |"
      print "| --- | --- | ---: | ---: | ---: | ---: | --- | --- | --- |"
    }
    function esc(value) {
      gsub(/\|/, "\\|", value)
      return value
    }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        col[$i] = i
      }
      next
    }
    {
      reason = $(col["gateReason"]) == "" ? "n/a" : $(col["gateReason"])
      printf "| `%s` | `%s` | %s | %s | %s | %s | `%s` | `%s` | %s |\n", \
        esc($(col["case"])), esc($(col["metric"])), $(col["baseline"]), $(col["current"]), \
        $(col["delta"]), $(col["deltaPercent"]), esc($(col["trend"])), esc($(col["gateStatus"])), esc(reason)
    }
  ' "$OUT"
  echo
  echo "## Artifacts"
  echo
  echo "- Config: \`${CONFIG}\`"
  echo "- Comparison TSV: \`${OUT}\`"
  echo "- Summary TSV: \`${SUMMARY_OUT}\`"
  echo "- Table: \`${TABLE_OUT}\`"
  echo "- Decision JSON: \`${DECISION_JSON}\`"
} > "$SUMMARY_MD"

jq -n \
  --arg baseline "$BASELINE_SUMMARY" \
  --arg current "$CURRENT_SUMMARY" \
  --arg baselineLabel "$BASELINE_LABEL" \
  --arg currentLabel "$CURRENT_LABEL" \
  --arg requestedCase "$CASE_NAME" \
  --arg summaryDir "$SUMMARY_DIR" \
  --arg comparisonPath "$OUT" \
  --arg summaryPath "$SUMMARY_OUT" \
  --arg tablePath "$TABLE_OUT" \
  --arg markdownPath "$SUMMARY_MD" \
  --arg decisionPath "$DECISION_JSON" \
  --arg configPath "$CONFIG" \
  --arg reportPath "$REPORT" \
  --arg baselineRunsPath "$BASELINE_TSV" \
  --arg currentRunsPath "$CURRENT_TSV" \
  --arg maxRegressionPercent "$MAX_REGRESSION_PERCENT" \
  --arg maxRegressionMs "$MAX_REGRESSION_MS" \
  --arg minBaselineValue "$MIN_BASELINE_VALUE" \
  --arg gateMetrics "$GATE_METRICS" \
  --rawfile config "$CONFIG" \
  --rawfile summary "$SUMMARY_OUT" \
  --rawfile comparison "$OUT" '
    def rows($raw):
      ($raw | split("\n") | map(select(length > 0) | split("\t"))) as $lines
      | if ($lines | length) == 0 then
          []
        else
          ($lines[0]) as $header
          | $lines[1:] | map(. as $row | reduce range(0; $header | length) as $index ({}; . + {($header[$index]): ($row[$index] // "")}))
        end;
    def kv($raw):
      reduce rows($raw)[] as $row ({}; . + {($row.key): $row.value});
    def n:
      if . == null or . == "" then 0 else tonumber end;
    def maybe_n:
      if . == null or . == "" then null
      elif test("^-?[0-9]+([.][0-9]+)?$") then tonumber
      else .
      end;
    def maybe_s:
      if . == null or . == "" then null else . end;
    def gate_metric_list($value):
      if $value == "" then ["default-latency"]
      elif $value == "all" then ["all"]
      else ($value | split(","))
      end;
    def action($status; $recommendation):
      if $status == "fail" or $recommendation == "reject-current" then "reject"
      elif $recommendation == "promote-current" then "promote"
      elif $recommendation == "promote-current-with-watchlist" then "promote-with-watchlist"
      elif $recommendation == "hold-current" then "hold"
      else "collect-more-samples"
      end;

    kv($config) as $cfg
    | kv($summary) as $sum
    | (rows($comparison)) as $metricRows
    | ($sum.status // "") as $status
    | ($sum.recommendation // "") as $recommendation
    | {
        schemaVersion: 1,
        generatedAt: ($cfg.generatedAt // ""),
        inputs: {
          baseline: $baseline,
          current: $current,
          baselineLabel: $baselineLabel,
          currentLabel: $currentLabel,
          requestedCase: ($requestedCase | maybe_s)
        },
        gates: {
          configured: (($maxRegressionPercent != "") or ($maxRegressionMs != "")),
          maxRegressionPercent: ($maxRegressionPercent | maybe_n),
          maxRegressionMs: ($maxRegressionMs | maybe_n),
          minBaselineValue: ($minBaselineValue | maybe_n),
          metrics: gate_metric_list($gateMetrics)
        },
        summary: {
          status: $status,
          reason: (($sum.reason // "") | maybe_s),
          recommendation: $recommendation,
          labels: {
            baseline: ($sum.baselineLabel // $baselineLabel),
            current: ($sum.currentLabel // $currentLabel)
          },
          case: (($sum.case // "") | maybe_s),
          counts: {
            comparedCases: (($sum.comparedCases // "0") | n),
            skippedCases: (($sum.skippedCases // "0") | n),
            comparedMetrics: (($sum.comparedMetrics // "0") | n),
            betterMetrics: (($sum.betterMetrics // "0") | n),
            worseMetrics: (($sum.worseMetrics // "0") | n),
            sameMetrics: (($sum.sameMetrics // "0") | n),
            skippedMetrics: (($sum.skippedMetrics // "0") | n),
            gatedMetrics: (($sum.gatedMetrics // "0") | n),
            failedMetrics: (($sum.failedMetrics // "0") | n)
          },
          largestImprovement: {
            case: (($sum.largestImprovementCase // "") | maybe_s),
            metric: (($sum.largestImprovementMetric // "") | maybe_s),
            delta: (($sum.largestImprovementDelta // "") | maybe_n),
            percent: (($sum.largestImprovementPercent // "") | maybe_n)
          },
          largestRegression: {
            case: (($sum.largestRegressionCase // "") | maybe_s),
            metric: (($sum.largestRegressionMetric // "") | maybe_s),
            delta: (($sum.largestRegressionDelta // "") | maybe_n),
            percent: (($sum.largestRegressionPercent // "") | maybe_n)
          },
          ffnStrategyTransition: {
            baseline: (($sum.baselineFfnStrategy // "") | maybe_s),
            current: (($sum.currentFfnStrategy // "") | maybe_s)
          },
          rowPrefillTransition: {
            baseline: (($sum.baselineRowPrefill // "") | maybe_s),
            current: (($sum.currentRowPrefill // "") | maybe_s)
          }
        },
        policy: {
          canPromote: ($status == "pass" and ($recommendation == "promote-current" or $recommendation == "promote-current-with-watchlist")),
          action: action($status; $recommendation)
        },
        metrics: ($metricRows | map({
          case: .case,
          metric,
          baseline: (.baseline | maybe_n),
          current: (.current | maybe_n),
          delta: (.delta | maybe_n),
          deltaPercent: (.deltaPercent | maybe_n),
          direction,
          trend,
          baselineFfnStrategy: (.baselineFfnStrategy | maybe_s),
          currentFfnStrategy: (.currentFfnStrategy | maybe_s),
          baselineRowPrefill: (.baselineRowPrefill | maybe_s),
          currentRowPrefill: (.currentRowPrefill | maybe_s),
          gateStatus,
          gateReason: (.gateReason | maybe_s)
        })),
        artifacts: {
          summaryDir: $summaryDir,
          config: $configPath,
          comparison: $comparisonPath,
          summary: $summaryPath,
          table: $tablePath,
          markdown: $markdownPath,
          decision: $decisionPath,
          report: $reportPath,
          baselineRuns: $baselineRunsPath,
          currentRuns: $currentRunsPath
        }
      }
  ' > "$DECISION_JSON"

{
  echo "Aqli safetensor benchmark comparison"
  echo "summaryDir=$SUMMARY_DIR"
  echo "artifacts.config=$CONFIG"
  echo "artifacts.comparison=$OUT"
  echo "artifacts.summary=$SUMMARY_OUT"
  echo "artifacts.table=$TABLE_OUT"
  echo "artifacts.markdown=$SUMMARY_MD"
  echo "artifacts.decision=$DECISION_JSON"
  echo "status=$STATUS"
  echo "reason=$REASON"
  echo "recommendation=$RECOMMENDATION"
  echo "comparedCases=$COMPARED_CASES"
  echo "comparedMetrics=$COMPARED_METRICS"
  echo "betterMetrics=$BETTER_METRICS"
  echo "worseMetrics=$WORSE_METRICS"
  echo "gatedMetrics=$GATED_METRICS"
  echo "failedMetrics=$FAILED_METRICS"
  echo "largestImprovement=$LARGEST_IMPROVEMENT_METRIC:${LARGEST_IMPROVEMENT_PERCENT}%"
  echo "largestRegression=$LARGEST_REGRESSION_METRIC:${LARGEST_REGRESSION_PERCENT}%"
  echo "ffnStrategyTransition=$BASELINE_STRATEGY->$CURRENT_STRATEGY"
  echo "rowPrefillTransition=$BASELINE_ROW->$CURRENT_ROW"
} | tee "$REPORT"

if [[ "$STATUS" == "fail" ]]; then
  exit 1
fi
