#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-onnx-profile-diagnosis.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

write_header() {
  printf 'case\tstatus\texitCode\tdurationMs\tpromptEvalTps\tgenerationTps\tdecodeTps\tttftMs\ttokenLatencyMs\tonnxBackend\tonnxTokenizeMs\tonnxInputPrepMs\tonnxPrefillRunMs\tonnxDecodeRunMs\tonnxOrtRunMs\tonnxLogitsSelectMs\tonnxSamplingMs\tonnxSteps\tcombinedLog\tstderrLog\n'
}

SUMMARY="${TMP_DIR}/summary.tsv"
{
  write_header
  printf 'warmup-1\tpass\t0\t2000\t10\t3\t2\t500\t140\tCPU\t9\t10\t100\t180\t300\t12\t14\t1\t/tmp/warmup.log\t/tmp/warmup.err\n'
  printf 'run-1\tpass\t0\t1000\t20\t10\t8\t200\t50\tCoreML\t2\t3\t20\t30\t60\t4\t5\t2\t/tmp/run-1.log\t/tmp/run-1.err\n'
  printf 'run-2\tpass\t0\t1200\t18\t8\t7\t240\t60\tCoreML\t3\t5\t25\t90\t120\t6\t7\t3\t/tmp/run-2.log\t/tmp/run-2.err\n'
} > "$SUMMARY"

AGGREGATE="${TMP_DIR}/aggregate.tsv"
{
  write_header
  printf 'measured-mean\tpass\t\t1100.000\t19.000\t9.000\t7.500\t220.000\t55.000\tCoreML\t2.500\t4.000\t22.500\t60.000\t90.000\t5.000\t6.000\t2.500\t/tmp/summary.tsv\t\n'
  printf 'measured-best\tpass\t\t1000.000\t20.000\t10.000\t8.000\t200.000\t50.000\tCoreML\t2.000\t3.000\t20.000\t30.000\t60.000\t4.000\t5.000\t2.000\t/tmp/summary.tsv\t\n'
  printf 'measured-worst\tpass\t\t1200.000\t18.000\t8.000\t7.000\t240.000\t60.000\tCoreML\t3.000\t5.000\t25.000\t90.000\t120.000\t6.000\t7.000\t3.000\t/tmp/summary.tsv\t\n'
} > "$AGGREGATE"

AGG_DIR="${TMP_DIR}/aggregate-diagnosis"
bash "$ROOT_DIR/scripts/diagnose-onnx-profile-summary.sh" \
  --summary "$AGGREGATE" \
  --summary-dir "$AGG_DIR" >"${TMP_DIR}/aggregate.out"

if ! grep -qx $'status\tpass' "$AGG_DIR/diagnosis.tsv" \
    || ! grep -qx $'selectedCase\tmeasured-mean' "$AGG_DIR/diagnosis.tsv" \
    || ! grep -qx $'selectedRows\t1' "$AGG_DIR/diagnosis.tsv" \
    || ! grep -qx $'backend\tCoreML' "$AGG_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryStage\tdecodeRun' "$AGG_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryMetric\tonnxDecodeRunMs' "$AGG_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryValueMs\t60.000' "$AGG_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryShareOfOrtPercent\t66.667' "$AGG_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryShareOfDurationPercent\t5.455' "$AGG_DIR/diagnosis.tsv" \
    || ! grep -qx $'decodeRun\tonnxDecodeRunMs\t60.000\t66.667\t5.455\tprimary\tFocus decode loop, KV cache feeds, provider placement, and tensor reuse.' "$AGG_DIR/stages.tsv" \
    || ! grep -qx "artifacts.diagnosis=$AGG_DIR/diagnosis.tsv" "${TMP_DIR}/aggregate.out"; then
  echo "Expected aggregate diagnosis" >&2
  cat "$AGG_DIR/diagnosis.tsv" >&2
  cat "$AGG_DIR/stages.tsv" >&2
  cat "${TMP_DIR}/aggregate.out" >&2
  exit 1
fi

RAW_DIR="${TMP_DIR}/raw-diagnosis"
bash "$ROOT_DIR/scripts/diagnose-onnx-profile-summary.sh" \
  --summary "$SUMMARY" \
  --summary-dir "$RAW_DIR" >"${TMP_DIR}/raw.out"

if ! grep -qx $'selectedCase\tmean' "$RAW_DIR/diagnosis.tsv" \
    || ! grep -qx $'selectedRows\t2' "$RAW_DIR/diagnosis.tsv" \
    || ! grep -qx $'backend\tCoreML' "$RAW_DIR/diagnosis.tsv" \
    || ! grep -qx $'durationMs\t1100.000' "$RAW_DIR/diagnosis.tsv" \
    || ! grep -qx $'onnxOrtRunMs\t90.000' "$RAW_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryStage\tdecodeRun' "$RAW_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryValueMs\t60.000' "$RAW_DIR/diagnosis.tsv"; then
  echo "Expected raw measured-row diagnosis" >&2
  cat "$RAW_DIR/diagnosis.tsv" >&2
  cat "$RAW_DIR/stages.tsv" >&2
  exit 1
fi

CASE_DIR="${TMP_DIR}/case-diagnosis"
bash "$ROOT_DIR/scripts/diagnose-onnx-profile-summary.sh" \
  --summary "$SUMMARY" \
  --summary-dir "$CASE_DIR" \
  --case run-1 >/dev/null

if ! grep -qx $'selectedCase\trun-1' "$CASE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryStage\tdecodeRun' "$CASE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryMetric\tonnxDecodeRunMs' "$CASE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryValueMs\t30.000' "$CASE_DIR/diagnosis.tsv"; then
  echo "Expected explicit case diagnosis" >&2
  cat "$CASE_DIR/diagnosis.tsv" >&2
  cat "$CASE_DIR/stages.tsv" >&2
  exit 1
fi

WARMUP_DIR="${TMP_DIR}/warmup-diagnosis"
bash "$ROOT_DIR/scripts/diagnose-onnx-profile-summary.sh" \
  --summary "$SUMMARY" \
  --summary-dir "$WARMUP_DIR" \
  --include-warmup >/dev/null

if ! grep -qx $'selectedRows\t3' "$WARMUP_DIR/diagnosis.tsv" \
    || ! grep -qx $'backend\tmixed' "$WARMUP_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryStage\tdecodeRun' "$WARMUP_DIR/diagnosis.tsv"; then
  echo "Expected warmup-inclusive diagnosis" >&2
  cat "$WARMUP_DIR/diagnosis.tsv" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/diagnose-onnx-profile-summary.sh" \
    --summary "$SUMMARY" \
    --case missing >"${TMP_DIR}/missing.out" 2>"${TMP_DIR}/missing.err"; then
  echo "Expected missing case diagnosis failure" >&2
  cat "${TMP_DIR}/missing.out" >&2
  cat "${TMP_DIR}/missing.err" >&2
  exit 1
fi
if ! grep -qx 'Requested case not found: missing' "${TMP_DIR}/missing.err"; then
  echo "Expected missing case diagnostic" >&2
  cat "${TMP_DIR}/missing.err" >&2
  exit 1
fi

printf 'ONNX profile diagnosis test passed\n'
