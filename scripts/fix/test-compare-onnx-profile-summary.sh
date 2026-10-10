#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-onnx-profile-compare.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

BASELINE="${TMP_DIR}/baseline.tsv"
CURRENT_PASS="${TMP_DIR}/current-pass.tsv"
CURRENT_FAIL="${TMP_DIR}/current-fail.tsv"

write_header() {
  printf 'case\tstatus\texitCode\tdurationMs\tpromptEvalTps\tgenerationTps\tdecodeTps\tttftMs\ttokenLatencyMs\tonnxBackend\tonnxTokenizeMs\tonnxInputPrepMs\tonnxPrefillRunMs\tonnxDecodeRunMs\tonnxOrtRunMs\tonnxLogitsSelectMs\tonnxSamplingMs\tonnxSteps\tcombinedLog\tstderrLog\n'
}

{
  write_header
  printf 'warmup-1\tpass\t0\t2000\t10\t2\t4\t400\t120\tCPU\t5\t5\t50\t60\t110\t6\t8\t1\t/tmp/warmup-base.log\t/tmp/warmup-base.err\n'
  printf 'run-1\tpass\t0\t1000\t20\t10\t8\t200\t50\tCoreML\t2\t3\t20\t30\t60\t4\t5\t2\t/tmp/run-base.log\t/tmp/run-base.err\n'
  printf 'run-2\tpass\t0\t1500\t20\t8\t7\t260\t70\tCoreML\t3\t4\t30\t40\t70\t5\t6\t2\t/tmp/run2-base.log\t/tmp/run2-base.err\n'
} > "$BASELINE"

{
  write_header
  printf 'warmup-1\tpass\t0\t4000\t4\t1\t2\t900\t300\tCPU\t10\t10\t90\t100\t200\t9\t12\t1\t/tmp/warmup-current.log\t/tmp/warmup-current.err\n'
  printf 'run-2\tpass\t0\t1510\t20\t7.9\t7\t270\t71\tCoreML\t3.1\t4.1\t31\t40.5\t71\t5.1\t6.1\t2\t/tmp/run2-current.log\t/tmp/run2-current.err\n'
  printf 'run-1\tpass\t0\t1080\t19.7\t9.5\t7.8\t210\t52\tCoreML\t2.1\t3.2\t21\t31\t61\t4.1\t5.2\t2\t/tmp/run-current.log\t/tmp/run-current.err\n'
} > "$CURRENT_PASS"

{
  write_header
  printf 'run-1\tpass\t0\t1250\t20\t6\t6\t260\t75\tCoreML\t2\t3\t20\t45\t90\t4\t5\t2\t/tmp/run-fail.log\t/tmp/run-fail.err\n'
} > "$CURRENT_FAIL"

PASS_DIR="${TMP_DIR}/pass"
bash "$ROOT_DIR/scripts/compare-onnx-profile-summary.sh" \
  --baseline "$BASELINE" \
  --current "$CURRENT_PASS" \
  --summary-dir "$PASS_DIR" \
  --max-regression-percent 10 \
  --metrics durationMs,generationTps,onnxDecodeRunMs >/dev/null

if grep -q '^warmup-1	' "$PASS_DIR/comparison.tsv"; then
  echo "Expected warmup rows to be skipped by default" >&2
  cat "$PASS_DIR/comparison.tsv" >&2
  exit 1
fi
if ! grep -qx $'run-1\tdurationMs\t1000\t1080\tlower-is-better\t8.000\t10\tpass\t' "$PASS_DIR/comparison.tsv" \
    || ! grep -qx $'run-1\tgenerationTps\t10\t9.5\thigher-is-better\t5.000\t10\tpass\t' "$PASS_DIR/comparison.tsv" \
    || ! grep -qx $'run-1\tonnxDecodeRunMs\t30\t31\tlower-is-better\t3.333\t10\tpass\t' "$PASS_DIR/comparison.tsv"; then
  echo "Expected within-limit comparison rows" >&2
  cat "$PASS_DIR/comparison.tsv" >&2
  exit 1
