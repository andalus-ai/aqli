#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: diagnose-safetensor-profile-summary.sh --summary SUMMARY [options]

Analyze a safetensor profile.tsv or gate.tsv and identify the dominant runtime
stage. This turns normalized benchmark timing columns into a small
action-oriented diagnosis for Metal/CPU performance work.

Options:
  --summary PATH       Input safetensor profile.tsv or gate.tsv (required)
  --summary-dir DIR    Directory for artifacts (default: temp dir)
  --out PATH           Diagnosis key/value TSV (default: summary-dir/diagnosis.tsv)
  --stages-out PATH    Stage ranking TSV (default: summary-dir/stages.tsv)
  --paths-out PATH     Path evidence TSV (default: summary-dir/paths.tsv)
  --case NAME          Diagnose a specific case row
  --include-failed     Include failed rows when computing the default mean
  --help               Show this help
USAGE
}

SUMMARY=""
SUMMARY_DIR=""
OUT=""
STAGES_OUT=""
PATHS_OUT=""
CASE_NAME=""
INCLUDE_FAILED=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --summary) SUMMARY="$2"; shift 2 ;;
    --summary=*) SUMMARY="${1#*=}"; shift ;;
    --summary-dir) SUMMARY_DIR="$2"; shift 2 ;;
    --summary-dir=*) SUMMARY_DIR="${1#*=}"; shift ;;
    --out) OUT="$2"; shift 2 ;;
    --out=*) OUT="${1#*=}"; shift ;;
    --stages-out) STAGES_OUT="$2"; shift 2 ;;
    --stages-out=*) STAGES_OUT="${1#*=}"; shift ;;
    --paths-out) PATHS_OUT="$2"; shift 2 ;;
    --paths-out=*) PATHS_OUT="${1#*=}"; shift ;;
    --case) CASE_NAME="$2"; shift 2 ;;
    --case=*) CASE_NAME="${1#*=}"; shift ;;
    --include-failed) INCLUDE_FAILED=1; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$SUMMARY" ]]; then
  echo "--summary is required" >&2
  usage
  exit 2
fi
if [[ ! -f "$SUMMARY" ]]; then
  echo "Summary file not found: $SUMMARY" >&2
  exit 2
fi
for cmd in awk date mkdir mktemp tee; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

if [[ -z "$SUMMARY_DIR" ]]; then
  SUMMARY_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-safetensor-profile-diagnosis.XXXXXX")"
else
  mkdir -p "$SUMMARY_DIR"
fi
OUT="${OUT:-${SUMMARY_DIR}/diagnosis.tsv}"
STAGES_OUT="${STAGES_OUT:-${SUMMARY_DIR}/stages.tsv}"
PATHS_OUT="${PATHS_OUT:-${SUMMARY_DIR}/paths.tsv}"
CONFIG="${SUMMARY_DIR}/config.tsv"
REPORT="${SUMMARY_DIR}/report.txt"

for artifact in "$OUT" "$STAGES_OUT" "$PATHS_OUT"; do
  artifact_parent="${artifact%/*}"
  if [[ "$artifact_parent" != "$artifact" ]]; then
    mkdir -p "$artifact_parent"
  fi
done

