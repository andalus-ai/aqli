#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-onnx-profile-gates.XXXXXX")"
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
profile_policy=""
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
    --require-profile|--no-require-profile) profile_policy="$1"; shift ;;
    *) shift ;;
  esac
done
run_dir="${out_dir}/${label}"
mkdir -p "${run_dir}/logs"
summary="${run_dir}/summary.tsv"
printf 'case\tstatus\texitCode\tdurationMs\tpromptEvalTps\tgenerationTps\tdecodeTps\tttftMs\ttokenLatencyMs\tonnxBackend\tonnxTokenizeMs\tonnxInputPrepMs\tonnxPrefillRunMs\tonnxDecodeRunMs\tonnxOrtRunMs\tonnxLogitsSelectMs\tonnxSamplingMs\tonnxSteps\tcombinedLog\tstderrLog\n' > "$summary"
printf 'warmup-1\tpass\t0\t1500\t10\t2.5\t3.0\t300\t80\tCPU\t4\t5\t40\t60\t100\t6\t7\t1\t%s\t%s\n' "${run_dir}/logs/warmup.combined.log" "${run_dir}/logs/warmup.stderr.log" >> "$summary"
printf 'run-1\tpass\t0\t900\t20\t4.5\t6.0\t120\t30\tCoreML\t1\t2\t8\t12\t25\t1\t2\t2\t%s\t%s\n' "${run_dir}/logs/run.combined.log" "${run_dir}/logs/run.stderr.log" >> "$summary"
printf 'fake bench model=%s prompt=%s max_tokens=%s runs=%s warmup_runs=%s aqli_bin=%s policy=%s expect_backend=%s\n' "$model" "$prompt" "$max_tokens" "$runs" "$warmup_runs" "$aqli_bin" "$profile_policy" "$expect_backend"
SH
chmod +x "$FAKE_BENCH"

PASS_DIR="${TMP_DIR}/pass"
bash "$ROOT_DIR/scripts/verify-onnx-profile-gates.sh" \
  --model fake-model \
  --prompt "where is jakarta" \
  --max-tokens 4 \
  --runs 1 \
  --warmup-runs 1 \
  --aqli-bin "$FAKE_AQLI" \
  --bench-script "$FAKE_BENCH" \
  --summary-dir "$PASS_DIR" \
  --expect-backend CoreML \
  --max-duration-ms 1000 \
  --min-generation-tps 4 \
  --min-decode-tps 5 \
  --max-ttft-ms 150 \
  --max-token-latency-ms 40 \
  --max-onnx-tokenize-ms 2 \
  --max-onnx-input-prep-ms 3 \
  --max-onnx-prefill-run-ms 10 \
  --max-onnx-decode-run-ms 15 \
  --max-onnx-ort-run-ms 30 \
  --max-onnx-logits-select-ms 2 \
  --max-onnx-sampling-ms 3 >"${TMP_DIR}/pass.out"

if ! grep -qx $'stage\tstatus\texitCode\tartifact\tstdout\tstderr\treason' "$PASS_DIR/results.tsv" \
    || ! grep -qx "benchmark	pass	0	$PASS_DIR/bench/profile/summary.tsv	$PASS_DIR/bench.stdout.log	$PASS_DIR/bench.stderr.log	" "$PASS_DIR/results.tsv" \
    || ! grep -qx "contracts	pass	0	$PASS_DIR/contracts.tsv	$PASS_DIR/bench.stdout.log	$PASS_DIR/bench.stderr.log	" "$PASS_DIR/results.tsv"; then
  echo "Expected pass results for ONNX profile gate" >&2
  cat "$PASS_DIR/results.tsv" >&2
  exit 1
fi
if ! grep -qx $'expectBackend\tCoreML' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'decision\t'"$PASS_DIR"'/decision.tsv' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'decisionJson\t'"$PASS_DIR"'/decision.json' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'bundle\t'"$PASS_DIR"'/bundle.tsv' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'bundleJson\t'"$PASS_DIR"'/bundle.json' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'18\t--expect-backend' "$PASS_DIR/bench.argv.tsv" \
    || ! grep -qx $'19\tCoreML' "$PASS_DIR/bench.argv.tsv" \
    || ! grep -q 'expect_backend=CoreML' "$PASS_DIR/bench.stdout.log"; then
  echo "Expected threshold gate to pass expected backend to benchmark" >&2
  cat "$PASS_DIR/config.tsv" >&2
  cat "$PASS_DIR/bench.argv.tsv" >&2
  cat "$PASS_DIR/bench.stdout.log" >&2
  exit 1
