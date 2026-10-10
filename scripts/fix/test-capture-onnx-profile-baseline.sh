#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-onnx-baseline-capture.XXXXXX")"
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

FAKE_BENCH="${TMP_DIR}/bench-onnx-profile.sh"
cat > "$FAKE_BENCH" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

out_dir=""
label=""
model=""
prompt=""
max_tokens=""
runs=""
warmup_runs=""
aqli_bin=""
expect_backend=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --out-dir) out_dir="$2"; shift 2 ;;
    --label) label="$2"; shift 2 ;;
    --model) model="$2"; shift 2 ;;
    --prompt) prompt="$2"; shift 2 ;;
    --max-tokens) max_tokens="$2"; shift 2 ;;
    --runs) runs="$2"; shift 2 ;;
    --warmup-runs) warmup_runs="$2"; shift 2 ;;
    --aqli-bin) aqli_bin="$2"; shift 2 ;;
    --expect-backend) expect_backend="$2"; shift 2 ;;
    --require-profile|--no-require-profile) shift ;;
    *) shift ;;
  esac
done
run_dir="${out_dir}/${label}"
mkdir -p "${run_dir}/logs"
summary="${run_dir}/summary.tsv"
run2_backend="${AQLI_FAKE_CAPTURE_RUN2_BACKEND:-CoreML}"
printf 'case\tstatus\texitCode\tdurationMs\tpromptEvalTps\tgenerationTps\tdecodeTps\tttftMs\ttokenLatencyMs\tonnxBackend\tonnxTokenizeMs\tonnxInputPrepMs\tonnxPrefillRunMs\tonnxDecodeRunMs\tonnxOrtRunMs\tonnxLogitsSelectMs\tonnxSamplingMs\tonnxSteps\tcombinedLog\tstderrLog\n' > "$summary"
printf 'warmup-1\tpass\t0\t2000\t10\t5\t4\t400\t120\tCPU\t5\t5\t50\t90\t120\t8\t9\t1\t%s\t%s\n' "${run_dir}/logs/warmup.log" "${run_dir}/logs/warmup.err" >> "$summary"
printf 'run-1\tpass\t0\t1000\t20\t10\t8\t200\t50\tCoreML\t2\t3\t20\t30\t60\t4\t5\t2\t%s\t%s\n' "${run_dir}/logs/run-1.log" "${run_dir}/logs/run-1.err" >> "$summary"
printf 'run-2\tpass\t0\t1200\t18\t8\t7\t240\t60\t%s\t3\t5\t25\t45\t80\t6\t7\t3\t%s\t%s\n' "$run2_backend" "${run_dir}/logs/run-2.log" "${run_dir}/logs/run-2.err" >> "$summary"
printf 'fake bench model=%s prompt=%s max_tokens=%s runs=%s warmup_runs=%s aqli_bin=%s expect_backend=%s\n' "$model" "$prompt" "$max_tokens" "$runs" "$warmup_runs" "$aqli_bin" "$expect_backend"
SH
chmod +x "$FAKE_BENCH"

BASELINE_ROOT="${TMP_DIR}/baselines"
PASS_DIR="${BASELINE_ROOT}/webworld/fixed"
bash "$ROOT_DIR/scripts/capture-onnx-profile-baseline.sh" \
  --model onnx-community/WebWorld-8B-Onnx \
  --prompt "where is jakarta" \
  --max-tokens 4 \
  --runs 2 \
  --warmup-runs 1 \
  --aqli-bin "$FAKE_AQLI" \
  --bench-script "$FAKE_BENCH" \
  --baseline-root "$BASELINE_ROOT" \
  --name webworld \
  --run-label fixed \
  --expect-backend CoreML \
  --max-noise-percent 25 \
  --noise-metrics durationMs,generationTps \
  --no-require-profile >"${TMP_DIR}/capture.out"