fi
if [[ "$(sed -n '2p' "$PASS_DIR/comparison.tsv")" != $'run-2\tdurationMs\t1500\t1510\tlower-is-better\t0.667\t10\tpass\t' ]] \
    || [[ "$(sed -n '5p' "$PASS_DIR/comparison.tsv")" != $'run-1\tdurationMs\t1000\t1080\tlower-is-better\t8.000\t10\tpass\t' ]] \
    || ! grep -Fqx $'rowOrder\tcurrent-summary' "$PASS_DIR/config.tsv"; then
  echo "Expected deterministic current-summary comparison row order" >&2
  cat "$PASS_DIR/config.tsv" >&2
  cat "$PASS_DIR/comparison.tsv" >&2
  exit 1
fi
if [[ ! -f "$PASS_DIR/metric-summary.tsv" ]] \
    || [[ ! -f "$PASS_DIR/results.tsv" ]] \
    || [[ ! -f "$PASS_DIR/decision.tsv" ]] \
    || [[ ! -f "$PASS_DIR/decision.json" ]] \
    || [[ ! -f "$PASS_DIR/bundle.tsv" ]] \
    || [[ ! -f "$PASS_DIR/bundle.json" ]] \
    || ! grep -Fqx "artifacts.metricSummary=$PASS_DIR/metric-summary.tsv" "$PASS_DIR/report.txt" \
    || ! grep -Fqx "artifacts.results=$PASS_DIR/results.tsv" "$PASS_DIR/report.txt" \
    || ! grep -Fqx "artifacts.decision=$PASS_DIR/decision.tsv" "$PASS_DIR/report.txt" \
    || ! grep -Fqx "artifacts.decisionJson=$PASS_DIR/decision.json" "$PASS_DIR/report.txt" \
    || ! grep -Fqx "artifacts.bundle=$PASS_DIR/bundle.tsv" "$PASS_DIR/report.txt" \
    || ! grep -Fqx "artifacts.bundleJson=$PASS_DIR/bundle.json" "$PASS_DIR/report.txt" \
    || ! grep -Fqx $'results\t'"$PASS_DIR/results.tsv" "$PASS_DIR/config.tsv" \
    || ! grep -Fqx $'comparison\t'"$PASS_DIR/comparison.tsv" "$PASS_DIR/config.tsv" \
    || ! grep -Fqx $'decision\t'"$PASS_DIR/decision.tsv" "$PASS_DIR/config.tsv" \
    || ! grep -Fqx $'decisionJson\t'"$PASS_DIR/decision.json" "$PASS_DIR/config.tsv" \
    || ! grep -Fqx $'bundle\t'"$PASS_DIR/bundle.tsv" "$PASS_DIR/config.tsv" \
    || ! grep -Fqx $'bundleJson\t'"$PASS_DIR/bundle.json" "$PASS_DIR/config.tsv" \
    || ! grep -Fqx $'metricSummary\t'"$PASS_DIR/metric-summary.tsv" "$PASS_DIR/config.tsv" \
    || ! grep -qx $'metric\tcases\tcompared\tpasses\tfailures\tskips\tworstCase\tworstRegressionPercent\tmaxRegressionPercent\tstatus' "$PASS_DIR/metric-summary.tsv" \
    || ! grep -qx $'durationMs\t2\t2\t2\t0\t0\trun-1\t8.000\t10\tpass' "$PASS_DIR/metric-summary.tsv" \
    || ! grep -qx $'generationTps\t2\t2\t2\t0\t0\trun-1\t5.000\t10\tpass' "$PASS_DIR/metric-summary.tsv" \
    || ! grep -qx $'onnxDecodeRunMs\t2\t2\t2\t0\t0\trun-1\t3.333\t10\tpass' "$PASS_DIR/metric-summary.tsv"; then
  echo "Expected per-metric comparison summary artifact" >&2
  cat "$PASS_DIR/report.txt" >&2
  cat "$PASS_DIR/config.tsv" >&2
  cat "$PASS_DIR/metric-summary.tsv" >&2
  exit 1
