#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-bench-safetensor-test.XXXXXX")"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

AQLI_BIN="$TMP_DIR/aqli"
cat > "$AQLI_BIN" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf 'Platform: Metal\n'
printf '✓ GPU acceleration enabled (Apple M4)\n'
printf 'Model: fake-safetensor\n'
printf 'Provider: safetensor\n'
printf 'AQLI_JAVA_OPTS=%s\n' "${AQLI_JAVA_OPTS:-}"
printf '%s\n' '--------------------------------------------------'
printf 'Jakarta is in Indonesia.\n'
printf '\n[PROFILE] backend=metal mode=direct load=1.00ms tokenize=2.00ms session=3.00ms ttft=15.00ms engine_ttft=10.00ms prefill=4.00ms decode=18.00ms tpot=9.00ms sampling=1.00ms argmax=0.50ms attention=6.00ms ffn=14.00ms logits=3.00ms logits_copy=1.00ms steps=2 linear={attn_q_proj=8.00, ffn_down=12.00} linear_paths={attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2} logits_paths={metal_matmul_f16=2} ffn_paths={matvec-gated-ffn-prefill-rows:skip:strategy_prefers_fused_geglu_prefill=2, fused-gated-ffn:accept:geglu:native_bf16=true=2} attention_paths={paged_metal=2} argmax_paths={native_argmax_f32=2} ffn_strategy=fused_geglu_prefill_over_row_prefill path_status={core=metal, linear=metal, logits=metal, ffn=metal, attention=metal, argmax=metal} path_coverage={core=12/12, linear=6/6, logits=2/2, ffn=2/2, attention=2/2, argmax=2/2}\n'
printf '[Stream updates: 2, Duration: 1,23s, Speed: 1,63 t/s]\n'
SH
chmod +x "$AQLI_BIN"

PRESETS_TSV="$TMP_DIR/presets.tsv"
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" --list-presets > "$PRESETS_TSV"
if ! grep -qx $'name\tdescription' "$PRESETS_TSV" \
    || ! grep -q $'^m4-smoke\tM4 Metal smoke gate with real Metal path proof' "$PRESETS_TSV" \
    || ! grep -q $'^m4-greedy-10\tM4 greedy 10-token Jakarta smoke with real Metal path proof' "$PRESETS_TSV" \
    || ! grep -q $'^m4-gemma4-12b-smoke\tM4 Gemma4 12B smoke gate with Metal path proof' "$PRESETS_TSV" \
    || ! grep -q $'^m4-gemma4-12b-row-prefill-ab\tM4 Gemma4 12B row-prefill A/B gate' "$PRESETS_TSV"; then
  echo "Expected direct safetensor benchmark preset list to include M4 and Gemma4 12B presets" >&2
  cat "$PRESETS_TSV" >&2
  exit 1
fi

GEMMA4_PRESET_DEFAULTS="$TMP_DIR/gemma4-12b-preset-defaults.tsv"
bash "$ROOT_DIR/scripts/safetensor-performance-presets.sh" defaults m4-gemma4-12b-smoke > "$GEMMA4_PRESET_DEFAULTS"
if ! grep -qx $'requireFfnStrategy\tfused_geglu_prefill_over_row_prefill' "$GEMMA4_PRESET_DEFAULTS" \
    || ! grep -qx $'maxTokens\t3' "$GEMMA4_PRESET_DEFAULTS" \
    || ! grep -qx $'ffnThresholdMs\t30000' "$GEMMA4_PRESET_DEFAULTS" \
    || ! grep -qx $'topStageThresholdMs\t60000' "$GEMMA4_PRESET_DEFAULTS"; then
  echo "Expected Gemma4 12B preset defaults to require fused strategy with relaxed 12B thresholds" >&2
  cat "$GEMMA4_PRESET_DEFAULTS" >&2
  exit 1
fi

ROW_PREFILL_PRESET_DEFAULTS="$TMP_DIR/gemma4-12b-row-prefill-preset-defaults.tsv"
bash "$ROOT_DIR/scripts/safetensor-performance-presets.sh" defaults m4-gemma4-12b-row-prefill-ab > "$ROW_PREFILL_PRESET_DEFAULTS"
if ! grep -qx $'requireFfnStrategy\trow_prefill_matvec_active' "$ROW_PREFILL_PRESET_DEFAULTS" \
    || ! grep -qx $'javaOpt\t-Daqli.safetensor.enable_metal_matvec_ffn_prefill_rows=true' "$ROW_PREFILL_PRESET_DEFAULTS" \
    || ! grep -qx $'javaOpt\t-Daqli.safetensor.prefer_metal_matvec_ffn_prefill_rows=true' "$ROW_PREFILL_PRESET_DEFAULTS" \
    || ! grep -qx $'maxTokens\t3' "$ROW_PREFILL_PRESET_DEFAULTS"; then
  echo "Expected Gemma4 12B row-prefill preset defaults to require row-prefill strategy and Java opts" >&2
  cat "$ROW_PREFILL_PRESET_DEFAULTS" >&2
  exit 1
fi

OUT_DIR="$TMP_DIR/out"
LABEL="fake-profile"
GATE_TSV="$TMP_DIR/fake-profile-gate.tsv"
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$LABEL" \
  --summary-file "$GATE_TSV" \
  --quick \
  --profile \
  --require-profile \
  --require-ffn-strategy fused_geglu_prefill_over_row_prefill \
  --require-metal \
  --require-metal-paths \
  --max-top-stage-ms 20 \
  --max-prefill-ms 5 \
  --max-decode-ms 20 \
  --max-tpot-ms 10 \
  --max-sampling-ms 2 \
  --max-argmax-ms 1 \
  --max-attention-ms 7 \
  --max-ffn-ms 15 \
  --max-logits-ms 4 \
  --max-tokens 2 > "$TMP_DIR/bench.out"

RUN_DIR="$OUT_DIR/$LABEL"
SUMMARY_JSON="$RUN_DIR/summary.json"
SUMMARY_CSV="$RUN_DIR/summary.csv"
PROFILE_TSV="$RUN_DIR/profile.tsv"
REPORT_TXT="$RUN_DIR/report.txt"
DIAGNOSIS_TSV="$RUN_DIR/diagnosis/diagnosis.tsv"
DIAGNOSIS_STAGES_TSV="$RUN_DIR/diagnosis/stages.tsv"
DIAGNOSIS_PATHS_TSV="$RUN_DIR/diagnosis/paths.tsv"
DIAGNOSIS_REPORT="$RUN_DIR/diagnosis/report.txt"

if [[ ! -f "$SUMMARY_JSON" || ! -f "$SUMMARY_CSV" || ! -f "$PROFILE_TSV" || ! -f "$GATE_TSV" || ! -f "$REPORT_TXT" \
    || ! -f "$DIAGNOSIS_TSV" || ! -f "$DIAGNOSIS_STAGES_TSV" || ! -f "$DIAGNOSIS_PATHS_TSV" || ! -f "$DIAGNOSIS_REPORT" ]]; then
  echo "Expected safetensor benchmark JSON, CSV, profile TSV, gate TSV, report, and diagnosis artifacts" >&2
  find "$RUN_DIR" -maxdepth 1 -type f -print >&2 || true
  find "$RUN_DIR/diagnosis" -maxdepth 1 -type f -print >&2 || true
  exit 1
fi

