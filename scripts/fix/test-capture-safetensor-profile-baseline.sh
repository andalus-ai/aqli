#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-safetensor-baseline-capture.XXXXXX")"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

FAKE_AQLI="${TMP_DIR}/aqli"
printf '#!/usr/bin/env bash\nexit 0\n' > "$FAKE_AQLI"
chmod +x "$FAKE_AQLI"

FAKE_BENCH="${TMP_DIR}/bench-safetensor-inference.sh"
cat > "$FAKE_BENCH" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

out_dir=""
label=""
summary=""
model=""
prompt=""
max_tokens=""
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
  case "${args[$i]}" in
    --out-dir) out_dir="${args[$((i + 1))]}" ;;
    --label) label="${args[$((i + 1))]}" ;;
    --summary-file) summary="${args[$((i + 1))]}" ;;
    --model) model="${args[$((i + 1))]}" ;;
    --det-prompt) prompt="${args[$((i + 1))]}" ;;
    --max-tokens) max_tokens="${args[$((i + 1))]}" ;;
  esac
done
run_dir="${out_dir}/${label}"
mkdir -p "$run_dir/diagnosis"
printf 'fake-bench %s\n' "$*" > "$run_dir/argv.txt"
printf 'case\tdurationMs\tbackend\tprofileMetal\tstatus\ttopStage\ttopStageMs\tprefillMs\tdecodeMs\ttpotMs\tattentionMs\tffnMs\tlogitsMs\tlinearPaths\tlogitsPaths\tffnPaths\tattentionPaths\tlog\n' > "$summary"
printf 'metal-deterministic\t140\tmetal\ttrue\tpassed\tdecode\t18.00\t4.00\t18.00\t9.00\t6.00\t14.00\t3.00\tlinear=metal\tlogits=metal\tffn=metal\tattention=metal\t/tmp/fake.log\n' >> "$summary"
printf 'case_id\tstatus\tbackend\tmetal\ttopStage\ttopStageMs\tprefillMs\tdecodeMs\ttpotMs\tattentionMs\tffnMs\tlogitsMs\tlinearPaths\tlogitsPaths\tffnPaths\tattentionPaths\tlogFile\n' > "$run_dir/profile.tsv"
printf 'metal-deterministic\tpassed\tmetal\ttrue\tdecode\t18.00\t4.00\t18.00\t9.00\t6.00\t14.00\t3.00\tlinear=metal\tlogits=metal\tffn=metal\tattention=metal\t/tmp/fake.log\n' >> "$run_dir/profile.tsv"
printf '{"model":"%s","maxTokens":"%s"}\n' "$model" "$max_tokens" > "$run_dir/summary.json"
printf 'case,status\nmetal-deterministic,passed\n' > "$run_dir/summary.csv"
printf 'report for %s\n' "$prompt" > "$run_dir/report.txt"
printf 'key\tvalue\nstatus\tpass\nprimaryStage\tdecode\nprimaryMetric\tdecodeMs\nprimaryValueMs\t18.000\n' > "$run_dir/diagnosis/diagnosis.tsv"
printf 'stage\tmetric\tvalueMs\tshareOfDurationPercent\tpriority\tpathEvidence\trecommendation\n' > "$run_dir/diagnosis/stages.tsv"
printf 'decode\tdecodeMs\t18.000\t12.857\tprimary\tlinear=metal\tFocus decode.\n' >> "$run_dir/diagnosis/stages.tsv"
printf 'logits\tlogitsMs\t3.000\t2.143\tnormal\tlogits=metal\tFocus logits.\n' >> "$run_dir/diagnosis/stages.tsv"
printf 'diagnosis report\n' > "$run_dir/diagnosis/report.txt"
if [[ "${AQLI_FAKE_NO_DIAGNOSIS:-false}" == "true" ]]; then
  rm -f "$run_dir/diagnosis/stages.tsv"
fi
SH
chmod +x "$FAKE_BENCH"

BASELINE_ROOT="${TMP_DIR}/baselines"
PASS_DIR="${BASELINE_ROOT}/gemma/fixed"
bash "$ROOT_DIR/scripts/capture-safetensor-profile-baseline.sh" \
  --model google/gemma-test \
  --aqli-bin "$FAKE_AQLI" \
  --bench-script "$FAKE_BENCH" \
  --baseline-root "$BASELINE_ROOT" \
  --name gemma \
  --run-label fixed \
  --prompt "where is jakarta" \
  --max-tokens 4 \
  --require-metal \
  --max-decode-ms 30 \
  --with-cpu > "${TMP_DIR}/capture.out"