fi
if ! grep -qx $'kind\tpath\trequired\tstatus\tdescription' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'config\t'"$PASS_DIR"'/config.tsv\trequired\tpresent\tComparison configuration' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'results\t'"$PASS_DIR"'/results.tsv\trequired\tpresent\tStage results' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'comparison\t'"$PASS_DIR"'/comparison.tsv\trequired\tpresent\tRow-level baseline comparison' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'comparisonTable\t'"$PASS_DIR"'/comparison-table.txt\toptional\tpresent\tHuman-readable baseline comparison' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'metricSummary\t'"$PASS_DIR"'/metric-summary.tsv\toptional\tpresent\tPer-metric comparison summary' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'decision\t'"$PASS_DIR"'/decision.tsv\trequired\tpresent\tComparison decision summary' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'decisionJson\t'"$PASS_DIR"'/decision.json\toptional\tpresent\tComparison decision JSON' "$PASS_DIR/bundle.tsv"; then
  echo "Expected comparison artifact bundle rows" >&2
  cat "$PASS_DIR/bundle.tsv" >&2
  exit 1
fi
if ! grep -Fq '"kind": "config"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"kind": "decisionJson"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"total": "7"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"requiredMissing": "0"' "$PASS_DIR/bundle.json"; then
  echo "Expected comparison bundle JSON summary" >&2
  cat "$PASS_DIR/bundle.json" >&2
  exit 1
fi
if ! grep -qx $'stage\tstatus\texitCode\tartifact\tstdout\tstderr\treason' "$PASS_DIR/results.tsv" \
    || ! grep -qx $'compare\tpass\t0\t'"$PASS_DIR"'/comparison.tsv\t\t\t' "$PASS_DIR/results.tsv" \
    || ! grep -qx $'key\tvalue' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'status\tpass' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'failedStages\t0' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'lastStage\tcompare' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'regressionFailures\t0' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'regressionCompared\t6' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'worstRegressionMetric\tdurationMs' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'worstRegressionPercent\t8.000' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'bundleArtifacts\t7' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'bundleRequiredMissing\t0' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'bundle\t'"$PASS_DIR"'/bundle.tsv' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'comparison\t'"$PASS_DIR"'/comparison.tsv' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'metricSummary\t'"$PASS_DIR"'/metric-summary.tsv' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'json\t'"$PASS_DIR"'/decision.json' "$PASS_DIR/decision.tsv"; then
  echo "Expected passing comparison decision summary" >&2
  cat "$PASS_DIR/results.tsv" >&2
  cat "$PASS_DIR/decision.tsv" >&2
  exit 1
fi
if [[ ! -f "$PASS_DIR/comparison-table.txt" ]] \
    || ! grep -Fqx "artifacts.table=$PASS_DIR/comparison-table.txt" "$PASS_DIR/report.txt" \
    || ! grep -Fqx $'table\t'"$PASS_DIR/comparison-table.txt" "$PASS_DIR/config.tsv" \
    || ! grep -q '^case[[:space:]][[:space:]]*metric[[:space:]][[:space:]]*baseline[[:space:]][[:space:]]*current[[:space:]][[:space:]]*regression%' "$PASS_DIR/comparison-table.txt" \
    || ! grep -q '^run-1[[:space:]][[:space:]]*durationMs[[:space:]][[:space:]]*1000[[:space:]][[:space:]]*1080[[:space:]][[:space:]]*8.000[[:space:]][[:space:]]*10[[:space:]][[:space:]]*pass' "$PASS_DIR/comparison-table.txt"; then
  echo "Expected default human-readable comparison table artifact" >&2
  cat "$PASS_DIR/report.txt" >&2
  cat "$PASS_DIR/config.tsv" >&2
  cat "$PASS_DIR/comparison-table.txt" >&2
  exit 1