if [[ ! -f "$PASS_DIR/aggregate.tsv" \
    || ! -f "$PASS_DIR/diagnosis/diagnosis.tsv" \
    || ! -f "$PASS_DIR/diagnosis/stages.tsv" \
    || ! -f "$PASS_DIR/diagnosis/report.txt" \
    || ! -f "$PASS_DIR/bench/profile/summary.tsv" \
    || ! -f "$PASS_DIR/environment.tsv" \
    || ! -f "$PASS_DIR/bundle.tsv" \
    || ! -f "$PASS_DIR/bundle.json" \
    || ! -f "$PASS_DIR/decision.tsv" \
    || ! -f "$PASS_DIR/noise.tsv" \
    || ! -f "$BASELINE_ROOT/webworld/latest.tsv" \
    || ! -f "$BASELINE_ROOT/webworld/latest-manifest.tsv" \
    || ! -f "$BASELINE_ROOT/webworld/latest-environment.tsv" \
    || ! -f "$PASS_DIR/config.tsv" \
    || ! -f "$PASS_DIR/results.tsv" \
    || ! -f "$PASS_DIR/manifest.tsv" ]]; then
  echo "Expected baseline capture artifacts" >&2
  find "$BASELINE_ROOT" -maxdepth 3 -type f -print >&2 || true
  exit 1
fi

if ! cmp -s "$PASS_DIR/aggregate.tsv" "$BASELINE_ROOT/webworld/latest.tsv"; then
  echo "Expected latest.tsv to copy aggregate.tsv" >&2
  exit 1
fi
if ! grep -qx $'baselineName\twebworld' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'runLabel\tfixed' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'bundle\t'"$PASS_DIR"'/bundle.tsv' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'bundleJson\t'"$PASS_DIR"'/bundle.json' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'diagnosis\t'"$PASS_DIR"'/diagnosis/diagnosis.tsv' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'diagnosisStages\t'"$PASS_DIR"'/diagnosis/stages.tsv' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'diagnosisReport\t'"$PASS_DIR"'/diagnosis/report.txt' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'decision\t'"$PASS_DIR"'/decision.tsv' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'runs\t2' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'warmupRuns\t1' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'expectBackend\tCoreML' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'allowMixedBackend\t0' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'maxNoisePercent\t25' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'noiseMetrics\tdurationMs,generationTps' "$PASS_DIR/config.tsv"; then
  echo "Expected capture config rows" >&2
  cat "$PASS_DIR/config.tsv" >&2
  exit 1
fi
if ! grep -qx $'model\tonnx-community/WebWorld-8B-Onnx' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'prompt\twhere is jakarta' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'maxTokens\t4' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'runs\t2' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'warmupRuns\t1' "$PASS_DIR/manifest.tsv" \
    || ! grep -q '^aqliVersion	' "$PASS_DIR/manifest.tsv" \
    || ! grep -q '^gitCommit	' "$PASS_DIR/manifest.tsv" \
    || ! grep -q '^gitDirty	' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'aggregateLabel\tmeasured' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'onnxBackend\tCoreML' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'expectBackend\tCoreML' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'allowMixedBackend\t0' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'maxNoisePercent\t25' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'noiseMetrics\tdurationMs,generationTps' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'noise\t'"$PASS_DIR"'/noise.tsv' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'environment\t'"$PASS_DIR"'/environment.tsv' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'diagnosis\t'"$PASS_DIR"'/diagnosis/diagnosis.tsv' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'diagnosisStages\t'"$PASS_DIR"'/diagnosis/stages.tsv' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'diagnosisReport\t'"$PASS_DIR"'/diagnosis/report.txt' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'bundle\t'"$PASS_DIR"'/bundle.tsv' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'bundleJson\t'"$PASS_DIR"'/bundle.json' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'decision\t'"$PASS_DIR"'/decision.tsv' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'latestEnvironment\t'"$BASELINE_ROOT"'/webworld/latest-environment.tsv' "$PASS_DIR/manifest.tsv" \
    || ! grep -qx $'includeWarmupAggregate\t0' "$PASS_DIR/manifest.tsv"; then
  echo "Expected manifest compatibility metadata rows" >&2
  cat "$PASS_DIR/manifest.tsv" >&2
  exit 1
fi
if ! grep -q 'expect_backend=CoreML' "$PASS_DIR/bench.stdout.log"; then
  echo "Expected capture to pass expected backend to benchmark" >&2
  cat "$PASS_DIR/bench.stdout.log" >&2
  exit 1
