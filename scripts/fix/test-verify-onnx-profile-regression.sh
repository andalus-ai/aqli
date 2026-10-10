#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-onnx-profile-regression.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

FAKE_AQLI="${TMP_DIR}/aqli"
cat > "$FAKE_AQLI" <<'SH'
#!/usr/bin/env bash
exit 0
SH
chmod +x "$FAKE_AQLI"

write_header() {
  printf 'case\tstatus\texitCode\tdurationMs\tpromptEvalTps\tgenerationTps\tdecodeTps\tttftMs\ttokenLatencyMs\tonnxBackend\tonnxTokenizeMs\tonnxInputPrepMs\tonnxPrefillRunMs\tonnxDecodeRunMs\tonnxOrtRunMs\tonnxLogitsSelectMs\tonnxSamplingMs\tonnxSteps\tcombinedLog\tstderrLog\n'
}

BASELINE_RAW="${TMP_DIR}/baseline-raw.tsv"
{
  write_header
  printf 'warmup-1\tpass\t0\t2200\t8\t4\t3\t500\t140\tCPU\t6\t6\t60\t100\t150\t9\t10\t1\t/tmp/warmup-base.log\t/tmp/warmup-base.err\n'
  printf 'run-1\tpass\t0\t1000\t20\t10\t8\t200\t50\tCoreML\t2\t3\t20\t30\t60\t4\t5\t2\t/tmp/run-base-1.log\t/tmp/run-base-1.err\n'
  printf 'run-2\tpass\t0\t1200\t18\t8\t7\t240\t60\tCoreML\t3\t5\t25\t45\t80\t6\t7\t3\t/tmp/run-base-2.log\t/tmp/run-base-2.err\n'
} > "$BASELINE_RAW"

GOOD_MANIFEST="${TMP_DIR}/baseline-good-manifest.tsv"
{
  printf 'key\tvalue\n'
  printf 'model\tfake-model\n'
  printf 'prompt\twhere is jakarta\n'
  printf 'maxTokens\t8\n'
  printf 'aggregateLabel\tmeasured\n'
  printf 'includeWarmupAggregate\t0\n'
} > "$GOOD_MANIFEST"

BACKEND_MANIFEST="${TMP_DIR}/baseline-backend-manifest.tsv"
{
  printf 'key\tvalue\n'
  printf 'model\tfake-model\n'
  printf 'prompt\twhere is jakarta\n'
  printf 'maxTokens\t8\n'
  printf 'aggregateLabel\tmeasured\n'
  printf 'includeWarmupAggregate\t0\n'
  printf 'onnxBackend\tCoreML\n'
} > "$BACKEND_MANIFEST"

BAD_MANIFEST="${TMP_DIR}/baseline-bad-manifest.tsv"
{
  printf 'key\tvalue\n'
  printf 'model\tfake-model\n'
  printf 'prompt\tdifferent prompt\n'
  printf 'maxTokens\t4\n'
  printf 'aggregateLabel\tmeasured\n'
  printf 'includeWarmupAggregate\t0\n'
} > "$BAD_MANIFEST"

BAD_ENVIRONMENT="${TMP_DIR}/baseline-bad-environment.tsv"
{
  printf 'key\tvalue\n'
  printf 'hostOs\tdefinitely-not-aqli-os\n'
  printf 'hostArch\tdefinitely-not-aqli-arch\n'
  printf 'javaVersion\tdefinitely-not-aqli-java\n'
  printf 'aqliJavaOpts\tdefinitely-not-aqli-java-opts\n'
  printf 'aqliBin\t/definitely/not/aqli\n'
} > "$BAD_ENVIRONMENT"

BAD_ENV_MANIFEST="${TMP_DIR}/baseline-bad-env-manifest.tsv"
{
  printf 'key\tvalue\n'
  printf 'model\tfake-model\n'
  printf 'prompt\twhere is jakarta\n'
  printf 'maxTokens\t8\n'
  printf 'aggregateLabel\tmeasured\n'
  printf 'includeWarmupAggregate\t0\n'
  printf 'environment\t%s\n' "$BAD_ENVIRONMENT"
} > "$BAD_ENV_MANIFEST"

FAKE_BENCH="${TMP_DIR}/bench-onnx-profile.sh"
cat > "$FAKE_BENCH" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

out_dir=""
label=""
expect_backend=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --out-dir) out_dir="$2"; shift 2 ;;
    --label) label="$2"; shift 2 ;;
    --expect-backend) expect_backend="$2"; shift 2 ;;
    *) shift ;;
  esac