fi

CUSTOM_TABLE_DIR="${TMP_DIR}/custom-table"
CUSTOM_TABLE="${TMP_DIR}/nested/custom-comparison.txt"
CUSTOM_METRIC_SUMMARY="${TMP_DIR}/nested/custom-metric-summary.tsv"
CUSTOM_DECISION="${TMP_DIR}/nested/custom-decision.tsv"
bash "$ROOT_DIR/scripts/compare-onnx-profile-summary.sh" \
  --baseline "$BASELINE" \
  --current "$CURRENT_PASS" \
  --summary-dir "$CUSTOM_TABLE_DIR" \
  --table-out "$CUSTOM_TABLE" \
  --metric-summary-out "$CUSTOM_METRIC_SUMMARY" \
  --decision-out "$CUSTOM_DECISION" \
  --max-regression-percent 10 \
  --metrics durationMs >/dev/null

if [[ ! -f "$CUSTOM_TABLE" ]] \
    || [[ ! -f "$CUSTOM_METRIC_SUMMARY" ]] \
    || [[ ! -f "$CUSTOM_DECISION" ]] \
    || [[ ! -f "${CUSTOM_DECISION%.tsv}.json" ]] \
    || [[ ! -f "$CUSTOM_TABLE_DIR/bundle.tsv" ]] \
    || [[ ! -f "$CUSTOM_TABLE_DIR/bundle.json" ]] \
    || ! grep -Fqx "artifacts.table=$CUSTOM_TABLE" "$CUSTOM_TABLE_DIR/report.txt" \
    || ! grep -Fqx "artifacts.metricSummary=$CUSTOM_METRIC_SUMMARY" "$CUSTOM_TABLE_DIR/report.txt" \
    || ! grep -Fqx "artifacts.decision=$CUSTOM_DECISION" "$CUSTOM_TABLE_DIR/report.txt" \
    || ! grep -Fqx "artifacts.decisionJson=${CUSTOM_DECISION%.tsv}.json" "$CUSTOM_TABLE_DIR/report.txt" \
    || ! grep -Fqx "artifacts.bundle=$CUSTOM_TABLE_DIR/bundle.tsv" "$CUSTOM_TABLE_DIR/report.txt" \
    || ! grep -Fqx "artifacts.bundleJson=$CUSTOM_TABLE_DIR/bundle.json" "$CUSTOM_TABLE_DIR/report.txt" \
    || ! grep -Fqx $'table\t'"$CUSTOM_TABLE" "$CUSTOM_TABLE_DIR/config.tsv" \
    || ! grep -Fqx $'metricSummary\t'"$CUSTOM_METRIC_SUMMARY" "$CUSTOM_TABLE_DIR/config.tsv" \
    || ! grep -Fqx $'decision\t'"$CUSTOM_DECISION" "$CUSTOM_TABLE_DIR/config.tsv" \
    || ! grep -Fqx $'decisionJson\t'"${CUSTOM_DECISION%.tsv}.json" "$CUSTOM_TABLE_DIR/config.tsv" \
    || ! grep -Fqx $'bundleJson\t'"$CUSTOM_TABLE_DIR/bundle.json" "$CUSTOM_TABLE_DIR/config.tsv" \
    || ! grep -qx $'decision\t'"$CUSTOM_DECISION"'\trequired\tpresent\tComparison decision summary' "$CUSTOM_TABLE_DIR/bundle.tsv" \
    || ! grep -qx $'decisionJson\t'"${CUSTOM_DECISION%.tsv}.json"$'\toptional\tpresent\tComparison decision JSON' "$CUSTOM_TABLE_DIR/bundle.tsv" \
    || ! grep -Fq '"path": "'"$CUSTOM_DECISION"'"' "$CUSTOM_TABLE_DIR/bundle.json"; then
  echo "Expected custom human-readable comparison artifacts" >&2
  cat "$CUSTOM_TABLE_DIR/report.txt" >&2
  cat "$CUSTOM_TABLE_DIR/config.tsv" >&2
  [[ -f "$CUSTOM_TABLE_DIR/bundle.tsv" ]] && cat "$CUSTOM_TABLE_DIR/bundle.tsv" >&2
  [[ -f "$CUSTOM_TABLE" ]] && cat "$CUSTOM_TABLE" >&2
  [[ -f "$CUSTOM_METRIC_SUMMARY" ]] && cat "$CUSTOM_METRIC_SUMMARY" >&2
  [[ -f "$CUSTOM_DECISION" ]] && cat "$CUSTOM_DECISION" >&2
  exit 1
