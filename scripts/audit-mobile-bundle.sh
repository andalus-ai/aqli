#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLUGIN_DIR="${AQLI_MOBILE_PLUGIN_DIR:-${ROOT_DIR}/../mobile/aqli_edge}"

if [[ ! -d "${PLUGIN_DIR}" ]]; then
  echo "Mobile plugin directory not found: ${PLUGIN_DIR}" >&2
  exit 1
fi

PLUGIN_DIR="$(cd "${PLUGIN_DIR}" && pwd)"
cd "${PLUGIN_DIR}"

declare -a DART_CANDIDATES=()
DART_CANDIDATE_COUNT=0

add_dart_candidate() {
  local candidate="$1"
  local existing
  local index
  [[ -n "$candidate" && -x "$candidate" ]] || return 0
  for ((index = 0; index < DART_CANDIDATE_COUNT; index += 1)); do
    existing="${DART_CANDIDATES[$index]}"
    [[ "$existing" == "$candidate" ]] && return 0
  done
  DART_CANDIDATES[$DART_CANDIDATE_COUNT]="$candidate"
  DART_CANDIDATE_COUNT=$((DART_CANDIDATE_COUNT + 1))
}

add_flutter_sdk_dart_candidate() {
  local command_path="$1"
  local dart_sdk_path
  [[ -n "$command_path" ]] || return 0
  dart_sdk_path="$(dirname "$command_path")/cache/dart-sdk/bin/dart"
  add_dart_candidate "$dart_sdk_path"
}

run_audit_with_dart() {
  local dart_bin="$1"
  local package_config="$PLUGIN_DIR/.dart_tool/package_config.json"
  local audit_home="${AQLI_EDGE_AUDIT_HOME:-$PLUGIN_DIR/.dart_tool/aqli_edge_audit_home}"
  shift

  if [[ -f "$package_config" && "${AQLI_EDGE_AUDIT_DIRECT_DART:-true}" != "false" ]]; then
    "$dart_bin" --packages="$package_config" "$PLUGIN_DIR/tool/mobile_bundle_audit.dart" "$@"
    return $?
  fi

  mkdir -p "$audit_home"
  HOME="$audit_home" \
    DART_SUPPRESS_ANALYTICS=true \
    "$dart_bin" run tool/mobile_bundle_audit.dart "$@"
}

if [[ -n "${AQLI_EDGE_DART_BIN:-}" ]]; then
  add_dart_candidate "$AQLI_EDGE_DART_BIN"
fi

if command -v dart >/dev/null 2>&1; then
  DART_PATH="$(command -v dart)"
  if [[ "$DART_PATH" == */flutter/bin/dart ]]; then
    add_flutter_sdk_dart_candidate "$DART_PATH"
  else
    add_dart_candidate "$DART_PATH"
  fi
fi

if command -v flutter >/dev/null 2>&1; then
  add_flutter_sdk_dart_candidate "$(command -v flutter)"
fi

if (( DART_CANDIDATE_COUNT == 0 )); then
  echo "Neither Dart nor Flutter's bundled Dart SDK is on PATH; cannot run mobile bundle audit." >&2
  exit 1
fi

last_status=1
for ((index = 0; index < DART_CANDIDATE_COUNT; index += 1)); do
  dart_bin="${DART_CANDIDATES[$index]}"
  set +e
  run_audit_with_dart "$dart_bin" "$@"
  last_status=$?
  set -e
  if [[ "$last_status" == "0" ]]; then
    exit 0
  fi
  if [[ "$last_status" == "2" || "$last_status" == "3" ]]; then
    exit "$last_status"
  fi
done

exit "$last_status"