done
run_dir="${out_dir}/${label}"
mkdir -p "${run_dir}/logs"
summary="${run_dir}/summary.tsv"
backend="${AQLI_FAKE_ONNX_BACKEND:-CoreML}"
printf 'case\tstatus\texitCode\tdurationMs\tpromptEvalTps\tgenerationTps\tdecodeTps\tttftMs\ttokenLatencyMs\tonnxBackend\tonnxTokenizeMs\tonnxInputPrepMs\tonnxPrefillRunMs\tonnxDecodeRunMs\tonnxOrtRunMs\tonnxLogitsSelectMs\tonnxSamplingMs\tonnxSteps\tcombinedLog\tstderrLog\n' > "$summary"
printf 'warmup-1\tpass\t0\t2100\t8\t4\t3\t500\t140\tCPU\t6\t6\t60\t100\t150\t9\t10\t1\t%s\t%s\n' "${run_dir}/logs/warmup.log" "${run_dir}/logs/warmup.err" >> "$summary"
case "${AQLI_FAKE_ONNX_REGRESSION:-pass}" in
  fail)
    printf 'run-1\tpass\t0\t1400\t20\t6\t6\t300\t90\t%s\t4\t7\t35\t60\t100\t8\t9\t2\t%s\t%s\n' "$backend" "${run_dir}/logs/run-fail-1.log" "${run_dir}/logs/run-fail-1.err" >> "$summary"
    printf 'run-2\tpass\t0\t1500\t19\t5\t5\t320\t95\t%s\t5\t8\t40\t70\t110\t9\t10\t3\t%s\t%s\n' "$backend" "${run_dir}/logs/run-fail-2.log" "${run_dir}/logs/run-fail-2.err" >> "$summary"
    ;;
  *)
    printf 'run-1\tpass\t0\t1080\t20\t9.5\t8.1\t210\t52\t%s\t2.1\t3.1\t21\t31\t61\t4.1\t5.1\t2\t%s\t%s\n' "$backend" "${run_dir}/logs/run-pass-1.log" "${run_dir}/logs/run-pass-1.err" >> "$summary"
    printf 'run-2\tpass\t0\t1160\t19\t8.8\t7.2\t235\t58\t%s\t3.1\t5.1\t25\t42\t78\t6.1\t7.1\t3\t%s\t%s\n' "$backend" "${run_dir}/logs/run-pass-2.log" "${run_dir}/logs/run-pass-2.err" >> "$summary"
    ;;
esac
printf 'fake bench expect_backend=%s\n' "$expect_backend"
SH
chmod +x "$FAKE_BENCH"

PASS_DIR="${TMP_DIR}/pass"
bash "$ROOT_DIR/scripts/verify-onnx-profile-regression.sh" \
  --model fake-model \
  --baseline "$BASELINE_RAW" \
  --baseline-manifest "$GOOD_MANIFEST" \
  --aqli-bin "$FAKE_AQLI" \
  --bench-script "$FAKE_BENCH" \
  --summary-dir "$PASS_DIR" \
  --max-regression-percent 10 \
  --max-noise-percent 10 \
  --metrics durationMs,generationTps,onnxDecodeRunMs \
  --noise-metrics durationMs,generationTps \
  --expect-backend CoreML \
  --no-require-profile >"${TMP_DIR}/pass.out"

if ! grep -qx $'stage\tstatus\texitCode\tartifact\tstdout\tstderr\treason' "$PASS_DIR/results.tsv" \
    || ! grep -q "^environment	pass	0	$PASS_DIR/environment.tsv" "$PASS_DIR/results.tsv" \
    || ! grep -q "^environment-metadata	skip	0	$PASS_DIR/environment-metadata.tsv			advisory$" "$PASS_DIR/results.tsv" \
    || ! grep -q "^baseline-metadata	pass	0	$PASS_DIR/baseline-metadata.tsv			$GOOD_MANIFEST$" "$PASS_DIR/results.tsv" \
    || ! grep -q "^benchmark	pass	0	$PASS_DIR/bench/current/summary.tsv	" "$PASS_DIR/results.tsv" \
    || ! grep -q "^current-aggregate	pass	0	$PASS_DIR/current-aggregate.tsv	" "$PASS_DIR/results.tsv" \
    || ! grep -q "^current-diagnosis	pass	0	$PASS_DIR/current-diagnosis/diagnosis.tsv	" "$PASS_DIR/results.tsv" \
    || ! grep -q "^current-noise	pass	0	$PASS_DIR/current-noise.tsv	" "$PASS_DIR/results.tsv" \
    || ! grep -q "^baseline-aggregate	pass	0	$PASS_DIR/baseline-aggregate.tsv	" "$PASS_DIR/results.tsv" \
    || ! grep -q "^baseline-diagnosis	pass	0	$PASS_DIR/baseline-diagnosis/diagnosis.tsv	" "$PASS_DIR/results.tsv" \
    || ! grep -q "^diagnosis-diff	pass	0	$PASS_DIR/diagnosis-diff/comparison.tsv	" "$PASS_DIR/results.tsv" \
    || ! grep -q "^backend-metadata	pass	0	$PASS_DIR/backend-metadata.tsv			CoreML$" "$PASS_DIR/results.tsv" \
    || ! grep -q "^compare	pass	0	$PASS_DIR/compare/comparison.tsv	" "$PASS_DIR/results.tsv"; then
  echo "Expected pass results for raw-baseline regression gate" >&2
  cat "$PASS_DIR/results.tsv" >&2
  exit 1
