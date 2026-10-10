#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-safetensor-profile-diagnosis.XXXXXX")"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

write_gate_header() {
  printf 'case\tdurationMs\tbackend\tprofileMetal\tstatus\ttopStage\ttopStageMs\tprefillMs\tdecodeMs\ttpotMs\tattentionMs\tffnMs\tlogitsMs\tlinearPaths\tlogitsPaths\tffnPaths\tattentionPaths\tlog\n'
}

GATE_SUMMARY="${TMP_DIR}/gate.tsv"
{
  write_gate_header
  printf 'metal-deterministic\t1000\tmetal\ttrue\tpassed\tdecode\t80.00\t12.00\t80.00\t40.00\t20.00\t70.00\t5.00\tattn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2\tmetal_logits=2\tmetal_geglu=2\tpaged_metal=2\t/tmp/metal-det.log\n'
  printf 'metal-normal\t1200\tmetal\ttrue\tpassed\tffn\t100.00\t20.00\t60.00\t30.00\t25.00\t100.00\t10.00\tattn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2\tmetal_logits=2\tmetal_geglu=2\tpaged_metal=2\t/tmp/metal-normal.log\n'
  printf 'cpu-deterministic\t3000\tcpu\tfalse\tpassed\tdecode\t200.00\t50.00\t200.00\t100.00\t90.00\t120.00\t15.00\tcpu_linear=2\tcpu_logits=2\tcpu_geglu=2\tcpu_attention=2\t/tmp/cpu.log\n'
  printf 'failed-metal\t900\tmetal\ttrue\tfailed\tattention\t300.00\t10.00\t20.00\t10.00\t300.00\t20.00\t5.00\tattn_q_proj:matvec=1\tmetal_logits=1\tmetal_geglu=1\tpaged_metal=1\t/tmp/failed.log\n'
} > "$GATE_SUMMARY"

GATE_DIR="${TMP_DIR}/gate-diagnosis"
bash "$ROOT_DIR/scripts/diagnose-safetensor-profile-summary.sh" \
  --summary "$GATE_SUMMARY" \
  --summary-dir "$GATE_DIR" > "${TMP_DIR}/gate.out"

if ! grep -qx $'status\tpass' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'selectedCase\tmetal-mean' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'selectedRows\t2' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'backend\tmetal' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'metal\ttrue' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'durationMs\t1100.000' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryStage\tffn' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryMetric\tffnMs' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryValueMs\t85.000' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryShareOfDurationPercent\t7.727' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'linearPathMetal\ttrue' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'logitsPathMetal\ttrue' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'ffnPathMetal\ttrue' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'attentionPathMetal\ttrue' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'metalPathStatus\tpass' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryPathMetal\ttrue' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'linearPathFallback\tfalse' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'logitsPathFallback\tfalse' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'ffnPathFallback\tfalse' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'attentionPathFallback\tfalse' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'pathFallbackStatus\tpass' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryPathFallback\tfalse' "$GATE_DIR/diagnosis.tsv" \
    || ! grep -qx $'ffn\tffnMs\t85.000\t7.727\tprimary\tmetal_geglu=2\tFocus fused gated FFN/GEGLU, matvec policy, BF16/F16 conversion, and weight-buffer reuse.' "$GATE_DIR/stages.tsv" \
    || ! grep -qx $'case\tpathGroup\tstageScope\tmetalStatus\tfallbackStatus\tstatus\tprimary\tpathEvidence\tnextAction' "$GATE_DIR/paths.tsv" \
    || ! grep -qx $'metal-mean\tlinear\tlinear\ttrue\tfalse\tpass\tfalse\tattn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2\tLinear path is Metal-clean; tune projection latency.' "$GATE_DIR/paths.tsv" \
    || ! grep -qx $'metal-mean\tlogits\tlogits\ttrue\tfalse\tpass\tfalse\tmetal_logits=2\tLogits path is Metal-clean; tune final projection/vocabulary latency.' "$GATE_DIR/paths.tsv" \
    || ! grep -qx $'metal-mean\tffn\tffn\ttrue\tfalse\tpass\ttrue\tmetal_geglu=2\tFFN path is Metal-clean; tune fused GEGLU/matvec latency.' "$GATE_DIR/paths.tsv" \
    || ! grep -qx $'metal-mean\tattention\tattention\ttrue\tfalse\tpass\tfalse\tpaged_metal=2\tAttention path is Metal-clean; tune latency only if it is a top stage.' "$GATE_DIR/paths.tsv" \
    || ! grep -qx 'metalPathStatus=pass' "${TMP_DIR}/gate.out" \
    || ! grep -qx 'primaryPathMetal=true' "${TMP_DIR}/gate.out" \
    || ! grep -qx 'pathFallbackStatus=pass' "${TMP_DIR}/gate.out" \
    || ! grep -qx 'primaryPathFallback=false' "${TMP_DIR}/gate.out" \
    || ! grep -qx "artifacts.paths=$GATE_DIR/paths.tsv" "${TMP_DIR}/gate.out" \
    || ! grep -qx "artifacts.diagnosis=$GATE_DIR/diagnosis.tsv" "${TMP_DIR}/gate.out"; then
  echo "Expected safetensor gate diagnosis to prefer passing Metal rows and identify FFN" >&2
  cat "$GATE_DIR/diagnosis.tsv" >&2
  cat "$GATE_DIR/stages.tsv" >&2
  cat "$GATE_DIR/paths.tsv" >&2
  cat "${TMP_DIR}/gate.out" >&2
  exit 1
