#!/usr/bin/env bash

aqli_safetensor_performance_preset_names() {
  printf '%s\n' m4-smoke m4-greedy-10 m4-gemma4-12b-smoke m4-gemma4-12b-row-prefill-ab
}

aqli_safetensor_performance_preset_names_csv() {
  printf 'm4-smoke,m4-greedy-10,m4-gemma4-12b-smoke,m4-gemma4-12b-row-prefill-ab'
}

aqli_safetensor_performance_preset_description() {
  case "$1" in
    m4-smoke)
      printf 'M4 Metal smoke gate with real Metal path proof, full coverage, fallback rejection, and bounded safetensor profile stages'
      ;;
    m4-greedy-10)
      printf 'M4 greedy 10-token Jakarta smoke with real Metal path proof, full coverage, fallback rejection, and bounded safetensor profile stages'
      ;;
    m4-gemma4-12b-smoke)
      printf 'M4 Gemma4 12B smoke gate with Metal path proof, fallback rejection, and fused GEGLU-over-row-prefill strategy proof'
      ;;
    m4-gemma4-12b-row-prefill-ab)
      printf 'M4 Gemma4 12B row-prefill A/B gate with Metal path proof and row-prefill strategy proof'
      ;;
    *)
      return 1
      ;;
  esac
}

aqli_safetensor_performance_preset_validate() {
  case "$1" in
    ""|m4-smoke|m4-greedy-10|m4-gemma4-12b-smoke|m4-gemma4-12b-row-prefill-ab) return 0 ;;
    *) return 1 ;;
  esac
}

aqli_safetensor_emit_m4_metal_path_gates() {
  printf '%s\t%s\n' profile true
  printf '%s\t%s\n' requireProfile true
  printf '%s\t%s\n' requireMetal true
  printf '%s\t%s\n' requireMetalPaths true
  printf '%s\t%s\n' rejectFallbackPaths true
  printf '%s\t%s\n' minCoreMetalCoverage 1.0
  printf '%s\t%s\n' minLinearMetalCoverage 1.0
  printf '%s\t%s\n' minLogitsMetalCoverage 1.0
  printf '%s\t%s\n' minFfnMetalCoverage 1.0
  printf '%s\t%s\n' minAttentionMetalCoverage 1.0
  printf '%s\t%s\n' minArgmaxMetalCoverage 1.0
}

aqli_safetensor_emit_m4_smoke_thresholds() {
  printf '%s\t%s\n' minSpeedTps 1.0
  printf '%s\t%s\n' topStageThresholdMs 5000
  printf '%s\t%s\n' prefillThresholdMs 5000
  printf '%s\t%s\n' decodeThresholdMs 5000
  printf '%s\t%s\n' tpotThresholdMs 2000
  printf '%s\t%s\n' samplingThresholdMs 1000
  printf '%s\t%s\n' argmaxThresholdMs 500
  printf '%s\t%s\n' attentionThresholdMs 2500
  printf '%s\t%s\n' ffnThresholdMs 2500
  printf '%s\t%s\n' logitsThresholdMs 1500
}

aqli_safetensor_emit_m4_gemma4_12b_smoke_thresholds() {
  printf '%s\t%s\n' minSpeedTps 0.1
  printf '%s\t%s\n' topStageThresholdMs 60000
  printf '%s\t%s\n' prefillThresholdMs 60000
  printf '%s\t%s\n' decodeThresholdMs 60000
  printf '%s\t%s\n' tpotThresholdMs 10000
  printf '%s\t%s\n' samplingThresholdMs 2000
  printf '%s\t%s\n' argmaxThresholdMs 1000
  printf '%s\t%s\n' attentionThresholdMs 30000
  printf '%s\t%s\n' ffnThresholdMs 30000
  printf '%s\t%s\n' logitsThresholdMs 5000
}

aqli_safetensor_performance_preset_defaults() {
  case "$1" in
    m4-smoke)
      aqli_safetensor_emit_m4_metal_path_gates
      printf '%s\t%s\n' maxTokens 3
      aqli_safetensor_emit_m4_smoke_thresholds
      ;;
    m4-greedy-10)
      aqli_safetensor_emit_m4_metal_path_gates
      printf '%s\t%s\n' maxTokens 10
      printf '%s\t%s\n' requireAnswerRegex 'jakarta.*indonesia|indonesia.*jakarta'
      printf '%s\t%s\n' maxRepeatedTokenRun 2
      printf '%s\t%s\n' minChunks 2
      aqli_safetensor_emit_m4_smoke_thresholds
      ;;
    m4-gemma4-12b-smoke)
      aqli_safetensor_emit_m4_metal_path_gates
      printf '%s\t%s\n' requireFfnStrategy fused_geglu_prefill_over_row_prefill
      printf '%s\t%s\n' maxTokens 3
      aqli_safetensor_emit_m4_gemma4_12b_smoke_thresholds
      ;;
    m4-gemma4-12b-row-prefill-ab)
      aqli_safetensor_emit_m4_metal_path_gates
      printf '%s\t%s\n' requireFfnStrategy row_prefill_matvec_active
      printf '%s\t%s\n' javaOpt -Daqli.safetensor.enable_metal_matvec_ffn_prefill_rows=true
      printf '%s\t%s\n' javaOpt -Daqli.safetensor.prefer_metal_matvec_ffn_prefill_rows=true
      printf '%s\t%s\n' maxTokens 3
      aqli_safetensor_emit_m4_gemma4_12b_smoke_thresholds
      ;;
    "")
      ;;
    *)
      return 1
      ;;
  esac
}

aqli_safetensor_performance_preset_list_tsv() {
  local name
  printf 'name\tdescription\n'
  while IFS= read -r name; do
    printf '%s\t%s\n' "$name" "$(aqli_safetensor_performance_preset_description "$name")"
  done < <(aqli_safetensor_performance_preset_names)
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -euo pipefail
  case "${1:-list}" in
    list|--list)
      aqli_safetensor_performance_preset_list_tsv
      ;;
    names)
      aqli_safetensor_performance_preset_names
      ;;
    defaults)
      if [[ $# -ne 2 ]]; then
        echo "Usage: safetensor-performance-presets.sh defaults PRESET" >&2
        exit 2
      fi
      if ! aqli_safetensor_performance_preset_defaults "$2"; then
        echo "Unknown preset: $2" >&2
        exit 2
      fi
      ;;
    validate)
      if [[ $# -ne 2 ]]; then
        echo "Usage: safetensor-performance-presets.sh validate PRESET" >&2
        exit 2
      fi
      aqli_safetensor_performance_preset_validate "$2"
      ;;
    *)
      echo "Usage: safetensor-performance-presets.sh [list|names|defaults PRESET|validate PRESET]" >&2
      exit 2
      ;;
  esac
fi