fi
if ! cmp -s "$PASS_DIR/manifest.tsv" "$BASELINE_ROOT/webworld/latest-manifest.tsv"; then
  echo "Expected latest-manifest.tsv to copy manifest.tsv" >&2
  exit 1
fi
if ! cmp -s "$PASS_DIR/environment.tsv" "$BASELINE_ROOT/webworld/latest-environment.tsv"; then
  echo "Expected latest-environment.tsv to copy environment.tsv" >&2
  exit 1
fi
if ! grep -qx $'kind\tpath\trequired\tstatus\tdescription' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'config\t'"$PASS_DIR"'/config.tsv\trequired\tpresent\tCapture configuration' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'decision\t'"$PASS_DIR"'/decision.tsv\trequired\tpresent\tCapture decision summary' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'aggregate\t'"$PASS_DIR"'/aggregate.tsv\trequired\tpresent\tAggregated baseline summary' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'diagnosis\t'"$PASS_DIR"'/diagnosis/diagnosis.tsv\toptional\tpresent\tAggregate performance diagnosis' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'diagnosisStages\t'"$PASS_DIR"'/diagnosis/stages.tsv\toptional\tpresent\tDiagnosed stage ranking' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'noise\t'"$PASS_DIR"'/noise.tsv\toptional\tpresent\tNoise stability summary' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'latest\t'"$BASELINE_ROOT"'/webworld/latest.tsv\toptional\tpresent\tLatest baseline pointer' "$PASS_DIR/bundle.tsv"; then
  echo "Expected capture artifact bundle rows" >&2
  cat "$PASS_DIR/bundle.tsv" >&2
  exit 1
fi
if ! grep -Fq '"kind": "aggregate"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"kind": "diagnosis"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"kind": "latest"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"requiredMissing": "0"' "$PASS_DIR/bundle.json"; then
  echo "Expected capture bundle JSON summary" >&2
  cat "$PASS_DIR/bundle.json" >&2
  exit 1
fi
if ! grep -qx $'key\tvalue' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'status\tpass' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'failedStages\t0' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'lastStage\tlatest-environment' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'primaryStage\tdecodeRun' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'primaryValueMs\t37.500' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'diagnosis\t'"$PASS_DIR"'/diagnosis/diagnosis.tsv' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'noiseFailures\t0' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'worstNoiseMetric\tgenerationTps' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'bundleRequiredMissing\t0' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'bundle\t'"$PASS_DIR"'/bundle.tsv' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'noise\t'"$PASS_DIR"'/noise.tsv' "$PASS_DIR/decision.tsv"; then
  echo "Expected passing capture decision summary" >&2
  cat "$PASS_DIR/decision.tsv" >&2
  exit 1
fi
if ! grep -qx $'key\tvalue' "$PASS_DIR/environment.tsv" \
    || ! grep -qx $'rootDir\t'"$ROOT_DIR" "$PASS_DIR/environment.tsv" \
    || ! grep -q '^aqliVersion	' "$PASS_DIR/environment.tsv" \
    || ! grep -q '^gitCommit	' "$PASS_DIR/environment.tsv" \
    || ! grep -q '^gitDirty	' "$PASS_DIR/environment.tsv" \
    || ! grep -q '^hostOs	' "$PASS_DIR/environment.tsv" \
    || ! grep -q '^hostArch	' "$PASS_DIR/environment.tsv" \
    || ! grep -q '^javaVersion	' "$PASS_DIR/environment.tsv" \
    || ! grep -qx $'aqliBin\t'"$FAKE_AQLI" "$PASS_DIR/environment.tsv"; then
  echo "Expected environment fingerprint rows" >&2
  cat "$PASS_DIR/environment.tsv" >&2
  exit 1