fi

STRATEGY_SUMMARY="${TMP_DIR}/strategy.tsv"
{
  printf 'case\tdurationMs\tbackend\tprofileMetal\tstatus\ttopStage\ttopStageMs\tprefillMs\tdecodeMs\ttpotMs\tsamplingMs\targmaxMs\tattentionMs\tffnMs\tlogitsMs\tlinearPaths\tlogitsPaths\tffnPaths\tattentionPaths\targmaxPaths\tffnStrategy\tffnRowPrefillNativeRows\tffnRowPrefillVariant\tlog\n'
  printf 'strategy-row\t1230\tmetal\ttrue\tpassed\tffn\t18.00\t4.00\t8.00\t4.00\t1.00\t0.50\t6.00\t18.00\t3.00\tattn_q_proj:metal_matmul_f16=2\tmetal_logits=2\tmatvec-gated-ffn-prefill-rows:accept:geglu:native_bf16=true:native_rows=12:variant=x4=2\tpaged_metal=2\tnative_argmax_f32=2\trow_prefill_matvec_active\t12\tx4\t/tmp/strategy.log\n'
} > "$STRATEGY_SUMMARY"

STRATEGY_DIR="${TMP_DIR}/strategy-diagnosis"
bash "$ROOT_DIR/scripts/diagnose-safetensor-profile-summary.sh" \
  --summary "$STRATEGY_SUMMARY" \
  --summary-dir "$STRATEGY_DIR" \
  --case strategy-row > "${TMP_DIR}/strategy.out"

if ! grep -qx $'selectedCase\tstrategy-row' "$STRATEGY_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryStage\tffn' "$STRATEGY_DIR/diagnosis.tsv" \
    || ! grep -qx $'ffnPathMetal\ttrue' "$STRATEGY_DIR/diagnosis.tsv" \
    || ! grep -qx $'ffnPathFallback\tfalse' "$STRATEGY_DIR/diagnosis.tsv" \
    || ! grep -qx $'ffnStrategy\trow_prefill_matvec_active' "$STRATEGY_DIR/diagnosis.tsv" \
    || ! grep -qx $'ffnRowPrefillNativeRows\t12' "$STRATEGY_DIR/diagnosis.tsv" \
    || ! grep -qx $'ffnRowPrefillVariant\tx4' "$STRATEGY_DIR/diagnosis.tsv" \
    || ! grep -qx $'ffnStrategyAction\tRow-prefill matvec FFN is active; compare TTFT against fused GEGLU default and disable it if prefill regresses.' "$STRATEGY_DIR/diagnosis.tsv" \
    || ! grep -qx 'ffnStrategy=row_prefill_matvec_active' "${TMP_DIR}/strategy.out" \
    || ! grep -qx 'ffnRowPrefillNativeRows=12' "${TMP_DIR}/strategy.out" \
    || ! grep -qx 'ffnRowPrefillVariant=x4' "${TMP_DIR}/strategy.out"; then
  echo "Expected safetensor diagnosis to preserve Gemma4 FFN strategy details" >&2
  cat "$STRATEGY_DIR/diagnosis.tsv" >&2
  cat "$STRATEGY_DIR/stages.tsv" >&2
  cat "$STRATEGY_DIR/paths.tsv" >&2
  cat "${TMP_DIR}/strategy.out" >&2
  exit 1