if ! jq -e '
  .runs[0].profile_backend == "metal"
  and .runs[0].profile_metal == "true"
  and .runs[0].speed_tps == 1.63
  and .runs[0].chunks == 2
  and .runs[0].answer_repeat_run == 1
  and .runs[0].profile_core_path_status == "metal"
  and .runs[0].profile_linear_path_status == "metal"
  and .runs[0].profile_logits_path_status == "metal"
  and .runs[0].profile_ffn_path_status == "metal"
  and .runs[0].profile_attention_path_status == "metal"
  and .runs[0].profile_argmax_path_status == "metal"
  and .runs[0].profile_core_path_coverage == "12/12"
  and .runs[0].profile_linear_path_coverage == "6/6"
  and .runs[0].profile_logits_path_coverage == "2/2"
  and .runs[0].profile_ffn_path_coverage == "2/2"
  and .runs[0].profile_attention_path_coverage == "2/2"
  and .runs[0].profile_argmax_path_coverage == "2/2"
  and .runs[0].profile_top_stage == "decode"
  and .runs[0].profile_top_stage_ms == 18
  and .runs[0].profile_decode_ms == 18
  and .runs[0].profile_tpot_ms == 9
  and .runs[0].profile_sampling_ms == 1
  and .runs[0].profile_argmax_ms == 0.5
  and .runs[0].profile_attention_ms == 6
  and .runs[0].profile_ffn_ms == 14
  and .runs[0].profile_logits_ms == 3
  and .runs[0].profile_linear_paths == "attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2"
  and .runs[0].profile_logits_paths == "metal_matmul_f16=2"
  and .runs[0].profile_ffn_paths == "matvec-gated-ffn-prefill-rows:skip:strategy_prefers_fused_geglu_prefill=2, fused-gated-ffn:accept:geglu:native_bf16=true=2"
  and .runs[0].profile_ffn_strategy == "fused_geglu_prefill_over_row_prefill"
  and .runs[0].profile_ffn_row_prefill_native_rows == null
  and .runs[0].profile_ffn_row_prefill_variant == null
  and .runs[0].profile_attention_paths == "paged_metal=2"
  and .runs[0].profile_argmax_paths == "native_argmax_f32=2"
' "$SUMMARY_JSON" >/dev/null; then
  echo "Expected normalized profile fields in summary.json" >&2
  cat "$SUMMARY_JSON" >&2
  exit 1
fi

if ! grep -qx $'case_id	status	backend	metal	corePathStatus	linearPathStatus	logitsPathStatus	ffnPathStatus	attentionPathStatus	argmaxPathStatus	corePathCoverage	linearPathCoverage	logitsPathCoverage	ffnPathCoverage	attentionPathCoverage	argmaxPathCoverage	topStage	topStageMs	prefillMs	decodeMs	tpotMs	samplingMs	argmaxMs	attentionMs	ffnMs	logitsMs	linearPaths	logitsPaths	ffnPaths	attentionPaths	argmaxPaths	ffnStrategy	ffnRowPrefillNativeRows	ffnRowPrefillVariant	logFile' "$PROFILE_TSV" \
    || ! grep -q $'^metal-deterministic	passed	metal	true	metal	metal	metal	metal	metal	metal	12/12	6/6	2/2	2/2	2/2	2/2	decode	18.00	4.00	18.00	9.00	1.00	0.50	6.00	14.00	3.00	attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2	metal_matmul_f16=2	matvec-gated-ffn-prefill-rows:skip:strategy_prefers_fused_geglu_prefill=2, fused-gated-ffn:accept:geglu:native_bf16=true=2	paged_metal=2	native_argmax_f32=2	fused_geglu_prefill_over_row_prefill	n/a	n/a	' "$PROFILE_TSV"; then
  echo "Expected profile.tsv to capture backend, Metal proof, slowest stage, timings, and paths" >&2
  cat "$PROFILE_TSV" >&2
  exit 1
fi

if ! grep -qx $'case	durationMs	speedTps	chunks	answerRepeatRun	backend	profileMetal	status	corePathStatus	linearPathStatus	logitsPathStatus	ffnPathStatus	attentionPathStatus	argmaxPathStatus	corePathCoverage	linearPathCoverage	logitsPathCoverage	ffnPathCoverage	attentionPathCoverage	argmaxPathCoverage	topStage	topStageMs	prefillMs	decodeMs	tpotMs	samplingMs	argmaxMs	attentionMs	ffnMs	logitsMs	linearPaths	logitsPaths	ffnPaths	attentionPaths	argmaxPaths	ffnStrategy	ffnRowPrefillNativeRows	ffnRowPrefillVariant	log' "$GATE_TSV" \
    || ! grep -q $'^metal-deterministic	1230	1.63	2	1	metal	true	passed	metal	metal	metal	metal	metal	metal	12/12	6/6	2/2	2/2	2/2	2/2	decode	18.00	4.00	18.00	9.00	1.00	0.50	6.00	14.00	3.00	attn_q_proj:metal_matmul_f16=2; ffn_down:metal_matmul_f16=2; logits:metal_matmul_f16=2	metal_matmul_f16=2	matvec-gated-ffn-prefill-rows:skip:strategy_prefers_fused_geglu_prefill=2; fused-gated-ffn:accept:geglu:native_bf16=true=2	paged_metal=2	native_argmax_f32=2	fused_geglu_prefill_over_row_prefill	n/a	n/a	' "$GATE_TSV"; then
  echo "Expected gate TSV to expose aggregate-friendly safetensor metrics" >&2
  cat "$GATE_TSV" >&2
  exit 1
fi

if ! head -n 1 "$SUMMARY_CSV" | grep -q 'profile_sampling_ms,profile_argmax_ms' \
    || ! head -n 1 "$SUMMARY_CSV" | grep -q 'profile_core_path_coverage,profile_linear_path_coverage' \
    || ! head -n 1 "$SUMMARY_CSV" | grep -q 'profile_attention_paths,profile_argmax_paths,profile_ffn_strategy' \
    || ! grep -q '"attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2"' "$SUMMARY_CSV" \
    || ! grep -q 'native_argmax_f32=2' "$SUMMARY_CSV" \
    || ! grep -q 'fused_geglu_prefill_over_row_prefill' "$SUMMARY_CSV" \
    || ! grep -q 'fused-gated-ffn:accept:geglu:native_bf16=true=2' "$SUMMARY_CSV"; then
  echo "Expected summary.csv to expose normalized profile columns" >&2
  cat "$SUMMARY_CSV" >&2
  exit 1
fi

if ! grep -q 'metal-deterministic.*metal.*true.*metal.*12/12.*decode.*18' "$REPORT_TXT" \
    || ! grep -q "  - $PROFILE_TSV" "$TMP_DIR/bench.out" \
    || ! grep -q "  - $GATE_TSV" "$TMP_DIR/bench.out" \
    || ! grep -q "  - $DIAGNOSIS_REPORT" "$TMP_DIR/bench.out" \
    || ! grep -q "  - $DIAGNOSIS_TSV" "$TMP_DIR/bench.out" \
    || ! grep -q "  - $DIAGNOSIS_STAGES_TSV" "$TMP_DIR/bench.out" \
    || ! grep -q "  - $DIAGNOSIS_PATHS_TSV" "$TMP_DIR/bench.out"; then
  echo "Expected report and command output to reference profile evidence" >&2
  cat "$REPORT_TXT" >&2
  cat "$TMP_DIR/bench.out" >&2
  exit 1
fi

JAVA_OPTS_LABEL="java-opts-merge"
AQLI_JAVA_OPTS="-Dbase.option=true" \
AQLI_BENCH_SAFETENSOR_JAVA_OPTS="-Denv.option=true" \
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$JAVA_OPTS_LABEL" \
  --quick \
  --profile \
  --java-opt -Dcli.option=true \
  --java-opt -Dgemma4.row_prefill_probe=true \
  --max-tokens 2 > "$TMP_DIR/bench-java-opts.out"

JAVA_OPTS_LOG="$OUT_DIR/$JAVA_OPTS_LABEL/logs/metal-deterministic.log"
EXPECTED_JAVA_OPTS='-Dbase.option=true -Denv.option=true -Dcli.option=true -Dgemma4.row_prefill_probe=true -Daqli.profile=true'
if ! grep -qx "aqli_java_opts=$EXPECTED_JAVA_OPTS" "$JAVA_OPTS_LOG" \
    || ! grep -qx "AQLI_JAVA_OPTS=$EXPECTED_JAVA_OPTS" "$JAVA_OPTS_LOG"; then
  echo "Expected benchmark Java opts to preserve caller/env opts and append profile flag" >&2
  cat "$JAVA_OPTS_LOG" >&2
  cat "$TMP_DIR/bench-java-opts.out" >&2
  exit 1
fi