if [[ ! -f "$PASS_DIR/gate.tsv" \
    || ! -f "$PASS_DIR/profile.tsv" \
    || ! -f "$PASS_DIR/summary.json" \
    || ! -f "$PASS_DIR/summary.csv" \
    || ! -f "$PASS_DIR/bench-report.txt" \
    || ! -f "$PASS_DIR/diagnosis.tsv" \
    || ! -f "$PASS_DIR/stages.tsv" \
    || ! -f "$PASS_DIR/diagnosis-report.txt" \
    || ! -f "$PASS_DIR/config.tsv" \
    || ! -f "$PASS_DIR/manifest.tsv" \
    || ! -f "$PASS_DIR/results.tsv" \
    || ! -f "$PASS_DIR/report.txt" \
    || ! -f "$BASELINE_ROOT/gemma/latest-gate.tsv" \
    || ! -f "$BASELINE_ROOT/gemma/latest-profile.tsv" \
    || ! -f "$BASELINE_ROOT/gemma/latest-diagnosis.tsv" \
    || ! -f "$BASELINE_ROOT/gemma/latest-stages.tsv" \
    || ! -f "$BASELINE_ROOT/gemma/latest-manifest.tsv" ]]; then
  echo "Expected safetensor baseline capture artifacts" >&2
  find "$BASELINE_ROOT" -maxdepth 3 -type f -print >&2 || true
  exit 1
fi

if ! cmp -s "$PASS_DIR/stages.tsv" "$BASELINE_ROOT/gemma/latest-stages.tsv" \
    || ! cmp -s "$PASS_DIR/gate.tsv" "$BASELINE_ROOT/gemma/latest-gate.tsv" \
    || ! cmp -s "$PASS_DIR/manifest.tsv" "$BASELINE_ROOT/gemma/latest-manifest.tsv"; then
  echo "Expected latest artifacts to mirror captured baseline artifacts" >&2
  exit 1
fi
if ! grep -qx $'baselineName\tgemma' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'runLabel\tfixed' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'model\tgoogle/gemma-test' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'prompt\twhere is jakarta' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'maxTokens\t4' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'requireMetal\t1' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'requireProfile\t1' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'stages\t'"$PASS_DIR"'/stages.tsv' "$PASS_DIR/config.tsv" \
    || ! grep -qx $'latestStages\t'"$BASELINE_ROOT"'/gemma/latest-stages.tsv' "$PASS_DIR/config.tsv"; then
  echo "Expected safetensor capture config rows" >&2
  cat "$PASS_DIR/config.tsv" >&2
  exit 1
fi
if ! grep -qx $'benchmark\tpass\t0\t'"$PASS_DIR"'/gate.tsv\t'"$PASS_DIR"'/bench.stdout.log\t'"$PASS_DIR"'/bench.stderr.log\t' "$PASS_DIR/results.tsv" \
    || ! grep -qx $'diagnosis\tpass\t0\t'"$PASS_DIR"'/stages.tsv\t'"$PASS_DIR"'/bench.stdout.log\t'"$PASS_DIR"'/bench.stderr.log\t' "$PASS_DIR/results.tsv" \
    || ! grep -qx $'latest\tpass\t0\t'"$BASELINE_ROOT"'/gemma/latest-stages.tsv\t\t\t' "$PASS_DIR/results.tsv"; then
  echo "Expected safetensor capture result rows" >&2
  cat "$PASS_DIR/results.tsv" >&2
  exit 1
fi
if ! grep -q -- '--with-cpu' "$PASS_DIR/bench/profile/argv.txt" \
    || ! grep -q -- '--require-metal' "$PASS_DIR/bench/profile/argv.txt" \
    || ! grep -q -- '--max-decode-ms 30' "$PASS_DIR/bench/profile/argv.txt"; then
  echo "Expected capture to pass selected benchmark options" >&2
  cat "$PASS_DIR/bench/profile/argv.txt" >&2
  exit 1
fi
if ! grep -qx 'Aqli safetensor profile baseline captured' "${TMP_DIR}/capture.out" \
    || ! grep -qx "artifacts.latestStages=$BASELINE_ROOT/gemma/latest-stages.tsv" "${TMP_DIR}/capture.out" \
    || ! grep -qx "next.verify=./scripts/verify-fast-speed-gates.sh --only safetensor --safetensor-model google/gemma-test --safetensor-baseline-stages $BASELINE_ROOT/gemma/latest-stages.tsv" "${TMP_DIR}/capture.out"; then
  echo "Expected capture stdout to include artifact paths and next command" >&2
  cat "${TMP_DIR}/capture.out" >&2
  exit 1
