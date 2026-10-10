#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-safetensor-diagnosis-compare.XXXXXX")"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

BASELINE="${TMP_DIR}/baseline-stages.tsv"
CURRENT="${TMP_DIR}/current-stages.tsv"

{
  printf 'stage\tmetric\tvalueMs\tshareOfDurationPercent\tpriority\tpathEvidence\trecommendation\n'
  printf 'decode\tdecodeMs\t80.000\t8.000\tprimary\tmetal_decode=2\tFocus decode.\n'
  printf 'ffn\tffnMs\t60.000\t6.000\tsecondary\tmetal_geglu=2\tFocus FFN.\n'
  printf 'attention\tattentionMs\t20.000\t2.000\tnormal\tpaged_metal=2\tFocus attention.\n'
  printf 'tpot\ttpotMs\t30.000\t3.000\tcontext\tmetal_loop=2\tFocus tpot.\n'
} > "$BASELINE"

{
  printf 'stage\tmetric\tvalueMs\tshareOfDurationPercent\tpriority\tpathEvidence\trecommendation\n'
  printf 'decode\tdecodeMs\t88.000\t8.800\tprimary\tmetal_decode=2\tFocus decode.\n'
  printf 'ffn\tffnMs\t75.000\t7.500\tsecondary\tmetal_geglu=2\tFocus FFN.\n'
  printf 'attention\tattentionMs\t15.000\t1.500\tnormal\tpaged_metal=2\tFocus attention.\n'
  printf 'logits\tlogitsMs\t9.000\t0.900\tnormal\tmetal_logits=1\tFocus logits.\n'
} > "$CURRENT"

COMPARE_DIR="${TMP_DIR}/compare"
bash "$ROOT_DIR/scripts/compare-safetensor-profile-diagnosis.sh" \
  --baseline-stages "$BASELINE" \
  --current-stages "$CURRENT" \
  --summary-dir "$COMPARE_DIR" > "${TMP_DIR}/compare.out"

if ! grep -qx $'stage\tmetric\tbaselineMs\tcurrentMs\tdeltaMs\tdeltaPercent\tbaselinePriority\tcurrentPriority\ttrend\tgateStatus\tgateReason' "$COMPARE_DIR/comparison.tsv" \
    || ! grep -qx $'decode\tdecodeMs\t80.000\t88.000\t8.000\t10.000\tprimary\tprimary\tslower\tnot-configured\t' "$COMPARE_DIR/comparison.tsv" \
    || ! grep -qx $'ffn\tffnMs\t60.000\t75.000\t15.000\t25.000\tsecondary\tsecondary\tslower\tnot-configured\t' "$COMPARE_DIR/comparison.tsv" \
    || ! grep -qx $'attention\tattentionMs\t20.000\t15.000\t-5.000\t-25.000\tnormal\tnormal\tfaster\tnot-configured\t' "$COMPARE_DIR/comparison.tsv" \
    || ! grep -qx $'tpot\ttpotMs\t30.000\t\t\t\tcontext\t\tskip\tskip\tmissing-current' "$COMPARE_DIR/comparison.tsv" \
    || ! grep -qx $'logits\tlogitsMs\t\t9.000\t\t\t\tnormal\tskip\tskip\tmissing-baseline' "$COMPARE_DIR/comparison.tsv"; then
  echo "Expected safetensor diagnosis comparison rows" >&2
  cat "$COMPARE_DIR/comparison.tsv" >&2
  exit 1
fi

if ! grep -qx $'status\tpass' "$COMPARE_DIR/summary.tsv" \
    || ! grep -qx $'comparedStages\t3' "$COMPARE_DIR/summary.tsv" \
    || ! grep -qx $'fasterStages\t1' "$COMPARE_DIR/summary.tsv" \
    || ! grep -qx $'slowerStages\t2' "$COMPARE_DIR/summary.tsv" \
    || ! grep -qx $'skippedStages\t2' "$COMPARE_DIR/summary.tsv" \
    || ! grep -qx $'gatedStages\t0' "$COMPARE_DIR/summary.tsv" \
    || ! grep -qx $'failedStages\t0' "$COMPARE_DIR/summary.tsv" \
    || ! grep -qx $'baselinePrimaryStage\tdecode' "$COMPARE_DIR/summary.tsv" \
    || ! grep -qx $'currentPrimaryStage\tdecode' "$COMPARE_DIR/summary.tsv" \
    || ! grep -qx $'primaryStageChanged\tfalse' "$COMPARE_DIR/summary.tsv" \
    || ! grep -qx $'largestSlowdownStage\tffn' "$COMPARE_DIR/summary.tsv" \
    || ! grep -qx $'largestSlowdownPercent\t25.000' "$COMPARE_DIR/summary.tsv" \
    || ! grep -Fqx "artifacts.comparison=$COMPARE_DIR/comparison.tsv" "${TMP_DIR}/compare.out"; then
  echo "Expected safetensor diagnosis comparison summary" >&2
  cat "$COMPARE_DIR/summary.tsv" >&2
  cat "${TMP_DIR}/compare.out" >&2
  exit 1