fi
if ! grep -qx $'expectBackend\tCoreML' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'bundle\t'"$PASS_DIR"'/bundle.tsv' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'bundleJson\t'"$PASS_DIR"'/bundle.json' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'decision\t'"$PASS_DIR"'/decision.tsv' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'currentDiagnosis\t'"$PASS_DIR"'/current-diagnosis/diagnosis.tsv' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'baselineDiagnosis\t'"$PASS_DIR"'/baseline-diagnosis/diagnosis.tsv' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'diagnosisDiff\t'"$PASS_DIR"'/diagnosis-diff/comparison.tsv' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'diagnosisDiffSummary\t'"$PASS_DIR"'/diagnosis-diff/summary.tsv' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'maxNoisePercent\t10' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'noiseMetrics\tdurationMs,generationTps' "$PASS_DIR/config.tsv" \
    || ! grep -q 'expect_backend=CoreML' "$PASS_DIR/bench.stdout.log"; then
  echo "Expected regression verifier to pass expected backend to benchmark" >&2
  cat "$PASS_DIR/config.tsv" >&2
  cat "$PASS_DIR/bench.stdout.log" >&2
  exit 1
fi
if ! grep -qx $'kind\tpath\trequired\tstatus\tdescription' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'config\t'"$PASS_DIR"'/config.tsv\trequired\tpresent\tRegression configuration' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'decision\t'"$PASS_DIR"'/decision.tsv\trequired\tpresent\tRegression decision summary' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'currentAggregate\t'"$PASS_DIR"'/current-aggregate.tsv\trequired\tpresent\tCurrent aggregate summary' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'currentDiagnosis\t'"$PASS_DIR"'/current-diagnosis/diagnosis.tsv\toptional\tpresent\tCurrent performance diagnosis' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'currentDiagnosisStages\t'"$PASS_DIR"'/current-diagnosis/stages.tsv\toptional\tpresent\tCurrent diagnosed stage ranking' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'currentNoise\t'"$PASS_DIR"'/current-noise.tsv\toptional\tpresent\tCurrent noise stability summary' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'baselineDiagnosis\t'"$PASS_DIR"'/baseline-diagnosis/diagnosis.tsv\toptional\tpresent\tBaseline performance diagnosis' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'baselineDiagnosisStages\t'"$PASS_DIR"'/baseline-diagnosis/stages.tsv\toptional\tpresent\tBaseline diagnosed stage ranking' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'diagnosisDiff\t'"$PASS_DIR"'/diagnosis-diff/comparison.tsv\toptional\tpresent\tCurrent versus baseline diagnosis comparison' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'diagnosisDiffSummary\t'"$PASS_DIR"'/diagnosis-diff/summary.tsv\toptional\tpresent\tDiagnosis comparison summary' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'comparison\t'"$PASS_DIR"'/compare/comparison.tsv\trequired\tpresent\tRow-level regression comparison' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'metricSummary\t'"$PASS_DIR"'/compare/metric-summary.tsv\toptional\tpresent\tPer-metric regression summary' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx "artifacts.bundle=$PASS_DIR/bundle.tsv" "${TMP_DIR}/pass.out" \
    || ! grep -qx "artifacts.bundleJson=$PASS_DIR/bundle.json" "${TMP_DIR}/pass.out" \
    || ! grep -qx "artifacts.currentDiagnosis=$PASS_DIR/current-diagnosis/diagnosis.tsv" "${TMP_DIR}/pass.out" \
    || ! grep -qx "artifacts.baselineDiagnosis=$PASS_DIR/baseline-diagnosis/diagnosis.tsv" "${TMP_DIR}/pass.out" \
    || ! grep -qx "artifacts.diagnosisDiff=$PASS_DIR/diagnosis-diff/comparison.tsv" "${TMP_DIR}/pass.out" \
    || ! grep -qx "artifacts.decision=$PASS_DIR/decision.tsv" "${TMP_DIR}/pass.out"; then
  echo "Expected regression artifact bundle rows" >&2
  cat "$PASS_DIR/bundle.tsv" >&2
  cat "${TMP_DIR}/pass.out" >&2
  exit 1
fi
if ! grep -Fq '"kind": "comparison"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"kind": "currentDiagnosis"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"kind": "baselineDiagnosis"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"kind": "diagnosisDiff"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"kind": "currentNoise"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"requiredMissing": "0"' "$PASS_DIR/bundle.json"; then
  echo "Expected regression bundle JSON summary" >&2
  cat "$PASS_DIR/bundle.json" >&2
  exit 1