ROW_PREFILL_BIN="$TMP_DIR/aqli-row-prefill"
cat > "$ROW_PREFILL_BIN" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf 'Platform: Metal\n'
printf '✓ GPU acceleration enabled (Apple M4)\n'
printf 'Model: fake-safetensor\n'
printf 'Provider: safetensor\n'
printf 'AQLI_JAVA_OPTS=%s\n' "${AQLI_JAVA_OPTS:-}"
printf '%s\n' '--------------------------------------------------'
printf 'Jakarta is in Indonesia.\n'
printf '\n[PROFILE] backend=metal mode=direct prefill=4.00ms decode=18.00ms tpot=9.00ms sampling=1.00ms argmax=0.50ms attention=6.00ms ffn=14.00ms logits=3.00ms steps=2 linear_paths={attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2} logits_paths={metal_matmul_f16=2} ffn_paths={matvec-gated-ffn-prefill-rows:accept:geglu:native_bf16=true:native_rows=12:variant=x4=2} attention_paths={paged_metal=2} argmax_paths={native_argmax_f32=2} ffn_strategy=row_prefill_matvec_active path_status={core=metal, linear=metal, logits=metal, ffn=metal, attention=metal, argmax=metal} path_coverage={core=12/12, linear=6/6, logits=2/2, ffn=2/2, attention=2/2, argmax=2/2}\n'
printf '[Stream updates: 2, Duration: 1,23s, Speed: 1,63 t/s]\n'
SH
chmod +x "$ROW_PREFILL_BIN"

ROW_PREFILL_LABEL="row-prefill-profile"
ROW_PREFILL_GATE_TSV="$TMP_DIR/row-prefill-gate.tsv"
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$ROW_PREFILL_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$ROW_PREFILL_LABEL" \
  --summary-file "$ROW_PREFILL_GATE_TSV" \
  --quick \
  --profile \
  --require-profile \
  --require-ffn-strategy row_prefill_matvec_active \
  --require-metal \
  --require-metal-paths \
  --reject-fallback-paths \
  --max-tokens 2 > "$TMP_DIR/bench-row-prefill.out"

ROW_PREFILL_JSON="$OUT_DIR/$ROW_PREFILL_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "passed"
  and .runs[0].profile_ffn_path_status == "metal"
  and .runs[0].profile_ffn_strategy == "row_prefill_matvec_active"
  and .runs[0].profile_ffn_row_prefill_native_rows == 12
  and .runs[0].profile_ffn_row_prefill_variant == "x4"
  and (.runs[0].profile_ffn_paths | contains("matvec-gated-ffn-prefill-rows:accept"))
' "$ROW_PREFILL_JSON" >/dev/null \
    || ! grep -q $'^metal-deterministic	.*	row_prefill_matvec_active	12	x4	' "$ROW_PREFILL_GATE_TSV"; then
  echo "Expected row-prefill FFN strategy details to be extracted and accepted as native Metal evidence" >&2
  cat "$ROW_PREFILL_JSON" >&2
  cat "$ROW_PREFILL_GATE_TSV" >&2
  cat "$TMP_DIR/bench-row-prefill.out" >&2
  exit 1
fi

ROW_PREFILL_PRESET_LABEL="preset-gemma4-12b-row-prefill-ab"
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$ROW_PREFILL_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$ROW_PREFILL_PRESET_LABEL" \
  --quick \
  --preset m4-gemma4-12b-row-prefill-ab \
  --max-tokens 2 > "$TMP_DIR/bench-preset-row-prefill.out"

ROW_PREFILL_PRESET_JSON="$OUT_DIR/$ROW_PREFILL_PRESET_LABEL/summary.json"
ROW_PREFILL_PRESET_LOG="$OUT_DIR/$ROW_PREFILL_PRESET_LABEL/logs/metal-deterministic.log"
ROW_PREFILL_PRESET_JAVA_OPTS='-Daqli.safetensor.enable_metal_matvec_ffn_prefill_rows=true -Daqli.safetensor.prefer_metal_matvec_ffn_prefill_rows=true -Daqli.profile=true'
if ! jq -e '
  .runs[0].status == "passed"
  and .runs[0].profile_ffn_path_status == "metal"
  and .runs[0].profile_ffn_strategy == "row_prefill_matvec_active"
  and .runs[0].profile_ffn_row_prefill_native_rows == 12
  and .runs[0].profile_ffn_row_prefill_variant == "x4"
' "$ROW_PREFILL_PRESET_JSON" >/dev/null \
    || ! grep -qx "aqli_java_opts=$ROW_PREFILL_PRESET_JAVA_OPTS" "$ROW_PREFILL_PRESET_LOG" \
    || ! grep -qx "AQLI_JAVA_OPTS=$ROW_PREFILL_PRESET_JAVA_OPTS" "$ROW_PREFILL_PRESET_LOG"; then
  echo "Expected row-prefill preset to inject Java opts and require row-prefill strategy" >&2
  cat "$ROW_PREFILL_PRESET_JSON" >&2
  cat "$ROW_PREFILL_PRESET_LOG" >&2
  cat "$TMP_DIR/bench-preset-row-prefill.out" >&2
  exit 1
fi

FFN_STRATEGY_FAIL_LABEL="ffn-strategy-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$FFN_STRATEGY_FAIL_LABEL" \
  --quick \
  --profile \
  --require-profile \
  --require-ffn-strategy row_prefill_matvec_active \
  --max-tokens 2 > "$TMP_DIR/bench-ffn-strategy-fail.out" 2>&1
ffn_strategy_fail_exit=$?
set -e

if [[ "$ffn_strategy_fail_exit" -eq 0 ]]; then
  echo "Expected mismatched FFN strategy gate to fail" >&2
  cat "$TMP_DIR/bench-ffn-strategy-fail.out" >&2
  exit 1
fi

FFN_STRATEGY_FAIL_JSON="$OUT_DIR/$FFN_STRATEGY_FAIL_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and .runs[0].profile_ffn_strategy == "fused_geglu_prefill_over_row_prefill"
  and (.runs[0].fatal_line | contains("ffn_strategy=fused_geglu_prefill_over_row_prefill did not match required row_prefill_matvec_active"))
' "$FFN_STRATEGY_FAIL_JSON" >/dev/null; then
  echo "Expected mismatched FFN strategy failure to be recorded in summary.json" >&2
  cat "$FFN_STRATEGY_FAIL_JSON" >&2
  cat "$TMP_DIR/bench-ffn-strategy-fail.out" >&2
  exit 1
fi

GEMMA4_PRESET_LABEL="preset-gemma4-12b-smoke"
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$GEMMA4_PRESET_LABEL" \
  --quick \
  --preset m4-gemma4-12b-smoke \
  --max-tokens 2 > "$TMP_DIR/bench-preset-gemma4-12b.out"

GEMMA4_PRESET_JSON="$OUT_DIR/$GEMMA4_PRESET_LABEL/summary.json"
GEMMA4_PRESET_LOG="$OUT_DIR/$GEMMA4_PRESET_LABEL/logs/metal-deterministic.log"
if ! jq -e '
  .runs[0].status == "passed"
  and .runs[0].profile_backend == "metal"
  and .runs[0].profile_metal == "true"
  and .runs[0].profile_ffn_path_status == "metal"
  and .runs[0].profile_ffn_strategy == "fused_geglu_prefill_over_row_prefill"
' "$GEMMA4_PRESET_JSON" >/dev/null \
    || ! grep -q -- '--max-tokens 2' "$GEMMA4_PRESET_LOG"; then
  echo "Expected Gemma4 12B preset to require fused FFN strategy while preserving explicit max tokens" >&2
  cat "$GEMMA4_PRESET_JSON" >&2
  cat "$GEMMA4_PRESET_LOG" >&2
  exit 1
fi

PRESET_LABEL="preset-smoke"
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$PRESET_LABEL" \
  --quick \
  --preset m4-smoke \
  --max-tokens 2 > "$TMP_DIR/bench-preset.out"

PRESET_JSON="$OUT_DIR/$PRESET_LABEL/summary.json"
PRESET_LOG="$OUT_DIR/$PRESET_LABEL/logs/metal-deterministic.log"
if ! jq -e '
  .runs[0].status == "passed"
  and .runs[0].profile_backend == "metal"
  and .runs[0].profile_metal == "true"
  and .runs[0].profile_core_path_status == "metal"
  and .runs[0].profile_core_path_coverage == "12/12"
  and .runs[0].profile_argmax_path_status == "metal"
  and .runs[0].profile_argmax_path_coverage == "2/2"
  and .runs[0].profile_sampling_ms == 1
  and .runs[0].profile_argmax_ms == 0.5
  and .runs[0].profile_decode_ms == 18
