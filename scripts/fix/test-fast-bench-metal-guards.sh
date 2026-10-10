#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-fast-bench-metal-guards.XXXXXX")"
cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

FAKE_AQLI="$TMP_DIR/aqli"
cat > "$FAKE_AQLI" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

state_dir="${AQLI_FAKE_STATE_DIR:-${TMPDIR:-/tmp}}"
mkdir -p "$state_dir"
litert_state="$state_dir/litert-daemon-state"
gguf_state="$state_dir/gguf-daemon-state"

if [[ "${1:-}" == "__daemon-stop" ]]; then
    rm -f "$litert_state"
    exit 0
fi
if [[ "${1:-}" == "__gguf-daemon-stop" ]]; then
    rm -f "$gguf_state"
    exit 0
fi

engine="auto"
provider=""
while [[ $# -gt 0 ]]; do
    case "$1" in
        --engine) engine="$2"; shift 2 ;;
        --provider) provider="$2"; shift 2 ;;
        *) shift ;;
    esac
done

backend="${AQLI_FAKE_BACKEND:-CPU}"
probe="${AQLI_FAKE_PROBE:-tensor=blk.0.attn_q.weight, type=Q4_K, rows=2304, cols=1536, sampledRows=16, dot=1.000ms, dotChecksum=1.0, matVecRows=4096, cache=1.000ms, preparedMatVecReady=true, parallelMatVec=5.0ms, matVecChecksum=1.0, cachedGenericMatVec=6.0ms, cachedChecksum=1.0}"
java_probe="${AQLI_FAKE_JAVA_PROBE:-$probe}"
if [[ -n "${AQLI_FAKE_RUN_EXIT:-}" ]]; then
    printf '%s\n' "${AQLI_FAKE_RUN_OUTPUT:-fake aqli run failure}" >&2
    exit "$AQLI_FAKE_RUN_EXIT"
fi
case "$engine" in
    benchmark)
        cat <<EOF
GGUF engine benchmark: Java-native loader/probe vs llama.cpp generation fallback
Java-native GGUF loader: status=loader-ready; decoder-tensors-ready; row-dot-primitives-ready; generation-disabled.
Java-native GGUF loader readiness: loaderReady=true, decoderTensorsReady=true, rowDotReady=true, generationReady=false.
Java-native GGUF loader config: ${AQLI_FAKE_CONFIG:-type=gemma4, layers=35, hidden=1536, heads=8/1, headDim=256, context=131072, vocab=262144}.
Java-native GGUF loader tensor probe: ${probe}
Using llama.cpp GGUF
Jakarta is in Indonesia.
nativeTiming(modelLoad=1.0ms, backend=${backend}, warmSession=true, promptCache=hit, prefill=1.0ms, decode=1.0ms)
[Stream updates: 1, Duration: 1.00s, Speed: 10.00 t/s]
EOF
        ;;
    java)
        cat <<EOF
Java-native GGUF loader: status=loader-ready; decoder-tensors-ready; row-dot-primitives-ready; generation-disabled.
Java-native GGUF loader readiness: loaderReady=true, decoderTensorsReady=true, rowDotReady=true, generationReady=false.
Java-native GGUF loader config: ${AQLI_FAKE_JAVA_CONFIG:-type=gemma4, layers=35, hidden=1536, heads=8/1, headDim=256, context=131072, vocab=262144}.
Java-native GGUF loader tensor probe: ${java_probe}
Java-native GGUF generation is not enabled yet; refusing to silently use llama.cpp.
EOF
        exit 1
        ;;
    *)
        if [[ "$provider" == "gguf" ]]; then
            gguf_runs=0
            if [[ -f "$gguf_state" ]]; then
                gguf_runs="$(cat "$gguf_state")"
            fi
            warm_session=false
            prompt_cache=stored
            if [[ "$gguf_runs" -gt 0 ]]; then
                warm_session=true
                prompt_cache=hit
                if [[ "$gguf_runs" -eq 1 && -n "${AQLI_FAKE_FIRST_REPEAT_PROMPT_CACHE:-}" ]]; then
                    prompt_cache="$AQLI_FAKE_FIRST_REPEAT_PROMPT_CACHE"
                fi
            fi
            printf '%s\n' "$((gguf_runs + 1))" > "$gguf_state"
            cat <<EOF
Jakarta is in Indonesia.
nativeTiming(modelLoad=1.0ms, backend=${backend}, warmSession=${warm_session}, promptCache=${prompt_cache}, prefill=1.0ms, decode=1.0ms)
[Stream updates: 1, Duration: 1.00s, Speed: 10.00 t/s]
EOF
            exit 0
        fi
        warm_engine=false
        if [[ -f "$litert_state" ]]; then
            warm_engine=true
        fi
        printf 'warm\n' > "$litert_state"
        cat <<EOF