safe_tsv_field() {
  local value="${1:-}"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

config_row() {
  printf '%s\t%s\n' "$(safe_tsv_field "$1")" "$(safe_tsv_field "${2:-}")" >> "$CONFIG"
}

{
  printf 'key\tvalue\n'
} > "$CONFIG"
config_row generatedAt "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
config_row summary "$SUMMARY"
config_row summaryDir "$SUMMARY_DIR"
config_row diagnosis "$OUT"
config_row stages "$STAGES_OUT"
config_row paths "$PATHS_OUT"
config_row case "$CASE_NAME"
config_row includeFailed "$INCLUDE_FAILED"

awk \
  -v caseName="$CASE_NAME" \
  -v includeFailed="$INCLUDE_FAILED" \
  -v stagesOut="$STAGES_OUT" \
  -v pathsOut="$PATHS_OUT" \
  -v diagnosisOut="$OUT" '
  BEGIN {
    FS = OFS = "\t"
    metricCount = 7
    primaryMetricCount = 6
    stageName[1] = "prefill"; metricName[1] = "prefillMs"
    stageName[2] = "decode"; metricName[2] = "decodeMs"
    stageName[3] = "attention"; metricName[3] = "attentionMs"
    stageName[4] = "ffn"; metricName[4] = "ffnMs"
    stageName[5] = "logits"; metricName[5] = "logitsMs"
    stageName[6] = "argmax"; metricName[6] = "argmaxMs"
    stageName[7] = "tpot"; metricName[7] = "tpotMs"
    print "stage", "metric", "valueMs", "shareOfDurationPercent", "priority", "pathEvidence", "recommendation" > stagesOut
    print "case", "pathGroup", "stageScope", "metalStatus", "fallbackStatus", "status", "primary", "pathEvidence", "nextAction" > pathsOut
  }
  function numeric(value) {
    return value ~ /^-?[0-9]+([.][0-9]+)?$/
  }
  function clean(value) {
    return value == "n/a" ? "" : value
  }
  function lower(value) {
    return tolower(value)
  }
  function pass_status(value) {
    value = lower(value)
    return value == "" || value == "pass" || value == "passed" || value == "ok" || value == "success"
  }
  function bool_true(value) {
    value = lower(value)
    return value == "true" || value == "yes" || value == "1" || value ~ /metal/ || value ~ /gpu/
  }
  function should_use(status) {
    return includeFailed == "1" || pass_status(status)
  }
  function remember(prefix, key, value, slot) {
    value = clean(value)
    if (value == "") {
      return
    }
    slot = prefix SUBSEP key
    if (remembered[slot] == "") {
      remembered[slot] = value
    } else if (remembered[slot] != value) {
      remembered[slot] = "mixed"
    }
  }
  function add_value(prefix, metric, value) {
    if (numeric(value)) {
      sum[prefix, metric] += value + 0
      count[prefix, metric]++
    }
  }
  function add_row(prefix, i, metric) {
    rows[prefix]++
    remember(prefix, "backend", backend)
    remember(prefix, "metal", metal)
    remember(prefix, "topStage", topStage)
    remember(prefix, "linearPaths", linearPaths)
    remember(prefix, "logitsPaths", logitsPaths)
    remember(prefix, "ffnPaths", ffnPaths)
    remember(prefix, "ffnStrategy", ffnStrategy)
    remember(prefix, "ffnRowPrefillNativeRows", ffnRowPrefillNativeRows)
    remember(prefix, "ffnRowPrefillVariant", ffnRowPrefillVariant)
    remember(prefix, "attentionPaths", attentionPaths)
    remember(prefix, "argmaxPaths", argmaxPaths)
    add_value(prefix, "durationMs", durationMs)
    add_value(prefix, "topStageMs", topStageMs)
    for (i = 1; i <= metricCount; i++) {
      metric = metricName[i]
      add_value(prefix, metric, valueOf[metric])
    }
  }
  function mean_value(prefix, metric) {
    return count[prefix, metric] > 0 ? sprintf("%.3f", sum[prefix, metric] / count[prefix, metric]) : ""
  }
  function first_existing(a, b) {
    return a ? a : b
  }
  function first_existing3(a, b, c) {
    return a ? a : (b ? b : c)
  }
  function load_row_fields() {
    rowCase = first_existing(column["case"] ? $(column["case"]) : "", column["case_id"] ? $(column["case_id"]) : "")
    status = column["status"] ? $(column["status"]) : ""
    backend = column["backend"] ? $(column["backend"]) : ""
    metal = first_existing(column["profileMetal"] ? $(column["profileMetal"]) : "", column["metal"] ? $(column["metal"]) : "")
    topStage = column["topStage"] ? $(column["topStage"]) : ""
    topStageMs = column["topStageMs"] ? $(column["topStageMs"]) : ""
    durationMs = column["durationMs"] ? $(column["durationMs"]) : ""
    linearPaths = column["linearPaths"] ? $(column["linearPaths"]) : ""
    logitsPaths = column["logitsPaths"] ? $(column["logitsPaths"]) : linearPaths
    ffnPaths = column["ffnPaths"] ? $(column["ffnPaths"]) : ""
    ffnStrategy = first_existing3(column["ffnStrategy"] ? $(column["ffnStrategy"]) : "", column["profileFfnStrategy"] ? $(column["profileFfnStrategy"]) : "", column["profile_ffn_strategy"] ? $(column["profile_ffn_strategy"]) : "")
    ffnRowPrefillNativeRows = first_existing3(column["ffnRowPrefillNativeRows"] ? $(column["ffnRowPrefillNativeRows"]) : "", column["profileFfnRowPrefillNativeRows"] ? $(column["profileFfnRowPrefillNativeRows"]) : "", column["profile_ffn_row_prefill_native_rows"] ? $(column["profile_ffn_row_prefill_native_rows"]) : "")
    ffnRowPrefillVariant = first_existing3(column["ffnRowPrefillVariant"] ? $(column["ffnRowPrefillVariant"]) : "", column["profileFfnRowPrefillVariant"] ? $(column["profileFfnRowPrefillVariant"]) : "", column["profile_ffn_row_prefill_variant"] ? $(column["profile_ffn_row_prefill_variant"]) : "")
    attentionPaths = column["attentionPaths"] ? $(column["attentionPaths"]) : ""
    argmaxPaths = column["argmaxPaths"] ? $(column["argmaxPaths"]) : ""
    for (i = 1; i <= metricCount; i++) {
      valueOf[metricName[i]] = column[metricName[i]] ? $(column[metricName[i]]) : ""
    }
  }
  function capture_row(prefix, i, metric) {
    selectedCase = rowCase
    selectedRows = 1
    selectedBackend = backend
    selectedMetal = metal
    selectedTopStage = topStage
    selectedLinearPaths = linearPaths
    selectedLogitsPaths = logitsPaths
    selectedFfnPaths = ffnPaths
    selectedFfnStrategy = ffnStrategy
    selectedFfnRowPrefillNativeRows = ffnRowPrefillNativeRows
    selectedFfnRowPrefillVariant = ffnRowPrefillVariant
    selectedAttentionPaths = attentionPaths
    selectedArgmaxPaths = argmaxPaths
    selected["durationMs"] = durationMs
    selected["topStageMs"] = topStageMs
    for (i = 1; i <= metricCount; i++) {
      metric = metricName[i]
      selected[metric] = valueOf[metric]
    }
  }
  function use_mean(prefix, label, i, metric) {
    selectedCase = label
    selectedRows = rows[prefix]
    selectedBackend = remembered[prefix, "backend"]
    selectedMetal = remembered[prefix, "metal"]
    selectedTopStage = remembered[prefix, "topStage"]
    selectedLinearPaths = remembered[prefix, "linearPaths"]
    selectedLogitsPaths = remembered[prefix, "logitsPaths"]
    selectedFfnPaths = remembered[prefix, "ffnPaths"]
    selectedFfnStrategy = remembered[prefix, "ffnStrategy"]
    selectedFfnRowPrefillNativeRows = remembered[prefix, "ffnRowPrefillNativeRows"]
    selectedFfnRowPrefillVariant = remembered[prefix, "ffnRowPrefillVariant"]
    selectedAttentionPaths = remembered[prefix, "attentionPaths"]
    selectedArgmaxPaths = remembered[prefix, "argmaxPaths"]
    selected["durationMs"] = mean_value(prefix, "durationMs")
    selected["topStageMs"] = mean_value(prefix, "topStageMs")
    for (i = 1; i <= metricCount; i++) {
      metric = metricName[i]
      selected[metric] = mean_value(prefix, metric)
    }
  }
  function recommendation(stage) {
    if (stage == "decode") {
      return "Focus decode token loop, KV cache reuse, Metal dispatch overhead, and per-token allocations."
    }
    if (stage == "prefill") {
      return "Focus prompt prefill, embedding/token preparation, session warmup, and attention prefill kernels."
    }
    if (stage == "attention") {
      return "Focus paged/flash attention routing, KV layout, Metal attention kernels, and CPU fallback evidence."
    }
    if (stage == "ffn") {
      return "Focus fused gated FFN/GEGLU, matvec policy, BF16/F16 conversion, and weight-buffer reuse."
    }
    if (stage == "logits") {
      return "Focus final projection, vocabulary scan, logits copy/select, and last-token matvec routing."
    }
    if (stage == "argmax") {
      return "Focus greedy argmax routing, native Metal bridge availability, and rejected-candidate filtering."
    }
    if (stage == "tpot") {
      return "Focus per-token latency: decode, sampling, logits, and cache reuse."
    }
    if (stage == "load") {
      return "Focus model load, mmap/cache reuse, and prepared session lifecycle."
    }
    if (stage == "tokenize") {
      return "Focus tokenizer/template caching and prompt construction overhead."
    }
    if (stage == "session") {
      return "Focus session acquisition, prepared model reuse, and daemon warm state."
    }
    if (stage == "sampling") {
      return "Focus sampler allocation, deterministic path, and candidate filtering."
    }
    if (stage == "logits_copy") {
      return "Focus device-to-host logits copy and last-token extraction."
    }
    return "Inspect detailed profile logs for this stage."
  }
  function path_evidence(stage) {
    if (stage == "attention") {
      return selectedAttentionPaths
    }
    if (stage == "ffn") {
      return selectedFfnPaths
    }
    if (stage == "logits") {
      return selectedLogitsPaths
    }
    if (stage == "argmax") {
      return selectedArgmaxPaths
    }
    return selectedLinearPaths
  }
  function path_metal_status(value, lowered) {
    value = clean(value)
    lowered = lower(value)
    if (value == "") {
      return "missing"
    }
    if (lowered == "mixed") {
      return "mixed"
    }
    if (lowered ~ /(metal|mtl|mps|gpu|native_argmax|fused-gated-ffn:accept|matvec-gated-ffn-prefill-rows:accept|native_bf16=true)/) {
      return "true"
    }
    return "false"
  }
  function combined_path_status(linear, logits, ffn, attention, argmax) {
    if (linear == "true" && logits == "true" && ffn == "true" && attention == "true" && argmax == "true") {
      return "pass"
    }
    if (linear == "mixed" || logits == "mixed" || ffn == "mixed" || attention == "mixed" || argmax == "mixed") {
      return "mixed"
    }
    if (linear == "false" || logits == "false" || ffn == "false" || attention == "false" || argmax == "false") {
      return "fail"
    }
    if (linear == "true" || logits == "true" || ffn == "true" || attention == "true" || argmax == "true") {
      return "pass"
    }
    return "fail"
  }
  function path_fallback_status(value, lowered) {
    value = clean(value)
    lowered = lower(value)
    if (value == "") {
      return "missing"
    }
    if (lowered == "mixed") {
      return "mixed"
    }
    gsub(/matvec-gated-ffn-prefill-rows:skip:strategy_prefers_fused_geglu_prefill/, "", lowered)
    if (lowered ~ /(cpu|java|accelerate|fallback|skip|reject|unavailable)/) {
      return "true"
    }
    return "false"
  }
  function combined_fallback_status(linear, logits, ffn, attention, argmax) {
    if (linear == "true" || logits == "true" || ffn == "true" || attention == "true" || argmax == "true") {
      return "fail"
    }
    if (linear == "mixed" || logits == "mixed" || ffn == "mixed" || attention == "mixed" || argmax == "mixed") {
      return "mixed"
    }
    return "pass"
  }
  function path_status_for_stage(stage) {
    if (stage == "attention") {
      return attentionPathMetal
    }
    if (stage == "ffn") {
      return ffnPathMetal
    }
    if (stage == "logits") {
      return logitsPathMetal
    }
    if (stage == "argmax") {
      return argmaxPathMetal
    }
    return linearPathMetal
  }
  function fallback_status_for_stage(stage) {
    if (stage == "attention") {
      return attentionPathFallback
    }
    if (stage == "ffn") {
      return ffnPathFallback
    }
    if (stage == "logits") {
      return logitsPathFallback
    }
    if (stage == "argmax") {
      return argmaxPathFallback
    }
    return linearPathFallback
  }
  function path_group_for_stage(stage) {
    if (stage == "attention") {
      return "attention"
    }
    if (stage == "ffn") {
      return "ffn"
    }
    if (stage == "logits") {
      return "logits"
    }
    if (stage == "argmax") {
      return "argmax"
    }
    return "linear"
  }
  function path_scope(group) {
    if (group == "attention") {
      return "attention"
    }
    if (group == "ffn") {
      return "ffn"
    }
    if (group == "logits") {
      return "logits"
    }
    if (group == "argmax") {
      return "argmax"
    }
    return "linear"
  }
  function group_evidence(group) {
    if (group == "attention") {
      return selectedAttentionPaths
    }
    if (group == "ffn") {
      return selectedFfnPaths
    }
    if (group == "logits") {
      return selectedLogitsPaths
    }
    if (group == "argmax") {
      return selectedArgmaxPaths
    }
    return selectedLinearPaths
  }
  function group_metal_status(group) {
    if (group == "attention") {
      return attentionPathMetal
    }
    if (group == "ffn") {
      return ffnPathMetal
    }
    if (group == "logits") {
      return logitsPathMetal
    }
    if (group == "argmax") {
      return argmaxPathMetal
    }
    return linearPathMetal
  }
  function group_fallback_status(group) {
    if (group == "attention") {
      return attentionPathFallback
    }
    if (group == "ffn") {
      return ffnPathFallback
    }
    if (group == "logits") {
      return logitsPathFallback
    }
    if (group == "argmax") {
      return argmaxPathFallback
    }
    return linearPathFallback
  }
  function path_row_status(metalStatus, fallbackStatus) {
    if (fallbackStatus == "true") {
      return "fallback"
    }
    if (fallbackStatus == "mixed" || metalStatus == "mixed") {
      return "mixed"
    }
    if (metalStatus == "true" && fallbackStatus == "false") {
      return "pass"
    }
    return "fail"
  }
  function group_action(group, metalStatus, fallbackStatus) {
    if (fallbackStatus == "true") {
      if (group == "attention") {
        return "Remove attention fallback markers before latency tuning."
      }
      if (group == "ffn") {
        return "Remove FFN fallback markers before latency tuning."
      }
      if (group == "logits") {
        return "Remove logits fallback markers before latency tuning."
      }
      if (group == "argmax") {
        return "Remove argmax fallback markers before latency tuning."
      }
      return "Remove linear fallback markers before latency tuning."
    }
    if (fallbackStatus == "mixed" || metalStatus == "mixed") {
      return "Stabilize repeated runs so this path reports one deterministic backend."
    }
    if (metalStatus == "false" || metalStatus == "missing") {
      if (group == "attention") {
        return "Route attention kernels through Metal."
      }
      if (group == "ffn") {
        return "Route gated FFN/GEGLU through Metal."
      }
      if (group == "logits") {
        return "Route final projection/logits matvec through Metal."
      }
      if (group == "argmax") {
        return "Route greedy argmax through native Metal."
      }
      return "Route linear matvec through Metal."
    }
    if (group == "attention") {
      return "Attention path is Metal-clean; tune latency only if it is a top stage."
    }
    if (group == "ffn") {
      return "FFN path is Metal-clean; tune fused GEGLU/matvec latency."
    }
    if (group == "logits") {
      return "Logits path is Metal-clean; tune final projection/vocabulary latency."
    }
    if (group == "argmax") {
      return "Argmax path is Metal-clean; tune greedy selection only if it is a top stage."
    }
    return "Linear path is Metal-clean; tune projection latency."
  }
  function ffn_strategy_action(strategy) {
    strategy = clean(strategy)
    if (strategy == "row_prefill_matvec_active") {
      return "Row-prefill matvec FFN is active; compare TTFT against fused GEGLU default and disable it if prefill regresses."
    }
    if (strategy == "fused_geglu_prefill_over_row_prefill") {
      return "Fused GEGLU prefill beat the row-prefill candidate; keep fused as default and use aqli.safetensor.prefer_metal_matvec_ffn_prefill_rows=true only for A/B tests."
    }
    if (strategy == "fused_geglu_prefill_active") {
      return "Fused GEGLU prefill is active; prioritize native BF16 fused/batched FFN tuning before row-prefill experiments."
    }
    if (strategy == "" || strategy == "n/a") {
      return "No FFN strategy metadata was present; rerun with a profile build that emits ffn_strategy for Gemma4 comparisons."
    }
    return "Review FFN strategy `" strategy "` with raw ffnPaths before changing routing policy."
  }
  function write_path_row(group, primaryGroup, metalStatus, fallbackStatus, evidence, status) {
    metalStatus = group_metal_status(group)
    fallbackStatus = group_fallback_status(group)
    evidence = group_evidence(group)
    status = path_row_status(metalStatus, fallbackStatus)
    print selectedCase, group, path_scope(group), metalStatus, fallbackStatus, status, (group == primaryGroup ? "true" : "false"), evidence, group_action(group, metalStatus, fallbackStatus) >> pathsOut
  }
  function next_action(stage, primaryPathMetalStatus, allPathStatus, primaryFallbackStatus, allFallbackStatus) {
    if (primaryFallbackStatus == "true") {
      if (stage == "attention") {
        return "Fix attention fallback evidence first: profile path includes CPU/Java/Accelerate/skip evidence before tuning timings."
      }
      if (stage == "ffn") {
        return "Fix FFN fallback evidence first: profile path includes CPU/Java/Accelerate/skip evidence before tuning timings."
      }
      if (stage == "logits") {
        return "Fix logits fallback evidence first: profile path includes CPU/Java/Accelerate/skip evidence before tuning timings."
      }
      if (stage == "argmax") {
        return "Fix argmax fallback evidence first: route greedy selection through native Metal before tuning timings."
      }
      return "Fix linear fallback evidence first: profile path includes CPU/Java/Accelerate/skip evidence before tuning timings."
    }
    if (primaryPathMetalStatus == "false" || primaryPathMetalStatus == "missing") {
      if (stage == "attention") {
        return "Fix attention CPU fallback first: route paged/flash attention through Metal before tuning timings."
      }
      if (stage == "ffn") {
        return "Fix FFN CPU fallback first: route gated FFN/GEGLU through Metal before tuning timings."
      }
      if (stage == "logits") {
        return "Fix logits CPU fallback first: route final projection through Metal before tuning timings."
      }
      if (stage == "argmax") {
        return "Fix argmax CPU fallback first: route greedy selection through native Metal before tuning timings."
      }
      return "Fix linear CPU fallback first: route matvec through Metal before tuning timings."
    }
    if (primaryFallbackStatus == "mixed" || allFallbackStatus == "mixed") {
      return "Stabilize mixed fallback evidence first: make repeated runs choose the same accelerated path before comparing latency."
    }
    if (primaryPathMetalStatus == "mixed" || allPathStatus == "mixed") {
      return "Stabilize mixed path evidence first: make repeated runs choose the same Metal path before comparing latency."
    }
    if (allFallbackStatus == "fail") {
      return "Primary stage looks accelerated, but another critical path reports fallback evidence; fix remaining fallback before latency tuning."
    }
    if (allPathStatus == "fail") {
      return "Primary stage is Metal, but another critical path is CPU; fix remaining fallback before latency tuning."
    }
    return recommendation(stage)
  }
  function write_diag_row(key, value) {
    print key, value >> diagnosisOut
  }
  function percent(value, denominator) {
    if (!numeric(value) || !numeric(denominator) || denominator + 0 == 0) {
      return ""
    }
    return sprintf("%.3f", (value + 0) * 100 / (denominator + 0))
  }
  function stage_priority(value, bestValue, stageIndex) {
    if (stageIndex > primaryMetricCount) {
      return "context"
    }
    if (!numeric(value) || !numeric(bestValue) || bestValue + 0 == 0) {
      return "normal"
    }
    if (value + 0 >= bestValue * 0.90) {
      return "primary"
    }
    if (value + 0 >= bestValue * 0.50) {
      return "secondary"
    }
    return "normal"
  }
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      column[$i] = i
    }
    next
  }
  NF > 0 {
    load_row_fields()
    if (caseName != "" && rowCase == caseName) {
      capture_row()
      foundRequested = 1
      next
    }
    if (caseName == "" && should_use(status)) {
      add_row("all")
      if (bool_true(metal) || lower(backend) ~ /(metal|gpu|mtl)/) {
        add_row("metal")
      }
    }
  }
  END {
    if (caseName != "" && !foundRequested) {
      print "Requested case not found: " caseName > "/dev/stderr"
      exit 3
    }
    if (selectedCase == "" && rows["metal"] > 0) {
      use_mean("metal", "metal-mean")
    } else if (selectedCase == "" && rows["all"] > 0) {
      use_mean("all", "mean")
    }
    if (selectedCase == "") {
      print "No safetensor profile rows selected for diagnosis" > "/dev/stderr"
      exit 3
    }
    bestStage = ""
    bestMetric = ""
    bestValue = ""
    for (i = 1; i <= primaryMetricCount; i++) {
      metric = metricName[i]
      value = selected[metric]
      if (numeric(value) && (bestValue == "" || value + 0 > bestValue + 0)) {
        bestStage = stageName[i]
        bestMetric = metric
        bestValue = value
      }
    }
    if (bestStage == "" && numeric(selected["topStageMs"])) {
      bestStage = selectedTopStage
      bestMetric = "topStageMs"
      bestValue = selected["topStageMs"]
    }
    if (bestStage == "") {
      print "No numeric safetensor profile metrics found for diagnosis" > "/dev/stderr"
      exit 3
    }
    linearPathMetal = path_metal_status(selectedLinearPaths)
    logitsPathMetal = path_metal_status(selectedLogitsPaths)
    ffnPathMetal = path_metal_status(selectedFfnPaths)
    attentionPathMetal = path_metal_status(selectedAttentionPaths)
    argmaxPathMetal = path_metal_status(selectedArgmaxPaths)
    metalPathStatus = combined_path_status(linearPathMetal, logitsPathMetal, ffnPathMetal, attentionPathMetal, argmaxPathMetal)
    primaryPathMetal = path_status_for_stage(bestStage)
    linearPathFallback = path_fallback_status(selectedLinearPaths)
    logitsPathFallback = path_fallback_status(selectedLogitsPaths)
    ffnPathFallback = path_fallback_status(selectedFfnPaths)
    attentionPathFallback = path_fallback_status(selectedAttentionPaths)
    argmaxPathFallback = path_fallback_status(selectedArgmaxPaths)
    pathFallbackStatus = combined_fallback_status(linearPathFallback, logitsPathFallback, ffnPathFallback, attentionPathFallback, argmaxPathFallback)
    primaryPathFallback = fallback_status_for_stage(bestStage)
    primaryPathGroup = path_group_for_stage(bestStage)
    action = next_action(bestStage, primaryPathMetal, metalPathStatus, primaryPathFallback, pathFallbackStatus)
    write_path_row("linear", primaryPathGroup)
    write_path_row("logits", primaryPathGroup)
    write_path_row("ffn", primaryPathGroup)
    write_path_row("attention", primaryPathGroup)
    write_path_row("argmax", primaryPathGroup)
    for (i = 1; i <= metricCount; i++) {
      metric = metricName[i]
      value = selected[metric]
      if (!numeric(value)) {
        continue
      }
      stage = stageName[i]
      priority = stage_priority(value + 0, bestValue + 0, i)
      print stage, metric, sprintf("%.3f", value + 0), percent(value, selected["durationMs"]), priority, path_evidence(stage), recommendation(stage) >> stagesOut
    }
    print "key", "value" > diagnosisOut
    write_diag_row("status", "pass")
    write_diag_row("selectedCase", selectedCase)
    write_diag_row("selectedRows", selectedRows)
    write_diag_row("backend", selectedBackend)
    write_diag_row("metal", selectedMetal)
    write_diag_row("durationMs", selected["durationMs"])
    write_diag_row("reportedTopStage", selectedTopStage)
    write_diag_row("reportedTopStageMs", selected["topStageMs"])
    write_diag_row("primaryStage", bestStage)
    write_diag_row("primaryMetric", bestMetric)
    write_diag_row("primaryValueMs", sprintf("%.3f", bestValue + 0))
    write_diag_row("primaryShareOfDurationPercent", percent(bestValue, selected["durationMs"]))
    write_diag_row("linearPaths", selectedLinearPaths)
    write_diag_row("logitsPaths", selectedLogitsPaths)
    write_diag_row("ffnPaths", selectedFfnPaths)
    write_diag_row("ffnStrategy", selectedFfnStrategy)
    write_diag_row("ffnRowPrefillNativeRows", selectedFfnRowPrefillNativeRows)
    write_diag_row("ffnRowPrefillVariant", selectedFfnRowPrefillVariant)
    write_diag_row("attentionPaths", selectedAttentionPaths)
    write_diag_row("argmaxPaths", selectedArgmaxPaths)
    write_diag_row("linearPathMetal", linearPathMetal)
    write_diag_row("logitsPathMetal", logitsPathMetal)
    write_diag_row("ffnPathMetal", ffnPathMetal)
    write_diag_row("attentionPathMetal", attentionPathMetal)
    write_diag_row("argmaxPathMetal", argmaxPathMetal)
    write_diag_row("metalPathStatus", metalPathStatus)
    write_diag_row("primaryPathMetal", primaryPathMetal)
    write_diag_row("linearPathFallback", linearPathFallback)
    write_diag_row("logitsPathFallback", logitsPathFallback)
    write_diag_row("ffnPathFallback", ffnPathFallback)
    write_diag_row("attentionPathFallback", attentionPathFallback)
    write_diag_row("argmaxPathFallback", argmaxPathFallback)
    write_diag_row("pathFallbackStatus", pathFallbackStatus)
    write_diag_row("primaryPathFallback", primaryPathFallback)
    write_diag_row("ffnStrategyAction", ffn_strategy_action(selectedFfnStrategy))
    write_diag_row("nextAction", action)
    write_diag_row("recommendation", recommendation(bestStage))
  }
