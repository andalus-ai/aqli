#!/bin/bash
set -euo pipefail

RESET="$(printf '\033[0m')"
BOLD="$(printf '\033[1m')"
GREEN="$(printf '\033[32m')"
CYAN="$(printf '\033[36m')"
YELLOW="$(printf '\033[33m')"
MAGENTA="$(printf '\033[35m')"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
source "$ROOT_DIR/scripts/module-selection-current.env"
source "$ROOT_DIR/scripts/mobile-edge-verify-args.sh"

print_usage() {
  cat <<'EOF_USAGE'
Usage: ./scripts/build-mobile.sh [options]

Options:
  --dry-run                         Print native, Gradle, and verifier commands without running them.
  --verify-only                     Run only the Aqli Edge example verifier.
  --edge-model-dir <path>           Scan this model directory during example verification.
  --require-edge-models             Require the edge model directory to contain supported models.
  --require-edge-model-format <fmt> Require an edge model format, e.g. litert or onnx. Repeatable.
  --summary-json <path>             Write the verifier summary JSON to this path.
  --skip-verify                     Skip the Aqli Edge example verifier.
  -h, --help                        Show this help.

Environment variables remain supported for CI, including AQLI_EDGE_DIR,
AQLI_EDGE_VERIFY_EDGE_MODELS, and AQLI_MOBILE_BUILD_DRY_RUN.
EOF_USAGE
}

append_csv_env_value() {
  local var_name="$1"
  local value="$2"
  local current="${!var_name:-}"
  if [[ -n "$current" ]]; then
    printf -v "$var_name" '%s,%s' "$current" "$value"
  else
    printf -v "$var_name" '%s' "$value"
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      AQLI_MOBILE_BUILD_DRY_RUN=true
      shift
      ;;
    --verify-only)
      AQLI_MOBILE_BUILD_VERIFY_ONLY=true
      shift
      ;;
    --edge-model-dir)
      if [[ $# -lt 2 || -z "$2" ]]; then
        echo "Missing value for --edge-model-dir" >&2
        print_usage >&2
        exit 64
      fi
      AQLI_EDGE_VERIFY_EDGE_MODEL_DIR="$2"
      shift 2
      ;;
    --require-edge-models|--require-models)
      AQLI_EDGE_VERIFY_EDGE_MODELS=true
      shift
      ;;
    --require-edge-model-format|--require-model-format)
      if [[ $# -lt 2 || -z "$2" ]]; then
        echo "Missing value for --require-edge-model-format" >&2
        print_usage >&2
        exit 64
      fi
      append_csv_env_value AQLI_EDGE_VERIFY_REQUIRED_EDGE_MODEL_FORMATS "$2"
      shift 2
      ;;
    --summary-json)
      if [[ $# -lt 2 || -z "$2" ]]; then
        echo "Missing value for --summary-json" >&2
        print_usage >&2
        exit 64
      fi
      AQLI_EDGE_VERIFY_SUMMARY_JSON="$2"
      shift 2
      ;;
    --skip-verify)
      AQLI_EDGE_VERIFY_EXAMPLE=false
      shift
      ;;
    -h|--help)
      print_usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    *)
      echo "Unknown option: $1" >&2
      print_usage >&2
      exit 64
      ;;
  esac
done