Jakarta is in Indonesia.
profile(routeResolve=0.002s, routeCacheHit=true, selectedArtifact=litertlm, routeSource=fake-index, engineInit=0.001s, firstChunk=0.001s, total=0.001s, backend=${backend}, warmEngine=${warm_engine})
[Stream updates: 1, Duration: 1.00s, Speed: 10.00 t/s]
EOF
        ;;
esac
SH
chmod +x "$FAKE_AQLI"

expect_failure() {
    local name="$1"
    shift
    expect_failure_message "$name" 'did not use Metal backend' "$@"
}

expect_failure_message() {
    local name="$1"
    local message="$2"
    shift 2
    if "$@" >"$TMP_DIR/${name}.out" 2>"$TMP_DIR/${name}.err"; then
        echo "Expected failure: $name" >&2
        cat "$TMP_DIR/${name}.out" >&2
        cat "$TMP_DIR/${name}.err" >&2
        exit 1
    fi
    if ! grep -q "$message" "$TMP_DIR/${name}.err"; then
        echo "Expected failure message '$message': $name" >&2
        cat "$TMP_DIR/${name}.out" >&2
        cat "$TMP_DIR/${name}.err" >&2
        exit 1
    fi
}

expect_success() {
    local name="$1"
    shift
    "$@" >"$TMP_DIR/${name}.out" 2>"$TMP_DIR/${name}.err"
}

COMMON_ARGS=(--aqli-bin "$FAKE_AQLI" --warm-threshold-ms 2000 --cold-threshold-ms 2000 --warmup-runs 0 --require-metal)
SLOW_JAVA_PROBE='tensor=blk.0.attn_q.weight, type=Q4_K, rows=2304, cols=1536, sampledRows=16, dot=1.000ms, dotChecksum=1.0, matVecRows=4096, cache=1.000ms, preparedMatVecReady=true, parallelMatVec=99.0ms, matVecChecksum=1.0, cachedGenericMatVec=100.0ms, cachedChecksum=1.0'
export AQLI_FAKE_STATE_DIR="$TMP_DIR"

AQLI_FAKE_BACKEND=GPU expect_failure litert_generic_gpu \
    bash "$ROOT_DIR/scripts/bench-litert-fast-run.sh" "${COMMON_ARGS[@]}"
LITERT_FAILURE_SUMMARY="$TMP_DIR/litert-failure-summary.tsv"
set +e
AQLI_FAKE_BACKEND='GPU/Metal' \
AQLI_FAKE_RUN_EXIT=64 \
AQLI_FAKE_RUN_OUTPUT='/dev/fd/62: Operation not permitted' \
    bash "$ROOT_DIR/scripts/bench-litert-fast-run.sh" --aqli-bin "$FAKE_AQLI" --warm-threshold-ms 2000 --cold-threshold-ms 2000 --warmup-runs 0 --require-metal --summary-file "$LITERT_FAILURE_SUMMARY" \
    >"$TMP_DIR/litert_run_failure.out" 2>"$TMP_DIR/litert_run_failure.err"
litert_run_failure_exit=$?
set -e
if [[ "$litert_run_failure_exit" -ne 64 ]] \
        || ! grep -q 'FAIL: cold command exited with status 64' "$TMP_DIR/litert_run_failure.err" \
        || ! grep -q '/dev/fd/62: Operation not permitted' "$TMP_DIR/litert_run_failure.err" \
        || ! grep -q $'^cold\tn/a\tunknown\tn/a\tn/a\tn/a\tn/a\tn/a\tn/a\tn/a\tn/a\t' "$LITERT_FAILURE_SUMMARY"; then
    echo "Expected LiteRT benchmark to preserve command-exit diagnostics and summary log path" >&2
    cat "$TMP_DIR/litert_run_failure.out" >&2
    cat "$TMP_DIR/litert_run_failure.err" >&2
    cat "$LITERT_FAILURE_SUMMARY" >&2
    exit 1
fi
rm -f "$TMP_DIR/litert-daemon-state"
AQLI_FAKE_BACKEND='GPU/Metal' \
    expect_failure_message litert_warm_only_without_daemon 'did not reuse the LiteRT daemon engine' \
    bash "$ROOT_DIR/scripts/bench-litert-fast-run.sh" --aqli-bin "$FAKE_AQLI" --warm-threshold-ms 2000 --warmup-runs 0 --require-metal --warm-only
printf 'warm\n' > "$TMP_DIR/litert-daemon-state"
AQLI_FAKE_BACKEND='GPU/Metal' \
    expect_failure_message litert_warm_profile_regression 'LiteRT profile total took 1ms' \
    bash "$ROOT_DIR/scripts/bench-litert-fast-run.sh" --aqli-bin "$FAKE_AQLI" --warm-threshold-ms 2000 --warm-profile-total-threshold-ms 0.5 --warmup-runs 0 --require-metal --warm-only