fi
if ! grep -qx $'key\tvalue' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'status\tpass' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'failedStages\t0' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'lastStage\tcompare' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'primaryStage\tdecodeRun' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'currentPrimaryStage\tdecodeRun' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'baselinePrimaryStage\tdecodeRun' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'diagnosisStageChanged\tfalse' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'diagnosisDiffComparedStages\t8' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'diagnosisDiffFasterStages\t1' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'diagnosisDiffSlowerStages\t6' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'diagnosisDiffSameStages\t1' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'diagnosisDiffPrimaryStageChanged\tfalse' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'diagnosisDiffLargestSpeedupStage\tdecodeRun' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'diagnosisDiffLargestSpeedupMs\t-1.000' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'regressionFailures\t0' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'noiseFailures\t0' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'bundleRequiredMissing\t0' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'bundle\t'"$PASS_DIR"'/bundle.tsv' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'diagnosisDiffSummary\t'"$PASS_DIR"'/diagnosis-diff/summary.tsv' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'comparison\t'"$PASS_DIR"'/compare/comparison.tsv' "$PASS_DIR/decision.tsv"; then
  echo "Expected passing regression decision summary" >&2
  cat "$PASS_DIR/decision.tsv" >&2
  exit 1
fi
if ! grep -qx $'primaryStage\tdecodeRun' "$PASS_DIR/current-diagnosis/diagnosis.tsv" \
    || ! grep -qx $'primaryValueMs\t36.500' "$PASS_DIR/current-diagnosis/diagnosis.tsv" \
    || ! grep -qx $'primaryStage\tdecodeRun' "$PASS_DIR/baseline-diagnosis/diagnosis.tsv" \
    || ! grep -qx $'primaryValueMs\t37.500' "$PASS_DIR/baseline-diagnosis/diagnosis.tsv"; then
  echo "Expected regression diagnosis rows" >&2
  cat "$PASS_DIR/current-diagnosis/diagnosis.tsv" >&2
  cat "$PASS_DIR/baseline-diagnosis/diagnosis.tsv" >&2
  exit 1
fi
if ! grep -qx $'decodeRun\tonnxDecodeRunMs\t37.500\t36.500\t-1.000\t-2.667\tprimary\tprimary\tfaster\t' "$PASS_DIR/diagnosis-diff/comparison.tsv" \
    || ! grep -qx $'primaryStageChanged\tfalse' "$PASS_DIR/diagnosis-diff/summary.tsv" \
    || ! grep -qx $'largestSpeedupStage\tdecodeRun' "$PASS_DIR/diagnosis-diff/summary.tsv"; then
  echo "Expected regression diagnosis diff rows" >&2
  cat "$PASS_DIR/diagnosis-diff/comparison.tsv" >&2
  cat "$PASS_DIR/diagnosis-diff/summary.tsv" >&2
  exit 1
fi
if ! grep -qx $'key\tvalue' "$PASS_DIR/environment.tsv" \
    || ! grep -qx $'rootDir\t'"$ROOT_DIR" "$PASS_DIR/environment.tsv" \
    || ! grep -q '^aqliVersion	' "$PASS_DIR/environment.tsv" \
    || ! grep -q '^gitCommit	' "$PASS_DIR/environment.tsv" \
    || ! grep -q '^gitDirty	' "$PASS_DIR/environment.tsv" \
    || ! grep -q '^hostOs	' "$PASS_DIR/environment.tsv" \
    || ! grep -q '^hostArch	' "$PASS_DIR/environment.tsv" \
    || ! grep -qx $'aqliBin\t'"$FAKE_AQLI" "$PASS_DIR/environment.tsv" \
    || ! grep -qx $'field\tbaseline\tcurrent\tstatus' "$PASS_DIR/environment-metadata.tsv" \
    || ! grep -qx $'check\tadvisory\tadvisory\tskip' "$PASS_DIR/environment-metadata.tsv"; then
  echo "Expected advisory environment metadata evidence" >&2
  cat "$PASS_DIR/environment.tsv" >&2
  cat "$PASS_DIR/environment-metadata.tsv" >&2
  exit 1
fi
if ! grep -qx $'field\texpected\tactual\tstatus' "$PASS_DIR/baseline-metadata.tsv" \
    || ! grep -qx $'model\tfake-model\tfake-model\tpass' "$PASS_DIR/baseline-metadata.tsv" \
    || ! grep -qx $'prompt\twhere is jakarta\twhere is jakarta\tpass' "$PASS_DIR/baseline-metadata.tsv" \
    || ! grep -qx $'maxTokens\t8\t8\tpass' "$PASS_DIR/baseline-metadata.tsv"; then
  echo "Expected compatible baseline metadata evidence" >&2
    cat "$PASS_DIR/baseline-metadata.tsv" >&2
  exit 1
fi
if ! grep -qx $'field\tbaseline\tcurrent\tstatus' "$PASS_DIR/backend-metadata.tsv" \
    || ! grep -qx $'onnxBackend\tCoreML\tCoreML\tpass' "$PASS_DIR/backend-metadata.tsv"; then
  echo "Expected compatible backend metadata evidence" >&2
  cat "$PASS_DIR/backend-metadata.tsv" >&2
  exit 1