' "$PRESET_JSON" >/dev/null \
    || ! grep -q -- '--max-tokens 2' "$PRESET_LOG"; then
  echo "Expected m4-smoke preset to pass real Metal path proof while preserving explicit max tokens" >&2
  cat "$PRESET_JSON" >&2
  cat "$PRESET_LOG" >&2
  exit 1
fi

GREEDY_PRESET_LABEL="preset-greedy-10"
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$GREEDY_PRESET_LABEL" \
  --quick \
  --preset m4-greedy-10 > "$TMP_DIR/bench-preset-greedy-10.out"

GREEDY_PRESET_JSON="$OUT_DIR/$GREEDY_PRESET_LABEL/summary.json"
GREEDY_PRESET_LOG="$OUT_DIR/$GREEDY_PRESET_LABEL/logs/metal-deterministic.log"
if ! jq -e '
  .runs[0].status == "passed"
  and .runs[0].profile_backend == "metal"
  and .runs[0].profile_metal == "true"
  and .runs[0].answer == "Jakarta is in Indonesia."
  and .runs[0].answer_repeat_run == 1
  and .runs[0].chunks == 2
  and .runs[0].profile_core_path_status == "metal"
  and .runs[0].profile_argmax_path_status == "metal"
' "$GREEDY_PRESET_JSON" >/dev/null \
    || ! grep -q -- '--max-tokens 10' "$GREEDY_PRESET_LOG"; then
  echo "Expected m4-greedy-10 preset to pass real Metal path proof and default to 10 greedy tokens" >&2
  cat "$GREEDY_PRESET_JSON" >&2
  cat "$GREEDY_PRESET_LOG" >&2
  exit 1
fi

TRUNCATED_STREAM_BIN="$TMP_DIR/aqli-truncated-stream"
cat > "$TRUNCATED_STREAM_BIN" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf 'Platform: Metal\n'
printf '✓ GPU acceleration enabled (Apple M4)\n'
printf 'Model: fake-safetensor\n'
printf 'Provider: safetensor\n'
printf '%s\n' '--------------------------------------------------'
printf 'Jakarta is in Indonesia.\n'
printf '\n[PROFILE] backend=metal mode=direct load=1.00ms tokenize=2.00ms session=3.00ms ttft=15.00ms engine_ttft=10.00ms prefill=4.00ms decode=18.00ms tpot=9.00ms sampling=1.00ms argmax=0.50ms attention=6.00ms ffn=14.00ms logits=3.00ms logits_copy=1.00ms steps=2 linear={attn_q_proj=8.00, ffn_down=12.00} linear_paths={attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2} logits_paths={metal_matmul_f16=2} ffn_paths={metal_geglu=2} attention_paths={paged_metal=2} argmax_paths={native_argmax_f32=2} path_status={core=metal, linear=metal, logits=metal, ffn=metal, attention=metal, argmax=metal} path_coverage={core=12/12, linear=6/6, logits=2/2, ffn=2/2, attention=2/2, argmax=2/2}\n'
printf '[Stream updates: 1, Duration: 1,23s, Speed: 1,63 t/s]\n'
SH
chmod +x "$TRUNCATED_STREAM_BIN"

TRUNCATED_STREAM_LABEL="preset-greedy-10-chunks-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$TRUNCATED_STREAM_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$TRUNCATED_STREAM_LABEL" \
  --quick \
  --preset m4-greedy-10 > "$TMP_DIR/bench-preset-greedy-10-chunks-fail.out" 2>&1
truncated_stream_exit=$?
set -e

if [[ "$truncated_stream_exit" -eq 0 ]]; then
  echo "Expected m4-greedy-10 preset to fail when stream update count is too low despite correct answer" >&2
  cat "$TMP_DIR/bench-preset-greedy-10-chunks-fail.out" >&2
  exit 1
fi

TRUNCATED_STREAM_JSON="$OUT_DIR/$TRUNCATED_STREAM_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and .runs[0].answer == "Jakarta is in Indonesia."
  and .runs[0].chunks == 1
  and .runs[0].answer_repeat_run == 1
  and .runs[0].profile_backend == "metal"
  and (.runs[0].fatal_line | contains("chunks=1 below 2"))
' "$TRUNCATED_STREAM_JSON" >/dev/null; then
  echo "Expected truncated stream to be rejected by min chunks gate" >&2
  cat "$TRUNCATED_STREAM_JSON" >&2
  exit 1
fi

MISSING_SPEED_BIN="$TMP_DIR/aqli-missing-speed"
cat > "$MISSING_SPEED_BIN" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf 'Platform: Metal\n'
printf '✓ GPU acceleration enabled (Apple M4)\n'
printf 'Model: fake-safetensor\n'
printf 'Provider: safetensor\n'
printf '%s\n' '--------------------------------------------------'
printf 'Jakarta is in Indonesia.\n'
printf '\n[PROFILE] backend=metal mode=direct load=1.00ms tokenize=2.00ms session=3.00ms ttft=15.00ms engine_ttft=10.00ms prefill=4.00ms decode=18.00ms tpot=9.00ms sampling=1.00ms argmax=0.50ms attention=6.00ms ffn=14.00ms logits=3.00ms logits_copy=1.00ms steps=2 linear={attn_q_proj=8.00, ffn_down=12.00} linear_paths={attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2} logits_paths={metal_matmul_f16=2} ffn_paths={metal_geglu=2} attention_paths={paged_metal=2} argmax_paths={native_argmax_f32=2} path_status={core=metal, linear=metal, logits=metal, ffn=metal, attention=metal, argmax=metal} path_coverage={core=12/12, linear=6/6, logits=2/2, ffn=2/2, attention=2/2, argmax=2/2}\n'
printf '[Stream updates: 2, Duration: 1,23s]\n'
SH
chmod +x "$MISSING_SPEED_BIN"

MISSING_SPEED_LABEL="preset-greedy-10-speed-missing-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$MISSING_SPEED_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$MISSING_SPEED_LABEL" \
  --quick \
  --preset m4-greedy-10 > "$TMP_DIR/bench-preset-greedy-10-speed-missing-fail.out" 2>&1
missing_speed_exit=$?
set -e

if [[ "$missing_speed_exit" -eq 0 ]]; then
  echo "Expected m4-greedy-10 preset to fail when required speed metric is missing" >&2
  cat "$TMP_DIR/bench-preset-greedy-10-speed-missing-fail.out" >&2
  exit 1
fi

MISSING_SPEED_JSON="$OUT_DIR/$MISSING_SPEED_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and .runs[0].speed_tps == null
  and .runs[0].chunks == 2
  and .runs[0].answer == "Jakarta is in Indonesia."
  and (.runs[0].fatal_line | contains("speed was required but not measured"))
' "$MISSING_SPEED_JSON" >/dev/null; then
  echo "Expected missing speed metric to be rejected by min speed gate" >&2
  cat "$MISSING_SPEED_JSON" >&2
  exit 1
fi

MISSING_CHUNKS_BIN="$TMP_DIR/aqli-missing-chunks"
cat > "$MISSING_CHUNKS_BIN" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf 'Platform: Metal\n'
printf '✓ GPU acceleration enabled (Apple M4)\n'
printf 'Model: fake-safetensor\n'
printf 'Provider: safetensor\n'
printf '%s\n' '--------------------------------------------------'
printf 'Jakarta is in Indonesia.\n'
printf '\n[PROFILE] backend=metal mode=direct load=1.00ms tokenize=2.00ms session=3.00ms ttft=15.00ms engine_ttft=10.00ms prefill=4.00ms decode=18.00ms tpot=9.00ms sampling=1.00ms argmax=0.50ms attention=6.00ms ffn=14.00ms logits=3.00ms logits_copy=1.00ms steps=2 linear={attn_q_proj=8.00, ffn_down=12.00} linear_paths={attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2} logits_paths={metal_matmul_f16=2} ffn_paths={metal_geglu=2} attention_paths={paged_metal=2} argmax_paths={native_argmax_f32=2} path_status={core=metal, linear=metal, logits=metal, ffn=metal, attention=metal, argmax=metal} path_coverage={core=12/12, linear=6/6, logits=2/2, ffn=2/2, attention=2/2, argmax=2/2}\n'
printf '[Duration: 1,23s, Speed: 1,63 t/s]\n'
SH
chmod +x "$MISSING_CHUNKS_BIN"