AQLI_FAKE_BACKEND=CPU expect_failure gguf_cpu \
    bash "$ROOT_DIR/scripts/bench-gguf-fast-run.sh" "${COMMON_ARGS[@]}"
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' AQLI_FAKE_FIRST_REPEAT_PROMPT_CACHE=stored \
    expect_failure_message gguf_first_repeat_cache_miss 'did not hit the GGUF prompt cache' \
    bash "$ROOT_DIR/scripts/bench-gguf-fast-run.sh" --aqli-bin "$FAKE_AQLI" --warm-threshold-ms 2000 --cold-threshold-ms 2000 --warmup-runs 1 --require-metal --require-prompt-cache
rm -f "$TMP_DIR/gguf-daemon-state"
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' \
    expect_failure_message gguf_warm_only_without_daemon 'did not reuse the GGUF daemon session' \
    bash "$ROOT_DIR/scripts/bench-gguf-fast-run.sh" --aqli-bin "$FAKE_AQLI" --warm-threshold-ms 2000 --warmup-runs 0 --require-metal --require-prompt-cache --warm-only
printf '2\n' > "$TMP_DIR/gguf-daemon-state"
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' \
    expect_failure_message gguf_warm_decode_regression 'native decode took 1.0ms' \
    bash "$ROOT_DIR/scripts/bench-gguf-fast-run.sh" --aqli-bin "$FAKE_AQLI" --warm-threshold-ms 2000 --warm-decode-threshold-ms 0.5 --warmup-runs 0 --require-metal --require-prompt-cache --warm-only
AQLI_FAKE_BACKEND=CPU expect_failure gguf_compare_cpu \
    bash "$ROOT_DIR/scripts/bench-gguf-engine-compare.sh" --aqli-bin "$FAKE_AQLI" --threshold-ms 2000 --require-metal
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' AQLI_FAKE_CONFIG='type=unknown, layers=0, hidden=0, heads=0/0, headDim=0, context=0, vocab=0' \
    expect_failure_message gguf_compare_bad_config 'mapped config did not match required regex' \
    bash "$ROOT_DIR/scripts/bench-gguf-engine-compare.sh" --aqli-bin "$FAKE_AQLI" --threshold-ms 2000 --require-metal
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' AQLI_FAKE_JAVA_CONFIG='type=gemma4, layers=34, hidden=1536, heads=8/1, headDim=256, context=131072, vocab=262144' \
    expect_failure_message gguf_compare_java_config_drift 'mapped config differed from benchmark mode' \
    bash "$ROOT_DIR/scripts/bench-gguf-engine-compare.sh" --aqli-bin "$FAKE_AQLI" --threshold-ms 2000 --require-metal
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' AQLI_FAKE_JAVA_PROBE="$SLOW_JAVA_PROBE" \
    expect_failure_message gguf_compare_java_only_matvec_regression 'Java-native GGUF --engine java parallelMatVec took 99.0ms' \
    bash "$ROOT_DIR/scripts/bench-gguf-engine-compare.sh" --aqli-bin "$FAKE_AQLI" --threshold-ms 2000 --java-matvec-threshold-ms 50 --require-metal
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' AQLI_FAKE_PROBE='parallelMatVec=5.0ms' \
    expect_failure_message gguf_compare_shallow_probe 'tensor probe did not match required regex' \
    bash "$ROOT_DIR/scripts/bench-gguf-engine-compare.sh" --aqli-bin "$FAKE_AQLI" --threshold-ms 2000 --require-metal
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' \
    expect_failure_message gguf_compare_decode_regression 'llama.cpp fallback decode took 1.0ms' \
    bash "$ROOT_DIR/scripts/bench-gguf-engine-compare.sh" --aqli-bin "$FAKE_AQLI" --threshold-ms 2000 --fallback-decode-threshold-ms 0.5 --require-metal

AQLI_FAKE_BACKEND='GPU/Metal' expect_success litert_metal \
    bash "$ROOT_DIR/scripts/bench-litert-fast-run.sh" "${COMMON_ARGS[@]}"
printf 'warm\n' > "$TMP_DIR/litert-daemon-state"
AQLI_FAKE_BACKEND='GPU/Metal' expect_success litert_warm_only \
    bash "$ROOT_DIR/scripts/bench-litert-fast-run.sh" --aqli-bin "$FAKE_AQLI" --warm-threshold-ms 2000 --warmup-runs 0 --require-metal --warm-only
LITERT_SUMMARY="$TMP_DIR/litert-summary.tsv"
printf 'warm\n' > "$TMP_DIR/litert-daemon-state"
AQLI_FAKE_BACKEND='GPU/Metal' expect_success litert_summary \
    bash "$ROOT_DIR/scripts/bench-litert-fast-run.sh" --aqli-bin "$FAKE_AQLI" --warm-threshold-ms 2000 --warmup-runs 0 --require-metal --warm-only --summary-file "$LITERT_SUMMARY"