fi
if ! grep -q "^benchmark	pass	0	$PASS_DIR/bench/profile/summary.tsv	" "$PASS_DIR/results.tsv" \
    || ! grep -q "^aggregate	pass	0	$PASS_DIR/aggregate.tsv	" "$PASS_DIR/results.tsv" \
    || ! grep -q "^diagnosis	pass	0	$PASS_DIR/diagnosis/diagnosis.tsv	" "$PASS_DIR/results.tsv" \
    || ! grep -q "^baseline-backend	pass	0	$PASS_DIR/aggregate.tsv	.*CoreML$" "$PASS_DIR/results.tsv" \
    || ! grep -q "^noise	pass	0	$PASS_DIR/noise.tsv	" "$PASS_DIR/results.tsv" \
    || ! grep -q "^environment	pass	0	$PASS_DIR/environment.tsv" "$PASS_DIR/results.tsv" \
    || ! grep -q "^latest	pass	0	$BASELINE_ROOT/webworld/latest.tsv" "$PASS_DIR/results.tsv" \
    || ! grep -q "^latest-environment	pass	0	$BASELINE_ROOT/webworld/latest-environment.tsv" "$PASS_DIR/results.tsv"; then
  echo "Expected capture results rows" >&2
  cat "$PASS_DIR/results.tsv" >&2
  exit 1
fi
if ! grep -qx $'measured-mean\tpass\t\t1100.000\t19.000\t9.000\t7.500\t220.000\t55.000\tCoreML\t2.500\t4.000\t22.500\t37.500\t70.000\t5.000\t6.000\t2.500\t'"$PASS_DIR"'/bench/profile/summary.tsv\t' "$PASS_DIR/aggregate.tsv"; then
  echo "Expected measured mean aggregate row" >&2
  cat "$PASS_DIR/aggregate.tsv" >&2
  exit 1
fi
if ! grep -qx $'primaryStage\tdecodeRun' "$PASS_DIR/diagnosis/diagnosis.tsv" \
    || ! grep -qx $'primaryValueMs\t37.500' "$PASS_DIR/diagnosis/diagnosis.tsv"; then
  echo "Expected capture diagnosis rows" >&2
  cat "$PASS_DIR/diagnosis/diagnosis.tsv" >&2
  exit 1
fi
if ! grep -qx $'metric\tmean\tbest\tworst\tnoisePercent\tmaxNoisePercent\tstatus\treason' "$PASS_DIR/noise.tsv" \
    || ! grep -qx $'durationMs\t1100.000\t1000.000\t1200.000\t18.182\t25\tpass\t' "$PASS_DIR/noise.tsv" \
    || ! grep -qx $'generationTps\t9.000\t10.000\t8.000\t22.222\t25\tpass\t' "$PASS_DIR/noise.tsv"; then
  echo "Expected pass noise rows" >&2
  cat "$PASS_DIR/noise.tsv" >&2
  exit 1
fi
if ! grep -q "verify-onnx-profile-regression.sh --model onnx-community/WebWorld-8B-Onnx --baseline $BASELINE_ROOT/webworld/latest.tsv" "${TMP_DIR}/capture.out" \
    || ! grep -qx "artifacts.bundle=$PASS_DIR/bundle.tsv" "${TMP_DIR}/capture.out" \
    || ! grep -qx "artifacts.bundleJson=$PASS_DIR/bundle.json" "${TMP_DIR}/capture.out" \
    || ! grep -qx "artifacts.diagnosis=$PASS_DIR/diagnosis/diagnosis.tsv" "${TMP_DIR}/capture.out" \
    || ! grep -qx "artifacts.decision=$PASS_DIR/decision.tsv" "${TMP_DIR}/capture.out"; then
  echo "Expected report to point at latest baseline" >&2
  cat "${TMP_DIR}/capture.out" >&2
  exit 1
fi

NOISY_ROOT="${TMP_DIR}/noisy"
NOISY_DIR="${NOISY_ROOT}/webworld/noisy"
if bash "$ROOT_DIR/scripts/capture-onnx-profile-baseline.sh" \
    --model webworld \
    --aqli-bin "$FAKE_AQLI" \
    --bench-script "$FAKE_BENCH" \
    --baseline-root "$NOISY_ROOT" \
    --name webworld \
    --run-label noisy \
    --max-noise-percent 10 \
    --noise-metrics durationMs,generationTps \
    --no-require-profile >"${TMP_DIR}/noisy.out" 2>"${TMP_DIR}/noisy.err"; then
  echo "Expected noisy baseline capture failure" >&2
  cat "${TMP_DIR}/noisy.out" >&2
  cat "${TMP_DIR}/noisy.err" >&2
  exit 1
