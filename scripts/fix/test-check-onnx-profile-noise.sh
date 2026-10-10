#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-onnx-profile-noise.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

AGGREGATE="${TMP_DIR}/aggregate.tsv"
cat > "$AGGREGATE" <<'TSV'
case	status	exitCode	durationMs	promptEvalTps	generationTps	decodeTps	ttftMs	tokenLatencyMs	onnxBackend	onnxTokenizeMs	onnxInputPrepMs	onnxPrefillRunMs	onnxDecodeRunMs	onnxOrtRunMs	onnxLogitsSelectMs	onnxSamplingMs	onnxSteps	combinedLog	stderrLog
measured-mean	pass		100.000	20.000	10.000	8.000	40.000	20.000	CoreML	2.000	3.000	4.000	5.000	9.000	1.000	1.000	2.000	/tmp/summary.tsv
measured-best	pass		95.000	22.000	11.000	9.000	38.000	19.000	CoreML	1.000	2.000	3.000	4.000	8.000	1.000	1.000	2.000	/tmp/summary.tsv
measured-worst	pass		105.000	18.000	9.000	7.000	44.000	22.000	CoreML	3.000	5.000	6.000	7.000	12.000	2.000	2.000	2.000	/tmp/summary.tsv
TSV

PASS_DIR="${TMP_DIR}/pass"
bash "$ROOT_DIR/scripts/check-onnx-profile-noise.sh" \
  --aggregate "$AGGREGATE" \
  --summary-dir "$PASS_DIR" \
  --out "$PASS_DIR/noise.tsv" \
  --max-noise-percent 25 \
  --metrics durationMs,generationTps,missingMetric >"${TMP_DIR}/pass.out"

if [[ ! -f "$PASS_DIR/config.tsv" || ! -f "$PASS_DIR/report.txt" || ! -f "$PASS_DIR/noise.tsv" ]]; then
  echo "Expected noise check artifacts" >&2
  find "$PASS_DIR" -maxdepth 1 -type f -print >&2 || true
  exit 1
fi
if ! grep -qx $'metric\tmean\tbest\tworst\tnoisePercent\tmaxNoisePercent\tstatus\treason' "$PASS_DIR/noise.tsv" \
    || ! grep -qx $'durationMs\t100.000\t95.000\t105.000\t10.000\t25\tpass\t' "$PASS_DIR/noise.tsv" \
    || ! grep -qx $'generationTps\t10.000\t11.000\t9.000\t20.000\t25\tpass\t' "$PASS_DIR/noise.tsv" \
    || ! grep -qx $'missingMetric\t\t\t\t\t25\tskip\tmissing-metric' "$PASS_DIR/noise.tsv"; then
  echo "Expected pass and skip noise rows" >&2
  cat "$PASS_DIR/noise.tsv" >&2
  exit 1
fi
if ! grep -qx 'failures=0' "$PASS_DIR/report.txt" \
    || ! grep -qx 'skips=1' "$PASS_DIR/report.txt"; then
  echo "Expected pass report counts" >&2
  cat "$PASS_DIR/report.txt" >&2
  exit 1
fi

FAIL_DIR="${TMP_DIR}/fail"
if bash "$ROOT_DIR/scripts/check-onnx-profile-noise.sh" \
    --aggregate "$AGGREGATE" \
    --summary-dir "$FAIL_DIR" \
    --out "$FAIL_DIR/noise.tsv" \
    --max-noise-percent 10 \
    --metrics durationMs,generationTps >"${TMP_DIR}/fail.out" 2>"${TMP_DIR}/fail.err"; then
  echo "Expected noise check to fail over threshold" >&2
  cat "${TMP_DIR}/fail.out" >&2
  cat "${TMP_DIR}/fail.err" >&2
  exit 1
fi
if ! grep -qx $'durationMs\t100.000\t95.000\t105.000\t10.000\t10\tpass\t' "$FAIL_DIR/noise.tsv" \
    || ! grep -qx $'generationTps\t10.000\t11.000\t9.000\t20.000\t10\tfail\t' "$FAIL_DIR/noise.tsv" \
    || ! grep -qx 'failures=1' "$FAIL_DIR/report.txt" \
    || ! grep -q 'generationTps mean=10.000 best=11.000 worst=9.000 noise=20.000% limit=10' "$FAIL_DIR/report.txt"; then
  echo "Expected failing noise evidence" >&2
  cat "$FAIL_DIR/noise.tsv" >&2
  cat "$FAIL_DIR/report.txt" >&2
  exit 1
fi

printf 'ONNX profile noise check test passed\n'