MISSING_CHUNKS_LABEL="preset-greedy-10-chunks-missing-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$MISSING_CHUNKS_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$MISSING_CHUNKS_LABEL" \
  --quick \
  --preset m4-greedy-10 > "$TMP_DIR/bench-preset-greedy-10-chunks-missing-fail.out" 2>&1
missing_chunks_exit=$?
set -e

if [[ "$missing_chunks_exit" -eq 0 ]]; then
  echo "Expected m4-greedy-10 preset to fail when required chunks metric is missing" >&2
  cat "$TMP_DIR/bench-preset-greedy-10-chunks-missing-fail.out" >&2
  exit 1
fi

MISSING_CHUNKS_JSON="$OUT_DIR/$MISSING_CHUNKS_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and .runs[0].speed_tps == 1.63
  and .runs[0].chunks == null
  and .runs[0].answer == "Jakarta is in Indonesia."
  and (.runs[0].fatal_line | contains("chunks were required but not measured"))
' "$MISSING_CHUNKS_JSON" >/dev/null; then
  echo "Expected missing chunks metric to be rejected by min chunks gate" >&2
  cat "$MISSING_CHUNKS_JSON" >&2
  exit 1
fi

BAD_ANSWER_BIN="$TMP_DIR/aqli-bad-answer"
cat > "$BAD_ANSWER_BIN" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf 'Platform: Metal\n'
printf '✓ GPU acceleration enabled (Apple M4)\n'
printf 'Model: fake-safetensor\n'
printf 'Provider: safetensor\n'
printf '%s\n' '--------------------------------------------------'
printf 'model is jakarta\n'
printf '\n[PROFILE] backend=metal mode=direct load=1.00ms tokenize=2.00ms session=3.00ms ttft=15.00ms engine_ttft=10.00ms prefill=4.00ms decode=18.00ms tpot=9.00ms sampling=1.00ms argmax=0.50ms attention=6.00ms ffn=14.00ms logits=3.00ms logits_copy=1.00ms steps=2 linear={attn_q_proj=8.00, ffn_down=12.00} linear_paths={attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2} logits_paths={metal_matmul_f16=2} ffn_paths={metal_geglu=2} attention_paths={paged_metal=2} argmax_paths={native_argmax_f32=2} path_status={core=metal, linear=metal, logits=metal, ffn=metal, attention=metal, argmax=metal} path_coverage={core=12/12, linear=6/6, logits=2/2, ffn=2/2, attention=2/2, argmax=2/2}\n'
printf '[Stream updates: 2, Duration: 1,23s, Speed: 1,63 t/s]\n'
SH
chmod +x "$BAD_ANSWER_BIN"

BAD_ANSWER_LABEL="preset-greedy-10-answer-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$BAD_ANSWER_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$BAD_ANSWER_LABEL" \
  --quick \
  --preset m4-greedy-10 > "$TMP_DIR/bench-preset-greedy-10-answer-fail.out" 2>&1
bad_answer_exit=$?
set -e

if [[ "$bad_answer_exit" -eq 0 ]]; then
  echo "Expected m4-greedy-10 preset to fail when answer omits Indonesia despite clean Metal proof" >&2
  cat "$TMP_DIR/bench-preset-greedy-10-answer-fail.out" >&2
  exit 1
fi

BAD_ANSWER_JSON="$OUT_DIR/$BAD_ANSWER_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and .runs[0].answer == "model is jakarta"
  and .runs[0].answer_repeat_run == 1
  and .runs[0].profile_backend == "metal"
  and .runs[0].profile_core_path_status == "metal"
  and (.runs[0].fatal_line | contains("answer did not match required regex"))
  and (.runs[0].fatal_line | contains("jakarta.*indonesia|indonesia.*jakarta"))
' "$BAD_ANSWER_JSON" >/dev/null; then
  echo "Expected bad Jakarta answer to be rejected by required answer regex" >&2
  cat "$BAD_ANSWER_JSON" >&2
  exit 1
fi

REPEATED_ANSWER_BIN="$TMP_DIR/aqli-repeated-answer"
cat > "$REPEATED_ANSWER_BIN" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf 'Platform: Metal\n'
printf '✓ GPU acceleration enabled (Apple M4)\n'
printf 'Model: fake-safetensor\n'
printf 'Provider: safetensor\n'
printf '%s\n' '--------------------------------------------------'
printf 'Jakarta Indonesia of of of\n'
printf '\n[PROFILE] backend=metal mode=direct load=1.00ms tokenize=2.00ms session=3.00ms ttft=15.00ms engine_ttft=10.00ms prefill=4.00ms decode=18.00ms tpot=9.00ms sampling=1.00ms argmax=0.50ms attention=6.00ms ffn=14.00ms logits=3.00ms logits_copy=1.00ms steps=2 linear={attn_q_proj=8.00, ffn_down=12.00} linear_paths={attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2} logits_paths={metal_matmul_f16=2} ffn_paths={metal_geglu=2} attention_paths={paged_metal=2} argmax_paths={native_argmax_f32=2} path_status={core=metal, linear=metal, logits=metal, ffn=metal, attention=metal, argmax=metal} path_coverage={core=12/12, linear=6/6, logits=2/2, ffn=2/2, attention=2/2, argmax=2/2}\n'
printf '[Stream updates: 5, Duration: 1,23s, Speed: 1,63 t/s]\n'
SH
chmod +x "$REPEATED_ANSWER_BIN"

REPEATED_ANSWER_LABEL="preset-greedy-10-repeat-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$REPEATED_ANSWER_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$REPEATED_ANSWER_LABEL" \
  --quick \
  --preset m4-greedy-10 > "$TMP_DIR/bench-preset-greedy-10-repeat-fail.out" 2>&1
repeated_answer_exit=$?
set -e

if [[ "$repeated_answer_exit" -eq 0 ]]; then
  echo "Expected m4-greedy-10 preset to fail when answer contains repeated-token loop despite clean Metal proof" >&2
  cat "$TMP_DIR/bench-preset-greedy-10-repeat-fail.out" >&2
  exit 1
fi

REPEATED_ANSWER_JSON="$OUT_DIR/$REPEATED_ANSWER_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and .runs[0].answer == "Jakarta Indonesia of of of"
  and .runs[0].answer_repeat_run == 3
  and .runs[0].chunks == 5
  and .runs[0].profile_backend == "metal"
  and .runs[0].profile_core_path_status == "metal"
  and (.runs[0].fatal_line | contains("answer repeated token run 3 exceeded 2"))
' "$REPEATED_ANSWER_JSON" >/dev/null; then
  echo "Expected repeated-token answer to be rejected by max repeated token run gate" >&2
  cat "$REPEATED_ANSWER_JSON" >&2
  exit 1
fi

if ! grep -qx $'selectedCase\tmetal-mean' "$DIAGNOSIS_TSV" \
    || ! grep -qx $'primaryStage\tdecode' "$DIAGNOSIS_TSV" \
    || ! grep -qx $'primaryMetric\tdecodeMs' "$DIAGNOSIS_TSV" \
    || ! grep -qx $'primaryValueMs\t18.000' "$DIAGNOSIS_TSV" \
    || ! grep -qx $'argmaxPaths\tnative_argmax_f32=2' "$DIAGNOSIS_TSV" \
    || ! grep -qx $'argmaxPathMetal\ttrue' "$DIAGNOSIS_TSV" \
    || ! grep -qx $'argmaxPathFallback\tfalse' "$DIAGNOSIS_TSV" \
    || ! grep -qx $'decode\tdecodeMs\t18.000\t1.463\tprimary\tattn_q_proj:metal_matmul_f16=2; ffn_down:metal_matmul_f16=2; logits:metal_matmul_f16=2\tFocus decode token loop, KV cache reuse, Metal dispatch overhead, and per-token allocations.' "$DIAGNOSIS_STAGES_TSV"; then
  echo "Expected safetensor benchmark diagnosis to identify decode bottleneck" >&2
  cat "$DIAGNOSIS_TSV" >&2
  cat "$DIAGNOSIS_STAGES_TSV" >&2
  exit 1
fi