if ! grep -q $'^case\tdurationMs\tbackend\twarmEngine\tengineInitMs\tfirstChunkMs\ttotalMs\trouteResolveMs\trouteCacheHit\tselectedArtifact\trouteSource\tlog$' "$LITERT_SUMMARY" \
        || ! grep -q $'^warm\t1000\tGPU/Metal\ttrue\t1\t1\t1\t2\ttrue\tlitertlm\tfake-index\t' "$LITERT_SUMMARY"; then
    echo "Expected LiteRT benchmark summary file with warm metrics" >&2
    cat "$LITERT_SUMMARY" >&2
    exit 1
fi
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' expect_success gguf_metal \
    bash "$ROOT_DIR/scripts/bench-gguf-fast-run.sh" "${COMMON_ARGS[@]}"
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' expect_success gguf_first_repeat_cache_hit \
    bash "$ROOT_DIR/scripts/bench-gguf-fast-run.sh" --aqli-bin "$FAKE_AQLI" --warm-threshold-ms 2000 --cold-threshold-ms 2000 --warmup-runs 1 --require-metal --require-prompt-cache
printf '2\n' > "$TMP_DIR/gguf-daemon-state"
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' expect_success gguf_warm_only_cache_hit \
    bash "$ROOT_DIR/scripts/bench-gguf-fast-run.sh" --aqli-bin "$FAKE_AQLI" --warm-threshold-ms 2000 --warmup-runs 0 --require-metal --require-prompt-cache --warm-only
GGUF_SUMMARY="$TMP_DIR/gguf-summary.tsv"
printf '2\n' > "$TMP_DIR/gguf-daemon-state"
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' expect_success gguf_summary \
    bash "$ROOT_DIR/scripts/bench-gguf-fast-run.sh" --aqli-bin "$FAKE_AQLI" --warm-threshold-ms 2000 --warmup-runs 0 --require-metal --require-prompt-cache --warm-only --summary-file "$GGUF_SUMMARY"
if ! grep -q $'^case\tdurationMs\tbackend\twarmSession\ttokenizeCache\tpromptCache\tpromptCacheEagerShort\toutputBytes\tjavaRetries\tmodelLoadMs\ttokenizeMs\tprefillMs\tdecodeMs\tlog$' "$GGUF_SUMMARY" \
        || ! grep -q $'^warm\t1000\tMTL0 (Apple M4)\ttrue\tn/a\thit\t' "$GGUF_SUMMARY"; then
    echo "Expected GGUF benchmark summary file with warm metrics" >&2
    cat "$GGUF_SUMMARY" >&2
    exit 1
fi
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' expect_success gguf_compare_metal \
    bash "$ROOT_DIR/scripts/bench-gguf-engine-compare.sh" --aqli-bin "$FAKE_AQLI" --threshold-ms 2000 --require-metal
GGUF_COMPARE_SUMMARY="$TMP_DIR/gguf-compare-summary.tsv"
AQLI_FAKE_BACKEND='MTL0 (Apple M4)' expect_success gguf_compare_summary \
    bash "$ROOT_DIR/scripts/bench-gguf-engine-compare.sh" --aqli-bin "$FAKE_AQLI" --threshold-ms 2000 --fallback-prefill-threshold-ms 2 --fallback-decode-threshold-ms 2 --require-metal --summary-file "$GGUF_COMPARE_SUMMARY"
if ! grep -q $'^case\tdurationMs\tbackend\twarmSession\tpromptCache\tmodelLoadMs\ttokenizeMs\tprefillMs\tdecodeMs\tjavaStatus\tloaderReady\tdecoderTensorsReady\trowDotReady\tgenerationReady\tbenchmarkParallelMatVecMs\tjavaOnlyParallelMatVecMs\tbestParallelMatVecMs\tjavaMatVecThresholdMs\tjavaFallbackRefusal\tlog$' "$GGUF_COMPARE_SUMMARY" \
        || ! grep -q $'^compare\t1000\tMTL0 (Apple M4)\ttrue\thit\t1.0\tn/a\t1.0\t1.0\tloader-ready; decoder-tensors-ready; row-dot-primitives-ready; generation-disabled\ttrue\ttrue\ttrue\tfalse\t5.0\t5.0\t5.000\t50\tchecked\t' "$GGUF_COMPARE_SUMMARY"; then
    echo "Expected GGUF compare benchmark summary file with fallback and Java probe metrics" >&2
    cat "$GGUF_COMPARE_SUMMARY" >&2
    exit 1
fi

printf 'fast benchmark Metal guard test passed\n'