fi

GATE_DIR="${TMP_DIR}/gate"
if bash "$ROOT_DIR/scripts/compare-safetensor-profile-diagnosis.sh" \
    --baseline-stages "$BASELINE" \
    --current-stages "$CURRENT" \
    --summary-dir "$GATE_DIR" \
    --max-regression-percent 20 > "${TMP_DIR}/gate.out" 2> "${TMP_DIR}/gate.err"; then
  echo "Expected safetensor regression gate failure" >&2
  cat "${TMP_DIR}/gate.out" >&2
  exit 1
fi

if ! grep -qx $'status\tfail' "$GATE_DIR/summary.tsv" \
    || ! grep -qx $'reason\tstage-regression' "$GATE_DIR/summary.tsv" \
    || ! grep -qx $'gatedStages\t3' "$GATE_DIR/summary.tsv" \
    || ! grep -qx $'failedStages\t1' "$GATE_DIR/summary.tsv" \
    || ! grep -qx $'ffn\tffnMs\t60.000\t75.000\t15.000\t25.000\tsecondary\tsecondary\tslower\tfail\tdeltaPercent=25.000 exceeded 20' "$GATE_DIR/comparison.tsv" \
    || ! grep -Fqx 'status=fail' "${TMP_DIR}/gate.out"; then
  echo "Expected safetensor percent regression details" >&2
  cat "$GATE_DIR/summary.tsv" >&2
  cat "$GATE_DIR/comparison.tsv" >&2
  cat "${TMP_DIR}/gate.out" >&2
  exit 1
fi

MS_GATE_DIR="${TMP_DIR}/ms-gate"
if bash "$ROOT_DIR/scripts/compare-safetensor-profile-diagnosis.sh" \
    --baseline-stages "$BASELINE" \
    --current-stages "$CURRENT" \
    --summary-dir "$MS_GATE_DIR" \
    --max-regression-ms 10 > "${TMP_DIR}/ms-gate.out" 2> "${TMP_DIR}/ms-gate.err"; then
  echo "Expected safetensor millisecond regression gate failure" >&2
  cat "${TMP_DIR}/ms-gate.out" >&2
  exit 1
fi
if ! grep -qx $'ffn\tffnMs\t60.000\t75.000\t15.000\t25.000\tsecondary\tsecondary\tslower\tfail\tdeltaMs=15.000 exceeded 10' "$MS_GATE_DIR/comparison.tsv"; then
  echo "Expected safetensor millisecond regression details" >&2
  cat "$MS_GATE_DIR/comparison.tsv" >&2
  exit 1
fi

SHIFT_CURRENT="${TMP_DIR}/shift-current-stages.tsv"
{
  printf 'stage\tmetric\tvalueMs\tshareOfDurationPercent\tpriority\tpathEvidence\trecommendation\n'
  printf 'ffn\tffnMs\t90.000\t9.000\tprimary\tmetal_geglu=2\tFocus FFN.\n'
  printf 'decode\tdecodeMs\t70.000\t7.000\tsecondary\tmetal_decode=2\tFocus decode.\n'
} > "$SHIFT_CURRENT"

SHIFT_DIR="${TMP_DIR}/primary-shift"
if bash "$ROOT_DIR/scripts/compare-safetensor-profile-diagnosis.sh" \
    --baseline-stages "$BASELINE" \
    --current-stages "$SHIFT_CURRENT" \
    --summary-dir "$SHIFT_DIR" \
    --fail-primary-shift > "${TMP_DIR}/shift.out" 2> "${TMP_DIR}/shift.err"; then
  echo "Expected primary shift gate failure" >&2
  cat "${TMP_DIR}/shift.out" >&2
  exit 1
fi
if ! grep -qx $'reason\tprimary-stage-shift' "$SHIFT_DIR/summary.tsv" \
    || ! grep -qx $'primaryStageChanged\ttrue' "$SHIFT_DIR/summary.tsv"; then
  echo "Expected primary shift summary" >&2
  cat "$SHIFT_DIR/summary.tsv" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/compare-safetensor-profile-diagnosis.sh" \
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

printf 'safetensor diagnosis comparison test passed\n'