fi
if ! grep -q "^noise	fail	47	$NOISY_DIR/noise.tsv	" "$NOISY_DIR/results.tsv" \
    || ! grep -qx $'durationMs\t1100.000\t1000.000\t1200.000\t18.182\t10\tfail\t' "$NOISY_DIR/noise.tsv" \
    || ! grep -qx $'generationTps\t9.000\t10.000\t8.000\t22.222\t10\tfail\t' "$NOISY_DIR/noise.tsv" \
    || ! grep -qx $'latest\t'"$NOISY_ROOT"'/webworld/latest.tsv\toptional\tmissing\tLatest baseline pointer' "$NOISY_DIR/bundle.tsv" \
    || ! grep -qx $'decision\t'"$NOISY_DIR"'/decision.tsv\trequired\tpresent\tCapture decision summary' "$NOISY_DIR/bundle.tsv" \
    || [[ -f "$NOISY_ROOT/webworld/latest.tsv" ]] \
    || ! grep -q '^ONNX profile baseline noise check failed' "${TMP_DIR}/noisy.err" \
    || ! grep -qx "artifacts.bundle=$NOISY_DIR/bundle.tsv" "${TMP_DIR}/noisy.err" \
    || ! grep -qx "artifacts.bundleJson=$NOISY_DIR/bundle.json" "${TMP_DIR}/noisy.err" \
    || ! grep -qx "artifacts.decision=$NOISY_DIR/decision.tsv" "${TMP_DIR}/noisy.err"; then
  echo "Expected noisy baseline failure evidence" >&2
  cat "$NOISY_DIR/results.tsv" >&2
  cat "$NOISY_DIR/bundle.tsv" >&2
  cat "$NOISY_DIR/noise.tsv" >&2
  cat "${TMP_DIR}/noisy.err" >&2
  exit 1
fi
if ! grep -qx $'bundleRequiredMissing\t0' "$NOISY_DIR/decision.tsv"; then
  echo "Expected noisy capture decision to summarize bundle health" >&2
  cat "$NOISY_DIR/decision.tsv" >&2
  exit 1
fi
if ! grep -Fq '"kind": "latest"' "$NOISY_DIR/bundle.json" \
    || ! grep -Fq '"status": "missing"' "$NOISY_DIR/bundle.json"; then
  echo "Expected noisy capture bundle JSON summary" >&2
  cat "$NOISY_DIR/bundle.json" >&2
  exit 1
fi
if ! grep -qx $'status\tfail' "$NOISY_DIR/decision.tsv" \
    || ! grep -qx $'failureStage\tnoise' "$NOISY_DIR/decision.tsv" \
    || ! grep -qx $'failureReason\tnoise-failed' "$NOISY_DIR/decision.tsv" \
    || ! grep -qx $'noiseFailures\t2' "$NOISY_DIR/decision.tsv" \
    || ! grep -qx $'regressionCompared\t0' "$NOISY_DIR/decision.tsv"; then
  echo "Expected noisy capture decision summary" >&2
  cat "$NOISY_DIR/decision.tsv" >&2
  exit 1
fi

MIXED_ROOT="${TMP_DIR}/mixed"
MIXED_DIR="${MIXED_ROOT}/webworld/mixed"
if AQLI_FAKE_CAPTURE_RUN2_BACKEND=CPU \
    bash "$ROOT_DIR/scripts/capture-onnx-profile-baseline.sh" \
      --model webworld \
      --aqli-bin "$FAKE_AQLI" \
      --bench-script "$FAKE_BENCH" \
      --baseline-root "$MIXED_ROOT" \
      --name webworld \
      --run-label mixed \
      --no-require-profile >"${TMP_DIR}/mixed.out" 2>"${TMP_DIR}/mixed.err"; then
  echo "Expected mixed backend baseline capture failure" >&2
  cat "${TMP_DIR}/mixed.out" >&2
  cat "${TMP_DIR}/mixed.err" >&2
  exit 1