fi
if ! grep -qx "artifacts.decision=$PASS_DIR/decision.tsv" "${TMP_DIR}/pass.out" \
    || ! grep -qx "artifacts.decisionJson=$PASS_DIR/decision.json" "${TMP_DIR}/pass.out" \
    || ! grep -qx "artifacts.bundle=$PASS_DIR/bundle.tsv" "${TMP_DIR}/pass.out" \
    || ! grep -qx "artifacts.bundleJson=$PASS_DIR/bundle.json" "${TMP_DIR}/pass.out" \
    || ! grep -qx $'key\tvalue' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'status\tpass' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'failedStages\t0' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'lastStage\tcontracts' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'contractFailures\t0' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'contractChecks\t12' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'bundleArtifacts\t10' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'bundleRequiredMissing\t0' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'bundle\t'"$PASS_DIR"'/bundle.tsv' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'contracts\t'"$PASS_DIR"'/contracts.tsv' "$PASS_DIR/decision.tsv" \
    || ! grep -qx $'json\t'"$PASS_DIR"'/decision.json' "$PASS_DIR/decision.tsv"; then
  echo "Expected passing gate decision summary" >&2
  cat "${TMP_DIR}/pass.out" >&2
  cat "$PASS_DIR/decision.tsv" >&2
  exit 1
fi
if ! grep -qx $'kind\tpath\trequired\tstatus\tdescription' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'config\t'"$PASS_DIR"'/config.tsv\trequired\tpresent\tGate configuration' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'results\t'"$PASS_DIR"'/results.tsv\trequired\tpresent\tStage results' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'benchSummary\t'"$PASS_DIR"'/bench/profile/summary.tsv\trequired\tpresent\tRaw benchmark summary' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'contracts\t'"$PASS_DIR"'/contracts.tsv\trequired\tpresent\tThreshold contract evidence' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'slowest\t'"$PASS_DIR"'/slowest.tsv\toptional\tpresent\tSlowest benchmark rows' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'decision\t'"$PASS_DIR"'/decision.tsv\trequired\tpresent\tGate decision summary' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'decisionJson\t'"$PASS_DIR"'/decision.json\toptional\tpresent\tGate decision JSON' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'benchStdout\t'"$PASS_DIR"'/bench.stdout.log\toptional\tpresent\tBenchmark stdout' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'benchStderr\t'"$PASS_DIR"'/bench.stderr.log\toptional\tpresent\tBenchmark stderr' "$PASS_DIR/bundle.tsv" \
    || ! grep -qx $'benchArgv\t'"$PASS_DIR"'/bench.argv.tsv\toptional\tpresent\tBenchmark argv' "$PASS_DIR/bundle.tsv"; then
  echo "Expected gate artifact bundle rows" >&2
  cat "$PASS_DIR/bundle.tsv" >&2
  exit 1
fi
if [[ ! -f "$PASS_DIR/bundle.json" ]] \
    || ! grep -Fq '"kind": "benchSummary"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"kind": "decisionJson"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"total": "10"' "$PASS_DIR/bundle.json" \
    || ! grep -Fq '"requiredMissing": "0"' "$PASS_DIR/bundle.json"; then
  echo "Expected gate bundle JSON summary" >&2
  [[ -f "$PASS_DIR/bundle.json" ]] && cat "$PASS_DIR/bundle.json" >&2
  exit 1
fi
if grep -q '^warmup-1	' "$PASS_DIR/contracts.tsv"; then
  echo "Expected warmup contracts to be skipped by default" >&2
  cat "$PASS_DIR/contracts.tsv" >&2
  exit 1
fi
if ! grep -qx $'run-1\tonnxDecodeRunMs\t12\t<=\t15\tpass\t' "$PASS_DIR/contracts.tsv" \
    || ! grep -qx $'run-1\tgenerationTps\t4.5\t>=\t4\tpass\t' "$PASS_DIR/contracts.tsv"; then
  echo "Expected run contracts to pass with threshold evidence" >&2
  cat "$PASS_DIR/contracts.tsv" >&2
  exit 1
fi
if ! grep -qx $'rank\tcase\tdurationMs\tgenerationTps\tonnxBackend\tonnxOrtRunMs\tonnxDecodeRunMs\tcombinedLog' "$PASS_DIR/slowest.tsv" \
    || ! grep -qx "1	warmup-1	1500	2.5	CPU	100	60	$PASS_DIR/bench/profile/logs/warmup.combined.log" "$PASS_DIR/slowest.tsv" \
    || ! grep -qx "2	run-1	900	4.5	CoreML	25	12	$PASS_DIR/bench/profile/logs/run.combined.log" "$PASS_DIR/slowest.tsv"; then
  echo "Expected slowest report to rank benchmark rows" >&2
  cat "$PASS_DIR/slowest.tsv" >&2
  exit 1