fi

WARMUP_DIR="${TMP_DIR}/warmup"
bash "$ROOT_DIR/scripts/compare-onnx-profile-summary.sh" \
  --baseline "$BASELINE" \
  --current "$CURRENT_PASS" \
  --summary-dir "$WARMUP_DIR" \
  --max-regression-percent 200 \
  --metrics durationMs \
  --include-warmup >/dev/null

if ! grep -qx $'warmup-1\tdurationMs\t2000\t4000\tlower-is-better\t100.000\t200\tpass\t' "$WARMUP_DIR/comparison.tsv"; then
  echo "Expected opt-in warmup comparison" >&2
  cat "$WARMUP_DIR/comparison.tsv" >&2
  exit 1
fi

FAIL_DIR="${TMP_DIR}/fail"
if bash "$ROOT_DIR/scripts/compare-onnx-profile-summary.sh" \
    --baseline "$BASELINE" \
    --current "$CURRENT_FAIL" \
    --summary-dir "$FAIL_DIR" \
    --max-regression-percent 10 \
    --metrics durationMs,generationTps,onnxDecodeRunMs >"${TMP_DIR}/fail.out"; then
  echo "Expected comparison to fail on regressions" >&2
  cat "${TMP_DIR}/fail.out" >&2
  exit 1
fi

if ! grep -qx $'run-1\tdurationMs\t1000\t1250\tlower-is-better\t25.000\t10\tfail\t' "$FAIL_DIR/comparison.tsv" \
    || ! grep -qx $'run-1\tgenerationTps\t10\t6\thigher-is-better\t40.000\t10\tfail\t' "$FAIL_DIR/comparison.tsv" \
    || ! grep -qx $'run-1\tonnxDecodeRunMs\t30\t45\tlower-is-better\t50.000\t10\tfail\t' "$FAIL_DIR/comparison.tsv" \
    || ! grep -q '^failures=3$' "${TMP_DIR}/fail.out" \
    || ! grep -qx $'durationMs\t1\t1\t0\t1\t0\trun-1\t25.000\t10\tfail' "$FAIL_DIR/metric-summary.tsv" \
    || ! grep -qx $'generationTps\t1\t1\t0\t1\t0\trun-1\t40.000\t10\tfail' "$FAIL_DIR/metric-summary.tsv" \
    || ! grep -qx $'onnxDecodeRunMs\t1\t1\t0\t1\t0\trun-1\t50.000\t10\tfail' "$FAIL_DIR/metric-summary.tsv" \
    || ! grep -q '^failingMetrics:$' "${TMP_DIR}/fail.out" \
    || ! grep -q '^  generationTps failures=1 worst=40.000% worstCase=run-1$' "${TMP_DIR}/fail.out" \
    || ! grep -Fqx "artifacts.decision=$FAIL_DIR/decision.tsv" "${TMP_DIR}/fail.out" \
    || ! grep -Fqx "artifacts.decisionJson=$FAIL_DIR/decision.json" "${TMP_DIR}/fail.out" \
    || ! grep -Fqx "artifacts.bundle=$FAIL_DIR/bundle.tsv" "${TMP_DIR}/fail.out" \
    || ! grep -Fqx "artifacts.bundleJson=$FAIL_DIR/bundle.json" "${TMP_DIR}/fail.out" \
    || ! grep -qx $'comparison\t'"$FAIL_DIR"'/comparison.tsv\trequired\tpresent\tRow-level baseline comparison' "$FAIL_DIR/bundle.tsv" \
    || ! grep -qx $'decision\t'"$FAIL_DIR"'/decision.tsv\trequired\tpresent\tComparison decision summary' "$FAIL_DIR/bundle.tsv" \
    || ! grep -qx $'decisionJson\t'"$FAIL_DIR"'/decision.json\toptional\tpresent\tComparison decision JSON' "$FAIL_DIR/bundle.tsv"; then
  echo "Expected failed regression evidence" >&2
  cat "$FAIL_DIR/comparison.tsv" >&2
  cat "$FAIL_DIR/metric-summary.tsv" >&2
  cat "$FAIL_DIR/bundle.tsv" >&2
  cat "${TMP_DIR}/fail.out" >&2
  exit 1
