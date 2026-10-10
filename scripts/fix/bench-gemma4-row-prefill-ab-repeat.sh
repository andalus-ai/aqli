#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<'USAGE'
Usage: bench-gemma4-row-prefill-ab-repeat.sh --model MODEL [options] [-- RUNNER_ARGS...]

Run the Gemma4 row-prefill A/B benchmark multiple times, keep each sample's
artifacts, and aggregate the comparison decisions into sample and metric TSVs
with average, median, p95, standard-deviation, and unstable-metric signals.

Options:
  --model ID             Model id or local id alias (required)
  --aqli-bin PATH      Aqli executable (default: ~/.local/bin/aqli)
  --out-dir PATH         Output directory for repeat artifacts
                         (default: aqli/ops/benchmarks/safetensor/gemma4-row-prefill-ab-repeat)
  --repeat N             Number of A/B samples to run (default: 3)
  --runner-script PATH   Single-sample A/B runner path
  --runner-arg ARG       Extra argument passed to each A/B runner; repeatable
  --max-p95-regression-percent N
                         Fail when any metric p95 regression exceeds N percent
  --fail-unstable-metrics
                         Fail when any metric has mixed better/worse samples or high variance
  --aggregate-gate-metrics CSV
                         Metrics considered by repeat-level p95/unstable gates
                         (default: all)
  --require-promotion-can-promote
                         Fail after artifacts are written unless promotion policy
                         has canPromote=true
  --require-promotion-action CSV
                         Fail after artifacts are written unless promotion policy
                         action is one of CSV
  --help                 Show this help

Any arguments after `--` are forwarded to every single-sample A/B run.

Examples:
  ./scripts/bench-gemma4-row-prefill-ab-repeat.sh --model 53f473 --repeat 3 -- \
    --max-regression-ms 250 --gate-metrics ttftMs,ffnMs
USAGE
}

MODEL_ID=""
AQLI_BIN="${HOME}/.local/bin/aqli"
OUT_DIR="aqli/ops/benchmarks/safetensor/gemma4-row-prefill-ab-repeat"
REPEAT_COUNT=3
RUNNER_SCRIPT="${AQLI_GEMMA4_ROW_PREFILL_AB_RUNNER_SCRIPT:-${SCRIPT_DIR}/bench-gemma4-row-prefill-ab.sh}"
PROMOTION_POLICY_SCRIPT="${AQLI_SAFETENSOR_PROMOTION_POLICY_SCRIPT:-${SCRIPT_DIR}/safetensor-promotion-policy.sh}"
MAX_P95_REGRESSION_PERCENT=""
FAIL_UNSTABLE_METRICS=0
AGGREGATE_GATE_METRICS=""
REQUIRE_PROMOTION_CAN_PROMOTE=0
REQUIRE_PROMOTION_ACTIONS=""
RUNNER_ARGS=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model) MODEL_ID="$2"; shift 2 ;;
    --model=*) MODEL_ID="${1#*=}"; shift ;;
    --aqli-bin) AQLI_BIN="$2"; shift 2 ;;
    --aqli-bin=*) AQLI_BIN="${1#*=}"; shift ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --out-dir=*) OUT_DIR="${1#*=}"; shift ;;
    --repeat) REPEAT_COUNT="$2"; shift 2 ;;
    --repeat=*) REPEAT_COUNT="${1#*=}"; shift ;;
    --runner-script) RUNNER_SCRIPT="$2"; shift 2 ;;
    --runner-script=*) RUNNER_SCRIPT="${1#*=}"; shift ;;
    --runner-arg) RUNNER_ARGS+=("$2"); shift 2 ;;
    --runner-arg=*) RUNNER_ARGS+=("${1#*=}"); shift ;;
    --max-p95-regression-percent) MAX_P95_REGRESSION_PERCENT="$2"; shift 2 ;;
    --max-p95-regression-percent=*) MAX_P95_REGRESSION_PERCENT="${1#*=}"; shift ;;
    --fail-unstable-metrics) FAIL_UNSTABLE_METRICS=1; shift ;;
    --aggregate-gate-metrics) AGGREGATE_GATE_METRICS="$2"; shift 2 ;;
    --aggregate-gate-metrics=*) AGGREGATE_GATE_METRICS="${1#*=}"; shift ;;
    --require-promotion-can-promote) REQUIRE_PROMOTION_CAN_PROMOTE=1; shift ;;
    --require-promotion-action) REQUIRE_PROMOTION_ACTIONS="$2"; shift 2 ;;
    --require-promotion-action=*) REQUIRE_PROMOTION_ACTIONS="${1#*=}"; shift ;;
    --) shift; RUNNER_ARGS+=("$@"); break ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$MODEL_ID" ]]; then
  echo "--model is required" >&2
  usage
  exit 2
fi
if [[ ! -f "$RUNNER_SCRIPT" ]]; then
  echo "A/B runner script not found: $RUNNER_SCRIPT" >&2
  exit 2
fi
if [[ ! -f "$PROMOTION_POLICY_SCRIPT" ]]; then
  echo "Promotion policy script not found: $PROMOTION_POLICY_SCRIPT" >&2
  exit 2
fi
if [[ ! "$REPEAT_COUNT" =~ ^[0-9]+$ || "$REPEAT_COUNT" -lt 1 ]]; then
  echo "Invalid repeat count: $REPEAT_COUNT" >&2
  exit 2
fi
if [[ -n "$MAX_P95_REGRESSION_PERCENT" && ! "$MAX_P95_REGRESSION_PERCENT" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "Invalid max p95 regression percent: $MAX_P95_REGRESSION_PERCENT" >&2
  exit 2
fi

known_metric() {
  case "$1" in
    durationMs|speedTps|chunks|ttftMs|topStageMs|prefillMs|decodeMs|tpotMs|samplingMs|argmaxMs|attentionMs|ffnMs|logitsMs) return 0 ;;
    *) return 1 ;;
  esac
}

validate_aggregate_gate_metrics() {
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
      echo "Unknown aggregate gate metric: $metric" >&2
      return 2
    fi
  done
  IFS="$previous_ifs"
}