fi

CASE_DIR="${TMP_DIR}/case-diagnosis"
bash "$ROOT_DIR/scripts/diagnose-safetensor-profile-summary.sh" \
  --summary "$GATE_SUMMARY" \
  --summary-dir "$CASE_DIR" \
  --case metal-deterministic >/dev/null

if ! grep -qx $'selectedCase\tmetal-deterministic' "$CASE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryStage\tdecode' "$CASE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryMetric\tdecodeMs' "$CASE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryValueMs\t80.000' "$CASE_DIR/diagnosis.tsv" \
    || ! grep -qx $'metalPathStatus\tpass' "$CASE_DIR/diagnosis.tsv" \
    || ! grep -qx $'pathFallbackStatus\tpass' "$CASE_DIR/diagnosis.tsv" \
    || ! grep -qx $'decode\tdecodeMs\t80.000\t8.000\tprimary\tattn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2\tFocus decode token loop, KV cache reuse, Metal dispatch overhead, and per-token allocations.' "$CASE_DIR/stages.tsv"; then
  echo "Expected explicit safetensor case diagnosis" >&2
  cat "$CASE_DIR/diagnosis.tsv" >&2
  cat "$CASE_DIR/stages.tsv" >&2
  exit 1
fi

FAILED_DIR="${TMP_DIR}/failed-inclusive"
bash "$ROOT_DIR/scripts/diagnose-safetensor-profile-summary.sh" \
  --summary "$GATE_SUMMARY" \
  --summary-dir "$FAILED_DIR" \
  --include-failed >/dev/null

if ! grep -qx $'selectedRows\t3' "$FAILED_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryStage\tattention' "$FAILED_DIR/diagnosis.tsv" \
    || ! grep -qx $'linearPathMetal\tmixed' "$FAILED_DIR/diagnosis.tsv" \
    || ! grep -qx $'metalPathStatus\tmixed' "$FAILED_DIR/diagnosis.tsv" \
    || ! grep -qx $'pathFallbackStatus\tmixed' "$FAILED_DIR/diagnosis.tsv" \
    || ! grep -qx $'nextAction\tStabilize mixed fallback evidence first: make repeated runs choose the same accelerated path before comparing latency.' "$FAILED_DIR/diagnosis.tsv"; then
  echo "Expected include-failed diagnosis to include failed Metal evidence" >&2
  cat "$FAILED_DIR/diagnosis.tsv" >&2
  cat "$FAILED_DIR/stages.tsv" >&2
  exit 1
fi

PROFILE_SUMMARY="${TMP_DIR}/profile.tsv"
{
  printf 'case_id\tstatus\tbackend\tmetal\ttopStage\ttopStageMs\tprefillMs\tdecodeMs\ttpotMs\tattentionMs\tffnMs\tlogitsMs\tlinearPaths\tlogitsPaths\tffnPaths\tattentionPaths\tlogFile\n'
  printf 'profile-case\tpassed\tcpu\tfalse\tlogits\t33.00\t2.00\t3.00\t1.00\t4.00\t5.00\t33.00\tcpu_linear=1\tcpu_logits=1\tcpu_ffn=1\tcpu_attention=1\t/tmp/profile.log\n'
} > "$PROFILE_SUMMARY"

PROFILE_DIR="${TMP_DIR}/profile-diagnosis"
bash "$ROOT_DIR/scripts/diagnose-safetensor-profile-summary.sh" \
  --summary "$PROFILE_SUMMARY" \
  --summary-dir "$PROFILE_DIR" >/dev/null

