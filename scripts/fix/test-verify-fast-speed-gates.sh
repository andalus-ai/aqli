#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-verify-fast-speed-gates.XXXXXX")"
cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

export VERIFY_FAST_GATES_LOG="$TMP_DIR/verify-fast-gates.log"
AQLI_BIN="$TMP_DIR/aqli"
cat > "$AQLI_BIN" <<'SH'
#!/usr/bin/env bash
exit 0
SH
chmod +x "$AQLI_BIN"

make_fake_bench() {
    local path="$1"
    local name="$2"
    cat > "$path" <<SH
#!/usr/bin/env bash
set -euo pipefail
summary=""
args=("\$@")
for ((i = 0; i < \${#args[@]}; i++)); do
    if [[ "\${args[\$i]}" == "--summary-file" ]]; then
        summary="\${args[\$((i + 1))]}"
    fi
done
printf '$name %s\n' "\$*" >> "\$VERIFY_FAST_GATES_LOG"
if [[ -n "\$summary" ]]; then
    case "$name" in
        litert)
            printf 'case\tdurationMs\tbackend\twarmEngine\tengineInitMs\tfirstChunkMs\ttotalMs\trouteResolveMs\trouteCacheHit\tselectedArtifact\trouteSource\tlog\n' > "\$summary"
            printf 'warm\t100\tMTL0 (Apple M4)\ttrue\t1\t2\t3\t2\ttrue\tlitertlm\tfake-litert-index\t/tmp/fake-litert.log\n' >> "\$summary"
            ;;
        gemma4-litert)
            gemma4_case="\${AQLI_FAKE_GEMMA4_CASE:-warm}"
            gemma4_route_resolve_ms="\${AQLI_FAKE_GEMMA4_ROUTE_RESOLVE_MS:-3}"
            gemma4_route_cache_hit="\${AQLI_FAKE_GEMMA4_ROUTE_CACHE_HIT:-true}"
            gemma4_selected_artifact="\${AQLI_FAKE_GEMMA4_SELECTED_ARTIFACT:-litertlm}"
            gemma4_route_source="\${AQLI_FAKE_GEMMA4_ROUTE_SOURCE:-fast-index-equivalent-litert}"
            printf 'case\tdurationMs\tbackend\twarmEngine\tengineInitMs\tfirstChunkMs\ttotalMs\trouteResolveMs\trouteCacheHit\tselectedArtifact\trouteSource\tlog\n' > "\$summary"
            printf '%s\t110\tMTL0 (Apple M4)\ttrue\t1\t2\t3\t%s\t%s\t%s\t%s\t/tmp/fake-gemma4-litert.log\n' "\$gemma4_case" "\$gemma4_route_resolve_ms" "\$gemma4_route_cache_hit" "\$gemma4_selected_artifact" "\$gemma4_route_source" >> "\$summary"
            if [[ -n "\${AQLI_FAKE_GEMMA4_SECOND_CASE:-}" ]]; then
                second_route_resolve_ms="\${AQLI_FAKE_GEMMA4_SECOND_ROUTE_RESOLVE_MS:-\$gemma4_route_resolve_ms}"
                second_route_cache_hit="\${AQLI_FAKE_GEMMA4_SECOND_ROUTE_CACHE_HIT:-\$gemma4_route_cache_hit}"
                second_selected_artifact="\${AQLI_FAKE_GEMMA4_SECOND_SELECTED_ARTIFACT:-\$gemma4_selected_artifact}"
                second_route_source="\${AQLI_FAKE_GEMMA4_SECOND_ROUTE_SOURCE:-\$gemma4_route_source}"
                printf '%s\t111\tMTL0 (Apple M4)\ttrue\t1\t2\t3\t%s\t%s\t%s\t%s\t/tmp/fake-gemma4-litert-second.log\n' "\$AQLI_FAKE_GEMMA4_SECOND_CASE" "\$second_route_resolve_ms" "\$second_route_cache_hit" "\$second_selected_artifact" "\$second_route_source" >> "\$summary"
            fi
            ;;
        gguf)
            printf 'case\tdurationMs\tbackend\twarmSession\ttokenizeCache\tpromptCache\tpromptCacheEagerShort\toutputBytes\tjavaRetries\tmodelLoadMs\ttokenizeMs\tprefillMs\tdecodeMs\tlog\n' > "\$summary"
            printf 'warm\t120\tMTL0 (Apple M4)\ttrue\thit\tprefix-hit\tfalse\t64\t0\t0\t4\t5\t6\t/tmp/fake-gguf.log\n' >> "\$summary"
            ;;
        safetensor)
            linear_paths="\${AQLI_FAKE_SAFETENSOR_LINEAR_PATHS:-attn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2}"
            logits_paths="\${AQLI_FAKE_SAFETENSOR_LOGITS_PATHS:-metal_logits=2}"
            ffn_paths="\${AQLI_FAKE_SAFETENSOR_FFN_PATHS:-metal_geglu=2}"
            attention_paths="\${AQLI_FAKE_SAFETENSOR_ATTENTION_PATHS:-paged_metal=2}"
            argmax_paths="\${AQLI_FAKE_SAFETENSOR_ARGMAX_PATHS:-metal_argmax=2}"
            printf 'case\tdurationMs\tbackend\tprofileMetal\tstatus\ttopStage\ttopStageMs\tprefillMs\tdecodeMs\ttpotMs\tattentionMs\tffnMs\tlogitsMs\tlinearPaths\tlogitsPaths\tffnPaths\tattentionPaths\targmaxPaths\tlog\n' > "\$summary"
            printf 'metal-deterministic\t140\tmetal\ttrue\tpassed\tdecode\t18.00\t4.00\t18.00\t9.00\t6.00\t14.00\t3.00\t%s\t%s\t%s\t%s\t%s\t/tmp/fake-safetensor.log\n' "\$linear_paths" "\$logits_paths" "\$ffn_paths" "\$attention_paths" "\$argmax_paths" >> "\$summary"
            ;;
        *)
            printf 'case\tdurationMs\tbackend\twarmSession\tpromptCache\tmodelLoadMs\ttokenizeMs\tprefillMs\tdecodeMs\tjavaStatus\tloaderReady\tdecoderTensorsReady\trowDotReady\tgenerationReady\tbenchmarkParallelMatVecMs\tjavaOnlyParallelMatVecMs\tbestParallelMatVecMs\tjavaMatVecThresholdMs\tjavaFallbackRefusal\tlog\n' > "\$summary"
            printf 'compare\t130\tMTL0 (Apple M4)\ttrue\thit\t0\t4\t5\t6\trow-dot-primitives-ready\ttrue\ttrue\ttrue\tfalse\t7\t8\t7\t9\tchecked\t/tmp/fake-compare.log\n' >> "\$summary"
            ;;
    esac
fi
if [[ "\${AQLI_FAKE_FAIL_GATE:-}" == "$name" ]]; then
    exit 42
fi
SH
    chmod +x "$path"
}

make_fake_bench "$TMP_DIR/fake-litert-bench.sh" litert
make_fake_bench "$TMP_DIR/fake-gemma4-litert-smoke.sh" gemma4-litert
make_fake_bench "$TMP_DIR/fake-gguf-bench.sh" gguf
make_fake_bench "$TMP_DIR/fake-gguf-compare-bench.sh" gguf-compare
make_fake_bench "$TMP_DIR/fake-safetensor-bench.sh" safetensor

ROUTE_STATS_HEADER=$'gate\tcase\tcount\tminRouteResolveMs\tavgRouteResolveMs\tmaxRouteResolveMs\trouteSources'
ROUTE_ISSUES_HEADER=$'gate\tcase\tissue\tactual\texpected\tbudgetMs\tlog'
ROUTE_ISSUE_SUMMARY_HEADER=$'issue\tcount\tcases'