fi

NO_LATEST_DIR="${BASELINE_ROOT}/gemma/no-latest"
bash "$ROOT_DIR/scripts/capture-safetensor-profile-baseline.sh" \
  --model google/gemma-test \
  --aqli-bin "$FAKE_AQLI" \
  --bench-script "$FAKE_BENCH" \
  --baseline-root "$BASELINE_ROOT" \
  --name gemma \
  --run-label no-latest \
  --no-update-latest > "${TMP_DIR}/no-latest.out"
if ! grep -qx $'latest\tskip\t0\t'"$BASELINE_ROOT"'/gemma/latest-stages.tsv\t\t\tdisabled' "$NO_LATEST_DIR/results.tsv"; then
  echo "Expected no-update-latest to skip latest pointer updates" >&2
  cat "$NO_LATEST_DIR/results.tsv" >&2
  exit 1
fi

ENV_DEFAULT_ROOT="${TMP_DIR}/env-default-baselines"
ENV_DEFAULT_DIR="${ENV_DEFAULT_ROOT}/env-gemma/env-default"
AQLI_CAPTURE_SAFETENSOR_BASELINE_ROOT="$ENV_DEFAULT_ROOT" \
  bash "$ROOT_DIR/scripts/capture-safetensor-profile-baseline.sh" \
    --model google/gemma-test \
    --aqli-bin "$FAKE_AQLI" \
    --bench-script "$FAKE_BENCH" \
    --name env-gemma \
    --run-label env-default > "${TMP_DIR}/env-default.out"
if [[ ! -f "$ENV_DEFAULT_DIR/stages.tsv" \
    || ! -f "$ENV_DEFAULT_ROOT/env-gemma/latest-stages.tsv" ]]; then
  echo "Expected env default baseline root to capture latest stages" >&2
  find "$ENV_DEFAULT_ROOT" -maxdepth 3 -type f -print >&2 || true
  exit 1
fi
if ! grep -qx $'baselineRoot\t'"$ENV_DEFAULT_ROOT" "$ENV_DEFAULT_DIR/config.tsv" \
    || ! grep -qx "artifacts.latestStages=$ENV_DEFAULT_ROOT/env-gemma/latest-stages.tsv" "${TMP_DIR}/env-default.out"; then
  echo "Expected env default baseline root in config and stdout" >&2
  cat "$ENV_DEFAULT_DIR/config.tsv" >&2
  cat "${TMP_DIR}/env-default.out" >&2
  exit 1
fi

MISSING_DIR="${BASELINE_ROOT}/gemma/missing"
set +e
AQLI_FAKE_NO_DIAGNOSIS=true bash "$ROOT_DIR/scripts/capture-safetensor-profile-baseline.sh" \
  --model google/gemma-test \
  --aqli-bin "$FAKE_AQLI" \
  --bench-script "$FAKE_BENCH" \
  --baseline-root "$BASELINE_ROOT" \
  --name gemma \
  --run-label missing > "${TMP_DIR}/missing.out" 2> "${TMP_DIR}/missing.err"
missing_exit=$?
set -e
if [[ "$missing_exit" -ne 3 ]]; then
  echo "Expected missing diagnosis capture failure" >&2
  cat "${TMP_DIR}/missing.out" >&2
  cat "${TMP_DIR}/missing.err" >&2
  exit 1
fi
if ! grep -qx $'diagnosis\tfail\t3\t'"$MISSING_DIR"'/bench/profile/diagnosis/stages.tsv\t'"$MISSING_DIR"'/bench.stdout.log\t'"$MISSING_DIR"'/bench.stderr.log\tmissing-diagnosis' "$MISSING_DIR/results.tsv" \
    || ! grep -qx "Safetensor diagnosis artifacts were not generated: $MISSING_DIR/bench/profile/diagnosis/stages.tsv" "${TMP_DIR}/missing.err"; then
  echo "Expected missing diagnosis failure result" >&2
  cat "$MISSING_DIR/results.tsv" >&2
  cat "${TMP_DIR}/missing.err" >&2
  exit 1
fi

printf 'safetensor baseline capture test passed\n'