if [[ $# -gt 0 ]]; then
  echo "Unexpected arguments: $*" >&2
  print_usage >&2
  exit 64
fi

ARCHITECTURE_VALUE="${ARCHITECTURE_TARGETS:-${ARCHITECTURE_TARGET:-native-java,binding}}"
RUNTIME_TARGET_VALUE="${RUNTIME_TARGET:-backend}"
MOBILE_PLUGIN_DIR_VALUE="${MOBILE_PLUGIN_DIR:-$ROOT_DIR/../mobile/aqli_edge}"
EDGE_MODEL_DIR_VALUE="${AQLI_EDGE_VERIFY_EDGE_MODEL_DIR:-${AQLI_EDGE_DIR:-}}"
DRY_RUN_VALUE="${AQLI_MOBILE_BUILD_DRY_RUN:-${AQLI_EDGE_RUN_BUILD_MOBILE_DRY_RUN:-false}}"
VERIFY_ONLY_VALUE="${AQLI_MOBILE_BUILD_VERIFY_ONLY:-${AQLI_EDGE_RUN_BUILD_MOBILE_VERIFY_ONLY:-false}}"
if command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA_25_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
  if [[ -n "${JAVA_25_HOME}" ]]; then
    export JAVA_HOME="${JAVA_25_HOME}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
  fi
fi
GRADLE_JAVA_HOME_ARG=()
if [[ -n "${JAVA_HOME:-}" ]]; then
  GRADLE_JAVA_HOME_ARG+=("-Dorg.gradle.java.home=${JAVA_HOME}")
fi
append_java_tool_option() {
  local option="$1"
  case " ${JAVA_TOOL_OPTIONS:-} " in
    *" ${option} "*) ;;
    *) JAVA_TOOL_OPTIONS="${option}${JAVA_TOOL_OPTIONS:+ ${JAVA_TOOL_OPTIONS}}" ;;
  esac
}
append_java_tool_option "--enable-native-access=ALL-UNNAMED"
append_java_tool_option "--add-modules=jdk.incubator.vector"
export JAVA_TOOL_OPTIONS
truthy() {
  case "${1:-}" in
    1|true|TRUE|yes|YES|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}
print_dry_run_command() {
  local label="$1"
  shift
  printf 'dry-run: %s' "$label"
  for arg in "$@"; do
    printf ' [%s]' "$arg"
  done
  printf '\n'
}
is_legacy_gradle_module() {
  case "$1" in
    :ml:*|:trainer:*|:examples:aqli-ml-examples|:models:aqli-model-common|:runner:onnx:aqli-ml-export-onnx|:runner:onnx:aqli-ml-onnx|:runner:diffuser:aqli-diffuser) return 0 ;;
    *) return 1 ;;
  esac
}
is_mobile_excluded_gradle_module() {
  case "$1" in
    :ui:*|:sdk:aqli-sdk-local|:runner:gguf:*|:core:plugin:aqli-plugin-runner-gguf) return 0 ;;
    *) return 1 ;;
  esac
}
is_existing_gradle_module() {
  local module="$1"
  local path="${module#:}"
  path="${path//://}"
  [[ -f "$ROOT_DIR/$path/build.gradle.kts" || -f "$ROOT_DIR/$path/build.gradle" ]]
}
read -r -a MODULE_ARRAY <<< "${MODULES:-}"
SUPPORTED_MODULES=()
SKIPPED_MODULES=()
MOBILE_SKIPPED_MODULES=()
MISSING_MODULES=()
for module in "${MODULE_ARRAY[@]}"; do
  if is_legacy_gradle_module "$module"; then
    SKIPPED_MODULES+=("$module")
  elif [[ "${MOBILE_PROFILE:-false}" == "true" ]] && is_mobile_excluded_gradle_module "$module"; then
    MOBILE_SKIPPED_MODULES+=("$module")
  elif ! is_existing_gradle_module "$module"; then
    MISSING_MODULES+=("$module")
  else
    SUPPORTED_MODULES+=("$module")
  fi
done
GRADLE_MOBILE_TASK="${AQLI_MOBILE_GRADLE_TASK:-jar}"
GRADLE_BUILD_TASKS=()
for module in ${SUPPORTED_MODULES[@]+"${SUPPORTED_MODULES[@]}"}; do
  GRADLE_BUILD_TASKS+=("${module}:${GRADLE_MOBILE_TASK}")
done

echo "${BOLD}${CYAN}:) Runtime target:${RESET} ${RUNTIME_TARGET_VALUE}"
echo "${BOLD}${CYAN}:) Resolved backend targets:${RESET} ${BACKEND_TARGETS}"
echo "${BOLD}${CYAN}:) Resolved format targets:${RESET} ${FORMAT_TARGETS}"
echo "${BOLD}${CYAN}:) Resolved LLM targets:${RESET} ${LLM_TARGETS}"
if [[ "${MOBILE_PROFILE:-false}" == "true" ]] || truthy "$VERIFY_ONLY_VALUE"; then
  echo "${BOLD}${CYAN}:) Mobile model formats:${RESET} ${MOBILE_FORMAT_TARGETS:-}"
  echo "${BOLD}${CYAN}:) Mobile features:${RESET} ${MOBILE_FEATURES:-}"
  echo "${BOLD}${CYAN}:) Mobile plugin:${RESET} ${MOBILE_PLUGIN_DIR_VALUE}"
  if [[ -n "${EDGE_MODEL_DIR_VALUE}" ]]; then
    echo "${BOLD}${CYAN}:) Edge model directory:${RESET} ${EDGE_MODEL_DIR_VALUE}"
  fi
  echo "${BOLD}${GREEN}:) Mobile profile JSON:${RESET} ${MOBILE_PROFILE_FILE:-${MOBILE_PLUGIN_DIR_VALUE}/assets/aqli_edge_profile.json}"