if ! grep -qx $'selectedCase\tmean' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'backend\tcpu' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'metal\tfalse' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryStage\tlogits' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryMetric\tlogitsMs' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryValueMs\t33.000' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'linearPathMetal\tfalse' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'logitsPathMetal\tfalse' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'ffnPathMetal\tfalse' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'attentionPathMetal\tfalse' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'metalPathStatus\tfail' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryPathMetal\tfalse' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'linearPathFallback\ttrue' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'logitsPathFallback\ttrue' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'ffnPathFallback\ttrue' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'attentionPathFallback\ttrue' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'pathFallbackStatus\tfail' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryPathFallback\ttrue' "$PROFILE_DIR/diagnosis.tsv" \
    || ! grep -qx $'mean\tlinear\tlinear\tfalse\ttrue\tfallback\tfalse\tcpu_linear=1\tRemove linear fallback markers before latency tuning.' "$PROFILE_DIR/paths.tsv" \
    || ! grep -qx $'mean\tlogits\tlogits\tfalse\ttrue\tfallback\ttrue\tcpu_logits=1\tRemove logits fallback markers before latency tuning.' "$PROFILE_DIR/paths.tsv" \
    || ! grep -qx $'mean\tffn\tffn\tfalse\ttrue\tfallback\tfalse\tcpu_ffn=1\tRemove FFN fallback markers before latency tuning.' "$PROFILE_DIR/paths.tsv" \
    || ! grep -qx $'mean\tattention\tattention\tfalse\ttrue\tfallback\tfalse\tcpu_attention=1\tRemove attention fallback markers before latency tuning.' "$PROFILE_DIR/paths.tsv" \
    || ! grep -qx $'nextAction\tFix logits fallback evidence first: profile path includes CPU/Java/Accelerate/skip evidence before tuning timings.' "$PROFILE_DIR/diagnosis.tsv"; then
  echo "Expected safetensor profile.tsv diagnosis" >&2
  cat "$PROFILE_DIR/diagnosis.tsv" >&2
  cat "$PROFILE_DIR/stages.tsv" >&2
  cat "$PROFILE_DIR/paths.tsv" >&2
  exit 1
fi

PARTIAL_SUMMARY="${TMP_DIR}/partial-fallback.tsv"
{
  write_gate_header
  printf 'partial-metal\t900\tmetal\ttrue\tpassed\tffn\t75.00\t10.00\t30.00\t15.00\t12.00\t75.00\t8.00\tq_proj:metal_matmul_f16=1\tmetal_logits=1\tmetal_geglu=1;java_geglu=1\tpaged_metal=1\t/tmp/partial.log\n'
} > "$PARTIAL_SUMMARY"

PARTIAL_DIR="${TMP_DIR}/partial-diagnosis"
bash "$ROOT_DIR/scripts/diagnose-safetensor-profile-summary.sh" \
  --summary "$PARTIAL_SUMMARY" \
  --summary-dir "$PARTIAL_DIR" > "${TMP_DIR}/partial.out"

if ! grep -qx $'selectedCase\tmetal-mean' "$PARTIAL_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryStage\tffn' "$PARTIAL_DIR/diagnosis.tsv" \
    || ! grep -qx $'metalPathStatus\tpass' "$PARTIAL_DIR/diagnosis.tsv" \
    || ! grep -qx $'ffnPathFallback\ttrue' "$PARTIAL_DIR/diagnosis.tsv" \
    || ! grep -qx $'pathFallbackStatus\tfail' "$PARTIAL_DIR/diagnosis.tsv" \
    || ! grep -qx $'primaryPathFallback\ttrue' "$PARTIAL_DIR/diagnosis.tsv" \
    || ! grep -qx $'metal-mean\tffn\tffn\ttrue\ttrue\tfallback\ttrue\tmetal_geglu=1;java_geglu=1\tRemove FFN fallback markers before latency tuning.' "$PARTIAL_DIR/paths.tsv" \
    || ! grep -qx $'nextAction\tFix FFN fallback evidence first: profile path includes CPU/Java/Accelerate/skip evidence before tuning timings.' "$PARTIAL_DIR/diagnosis.tsv" \
    || ! grep -qx 'pathFallbackStatus=fail' "${TMP_DIR}/partial.out"; then
  echo "Expected partial Metal diagnosis to expose hidden FFN fallback evidence" >&2
  cat "$PARTIAL_DIR/diagnosis.tsv" >&2
  cat "$PARTIAL_DIR/stages.tsv" >&2
  cat "$PARTIAL_DIR/paths.tsv" >&2
  cat "${TMP_DIR}/partial.out" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/diagnose-safetensor-profile-summary.sh" \
    --summary "$GATE_SUMMARY" \
    --case missing >"${TMP_DIR}/missing.out" 2>"${TMP_DIR}/missing.err"; then
  echo "Expected missing safetensor case diagnosis failure" >&2
  cat "${TMP_DIR}/missing.out" >&2
  cat "${TMP_DIR}/missing.err" >&2
  exit 1
fi
if ! grep -qx 'Requested case not found: missing' "${TMP_DIR}/missing.err"; then
  echo "Expected missing case diagnostic" >&2
  cat "${TMP_DIR}/missing.err" >&2
  exit 1
fi

printf 'safetensor profile diagnosis test passed\n'
