#!/usr/bin/env bash

# Shared argument builder for scripts/verify-mobile-edge-example.sh.
# Prints one shell argument per line so callers can preserve spaces with read -r.

mobile_edge_verify_truthy() {
  case "${1:-}" in
    1|true|TRUE|yes|YES|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}

mobile_edge_verify_trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

mobile_edge_verify_print_arg() {
  printf '%s\n' "$1"
}

mobile_edge_verify_print_csv_requirements() {
  local flag="$1"
  local csv="$2"
  local entry
  local trimmed
  local -a entries

  [[ -n "$csv" ]] || return 0

  IFS=',' read -r -a entries <<< "$csv"
  for entry in "${entries[@]}"; do
    trimmed="$(mobile_edge_verify_trim "$entry")"
    if [[ -n "$trimmed" ]]; then
      mobile_edge_verify_print_arg "$flag"
      mobile_edge_verify_print_arg "$trimmed"
    fi
  done
}

mobile_edge_verify_print_linked_runtime_requirements() {
  local csv="$1"
  local entry
  local trimmed
  local -a entries

  [[ -n "$csv" ]] || return 0

  IFS=',' read -r -a entries <<< "$csv"
  for entry in "${entries[@]}"; do
    trimmed="$(mobile_edge_verify_trim "$entry")"
    case "$trimmed" in
      litert|LiteRT|LITERT)
        mobile_edge_verify_print_arg "--require-linked-runtime"
        mobile_edge_verify_print_arg "litert"
        ;;
      onnx|ONNX|Onnx)
        mobile_edge_verify_print_arg "--require-linked-runtime"
        mobile_edge_verify_print_arg "onnx"
        ;;
    esac
  done
}

mobile_edge_verify_print_edge_model_format_requirements() {
  local csv="$1"
  local entry
  local trimmed
  local -a entries

  [[ -n "$csv" ]] || return 0

  IFS=',' read -r -a entries <<< "$csv"
  for entry in "${entries[@]}"; do
    trimmed="$(mobile_edge_verify_trim "$entry")"
    case "$trimmed" in
      litert|LiteRT|LITERT)
        mobile_edge_verify_print_arg "--require-edge-model-format"
        mobile_edge_verify_print_arg "litert"
        ;;
      onnx|ONNX|Onnx)
        mobile_edge_verify_print_arg "--require-edge-model-format"
        mobile_edge_verify_print_arg "onnx"
        ;;
    esac
  done
}

mobile_edge_print_verify_args() {
  local required_formats
  local required_features
  local required_abis
  local required_linked_runtimes
  local edge_model_dir
  local required_edge_formats
  local -a extra_verify_args
  local arg

  if ! mobile_edge_verify_truthy "${AQLI_EDGE_VERIFY_BUNDLE_AUDIT:-false}"; then
    mobile_edge_verify_print_arg "--skip-bundle-audit"
  fi

  if ! mobile_edge_verify_truthy "${AQLI_EDGE_VERIFY_ANDROID:-false}"; then
    mobile_edge_verify_print_arg "--skip-android"
  fi

  if mobile_edge_verify_truthy "${AQLI_EDGE_VERIFY_CAPABILITIES:-true}"; then
    required_formats="${AQLI_EDGE_VERIFY_REQUIRED_FORMATS:-${MOBILE_FORMAT_TARGETS:-${FORMAT_TARGETS:-}}}"
    required_features="${AQLI_EDGE_VERIFY_REQUIRED_FEATURES:-text}"
    required_abis="${AQLI_EDGE_VERIFY_REQUIRED_ANDROID_ABIS:-${AQLI_EDGE_ANDROID_ABIS:-arm64-v8a}}"
    required_linked_runtimes="${AQLI_EDGE_VERIFY_REQUIRED_LINKED_RUNTIMES:-}"

    mobile_edge_verify_print_arg "--require-capability-assets"
    mobile_edge_verify_print_arg "--require-mobile-runtime"
    mobile_edge_verify_print_csv_requirements "--require-format" "$required_formats"
    mobile_edge_verify_print_csv_requirements "--require-feature" "$required_features"
    mobile_edge_verify_print_csv_requirements "--require-android-abi" "$required_abis"

    if [[ -n "$required_linked_runtimes" ]]; then
      mobile_edge_verify_print_csv_requirements "--require-linked-runtime" "$required_linked_runtimes"
    else
      mobile_edge_verify_print_linked_runtime_requirements "$required_formats"
    fi
  fi

  edge_model_dir="${AQLI_EDGE_VERIFY_EDGE_MODEL_DIR:-${AQLI_EDGE_DIR:-}}"
  if [[ -n "$edge_model_dir" ]]; then
    mobile_edge_verify_print_arg "--edge-model-dir"
    mobile_edge_verify_print_arg "$edge_model_dir"
  fi

  required_edge_formats="${AQLI_EDGE_VERIFY_REQUIRED_EDGE_MODEL_FORMATS:-}"
  if mobile_edge_verify_truthy "${AQLI_EDGE_VERIFY_EDGE_MODELS:-false}" || [[ -n "$required_edge_formats" ]]; then
    mobile_edge_verify_print_arg "--require-edge-model-dir"
    if [[ -n "$required_edge_formats" ]]; then
      mobile_edge_verify_print_csv_requirements "--require-edge-model-format" "$required_edge_formats"
    else
      mobile_edge_verify_print_edge_model_format_requirements \
        "${AQLI_EDGE_VERIFY_REQUIRED_FORMATS:-${MOBILE_FORMAT_TARGETS:-${FORMAT_TARGETS:-}}}"
    fi
  fi

  if [[ -n "${AQLI_EDGE_VERIFY_ARGS:-}" ]]; then
    read -r -a extra_verify_args <<< "$AQLI_EDGE_VERIFY_ARGS"
    for arg in "${extra_verify_args[@]}"; do
      mobile_edge_verify_print_arg "$arg"
    done
  fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -euo pipefail
  mobile_edge_print_verify_args "$@"
fi