fi
if ! grep -qx $'measured-mean\tdurationMs\t1100.000\t1120.000\tlower-is-better\t1.818\t10\tpass\t' "$PASS_DIR/compare/comparison.tsv" \
    || ! grep -qx $'measured-mean\tgenerationTps\t9.000\t9.150\thigher-is-better\t-1.667\t10\tpass\t' "$PASS_DIR/compare/comparison.tsv"; then
  echo "Expected passing aggregate comparison evidence" >&2
  cat "$PASS_DIR/compare/comparison.tsv" >&2
  exit 1
fi
if ! grep -qx $'metric\tmean\tbest\tworst\tnoisePercent\tmaxNoisePercent\tstatus\treason' "$PASS_DIR/current-noise.tsv" \
    || ! grep -qx $'durationMs\t1120.000\t1080.000\t1160.000\t7.143\t10\tpass\t' "$PASS_DIR/current-noise.tsv" \
    || ! grep -qx $'generationTps\t9.150\t9.500\t8.800\t7.650\t10\tpass\t' "$PASS_DIR/current-noise.tsv"; then
  echo "Expected passing current noise evidence" >&2
  cat "$PASS_DIR/current-noise.tsv" >&2
  exit 1
fi

NOISY_DIR="${TMP_DIR}/noisy"
if bash "$ROOT_DIR/scripts/verify-onnx-profile-regression.sh" \
    --model fake-model \
    --baseline "$BASELINE_RAW" \
    --baseline-manifest "$GOOD_MANIFEST" \
    --aqli-bin "$FAKE_AQLI" \
    --bench-script "$FAKE_BENCH" \
    --summary-dir "$NOISY_DIR" \
    --max-regression-percent 10 \
    --max-noise-percent 5 \
    --metrics durationMs,generationTps \
    --noise-metrics durationMs,generationTps \
    --expect-backend CoreML \
    --no-require-profile >"${TMP_DIR}/noisy.out" 2>"${TMP_DIR}/noisy.err"; then
  echo "Expected current noise gate failure" >&2
  cat "${TMP_DIR}/noisy.out" >&2
  cat "${TMP_DIR}/noisy.err" >&2
  exit 1
fi
if ! grep -q "^current-noise	fail	47	$NOISY_DIR/current-noise.tsv	" "$NOISY_DIR/results.tsv" \
    || ! grep -qx $'durationMs\t1120.000\t1080.000\t1160.000\t7.143\t5\tfail\t' "$NOISY_DIR/current-noise.tsv" \
    || ! grep -qx $'generationTps\t9.150\t9.500\t8.800\t7.650\t5\tfail\t' "$NOISY_DIR/current-noise.tsv" \
    || ! grep -qx $'comparison\t'"$NOISY_DIR"'/compare/comparison.tsv\trequired\tmissing\tRow-level regression comparison' "$NOISY_DIR/bundle.tsv" \
    || ! grep -qx $'decision\t'"$NOISY_DIR"'/decision.tsv\trequired\tpresent\tRegression decision summary' "$NOISY_DIR/bundle.tsv" \
    || grep -q '^baseline-aggregate	' "$NOISY_DIR/results.tsv" \
    || grep -q '^compare	' "$NOISY_DIR/results.tsv" \
    || ! grep -q '^ONNX profile current benchmark noise check failed' "${TMP_DIR}/noisy.err" \
    || ! grep -qx "artifacts.bundle=$NOISY_DIR/bundle.tsv" "${TMP_DIR}/noisy.err" \
    || ! grep -qx "artifacts.bundleJson=$NOISY_DIR/bundle.json" "${TMP_DIR}/noisy.err" \
    || ! grep -qx "artifacts.decision=$NOISY_DIR/decision.tsv" "${TMP_DIR}/noisy.err"; then
  echo "Expected current noise gate failure evidence" >&2
  cat "$NOISY_DIR/results.tsv" >&2
  cat "$NOISY_DIR/bundle.tsv" >&2
  cat "$NOISY_DIR/current-noise.tsv" >&2
  cat "${TMP_DIR}/noisy.err" >&2
  exit 1
fi
if ! grep -qx $'status\tfail' "$NOISY_DIR/decision.tsv" \
    || ! grep -qx $'failureStage\tcurrent-noise' "$NOISY_DIR/decision.tsv" \
    || ! grep -qx $'failureReason\tnoise-failed' "$NOISY_DIR/decision.tsv" \
    || ! grep -qx $'noiseFailures\t2' "$NOISY_DIR/decision.tsv" \
    || ! grep -qx $'regressionCompared\t0' "$NOISY_DIR/decision.tsv"; then
  echo "Expected current noise failure decision summary" >&2
  cat "$NOISY_DIR/decision.tsv" >&2
  exit 1
fi

