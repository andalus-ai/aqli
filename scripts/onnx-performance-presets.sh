#!/usr/bin/env bash

aqli_onnx_performance_preset_names() {
  printf '%s\n' quick stable coreml-stable
}

aqli_onnx_performance_preset_names_csv() {
  printf 'quick, stable, coreml-stable'
}

aqli_onnx_performance_preset_description() {
  case "$1" in
    quick)
      printf 'Light smoke run: 1 token, 1 run, no warmup'
      ;;
    stable)
      printf 'Stable run: 5 runs, 1 warmup, noise gate for regression/capture'
      ;;
    coreml-stable)
      printf 'Stable run with CoreML backend expectation'
      ;;
    *)
      return 1
      ;;
  esac
}

aqli_onnx_performance_preset_validate() {
  case "$1" in
    ""|quick|stable|coreml-stable) return 0 ;;
    *) return 1 ;;
  esac
}

aqli_onnx_performance_preset_args() {
  local preset="$1"
  local mode="$2"
  case "$preset" in
    quick)
      printf '%s\n' "--max-tokens" "1" "--runs" "1" "--warmup-runs" "0"
      ;;
    stable)
      printf '%s\n' "--runs" "5" "--warmup-runs" "1"
      if [[ "$mode" != "gate" ]]; then
        printf '%s\n' "--max-noise-percent" "10" "--noise-metrics" "durationMs,generationTps,onnxOrtRunMs"
      fi
      ;;
    coreml-stable)
      printf '%s\n' "--runs" "5" "--warmup-runs" "1" "--expect-backend" "CoreML"
      if [[ "$mode" != "gate" ]]; then
        printf '%s\n' "--max-noise-percent" "10" "--noise-metrics" "durationMs,generationTps,onnxOrtRunMs"
      fi
      ;;
    "")
      ;;
    *)
      return 1
      ;;
  esac
}

aqli_onnx_performance_preset_list_tsv() {
  local name
  printf 'name\tdescription\n'
  while IFS= read -r name; do
    printf '%s\t%s\n' "$name" "$(aqli_onnx_performance_preset_description "$name")"
  done < <(aqli_onnx_performance_preset_names)
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -euo pipefail
  case "${1:-list}" in
    list|--list)
      aqli_onnx_performance_preset_list_tsv
      ;;
    names)
      aqli_onnx_performance_preset_names
      ;;
    args)
      if [[ $# -ne 3 ]]; then
        echo "Usage: onnx-performance-presets.sh args PRESET MODE" >&2
        exit 2
      fi
      if ! aqli_onnx_performance_preset_args "$2" "$3"; then
        echo "Unknown preset: $2" >&2
        exit 2
      fi
      ;;
    validate)
      if [[ $# -ne 2 ]]; then
        echo "Usage: onnx-performance-presets.sh validate PRESET" >&2
        exit 2
      fi
      aqli_onnx_performance_preset_validate "$2"
      ;;
    *)
      echo "Usage: onnx-performance-presets.sh [list|names|args PRESET MODE|validate PRESET]" >&2
      exit 2
      ;;
  esac
fi