if ! grep -qx $'case\tpathGroup\tstageScope\tmetalStatus\tfallbackStatus\tstatus\tprimary\tpathEvidence\tnextAction' "$DIAGNOSIS_PATHS_TSV" \
    || ! grep -qx $'metal-mean\targmax\targmax\ttrue\tfalse\tpass\tfalse\tnative_argmax_f32=2\tArgmax path is Metal-clean; tune greedy selection only if it is a top stage.' "$DIAGNOSIS_PATHS_TSV"; then
  echo "Expected safetensor benchmark diagnosis to expose argmax path evidence" >&2
  cat "$DIAGNOSIS_PATHS_TSV" >&2
  exit 1
fi

ARGMAX_DIAG_GATE="$TMP_DIR/argmax-diagnosis-gate.tsv"
ARGMAX_DIAG_DIR="$TMP_DIR/argmax-diagnosis"
{
  printf 'case\tdurationMs\tbackend\tprofileMetal\tstatus\tcorePathStatus\tlinearPathStatus\tlogitsPathStatus\tffnPathStatus\tattentionPathStatus\targmaxPathStatus\tcorePathCoverage\tlinearPathCoverage\tlogitsPathCoverage\tffnPathCoverage\tattentionPathCoverage\targmaxPathCoverage\ttopStage\ttopStageMs\tprefillMs\tdecodeMs\ttpotMs\tsamplingMs\targmaxMs\tattentionMs\tffnMs\tlogitsMs\tlinearPaths\tlogitsPaths\tffnPaths\tattentionPaths\targmaxPaths\tlog\n'
  printf 'metal-deterministic\t1230\tmetal\ttrue\tpassed\tmixed\tmetal\tmetal\tmetal\tmetal\tfallback\t12/14\t6/6\t2/2\t2/2\t2/2\t0/2\targmax\t22.00\t4.00\t18.00\t9.00\t1.00\t22.00\t6.00\t14.00\t3.00\tattn_q_proj:metal_matmul_f16=2; ffn_down:metal_matmul_f16=2; logits:metal_matmul_f16=2\tmetal_matmul_f16=2\tmetal_geglu=2\tpaged_metal=2\tjava_memory_segment=2\t/tmp/fake.log\n'
} > "$ARGMAX_DIAG_GATE"

bash "$ROOT_DIR/scripts/diagnose-safetensor-profile-summary.sh" \
  --summary "$ARGMAX_DIAG_GATE" \
  --summary-dir "$ARGMAX_DIAG_DIR" > "$TMP_DIR/argmax-diagnosis.out"

if ! grep -qx $'primaryStage\targmax' "$ARGMAX_DIAG_DIR/diagnosis.tsv" \
    || ! grep -qx $'metalPathStatus\tfail' "$ARGMAX_DIAG_DIR/diagnosis.tsv" \
    || ! grep -qx $'pathFallbackStatus\tfail' "$ARGMAX_DIAG_DIR/diagnosis.tsv" \
    || ! grep -qx $'argmaxPathMetal\tfalse' "$ARGMAX_DIAG_DIR/diagnosis.tsv" \
    || ! grep -qx $'argmaxPathFallback\ttrue' "$ARGMAX_DIAG_DIR/diagnosis.tsv" \
    || ! grep -qx $'metal-mean\targmax\targmax\tfalse\ttrue\tfallback\ttrue\tjava_memory_segment=2\tRemove argmax fallback markers before latency tuning.' "$ARGMAX_DIAG_DIR/paths.tsv"; then
  echo "Expected diagnosis to flag Java argmax fallback as the primary blocker" >&2
  cat "$ARGMAX_DIAG_DIR/diagnosis.tsv" >&2
  cat "$ARGMAX_DIAG_DIR/paths.tsv" >&2
  exit 1
fi

FAIL_LABEL="decode-threshold-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$FAIL_LABEL" \
  --quick \
  --profile \
  --require-profile \
  --require-metal \
  --max-decode-ms 10 \
  --max-tokens 2 > "$TMP_DIR/bench-fail.out" 2>&1
fail_exit=$?
set -e

if [[ "$fail_exit" -eq 0 ]]; then
  echo "Expected decode threshold guard to fail with non-zero exit" >&2
  cat "$TMP_DIR/bench-fail.out" >&2
  exit 1
fi

FAIL_JSON="$OUT_DIR/$FAIL_LABEL/summary.json"
if [[ ! -f "$FAIL_JSON" ]]; then
  echo "Expected failed safetensor benchmark to still write summary.json" >&2
  cat "$TMP_DIR/bench-fail.out" >&2
  exit 1
fi

if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and (.runs[0].fatal_line | contains("decode=18.00ms exceeded 10ms"))
' "$FAIL_JSON" >/dev/null; then
  echo "Expected decode threshold failure to be recorded in failed summary" >&2
  cat "$FAIL_JSON" >&2
  exit 1
fi

FFN_FAIL_LABEL="ffn-threshold-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$FFN_FAIL_LABEL" \
  --quick \
  --profile \
  --require-profile \
  --require-metal \
  --max-ffn-ms 10 \
  --max-tokens 2 > "$TMP_DIR/bench-ffn-fail.out" 2>&1
ffn_fail_exit=$?
set -e

if [[ "$ffn_fail_exit" -eq 0 ]]; then
  echo "Expected FFN threshold guard to fail with non-zero exit" >&2
  cat "$TMP_DIR/bench-ffn-fail.out" >&2
  exit 1
fi

FFN_FAIL_JSON="$OUT_DIR/$FFN_FAIL_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and (.runs[0].fatal_line | contains("ffn=14.00ms exceeded 10ms"))
' "$FFN_FAIL_JSON" >/dev/null; then
  echo "Expected FFN threshold failure to be recorded in failed summary" >&2
  cat "$FFN_FAIL_JSON" >&2
  exit 1
fi

SAMPLING_FAIL_LABEL="sampling-threshold-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$SAMPLING_FAIL_LABEL" \
  --quick \
  --profile \
  --require-profile \
  --require-metal \
  --max-sampling-ms 0.5 \
  --max-tokens 2 > "$TMP_DIR/bench-sampling-fail.out" 2>&1
sampling_fail_exit=$?
set -e

if [[ "$sampling_fail_exit" -eq 0 ]]; then
  echo "Expected sampling threshold guard to fail with non-zero exit" >&2
  cat "$TMP_DIR/bench-sampling-fail.out" >&2
  exit 1
fi

SAMPLING_FAIL_JSON="$OUT_DIR/$SAMPLING_FAIL_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and .runs[0].profile_sampling_ms == 1
  and (.runs[0].fatal_line | contains("sampling=1.00ms exceeded 0.5ms"))
' "$SAMPLING_FAIL_JSON" >/dev/null; then
  echo "Expected sampling threshold failure to be recorded in failed summary" >&2
  cat "$SAMPLING_FAIL_JSON" >&2
  exit 1
fi

SPEED_FAIL_LABEL="speed-threshold-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$SPEED_FAIL_LABEL" \
  --quick \
  --profile \
  --require-profile \
  --require-metal \
  --min-speed-tps 2.0 \
  --max-tokens 2 > "$TMP_DIR/bench-speed-fail.out" 2>&1
speed_fail_exit=$?
set -e

if [[ "$speed_fail_exit" -eq 0 ]]; then
  echo "Expected speed threshold guard to fail with non-zero exit" >&2
  cat "$TMP_DIR/bench-speed-fail.out" >&2
  exit 1
fi

SPEED_FAIL_JSON="$OUT_DIR/$SPEED_FAIL_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and .runs[0].speed_tps == 1.63
  and .runs[0].chunks == 2
  and (.runs[0].fatal_line | contains("speed=1.63 t/s below 2.0 t/s"))
' "$SPEED_FAIL_JSON" >/dev/null; then
  echo "Expected speed threshold failure to be recorded in failed summary" >&2
  cat "$SPEED_FAIL_JSON" >&2
  exit 1
fi

ARGMAX_FAIL_LABEL="argmax-threshold-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$ARGMAX_FAIL_LABEL" \
  --quick \
  --profile \
  --require-profile \
  --require-metal \
  --max-argmax-ms 0.25 \
  --max-tokens 2 > "$TMP_DIR/bench-argmax-fail.out" 2>&1
argmax_fail_exit=$?
set -e

if [[ "$argmax_fail_exit" -eq 0 ]]; then
  echo "Expected argmax threshold guard to fail with non-zero exit" >&2
  cat "$TMP_DIR/bench-argmax-fail.out" >&2
  exit 1