INFER_BACKEND_DIR="${TMP_DIR}/infer-backend"
bash "$ROOT_DIR/scripts/verify-onnx-profile-regression.sh" \
  --model fake-model \
  --baseline "$BASELINE_RAW" \
  --baseline-manifest "$BACKEND_MANIFEST" \
  --aqli-bin "$FAKE_AQLI" \
  --bench-script "$FAKE_BENCH" \
  --summary-dir "$INFER_BACKEND_DIR" \
  --max-regression-percent 10 \
  --metrics durationMs \
  --no-require-profile >"${TMP_DIR}/infer-backend.out"

if ! grep -qx $'configuredExpectBackend\t' "$INFER_BACKEND_DIR/config.tsv" \
    || ! grep -qx $'expectBackend\tCoreML' "$INFER_BACKEND_DIR/config.tsv" \
    || ! grep -qx $'expectBackendSource\tbaseline-manifest:onnxBackend' "$INFER_BACKEND_DIR/config.tsv" \
    || ! grep -q 'expect_backend=CoreML' "$INFER_BACKEND_DIR/bench.stdout.log" \
    || ! grep -q "^compare	pass	0	$INFER_BACKEND_DIR/compare/comparison.tsv	" "$INFER_BACKEND_DIR/results.tsv"; then
  echo "Expected regression verifier to infer expected backend from baseline manifest" >&2
  cat "$INFER_BACKEND_DIR/config.tsv" >&2
  cat "$INFER_BACKEND_DIR/bench.stdout.log" >&2
  cat "$INFER_BACKEND_DIR/results.tsv" >&2
  exit 1
fi

LEGACY_RAW_INFER_DIR="${TMP_DIR}/legacy-raw-infer"
bash "$ROOT_DIR/scripts/verify-onnx-profile-regression.sh" \
  --model fake-model \
  --baseline "$BASELINE_RAW" \
  --aqli-bin "$FAKE_AQLI" \
  --bench-script "$FAKE_BENCH" \
  --summary-dir "$LEGACY_RAW_INFER_DIR" \
  --max-regression-percent 10 \
  --metrics durationMs \
  --no-require-profile >"${TMP_DIR}/legacy-raw-infer.out"

if ! grep -qx $'baselineManifest\t' "$LEGACY_RAW_INFER_DIR/config.tsv" \
    || ! grep -qx $'configuredExpectBackend\t' "$LEGACY_RAW_INFER_DIR/config.tsv" \
    || ! grep -qx $'expectBackend\tCoreML' "$LEGACY_RAW_INFER_DIR/config.tsv" \
    || ! grep -qx $'expectBackendSource\tbaseline-summary:onnxBackend' "$LEGACY_RAW_INFER_DIR/config.tsv" \
    || ! grep -q 'expect_backend=CoreML' "$LEGACY_RAW_INFER_DIR/bench.stdout.log" \
    || ! grep -q "^baseline-metadata	skip	0	$LEGACY_RAW_INFER_DIR/baseline-metadata.tsv			manifest-not-found$" "$LEGACY_RAW_INFER_DIR/results.tsv" \
    || ! grep -q "^compare	pass	0	$LEGACY_RAW_INFER_DIR/compare/comparison.tsv	" "$LEGACY_RAW_INFER_DIR/results.tsv"; then
  echo "Expected regression verifier to infer expected backend from legacy raw baseline summary" >&2
  cat "$LEGACY_RAW_INFER_DIR/config.tsv" >&2
  cat "$LEGACY_RAW_INFER_DIR/bench.stdout.log" >&2
  cat "$LEGACY_RAW_INFER_DIR/results.tsv" >&2
  exit 1
fi

ENV_MISMATCH_DIR="${TMP_DIR}/env-mismatch"
if bash "$ROOT_DIR/scripts/verify-onnx-profile-regression.sh" \
    --model fake-model \
    --baseline "$BASELINE_RAW" \
    --baseline-manifest "$BAD_ENV_MANIFEST" \
    --aqli-bin "$FAKE_AQLI" \
    --bench-script "$FAKE_BENCH" \
    --summary-dir "$ENV_MISMATCH_DIR" \
    --strict-environment-check \
    --no-require-profile >"${TMP_DIR}/env-mismatch.out" 2>"${TMP_DIR}/env-mismatch.err"; then
  echo "Expected strict environment mismatch failure" >&2
  cat "${TMP_DIR}/env-mismatch.out" >&2
  cat "${TMP_DIR}/env-mismatch.err" >&2
  exit 1
fi
if ! grep -Fq '"kind": "comparison"' "$NOISY_DIR/bundle.json" \
    || ! grep -Fq '"status": "missing"' "$NOISY_DIR/bundle.json"; then
  echo "Expected noisy regression bundle JSON summary" >&2
  cat "$NOISY_DIR/bundle.json" >&2
  exit 1