write_gemma4_route_summary() {
    local path="$1"
    shift
    printf 'case\tdurationMs\tbackend\twarmEngine\tengineInitMs\tfirstChunkMs\ttotalMs\trouteResolveMs\trouteCacheHit\tselectedArtifact\trouteSource\tlog\n' > "$path"
    while [[ $# -gt 0 ]]; do
        printf '%s\n' "$1" >> "$path"
        shift
    done
}

run_gemma4_route_summary_gate() {
    local summary="$1"
    local summary_dir="$2"
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only gemma4-litert \
        --summary-dir "$summary_dir" \
        --aqli-bin "$AQLI_BIN" \
        --gemma4-litert-route-summary "$summary" \
        --no-require-metal
}

assert_gemma4_smoke_not_invoked() {
    local context="$1"
    if grep -q '^gemma4-litert ' "$VERIFY_FAST_GATES_LOG"; then
        echo "Expected $context to skip the Gemma4 smoke runner" >&2
        cat "$VERIFY_FAST_GATES_LOG" >&2
        exit 1
    fi
}

assert_tsv_header_only() {
    local path="$1"
    local header="$2"
    local context="$3"
    if [[ ! -f "$path" ]]; then
        echo "Expected $context artifact to exist: $path" >&2
        exit 1
    fi
    if ! grep -qx "$header" "$path" || [[ "$(wc -l < "$path")" -ne 1 ]]; then
        echo "Expected $context to contain only its TSV header" >&2
        cat "$path" >&2
        exit 1
    fi
}

SAFETENSOR_METAL_PATHS_DEFAULT=false
if [[ "$(uname -s)" == "Darwin" ]]; then
    SAFETENSOR_METAL_PATHS_DEFAULT=true
fi

HELP_OUT="$TMP_DIR/help.out"
bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" --help > "$HELP_OUT"
if ! grep -Fqx 'Examples:' "$HELP_OUT" \
        || ! grep -Fqx '  Verify Gemma4 LiteRT route contracts from a captured route summary without' "$HELP_OUT" \
        || ! grep -Fqx '    verify-fast-speed-gates.sh --only gemma4-litert \' "$HELP_OUT" \
        || ! grep -Fqx '      --gemma4-litert-route-summary /tmp/gemma4-route-summary.tsv' "$HELP_OUT" \
        || ! grep -Fqx '  Use auto mode to run installed gates plus Gemma4 route-summary contracts even' "$HELP_OUT" \
        || ! grep -Fqx '    AQLI_VERIFY_GEMMA4_LITERT_ROUTE_SOURCE= \' "$HELP_OUT" \
        || ! grep -Fqx '    case durationMs backend routeResolveMs routeCacheHit selectedArtifact routeSource log' "$HELP_OUT"; then
    echo "Expected help output to document Gemma4 route-summary examples and TSV columns" >&2
    cat "$HELP_OUT" >&2
    exit 1
fi

SAFETENSOR_PRESETS_TSV="$TMP_DIR/safetensor-presets.tsv"
bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" --list-safetensor-presets > "$SAFETENSOR_PRESETS_TSV"
if ! grep -qx $'name\tdescription' "$SAFETENSOR_PRESETS_TSV" \
        || ! grep -qx $'m4-smoke\tM4 Metal smoke gate with real Metal path proof, full coverage, fallback rejection, and bounded safetensor profile stages' "$SAFETENSOR_PRESETS_TSV"; then
    echo "Expected safetensor preset list to expose m4-smoke" >&2
    cat "$SAFETENSOR_PRESETS_TSV" >&2
    exit 1
fi

SAFETENSOR_BASELINE_ROOT="$TMP_DIR/safetensor-baselines"
SAFETENSOR_BASELINE_NAME="safetensor-local"
SAFETENSOR_BASELINE_STAGES="$SAFETENSOR_BASELINE_ROOT/$SAFETENSOR_BASELINE_NAME/latest-stages.tsv"
mkdir -p "$SAFETENSOR_BASELINE_ROOT/$SAFETENSOR_BASELINE_NAME"
{
    printf 'stage\tmetric\tvalueMs\tshareOfDurationPercent\tpriority\tpathEvidence\trecommendation\n'
    printf 'prefill\tprefillMs\t4.000\t2.857\tnormal\tattn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2\tFocus prefill.\n'
    printf 'decode\tdecodeMs\t17.000\t12.143\tprimary\tattn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2\tFocus decode.\n'
    printf 'attention\tattentionMs\t6.000\t4.286\tnormal\tpaged_metal=2\tFocus attention.\n'
    printf 'ffn\tffnMs\t14.000\t10.000\tsecondary\tmetal_geglu=2\tFocus FFN.\n'
    printf 'logits\tlogitsMs\t3.000\t2.143\tnormal\tmetal_logits=2\tFocus logits.\n'
    printf 'tpot\ttpotMs\t9.000\t6.429\tcontext\tattn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2\tFocus tpot.\n'
} > "$SAFETENSOR_BASELINE_STAGES"

SUMMARY_DIR="$TMP_DIR/summaries"
ALL_OUT="$TMP_DIR/all.out"
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GEMMA4_LITERT_SMOKE="$TMP_DIR/fake-gemma4-litert-smoke.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
AQLI_VERIFY_LITERT_WARM_ENGINE_INIT_THRESHOLD_MS=3 \
AQLI_VERIFY_LITERT_WARM_FIRST_CHUNK_THRESHOLD_MS=4 \
AQLI_VERIFY_LITERT_WARM_TOTAL_THRESHOLD_MS=5 \
AQLI_VERIFY_GEMMA4_LITERT_MAX_TOKENS=31 \
AQLI_VERIFY_GEMMA4_LITERT_WARM_THRESHOLD_MS=901 \
AQLI_VERIFY_GEMMA4_LITERT_WARM_FIRST_CHUNK_THRESHOLD_MS=201 \
AQLI_VERIFY_GEMMA4_LITERT_WARM_TOTAL_THRESHOLD_MS=902 \
AQLI_VERIFY_GGUF_WARM_TOKENIZE_THRESHOLD_MS=6 \
AQLI_VERIFY_GGUF_WARM_PREFILL_THRESHOLD_MS=7 \
AQLI_VERIFY_GGUF_WARM_DECODE_THRESHOLD_MS=8 \
AQLI_VERIFY_GGUF_COMPARE_MAX_TOKENS=12 \
AQLI_VERIFY_GGUF_COMPARE_JAVA_MATVEC_THRESHOLD_MS=9 \
AQLI_VERIFY_GGUF_COMPARE_FALLBACK_PREFILL_THRESHOLD_MS=10 \
AQLI_VERIFY_GGUF_COMPARE_FALLBACK_DECODE_THRESHOLD_MS=11 \
AQLI_VERIFY_GGUF_COMPARE_JAVA_READY_REGEX=row-dot-primitives-ready \
AQLI_VERIFY_GGUF_COMPARE_JAVA_CONFIG_REGEX='type=gemma4, layers=35' \
AQLI_VERIFY_GGUF_COMPARE_JAVA_PROBE_REGEX='preparedMatVecReady=true' \
AQLI_VERIFY_GGUF_COMPARE_JAVA_REFUSAL=false \
AQLI_VERIFY_SAFETENSOR_MAX_TOKENS=2 \
AQLI_VERIFY_SAFETENSOR_TOP_STAGE_THRESHOLD_MS=20 \
AQLI_VERIFY_SAFETENSOR_PREFILL_THRESHOLD_MS=19 \
AQLI_VERIFY_SAFETENSOR_DECODE_THRESHOLD_MS=21 \
AQLI_VERIFY_SAFETENSOR_TPOT_THRESHOLD_MS=22 \
AQLI_VERIFY_SAFETENSOR_ATTENTION_THRESHOLD_MS=23 \
AQLI_VERIFY_SAFETENSOR_FFN_THRESHOLD_MS=24 \
AQLI_VERIFY_SAFETENSOR_LOGITS_THRESHOLD_MS=25 \
AQLI_VERIFY_SAFETENSOR_BASELINE_STAGES=latest \
AQLI_VERIFY_SAFETENSOR_BASELINE_ROOT="$SAFETENSOR_BASELINE_ROOT" \
AQLI_VERIFY_SAFETENSOR_BASELINE_NAME="$SAFETENSOR_BASELINE_NAME" \
AQLI_VERIFY_SAFETENSOR_MAX_REGRESSION_PERCENT=20 \
AQLI_VERIFY_SAFETENSOR_MAX_REGRESSION_MS=5 \
AQLI_VERIFY_SAFETENSOR_MIN_BASELINE_MS=1 \
AQLI_VERIFY_SAFETENSOR_FAIL_PRIMARY_SHIFT=false \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --summary-dir "$SUMMARY_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --litert-model litert-local \
        --gemma4-litert-model gemma4-litert-local \
        --gguf-model gguf-local \
        --safetensor-model safetensor-local \
        --prompt "where is jakarta?" \
        --litert-expected 'litert-ok|Jakarta' \
        --gemma4-litert-expected 'gemma4-ok|Jakarta' \
        --gguf-expected 'gguf-ok|Jakarta' \
        --require-metal \
        --safetensor-require-metal-paths \
        --warm-only \
        --keep-daemon >"$ALL_OUT"

if [[ ! -f "$SUMMARY_DIR/litert-fast.tsv" \
        || ! -f "$SUMMARY_DIR/gemma4-litert-warm.tsv" \
        || ! -f "$SUMMARY_DIR/gguf-fast.tsv" \
        || ! -f "$SUMMARY_DIR/gguf-compare.tsv" \
        || ! -f "$SUMMARY_DIR/safetensor-fast.tsv" \
        || ! -f "$SUMMARY_DIR/config.tsv" \
        || ! -f "$SUMMARY_DIR/manifest.tsv" \
        || ! -f "$SUMMARY_DIR/rollup.tsv" \
        || ! -f "$SUMMARY_DIR/backend.tsv" \
        || ! -f "$SUMMARY_DIR/contracts.tsv" \
        || ! -f "$SUMMARY_DIR/slowest.tsv" \
        || ! -f "$SUMMARY_DIR/route-stats.tsv" \
        || ! -f "$SUMMARY_DIR/route-issues.tsv" \
        || ! -f "$SUMMARY_DIR/route-issue-summary.tsv" \
        || ! -f "$SUMMARY_DIR/safetensor-diagnosis/diagnosis.tsv" \
        || ! -f "$SUMMARY_DIR/safetensor-diagnosis/stages.tsv" \
        || ! -f "$SUMMARY_DIR/safetensor-diagnosis/paths.tsv" \
        || ! -f "$SUMMARY_DIR/safetensor-diagnosis/report.txt" \
        || ! -f "$SUMMARY_DIR/safetensor-regression/comparison.tsv" \
        || ! -f "$SUMMARY_DIR/safetensor-regression/summary.tsv" \
        || ! -f "$SUMMARY_DIR/safetensor-regression/comparison-table.txt" \
        || ! -f "$SUMMARY_DIR/safetensor-regression/report.txt" \
        || ! -f "$SUMMARY_DIR/results.tsv" ]]; then
    echo "Expected aggregate speed gate summaries, config, manifest, rollup, backend, contracts, route stats, slowest, safetensor diagnosis/regression, and results" >&2
    find "$SUMMARY_DIR" -maxdepth 1 -type f -print >&2 || true
    find "$SUMMARY_DIR/safetensor-diagnosis" -maxdepth 1 -type f -print >&2 || true
    find "$SUMMARY_DIR/safetensor-regression" -maxdepth 1 -type f -print >&2 || true
    exit 1
fi

if ! grep -qx $'key\tvalue' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'only\tall' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx "summaryDir	$SUMMARY_DIR" "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'litert.model\tlitert-local' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'gemma4Litert.model\tgemma4-litert-local' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'gguf.model\tgguf-local' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.model\tsafetensor-local' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'prompt\twhere is jakarta?' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'requireMetal\ttrue' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'warmOnly\ttrue' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'keepDaemon\ttrue' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'slowestLimit\t5' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'gguf.promptCache\ttrue' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'litert.warmEngineInitThresholdMs\t3' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'gemma4Litert.maxTokens\t31' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'gemma4Litert.warmThresholdMs\t901' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'gemma4Litert.warmFirstChunkThresholdMs\t201' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'gemma4Litert.warmProfileTotalThresholdMs\t902' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'gguf.warmPrefillThresholdMs\t7' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'ggufCompare.javaConfigRegex\ttype=gemma4, layers=35' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.maxTokens\t2' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.requireProfile\ttrue' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.requireMetalPaths\ttrue' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.topStageThresholdMs\t20' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.prefillThresholdMs\t19' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.decodeThresholdMs\t21' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.tpotThresholdMs\t22' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.attentionThresholdMs\t23' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.ffnThresholdMs\t24' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.logitsThresholdMs\t25' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx "safetensor.diagnosisScript	$ROOT_DIR/scripts/diagnose-safetensor-profile-summary.sh" "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx "safetensor.diagnosisDir	$SUMMARY_DIR/safetensor-diagnosis" "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx "safetensor.diagnosisPaths	$SUMMARY_DIR/safetensor-diagnosis/paths.tsv" "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx "safetensor.compareScript	$ROOT_DIR/scripts/compare-safetensor-profile-diagnosis.sh" "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.baselineStagesRequest\tlatest' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx "safetensor.baselineRoot	$SAFETENSOR_BASELINE_ROOT" "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx "safetensor.baselineName	$SAFETENSOR_BASELINE_NAME" "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx "safetensor.baselineStages	$SAFETENSOR_BASELINE_STAGES" "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx "safetensor.regressionDir	$SUMMARY_DIR/safetensor-regression" "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.maxRegressionPercent\t20' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.maxRegressionMs\t5' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.minBaselineMs\t1' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'safetensor.failPrimaryShift\tfalse' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'gemma4Litert.routeSource\tfast-index-equivalent-litert' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'gemma4Litert.routeResolveThresholdMs\t250' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'gemma4Litert.coldRouteResolveThresholdMs\t1000' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'gemma4Litert.routeOnly\tfalse' "$SUMMARY_DIR/config.tsv" \
        || ! grep -qx $'gemma4Litert.routeSummaryFile\t' "$SUMMARY_DIR/config.tsv"; then
    echo "Expected config artifact to capture resolved aggregate gate settings" >&2
    cat "$SUMMARY_DIR/config.tsv" >&2
    exit 1
fi
if ! grep -qx $'gate\tcase\tdurationMs\tbackend\tmetrics\tlog' "$SUMMARY_DIR/rollup.tsv" \
        || ! grep -qx "litert	warm	100	MTL0 (Apple M4)	warmEngine=true,engineInitMs=1,firstChunkMs=2,totalMs=3,routeResolveMs=2,routeCacheHit=true,selectedArtifact=litertlm,routeSource=fake-litert-index	/tmp/fake-litert.log" "$SUMMARY_DIR/rollup.tsv" \
        || ! grep -qx "gemma4-litert	warm	110	MTL0 (Apple M4)	warmEngine=true,engineInitMs=1,firstChunkMs=2,totalMs=3,routeResolveMs=3,routeCacheHit=true,selectedArtifact=litertlm,routeSource=fast-index-equivalent-litert	/tmp/fake-gemma4-litert.log" "$SUMMARY_DIR/rollup.tsv" \
        || ! grep -qx "gguf	warm	120	MTL0 (Apple M4)	warmSession=true,tokenizeCache=hit,promptCache=prefix-hit,promptCacheEagerShort=false,outputBytes=64,javaRetries=0,modelLoadMs=0,tokenizeMs=4,prefillMs=5,decodeMs=6	/tmp/fake-gguf.log" "$SUMMARY_DIR/rollup.tsv" \
        || ! grep -qx "gguf-compare	compare	130	MTL0 (Apple M4)	warmSession=true,promptCache=hit,modelLoadMs=0,tokenizeMs=4,prefillMs=5,decodeMs=6,javaStatus=row-dot-primitives-ready,loaderReady=true,decoderTensorsReady=true,rowDotReady=true,generationReady=false,benchmarkParallelMatVecMs=7,javaOnlyParallelMatVecMs=8,bestParallelMatVecMs=7,javaMatVecThresholdMs=9,javaFallbackRefusal=checked	/tmp/fake-compare.log" "$SUMMARY_DIR/rollup.tsv" \
        || ! grep -qx "safetensor	metal-deterministic	140	metal	profileMetal=true,status=passed,topStage=decode,topStageMs=18.00,prefillMs=4.00,decodeMs=18.00,tpotMs=9.00,attentionMs=6.00,ffnMs=14.00,logitsMs=3.00,linearPaths=attn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2,logitsPaths=metal_logits=2,ffnPaths=metal_geglu=2,attentionPaths=paged_metal=2,argmaxPaths=metal_argmax=2	/tmp/fake-safetensor.log" "$SUMMARY_DIR/rollup.tsv"; then
    echo "Expected normalized rollup rows for all speed gates" >&2
    cat "$SUMMARY_DIR/rollup.tsv" >&2
    exit 1
fi
if ! grep -qx $'gate\tcase\tbackend\tmetal\tmetalPathProof\tfallbackPathEvidence\twarmReuse\tpromptCache\tjavaFallbackRefusal\trouteResolveMs\trouteCacheHit\tselectedArtifact\trouteSource\tlog' "$SUMMARY_DIR/backend.tsv" \
        || ! grep -qx "litert	warm	MTL0 (Apple M4)	true	n/a	n/a	true	n/a	n/a	2	true	litertlm	fake-litert-index	/tmp/fake-litert.log" "$SUMMARY_DIR/backend.tsv" \
        || ! grep -qx "gemma4-litert	warm	MTL0 (Apple M4)	true	n/a	n/a	true	n/a	n/a	3	true	litertlm	fast-index-equivalent-litert	/tmp/fake-gemma4-litert.log" "$SUMMARY_DIR/backend.tsv" \
        || ! grep -qx "gguf	warm	MTL0 (Apple M4)	true	n/a	n/a	true	prefix-hit	n/a	n/a	n/a	n/a	n/a	/tmp/fake-gguf.log" "$SUMMARY_DIR/backend.tsv" \
        || ! grep -qx "gguf-compare	compare	MTL0 (Apple M4)	true	n/a	n/a	true	hit	checked	n/a	n/a	n/a	n/a	/tmp/fake-compare.log" "$SUMMARY_DIR/backend.tsv" \
        || ! grep -qx "safetensor	metal-deterministic	metal	true	true	false	n/a	n/a	n/a	n/a	n/a	n/a	n/a	/tmp/fake-safetensor.log" "$SUMMARY_DIR/backend.tsv"; then
    echo "Expected backend proof rows for Metal, hidden fallback evidence, warm reuse, prompt cache, and fallback refusal" >&2
    cat "$SUMMARY_DIR/backend.tsv" >&2
    exit 1
fi
if ! grep -qx $'gate\tcase\tmetalRequired\tmetal\tmetalStatus\tmetalPathRequired\tmetalPathProof\tmetalPathStatus\tfallbackPathRejectedRequired\tfallbackPathEvidence\tfallbackPathStatus\twarmRequired\twarmReuse\twarmStatus\tpromptCacheRequired\tpromptCache\tpromptCacheStatus\tjavaFallbackRefusalRequired\tjavaFallbackRefusal\tjavaFallbackRefusalStatus\trouteCacheRequired\trouteCacheHit\trouteCacheStatus\tselectedArtifactRequired\tselectedArtifact\tselectedArtifactStatus\trouteResolveMs\trouteSource\tlog\trouteSourceRequired\trouteSourceStatus\trouteResolveBudgetMs\trouteResolveStatus' "$SUMMARY_DIR/contracts.tsv" \
        || ! grep -qx "litert	warm	true	true	pass	false	n/a	n/a	false	n/a	n/a	true	true	pass	false	n/a	n/a	false	n/a	n/a	false	true	n/a	false	litertlm	n/a	2	fake-litert-index	/tmp/fake-litert.log	n/a	n/a	n/a	n/a" "$SUMMARY_DIR/contracts.tsv" \
        || ! grep -qx "gemma4-litert	warm	true	true	pass	false	n/a	n/a	false	n/a	n/a	true	true	pass	false	n/a	n/a	false	n/a	n/a	true	true	pass	true	litertlm	pass	3	fast-index-equivalent-litert	/tmp/fake-gemma4-litert.log	fast-index-equivalent-litert	pass	250	pass" "$SUMMARY_DIR/contracts.tsv" \
        || ! grep -qx "gguf	warm	true	true	pass	false	n/a	n/a	false	n/a	n/a	true	true	pass	true	prefix-hit	pass	false	n/a	n/a	false	n/a	n/a	false	n/a	n/a	n/a	n/a	/tmp/fake-gguf.log	n/a	n/a	n/a	n/a" "$SUMMARY_DIR/contracts.tsv" \
        || ! grep -qx "gguf-compare	compare	true	true	pass	false	n/a	n/a	false	n/a	n/a	false	true	n/a	false	hit	n/a	false	checked	n/a	false	n/a	n/a	false	n/a	n/a	n/a	n/a	/tmp/fake-compare.log	n/a	n/a	n/a	n/a" "$SUMMARY_DIR/contracts.tsv" \
        || ! grep -qx "safetensor	metal-deterministic	true	true	pass	true	true	pass	false	false	n/a	false	n/a	n/a	false	n/a	n/a	false	n/a	n/a	false	n/a	n/a	false	n/a	n/a	n/a	n/a	/tmp/fake-safetensor.log	n/a	n/a	n/a	n/a" "$SUMMARY_DIR/contracts.tsv"; then
    echo "Expected backend contract rows to mark required checks as pass or n/a" >&2
    cat "$SUMMARY_DIR/contracts.tsv" >&2
    exit 1
fi
if ! grep -qx $'rank\tgate\tcase\tdurationMs\tbackend\tmetrics\tlog' "$SUMMARY_DIR/slowest.tsv" \
        || ! grep -qx "1	safetensor	metal-deterministic	140	metal	profileMetal=true,status=passed,topStage=decode,topStageMs=18.00,prefillMs=4.00,decodeMs=18.00,tpotMs=9.00,attentionMs=6.00,ffnMs=14.00,logitsMs=3.00,linearPaths=attn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2,logitsPaths=metal_logits=2,ffnPaths=metal_geglu=2,attentionPaths=paged_metal=2,argmaxPaths=metal_argmax=2	/tmp/fake-safetensor.log" "$SUMMARY_DIR/slowest.tsv" \
        || ! grep -qx "2	gguf-compare	compare	130	MTL0 (Apple M4)	warmSession=true,promptCache=hit,modelLoadMs=0,tokenizeMs=4,prefillMs=5,decodeMs=6,javaStatus=row-dot-primitives-ready,loaderReady=true,decoderTensorsReady=true,rowDotReady=true,generationReady=false,benchmarkParallelMatVecMs=7,javaOnlyParallelMatVecMs=8,bestParallelMatVecMs=7,javaMatVecThresholdMs=9,javaFallbackRefusal=checked	/tmp/fake-compare.log" "$SUMMARY_DIR/slowest.tsv" \
        || ! grep -qx "3	gguf	warm	120	MTL0 (Apple M4)	warmSession=true,tokenizeCache=hit,promptCache=prefix-hit,promptCacheEagerShort=false,outputBytes=64,javaRetries=0,modelLoadMs=0,tokenizeMs=4,prefillMs=5,decodeMs=6	/tmp/fake-gguf.log" "$SUMMARY_DIR/slowest.tsv" \
        || ! grep -qx "4	gemma4-litert	warm	110	MTL0 (Apple M4)	warmEngine=true,engineInitMs=1,firstChunkMs=2,totalMs=3,routeResolveMs=3,routeCacheHit=true,selectedArtifact=litertlm,routeSource=fast-index-equivalent-litert	/tmp/fake-gemma4-litert.log" "$SUMMARY_DIR/slowest.tsv" \
        || ! grep -qx "5	litert	warm	100	MTL0 (Apple M4)	warmEngine=true,engineInitMs=1,firstChunkMs=2,totalMs=3,routeResolveMs=2,routeCacheHit=true,selectedArtifact=litertlm,routeSource=fake-litert-index	/tmp/fake-litert.log" "$SUMMARY_DIR/slowest.tsv"; then
    echo "Expected slowest report to rank normalized rollup rows by duration" >&2
    cat "$SUMMARY_DIR/slowest.tsv" >&2
    exit 1
fi
if ! grep -qx "$ROUTE_STATS_HEADER" "$SUMMARY_DIR/route-stats.tsv" \
        || ! grep -qx "gemma4-litert	warm	1	3.000	3.000	3.000	fast-index-equivalent-litert" "$SUMMARY_DIR/route-stats.tsv"; then
    echo "Expected route stats report to summarize Gemma4 route resolution timings" >&2
    cat "$SUMMARY_DIR/route-stats.tsv" >&2
    exit 1
fi
if ! grep -qx $'key\tvalue' "$SUMMARY_DIR/safetensor-diagnosis/diagnosis.tsv" \
        || ! grep -qx $'selectedCase\tmetal-mean' "$SUMMARY_DIR/safetensor-diagnosis/diagnosis.tsv" \
        || ! grep -qx $'selectedRows\t1' "$SUMMARY_DIR/safetensor-diagnosis/diagnosis.tsv" \
        || ! grep -qx $'backend\tmetal' "$SUMMARY_DIR/safetensor-diagnosis/diagnosis.tsv" \
        || ! grep -qx $'metal\ttrue' "$SUMMARY_DIR/safetensor-diagnosis/diagnosis.tsv" \
        || ! grep -qx $'primaryStage\tdecode' "$SUMMARY_DIR/safetensor-diagnosis/diagnosis.tsv" \
        || ! grep -qx $'primaryMetric\tdecodeMs' "$SUMMARY_DIR/safetensor-diagnosis/diagnosis.tsv" \
        || ! grep -qx $'primaryValueMs\t18.000' "$SUMMARY_DIR/safetensor-diagnosis/diagnosis.tsv" \
        || ! grep -qx $'primaryShareOfDurationPercent\t12.857' "$SUMMARY_DIR/safetensor-diagnosis/diagnosis.tsv" \
        || ! grep -qx $'decode\tdecodeMs\t18.000\t12.857\tprimary\tattn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2\tFocus decode token loop, KV cache reuse, Metal dispatch overhead, and per-token allocations.' "$SUMMARY_DIR/safetensor-diagnosis/stages.tsv" \
        || ! grep -qx $'metal-mean\tlinear\tlinear\ttrue\tfalse\tpass\ttrue\tattn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2\tLinear path is Metal-clean; tune projection latency.' "$SUMMARY_DIR/safetensor-diagnosis/paths.tsv" \
        || ! grep -qx $'metal-mean\tlogits\tlogits\ttrue\tfalse\tpass\tfalse\tmetal_logits=2\tLogits path is Metal-clean; tune final projection/vocabulary latency.' "$SUMMARY_DIR/safetensor-diagnosis/paths.tsv" \
        || ! grep -qx $'metal-mean\tffn\tffn\ttrue\tfalse\tpass\tfalse\tmetal_geglu=2\tFFN path is Metal-clean; tune fused GEGLU/matvec latency.' "$SUMMARY_DIR/safetensor-diagnosis/paths.tsv" \
        || ! grep -qx $'metal-mean\tattention\tattention\ttrue\tfalse\tpass\tfalse\tpaged_metal=2\tAttention path is Metal-clean; tune latency only if it is a top stage.' "$SUMMARY_DIR/safetensor-diagnosis/paths.tsv"; then
    echo "Expected safetensor diagnosis artifacts to identify the primary profile stage" >&2
    cat "$SUMMARY_DIR/safetensor-diagnosis/diagnosis.tsv" >&2
    cat "$SUMMARY_DIR/safetensor-diagnosis/stages.tsv" >&2
    cat "$SUMMARY_DIR/safetensor-diagnosis/paths.tsv" >&2
    exit 1
fi
if ! grep -qx $'status\tpass' "$SUMMARY_DIR/safetensor-regression/summary.tsv" \
        || ! grep -qx $'comparedStages\t6' "$SUMMARY_DIR/safetensor-regression/summary.tsv" \
        || ! grep -qx $'failedStages\t0' "$SUMMARY_DIR/safetensor-regression/summary.tsv" \
        || ! grep -qx $'primaryStageChanged\tfalse' "$SUMMARY_DIR/safetensor-regression/summary.tsv" \
        || ! grep -qx $'largestSlowdownStage\tdecode' "$SUMMARY_DIR/safetensor-regression/summary.tsv" \
        || ! grep -qx $'largestSlowdownMs\t1.000' "$SUMMARY_DIR/safetensor-regression/summary.tsv" \
        || ! grep -qx $'largestSlowdownPercent\t5.882' "$SUMMARY_DIR/safetensor-regression/summary.tsv" \
        || ! grep -qx $'decode\tdecodeMs\t17.000\t18.000\t1.000\t5.882\tprimary\tprimary\tslower\tpass\t' "$SUMMARY_DIR/safetensor-regression/comparison.tsv"; then
    echo "Expected safetensor regression artifacts to compare baseline and current stages" >&2
    cat "$SUMMARY_DIR/safetensor-regression/summary.tsv" >&2
    cat "$SUMMARY_DIR/safetensor-regression/comparison.tsv" >&2
    exit 1
fi
if ! grep -qx 'PASS: fast speed gates passed' "$ALL_OUT" \
        || ! grep -qx "summaryDir=$SUMMARY_DIR" "$ALL_OUT" \
        || ! grep -qx "artifacts.config=$SUMMARY_DIR/config.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.results=$SUMMARY_DIR/results.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.rollup=$SUMMARY_DIR/rollup.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.backend=$SUMMARY_DIR/backend.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.contracts=$SUMMARY_DIR/contracts.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.slowest=$SUMMARY_DIR/slowest.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.routeStats=$SUMMARY_DIR/route-stats.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.routeIssues=$SUMMARY_DIR/route-issues.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.routeIssueSummary=$SUMMARY_DIR/route-issue-summary.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.safetensorDiagnosis=$SUMMARY_DIR/safetensor-diagnosis/diagnosis.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.safetensorDiagnosisStages=$SUMMARY_DIR/safetensor-diagnosis/stages.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.safetensorDiagnosisPaths=$SUMMARY_DIR/safetensor-diagnosis/paths.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.safetensorDiagnosisReport=$SUMMARY_DIR/safetensor-diagnosis/report.txt" "$ALL_OUT" \
        || ! grep -qx "artifacts.safetensorRegression=$SUMMARY_DIR/safetensor-regression/comparison.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.safetensorRegressionSummary=$SUMMARY_DIR/safetensor-regression/summary.tsv" "$ALL_OUT" \
        || ! grep -qx "artifacts.safetensorRegressionTable=$SUMMARY_DIR/safetensor-regression/comparison-table.txt" "$ALL_OUT" \
        || ! grep -qx "artifacts.safetensorRegressionReport=$SUMMARY_DIR/safetensor-regression/report.txt" "$ALL_OUT" \
        || ! grep -qx 'backend:' "$ALL_OUT" \
        || ! grep -qx '  gemma4-litert/warm metal=true metalPathProof=n/a fallbackPathEvidence=n/a warmReuse=true promptCache=n/a javaFallbackRefusal=n/a routeResolveMs=3 routeCacheHit=true selectedArtifact=litertlm routeSource=fast-index-equivalent-litert backend=MTL0 (Apple M4) log=/tmp/fake-gemma4-litert.log' "$ALL_OUT" \
        || ! grep -qx '  gguf/warm metal=true metalPathProof=n/a fallbackPathEvidence=n/a warmReuse=true promptCache=prefix-hit javaFallbackRefusal=n/a routeResolveMs=n/a routeCacheHit=n/a selectedArtifact=n/a routeSource=n/a backend=MTL0 (Apple M4) log=/tmp/fake-gguf.log' "$ALL_OUT" \
        || ! grep -qx '  safetensor/metal-deterministic metal=true metalPathProof=true fallbackPathEvidence=false warmReuse=n/a promptCache=n/a javaFallbackRefusal=n/a routeResolveMs=n/a routeCacheHit=n/a selectedArtifact=n/a routeSource=n/a backend=metal log=/tmp/fake-safetensor.log' "$ALL_OUT" \
        || ! grep -qx 'contracts:' "$ALL_OUT" \
        || ! grep -qx '  gemma4-litert/warm metal=pass metalPath=n/a fallbackPath=n/a warm=pass promptCache=n/a javaRefusal=n/a routeCache=pass selectedArtifact=pass routeResolveMs=3 routeSource=fast-index-equivalent-litert routeSourceStatus=pass routeResolveBudgetMs=250 routeResolveStatus=pass log=/tmp/fake-gemma4-litert.log' "$ALL_OUT" \
        || ! grep -qx '  gguf/warm metal=pass metalPath=n/a fallbackPath=n/a warm=pass promptCache=pass javaRefusal=n/a routeCache=n/a selectedArtifact=n/a routeResolveMs=n/a routeSource=n/a routeSourceStatus=n/a routeResolveBudgetMs=n/a routeResolveStatus=n/a log=/tmp/fake-gguf.log' "$ALL_OUT" \
        || ! grep -qx '  safetensor/metal-deterministic metal=pass metalPath=pass fallbackPath=n/a warm=n/a promptCache=n/a javaRefusal=n/a routeCache=n/a selectedArtifact=n/a routeResolveMs=n/a routeSource=n/a routeSourceStatus=n/a routeResolveBudgetMs=n/a routeResolveStatus=n/a log=/tmp/fake-safetensor.log' "$ALL_OUT" \
        || ! grep -qx 'slowest:' "$ALL_OUT" \
        || ! grep -qx '  #1 safetensor/metal-deterministic duration=140ms backend=metal log=/tmp/fake-safetensor.log' "$ALL_OUT" \
        || ! grep -qx 'routeStats:' "$ALL_OUT" \
        || ! grep -qx '  gemma4-litert/warm count=1 min=3.000ms avg=3.000ms max=3.000ms routeSources=fast-index-equivalent-litert' "$ALL_OUT" \
        || ! grep -qx 'safetensorDiagnosis:' "$ALL_OUT" \
        || ! grep -qx '  primaryStage=decode primaryMetric=decodeMs primaryValueMs=18.000 recommendation=Focus decode token loop, KV cache reuse, Metal dispatch overhead, and per-token allocations.' "$ALL_OUT" \
        || ! grep -qx 'safetensorPaths:' "$ALL_OUT" \
        || ! grep -qx '  linear scope=linear status=pass metal=true fallback=false primary=true evidence=attn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2' "$ALL_OUT" \
        || ! grep -qx '  logits scope=logits status=pass metal=true fallback=false primary=false evidence=metal_logits=2' "$ALL_OUT" \
        || ! grep -qx '  ffn scope=ffn status=pass metal=true fallback=false primary=false evidence=metal_geglu=2' "$ALL_OUT" \
        || ! grep -qx '  attention scope=attention status=pass metal=true fallback=false primary=false evidence=paged_metal=2' "$ALL_OUT" \
        || ! grep -qx 'safetensorRegression:' "$ALL_OUT" \
        || ! grep -qx '  status=pass reason= primaryStageChanged=false largestSlowdown=decode/decodeMs deltaMs=1.000 deltaPercent=5.882' "$ALL_OUT"; then
    echo "Expected aggregate output to print artifact paths, slowest rows, safetensor diagnosis, and safetensor regression" >&2
    cat "$ALL_OUT" >&2
    exit 1
fi
if grep -qx 'safetensorPathIssues:' "$ALL_OUT"; then
    echo "Expected clean aggregate safetensor output to omit path issue section" >&2
    cat "$ALL_OUT" >&2
    exit 1
fi

if ! grep -qx $'gate\tsummary' "$SUMMARY_DIR/manifest.tsv" \
        || ! grep -qx "litert	$SUMMARY_DIR/litert-fast.tsv" "$SUMMARY_DIR/manifest.tsv" \
        || ! grep -qx "gemma4-litert	$SUMMARY_DIR/gemma4-litert-warm.tsv" "$SUMMARY_DIR/manifest.tsv" \
        || ! grep -qx "gguf	$SUMMARY_DIR/gguf-fast.tsv" "$SUMMARY_DIR/manifest.tsv" \
        || ! grep -qx "gguf-compare	$SUMMARY_DIR/gguf-compare.tsv" "$SUMMARY_DIR/manifest.tsv" \
        || ! grep -qx "safetensor	$SUMMARY_DIR/safetensor-fast.tsv" "$SUMMARY_DIR/manifest.tsv"; then
    echo "Expected manifest rows for all speed gates" >&2
    cat "$SUMMARY_DIR/manifest.tsv" >&2
    exit 1
fi
if ! grep -qx $'gate\tstatus\texitCode\telapsedMs\tsummary\tstdout\tstderr\targv\treason' "$SUMMARY_DIR/results.tsv" \
        || ! grep -q "^litert	pass	0	[0-9][0-9]*	$SUMMARY_DIR/litert-fast.tsv	$SUMMARY_DIR/litert.out	$SUMMARY_DIR/litert.err	$SUMMARY_DIR/litert.argv.tsv	$" "$SUMMARY_DIR/results.tsv" \
        || ! grep -q "^gemma4-litert	pass	0	[0-9][0-9]*	$SUMMARY_DIR/gemma4-litert-warm.tsv	$SUMMARY_DIR/gemma4-litert.out	$SUMMARY_DIR/gemma4-litert.err	$SUMMARY_DIR/gemma4-litert.argv.tsv	$" "$SUMMARY_DIR/results.tsv" \
        || ! grep -q "^gguf	pass	0	[0-9][0-9]*	$SUMMARY_DIR/gguf-fast.tsv	$SUMMARY_DIR/gguf.out	$SUMMARY_DIR/gguf.err	$SUMMARY_DIR/gguf.argv.tsv	$" "$SUMMARY_DIR/results.tsv" \
        || ! grep -q "^gguf-compare	pass	0	[0-9][0-9]*	$SUMMARY_DIR/gguf-compare.tsv	$SUMMARY_DIR/gguf-compare.out	$SUMMARY_DIR/gguf-compare.err	$SUMMARY_DIR/gguf-compare.argv.tsv	$" "$SUMMARY_DIR/results.tsv" \
        || ! grep -q "^safetensor	pass	0	[0-9][0-9]*	$SUMMARY_DIR/safetensor-fast.tsv	$SUMMARY_DIR/safetensor.out	$SUMMARY_DIR/safetensor.err	$SUMMARY_DIR/safetensor.argv.tsv	$" "$SUMMARY_DIR/results.tsv"; then
    echo "Expected pass rows for all speed gates" >&2
    cat "$SUMMARY_DIR/results.tsv" >&2
    exit 1
fi
if [[ ! -f "$SUMMARY_DIR/litert.out" || ! -f "$SUMMARY_DIR/litert.err" \
        || ! -f "$SUMMARY_DIR/litert.argv.tsv" \
        || ! -f "$SUMMARY_DIR/gemma4-litert.out" || ! -f "$SUMMARY_DIR/gemma4-litert.err" \
        || ! -f "$SUMMARY_DIR/gemma4-litert.argv.tsv" \
        || ! -f "$SUMMARY_DIR/gguf.out" || ! -f "$SUMMARY_DIR/gguf.err" \
        || ! -f "$SUMMARY_DIR/gguf.argv.tsv" \
        || ! -f "$SUMMARY_DIR/gguf-compare.out" || ! -f "$SUMMARY_DIR/gguf-compare.err" \
        || ! -f "$SUMMARY_DIR/gguf-compare.argv.tsv" \
        || ! -f "$SUMMARY_DIR/safetensor.out" || ! -f "$SUMMARY_DIR/safetensor.err" \
        || ! -f "$SUMMARY_DIR/safetensor.argv.tsv" ]]; then
    echo "Expected stdout/stderr/argv logs for all speed gates" >&2
    find "$SUMMARY_DIR" -maxdepth 1 -type f -print >&2
    exit 1
fi
if ! grep -qx $'0\tbash' "$SUMMARY_DIR/litert.argv.tsv" \
        || ! grep -qx $'4\t--model' "$SUMMARY_DIR/litert.argv.tsv" \
        || ! grep -qx $'5\tlitert-local' "$SUMMARY_DIR/litert.argv.tsv" \
        || ! grep -qx $'7\twhere is jakarta?' "$SUMMARY_DIR/litert.argv.tsv"; then
    echo "Expected LiteRT argv artifact to preserve command arguments" >&2
    cat "$SUMMARY_DIR/litert.argv.tsv" >&2
    exit 1
fi

if ! grep -q "^litert .*--aqli-bin $AQLI_BIN .*--model litert-local .*--prompt where is jakarta? .*--expected litert-ok|Jakarta .*--require-metal .*--warm-engine-init-threshold-ms 3 .*--warm-first-chunk-threshold-ms 4 .*--warm-profile-total-threshold-ms 5 .*--warm-only .*--summary-file $SUMMARY_DIR/litert-fast.tsv .*--keep-daemon$" "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected LiteRT aggregate gate invocation with thresholds and summary" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi

if ! grep -q "^gemma4-litert .*--aqli-bin $AQLI_BIN .*--model gemma4-litert-local .*--prompt where is jakarta? .*--expected gemma4-ok|Jakarta .*--max-tokens 31 .*--warm-threshold-ms 901 .*--warm-first-chunk-threshold-ms 201 .*--warm-profile-total-threshold-ms 902 .*--require-metal .*--keep-daemon .*-- --summary-file $SUMMARY_DIR/gemma4-litert-warm.tsv$" "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected Gemma4 LiteRT aggregate gate invocation with warm thresholds and summary" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi

if ! grep -q "^gguf .*--aqli-bin $AQLI_BIN .*--model gguf-local .*--prompt where is jakarta? .*--expected gguf-ok|Jakarta .*--require-metal .*--require-prompt-cache .*--warm-tokenize-threshold-ms 6 .*--warm-prefill-threshold-ms 7 .*--warm-decode-threshold-ms 8 .*--warm-only .*--summary-file $SUMMARY_DIR/gguf-fast.tsv .*--keep-daemon$" "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected GGUF aggregate gate invocation with cache guard, thresholds, and summary" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi

if ! grep -q "^gguf-compare .*--aqli-bin $AQLI_BIN .*--model gguf-local .*--prompt where is jakarta? .*--expected gguf-ok|Jakarta .*--max-tokens 12 .*--java-matvec-threshold-ms 9 .*--require-metal .*--fallback-prefill-threshold-ms 10 .*--fallback-decode-threshold-ms 11 .*--java-ready-regex row-dot-primitives-ready .*--java-config-regex type=gemma4, layers=35 .*--java-probe-regex preparedMatVecReady=true .*--no-verify-java-refusal .*--summary-file $SUMMARY_DIR/gguf-compare.tsv$" "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected GGUF compare aggregate gate invocation with thresholds and summary" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi

if ! grep -q "^safetensor .*--aqli-bin $AQLI_BIN .*--model safetensor-local .*--out-dir $SUMMARY_DIR/safetensor-bench .*--label safetensor-fast .*--det-prompt where is jakarta? .*--max-tokens 2 .*--quick .*--profile .*--summary-file $SUMMARY_DIR/safetensor-fast.tsv .*--require-profile .*--require-metal .*--require-metal-paths .*--max-top-stage-ms 20 .*--max-prefill-ms 19 .*--max-decode-ms 21 .*--max-tpot-ms 22 .*--max-attention-ms 23 .*--max-ffn-ms 24 .*--max-logits-ms 25$" "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected safetensor aggregate gate invocation with profile, Metal, thresholds, and summary" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_SUMMARY="$TMP_DIR/gemma4-route-only-source.tsv"
write_gemma4_route_summary "$GEMMA4_ROUTE_ONLY_SUMMARY" \
    $'route\t8\tMTL0 (Apple M4)\ttrue\tn/a\tn/a\tn/a\t7\ttrue\tlitertlm\tfast-index-equivalent-litert\t/tmp/fake-gemma4-route.log'
GEMMA4_ROUTE_ONLY_DIR="$TMP_DIR/gemma4-route-only"
GEMMA4_ROUTE_ONLY_OUT="$TMP_DIR/gemma4-route-only.out"
GEMMA4_ROUTE_ONLY_CONFIG="$GEMMA4_ROUTE_ONLY_DIR/config.tsv"
GEMMA4_ROUTE_ONLY_ROLLUP="$GEMMA4_ROUTE_ONLY_DIR/rollup.tsv"
GEMMA4_ROUTE_ONLY_CONTRACTS="$GEMMA4_ROUTE_ONLY_DIR/contracts.tsv"
GEMMA4_ROUTE_ONLY_RESULTS="$GEMMA4_ROUTE_ONLY_DIR/results.tsv"
GEMMA4_ROUTE_ONLY_STATS="$GEMMA4_ROUTE_ONLY_DIR/route-stats.tsv"
GEMMA4_ROUTE_ONLY_ISSUES="$GEMMA4_ROUTE_ONLY_DIR/route-issues.tsv"
GEMMA4_ROUTE_ONLY_ISSUE_SUMMARY="$GEMMA4_ROUTE_ONLY_DIR/route-issue-summary.tsv"
run_gemma4_route_summary_gate "$GEMMA4_ROUTE_ONLY_SUMMARY" "$GEMMA4_ROUTE_ONLY_DIR" >"$GEMMA4_ROUTE_ONLY_OUT"
assert_gemma4_smoke_not_invoked "Gemma4 route-only verification"
assert_tsv_header_only "$GEMMA4_ROUTE_ONLY_ISSUES" "$ROUTE_ISSUES_HEADER" "passing Gemma4 route issue report"
assert_tsv_header_only "$GEMMA4_ROUTE_ONLY_ISSUE_SUMMARY" "$ROUTE_ISSUE_SUMMARY_HEADER" "passing Gemma4 route issue summary"
if ! grep -qx $'gemma4Litert.routeOnly\ttrue' "$GEMMA4_ROUTE_ONLY_CONFIG" \
        || ! grep -qx "gemma4Litert.routeSummaryFile	$GEMMA4_ROUTE_ONLY_SUMMARY" "$GEMMA4_ROUTE_ONLY_CONFIG" \
        || ! grep -qx "gemma4-litert	route	8	MTL0 (Apple M4)	warmEngine=true,engineInitMs=n/a,firstChunkMs=n/a,totalMs=n/a,routeResolveMs=7,routeCacheHit=true,selectedArtifact=litertlm,routeSource=fast-index-equivalent-litert	/tmp/fake-gemma4-route.log" "$GEMMA4_ROUTE_ONLY_ROLLUP" \
        || ! grep -qx "gemma4-litert	route	1	7.000	7.000	7.000	fast-index-equivalent-litert" "$GEMMA4_ROUTE_ONLY_STATS" \
        || ! grep -qx "gemma4-litert	route	false	true	n/a	false	n/a	n/a	false	n/a	n/a	false	true	n/a	false	n/a	n/a	false	n/a	n/a	true	true	pass	true	litertlm	pass	7	fast-index-equivalent-litert	/tmp/fake-gemma4-route.log	fast-index-equivalent-litert	pass	250	pass" "$GEMMA4_ROUTE_ONLY_CONTRACTS" \
        || ! grep -q "^gemma4-litert	pass	0	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_DIR/gemma4-litert.argv.tsv	$" "$GEMMA4_ROUTE_ONLY_RESULTS" \
        || ! grep -qx "artifacts.routeIssues=$GEMMA4_ROUTE_ONLY_ISSUES" "$GEMMA4_ROUTE_ONLY_OUT" \
        || ! grep -qx "artifacts.routeIssueSummary=$GEMMA4_ROUTE_ONLY_ISSUE_SUMMARY" "$GEMMA4_ROUTE_ONLY_OUT" \
        || ! grep -qx '  gemma4-litert/route metal=n/a metalPath=n/a fallbackPath=n/a warm=n/a promptCache=n/a javaRefusal=n/a routeCache=pass selectedArtifact=pass routeResolveMs=7 routeSource=fast-index-equivalent-litert routeSourceStatus=pass routeResolveBudgetMs=250 routeResolveStatus=pass log=/tmp/fake-gemma4-route.log' "$GEMMA4_ROUTE_ONLY_OUT" \
        || ! grep -qx '  gemma4-litert/route count=1 min=7.000ms avg=7.000ms max=7.000ms routeSources=fast-index-equivalent-litert' "$GEMMA4_ROUTE_ONLY_OUT" \
        || grep -qx 'routeIssues:' "$GEMMA4_ROUTE_ONLY_OUT" \
        || grep -qx 'routeIssueSummary:' "$GEMMA4_ROUTE_ONLY_OUT"; then
    echo "Expected Gemma4 route-only summary to flow through the standard contract artifacts" >&2
    cat "$GEMMA4_ROUTE_ONLY_CONFIG" >&2
    cat "$GEMMA4_ROUTE_ONLY_ROLLUP" >&2
    cat "$GEMMA4_ROUTE_ONLY_STATS" >&2
    cat "$GEMMA4_ROUTE_ONLY_ISSUES" >&2
    cat "$GEMMA4_ROUTE_ONLY_ISSUE_SUMMARY" >&2
    cat "$GEMMA4_ROUTE_ONLY_CONTRACTS" >&2
    cat "$GEMMA4_ROUTE_ONLY_RESULTS" >&2
    cat "$GEMMA4_ROUTE_ONLY_OUT" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_REGRESSION_BASELINE="$TMP_DIR/gemma4-route-regression-baseline.tsv"
{
    printf '%s\n' "$ROUTE_STATS_HEADER"
    printf 'gemma4-litert\troute\t1\t5.000\t5.000\t5.000\tfast-index-equivalent-litert\n'
} > "$GEMMA4_ROUTE_REGRESSION_BASELINE"
GEMMA4_ROUTE_REGRESSION_DIR="$TMP_DIR/gemma4-route-regression"
set +e
bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
    --only gemma4-litert \
    --summary-dir "$GEMMA4_ROUTE_REGRESSION_DIR" \
    --aqli-bin "$AQLI_BIN" \
    --gemma4-litert-route-summary "$GEMMA4_ROUTE_ONLY_SUMMARY" \
    --gemma4-litert-route-baseline-stats "$GEMMA4_ROUTE_REGRESSION_BASELINE" \
    --gemma4-litert-route-max-regression-ms 1 \
    --no-require-metal >"$TMP_DIR/gemma4-route-regression.out" 2>"$TMP_DIR/gemma4-route-regression.err"
gemma4_route_regression_exit=$?
set -e
if [[ "$gemma4_route_regression_exit" -ne 1 ]]; then
    echo "Expected Gemma4 route regression guard to fail when current route resolution exceeds baseline" >&2
    cat "$TMP_DIR/gemma4-route-regression.out" >&2
    cat "$TMP_DIR/gemma4-route-regression.err" >&2
    exit 1
fi
assert_gemma4_smoke_not_invoked "Gemma4 route regression summary verification"
if ! grep -qx "gemma4Litert.routeBaselineStats	$GEMMA4_ROUTE_REGRESSION_BASELINE" "$GEMMA4_ROUTE_REGRESSION_DIR/config.tsv" \
        || ! grep -qx $'gemma4Litert.routeMaxRegressionMs\t1' "$GEMMA4_ROUTE_REGRESSION_DIR/config.tsv" \
        || ! grep -qx $'gate\tcase\tbaselineAvgRouteResolveMs\tcurrentAvgRouteResolveMs\tdeltaMs\tdeltaPercent\tstatus\treason' "$GEMMA4_ROUTE_REGRESSION_DIR/route-regression.tsv" \
        || ! grep -qx $'gemma4-litert\troute\t5.000\t7.000\t2.000\t40.000\tfail\tdeltaMs 2.000 > 1.000' "$GEMMA4_ROUTE_REGRESSION_DIR/route-regression.tsv" \
        || ! grep -q "^gemma4-litert	fail	1	[0-9][0-9]*	$GEMMA4_ROUTE_REGRESSION_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_REGRESSION_DIR/gemma4-litert.out	$GEMMA4_ROUTE_REGRESSION_DIR/gemma4-litert.err	$GEMMA4_ROUTE_REGRESSION_DIR/gemma4-litert.argv.tsv	route-regression$" "$GEMMA4_ROUTE_REGRESSION_DIR/results.tsv" \
        || ! grep -qx '  gemma4-litert/route baselineAvg=5.000ms currentAvg=7.000ms delta=2.000ms deltaPercent=40.000 status=fail reason=deltaMs 2.000 > 1.000' "$TMP_DIR/gemma4-route-regression.out" \
        || ! grep -q "FAIL: gemma4-litert speed gate failed (route-regression);" "$TMP_DIR/gemma4-route-regression.err"; then
    echo "Expected Gemma4 route regression artifacts to capture baseline/current delta" >&2
    cat "$GEMMA4_ROUTE_REGRESSION_DIR/config.tsv" >&2
    cat "$GEMMA4_ROUTE_REGRESSION_DIR/route-stats.tsv" >&2
    cat "$GEMMA4_ROUTE_REGRESSION_DIR/route-regression.tsv" >&2
    cat "$GEMMA4_ROUTE_REGRESSION_DIR/results.tsv" >&2
    cat "$TMP_DIR/gemma4-route-regression.out" >&2
    cat "$TMP_DIR/gemma4-route-regression.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_STATS_SUMMARY="$TMP_DIR/gemma4-route-only-stats-source.tsv"
write_gemma4_route_summary "$GEMMA4_ROUTE_ONLY_STATS_SUMMARY" \
    $'route\t8\tMTL0 (Apple M4)\ttrue\tn/a\tn/a\tn/a\t7\ttrue\tlitertlm\tfast-index-equivalent-litert\t/tmp/fake-gemma4-route-a.log' \
    $'route\t9\tMTL0 (Apple M4)\ttrue\tn/a\tn/a\tn/a\t13\ttrue\tlitertlm\tfast-index-equivalent-litert\t/tmp/fake-gemma4-route-b.log'
GEMMA4_ROUTE_ONLY_STATS_DIR="$TMP_DIR/gemma4-route-only-stats"
run_gemma4_route_summary_gate "$GEMMA4_ROUTE_ONLY_STATS_SUMMARY" "$GEMMA4_ROUTE_ONLY_STATS_DIR" >"$TMP_DIR/gemma4-route-only-stats.out"
assert_gemma4_smoke_not_invoked "Gemma4 route stats summary verification"
if ! grep -qx "gemma4-litert	route	2	7.000	10.000	13.000	fast-index-equivalent-litert" "$GEMMA4_ROUTE_ONLY_STATS_DIR/route-stats.tsv" \
        || ! grep -qx '  gemma4-litert/route count=2 min=7.000ms avg=10.000ms max=13.000ms routeSources=fast-index-equivalent-litert' "$TMP_DIR/gemma4-route-only-stats.out" \
        || ! grep -q "^gemma4-litert	pass	0	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_STATS_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_STATS_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_STATS_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_STATS_DIR/gemma4-litert.argv.tsv	$" "$GEMMA4_ROUTE_ONLY_STATS_DIR/results.tsv"; then
    echo "Expected Gemma4 route stats to aggregate repeated cases and de-duplicate route sources" >&2
    cat "$GEMMA4_ROUTE_ONLY_STATS_DIR/route-stats.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_STATS_DIR/results.tsv" >&2
    cat "$TMP_DIR/gemma4-route-only-stats.out" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_BAD_SCHEMA_SUMMARY="$TMP_DIR/gemma4-route-only-bad-schema-source.tsv"
printf 'case\tdurationMs\tbackend\trouteResolveMs\trouteCacheHit\tselectedArtifact\tlog\n' > "$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_SUMMARY"
printf 'route\t8\tMTL0 (Apple M4)\t7\ttrue\tlitertlm\t/tmp/fake-gemma4-route.log\n' >> "$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_SUMMARY"
GEMMA4_ROUTE_ONLY_BAD_SCHEMA_DIR="$TMP_DIR/gemma4-route-only-bad-schema"
set +e
run_gemma4_route_summary_gate "$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_SUMMARY" "$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_DIR" >"$TMP_DIR/gemma4-route-only-bad-schema.out" 2>"$TMP_DIR/gemma4-route-only-bad-schema.err"
gemma4_route_only_bad_schema_exit=$?
set -e
if [[ "$gemma4_route_only_bad_schema_exit" -ne 2 ]]; then
    echo "Expected malformed Gemma4 route-only summary to fail before contract validation" >&2
    cat "$TMP_DIR/gemma4-route-only-bad-schema.out" >&2
    cat "$TMP_DIR/gemma4-route-only-bad-schema.err" >&2
    exit 1
fi
assert_gemma4_smoke_not_invoked "malformed Gemma4 route-only summary"
if ! grep -q "^gemma4-litert	fail	2	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_DIR/gemma4-litert.argv.tsv	summary-schema:missing-columns:routeSource$" "$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_DIR/results.tsv" \
        || ! grep -qx $'schemaError\tmissing-columns:routeSource' "$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_DIR/gemma4-litert.err" \
        || [[ -s "$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_DIR/rollup.tsv" && "$(wc -l < "$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_DIR/rollup.tsv")" -ne 1 ]]; then
    echo "Expected malformed Gemma4 route-only summary to produce a schema failure without rollup rows" >&2
    cat "$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_DIR/results.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_DIR/gemma4-litert.err" >&2
    cat "$GEMMA4_ROUTE_ONLY_BAD_SCHEMA_DIR/rollup.tsv" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_SUMMARY="$TMP_DIR/gemma4-route-only-multi-bad-schema-source.tsv"
printf 'case\tdurationMs\tbackend\tselectedArtifact\tlog\n' > "$GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_SUMMARY"
printf 'route\t8\tMTL0 (Apple M4)\tlitertlm\t/tmp/fake-gemma4-route.log\n' >> "$GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_SUMMARY"
GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_DIR="$TMP_DIR/gemma4-route-only-multi-bad-schema"
set +e
run_gemma4_route_summary_gate "$GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_SUMMARY" "$GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_DIR" >"$TMP_DIR/gemma4-route-only-multi-bad-schema.out" 2>"$TMP_DIR/gemma4-route-only-multi-bad-schema.err"
gemma4_route_only_multi_bad_schema_exit=$?
set -e
if [[ "$gemma4_route_only_multi_bad_schema_exit" -ne 2 ]]; then
    echo "Expected multi-missing Gemma4 route-only summary schema failure" >&2
    cat "$TMP_DIR/gemma4-route-only-multi-bad-schema.out" >&2
    cat "$TMP_DIR/gemma4-route-only-multi-bad-schema.err" >&2
    exit 1
fi
if ! grep -q "^gemma4-litert	fail	2	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_DIR/gemma4-litert.argv.tsv	summary-schema:missing-columns:routeResolveMs+routeCacheHit+routeSource$" "$GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_DIR/results.tsv" \
        || ! grep -qx $'schemaError\tmissing-columns:routeResolveMs+routeCacheHit+routeSource' "$GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_DIR/gemma4-litert.err"; then
    echo "Expected multi-missing Gemma4 route-only schema reason to be deterministic" >&2
    cat "$GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_DIR/results.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_MULTI_BAD_SCHEMA_DIR/gemma4-litert.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_MISSING_SUMMARY="$TMP_DIR/gemma4-route-only-missing-source.tsv"
GEMMA4_ROUTE_ONLY_MISSING_DIR="$TMP_DIR/gemma4-route-only-missing"
set +e
run_gemma4_route_summary_gate "$GEMMA4_ROUTE_ONLY_MISSING_SUMMARY" "$GEMMA4_ROUTE_ONLY_MISSING_DIR" >"$TMP_DIR/gemma4-route-only-missing.out" 2>"$TMP_DIR/gemma4-route-only-missing.err"
gemma4_route_only_missing_exit=$?
set -e
if [[ "$gemma4_route_only_missing_exit" -ne 2 ]]; then
    echo "Expected missing Gemma4 route summary to fail schema validation" >&2
    cat "$TMP_DIR/gemma4-route-only-missing.out" >&2
    cat "$TMP_DIR/gemma4-route-only-missing.err" >&2
    exit 1
fi
assert_gemma4_smoke_not_invoked "missing Gemma4 route summary"
if ! grep -q "^gemma4-litert	fail	2	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_MISSING_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_MISSING_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_MISSING_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_MISSING_DIR/gemma4-litert.argv.tsv	summary-schema:missing-file$" "$GEMMA4_ROUTE_ONLY_MISSING_DIR/results.tsv" \
        || ! grep -qx $'schemaError\tmissing-file' "$GEMMA4_ROUTE_ONLY_MISSING_DIR/gemma4-litert.err" \
        || [[ -s "$GEMMA4_ROUTE_ONLY_MISSING_DIR/rollup.tsv" && "$(wc -l < "$GEMMA4_ROUTE_ONLY_MISSING_DIR/rollup.tsv")" -ne 1 ]]; then
    echo "Expected missing Gemma4 route summary to produce a clean schema failure without awk noise" >&2
    cat "$GEMMA4_ROUTE_ONLY_MISSING_DIR/results.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_MISSING_DIR/gemma4-litert.err" >&2
    cat "$GEMMA4_ROUTE_ONLY_MISSING_DIR/rollup.tsv" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_NOT_FILE_SUMMARY="$TMP_DIR"
GEMMA4_ROUTE_ONLY_NOT_FILE_DIR="$TMP_DIR/gemma4-route-only-not-file"
set +e
run_gemma4_route_summary_gate "$GEMMA4_ROUTE_ONLY_NOT_FILE_SUMMARY" "$GEMMA4_ROUTE_ONLY_NOT_FILE_DIR" >"$TMP_DIR/gemma4-route-only-not-file.out" 2>"$TMP_DIR/gemma4-route-only-not-file.err"
gemma4_route_only_not_file_exit=$?
set -e
if [[ "$gemma4_route_only_not_file_exit" -ne 2 ]]; then
    echo "Expected directory Gemma4 route summary path to fail schema validation" >&2
    cat "$TMP_DIR/gemma4-route-only-not-file.out" >&2
    cat "$TMP_DIR/gemma4-route-only-not-file.err" >&2
    exit 1
fi
assert_gemma4_smoke_not_invoked "directory Gemma4 route summary"
if ! grep -q "^gemma4-litert	fail	2	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_NOT_FILE_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_NOT_FILE_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_NOT_FILE_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_NOT_FILE_DIR/gemma4-litert.argv.tsv	summary-schema:not-file$" "$GEMMA4_ROUTE_ONLY_NOT_FILE_DIR/results.tsv" \
        || ! grep -qx $'schemaError\tnot-file' "$GEMMA4_ROUTE_ONLY_NOT_FILE_DIR/gemma4-litert.err"; then
    echo "Expected directory Gemma4 route summary to produce a not-file schema failure" >&2
    cat "$GEMMA4_ROUTE_ONLY_NOT_FILE_DIR/results.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_NOT_FILE_DIR/gemma4-litert.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_EMPTY_SUMMARY="$TMP_DIR/gemma4-route-only-empty-source.tsv"
: > "$GEMMA4_ROUTE_ONLY_EMPTY_SUMMARY"
GEMMA4_ROUTE_ONLY_EMPTY_DIR="$TMP_DIR/gemma4-route-only-empty"
set +e
run_gemma4_route_summary_gate "$GEMMA4_ROUTE_ONLY_EMPTY_SUMMARY" "$GEMMA4_ROUTE_ONLY_EMPTY_DIR" >"$TMP_DIR/gemma4-route-only-empty.out" 2>"$TMP_DIR/gemma4-route-only-empty.err"
gemma4_route_only_empty_exit=$?
set -e
if [[ "$gemma4_route_only_empty_exit" -ne 2 ]]; then
    echo "Expected empty Gemma4 route summary file to fail schema validation" >&2
    cat "$TMP_DIR/gemma4-route-only-empty.out" >&2
    cat "$TMP_DIR/gemma4-route-only-empty.err" >&2
    exit 1
fi
assert_gemma4_smoke_not_invoked "empty Gemma4 route summary"
if ! grep -q "^gemma4-litert	fail	2	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_EMPTY_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_EMPTY_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_EMPTY_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_EMPTY_DIR/gemma4-litert.argv.tsv	summary-schema:empty$" "$GEMMA4_ROUTE_ONLY_EMPTY_DIR/results.tsv" \
        || ! grep -qx $'schemaError\tempty' "$GEMMA4_ROUTE_ONLY_EMPTY_DIR/gemma4-litert.err"; then
    echo "Expected empty Gemma4 route summary file to produce an empty schema failure" >&2
    cat "$GEMMA4_ROUTE_ONLY_EMPTY_DIR/results.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_EMPTY_DIR/gemma4-litert.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_SUMMARY="$TMP_DIR/gemma4-route-only-log-not-last-source.tsv"
printf 'case\tdurationMs\tbackend\trouteResolveMs\trouteCacheHit\tselectedArtifact\tlog\trouteSource\n' > "$GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_SUMMARY"
printf 'route\t8\tMTL0 (Apple M4)\t7\ttrue\tlitertlm\t/tmp/fake-gemma4-route.log\tfast-index-equivalent-litert\n' >> "$GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_SUMMARY"
GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_DIR="$TMP_DIR/gemma4-route-only-log-not-last"
set +e
run_gemma4_route_summary_gate "$GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_SUMMARY" "$GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_DIR" >"$TMP_DIR/gemma4-route-only-log-not-last.out" 2>"$TMP_DIR/gemma4-route-only-log-not-last.err"
gemma4_route_only_log_not_last_exit=$?
set -e
if [[ "$gemma4_route_only_log_not_last_exit" -ne 2 ]]; then
    echo "Expected Gemma4 route summary with trailing columns after log to fail schema validation" >&2
    cat "$TMP_DIR/gemma4-route-only-log-not-last.out" >&2
    cat "$TMP_DIR/gemma4-route-only-log-not-last.err" >&2
    exit 1
fi
assert_gemma4_smoke_not_invoked "Gemma4 route summary with log not last"
if ! grep -q "^gemma4-litert	fail	2	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_DIR/gemma4-litert.argv.tsv	summary-schema:missing-columns:log-last$" "$GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_DIR/results.tsv" \
        || ! grep -qx $'schemaError\tmissing-columns:log-last' "$GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_DIR/gemma4-litert.err"; then
    echo "Expected Gemma4 route summary with log not last to produce deterministic schema failure" >&2
    cat "$GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_DIR/results.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_LOG_NOT_LAST_DIR/gemma4-litert.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_NO_DATA_SUMMARY="$TMP_DIR/gemma4-route-only-no-data-source.tsv"
write_gemma4_route_summary "$GEMMA4_ROUTE_ONLY_NO_DATA_SUMMARY"
GEMMA4_ROUTE_ONLY_NO_DATA_DIR="$TMP_DIR/gemma4-route-only-no-data"
set +e
run_gemma4_route_summary_gate "$GEMMA4_ROUTE_ONLY_NO_DATA_SUMMARY" "$GEMMA4_ROUTE_ONLY_NO_DATA_DIR" >"$TMP_DIR/gemma4-route-only-no-data.out" 2>"$TMP_DIR/gemma4-route-only-no-data.err"
gemma4_route_only_no_data_exit=$?
set -e
if [[ "$gemma4_route_only_no_data_exit" -ne 2 ]]; then
    echo "Expected header-only Gemma4 route summary to fail schema validation" >&2
    cat "$TMP_DIR/gemma4-route-only-no-data.out" >&2
    cat "$TMP_DIR/gemma4-route-only-no-data.err" >&2
    exit 1
fi
assert_gemma4_smoke_not_invoked "header-only Gemma4 route summary"
if ! grep -q "^gemma4-litert	fail	2	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_NO_DATA_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_NO_DATA_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_NO_DATA_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_NO_DATA_DIR/gemma4-litert.argv.tsv	summary-schema:no-data$" "$GEMMA4_ROUTE_ONLY_NO_DATA_DIR/results.tsv" \
        || ! grep -qx $'schemaError\tno-data' "$GEMMA4_ROUTE_ONLY_NO_DATA_DIR/gemma4-litert.err"; then
    echo "Expected header-only Gemma4 route summary to produce a no-data schema failure" >&2
    cat "$GEMMA4_ROUTE_ONLY_NO_DATA_DIR/results.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_NO_DATA_DIR/gemma4-litert.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_EMPTY_FIELD_SUMMARY="$TMP_DIR/gemma4-route-only-empty-field-source.tsv"
write_gemma4_route_summary "$GEMMA4_ROUTE_ONLY_EMPTY_FIELD_SUMMARY" \
    $'route\t8\tMTL0 (Apple M4)\ttrue\tn/a\tn/a\tn/a\t\ttrue\tlitertlm\t\t/tmp/fake-gemma4-route.log'
GEMMA4_ROUTE_ONLY_EMPTY_FIELD_DIR="$TMP_DIR/gemma4-route-only-empty-field"
set +e
run_gemma4_route_summary_gate "$GEMMA4_ROUTE_ONLY_EMPTY_FIELD_SUMMARY" "$GEMMA4_ROUTE_ONLY_EMPTY_FIELD_DIR" >"$TMP_DIR/gemma4-route-only-empty-field.out" 2>"$TMP_DIR/gemma4-route-only-empty-field.err"
gemma4_route_only_empty_field_exit=$?
set -e
if [[ "$gemma4_route_only_empty_field_exit" -ne 2 ]]; then
    echo "Expected blank required Gemma4 route fields to fail schema validation" >&2
    cat "$TMP_DIR/gemma4-route-only-empty-field.out" >&2
    cat "$TMP_DIR/gemma4-route-only-empty-field.err" >&2
    exit 1
fi
assert_gemma4_smoke_not_invoked "blank required Gemma4 route fields"
if ! grep -q "^gemma4-litert	fail	2	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_EMPTY_FIELD_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_EMPTY_FIELD_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_EMPTY_FIELD_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_EMPTY_FIELD_DIR/gemma4-litert.argv.tsv	summary-schema:empty-fields:line2:routeResolveMs+routeSource$" "$GEMMA4_ROUTE_ONLY_EMPTY_FIELD_DIR/results.tsv" \
        || ! grep -qx $'schemaError\tempty-fields:line2:routeResolveMs+routeSource' "$GEMMA4_ROUTE_ONLY_EMPTY_FIELD_DIR/gemma4-litert.err"; then
    echo "Expected blank required Gemma4 route fields to produce a deterministic schema failure" >&2
    cat "$GEMMA4_ROUTE_ONLY_EMPTY_FIELD_DIR/results.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_EMPTY_FIELD_DIR/gemma4-litert.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_INVALID_VALUE_SUMMARY="$TMP_DIR/gemma4-route-only-invalid-value-source.tsv"
write_gemma4_route_summary "$GEMMA4_ROUTE_ONLY_INVALID_VALUE_SUMMARY" \
    $'route\tslow\tMTL0 (Apple M4)\ttrue\tn/a\tn/a\tn/a\tNaN\tyes\tsafetensor\tfast-index-equivalent-litert\t/tmp/fake-gemma4-route.log'
GEMMA4_ROUTE_ONLY_INVALID_VALUE_DIR="$TMP_DIR/gemma4-route-only-invalid-value"
set +e
run_gemma4_route_summary_gate "$GEMMA4_ROUTE_ONLY_INVALID_VALUE_SUMMARY" "$GEMMA4_ROUTE_ONLY_INVALID_VALUE_DIR" >"$TMP_DIR/gemma4-route-only-invalid-value.out" 2>"$TMP_DIR/gemma4-route-only-invalid-value.err"
gemma4_route_only_invalid_value_exit=$?
set -e
if [[ "$gemma4_route_only_invalid_value_exit" -ne 2 ]]; then
    echo "Expected invalid Gemma4 route values to fail schema validation" >&2
    cat "$TMP_DIR/gemma4-route-only-invalid-value.out" >&2
    cat "$TMP_DIR/gemma4-route-only-invalid-value.err" >&2
    exit 1
fi
assert_gemma4_smoke_not_invoked "invalid Gemma4 route values"
if ! grep -q "^gemma4-litert	fail	2	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_INVALID_VALUE_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_INVALID_VALUE_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_INVALID_VALUE_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_INVALID_VALUE_DIR/gemma4-litert.argv.tsv	summary-schema:invalid-fields:line2:durationMs+routeResolveMs+routeCacheHit+selectedArtifact$" "$GEMMA4_ROUTE_ONLY_INVALID_VALUE_DIR/results.tsv" \
        || ! grep -qx $'schemaError\tinvalid-fields:line2:durationMs+routeResolveMs+routeCacheHit+selectedArtifact' "$GEMMA4_ROUTE_ONLY_INVALID_VALUE_DIR/gemma4-litert.err"; then
    echo "Expected invalid Gemma4 route values to produce a deterministic schema failure" >&2
    cat "$GEMMA4_ROUTE_ONLY_INVALID_VALUE_DIR/results.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_INVALID_VALUE_DIR/gemma4-litert.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_PLACEHOLDER_SOURCE_SUMMARY="$TMP_DIR/gemma4-route-only-placeholder-source.tsv"
write_gemma4_route_summary "$GEMMA4_ROUTE_ONLY_PLACEHOLDER_SOURCE_SUMMARY" \
    $'route\t8\tMTL0 (Apple M4)\ttrue\tn/a\tn/a\tn/a\t7\ttrue\tlitertlm\tn/a\t/tmp/fake-gemma4-route.log'
GEMMA4_ROUTE_ONLY_PLACEHOLDER_SOURCE_DIR="$TMP_DIR/gemma4-route-only-placeholder-source"
set +e
run_gemma4_route_summary_gate "$GEMMA4_ROUTE_ONLY_PLACEHOLDER_SOURCE_SUMMARY" "$GEMMA4_ROUTE_ONLY_PLACEHOLDER_SOURCE_DIR" >"$TMP_DIR/gemma4-route-only-placeholder-source.out" 2>"$TMP_DIR/gemma4-route-only-placeholder-source.err"
gemma4_route_only_placeholder_source_exit=$?
set -e
if [[ "$gemma4_route_only_placeholder_source_exit" -ne 2 ]]; then
    echo "Expected placeholder Gemma4 route source to fail schema validation" >&2
    cat "$TMP_DIR/gemma4-route-only-placeholder-source.out" >&2
    cat "$TMP_DIR/gemma4-route-only-placeholder-source.err" >&2
    exit 1
fi
assert_gemma4_smoke_not_invoked "placeholder Gemma4 route source"
if ! grep -q "^gemma4-litert	fail	2	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_PLACEHOLDER_SOURCE_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_PLACEHOLDER_SOURCE_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_PLACEHOLDER_SOURCE_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_PLACEHOLDER_SOURCE_DIR/gemma4-litert.argv.tsv	summary-schema:invalid-fields:line2:routeSource$" "$GEMMA4_ROUTE_ONLY_PLACEHOLDER_SOURCE_DIR/results.tsv" \
        || ! grep -qx $'schemaError\tinvalid-fields:line2:routeSource' "$GEMMA4_ROUTE_ONLY_PLACEHOLDER_SOURCE_DIR/gemma4-litert.err"; then
    echo "Expected placeholder Gemma4 route source to produce a deterministic schema failure" >&2
    cat "$GEMMA4_ROUTE_ONLY_PLACEHOLDER_SOURCE_DIR/results.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_PLACEHOLDER_SOURCE_DIR/gemma4-litert.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_FAIL_SUMMARY="$TMP_DIR/gemma4-route-only-fail-source.tsv"
write_gemma4_route_summary "$GEMMA4_ROUTE_ONLY_FAIL_SUMMARY" \
    $'route\t9\tMTL0 (Apple M4)\ttrue\tn/a\tn/a\tn/a\t900\ttrue\tlitertlm\tmanual-litert\t/tmp/fake-gemma4-route-fail.log'
GEMMA4_ROUTE_ONLY_FAIL_DIR="$TMP_DIR/gemma4-route-only-fail"
set +e
run_gemma4_route_summary_gate "$GEMMA4_ROUTE_ONLY_FAIL_SUMMARY" "$GEMMA4_ROUTE_ONLY_FAIL_DIR" >"$TMP_DIR/gemma4-route-only-fail.out" 2>"$TMP_DIR/gemma4-route-only-fail.err"
gemma4_route_only_fail_exit=$?
set -e
if [[ "$gemma4_route_only_fail_exit" -ne 1 ]]; then
    echo "Expected Gemma4 route-only contract failure to fail the gate" >&2
    cat "$TMP_DIR/gemma4-route-only-fail.out" >&2
    cat "$TMP_DIR/gemma4-route-only-fail.err" >&2
    exit 1
fi
assert_gemma4_smoke_not_invoked "failing Gemma4 route-only verification"
if ! grep -q "^gemma4-litert	fail	1	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_FAIL_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_FAIL_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_FAIL_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_FAIL_DIR/gemma4-litert.argv.tsv	contract:routeSourceStatus:route:manual-litert->fast-index-equivalent-litert,routeResolveStatus:route:900/250ms$" "$GEMMA4_ROUTE_ONLY_FAIL_DIR/results.tsv" \
        || ! grep -qx "$ROUTE_ISSUES_HEADER" "$GEMMA4_ROUTE_ONLY_FAIL_DIR/route-issues.tsv" \
        || ! grep -qx $'gemma4-litert\troute\trouteSource\tmanual-litert\tfast-index-equivalent-litert\tn/a\t/tmp/fake-gemma4-route-fail.log' "$GEMMA4_ROUTE_ONLY_FAIL_DIR/route-issues.tsv" \
        || ! grep -qx $'gemma4-litert\troute\trouteResolve\t900\tlte\t250\t/tmp/fake-gemma4-route-fail.log' "$GEMMA4_ROUTE_ONLY_FAIL_DIR/route-issues.tsv" \
        || ! grep -qx '  gemma4-litert/route issue=routeSource actual=manual-litert expected=fast-index-equivalent-litert budgetMs=n/a log=/tmp/fake-gemma4-route-fail.log' "$TMP_DIR/gemma4-route-only-fail.out" \
        || ! grep -qx '  gemma4-litert/route issue=routeResolve actual=900 expected=lte budgetMs=250 log=/tmp/fake-gemma4-route-fail.log' "$TMP_DIR/gemma4-route-only-fail.out" \
        || ! grep -q "FAIL: gemma4-litert speed gate failed (contract:routeSourceStatus:route:manual-litert->fast-index-equivalent-litert,routeResolveStatus:route:900/250ms);" "$TMP_DIR/gemma4-route-only-fail.err"; then
    echo "Expected Gemma4 route-only failures to keep decorated route diagnostics" >&2
    cat "$GEMMA4_ROUTE_ONLY_FAIL_DIR/results.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_FAIL_DIR/route-issues.tsv" >&2
    cat "$TMP_DIR/gemma4-route-only-fail.out" >&2
    cat "$TMP_DIR/gemma4-route-only-fail.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_ONLY_SOURCE_DISABLED_DIR="$TMP_DIR/gemma4-route-only-source-disabled"
set +e
AQLI_VERIFY_GEMMA4_LITERT_ROUTE_SOURCE= \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only gemma4-litert \
        --summary-dir "$GEMMA4_ROUTE_ONLY_SOURCE_DISABLED_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --gemma4-litert-route-summary "$GEMMA4_ROUTE_ONLY_FAIL_SUMMARY" \
        --no-require-metal >"$TMP_DIR/gemma4-route-only-source-disabled.out" 2>"$TMP_DIR/gemma4-route-only-source-disabled.err"
gemma4_route_only_source_disabled_exit=$?
set -e
if [[ "$gemma4_route_only_source_disabled_exit" -ne 1 ]]; then
    echo "Expected disabled Gemma4 route-source contract to still fail route resolution budget" >&2
    cat "$TMP_DIR/gemma4-route-only-source-disabled.out" >&2
    cat "$TMP_DIR/gemma4-route-only-source-disabled.err" >&2
    exit 1
fi
if ! grep -qx $'gemma4Litert.routeSource\t' "$GEMMA4_ROUTE_ONLY_SOURCE_DISABLED_DIR/config.tsv" \
        || ! grep -qx "gemma4-litert	route	false	true	n/a	false	n/a	n/a	false	n/a	n/a	false	true	n/a	false	n/a	n/a	false	n/a	n/a	true	true	pass	true	litertlm	pass	900	manual-litert	/tmp/fake-gemma4-route-fail.log	n/a	n/a	250	fail" "$GEMMA4_ROUTE_ONLY_SOURCE_DISABLED_DIR/contracts.tsv" \
        || ! grep -q "^gemma4-litert	fail	1	[0-9][0-9]*	$GEMMA4_ROUTE_ONLY_SOURCE_DISABLED_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_ONLY_SOURCE_DISABLED_DIR/gemma4-litert.out	$GEMMA4_ROUTE_ONLY_SOURCE_DISABLED_DIR/gemma4-litert.err	$GEMMA4_ROUTE_ONLY_SOURCE_DISABLED_DIR/gemma4-litert.argv.tsv	contract:routeResolveStatus:route:900/250ms$" "$GEMMA4_ROUTE_ONLY_SOURCE_DISABLED_DIR/results.tsv" \
        || ! grep -q "FAIL: gemma4-litert speed gate failed (contract:routeResolveStatus:route:900/250ms);" "$TMP_DIR/gemma4-route-only-source-disabled.err"; then
    echo "Expected empty Gemma4 route-source env to disable only the route-source contract" >&2
    cat "$GEMMA4_ROUTE_ONLY_SOURCE_DISABLED_DIR/config.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_SOURCE_DISABLED_DIR/contracts.tsv" >&2
    cat "$GEMMA4_ROUTE_ONLY_SOURCE_DISABLED_DIR/results.tsv" >&2
    cat "$TMP_DIR/gemma4-route-only-source-disabled.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_COLD_ROUTE_CONTRACT_DIR="$TMP_DIR/gemma4-cold-route-contract"
AQLI_FAKE_GEMMA4_CASE=cold \
AQLI_FAKE_GEMMA4_ROUTE_RESOLVE_MS=900 \
AQLI_VERIFY_FAST_GEMMA4_LITERT_SMOKE="$TMP_DIR/fake-gemma4-litert-smoke.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only gemma4-litert \
        --summary-dir "$GEMMA4_COLD_ROUTE_CONTRACT_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --no-require-metal >"$TMP_DIR/gemma4-cold-route-contract.out"
if ! grep -qx "gemma4-litert	cold	false	true	n/a	false	n/a	n/a	false	n/a	n/a	false	true	n/a	false	n/a	n/a	false	n/a	n/a	true	true	pass	true	litertlm	pass	900	fast-index-equivalent-litert	/tmp/fake-gemma4-litert.log	fast-index-equivalent-litert	pass	1000	pass" "$GEMMA4_COLD_ROUTE_CONTRACT_DIR/contracts.tsv" \
        || ! grep -qx '  gemma4-litert/cold metal=n/a metalPath=n/a fallbackPath=n/a warm=n/a promptCache=n/a javaRefusal=n/a routeCache=pass selectedArtifact=pass routeResolveMs=900 routeSource=fast-index-equivalent-litert routeSourceStatus=pass routeResolveBudgetMs=1000 routeResolveStatus=pass log=/tmp/fake-gemma4-litert.log' "$TMP_DIR/gemma4-cold-route-contract.out"; then
    echo "Expected cold Gemma4 route budget to allow slower cold resolution while keeping route source strict" >&2
    cat "$GEMMA4_COLD_ROUTE_CONTRACT_DIR/contracts.tsv" >&2
    cat "$TMP_DIR/gemma4-cold-route-contract.out" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_CONTRACT_DIR="$TMP_DIR/gemma4-route-contract"
set +e
AQLI_FAKE_GEMMA4_ROUTE_SOURCE=cached-gemma4-litert \
AQLI_FAKE_GEMMA4_ROUTE_RESOLVE_MS=900 \
AQLI_VERIFY_FAST_GEMMA4_LITERT_SMOKE="$TMP_DIR/fake-gemma4-litert-smoke.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only gemma4-litert \
        --summary-dir "$GEMMA4_ROUTE_CONTRACT_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --no-require-metal >"$TMP_DIR/gemma4-route-contract.out" 2>"$TMP_DIR/gemma4-route-contract.err"
gemma4_route_contract_exit=$?
set -e
if [[ "$gemma4_route_contract_exit" -ne 1 ]]; then
    echo "Expected Gemma4 route contract to fail when source or route resolution regresses" >&2
    cat "$TMP_DIR/gemma4-route-contract.out" >&2
    cat "$TMP_DIR/gemma4-route-contract.err" >&2
    exit 1
fi
if ! grep -qx "gemma4-litert	warm	false	true	n/a	false	n/a	n/a	false	n/a	n/a	true	true	pass	false	n/a	n/a	false	n/a	n/a	true	true	pass	true	litertlm	pass	900	cached-gemma4-litert	/tmp/fake-gemma4-litert.log	fast-index-equivalent-litert	fail	250	fail" "$GEMMA4_ROUTE_CONTRACT_DIR/contracts.tsv" \
        || ! grep -q "^gemma4-litert	fail	1	[0-9][0-9]*	$GEMMA4_ROUTE_CONTRACT_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_CONTRACT_DIR/gemma4-litert.out	$GEMMA4_ROUTE_CONTRACT_DIR/gemma4-litert.err	$GEMMA4_ROUTE_CONTRACT_DIR/gemma4-litert.argv.tsv	contract:routeSourceStatus:warm:cached-gemma4-litert->fast-index-equivalent-litert,routeResolveStatus:warm:900/250ms$" "$GEMMA4_ROUTE_CONTRACT_DIR/results.tsv" \
        || ! grep -qx '  gemma4-litert/warm metal=n/a metalPath=n/a fallbackPath=n/a warm=pass promptCache=n/a javaRefusal=n/a routeCache=pass selectedArtifact=pass routeResolveMs=900 routeSource=cached-gemma4-litert routeSourceStatus=fail routeResolveBudgetMs=250 routeResolveStatus=fail log=/tmp/fake-gemma4-litert.log' "$TMP_DIR/gemma4-route-contract.out" \
        || ! grep -q "FAIL: gemma4-litert speed gate failed (contract:routeSourceStatus:warm:cached-gemma4-litert->fast-index-equivalent-litert,routeResolveStatus:warm:900/250ms);" "$TMP_DIR/gemma4-route-contract.err"; then
    echo "Expected Gemma4 route contract failure to be captured in artifacts and terminal output" >&2
    cat "$GEMMA4_ROUTE_CONTRACT_DIR/contracts.tsv" >&2
    cat "$GEMMA4_ROUTE_CONTRACT_DIR/results.tsv" >&2
    cat "$TMP_DIR/gemma4-route-contract.out" >&2
    cat "$TMP_DIR/gemma4-route-contract.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR="$TMP_DIR/gemma4-route-cache-artifact-contract"
set +e
AQLI_FAKE_GEMMA4_ROUTE_CACHE_HIT=false \
AQLI_FAKE_GEMMA4_SELECTED_ARTIFACT=safetensor \
AQLI_VERIFY_FAST_GEMMA4_LITERT_SMOKE="$TMP_DIR/fake-gemma4-litert-smoke.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only gemma4-litert \
        --summary-dir "$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --no-require-metal >"$TMP_DIR/gemma4-route-cache-artifact-contract.out" 2>"$TMP_DIR/gemma4-route-cache-artifact-contract.err"
gemma4_route_cache_artifact_contract_exit=$?
set -e
if [[ "$gemma4_route_cache_artifact_contract_exit" -ne 1 ]]; then
    echo "Expected Gemma4 route contract to fail when cache or selected artifact regresses" >&2
    cat "$TMP_DIR/gemma4-route-cache-artifact-contract.out" >&2
    cat "$TMP_DIR/gemma4-route-cache-artifact-contract.err" >&2
    exit 1
fi
if ! grep -qx "gemma4-litert	warm	false	true	n/a	false	n/a	n/a	false	n/a	n/a	true	true	pass	false	n/a	n/a	false	n/a	n/a	true	false	fail	true	safetensor	fail	3	fast-index-equivalent-litert	/tmp/fake-gemma4-litert.log	fast-index-equivalent-litert	pass	250	pass" "$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/contracts.tsv" \
        || ! grep -q "^gemma4-litert	fail	1	[0-9][0-9]*	$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/gemma4-litert-warm.tsv	$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/gemma4-litert.out	$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/gemma4-litert.err	$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/gemma4-litert.argv.tsv	contract:routeCacheStatus:warm:false->true,selectedArtifactStatus:warm:safetensor->litertlm$" "$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/results.tsv" \
        || ! grep -qx $'gemma4-litert\twarm\trouteCache\tfalse\ttrue\tn/a\t/tmp/fake-gemma4-litert.log' "$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/route-issues.tsv" \
        || ! grep -qx $'gemma4-litert\twarm\tselectedArtifact\tsafetensor\tlitertlm\tn/a\t/tmp/fake-gemma4-litert.log' "$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/route-issues.tsv" \
        || ! grep -qx "$ROUTE_ISSUE_SUMMARY_HEADER" "$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/route-issue-summary.tsv" \
        || ! grep -qx $'routeCache\t1\twarm' "$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/route-issue-summary.tsv" \
        || ! grep -qx $'selectedArtifact\t1\twarm' "$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/route-issue-summary.tsv" \
        || ! grep -qx '  gemma4-litert/warm issue=routeCache actual=false expected=true budgetMs=n/a log=/tmp/fake-gemma4-litert.log' "$TMP_DIR/gemma4-route-cache-artifact-contract.out" \
        || ! grep -qx '  gemma4-litert/warm issue=selectedArtifact actual=safetensor expected=litertlm budgetMs=n/a log=/tmp/fake-gemma4-litert.log' "$TMP_DIR/gemma4-route-cache-artifact-contract.out" \
        || ! grep -qx '  issue=routeCache count=1 cases=warm' "$TMP_DIR/gemma4-route-cache-artifact-contract.out" \
        || ! grep -qx '  issue=selectedArtifact count=1 cases=warm' "$TMP_DIR/gemma4-route-cache-artifact-contract.out" \
        || ! grep -q "FAIL: gemma4-litert speed gate failed (contract:routeCacheStatus:warm:false->true,selectedArtifactStatus:warm:safetensor->litertlm);" "$TMP_DIR/gemma4-route-cache-artifact-contract.err"; then
    echo "Expected Gemma4 route cache/artifact failures to be captured in route issues" >&2
    cat "$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/contracts.tsv" >&2
    cat "$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/results.tsv" >&2
    cat "$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/route-issues.tsv" >&2
    cat "$GEMMA4_ROUTE_CACHE_ARTIFACT_CONTRACT_DIR/route-issue-summary.tsv" >&2
    cat "$TMP_DIR/gemma4-route-cache-artifact-contract.out" >&2
    cat "$TMP_DIR/gemma4-route-cache-artifact-contract.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
GEMMA4_MULTI_ROUTE_CONTRACT_DIR="$TMP_DIR/gemma4-multi-route-contract"
set +e
AQLI_FAKE_GEMMA4_CASE=cold \
AQLI_FAKE_GEMMA4_ROUTE_SOURCE=cached-gemma4-litert \
AQLI_FAKE_GEMMA4_ROUTE_RESOLVE_MS=1500 \
AQLI_FAKE_GEMMA4_SECOND_CASE=warm \
AQLI_FAKE_GEMMA4_SECOND_ROUTE_SOURCE=manual-litert \
AQLI_FAKE_GEMMA4_SECOND_ROUTE_RESOLVE_MS=900 \
AQLI_VERIFY_FAST_GEMMA4_LITERT_SMOKE="$TMP_DIR/fake-gemma4-litert-smoke.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only gemma4-litert \
        --summary-dir "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --no-require-metal >"$TMP_DIR/gemma4-multi-route-contract.out" 2>"$TMP_DIR/gemma4-multi-route-contract.err"
gemma4_multi_route_contract_exit=$?
set -e
if [[ "$gemma4_multi_route_contract_exit" -ne 1 ]]; then
    echo "Expected multi-row Gemma4 route contract to fail" >&2
    cat "$TMP_DIR/gemma4-multi-route-contract.out" >&2
    cat "$TMP_DIR/gemma4-multi-route-contract.err" >&2
    exit 1
fi
if ! grep -q "^gemma4-litert	fail	1	[0-9][0-9]*	$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/gemma4-litert-warm.tsv	$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/gemma4-litert.out	$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/gemma4-litert.err	$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/gemma4-litert.argv.tsv	contract:routeSourceStatus:cold:cached-gemma4-litert->fast-index-equivalent-litert+warm:manual-litert->fast-index-equivalent-litert,routeResolveStatus:cold:1500/1000ms+warm:900/250ms$" "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/results.tsv" \
        || ! grep -q "FAIL: gemma4-litert speed gate failed (contract:routeSourceStatus:cold:cached-gemma4-litert->fast-index-equivalent-litert+warm:manual-litert->fast-index-equivalent-litert,routeResolveStatus:cold:1500/1000ms+warm:900/250ms);" "$TMP_DIR/gemma4-multi-route-contract.err"; then
    echo "Expected multi-row Gemma4 route failure reason to include each failing case" >&2
    cat "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/results.tsv" >&2
    cat "$TMP_DIR/gemma4-multi-route-contract.err" >&2
    exit 1
fi
if ! grep -qx "$ROUTE_STATS_HEADER" "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-stats.tsv" \
        || ! grep -qx "gemma4-litert	cold	1	1500.000	1500.000	1500.000	cached-gemma4-litert" "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-stats.tsv" \
        || ! grep -qx "gemma4-litert	warm	1	900.000	900.000	900.000	manual-litert" "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-stats.tsv" \
        || [[ "$(sed -n '2p' "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-stats.tsv")" != "gemma4-litert	cold	1	1500.000	1500.000	1500.000	cached-gemma4-litert" ]] \
        || [[ "$(sed -n '3p' "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-stats.tsv")" != "gemma4-litert	warm	1	900.000	900.000	900.000	manual-litert" ]]; then
    echo "Expected multi-row Gemma4 route stats to preserve first-seen case ordering" >&2
    cat "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-stats.tsv" >&2
    exit 1
fi
if ! grep -qx "$ROUTE_ISSUES_HEADER" "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-issues.tsv" \
        || ! grep -qx $'gemma4-litert\tcold\trouteSource\tcached-gemma4-litert\tfast-index-equivalent-litert\tn/a\t/tmp/fake-gemma4-litert.log' "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-issues.tsv" \
        || ! grep -qx $'gemma4-litert\tcold\trouteResolve\t1500\tlte\t1000\t/tmp/fake-gemma4-litert.log' "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-issues.tsv" \
        || ! grep -qx $'gemma4-litert\twarm\trouteSource\tmanual-litert\tfast-index-equivalent-litert\tn/a\t/tmp/fake-gemma4-litert-second.log' "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-issues.tsv" \
        || ! grep -qx $'gemma4-litert\twarm\trouteResolve\t900\tlte\t250\t/tmp/fake-gemma4-litert-second.log' "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-issues.tsv" \
        || ! grep -qx "$ROUTE_ISSUE_SUMMARY_HEADER" "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-issue-summary.tsv" \
        || ! grep -qx $'routeSource\t2\tcold,warm' "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-issue-summary.tsv" \
        || ! grep -qx $'routeResolve\t2\tcold,warm' "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-issue-summary.tsv" \
        || [[ "$(sed -n '2p' "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-issue-summary.tsv")" != "routeSource	2	cold,warm" ]] \
        || [[ "$(sed -n '3p' "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-issue-summary.tsv")" != "routeResolve	2	cold,warm" ]] \
        || ! grep -qx '  issue=routeSource count=2 cases=cold,warm' "$TMP_DIR/gemma4-multi-route-contract.out" \
        || ! grep -qx '  issue=routeResolve count=2 cases=cold,warm' "$TMP_DIR/gemma4-multi-route-contract.out"; then
    echo "Expected multi-row Gemma4 route issue summary to aggregate counts and preserve first-seen case ordering" >&2
    cat "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-issues.tsv" >&2
    cat "$GEMMA4_MULTI_ROUTE_CONTRACT_DIR/route-issue-summary.tsv" >&2
    cat "$TMP_DIR/gemma4-multi-route-contract.out" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
PRESET_DIR="$TMP_DIR/safetensor-preset"
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
AQLI_VERIFY_SAFETENSOR_DECODE_THRESHOLD_MS=123 \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only safetensor \
        --summary-dir "$PRESET_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --safetensor-model safetensor-local \
        --safetensor-preset m4-smoke >/dev/null

if ! grep -qx $'safetensor.preset\tm4-smoke' "$PRESET_DIR/config.tsv" \
        || ! grep -qx "safetensor.presetScript	$ROOT_DIR/scripts/safetensor-performance-presets.sh" "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'requireMetal\ttrue' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.maxTokens\t3' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.requireProfile\ttrue' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.requireMetalPaths\ttrue' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.rejectFallbackPaths\ttrue' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.minSpeedTps\t1.0' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.topStageThresholdMs\t5000' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.prefillThresholdMs\t5000' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.decodeThresholdMs\t123' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.tpotThresholdMs\t2000' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.samplingThresholdMs\t1000' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.argmaxThresholdMs\t500' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.attentionThresholdMs\t2500' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.ffnThresholdMs\t2500' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.logitsThresholdMs\t1500' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.minCoreMetalCoverage\t1.0' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.minLinearMetalCoverage\t1.0' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.minLogitsMetalCoverage\t1.0' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.minFfnMetalCoverage\t1.0' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.minAttentionMetalCoverage\t1.0' "$PRESET_DIR/config.tsv" \
        || ! grep -qx $'safetensor.minArgmaxMetalCoverage\t1.0' "$PRESET_DIR/config.tsv"; then
    echo "Expected m4-smoke preset to resolve safetensor profile thresholds without overriding explicit decode threshold" >&2
    cat "$PRESET_DIR/config.tsv" >&2
    exit 1
fi
if ! grep -q "^safetensor .*--max-tokens 3 .*--require-metal .*--require-metal-paths .*--reject-fallback-paths .*--min-speed-tps 1.0 .*--max-top-stage-ms 5000 .*--max-prefill-ms 5000 .*--max-decode-ms 123 .*--max-tpot-ms 2000 .*--max-sampling-ms 1000 .*--max-argmax-ms 500 .*--max-attention-ms 2500 .*--max-ffn-ms 2500 .*--max-logits-ms 1500 .*--min-core-metal-coverage 1.0 .*--min-linear-metal-coverage 1.0 .*--min-logits-metal-coverage 1.0 .*--min-ffn-metal-coverage 1.0 .*--min-attention-metal-coverage 1.0 .*--min-argmax-metal-coverage 1.0$" "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected m4-smoke preset to invoke safetensor gate with Metal proof and bounded stage thresholds" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
PRESET_ALLOW_FALLBACK_DIR="$TMP_DIR/safetensor-preset-allow-fallback"
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only safetensor \
        --summary-dir "$PRESET_ALLOW_FALLBACK_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --safetensor-model safetensor-local \
        --safetensor-preset m4-smoke \
        --safetensor-allow-fallback-paths >/dev/null

if ! grep -qx $'safetensor.rejectFallbackPaths\tfalse' "$PRESET_ALLOW_FALLBACK_DIR/config.tsv" \
        || grep -q -- '--reject-fallback-paths' "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected explicit safetensor fallback allowance to override m4-smoke fallback rejection" >&2
    cat "$PRESET_ALLOW_FALLBACK_DIR/config.tsv" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
FALLBACK_REPORT_DIR="$TMP_DIR/safetensor-hidden-fallback-report"
set +e
AQLI_FAKE_SAFETENSOR_FFN_PATHS='metal_geglu=1;java_geglu=1' \
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only safetensor \
        --summary-dir "$FALLBACK_REPORT_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --safetensor-model safetensor-local \
        --safetensor-preset m4-smoke >"$TMP_DIR/hidden-fallback.out" 2>"$TMP_DIR/hidden-fallback.err"
hidden_fallback_exit=$?
set -e

if [[ "$hidden_fallback_exit" -ne 1 ]]; then
    echo "Expected hidden safetensor fallback contract to fail with exit code 1" >&2
    cat "$TMP_DIR/hidden-fallback.out" >&2
    cat "$TMP_DIR/hidden-fallback.err" >&2
    exit 1
fi

if ! grep -qx "safetensor	metal-deterministic	metal	true	true	true	n/a	n/a	n/a	n/a	n/a	n/a	n/a	/tmp/fake-safetensor.log" "$FALLBACK_REPORT_DIR/backend.tsv" \
        || ! grep -qx "safetensor	metal-deterministic	true	true	pass	true	true	pass	true	true	fail	false	n/a	n/a	false	n/a	n/a	false	n/a	n/a	false	n/a	n/a	false	n/a	n/a	n/a	n/a	/tmp/fake-safetensor.log	n/a	n/a	n/a	n/a" "$FALLBACK_REPORT_DIR/contracts.tsv"; then
    echo "Expected aggregate safetensor report to surface hidden Java fallback evidence even when Metal path proof is present" >&2
    cat "$FALLBACK_REPORT_DIR/backend.tsv" >&2
    cat "$FALLBACK_REPORT_DIR/contracts.tsv" >&2
    exit 1
fi
if ! grep -q "^safetensor	fail	1	[0-9][0-9]*	$FALLBACK_REPORT_DIR/safetensor-fast.tsv	$FALLBACK_REPORT_DIR/safetensor.out	$FALLBACK_REPORT_DIR/safetensor.err	$FALLBACK_REPORT_DIR/safetensor.argv.tsv	contract:fallbackPathStatus:ffn$" "$FALLBACK_REPORT_DIR/results.tsv" \
        || ! grep -qx '  safetensor/metal-deterministic metal=pass metalPath=pass fallbackPath=fail warm=n/a promptCache=n/a javaRefusal=n/a routeCache=n/a selectedArtifact=n/a routeResolveMs=n/a routeSource=n/a routeSourceStatus=n/a routeResolveBudgetMs=n/a routeResolveStatus=n/a log=/tmp/fake-safetensor.log' "$TMP_DIR/hidden-fallback.out" \
        || ! grep -qx 'safetensorPaths:' "$TMP_DIR/hidden-fallback.out" \
        || ! grep -qx '  ffn scope=ffn status=fallback metal=true fallback=true primary=false evidence=metal_geglu=1;java_geglu=1' "$TMP_DIR/hidden-fallback.out" \
        || ! grep -qx 'safetensorPathIssues:' "$TMP_DIR/hidden-fallback.out" \
        || ! grep -qx '  ffn scope=ffn status=fallback metal=true fallback=true primary=false action=Remove FFN fallback markers before latency tuning. evidence=metal_geglu=1;java_geglu=1' "$TMP_DIR/hidden-fallback.out" \
        || ! grep -q "FAIL: safetensor speed gate failed (contract:fallbackPathStatus:ffn);" "$TMP_DIR/hidden-fallback.err"; then
    echo "Expected hidden safetensor fallback contract failure to be recorded as the gate failure reason" >&2
    cat "$FALLBACK_REPORT_DIR/results.tsv" >&2
    cat "$TMP_DIR/hidden-fallback.out" >&2
    cat "$TMP_DIR/hidden-fallback.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
LOGITS_FALLBACK_DIR="$TMP_DIR/safetensor-hidden-logits-fallback"
set +e
AQLI_FAKE_SAFETENSOR_LOGITS_PATHS='metal_logits=1;cpu_logits=1' \
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only safetensor \
        --summary-dir "$LOGITS_FALLBACK_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --safetensor-model safetensor-local \
        --safetensor-preset m4-smoke >"$TMP_DIR/hidden-logits-fallback.out" 2>"$TMP_DIR/hidden-logits-fallback.err"
hidden_logits_fallback_exit=$?
set -e

if [[ "$hidden_logits_fallback_exit" -ne 1 ]]; then
    echo "Expected hidden safetensor logits fallback contract to fail with exit code 1" >&2
    cat "$TMP_DIR/hidden-logits-fallback.out" >&2
    cat "$TMP_DIR/hidden-logits-fallback.err" >&2
    exit 1
fi
if ! grep -q "^safetensor	fail	1	[0-9][0-9]*	$LOGITS_FALLBACK_DIR/safetensor-fast.tsv	$LOGITS_FALLBACK_DIR/safetensor.out	$LOGITS_FALLBACK_DIR/safetensor.err	$LOGITS_FALLBACK_DIR/safetensor.argv.tsv	contract:fallbackPathStatus:logits$" "$LOGITS_FALLBACK_DIR/results.tsv" \
        || ! grep -qx '  logits scope=logits status=fallback metal=true fallback=true primary=false evidence=metal_logits=1;cpu_logits=1' "$TMP_DIR/hidden-logits-fallback.out" \
        || ! grep -qx '  logits scope=logits status=fallback metal=true fallback=true primary=false action=Remove logits fallback markers before latency tuning. evidence=metal_logits=1;cpu_logits=1' "$TMP_DIR/hidden-logits-fallback.out" \
        || ! grep -q "FAIL: safetensor speed gate failed (contract:fallbackPathStatus:logits);" "$TMP_DIR/hidden-logits-fallback.err"; then
    echo "Expected hidden safetensor logits fallback to be recorded as its own gate failure reason" >&2
    cat "$LOGITS_FALLBACK_DIR/results.tsv" >&2
    cat "$TMP_DIR/hidden-logits-fallback.out" >&2
    cat "$TMP_DIR/hidden-logits-fallback.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
DEFAULT_PATH_DIR="$TMP_DIR/default-safetensor-metal-paths"
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only safetensor \
        --summary-dir "$DEFAULT_PATH_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --safetensor-model safetensor-local \
        --no-require-metal >/dev/null

if ! grep -qx "safetensor.requireMetalPaths	$SAFETENSOR_METAL_PATHS_DEFAULT" "$DEFAULT_PATH_DIR/config.tsv"; then
    echo "Expected safetensor Metal path proof default to be recorded in aggregate config" >&2
    cat "$DEFAULT_PATH_DIR/config.tsv" >&2
    exit 1
fi
if [[ "$SAFETENSOR_METAL_PATHS_DEFAULT" == "true" ]]; then
    if ! grep -q '^safetensor .*--require-metal-paths' "$VERIFY_FAST_GATES_LOG"; then
        echo "Expected direct aggregate safetensor gate to require Metal path proof by default on macOS" >&2
        cat "$VERIFY_FAST_GATES_LOG" >&2
        exit 1
    fi
else
    if grep -q -- '--require-metal-paths' "$VERIFY_FAST_GATES_LOG"; then
        echo "Expected direct aggregate safetensor Metal path proof to default off away from macOS" >&2
        cat "$VERIFY_FAST_GATES_LOG" >&2
        exit 1
    fi
fi

>"$VERIFY_FAST_GATES_LOG"
OPTOUT_PATH_DIR="$TMP_DIR/optout-safetensor-metal-paths"
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only safetensor \
        --summary-dir "$OPTOUT_PATH_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --safetensor-model safetensor-local \
        --safetensor-no-require-metal-paths \
        --no-require-metal >/dev/null

if ! grep -qx $'safetensor.requireMetalPaths\tfalse' "$OPTOUT_PATH_DIR/config.tsv" \
        || grep -q -- '--require-metal-paths' "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected direct aggregate safetensor Metal path proof to allow explicit opt-out" >&2
    cat "$OPTOUT_PATH_DIR/config.tsv" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
REGRESSION_FAIL_DIR="$TMP_DIR/safetensor-regression-failure"
set +e
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only safetensor \
        --summary-dir "$REGRESSION_FAIL_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --safetensor-model safetensor-local \
        --safetensor-baseline-stages "$SAFETENSOR_BASELINE_STAGES" \
        --safetensor-max-regression-percent 1 \
        --no-require-metal >"$TMP_DIR/safetensor-regression-failure.out" 2>"$TMP_DIR/safetensor-regression-failure.err"
regression_failure_exit=$?
set -e
if [[ "$regression_failure_exit" -ne 1 ]]; then
    echo "Expected safetensor regression gate to fail with exit code 1" >&2
    cat "$TMP_DIR/safetensor-regression-failure.out" >&2
    cat "$TMP_DIR/safetensor-regression-failure.err" >&2
    exit 1
fi
if ! grep -q "^safetensor	fail	1	[0-9][0-9]*	$REGRESSION_FAIL_DIR/safetensor-fast.tsv	$REGRESSION_FAIL_DIR/safetensor.out	$REGRESSION_FAIL_DIR/safetensor.err	$REGRESSION_FAIL_DIR/safetensor.argv.tsv	safetensor-regression$" "$REGRESSION_FAIL_DIR/results.tsv" \
        || ! grep -qx $'status\tfail' "$REGRESSION_FAIL_DIR/safetensor-regression/summary.tsv" \
        || ! grep -qx $'reason\tstage-regression' "$REGRESSION_FAIL_DIR/safetensor-regression/summary.tsv" \
        || ! grep -qx $'failedStages\t1' "$REGRESSION_FAIL_DIR/safetensor-regression/summary.tsv" \
        || ! grep -qx $'decode\tdecodeMs\t17.000\t18.000\t1.000\t5.882\tprimary\tprimary\tslower\tfail\tdeltaPercent=5.882 exceeded 1' "$REGRESSION_FAIL_DIR/safetensor-regression/comparison.tsv" \
        || ! grep -qx "artifacts.safetensorRegression=$REGRESSION_FAIL_DIR/safetensor-regression/comparison.tsv" "$TMP_DIR/safetensor-regression-failure.out" \
        || ! grep -qx '  status=fail reason=stage-regression primaryStageChanged=false largestSlowdown=decode/decodeMs deltaMs=1.000 deltaPercent=5.882' "$TMP_DIR/safetensor-regression-failure.out"; then
    echo "Expected safetensor regression failure artifacts and results reason" >&2
    cat "$REGRESSION_FAIL_DIR/results.tsv" >&2
    cat "$REGRESSION_FAIL_DIR/safetensor-regression/summary.tsv" >&2
    cat "$REGRESSION_FAIL_DIR/safetensor-regression/comparison.tsv" >&2
    cat "$TMP_DIR/safetensor-regression-failure.out" >&2
    cat "$TMP_DIR/safetensor-regression-failure.err" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only gguf-compare \
        --summary-dir "$TMP_DIR/compare-only" \
        --aqli-bin "$AQLI_BIN" \
        --no-require-metal >/dev/null

if grep -q '^litert ' "$VERIFY_FAST_GATES_LOG" || grep -q '^gemma4-litert ' "$VERIFY_FAST_GATES_LOG" || grep -q '^gguf ' "$VERIFY_FAST_GATES_LOG" || grep -q '^safetensor ' "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected --only gguf-compare to skip LiteRT, Gemma4 LiteRT, GGUF, and safetensor fast gates" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi
if ! grep -q '^gguf-compare .*--no-require-metal .*--summary-file ' "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected --only gguf-compare to run compare gate with requested Metal policy" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
LIMIT_DIR="$TMP_DIR/slowest-limit"
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GEMMA4_LITERT_SMOKE="$TMP_DIR/fake-gemma4-litert-smoke.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --summary-dir "$LIMIT_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --slowest-limit 2 \
        --no-require-metal >"$TMP_DIR/slowest-limit.out"

if ! grep -qx $'slowestLimit\t2' "$LIMIT_DIR/config.tsv" \
        || ! grep -qx "1	safetensor	metal-deterministic	140	metal	profileMetal=true,status=passed,topStage=decode,topStageMs=18.00,prefillMs=4.00,decodeMs=18.00,tpotMs=9.00,attentionMs=6.00,ffnMs=14.00,logitsMs=3.00,linearPaths=attn_q_proj:matvec=2;ffn_down:metal_matmul_f16=2,logitsPaths=metal_logits=2,ffnPaths=metal_geglu=2,attentionPaths=paged_metal=2,argmaxPaths=metal_argmax=2	/tmp/fake-safetensor.log" "$LIMIT_DIR/slowest.tsv" \
        || ! grep -qx "2	gguf-compare	compare	130	MTL0 (Apple M4)	warmSession=true,promptCache=hit,modelLoadMs=0,tokenizeMs=4,prefillMs=5,decodeMs=6,javaStatus=row-dot-primitives-ready,loaderReady=true,decoderTensorsReady=true,rowDotReady=true,generationReady=false,benchmarkParallelMatVecMs=7,javaOnlyParallelMatVecMs=8,bestParallelMatVecMs=7,javaMatVecThresholdMs=9,javaFallbackRefusal=checked	/tmp/fake-compare.log" "$LIMIT_DIR/slowest.tsv" \
        || grep -q '^3	' "$LIMIT_DIR/slowest.tsv" \
        || grep -q '^  #3 ' "$TMP_DIR/slowest-limit.out"; then
    echo "Expected --slowest-limit to trim slowest report and terminal footer" >&2
    cat "$LIMIT_DIR/config.tsv" >&2
    cat "$LIMIT_DIR/slowest.tsv" >&2
    cat "$TMP_DIR/slowest-limit.out" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
cat > "$TMP_DIR/auto-index.json" <<JSON
[ {
  "id" : "litert/local",
  "shortId" : "litert-local",
  "name" : "litert-local",
  "format" : "litert",
  "runnable" : true,
  "path" : "$TMP_DIR/litert-local"
} ]
JSON
AQLI_VERIFY_MODEL_INDEX="$TMP_DIR/auto-index.json" \
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only auto \
        --summary-dir "$TMP_DIR/auto-only" \
        --aqli-bin "$AQLI_BIN" \
        --litert-model litert-local \
        --gguf-model gguf-local \
        --safetensor-model safetensor-local \
        --no-require-metal >/dev/null

if ! grep -q '^litert ' "$VERIFY_FAST_GATES_LOG" \
        || grep -q '^gemma4-litert ' "$VERIFY_FAST_GATES_LOG" \
        || grep -q '^gguf ' "$VERIFY_FAST_GATES_LOG" \
        || grep -q '^gguf-compare ' "$VERIFY_FAST_GATES_LOG" \
        || grep -q '^safetensor ' "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected --only auto to run only locally indexed LiteRT gate and skip compare/safetensor" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi
if ! grep -q "^litert	pass	0	[0-9][0-9]*	$TMP_DIR/auto-only/litert-fast.tsv	$TMP_DIR/auto-only/litert.out	$TMP_DIR/auto-only/litert.err	$TMP_DIR/auto-only/litert.argv.tsv	$" "$TMP_DIR/auto-only/results.tsv" \
        || ! grep -q '^gemma4-litert	skip	0	0					model-not-found$' "$TMP_DIR/auto-only/results.tsv" \
        || ! grep -q '^gguf	skip	0	0					model-not-found$' "$TMP_DIR/auto-only/results.tsv" \
        || ! grep -q '^gguf-compare	skip	0	0					auto-skips-compare$' "$TMP_DIR/auto-only/results.tsv" \
        || ! grep -q '^safetensor	skip	0	0					model-not-found$' "$TMP_DIR/auto-only/results.tsv"; then
    echo "Expected auto results to include LiteRT pass and explicit skip rows" >&2
    cat "$TMP_DIR/auto-only/results.tsv" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
AQLI_VERIFY_MODEL_INDEX="$TMP_DIR/auto-index.json" \
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --only auto \
        --summary-dir "$TMP_DIR/auto-route-only" \
        --aqli-bin "$AQLI_BIN" \
        --litert-model litert-local \
        --gemma4-litert-model gemma4-litert-missing \
        --gguf-model gguf-local \
        --safetensor-model safetensor-local \
        --gemma4-litert-route-summary "$GEMMA4_ROUTE_ONLY_SUMMARY" \
        --no-require-metal >"$TMP_DIR/auto-route-only.out"

if ! grep -q '^litert ' "$VERIFY_FAST_GATES_LOG" \
        || grep -q '^gemma4-litert ' "$VERIFY_FAST_GATES_LOG" \
        || grep -q '^gguf ' "$VERIFY_FAST_GATES_LOG" \
        || grep -q '^gguf-compare ' "$VERIFY_FAST_GATES_LOG" \
        || grep -q '^safetensor ' "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected --only auto route-summary mode to run indexed LiteRT and Gemma4 contracts without invoking Gemma4 smoke" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi
if ! grep -q "^litert	pass	0	[0-9][0-9]*	$TMP_DIR/auto-route-only/litert-fast.tsv	$TMP_DIR/auto-route-only/litert.out	$TMP_DIR/auto-route-only/litert.err	$TMP_DIR/auto-route-only/litert.argv.tsv	$" "$TMP_DIR/auto-route-only/results.tsv" \
        || ! grep -q "^gemma4-litert	pass	0	[0-9][0-9]*	$TMP_DIR/auto-route-only/gemma4-litert-warm.tsv	$TMP_DIR/auto-route-only/gemma4-litert.out	$TMP_DIR/auto-route-only/gemma4-litert.err	$TMP_DIR/auto-route-only/gemma4-litert.argv.tsv	$" "$TMP_DIR/auto-route-only/results.tsv" \
        || ! grep -q '^gguf	skip	0	0					model-not-found$' "$TMP_DIR/auto-route-only/results.tsv" \
        || ! grep -q '^gguf-compare	skip	0	0					auto-skips-compare$' "$TMP_DIR/auto-route-only/results.tsv" \
        || ! grep -q '^safetensor	skip	0	0					model-not-found$' "$TMP_DIR/auto-route-only/results.tsv"; then
    echo "Expected auto route-summary results to include LiteRT pass, Gemma4 contract pass, and explicit skip rows" >&2
    cat "$TMP_DIR/auto-route-only/results.tsv" >&2
    exit 1
fi
if ! grep -qx "gemma4-litert	route	false	true	n/a	false	n/a	n/a	false	n/a	n/a	false	true	n/a	false	n/a	n/a	false	n/a	n/a	true	true	pass	true	litertlm	pass	7	fast-index-equivalent-litert	/tmp/fake-gemma4-route.log	fast-index-equivalent-litert	pass	250	pass" "$TMP_DIR/auto-route-only/contracts.tsv"; then
    echo "Expected auto route-summary mode to validate Gemma4 route contracts from TSV" >&2
    cat "$TMP_DIR/auto-route-only/contracts.tsv" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
FAIL_DIR="$TMP_DIR/failure"
set +e
AQLI_FAKE_FAIL_GATE=gguf \
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GEMMA4_LITERT_SMOKE="$TMP_DIR/fake-gemma4-litert-smoke.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --summary-dir "$FAIL_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --no-require-metal >"$TMP_DIR/failure.out" 2>"$TMP_DIR/failure.err"
failure_exit=$?
set -e
if [[ "$failure_exit" -ne 42 ]]; then
    echo "Expected failing aggregate gate to preserve benchmark exit code" >&2
    cat "$TMP_DIR/failure.out" >&2
    cat "$TMP_DIR/failure.err" >&2
    exit 1
fi
if ! grep -q "^gguf	fail	42	[0-9][0-9]*	$FAIL_DIR/gguf-fast.tsv	$FAIL_DIR/gguf.out	$FAIL_DIR/gguf.err	$FAIL_DIR/gguf.argv.tsv	$" "$FAIL_DIR/results.tsv"; then
    echo "Expected failed GGUF gate row in aggregate results" >&2
    cat "$FAIL_DIR/results.tsv" >&2
    exit 1
fi
if ! grep -qx $'rank\tgate\tcase\tdurationMs\tbackend\tmetrics\tlog' "$FAIL_DIR/slowest.tsv" \
        || ! grep -qx "1	gguf	warm	120	MTL0 (Apple M4)	warmSession=true,tokenizeCache=hit,promptCache=prefix-hit,promptCacheEagerShort=false,outputBytes=64,javaRetries=0,modelLoadMs=0,tokenizeMs=4,prefillMs=5,decodeMs=6	/tmp/fake-gguf.log" "$FAIL_DIR/slowest.tsv" \
        || ! grep -qx "2	gemma4-litert	warm	110	MTL0 (Apple M4)	warmEngine=true,engineInitMs=1,firstChunkMs=2,totalMs=3,routeResolveMs=3,routeCacheHit=true,selectedArtifact=litertlm,routeSource=fast-index-equivalent-litert	/tmp/fake-gemma4-litert.log" "$FAIL_DIR/slowest.tsv" \
        || ! grep -qx "3	litert	warm	100	MTL0 (Apple M4)	warmEngine=true,engineInitMs=1,firstChunkMs=2,totalMs=3,routeResolveMs=2,routeCacheHit=true,selectedArtifact=litertlm,routeSource=fake-litert-index	/tmp/fake-litert.log" "$FAIL_DIR/slowest.tsv"; then
    echo "Expected early failure to leave a populated slowest report" >&2
    cat "$FAIL_DIR/slowest.tsv" >&2
    exit 1
fi
if ! grep -qx "summaryDir=$FAIL_DIR" "$TMP_DIR/failure.out" \
        || ! grep -qx "artifacts.backend=$FAIL_DIR/backend.tsv" "$TMP_DIR/failure.out" \
        || ! grep -qx "artifacts.contracts=$FAIL_DIR/contracts.tsv" "$TMP_DIR/failure.out" \
        || ! grep -qx "artifacts.slowest=$FAIL_DIR/slowest.tsv" "$TMP_DIR/failure.out" \
        || ! grep -qx '  gguf/warm metal=true metalPathProof=n/a fallbackPathEvidence=n/a warmReuse=true promptCache=prefix-hit javaFallbackRefusal=n/a routeResolveMs=n/a routeCacheHit=n/a selectedArtifact=n/a routeSource=n/a backend=MTL0 (Apple M4) log=/tmp/fake-gguf.log' "$TMP_DIR/failure.out" \
        || ! grep -qx '  gguf/warm metal=n/a metalPath=n/a fallbackPath=n/a warm=pass promptCache=pass javaRefusal=n/a routeCache=n/a selectedArtifact=n/a routeResolveMs=n/a routeSource=n/a routeSourceStatus=n/a routeResolveBudgetMs=n/a routeResolveStatus=n/a log=/tmp/fake-gguf.log' "$TMP_DIR/failure.out" \
        || ! grep -qx '  #1 gguf/warm duration=120ms backend=MTL0 (Apple M4) log=/tmp/fake-gguf.log' "$TMP_DIR/failure.out"; then
    echo "Expected early failure output to print artifact paths and slowest rows" >&2
    cat "$TMP_DIR/failure.out" >&2
    cat "$TMP_DIR/failure.err" >&2
    exit 1
fi
if grep -q '^gguf-compare ' "$VERIFY_FAST_GATES_LOG" || grep -q '^safetensor ' "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected aggregate gate to stop after GGUF failure" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi

>"$VERIFY_FAST_GATES_LOG"
CONTINUE_DIR="$TMP_DIR/continue-failure"
set +e
AQLI_FAKE_FAIL_GATE=gguf \
AQLI_VERIFY_FAST_LITERT_BENCH="$TMP_DIR/fake-litert-bench.sh" \
AQLI_VERIFY_FAST_GEMMA4_LITERT_SMOKE="$TMP_DIR/fake-gemma4-litert-smoke.sh" \
AQLI_VERIFY_FAST_GGUF_BENCH="$TMP_DIR/fake-gguf-bench.sh" \
AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH="$TMP_DIR/fake-gguf-compare-bench.sh" \
AQLI_VERIFY_FAST_SAFETENSOR_BENCH="$TMP_DIR/fake-safetensor-bench.sh" \
    bash "$ROOT_DIR/scripts/verify-fast-speed-gates.sh" \
        --summary-dir "$CONTINUE_DIR" \
        --aqli-bin "$AQLI_BIN" \
        --no-require-metal \
        --continue-on-failure >"$TMP_DIR/continue.out" 2>"$TMP_DIR/continue.err"
continue_exit=$?
set -e
if [[ "$continue_exit" -ne 42 ]]; then
    echo "Expected continue-on-failure aggregate gate to preserve first failure exit code" >&2
    cat "$TMP_DIR/continue.out" >&2
    cat "$TMP_DIR/continue.err" >&2
    exit 1
fi
if ! grep -q '^litert ' "$VERIFY_FAST_GATES_LOG" \
        || ! grep -q '^gemma4-litert ' "$VERIFY_FAST_GATES_LOG" \
        || ! grep -q '^gguf ' "$VERIFY_FAST_GATES_LOG" \
        || ! grep -q '^gguf-compare ' "$VERIFY_FAST_GATES_LOG" \
        || ! grep -q '^safetensor ' "$VERIFY_FAST_GATES_LOG"; then
    echo "Expected continue-on-failure to run all selected gates" >&2
    cat "$VERIFY_FAST_GATES_LOG" >&2
    exit 1
fi
if ! grep -q "^litert	pass	0	[0-9][0-9]*	$CONTINUE_DIR/litert-fast.tsv	$CONTINUE_DIR/litert.out	$CONTINUE_DIR/litert.err	$CONTINUE_DIR/litert.argv.tsv	$" "$CONTINUE_DIR/results.tsv" \
        || ! grep -q "^gemma4-litert	pass	0	[0-9][0-9]*	$CONTINUE_DIR/gemma4-litert-warm.tsv	$CONTINUE_DIR/gemma4-litert.out	$CONTINUE_DIR/gemma4-litert.err	$CONTINUE_DIR/gemma4-litert.argv.tsv	$" "$CONTINUE_DIR/results.tsv" \
        || ! grep -q "^gguf	fail	42	[0-9][0-9]*	$CONTINUE_DIR/gguf-fast.tsv	$CONTINUE_DIR/gguf.out	$CONTINUE_DIR/gguf.err	$CONTINUE_DIR/gguf.argv.tsv	$" "$CONTINUE_DIR/results.tsv" \
        || ! grep -q "^gguf-compare	pass	0	[0-9][0-9]*	$CONTINUE_DIR/gguf-compare.tsv	$CONTINUE_DIR/gguf-compare.out	$CONTINUE_DIR/gguf-compare.err	$CONTINUE_DIR/gguf-compare.argv.tsv	$" "$CONTINUE_DIR/results.tsv" \
        || ! grep -q "^safetensor	pass	0	[0-9][0-9]*	$CONTINUE_DIR/safetensor-fast.tsv	$CONTINUE_DIR/safetensor.out	$CONTINUE_DIR/safetensor.err	$CONTINUE_DIR/safetensor.argv.tsv	$" "$CONTINUE_DIR/results.tsv"; then
    echo "Expected continue-on-failure results to include pass, pass, fail, pass, pass rows" >&2
    cat "$CONTINUE_DIR/results.tsv" >&2
    exit 1
fi

printf 'verify-fast-speed-gates test passed\n'