fi

ARGMAX_FAIL_JSON="$OUT_DIR/$ARGMAX_FAIL_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and .runs[0].profile_argmax_ms == 0.5
  and (.runs[0].fatal_line | contains("argmax=0.50ms exceeded 0.25ms"))
' "$ARGMAX_FAIL_JSON" >/dev/null; then
  echo "Expected argmax threshold failure to be recorded in failed summary" >&2
  cat "$ARGMAX_FAIL_JSON" >&2
  exit 1
fi

JAVA_ARGMAX_BIN="$TMP_DIR/aqli-java-argmax"
cat > "$JAVA_ARGMAX_BIN" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf 'Platform: Metal\n'
printf '✓ GPU acceleration enabled (Apple M4)\n'
printf 'Model: fake-safetensor\n'
printf 'Provider: safetensor\n'
printf '%s\n' '--------------------------------------------------'
printf 'Jakarta is in Indonesia.\n'
printf '\n[PROFILE] backend=metal mode=direct prefill=4.00ms decode=18.00ms tpot=9.00ms sampling=1.00ms argmax=0.50ms attention=6.00ms ffn=14.00ms logits=3.00ms steps=2 linear_paths={attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2} logits_paths={metal_matmul_f16=2} ffn_paths={metal_geglu=2} attention_paths={paged_metal=2} argmax_paths={java_memory_segment=2} path_status={core=metal, linear=metal, logits=metal, ffn=metal, attention=metal, argmax=fallback} path_coverage={core=12/12, linear=6/6, logits=2/2, ffn=2/2, attention=2/2, argmax=0/2}\n'
printf '[Stream updates: 2, Duration: 1,23s, Speed: 1,63 t/s]\n'
SH
chmod +x "$JAVA_ARGMAX_BIN"

ARGMAX_COVERAGE_FAIL_LABEL="argmax-coverage-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$JAVA_ARGMAX_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$ARGMAX_COVERAGE_FAIL_LABEL" \
  --quick \
  --profile \
  --require-profile \
  --require-metal \
  --allow-fallback-paths \
  --min-argmax-metal-coverage 1 \
  --max-tokens 2 > "$TMP_DIR/bench-argmax-coverage-fail.out" 2>&1
argmax_coverage_fail_exit=$?
set -e

if [[ "$argmax_coverage_fail_exit" -eq 0 ]]; then
  echo "Expected argmax Metal coverage guard to fail with non-zero exit" >&2
  cat "$TMP_DIR/bench-argmax-coverage-fail.out" >&2
  exit 1
fi

ARGMAX_COVERAGE_FAIL_JSON="$OUT_DIR/$ARGMAX_COVERAGE_FAIL_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and .runs[0].profile_argmax_path_status == "fallback"
  and .runs[0].profile_argmax_path_coverage == "0/2"
  and .runs[0].profile_argmax_paths == "java_memory_segment=2"
  and (.runs[0].fatal_line | contains("argmax Metal coverage 0/2 below 1.000"))
' "$ARGMAX_COVERAGE_FAIL_JSON" >/dev/null; then
  echo "Expected Java argmax fallback to be rejected by Metal coverage gate" >&2
  cat "$ARGMAX_COVERAGE_FAIL_JSON" >&2
  exit 1
fi

ARGMAX_PATH_FAIL_LABEL="argmax-path-proof-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$JAVA_ARGMAX_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$ARGMAX_PATH_FAIL_LABEL" \
  --quick \
  --profile \
  --require-profile \
  --require-metal \
  --require-metal-paths \
  --allow-fallback-paths \
  --max-tokens 2 > "$TMP_DIR/bench-argmax-path-fail.out" 2>&1
argmax_path_fail_exit=$?
set -e

if [[ "$argmax_path_fail_exit" -eq 0 ]]; then
  echo "Expected argmax Metal path proof guard to fail with non-zero exit" >&2
  cat "$TMP_DIR/bench-argmax-path-fail.out" >&2
  exit 1
fi

ARGMAX_PATH_FAIL_JSON="$OUT_DIR/$ARGMAX_PATH_FAIL_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and .runs[0].profile_argmax_paths == "java_memory_segment=2"
  and (.runs[0].fatal_line | contains("Metal argmax path was required but not proven"))
' "$ARGMAX_PATH_FAIL_JSON" >/dev/null; then
  echo "Expected Java argmax fallback to be rejected by Metal path proof gate" >&2
  cat "$ARGMAX_PATH_FAIL_JSON" >&2
  exit 1
fi

CPU_PATH_BIN="$TMP_DIR/aqli-cpu-paths"
cat > "$CPU_PATH_BIN" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf 'Platform: Metal\n'
printf '✓ GPU acceleration enabled (Apple M4)\n'
printf 'Model: fake-safetensor\n'
printf 'Provider: safetensor\n'
printf '%s\n' '--------------------------------------------------'
printf 'Jakarta is in Indonesia.\n'
printf '\n[PROFILE] backend=metal mode=direct prefill=4.00ms decode=18.00ms tpot=9.00ms attention=6.00ms ffn=14.00ms logits=3.00ms steps=2 linear_paths={attn_q_proj:cpu_matvec=2, ffn_down:cpu_matvec=2, logits:cpu_matvec=2} logits_paths={cpu_logits=2} ffn_paths={java_geglu=2} attention_paths={paged_cpu=2} path_status={core=fallback, linear=fallback, logits=fallback, ffn=fallback, attention=fallback, argmax=missing} path_coverage={core=0/12, linear=0/6, logits=0/2, ffn=0/2, attention=0/2, argmax=0/0}\n'
printf '[Stream updates: 2, Duration: 1,23s, Speed: 1,63 t/s]\n'
SH
chmod +x "$CPU_PATH_BIN"

PARTIAL_FALLBACK_BIN="$TMP_DIR/aqli-partial-fallback"
cat > "$PARTIAL_FALLBACK_BIN" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf 'Platform: Metal\n'
printf '✓ GPU acceleration enabled (Apple M4)\n'
printf 'Model: fake-safetensor\n'
printf 'Provider: safetensor\n'
printf '%s\n' '--------------------------------------------------'
printf 'Jakarta is in Indonesia.\n'
printf '\n[PROFILE] backend=metal mode=direct prefill=4.00ms decode=18.00ms tpot=9.00ms attention=6.00ms ffn=14.00ms logits=3.00ms steps=2 linear_paths={attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2} logits_paths={metal_matmul_f16=2} ffn_paths={metal_geglu=1, java_geglu=1} attention_paths={paged_metal=2} argmax_paths={native_argmax_f32=2} path_status={core=mixed, linear=metal, logits=metal, ffn=mixed, attention=metal, argmax=metal} path_coverage={core=11/12, linear=6/6, logits=2/2, ffn=1/2, attention=2/2, argmax=2/2}\n'
printf '[Stream updates: 2, Duration: 1,23s, Speed: 1,63 t/s]\n'
SH
chmod +x "$PARTIAL_FALLBACK_BIN"

PARTIAL_FALLBACK_FAIL_LABEL="partial-fallback-preset-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$PARTIAL_FALLBACK_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$PARTIAL_FALLBACK_FAIL_LABEL" \
  --quick \
  --preset m4-smoke \
  --max-tokens 2 > "$TMP_DIR/bench-partial-fallback-fail.out" 2>&1
partial_fallback_fail_exit=$?
set -e

if [[ "$partial_fallback_fail_exit" -eq 0 ]]; then
  echo "Expected m4-smoke preset to fail when a hidden fallback path appears inside Metal evidence" >&2
  cat "$TMP_DIR/bench-partial-fallback-fail.out" >&2
  exit 1
fi

PARTIAL_FALLBACK_FAIL_JSON="$OUT_DIR/$PARTIAL_FALLBACK_FAIL_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and (.runs[0].fatal_line | contains("Fallback FFN path was rejected"))
  and (.runs[0].fatal_line | contains("java_geglu=1"))
' "$PARTIAL_FALLBACK_FAIL_JSON" >/dev/null; then
  echo "Expected hidden FFN fallback rejection to be recorded in failed summary" >&2
  cat "$PARTIAL_FALLBACK_FAIL_JSON" >&2
  exit 1
fi

