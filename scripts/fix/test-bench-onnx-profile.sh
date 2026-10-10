#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-onnx-bench-profile.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

FAKE_AQLI="${TMP_DIR}/aqli"
cat > "$FAKE_AQLI" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

backend="${AQLI_FAKE_ONNX_BACKEND:-CoreML}"
printf 'Model: fake\n'
printf 'Provider: onnx\n'
printf '[Stream updates: 0, Duration: 1.00s, Speed: 1.00 t/s]\n'
printf 'onnx profile:\n'
printf '  backend       = %s\n' "$backend"
printf '  prompt eval   = 12.5 t/s\n'
printf '  generation    = 6.25 t/s\n'
printf '  decode         = 5.50 t/s\n'
printf '  latency (ttft)= 101 ms\n'
printf '  token latency = 22 ms/token\n'
printf '  tokenize      = 1.5 ms\n'
printf '  input prep    = 2.5 ms\n'
printf '  prefill run   = 10.5 ms\n'
printf '  decode run    = 12.5 ms\n'
printf '  ort run       = 20.5 ms\n'
printf '  logits select = 0.5 ms\n'
printf '  sampling      = 0.7 ms\n'
printf '  steps         = 2\n'
SH
chmod +x "$FAKE_AQLI"

PASS_OUT="${TMP_DIR}/pass"
bash "$ROOT_DIR/scripts/bench-onnx-profile.sh" \
  --model fake-model \
  --prompt "where is jakarta" \
  --max-tokens 2 \
  --runs 1 \
  --aqli-bin "$FAKE_AQLI" \
  --out-dir "$PASS_OUT" \
  --label pass \
  --expect-backend CoreML >"${TMP_DIR}/pass.out"

PASS_SUMMARY="${PASS_OUT}/pass/summary.tsv"
if ! grep -qx $'expect_backend\tCoreML' "${PASS_OUT}/pass/config.tsv" \
    || ! grep -qx $'run-1\tpass\t0\t1000\t12.5\t6.25\t5.50\t101\t22\tCoreML\t1.5\t2.5\t10.5\t12.5\t20.5\t0.5\t0.7\t2\t'"${PASS_OUT}"'/pass/logs/run-1.combined.log\t'"${PASS_OUT}"'/pass/logs/run-1.stderr.log' "$PASS_SUMMARY"; then
  echo "Expected backend-matched benchmark row" >&2
  cat "${PASS_OUT}/pass/config.tsv" >&2
  cat "$PASS_SUMMARY" >&2
  exit 1
fi

FAIL_OUT="${TMP_DIR}/fail"
if AQLI_FAKE_ONNX_BACKEND=CPU \
    bash "$ROOT_DIR/scripts/bench-onnx-profile.sh" \
      --model fake-model \
      --max-tokens 2 \
      --runs 1 \
      --aqli-bin "$FAKE_AQLI" \
      --out-dir "$FAIL_OUT" \
      --label fail \
      --expect-backend CoreML >"${TMP_DIR}/fail.out" 2>"${TMP_DIR}/fail.err"; then
  echo "Expected backend mismatch benchmark failure" >&2
  cat "${TMP_DIR}/fail.out" >&2
  cat "${TMP_DIR}/fail.err" >&2
  exit 1
fi

FAIL_SUMMARY="${FAIL_OUT}/fail/summary.tsv"
if ! grep -q "Expected ONNX backend 'CoreML' for run-1, got 'CPU'" "${TMP_DIR}/fail.err" \
    || ! grep -qx $'run-1\tfail\t4\t1000\t12.5\t6.25\t5.50\t101\t22\tCPU\t1.5\t2.5\t10.5\t12.5\t20.5\t0.5\t0.7\t2\t'"${FAIL_OUT}"'/fail/logs/run-1.combined.log\t'"${FAIL_OUT}"'/fail/logs/run-1.stderr.log' "$FAIL_SUMMARY"; then
  echo "Expected backend mismatch evidence" >&2
  cat "${TMP_DIR}/fail.err" >&2
  cat "$FAIL_SUMMARY" >&2
  exit 1
fi

printf 'ONNX profile benchmark test passed\n'