AGGREGATE_GATE_METRICS="${AGGREGATE_GATE_METRICS//[[:space:]]/}"
REQUIRE_PROMOTION_ACTIONS="${REQUIRE_PROMOTION_ACTIONS//[[:space:]]/}"
validate_aggregate_gate_metrics "$AGGREGATE_GATE_METRICS"

for cmd in awk date jq mkdir tee; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

SAMPLES_DIR="${OUT_DIR%/}/samples"
SAMPLES_TSV="${OUT_DIR%/}/samples.tsv"
SAMPLE_DECISIONS_TSV="${OUT_DIR%/}/sample-decisions.tsv"
METRIC_ROWS_TSV="${OUT_DIR%/}/metric-rows.tsv"
METRICS_TSV="${OUT_DIR%/}/metrics.tsv"
AGGREGATE_GATE_FAILURES_TSV="${OUT_DIR%/}/aggregate-gate-failures.tsv"
SUMMARY_TSV="${OUT_DIR%/}/summary.tsv"
SUMMARY_MD="${OUT_DIR%/}/summary.md"
DECISION_JSON="${OUT_DIR%/}/decision.json"
PROMOTION_POLICY_JSON="${OUT_DIR%/}/promotion-policy.json"
PROMOTION_POLICY_TSV="${OUT_DIR%/}/promotion-policy.tsv"
CONFIG_TSV="${OUT_DIR%/}/config.tsv"
REPORT="${OUT_DIR%/}/report.txt"

mkdir -p "$OUT_DIR" "$SAMPLES_DIR"