COVERAGE_FAIL_LABEL="coverage-threshold-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$PARTIAL_FALLBACK_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$COVERAGE_FAIL_LABEL" \
  --quick \
  --profile \
  --require-profile \
  --require-metal \
  --allow-fallback-paths \
  --min-core-metal-coverage 1 \
  --min-ffn-metal-coverage 1 \
  --max-tokens 2 > "$TMP_DIR/bench-coverage-fail.out" 2>&1
coverage_fail_exit=$?
set -e

if [[ "$coverage_fail_exit" -eq 0 ]]; then
  echo "Expected Metal coverage threshold guard to fail with non-zero exit" >&2
  cat "$TMP_DIR/bench-coverage-fail.out" >&2
  exit 1
fi

COVERAGE_FAIL_JSON="$OUT_DIR/$COVERAGE_FAIL_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and (.runs[0].fatal_line | contains("core Metal coverage 11/12 below 1.000"))
  and (.runs[0].fatal_line | contains("FFN Metal coverage 1/2 below 1.000"))
' "$COVERAGE_FAIL_JSON" >/dev/null; then
  echo "Expected partial Metal coverage failure to be recorded in failed summary" >&2
  cat "$COVERAGE_FAIL_JSON" >&2
  exit 1
fi

PARTIAL_FALLBACK_ALLOWED_LABEL="partial-fallback-allowed"
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$PARTIAL_FALLBACK_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$PARTIAL_FALLBACK_ALLOWED_LABEL" \
  --quick \
  --preset m4-smoke \
  --allow-fallback-paths \
  --min-core-metal-coverage 0 \
  --min-ffn-metal-coverage 0 \
  --max-tokens 2 > "$TMP_DIR/bench-partial-fallback-allowed.out"

PARTIAL_FALLBACK_ALLOWED_JSON="$OUT_DIR/$PARTIAL_FALLBACK_ALLOWED_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "passed"
  and .runs[0].profile_backend == "metal"
  and .runs[0].profile_core_path_status == "mixed"
  and .runs[0].profile_ffn_path_status == "mixed"
  and .runs[0].profile_core_path_coverage == "11/12"
  and .runs[0].profile_ffn_path_coverage == "1/2"
  and (.runs[0].profile_ffn_paths | contains("java_geglu=1"))
' "$PARTIAL_FALLBACK_ALLOWED_JSON" >/dev/null; then
  echo "Expected explicit fallback and coverage allowance to keep diagnostic partial-fallback run available" >&2
  cat "$PARTIAL_FALLBACK_ALLOWED_JSON" >&2
  exit 1
fi

NO_PROFILE_BACKEND_BIN="$TMP_DIR/aqli-no-profile-backend"
cat > "$NO_PROFILE_BACKEND_BIN" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf 'Platform: Metal\n'
printf '✓ GPU acceleration enabled (Apple M4)\n'
printf 'Model: fake-safetensor\n'
printf 'Provider: safetensor\n'
printf '%s\n' '--------------------------------------------------'
printf 'Jakarta is in Indonesia.\n'
printf '\n[PROFILE] mode=direct prefill=4.00ms decode=18.00ms tpot=9.00ms attention=6.00ms ffn=14.00ms logits=3.00ms steps=2 linear_paths={attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2} logits_paths={metal_matmul_f16=2} ffn_paths={metal_geglu=2} attention_paths={paged_metal=2} argmax_paths={native_argmax_f32=2} path_status={core=metal, linear=metal, logits=metal, ffn=metal, attention=metal, argmax=metal} path_coverage={core=12/12, linear=6/6, logits=2/2, ffn=2/2, attention=2/2, argmax=2/2}\n'
printf '[Stream updates: 2, Duration: 1,23s, Speed: 1,63 t/s]\n'
SH
chmod +x "$NO_PROFILE_BACKEND_BIN"

NO_PROFILE_BACKEND_FAIL_LABEL="profile-backend-required-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$NO_PROFILE_BACKEND_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$NO_PROFILE_BACKEND_FAIL_LABEL" \
  --quick \
  --profile \
  --require-profile \
  --require-metal \
  --max-tokens 2 > "$TMP_DIR/bench-profile-backend-required-fail.out" 2>&1
no_profile_backend_fail_exit=$?
set -e

if [[ "$no_profile_backend_fail_exit" -eq 0 ]]; then
  echo "Expected require-metal to fail when profile omits backend even if the log banner says Metal" >&2
  cat "$TMP_DIR/bench-profile-backend-required-fail.out" >&2
  exit 1
fi

NO_PROFILE_BACKEND_FAIL_JSON="$OUT_DIR/$NO_PROFILE_BACKEND_FAIL_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and .runs[0].profile_backend == null
  and .runs[0].profile_metal == "false"
  and (.runs[0].fatal_line | contains("Metal backend was required but not proven"))
' "$NO_PROFILE_BACKEND_FAIL_JSON" >/dev/null; then
  echo "Expected missing profile backend to be rejected instead of trusting cosmetic Metal logs" >&2
  cat "$NO_PROFILE_BACKEND_FAIL_JSON" >&2
  exit 1
fi

METAL_PATH_PRESET_FAIL_LABEL="metal-path-preset-proof-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$CPU_PATH_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$METAL_PATH_PRESET_FAIL_LABEL" \
  --quick \
  --preset m4-smoke \
  --max-tokens 2 > "$TMP_DIR/bench-metal-path-preset-fail.out" 2>&1
metal_path_preset_fail_exit=$?
set -e

if [[ "$metal_path_preset_fail_exit" -eq 0 ]]; then
  echo "Expected m4-smoke preset to fail when Metal path proof is cosmetic" >&2
  cat "$TMP_DIR/bench-metal-path-preset-fail.out" >&2
  exit 1
fi

METAL_PATH_PRESET_FAIL_JSON="$OUT_DIR/$METAL_PATH_PRESET_FAIL_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and (.runs[0].fatal_line | contains("Metal linear path was required but not proven"))
  and (.runs[0].fatal_line | contains("Metal logits path was required but not proven"))
  and (.runs[0].fatal_line | contains("Metal FFN path was required but not proven"))
  and (.runs[0].fatal_line | contains("Metal attention path was required but not proven"))
  and (.runs[0].fatal_line | contains("Metal argmax path was required but not proven"))
' "$METAL_PATH_PRESET_FAIL_JSON" >/dev/null; then
  echo "Expected m4-smoke preset to enforce real Metal linear, FFN, and attention paths" >&2
  cat "$METAL_PATH_PRESET_FAIL_JSON" >&2
  exit 1
fi

METAL_PATH_FAIL_LABEL="metal-path-proof-fail"
set +e
bash "$ROOT_DIR/scripts/bench-safetensor-inference.sh" \
  --model fake-model \
  --aqli-bin "$CPU_PATH_BIN" \
  --out-dir "$OUT_DIR" \
  --label "$METAL_PATH_FAIL_LABEL" \
  --quick \
  --profile \
  --require-profile \
  --require-metal \
  --require-metal-paths \
  --max-tokens 2 > "$TMP_DIR/bench-metal-path-fail.out" 2>&1
metal_path_fail_exit=$?
set -e

if [[ "$metal_path_fail_exit" -eq 0 ]]; then
  echo "Expected Metal path proof guard to fail with non-zero exit" >&2
  cat "$TMP_DIR/bench-metal-path-fail.out" >&2
  exit 1
fi

METAL_PATH_FAIL_JSON="$OUT_DIR/$METAL_PATH_FAIL_LABEL/summary.json"
if ! jq -e '
  .runs[0].status == "failed"
  and .runs[0].exit_code == 1
  and (.runs[0].profile_backend == "metal")
  and (.runs[0].profile_metal == "true")
  and (.runs[0].fatal_line | contains("Metal linear path was required but not proven"))
  and (.runs[0].fatal_line | contains("Metal logits path was required but not proven"))
  and (.runs[0].fatal_line | contains("Metal FFN path was required but not proven"))
  and (.runs[0].fatal_line | contains("Metal attention path was required but not proven"))
  and (.runs[0].fatal_line | contains("Metal argmax path was required but not proven"))
' "$METAL_PATH_FAIL_JSON" >/dev/null; then
  echo "Expected Metal path proof failure to reject CPU fallback path evidence" >&2
  cat "$METAL_PATH_FAIL_JSON" >&2
  exit 1
fi

printf 'safetensor benchmark profile parser test passed\n'