fi
if ! grep -Eq $'^bundleRequiredMissing\t[1-9]' "$NOISY_DIR/decision.tsv" \
    || ! grep -qx $'firstBundleMissingKind\tbackendMetadata' "$NOISY_DIR/decision.tsv"; then
  echo "Expected noisy regression decision to summarize missing bundle requirements" >&2
  cat "$NOISY_DIR/decision.tsv" >&2
  exit 1
fi
if ! grep -q "^environment-metadata	fail	45	$ENV_MISMATCH_DIR/environment-metadata.tsv			environment-mismatch$" "$ENV_MISMATCH_DIR/results.tsv" \
    || ! grep -qx $'hostOs\tdefinitely-not-aqli-os\t'"$(uname -s)"$'\tfail' "$ENV_MISMATCH_DIR/environment-metadata.tsv" \
    || ! grep -qx $'currentRaw\t'"$ENV_MISMATCH_DIR"'/bench/current/summary.tsv\trequired\tmissing\tCurrent raw benchmark summary' "$ENV_MISMATCH_DIR/bundle.tsv" \
    || ! grep -qx $'decision\t'"$ENV_MISMATCH_DIR"'/decision.tsv\trequired\tpresent\tRegression decision summary' "$ENV_MISMATCH_DIR/bundle.tsv" \
    || grep -q '^benchmark	' "$ENV_MISMATCH_DIR/results.tsv"; then
  echo "Expected early strict environment mismatch evidence" >&2
  cat "$ENV_MISMATCH_DIR/results.tsv" >&2
  cat "$ENV_MISMATCH_DIR/environment-metadata.tsv" >&2
  cat "$ENV_MISMATCH_DIR/bundle.tsv" >&2
  exit 1
fi
if ! grep -Fq '"kind": "currentRaw"' "$ENV_MISMATCH_DIR/bundle.json" \
    || ! grep -Fq '"status": "missing"' "$ENV_MISMATCH_DIR/bundle.json"; then
  echo "Expected environment mismatch bundle JSON summary" >&2
  cat "$ENV_MISMATCH_DIR/bundle.json" >&2
  exit 1
fi
if ! grep -Eq $'^bundleRequiredMissing\t[1-9]' "$ENV_MISMATCH_DIR/decision.tsv" \
    || ! grep -qx $'firstBundleMissingKind\tbaselineMetadata' "$ENV_MISMATCH_DIR/decision.tsv"; then
  echo "Expected environment mismatch decision to summarize missing bundle requirements" >&2
  cat "$ENV_MISMATCH_DIR/decision.tsv" >&2
  exit 1
fi
if ! grep -qx $'status\tfail' "$ENV_MISMATCH_DIR/decision.tsv" \
    || ! grep -qx $'failureStage\tenvironment-metadata' "$ENV_MISMATCH_DIR/decision.tsv" \
    || ! grep -qx $'failureReason\tenvironment-mismatch' "$ENV_MISMATCH_DIR/decision.tsv"; then
  echo "Expected strict environment mismatch decision summary" >&2
  cat "$ENV_MISMATCH_DIR/decision.tsv" >&2
  exit 1
fi

MISMATCH_DIR="${TMP_DIR}/mismatch"
if bash "$ROOT_DIR/scripts/verify-onnx-profile-regression.sh" \
    --model fake-model \
    --baseline "$BASELINE_RAW" \
    --baseline-manifest "$BAD_MANIFEST" \
    --aqli-bin "$FAKE_AQLI" \
    --bench-script "$FAKE_BENCH" \
    --summary-dir "$MISMATCH_DIR" \
    --no-require-profile >"${TMP_DIR}/mismatch.out" 2>"${TMP_DIR}/mismatch.err"; then
  echo "Expected metadata mismatch failure" >&2
  cat "${TMP_DIR}/mismatch.out" >&2
  cat "${TMP_DIR}/mismatch.err" >&2
  exit 1
fi
if ! grep -q "^baseline-metadata	fail	43	$MISMATCH_DIR/baseline-metadata.tsv			incompatible-baseline$" "$MISMATCH_DIR/results.tsv" \
    || ! grep -qx $'prompt\twhere is jakarta\tdifferent prompt\tfail' "$MISMATCH_DIR/baseline-metadata.tsv" \
    || ! grep -qx $'maxTokens\t8\t4\tfail' "$MISMATCH_DIR/baseline-metadata.tsv" \
    || grep -q '^benchmark	' "$MISMATCH_DIR/results.tsv"; then
  echo "Expected early baseline metadata mismatch evidence" >&2
  cat "$MISMATCH_DIR/results.tsv" >&2
  cat "$MISMATCH_DIR/baseline-metadata.tsv" >&2
  exit 1
fi

AGG_BASELINE_DIR="${TMP_DIR}/aggregate-baseline"
bash "$ROOT_DIR/scripts/summarize-onnx-profile-summary.sh" \
  --summary "$BASELINE_RAW" \
  --summary-dir "$AGG_BASELINE_DIR" \
  --out "$AGG_BASELINE_DIR/aggregate.tsv" >/dev/null