' "$SUMMARY"

PRIMARY_STAGE="$(awk 'BEGIN { FS = "\t" } $1 == "primaryStage" { print $2; exit }' "$OUT")"
PRIMARY_METRIC="$(awk 'BEGIN { FS = "\t" } $1 == "primaryMetric" { print $2; exit }' "$OUT")"
PRIMARY_VALUE="$(awk 'BEGIN { FS = "\t" } $1 == "primaryValueMs" { print $2; exit }' "$OUT")"
METAL_PATH_STATUS="$(awk 'BEGIN { FS = "\t" } $1 == "metalPathStatus" { print $2; exit }' "$OUT")"
PRIMARY_PATH_METAL="$(awk 'BEGIN { FS = "\t" } $1 == "primaryPathMetal" { print $2; exit }' "$OUT")"
PATH_FALLBACK_STATUS="$(awk 'BEGIN { FS = "\t" } $1 == "pathFallbackStatus" { print $2; exit }' "$OUT")"
PRIMARY_PATH_FALLBACK="$(awk 'BEGIN { FS = "\t" } $1 == "primaryPathFallback" { print $2; exit }' "$OUT")"
FFN_STRATEGY="$(awk 'BEGIN { FS = "\t" } $1 == "ffnStrategy" { print $2; exit }' "$OUT")"
FFN_ROW_PREFILL_NATIVE_ROWS="$(awk 'BEGIN { FS = "\t" } $1 == "ffnRowPrefillNativeRows" { print $2; exit }' "$OUT")"
FFN_ROW_PREFILL_VARIANT="$(awk 'BEGIN { FS = "\t" } $1 == "ffnRowPrefillVariant" { print $2; exit }' "$OUT")"
FFN_STRATEGY_ACTION="$(awk 'BEGIN { FS = "\t" } $1 == "ffnStrategyAction" { value = $0; sub(/^[^\t]*\t/, "", value); print value; exit }' "$OUT")"
NEXT_ACTION="$(awk 'BEGIN { FS = "\t" } $1 == "nextAction" { value = $0; sub(/^[^\t]*\t/, "", value); print value; exit }' "$OUT")"
RECOMMENDATION="$(awk 'BEGIN { FS = "\t" } $1 == "recommendation" { value = $0; sub(/^[^\t]*\t/, "", value); print value; exit }' "$OUT")"