fi
if ! grep -q "^baseline-backend	fail	46	$MIXED_DIR/aggregate.tsv	" "$MIXED_DIR/results.tsv" \
    || ! grep -q $'\tmixed-or-missing-backend$' "$MIXED_DIR/results.tsv" \
    || ! grep -qx $'measured-mean\tpass\t\t1100.000\t19.000\t9.000\t7.500\t220.000\t55.000\tmixed\t2.500\t4.000\t22.500\t37.500\t70.000\t5.000\t6.000\t2.500\t'"$MIXED_DIR"'/bench/profile/summary.tsv\t' "$MIXED_DIR/aggregate.tsv" \
    || [[ -f "$MIXED_ROOT/webworld/latest.tsv" ]] \
    || ! grep -q '^backend=mixed$' "${TMP_DIR}/mixed.err"; then
  echo "Expected mixed backend failure evidence" >&2
  cat "$MIXED_DIR/results.tsv" >&2
  cat "$MIXED_DIR/aggregate.tsv" >&2
  cat "${TMP_DIR}/mixed.err" >&2
  exit 1
fi
if ! grep -qx $'status\tfail' "$MIXED_DIR/decision.tsv" \
    || ! grep -qx $'failureStage\tbaseline-backend' "$MIXED_DIR/decision.tsv" \
    || ! grep -qx $'failureReason\tmixed-or-missing-backend' "$MIXED_DIR/decision.tsv"; then
  echo "Expected mixed backend decision summary" >&2
  cat "$MIXED_DIR/decision.tsv" >&2
  exit 1
fi

MIXED_ALLOWED_ROOT="${TMP_DIR}/mixed-allowed"
MIXED_ALLOWED_DIR="${MIXED_ALLOWED_ROOT}/webworld/mixed-allowed"
AQLI_FAKE_CAPTURE_RUN2_BACKEND=CPU \
  bash "$ROOT_DIR/scripts/capture-onnx-profile-baseline.sh" \
    --model webworld \
    --aqli-bin "$FAKE_AQLI" \
    --bench-script "$FAKE_BENCH" \
    --baseline-root "$MIXED_ALLOWED_ROOT" \
    --name webworld \
    --run-label mixed-allowed \
    --allow-mixed-backend \
    --no-require-profile >"${TMP_DIR}/mixed-allowed.out"

if ! grep -qx $'allowMixedBackend\t1' "$MIXED_ALLOWED_DIR/config.tsv" \
    || ! grep -qx $'allowMixedBackend\t1' "$MIXED_ALLOWED_DIR/manifest.tsv" \
    || ! grep -q "^baseline-backend	skip	0	$MIXED_ALLOWED_DIR/aggregate.tsv	.*mixed-or-missing-backend-allowed$" "$MIXED_ALLOWED_DIR/results.tsv" \
    || [[ ! -f "$MIXED_ALLOWED_ROOT/webworld/latest.tsv" ]]; then
  echo "Expected explicit mixed backend allowance evidence" >&2
  cat "$MIXED_ALLOWED_DIR/config.tsv" >&2
  cat "$MIXED_ALLOWED_DIR/manifest.tsv" >&2
  cat "$MIXED_ALLOWED_DIR/results.tsv" >&2
  exit 1
fi

NO_LATEST_ROOT="${TMP_DIR}/no-latest"
NO_LATEST_DIR="${NO_LATEST_ROOT}/webworld/no-latest"
bash "$ROOT_DIR/scripts/capture-onnx-profile-baseline.sh" \
  --model webworld \
  --aqli-bin "$FAKE_AQLI" \
  --bench-script "$FAKE_BENCH" \
  --baseline-root "$NO_LATEST_ROOT" \
  --name webworld \
  --run-label no-latest \
  --no-require-profile \
  --no-update-latest >"${TMP_DIR}/no-latest.out"

if [[ -f "$NO_LATEST_ROOT/webworld/latest.tsv" ]]; then
  echo "Expected --no-update-latest to skip latest.tsv" >&2
  exit 1
fi
if grep -q '^latest	' "$NO_LATEST_DIR/results.tsv"; then
  echo "Expected no latest result row when disabled" >&2
  cat "$NO_LATEST_DIR/results.tsv" >&2
  exit 1
fi
if ! grep -q "verify-onnx-profile-regression.sh --model webworld --baseline $NO_LATEST_DIR/aggregate.tsv" "${TMP_DIR}/no-latest.out"; then
  echo "Expected no-latest report to point at run aggregate" >&2
  cat "${TMP_DIR}/no-latest.out" >&2
  exit 1
fi

printf 'ONNX profile baseline capture test passed\n'