fi
if ! grep -Fq '"kind": "comparison"' "$FAIL_DIR/bundle.json" \
    || ! grep -Fq '"requiredMissing": "0"' "$FAIL_DIR/bundle.json"; then
  echo "Expected failed comparison bundle JSON summary" >&2
  cat "$FAIL_DIR/bundle.json" >&2
  exit 1
fi
if ! grep -qx $'compare\tfail\t42\t'"$FAIL_DIR"'/comparison.tsv\t\t\tregression-detected' "$FAIL_DIR/results.tsv" \
    || ! grep -qx $'status\tfail' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'failureStage\tcompare' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'failureReason\tregression-detected' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'regressionFailures\t3' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'worstRegressionMetric\tonnxDecodeRunMs' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'worstRegressionPercent\t50.000' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'bundleArtifacts\t7' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'bundleRequiredMissing\t0' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'bundle\t'"$FAIL_DIR"'/bundle.tsv' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'json\t'"$FAIL_DIR"'/decision.json' "$FAIL_DIR/decision.tsv"; then
  echo "Expected failed comparison decision summary" >&2
  cat "$FAIL_DIR/results.tsv" >&2
  cat "$FAIL_DIR/decision.tsv" >&2
  exit 1
fi

MISSING_DIR="${TMP_DIR}/missing"
bash "$ROOT_DIR/scripts/compare-onnx-profile-summary.sh" \
  --baseline "$BASELINE" \
  --current "$CURRENT_PASS" \
  --summary-dir "$MISSING_DIR" \
  --metrics onnxMissingMetric >/dev/null

if ! grep -qx $'run-1\tonnxMissingMetric\t\t\tlower-is-better\t\t10\tskip\tmissing-metric' "$MISSING_DIR/comparison.tsv"; then
  echo "Expected missing metrics to skip by default" >&2
  cat "$MISSING_DIR/comparison.tsv" >&2
  exit 1
fi
if ! grep -qx $'onnxMissingMetric\t2\t0\t0\t0\t2\t\t\t10\tskip' "$MISSING_DIR/metric-summary.tsv"; then
  echo "Expected missing metrics to be summarized as skipped" >&2
  cat "$MISSING_DIR/metric-summary.tsv" >&2
  exit 1
fi
if ! grep -qx $'status\tpass' "$MISSING_DIR/decision.tsv" \
    || ! grep -qx $'regressionSkips\t2' "$MISSING_DIR/decision.tsv" \
    || ! grep -qx $'regressionCompared\t0' "$MISSING_DIR/decision.tsv"; then
  echo "Expected skipped comparison decision summary" >&2
  cat "$MISSING_DIR/decision.tsv" >&2
  exit 1
fi

printf 'ONNX profile summary comparison test passed\n'