{
  echo "Aqli safetensor profile diagnosis"
  echo "summaryDir=$SUMMARY_DIR"
  echo "artifacts.config=$CONFIG"
  echo "artifacts.diagnosis=$OUT"
  echo "artifacts.stages=$STAGES_OUT"
  echo "artifacts.paths=$PATHS_OUT"
  echo "primaryStage=$PRIMARY_STAGE"
  echo "primaryMetric=$PRIMARY_METRIC"
  echo "primaryValueMs=$PRIMARY_VALUE"
  echo "metalPathStatus=$METAL_PATH_STATUS"
  echo "primaryPathMetal=$PRIMARY_PATH_METAL"
  echo "pathFallbackStatus=$PATH_FALLBACK_STATUS"
  echo "primaryPathFallback=$PRIMARY_PATH_FALLBACK"
  echo "ffnStrategy=$FFN_STRATEGY"
  echo "ffnRowPrefillNativeRows=$FFN_ROW_PREFILL_NATIVE_ROWS"
  echo "ffnRowPrefillVariant=$FFN_ROW_PREFILL_VARIANT"
  echo "ffnStrategyAction=$FFN_STRATEGY_ACTION"
  echo "nextAction=$NEXT_ACTION"
  echo "recommendation=$RECOMMENDATION"
} | tee "$REPORT"