ALREADY_DIR="${TMP_DIR}/already"
bash "$ROOT_DIR/scripts/verify-onnx-profile-regression.sh" \
  --model fake-model \
  --baseline "$AGG_BASELINE_DIR/aggregate.tsv" \
  --aqli-bin "$FAKE_AQLI" \
  --bench-script "$FAKE_BENCH" \
  --summary-dir "$ALREADY_DIR" \
  --max-regression-percent 10 \
  --metrics durationMs \
  --no-require-profile >/dev/null

if ! grep -q "^baseline-aggregate	pass	0	$ALREADY_DIR/baseline-aggregate.tsv			already-aggregate$" "$ALREADY_DIR/results.tsv"; then
  echo "Expected already-aggregate baseline to be detected" >&2
  cat "$ALREADY_DIR/results.tsv" >&2
  exit 1
fi

BACKEND_MISMATCH_DIR="${TMP_DIR}/backend-mismatch"
if AQLI_FAKE_ONNX_BACKEND=CPU \
    bash "$ROOT_DIR/scripts/verify-onnx-profile-regression.sh" \
      --model fake-model \
      --baseline "$BASELINE_RAW" \
      --aqli-bin "$FAKE_AQLI" \
      --bench-script "$FAKE_BENCH" \
      --summary-dir "$BACKEND_MISMATCH_DIR" \
      --max-regression-percent 10 \
      --metrics durationMs \
      --no-require-profile >"${TMP_DIR}/backend-mismatch.out" 2>"${TMP_DIR}/backend-mismatch.err"; then
  echo "Expected backend mismatch failure" >&2
  cat "${TMP_DIR}/backend-mismatch.out" >&2
  cat "${TMP_DIR}/backend-mismatch.err" >&2
  exit 1
fi
if ! grep -q "^backend-metadata	fail	44	$BACKEND_MISMATCH_DIR/backend-metadata.tsv			backend-mismatch$" "$BACKEND_MISMATCH_DIR/results.tsv" \
    || ! grep -qx $'onnxBackend\tCoreML\tCPU\tfail' "$BACKEND_MISMATCH_DIR/backend-metadata.tsv" \
    || grep -q '^compare	' "$BACKEND_MISMATCH_DIR/results.tsv"; then
  echo "Expected early backend mismatch evidence" >&2
  cat "$BACKEND_MISMATCH_DIR/results.tsv" >&2
  cat "$BACKEND_MISMATCH_DIR/backend-metadata.tsv" >&2
  exit 1
fi

FAIL_DIR="${TMP_DIR}/fail"
if AQLI_FAKE_ONNX_REGRESSION=fail \
    bash "$ROOT_DIR/scripts/verify-onnx-profile-regression.sh" \
      --model fake-model \
      --baseline "$BASELINE_RAW" \
      --aqli-bin "$FAKE_AQLI" \
      --bench-script "$FAKE_BENCH" \
      --summary-dir "$FAIL_DIR" \
      --max-regression-percent 10 \
      --metrics durationMs,generationTps,onnxDecodeRunMs \
      --no-require-profile >"${TMP_DIR}/fail.out"; then
  echo "Expected regression gate failure" >&2
  cat "${TMP_DIR}/fail.out" >&2
  exit 1
fi

if ! grep -q "^compare	fail	42	$FAIL_DIR/compare/comparison.tsv	" "$FAIL_DIR/results.tsv" \
    || ! grep -qx $'measured-mean\tdurationMs\t1100.000\t1450.000\tlower-is-better\t31.818\t10\tfail\t' "$FAIL_DIR/compare/comparison.tsv" \
    || ! grep -qx $'measured-mean\tgenerationTps\t9.000\t5.500\thigher-is-better\t38.889\t10\tfail\t' "$FAIL_DIR/compare/comparison.tsv" \
    || ! grep -q '^regression: fail$' "${TMP_DIR}/fail.out"; then
  echo "Expected failed regression evidence" >&2
  cat "$FAIL_DIR/results.tsv" >&2
  cat "$FAIL_DIR/compare/comparison.tsv" >&2
  cat "${TMP_DIR}/fail.out" >&2
  exit 1
fi
if ! grep -qx $'status\tfail' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'failureStage\tcompare' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'failureReason\tregression-detected' "$FAIL_DIR/decision.tsv" \
    || ! grep -q '^regressionFailures	[1-9]' "$FAIL_DIR/decision.tsv" \
    || ! grep -q '^worstRegressionMetric	[^	]' "$FAIL_DIR/decision.tsv" \
    || ! grep -q '^worstRegressionPercent	[0-9]' "$FAIL_DIR/decision.tsv"; then
  echo "Expected failed regression decision summary" >&2
  cat "$FAIL_DIR/decision.tsv" >&2
  exit 1
fi

printf 'ONNX profile regression verifier test passed\n'