fi
echo "${BOLD}${MAGENTA}:) Architecture:${RESET} ${ARCHITECTURE_VALUE}"
echo "${BOLD}${MAGENTA}:) Profile:${RESET} ${BUILD_PROFILE}"
echo "${BOLD}${MAGENTA}:) Java home:${RESET} ${JAVA_HOME:-$(command -v java)}"
echo "${BOLD}${MAGENTA}:) Build mode:${RESET} native mobile runtime + Gradle ${GRADLE_MOBILE_TASK} packaging + Flutter plugin check"
echo "${BOLD}${GREEN}:) Selection manifest:${RESET} ${ROOT_DIR}/scripts/module-selection-current.env"
if truthy "$DRY_RUN_VALUE"; then
  echo "${BOLD}${YELLOW}:| Dry run:${RESET} commands will be printed instead of executed"
fi
if truthy "$VERIFY_ONLY_VALUE"; then
  echo "${BOLD}${YELLOW}:| Verify only:${RESET} native runtime, bundle audit, and Gradle build will be skipped"
fi
if [[ -n "${WARNINGS:-}" ]]; then
  echo "${YELLOW}:| Warning:${RESET} ${WARNINGS}"
fi

if [[ ${#SKIPPED_MODULES[@]} -gt 0 ]]; then
  echo "${BOLD}${YELLOW}:| Warning:${RESET} skipping legacy Gradle modules in wrapper build:"
  for module in "${SKIPPED_MODULES[@]}"; do
    echo "   - ${module}"
  done
fi

if [[ ${#MOBILE_SKIPPED_MODULES[@]} -gt 0 ]]; then
  echo "${BOLD}${YELLOW}:| Warning:${RESET} skipping non-mobile Gradle modules in wrapper build:"
  for module in "${MOBILE_SKIPPED_MODULES[@]}"; do
    echo "   - ${module}"
  done
fi

if [[ ${#MISSING_MODULES[@]} -gt 0 ]]; then
  echo "${BOLD}${YELLOW}:| Warning:${RESET} skipping modules not included in this checkout:"
  for module in "${MISSING_MODULES[@]}"; do
    echo "   - ${module}"
  done
fi

if [[ "${MOBILE_PROFILE:-false}" == "true" ]] || truthy "$VERIFY_ONLY_VALUE"; then
  if truthy "$VERIFY_ONLY_VALUE"; then
    echo "${YELLOW}:| Verify only: skipped native mobile runtime and bundle audit.${RESET}"
  else
    echo "${BOLD}${GREEN}:) Building Aqli Edge native runtime:${RESET} ${MOBILE_PLUGIN_DIR_VALUE}"
    if truthy "$DRY_RUN_VALUE"; then
      print_dry_run_command "native-runtime" bash "$ROOT_DIR/scripts/build-mobile-runtime.sh"
    else
      bash "$ROOT_DIR/scripts/build-mobile-runtime.sh"
    fi
    if truthy "${AQLI_EDGE_AUDIT_MOBILE_BUNDLE:-true}"; then
      if [[ -x "$ROOT_DIR/scripts/audit-mobile-bundle.sh" ]]; then
        audit_args=(--no-example-artifacts)
        if truthy "${AQLI_EDGE_AUDIT_FAIL_ON_BUDGET:-false}"; then
          audit_args+=(--fail-on-budget)
        fi
        if truthy "${AQLI_EDGE_AUDIT_FAIL_ON_FINDINGS:-false}"; then
          audit_args+=(--fail-on-findings)
        fi
        if [[ -n "${AQLI_EDGE_AUDIT_ARGS:-}" ]]; then
          read -r -a extra_audit_args <<< "$AQLI_EDGE_AUDIT_ARGS"
          audit_args+=("${extra_audit_args[@]}")
        fi
        echo "${BOLD}${GREEN}:) Auditing Aqli Edge bundle:${RESET} ${MOBILE_PLUGIN_DIR_VALUE}"
        if truthy "$DRY_RUN_VALUE"; then
          print_dry_run_command "mobile-bundle-audit" "$ROOT_DIR/scripts/audit-mobile-bundle.sh" "${audit_args[@]}"
        else
          "$ROOT_DIR/scripts/audit-mobile-bundle.sh" "${audit_args[@]}"
        fi
      else
        echo "${YELLOW}:| Mobile bundle audit script not found; skipped.${RESET}"
      fi
    fi
  fi
fi

if truthy "$VERIFY_ONLY_VALUE"; then
  echo "${YELLOW}:| Verify only: skipped Gradle packaging.${RESET}"
elif [[ ${#SUPPORTED_MODULES[@]} -gt 0 ]]; then
  gradle_command=(./gradlew "${GRADLE_JAVA_HOME_ARG[@]}" "${GRADLE_BUILD_TASKS[@]}" \
    -x test \
    -x compileTestJava \
    -x processTestResources \
    -Paqli.backend="${RESOLVED_BACKEND_PROPERTY}" \
    -Paqli.profile="${BUILD_PROFILE}" \
    -Paqli.model.formats="${FORMAT_TARGETS}" \
    -Paqli.llm.types="${LLM_TARGETS}" \
    -Paqli.architecture="${ARCHITECTURE_VALUE}" \
    -Paqli.runtime.target="${RUNTIME_TARGET_VALUE}" \
    -Paqli.mobile.features="${MOBILE_FEATURES:-}")
  if truthy "$DRY_RUN_VALUE"; then
    print_dry_run_command "gradle" "${gradle_command[@]}"
  else
    "${gradle_command[@]}"
  fi
else
  echo "${YELLOW}:| No supported Gradle modules selected; continuing to native mobile runtime.${RESET}"
fi

if [[ "${MOBILE_PROFILE:-false}" == "true" ]] || truthy "$VERIFY_ONLY_VALUE"; then
  if truthy "${AQLI_EDGE_VERIFY_EXAMPLE:-true}"; then
    echo "${BOLD}${GREEN}:) Verifying Aqli Edge example:${RESET} ${MOBILE_PLUGIN_DIR_VALUE}"
    if [[ ! -d "${MOBILE_PLUGIN_DIR_VALUE}" ]]; then
      echo "${YELLOW}:| Mobile plugin directory not found: ${MOBILE_PLUGIN_DIR_VALUE}${RESET}"
    elif [[ -x "$ROOT_DIR/scripts/verify-mobile-edge-example.sh" ]]; then
      verify_args=()
      while IFS= read -r verify_arg; do
        verify_args+=("$verify_arg")
      done < <(mobile_edge_print_verify_args)
      verify_env=(AQLI_MOBILE_PLUGIN_DIR="${MOBILE_PLUGIN_DIR_VALUE}")
      if [[ -n "${AQLI_EDGE_VERIFY_SUMMARY_JSON:-}" ]]; then
        verify_env+=(AQLI_EDGE_VERIFY_SUMMARY_JSON="${AQLI_EDGE_VERIFY_SUMMARY_JSON}")
      fi
      if truthy "$DRY_RUN_VALUE"; then
        print_dry_run_command "verify-mobile-edge-example" \
          "${verify_env[@]}" \
          "$ROOT_DIR/scripts/verify-mobile-edge-example.sh" "${verify_args[@]}"
      else
        env "${verify_env[@]}" \
          "$ROOT_DIR/scripts/verify-mobile-edge-example.sh" "${verify_args[@]}"
      fi
    elif command -v flutter >/dev/null 2>&1; then
      (
        cd "${MOBILE_PLUGIN_DIR_VALUE}"
        flutter pub get
        flutter test
        if [[ -d example/test ]]; then
          (
            cd example
            flutter pub get
            flutter test
          )
        fi
      )
    else
      echo "${YELLOW}:| Flutter is not on PATH; skipped mobile/aqli_edge validation.${RESET}"
    fi
  else
    echo "${YELLOW}:| Aqli Edge example verification disabled by AQLI_EDGE_VERIFY_EXAMPLE=false.${RESET}"
  fi
fi