safe_tsv_field() {
  local value="${1:-}"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

config_row() {
  printf '%s\t%s\n' "$(safe_tsv_field "$1")" "$(safe_tsv_field "${2:-}")" >> "$CONFIG_TSV"
}

{
  printf 'key\tvalue\n'
} > "$CONFIG_TSV"
config_row generatedAt "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
config_row model "$MODEL_ID"
config_row aqliBin "$AQLI_BIN"
config_row outDir "$OUT_DIR"
config_row repeat "$REPEAT_COUNT"
config_row runnerScript "$RUNNER_SCRIPT"
config_row runnerArgs "${RUNNER_ARGS[*]:-}"
config_row maxP95RegressionPercent "$MAX_P95_REGRESSION_PERCENT"
config_row failUnstableMetrics "$FAIL_UNSTABLE_METRICS"
config_row aggregateGateMetrics "$AGGREGATE_GATE_METRICS"
config_row requirePromotionCanPromote "$REQUIRE_PROMOTION_CAN_PROMOTE"
config_row requirePromotionAction "$REQUIRE_PROMOTION_ACTIONS"

report_value() {
  local key="$1"
  local file="$2"
  if [[ -f "$file" ]]; then
    awk -v key="$key" 'index($0, key "=") == 1 { print substr($0, length(key) + 2); exit }' "$file"
  fi
}

append_metric_rows() {
  local sample="$1"
  local comparison="$2"
  if [[ ! -f "$comparison" ]]; then
    return
  fi
  if [[ ! -f "$METRIC_ROWS_TSV" ]]; then
    awk 'BEGIN { FS = OFS = "\t" } NR == 1 { print "sample", $0; exit }' "$comparison" > "$METRIC_ROWS_TSV"
  fi
  awk -v sample="$sample" 'BEGIN { FS = OFS = "\t" } NR > 1 { print sample, $0 }' "$comparison" >> "$METRIC_ROWS_TSV"
}

sample_decision_row() {
  local first=1
  local value
  for value in "$@"; do
    if (( first == 1 )); then
      first=0
    else
      printf '\t' >> "$SAMPLE_DECISIONS_TSV"
    fi
    safe_tsv_field "$value" >> "$SAMPLE_DECISIONS_TSV"
  done
  printf '\n' >> "$SAMPLE_DECISIONS_TSV"
}

append_sample_decision() {
  local sample="$1"
  local compare_markdown="${2:-}"
  local compare_decision="${3:-}"
  if [[ ! -f "$compare_decision" ]]; then
    sample_decision_row "$sample" "" "" "" "" "" "" "" "" "" "" "" "" "$compare_markdown" "$compare_decision"
    return
  fi
  jq -r \
    --arg sample "$sample" \
    --arg compareMarkdown "$compare_markdown" \
    --arg compareDecision "$compare_decision" '
      def str($value):
        if $value == null then "" else ($value | tostring) end;
      def metric_percent($node):
        if ($node.metric // "") == "" then
          ""
        else
          ($node.metric // "") + ":" + (($node.percent // "") | tostring) + "%"
        end;
      [
        $sample,
        (.summary.status // ""),
        (.summary.recommendation // ""),
        (.policy.action // ""),
        (.policy.canPromote // ""),
        (.summary.counts.gatedMetrics // ""),
        (.summary.counts.failedMetrics // ""),
        metric_percent(.summary.largestImprovement),
        metric_percent(.summary.largestRegression),
        (.summary.ffnStrategyTransition.baseline // ""),
        (.summary.ffnStrategyTransition.current // ""),
        (.summary.rowPrefillTransition.baseline // ""),
        (.summary.rowPrefillTransition.current // ""),
        $compareMarkdown,
        $compareDecision
      ] | map(str(.)) | @tsv
    ' "$compare_decision" >> "$SAMPLE_DECISIONS_TSV"
}

{
  printf 'sample\texitCode\tstatus\trecommendation\tgatedMetrics\tfailedMetrics\tlargestImprovement\tlargestRegression\tffnStrategyTransition\trowPrefillTransition\tsampleDir\treport\tcompareMarkdown\tcompareDecision\n'
} > "$SAMPLES_TSV"
{
  printf 'sample\tcompareStatus\tcompareRecommendation\tcompareAction\tcompareCanPromote\tcompareGatedMetrics\tcompareFailedMetrics\tcompareLargestImprovement\tcompareLargestRegression\tcompareBaselineFfnStrategy\tcompareCurrentFfnStrategy\tcompareBaselineRowPrefill\tcompareCurrentRowPrefill\tcompareMarkdown\tcompareDecision\n'
} > "$SAMPLE_DECISIONS_TSV"
rm -f "$METRIC_ROWS_TSV"

for ((sample_index = 1; sample_index <= REPEAT_COUNT; sample_index++)); do
  sample="$(printf 'sample-%02d' "$sample_index")"
  sample_dir="${SAMPLES_DIR}/${sample}"
  stdout_file="${sample_dir}/stdout.log"
  stderr_file="${sample_dir}/stderr.log"
  report_file="${sample_dir}/report.txt"
  comparison_file="${sample_dir}/compare/comparison.tsv"
  mkdir -p "$sample_dir"

  echo "[repeat] running ${sample}/${REPEAT_COUNT}"
  cmd=(
    bash "$RUNNER_SCRIPT"
    --model "$MODEL_ID"
    --aqli-bin "$AQLI_BIN"
    --out-dir "$sample_dir"
  )
  if (( ${#RUNNER_ARGS[@]} > 0 )); then
    cmd+=("${RUNNER_ARGS[@]}")
  fi

  set +e
  "${cmd[@]}" > "$stdout_file" 2> "$stderr_file"
  exit_code=$?
  set -e

  status="$(report_value status "$report_file")"
  recommendation="$(report_value recommendation "$report_file")"
  gated_metrics="$(report_value gatedMetrics "$report_file")"
  failed_metrics="$(report_value failedMetrics "$report_file")"
  largest_improvement="$(report_value largestImprovement "$report_file")"
  largest_regression="$(report_value largestRegression "$report_file")"
  ffn_transition="$(report_value ffnStrategyTransition "$report_file")"
  row_transition="$(report_value rowPrefillTransition "$report_file")"
  compare_markdown="$(report_value artifacts.compareMarkdown "$report_file")"
  compare_decision="$(report_value artifacts.compareDecision "$report_file")"

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$(safe_tsv_field "$sample")" \
    "$(safe_tsv_field "$exit_code")" \
    "$(safe_tsv_field "${status:-error}")" \
    "$(safe_tsv_field "${recommendation:-unknown}")" \
    "$(safe_tsv_field "${gated_metrics:-0}")" \
    "$(safe_tsv_field "${failed_metrics:-0}")" \
    "$(safe_tsv_field "$largest_improvement")" \
    "$(safe_tsv_field "$largest_regression")" \
    "$(safe_tsv_field "$ffn_transition")" \
    "$(safe_tsv_field "$row_transition")" \
    "$(safe_tsv_field "$sample_dir")" \
    "$(safe_tsv_field "$report_file")" \
    "$(safe_tsv_field "$compare_markdown")" \
    "$(safe_tsv_field "$compare_decision")" >> "$SAMPLES_TSV"

  append_sample_decision "$sample" "$compare_markdown" "$compare_decision"
  append_metric_rows "$sample" "$comparison_file"
done

if [[ -f "$METRIC_ROWS_TSV" ]]; then
  awk '
    BEGIN { FS = OFS = "\t" }
    function numeric(value) { return value ~ /^-?[0-9]+([.][0-9]+)?$/ }
    function abs(value) { return value < 0 ? -value : value }
    function clear_sorted(key) {
      for (key in sorted) {
        delete sorted[key]
      }
    }
    function bubble_sort(n, i, j, tmp) {
      for (i = 1; i <= n; i++) {
        for (j = i + 1; j <= n; j++) {
          if (sorted[j] < sorted[i]) {
            tmp = sorted[i]
            sorted[i] = sorted[j]
            sorted[j] = tmp
          }
        }
      }
    }
    function percentile_rank(n, percentile, raw) {
      raw = n * percentile
      return raw == int(raw) ? int(raw) : int(raw) + 1
    }
    function sort_delta(metric, n, i) {
      clear_sorted()
      for (i = 1; i <= n; i++) {
        sorted[i] = deltaValue[metric, i] + 0
      }
      bubble_sort(n)
    }
    function sort_percent(metric, n, i) {
      clear_sorted()
      for (i = 1; i <= n; i++) {
        sorted[i] = percentValue[metric, i] + 0
      }
      bubble_sort(n)
    }
    function sort_regression(metric, n, i) {
      clear_sorted()
      for (i = 1; i <= n; i++) {
        sorted[i] = regressionValue[metric, i] + 0
      }
      bubble_sort(n)
    }
    function median_delta(metric, n, mid) {
      n = samples[metric] + 0
      if (n == 0) return ""
      sort_delta(metric, n)
      mid = int((n + 1) / 2)
      return n % 2 ? sorted[mid] : (sorted[mid] + sorted[mid + 1]) / 2
    }
    function median_percent(metric, n, mid) {
      n = samples[metric] + 0
      if (n == 0) return ""
      sort_percent(metric, n)
      mid = int((n + 1) / 2)
      return n % 2 ? sorted[mid] : (sorted[mid] + sorted[mid + 1]) / 2
    }
    function p95_delta(metric, n, rank) {
      n = samples[metric] + 0
      if (n == 0) return ""
      sort_delta(metric, n)
      rank = percentile_rank(n, 0.95)
      if (rank < 1) rank = 1
      if (rank > n) rank = n
      return sorted[rank]
    }
    function p95_regression(metric, n, rank) {
      n = samples[metric] + 0
      if (n == 0) return ""
      sort_regression(metric, n)
      rank = percentile_rank(n, 0.95)
      if (rank < 1) rank = 1
      if (rank > n) rank = n
      return sorted[rank]
    }
    function stddev_delta(metric, n, mean, i, sumsq, diff) {
      n = samples[metric] + 0
      if (n <= 1) return 0
      mean = sumDelta[metric] / n
      for (i = 1; i <= n; i++) {
        diff = deltaValue[metric, i] - mean
        sumsq += diff * diff
      }
      return sqrt(sumsq / n)
    }
    function stddev_percent(metric, n, mean, i, sumsq, diff) {
      n = samples[metric] + 0
      if (n <= 1) return 0
      mean = sumPercent[metric] / n
      for (i = 1; i <= n; i++) {
        diff = percentValue[metric, i] - mean
        sumsq += diff * diff
      }
      return sqrt(sumsq / n)
    }
    function unstable_metric(metric, percentStddev) {
      if (samples[metric] + 0 <= 1) {
        return "false"
      }
      if (better[metric] + 0 > 0 && worse[metric] + 0 > 0) {
        return "true"
      }
      if (percentStddev + 0 > 10) {
        return "true"
      }
      return "false"
    }
    function remember(metric) {
      if (metric == "" || seen[metric]) {
        return
      }
      seen[metric] = 1
      order[++orderCount] = metric
    }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        col[$i] = i
      }
      print "metric", "samples", "better", "worse", "same", "avgDelta", "medianDelta", "p95Delta", "stddevDelta", "avgDeltaPercent", "medianDeltaPercent", "p95RegressionPercent", "stddevDeltaPercent", "maxImprovementPercent", "maxRegressionPercent", "failedGateCount", "unstable"
      next
    }
    {
      metric = $(col["metric"])
      direction = $(col["direction"])
      trend = $(col["trend"])
      gate = $(col["gateStatus"])
      delta = $(col["delta"])
      percent = $(col["deltaPercent"])
      remember(metric)
      if (numeric(delta) && numeric(percent)) {
        samples[metric]++
        deltaValue[metric, samples[metric]] = delta + 0
        percentValue[metric, samples[metric]] = percent + 0
        sumDelta[metric] += delta
        sumPercent[metric] += percent
        if (trend == "better") {
          better[metric]++
        } else if (trend == "worse") {
          worse[metric]++
        } else if (trend == "same") {
          same[metric]++
        }
        improvement = 0
        regression = 0
        if (direction == "higher") {
          if (percent + 0 > 0) improvement = percent + 0
          if (percent + 0 < 0) regression = abs(percent + 0)
        } else {
          if (percent + 0 < 0) improvement = abs(percent + 0)
          if (percent + 0 > 0) regression = percent + 0
        }
        if (improvement > maxImprovement[metric]) {
          maxImprovement[metric] = improvement
        }
        if (regression > maxRegression[metric]) {
          maxRegression[metric] = regression
        }
        regressionValue[metric, samples[metric]] = regression
      }
      if (gate == "fail") {
        failedGate[metric]++
      }
    }
    END {
      for (i = 1; i <= orderCount; i++) {
        metric = order[i]
        if (samples[metric] + 0 == 0) {
          continue
        }
        deltaStddev = stddev_delta(metric)
        percentStddev = stddev_percent(metric)
        print metric, samples[metric] + 0, better[metric] + 0, worse[metric] + 0, same[metric] + 0, sprintf("%.3f", sumDelta[metric] / samples[metric]), sprintf("%.3f", median_delta(metric)), sprintf("%.3f", p95_delta(metric)), sprintf("%.3f", deltaStddev), sprintf("%.3f", sumPercent[metric] / samples[metric]), sprintf("%.3f", median_percent(metric)), sprintf("%.3f", p95_regression(metric)), sprintf("%.3f", percentStddev), sprintf("%.3f", maxImprovement[metric] + 0), sprintf("%.3f", maxRegression[metric] + 0), failedGate[metric] + 0, unstable_metric(metric, percentStddev)
      }
    }
  ' "$METRIC_ROWS_TSV" > "$METRICS_TSV"
else
  printf 'metric\tsamples\tbetter\tworse\tsame\tavgDelta\tmedianDelta\tp95Delta\tstddevDelta\tavgDeltaPercent\tmedianDeltaPercent\tp95RegressionPercent\tstddevDeltaPercent\tmaxImprovementPercent\tmaxRegressionPercent\tfailedGateCount\tunstable\n' > "$METRICS_TSV"
fi

UNSTABLE_METRICS="$(awk 'BEGIN { FS = "\t" } NR > 1 && $NF == "true" { count++ } END { print count + 0 }' "$METRICS_TSV")"
awk \
  -v maxP95RegressionPercent="$MAX_P95_REGRESSION_PERCENT" \
  -v failUnstableMetrics="$FAIL_UNSTABLE_METRICS" \
  -v gateMetrics="$AGGREGATE_GATE_METRICS" '
      BEGIN { FS = OFS = "\t" }
      function parse_gate_metrics(metrics, count, parts, i) {
        gsub(/[[:space:]]+/, "", metrics)
        if (metrics == "" || metrics == "all") {
          gateAll = 1
          return
        }
        count = split(metrics, parts, ",")
        for (i = 1; i <= count; i++) {
          if (parts[i] != "") {
            gateMetric[parts[i]] = 1
          }
        }
      }
      function metric_allowed(metric) {
        return gateAll || gateMetric[metric]
      }
      BEGIN {
        parse_gate_metrics(gateMetrics)
        print "metric", "gate", "value", "threshold", "reason"
      }
      NR == 1 {
        for (i = 1; i <= NF; i++) {
          col[$i] = i
        }
        next
      }
      metric_allowed($(col["metric"])) && maxP95RegressionPercent != "" && $(col["p95RegressionPercent"]) + 0 > maxP95RegressionPercent + 0 {
        print $(col["metric"]), "p95-regression", $(col["p95RegressionPercent"]), maxP95RegressionPercent, "p95RegressionPercent=" $(col["p95RegressionPercent"]) " exceeded " maxP95RegressionPercent
      }
      metric_allowed($(col["metric"])) && failUnstableMetrics == "1" && $(col["unstable"]) == "true" {
        print $(col["metric"]), "unstable-metrics", $(col["stddevDeltaPercent"]), "stable", "metric has mixed better/worse samples or high variance"
      }
    ' "$METRICS_TSV" > "$AGGREGATE_GATE_FAILURES_TSV"

P95_REGRESSION_GATE_FAILURES="$(awk 'BEGIN { FS = "\t" } NR > 1 && $2 == "p95-regression" { count++ } END { print count + 0 }' "$AGGREGATE_GATE_FAILURES_TSV")"
UNSTABLE_GATE_FAILURES="$(awk 'BEGIN { FS = "\t" } NR > 1 && $2 == "unstable-metrics" { count++ } END { print count + 0 }' "$AGGREGATE_GATE_FAILURES_TSV")"
AGGREGATE_GATE_FAILURES=$((P95_REGRESSION_GATE_FAILURES + UNSTABLE_GATE_FAILURES))
AGGREGATE_GATE_REASON=""
if (( P95_REGRESSION_GATE_FAILURES > 0 )); then
  AGGREGATE_GATE_REASON="p95-regression"
fi
if (( UNSTABLE_GATE_FAILURES > 0 )); then
  if [[ -n "$AGGREGATE_GATE_REASON" ]]; then
    AGGREGATE_GATE_REASON="${AGGREGATE_GATE_REASON}; unstable-metrics"
  else
    AGGREGATE_GATE_REASON="unstable-metrics"
  fi
fi

awk \
  -v repeat="$REPEAT_COUNT" \
  -v unstableMetrics="$UNSTABLE_METRICS" \
  -v aggregateGateFailures="$AGGREGATE_GATE_FAILURES" \
  -v aggregateGateReason="$AGGREGATE_GATE_REASON" \
  -v p95RegressionGateFailures="$P95_REGRESSION_GATE_FAILURES" \
  -v unstableGateFailures="$UNSTABLE_GATE_FAILURES" \
  -v aggregateGateMetrics="$AGGREGATE_GATE_METRICS" \
  -v summaryOut="$SUMMARY_TSV" '
  BEGIN { FS = OFS = "\t" }
  function write_summary(key, value) { print key, value >> summaryOut }
  function append_reason(existing, nextReason) { return existing == "" ? nextReason : existing "; " nextReason }
  NR == 1 { next }
  {
    samples++
    exitCode = $2 + 0
    status = $3
    recommendation = $4
    failedMetrics += $6 + 0
    if (exitCode == 0 && status == "pass") {
      passedSamples++
    } else if (status == "error" || recommendation == "unknown") {
      errorSamples++
    } else {
      failedSamples++
    }
    if (recommendation == "promote-current") {
      promoteSamples++
    } else if (recommendation == "promote-current-with-watchlist") {
      watchlistSamples++
    } else if (recommendation == "hold-current") {
      holdSamples++
    } else if (recommendation == "reject-current") {
      rejectSamples++
    } else {
      collectSamples++
    }
  }
  END {
    print "key", "value" > summaryOut
    reason = ""
    if (failedSamples + errorSamples + rejectSamples > 0) {
      reason = append_reason(reason, "sample-regression")
    }
    if (aggregateGateReason != "") {
      reason = append_reason(reason, aggregateGateReason)
    }
    status = (failedSamples + errorSamples + rejectSamples + aggregateGateFailures == 0) ? "pass" : "fail"
    if (status == "fail") {
      recommendation = "reject-current"
    } else if (promoteSamples == samples) {
      recommendation = "promote-current"
    } else if (promoteSamples + watchlistSamples == samples) {
      recommendation = "promote-current-with-watchlist"
    } else if (promoteSamples + watchlistSamples > holdSamples + collectSamples) {
      recommendation = "promote-current-with-watchlist"
    } else if (holdSamples > 0) {
      recommendation = "hold-current"
    } else {
      recommendation = "collect-more-samples"
    }
    write_summary("status", status)
    write_summary("reason", reason)
    write_summary("recommendation", recommendation)
    write_summary("requestedSamples", repeat)
    write_summary("samples", samples + 0)
    write_summary("passedSamples", passedSamples + 0)
    write_summary("failedSamples", failedSamples + 0)
    write_summary("errorSamples", errorSamples + 0)
    write_summary("failedMetrics", failedMetrics + 0)
    write_summary("unstableMetrics", unstableMetrics + 0)
    write_summary("aggregateGateMetrics", aggregateGateMetrics)
    write_summary("aggregateGateFailures", aggregateGateFailures + 0)
    write_summary("p95RegressionGateFailures", p95RegressionGateFailures + 0)
    write_summary("unstableGateFailures", unstableGateFailures + 0)
    write_summary("promoteCurrentSamples", promoteSamples + 0)
    write_summary("watchlistSamples", watchlistSamples + 0)
    write_summary("holdSamples", holdSamples + 0)
    write_summary("rejectSamples", rejectSamples + 0)
    write_summary("collectMoreSamples", collectSamples + 0)
  }
' "$SAMPLES_TSV"

STATUS="$(awk 'BEGIN { FS = "\t" } $1 == "status" { print $2; exit }' "$SUMMARY_TSV")"
REASON="$(awk 'BEGIN { FS = "\t" } $1 == "reason" { print $2; exit }' "$SUMMARY_TSV")"
RECOMMENDATION="$(awk 'BEGIN { FS = "\t" } $1 == "recommendation" { print $2; exit }' "$SUMMARY_TSV")"
SAMPLES="$(awk 'BEGIN { FS = "\t" } $1 == "samples" { print $2; exit }' "$SUMMARY_TSV")"
PASSED_SAMPLES="$(awk 'BEGIN { FS = "\t" } $1 == "passedSamples" { print $2; exit }' "$SUMMARY_TSV")"
FAILED_SAMPLES="$(awk 'BEGIN { FS = "\t" } $1 == "failedSamples" { print $2; exit }' "$SUMMARY_TSV")"
ERROR_SAMPLES="$(awk 'BEGIN { FS = "\t" } $1 == "errorSamples" { print $2; exit }' "$SUMMARY_TSV")"
FAILED_METRICS="$(awk 'BEGIN { FS = "\t" } $1 == "failedMetrics" { print $2; exit }' "$SUMMARY_TSV")"
UNSTABLE_METRICS="$(awk 'BEGIN { FS = "\t" } $1 == "unstableMetrics" { print $2; exit }' "$SUMMARY_TSV")"
AGGREGATE_GATE_METRICS_SUMMARY="$(awk 'BEGIN { FS = "\t" } $1 == "aggregateGateMetrics" { print $2; exit }' "$SUMMARY_TSV")"
AGGREGATE_GATE_FAILURES="$(awk 'BEGIN { FS = "\t" } $1 == "aggregateGateFailures" { print $2; exit }' "$SUMMARY_TSV")"
P95_REGRESSION_GATE_FAILURES="$(awk 'BEGIN { FS = "\t" } $1 == "p95RegressionGateFailures" { print $2; exit }' "$SUMMARY_TSV")"
UNSTABLE_GATE_FAILURES="$(awk 'BEGIN { FS = "\t" } $1 == "unstableGateFailures" { print $2; exit }' "$SUMMARY_TSV")"

{
  echo "# Aqli Gemma4 Row-Prefill Repeat A/B"
  echo
  echo "| Field | Value |"
  echo "| --- | --- |"
  echo "| Status | ${STATUS} |"
  echo "| Recommendation | ${RECOMMENDATION} |"
  echo "| Reason | ${REASON:-n/a} |"
  echo "| Samples | ${SAMPLES} |"
  echo "| Passed Samples | ${PASSED_SAMPLES} |"
  echo "| Failed Samples | ${FAILED_SAMPLES} |"
  echo "| Error Samples | ${ERROR_SAMPLES} |"
  echo "| Failed Metrics | ${FAILED_METRICS} |"
  echo "| Unstable Metrics | ${UNSTABLE_METRICS} |"
  echo "| Aggregate Gate Metrics | ${AGGREGATE_GATE_METRICS_SUMMARY:-all} |"
  echo "| Aggregate Gate Failures | ${AGGREGATE_GATE_FAILURES} |"
  echo "| P95 Regression Gate Failures | ${P95_REGRESSION_GATE_FAILURES} |"
  echo "| Unstable Gate Failures | ${UNSTABLE_GATE_FAILURES} |"
  echo
  echo "## Aggregate Gate Failures"
  echo
  awk '
    BEGIN {
      FS = "\t"
      print "| Metric | Gate | Value | Threshold | Reason |"
      print "| --- | --- | ---: | ---: | --- |"
    }
    function esc(value) {
      gsub(/\|/, "\\|", value)
      return value
    }
    NR > 1 {
      found = 1
      printf "| `%s` | `%s` | %s | %s | %s |\n", esc($1), esc($2), esc($3), esc($4), esc($5)
    }
    END {
      if (!found) {
        print "| n/a | n/a | n/a | n/a | No aggregate gate failures. |"
      }
    }
  ' "$AGGREGATE_GATE_FAILURES_TSV"
  echo
  echo "## Metric Stats"
  echo
  awk '
    BEGIN {
      FS = "\t"
      print "| Metric | Samples | Better | Worse | Avg Delta | Median Delta | P95 Delta | Avg Delta % | P95 Regression % | Stddev % | Unstable |"
      print "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |"
    }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        col[$i] = i
      }
      next
    }
    {
      printf "| `%s` | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", \
        $(col["metric"]), $(col["samples"]), $(col["better"]), $(col["worse"]), \
        $(col["avgDelta"]), $(col["medianDelta"]), $(col["p95Delta"]), \
        $(col["avgDeltaPercent"]), $(col["p95RegressionPercent"]), \
        $(col["stddevDeltaPercent"]), $(col["unstable"])
    }
  ' "$METRICS_TSV"
  echo
  echo "## Samples"
  echo
  awk '
    BEGIN {
      FS = "\t"
      print "| Sample | Status | Recommendation | Report | Compare Markdown | Compare Decision |"
      print "| --- | --- | --- | --- | --- | --- |"
    }
    function esc(value) {
      gsub(/\|/, "\\|", value)
      return value
    }
    function code_or_na(value) {
      return value == "" ? "n/a" : "`" esc(value) "`"
    }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        col[$i] = i
      }
      next
    }
    {
      printf "| `%s` | `%s` | `%s` | %s | %s | %s |\n", \
        esc($(col["sample"])), esc($(col["status"])), esc($(col["recommendation"])), \
        code_or_na($(col["report"])), code_or_na($(col["compareMarkdown"])), code_or_na($(col["compareDecision"]))
    }
  ' "$SAMPLES_TSV"
  echo
  echo "## Artifacts"
  echo
  echo "- Config: \`${CONFIG_TSV}\`"
  echo "- Samples: \`${SAMPLES_TSV}\`"
  echo "- Sample decisions: \`${SAMPLE_DECISIONS_TSV}\`"
  echo "- Metric rows: \`${METRIC_ROWS_TSV}\`"
  echo "- Metrics: \`${METRICS_TSV}\`"
  echo "- Aggregate gate failures: \`${AGGREGATE_GATE_FAILURES_TSV}\`"
  echo "- Summary TSV: \`${SUMMARY_TSV}\`"
  echo "- Decision JSON: \`${DECISION_JSON}\`"
  echo "- Promotion policy JSON: \`${PROMOTION_POLICY_JSON}\`"
  echo "- Promotion policy TSV: \`${PROMOTION_POLICY_TSV}\`"
} > "$SUMMARY_MD"

if (( ${#RUNNER_ARGS[@]} == 0 )); then
  RUNNER_ARGS_JSON="[]"
else
  RUNNER_ARGS_JSON="$(jq -cn '$ARGS.positional' --args -- "${RUNNER_ARGS[@]}")"
fi
jq -n \
  --arg model "$MODEL_ID" \
  --arg aqliBin "$AQLI_BIN" \
  --arg runnerScript "$RUNNER_SCRIPT" \
  --arg repeat "$REPEAT_COUNT" \
  --arg maxP95RegressionPercent "$MAX_P95_REGRESSION_PERCENT" \
  --arg failUnstableMetrics "$FAIL_UNSTABLE_METRICS" \
  --arg aggregateGateMetrics "$AGGREGATE_GATE_METRICS" \
  --arg outDir "$OUT_DIR" \
  --arg samplesDir "$SAMPLES_DIR" \
  --arg configPath "$CONFIG_TSV" \
  --arg samplesPath "$SAMPLES_TSV" \
  --arg sampleDecisionsPath "$SAMPLE_DECISIONS_TSV" \
  --arg metricRowsPath "$METRIC_ROWS_TSV" \
  --arg metricsPath "$METRICS_TSV" \
  --arg aggregateGateFailuresPath "$AGGREGATE_GATE_FAILURES_TSV" \
  --arg summaryPath "$SUMMARY_TSV" \
  --arg markdownPath "$SUMMARY_MD" \
  --arg decisionPath "$DECISION_JSON" \
  --arg reportPath "$REPORT" \
  --argjson runnerArgs "$RUNNER_ARGS_JSON" \
  --rawfile config "$CONFIG_TSV" \
  --rawfile summary "$SUMMARY_TSV" \
  --rawfile samples "$SAMPLES_TSV" \
  --rawfile sampleDecisions "$SAMPLE_DECISIONS_TSV" \
  --rawfile metrics "$METRICS_TSV" \
  --rawfile failures "$AGGREGATE_GATE_FAILURES_TSV" '
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
    def b:
      . == "true";
    def maybe_b:
      if . == null or . == "" then null else . == "true" end;
    def gate_metrics($value):
      if $value == "" or $value == "all" then ["all"] else ($value | split(",")) end;
    def action($status; $recommendation):
      if $status == "fail" or $recommendation == "reject-current" then "reject"
      elif $recommendation == "promote-current" then "promote"
      elif $recommendation == "promote-current-with-watchlist" then "promote-with-watchlist"
      elif $recommendation == "hold-current" then "hold"
      else "collect-more-samples"
      end;

    kv($config) as $cfg
    | kv($summary) as $sum
    | (rows($samples)) as $sampleRows
    | (rows($sampleDecisions)) as $sampleDecisionRows
    | (rows($metrics)) as $metricRows
    | (rows($failures)) as $failureRows
    | ($sum.status // "") as $status
    | ($sum.recommendation // "") as $recommendation
    | {
        schemaVersion: 1,
        generatedAt: ($cfg.generatedAt // ""),
        model: $model,
        runner: {
          aqliBin: ($cfg.aqliBin // $aqliBin),
          runnerScript: ($cfg.runnerScript // $runnerScript),
          runnerArgs: $runnerArgs,
          repeat: (($cfg.repeat // $repeat) | n)
        },
        gates: {
          maxP95RegressionPercent: ($maxP95RegressionPercent | maybe_n),
          failUnstableMetrics: ($failUnstableMetrics == "1"),
          aggregateGateMetrics: gate_metrics($aggregateGateMetrics)
        },
        summary: {
          status: $status,
          reason: (($sum.reason // "") | maybe_s),
          recommendation: $recommendation,
          samples: {
            requested: (($sum.requestedSamples // "0") | n),
            total: (($sum.samples // "0") | n),
            passed: (($sum.passedSamples // "0") | n),
            failed: (($sum.failedSamples // "0") | n),
            error: (($sum.errorSamples // "0") | n)
          },
          metrics: {
            failed: (($sum.failedMetrics // "0") | n),
            unstable: (($sum.unstableMetrics // "0") | n)
          },
          gates: {
            aggregateMetrics: gate_metrics(($sum.aggregateGateMetrics // "")),
            aggregateFailures: (($sum.aggregateGateFailures // "0") | n),
            p95RegressionFailures: (($sum.p95RegressionGateFailures // "0") | n),
            unstableFailures: (($sum.unstableGateFailures // "0") | n)
          },
          recommendations: {
            promoteCurrent: (($sum.promoteCurrentSamples // "0") | n),
            watchlist: (($sum.watchlistSamples // "0") | n),
            hold: (($sum.holdSamples // "0") | n),
            reject: (($sum.rejectSamples // "0") | n),
            collectMore: (($sum.collectMoreSamples // "0") | n)
          }
        },
        policy: {
          canPromote: ($status == "pass" and ($recommendation == "promote-current" or $recommendation == "promote-current-with-watchlist")),
          action: action($status; $recommendation)
        },
        samples: ($sampleRows | map({
          sample,
          exitCode: (.exitCode | n),
          status,
          recommendation,
          gatedMetrics: (.gatedMetrics | n),
          failedMetrics: (.failedMetrics | n),
          largestImprovement: (.largestImprovement | maybe_s),
          largestRegression: (.largestRegression | maybe_s),
          ffnStrategyTransition: (.ffnStrategyTransition | maybe_s),
          rowPrefillTransition: (.rowPrefillTransition | maybe_s),
          sampleDir,
          report,
          compareMarkdown: (.compareMarkdown | maybe_s),
          compareDecision: (.compareDecision | maybe_s)
        })),
        sampleDecisions: ($sampleDecisionRows | map({
          sample,
          compareStatus: (.compareStatus | maybe_s),
          compareRecommendation: (.compareRecommendation | maybe_s),
          compareAction: (.compareAction | maybe_s),
          compareCanPromote: (.compareCanPromote | maybe_b),
          compareGatedMetrics: (.compareGatedMetrics | maybe_n),
          compareFailedMetrics: (.compareFailedMetrics | maybe_n),
          compareLargestImprovement: (.compareLargestImprovement | maybe_s),
          compareLargestRegression: (.compareLargestRegression | maybe_s),
          compareBaselineFfnStrategy: (.compareBaselineFfnStrategy | maybe_s),
          compareCurrentFfnStrategy: (.compareCurrentFfnStrategy | maybe_s),
          compareBaselineRowPrefill: (.compareBaselineRowPrefill | maybe_s),
          compareCurrentRowPrefill: (.compareCurrentRowPrefill | maybe_s),
          compareMarkdown: (.compareMarkdown | maybe_s),
          compareDecision: (.compareDecision | maybe_s)
        })),
        metrics: ($metricRows | map({
          metric,
          samples: (.samples | n),
          better: (.better | n),
          worse: (.worse | n),
          same: (.same | n),
          avgDelta: (.avgDelta | maybe_n),
          medianDelta: (.medianDelta | maybe_n),
          p95Delta: (.p95Delta | maybe_n),
          stddevDelta: (.stddevDelta | maybe_n),
          avgDeltaPercent: (.avgDeltaPercent | maybe_n),
          medianDeltaPercent: (.medianDeltaPercent | maybe_n),
          p95RegressionPercent: (.p95RegressionPercent | maybe_n),
          stddevDeltaPercent: (.stddevDeltaPercent | maybe_n),
          maxImprovementPercent: (.maxImprovementPercent | maybe_n),
          maxRegressionPercent: (.maxRegressionPercent | maybe_n),
          failedGateCount: (.failedGateCount | n),
          unstable: (.unstable | b)
        })),
        aggregateGateFailures: ($failureRows | map({
          metric,
          gate,
          value: (.value | maybe_n),
          threshold: (.threshold | maybe_n),
          reason
        })),
        artifacts: {
          outDir: $outDir,
          samplesDir: $samplesDir,
          config: $configPath,
          samples: $samplesPath,
          sampleDecisions: $sampleDecisionsPath,
          metricRows: $metricRowsPath,
          metrics: $metricsPath,
          aggregateGateFailures: $aggregateGateFailuresPath,
          summary: $summaryPath,
          markdown: $markdownPath,
          decision: $decisionPath,
          report: $reportPath
        }
      }
  ' > "$DECISION_JSON"

promotion_policy_cmd=(
  bash "$PROMOTION_POLICY_SCRIPT"
  --decision "$DECISION_JSON"
  --out "$PROMOTION_POLICY_JSON"
  --tsv-out "$PROMOTION_POLICY_TSV"
)
if (( REQUIRE_PROMOTION_CAN_PROMOTE == 1 )); then
  promotion_policy_cmd+=(--require-can-promote)
fi
if [[ -n "$REQUIRE_PROMOTION_ACTIONS" ]]; then
  promotion_policy_cmd+=(--require-action "$REQUIRE_PROMOTION_ACTIONS")
fi

set +e
"${promotion_policy_cmd[@]}"
PROMOTION_POLICY_STATUS=$?
set -e

PROMOTION_ACTION="$(jq -r '.action // ""' "$PROMOTION_POLICY_JSON")"
PROMOTION_CAN_PROMOTE="$(jq -r '.canPromote // false' "$PROMOTION_POLICY_JSON")"
PROMOTION_REQUIRES_WATCHLIST="$(jq -r '.requiresWatchlist // false' "$PROMOTION_POLICY_JSON")"
PROMOTION_REASONS="$(jq -r '.reasons | join("; ")' "$PROMOTION_POLICY_JSON")"
PROMOTION_WATCHLIST_SAMPLE_DECISIONS="$(jq -r '.sampleDecisionCounts.watchlist // 0' "$PROMOTION_POLICY_JSON")"
PROMOTION_MISSING_SAMPLE_DECISIONS="$(jq -r '.sampleDecisionCounts.missing // 0' "$PROMOTION_POLICY_JSON")"

{
  echo
  echo "## Promotion Policy"
  echo
  echo "| Field | Value |"
  echo "| --- | --- |"
  echo "| Action | ${PROMOTION_ACTION} |"
  echo "| Can Promote | ${PROMOTION_CAN_PROMOTE} |"
  echo "| Requires Watchlist | ${PROMOTION_REQUIRES_WATCHLIST} |"
  echo "| Policy Exit Code | ${PROMOTION_POLICY_STATUS} |"
  echo "| Reasons | ${PROMOTION_REASONS:-n/a} |"
  echo "| Watchlist Sample Decisions | ${PROMOTION_WATCHLIST_SAMPLE_DECISIONS} |"
  echo "| Missing Sample Decisions | ${PROMOTION_MISSING_SAMPLE_DECISIONS} |"
} >> "$SUMMARY_MD"

{
  echo "Aqli Gemma4 row-prefill repeat A/B"
  echo "outDir=$OUT_DIR"
  echo "samplesDir=$SAMPLES_DIR"
  echo "artifacts.config=$CONFIG_TSV"
  echo "artifacts.samples=$SAMPLES_TSV"
  echo "artifacts.sampleDecisions=$SAMPLE_DECISIONS_TSV"
  echo "artifacts.metricRows=$METRIC_ROWS_TSV"
  echo "artifacts.metrics=$METRICS_TSV"
  echo "artifacts.aggregateGateFailures=$AGGREGATE_GATE_FAILURES_TSV"
  echo "artifacts.summary=$SUMMARY_TSV"
  echo "artifacts.markdown=$SUMMARY_MD"
  echo "artifacts.decision=$DECISION_JSON"
  echo "artifacts.promotionPolicy=$PROMOTION_POLICY_JSON"
  echo "artifacts.promotionPolicyTsv=$PROMOTION_POLICY_TSV"
  echo "status=$STATUS"
  echo "reason=$REASON"
  echo "recommendation=$RECOMMENDATION"
  echo "promotionAction=$PROMOTION_ACTION"
  echo "promotionCanPromote=$PROMOTION_CAN_PROMOTE"
  echo "promotionRequiresWatchlist=$PROMOTION_REQUIRES_WATCHLIST"
  echo "promotionPolicyExitCode=$PROMOTION_POLICY_STATUS"
  echo "promotionReasons=$PROMOTION_REASONS"
  echo "samples=$SAMPLES"
  echo "passedSamples=$PASSED_SAMPLES"
  echo "failedSamples=$FAILED_SAMPLES"
  echo "errorSamples=$ERROR_SAMPLES"
  echo "failedMetrics=$FAILED_METRICS"
  echo "unstableMetrics=$UNSTABLE_METRICS"
  echo "aggregateGateMetrics=$AGGREGATE_GATE_METRICS_SUMMARY"
  echo "aggregateGateFailures=$AGGREGATE_GATE_FAILURES"
  echo "p95RegressionGateFailures=$P95_REGRESSION_GATE_FAILURES"
  echo "unstableGateFailures=$UNSTABLE_GATE_FAILURES"
} | tee "$REPORT"

if [[ "$STATUS" == "fail" || "$PROMOTION_POLICY_STATUS" -ne 0 ]]; then
  exit 1
fi
