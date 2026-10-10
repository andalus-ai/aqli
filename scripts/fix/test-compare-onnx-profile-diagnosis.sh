#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-onnx-diagnosis-compare.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

BASELINE="${TMP_DIR}/baseline-stages.tsv"
CURRENT="${TMP_DIR}/current-stages.tsv"

{
  printf 'stage\tmetric\tvalueMs\tshareOfOrtPercent\tshareOfDurationPercent\tpriority\trecommendation\n'
  printf 'decodeRun\tonnxDecodeRunMs\t40.000\t57.143\t3.636\tprimary\tFocus decode.\n'
  printf 'prefillRun\tonnxPrefillRunMs\t30.000\t42.857\t2.727\tsecondary\tFocus prefill.\n'
  printf 'tokenize\tonnxTokenizeMs\t2.000\t2.857\t0.182\tnormal\tFocus tokenizer.\n'
  printf 'sampling\tonnxSamplingMs\t4.000\t5.714\t0.364\tnormal\tFocus sampling.\n'
} > "$BASELINE"

{
  printf 'stage\tmetric\tvalueMs\tshareOfOrtPercent\tshareOfDurationPercent\tpriority\trecommendation\n'
  printf 'prefillRun\tonnxPrefillRunMs\t42.000\t60.000\t3.750\tprimary\tFocus prefill.\n'
  printf 'decodeRun\tonnxDecodeRunMs\t36.500\t52.143\t3.259\tsecondary\tFocus decode.\n'
  printf 'tokenize\tonnxTokenizeMs\t2.000\t2.857\t0.179\tnormal\tFocus tokenizer.\n'
  printf 'logitsSelect\tonnxLogitsSelectMs\t5.500\t7.857\t0.491\tnormal\tFocus logits.\n'
} > "$CURRENT"

OUT_DIR="${TMP_DIR}/diagnosis-diff"
bash "$ROOT_DIR/scripts/compare-onnx-profile-diagnosis.sh" \
  --baseline-stages "$BASELINE" \
  --current-stages "$CURRENT" \
  --summary-dir "$OUT_DIR" >"${TMP_DIR}/compare.out"

if ! grep -qx $'stage\tmetric\tbaselineMs\tcurrentMs\tdeltaMs\tdeltaPercent\tbaselinePriority\tcurrentPriority\tstatus\treason' "$OUT_DIR/comparison.tsv" \
    || ! grep -qx $'decodeRun\tonnxDecodeRunMs\t40.000\t36.500\t-3.500\t-8.750\tprimary\tsecondary\tfaster\t' "$OUT_DIR/comparison.tsv" \
    || ! grep -qx $'prefillRun\tonnxPrefillRunMs\t30.000\t42.000\t12.000\t40.000\tsecondary\tprimary\tslower\t' "$OUT_DIR/comparison.tsv" \
    || ! grep -qx $'tokenize\tonnxTokenizeMs\t2.000\t2.000\t0.000\t0.000\tnormal\tnormal\tsame\t' "$OUT_DIR/comparison.tsv" \
    || ! grep -qx $'sampling\tonnxSamplingMs\t4.000\t\t\t\tnormal\t\tskip\tmissing-current' "$OUT_DIR/comparison.tsv" \
    || ! grep -qx $'logitsSelect\tonnxLogitsSelectMs\t\t5.500\t\t\t\tnormal\tskip\tmissing-baseline' "$OUT_DIR/comparison.tsv"; then
  echo "Expected stage comparison rows" >&2
  cat "$OUT_DIR/comparison.tsv" >&2
  exit 1
fi

if ! grep -qx $'comparedStages\t3' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'fasterStages\t1' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'slowerStages\t1' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'sameStages\t1' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'skippedStages\t2' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'baselinePrimaryStage\tdecodeRun' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'currentPrimaryStage\tprefillRun' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'primaryStageChanged\ttrue' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'largestSlowdownStage\tprefillRun' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'largestSlowdownMs\t12.000' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'largestSpeedupStage\tdecodeRun' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'largestSpeedupMs\t-3.500' "$OUT_DIR/summary.tsv"; then
  echo "Expected diagnosis diff summary rows" >&2
  cat "$OUT_DIR/summary.tsv" >&2
  exit 1
fi

if [[ ! -f "$OUT_DIR/comparison-table.txt" ]] \
    || ! grep -Fqx "artifacts.comparison=$OUT_DIR/comparison.tsv" "${TMP_DIR}/compare.out" \
    || ! grep -Fqx "primaryStageChanged=true" "${TMP_DIR}/compare.out" \
    || ! grep -q '^decodeRun[[:space:]][[:space:]]*onnxDecodeRunMs' "$OUT_DIR/comparison-table.txt"; then
  echo "Expected diagnosis diff report/table" >&2
  cat "${TMP_DIR}/compare.out" >&2
  cat "$OUT_DIR/comparison-table.txt" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/compare-onnx-profile-diagnosis.sh" \
    --baseline-stages "$BASELINE" \
    --current-stages "$TMP_DIR/missing.tsv" >"${TMP_DIR}/missing.out" 2>"${TMP_DIR}/missing.err"; then
  echo "Expected missing current stages failure" >&2
  cat "${TMP_DIR}/missing.out" >&2
  cat "${TMP_DIR}/missing.err" >&2
  exit 1
fi
if ! grep -qx "Current stages not found: $TMP_DIR/missing.tsv" "${TMP_DIR}/missing.err"; then
  echo "Expected missing current stages diagnostic" >&2
  cat "${TMP_DIR}/missing.err" >&2
  exit 1
fi

printf 'ONNX diagnosis comparison test passed\n'