fi

WARMUP_DIR="${TMP_DIR}/warmup"
bash "$ROOT_DIR/scripts/verify-onnx-profile-gates.sh" \
  --model fake-model \
  --aqli-bin "$FAKE_AQLI" \
  --bench-script "$FAKE_BENCH" \
  --summary-dir "$WARMUP_DIR" \
  --include-warmup-contracts \
  --max-duration-ms 2000 >/dev/null

if ! grep -qx $'warmup-1\tdurationMs\t1500\t<=\t2000\tpass\t' "$WARMUP_DIR/contracts.tsv"; then
  echo "Expected opt-in warmup contracts" >&2
  cat "$WARMUP_DIR/contracts.tsv" >&2
  exit 1
fi

FAIL_DIR="${TMP_DIR}/fail"
if bash "$ROOT_DIR/scripts/verify-onnx-profile-gates.sh" \
    --model fake-model \
    --aqli-bin "$FAKE_AQLI" \
    --bench-script "$FAKE_BENCH" \
    --summary-dir "$FAIL_DIR" \
    --max-onnx-decode-run-ms 10 >"${TMP_DIR}/fail.out"; then
  echo "Expected ONNX profile gate threshold failure" >&2
  cat "${TMP_DIR}/fail.out" >&2
  exit 1
fi
if ! grep -qx $'run-1\tonnxDecodeRunMs\t12\t<=\t10\tfail\t' "$FAIL_DIR/contracts.tsv" \
    || ! grep -q '^contracts	fail	42	' "$FAIL_DIR/results.tsv" \
    || ! grep -q 'contract failures:' "${TMP_DIR}/fail.out" \
    || ! grep -qx "artifacts.decision=$FAIL_DIR/decision.tsv" "${TMP_DIR}/fail.out" \
    || ! grep -qx "artifacts.decisionJson=$FAIL_DIR/decision.json" "${TMP_DIR}/fail.out" \
    || ! grep -qx "artifacts.bundle=$FAIL_DIR/bundle.tsv" "${TMP_DIR}/fail.out" \
    || ! grep -qx "artifacts.bundleJson=$FAIL_DIR/bundle.json" "${TMP_DIR}/fail.out" \
    || ! grep -qx $'contracts\t'"$FAIL_DIR"'/contracts.tsv\trequired\tpresent\tThreshold contract evidence' "$FAIL_DIR/bundle.tsv" \
    || ! grep -qx $'decision\t'"$FAIL_DIR"'/decision.tsv\trequired\tpresent\tGate decision summary' "$FAIL_DIR/bundle.tsv" \
    || ! grep -qx $'decisionJson\t'"$FAIL_DIR"'/decision.json\toptional\tpresent\tGate decision JSON' "$FAIL_DIR/bundle.tsv"; then
  echo "Expected failed contract evidence" >&2
  cat "$FAIL_DIR/contracts.tsv" >&2
  cat "$FAIL_DIR/results.tsv" >&2
  cat "$FAIL_DIR/bundle.tsv" >&2
  cat "${TMP_DIR}/fail.out" >&2
  exit 1
fi
if ! grep -Fq '"kind": "contracts"' "$FAIL_DIR/bundle.json" \
    || ! grep -Fq '"requiredMissing": "0"' "$FAIL_DIR/bundle.json"; then
  echo "Expected failed gate bundle JSON summary" >&2
  cat "$FAIL_DIR/bundle.json" >&2
  exit 1
fi
if ! grep -qx $'status\tfail' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'failureStage\tcontracts' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'failureReason\tcontract-failures=1' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'contractFailures\t1' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'firstContractFailureMetric\tonnxDecodeRunMs' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'worstContractMetric\tonnxDecodeRunMs' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'worstContractPercent\t20.000' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'bundleArtifacts\t10' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'bundleRequiredMissing\t0' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'bundle\t'"$FAIL_DIR"'/bundle.tsv' "$FAIL_DIR/decision.tsv" \
    || ! grep -qx $'json\t'"$FAIL_DIR"'/decision.json' "$FAIL_DIR/decision.tsv"; then
  echo "Expected failed gate decision summary" >&2
  cat "$FAIL_DIR/decision.tsv" >&2
  exit 1
fi

printf 'ONNX profile gate verifier test passed\n'
