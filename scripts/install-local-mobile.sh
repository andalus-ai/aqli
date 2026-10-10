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
ARCHITECTURE_VALUE="${ARCHITECTURE_TARGETS:-${ARCHITECTURE_TARGET:-native-java,binding}}"
RUNTIME_TARGET_VALUE="${RUNTIME_TARGET:-backend}"
MOBILE_PLUGIN_DIR_VALUE="${MOBILE_PLUGIN_DIR:-$ROOT_DIR/../mobile/aqli_edge}"
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
GRADLE_INSTALL_TASKS=()
for module in "${SUPPORTED_MODULES[@]}"; do
  GRADLE_INSTALL_TASKS+=("${module}:publishToMavenLocal")
done

echo "${BOLD}${CYAN}:) Runtime target:${RESET} ${RUNTIME_TARGET_VALUE}"
echo "${BOLD}${CYAN}:) Resolved backend targets:${RESET} ${BACKEND_TARGETS}"
echo "${BOLD}${CYAN}:) Resolved format targets:${RESET} ${FORMAT_TARGETS}"
echo "${BOLD}${CYAN}:) Resolved LLM targets:${RESET} ${LLM_TARGETS}"
if [[ "${MOBILE_PROFILE:-false}" == "true" ]]; then
  echo "${BOLD}${CYAN}:) Mobile model formats:${RESET} ${MOBILE_FORMAT_TARGETS:-}"
  echo "${BOLD}${CYAN}:) Mobile features:${RESET} ${MOBILE_FEATURES:-}"
  echo "${BOLD}${CYAN}:) Mobile plugin:${RESET} ${MOBILE_PLUGIN_DIR_VALUE}"
  echo "${BOLD}${GREEN}:) Mobile profile JSON:${RESET} ${MOBILE_PROFILE_FILE:-${MOBILE_PLUGIN_DIR_VALUE}/assets/aqli_edge_profile.json}"
fi
echo "${BOLD}${MAGENTA}:) Architecture:${RESET} ${ARCHITECTURE_VALUE}"
echo "${BOLD}${MAGENTA}:) Profile:${RESET} ${BUILD_PROFILE}"
echo "${BOLD}${MAGENTA}:) Java home:${RESET} ${JAVA_HOME:-$(command -v java)}"
echo "${BOLD}${GREEN}:) Selection manifest:${RESET} ${ROOT_DIR}/scripts/module-selection-current.env"
if [[ -n "${WARNINGS:-}" ]]; then
  echo "${YELLOW}:| Warning:${RESET} ${WARNINGS}"
fi

if [[ ${#SKIPPED_MODULES[@]} -gt 0 ]]; then
  echo "${BOLD}${YELLOW}:| Warning:${RESET} skipping legacy Gradle modules in local install wrapper:"
  for module in "${SKIPPED_MODULES[@]}"; do
    echo "   - ${module}"
  done
fi

if [[ ${#MOBILE_SKIPPED_MODULES[@]} -gt 0 ]]; then
  echo "${BOLD}${YELLOW}:| Warning:${RESET} skipping non-mobile Gradle modules in local install wrapper:"
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

if [[ "${MOBILE_PROFILE:-false}" == "true" ]]; then
  echo "${BOLD}${GREEN}:) Building Aqli Edge native runtime:${RESET} ${MOBILE_PLUGIN_DIR_VALUE}"
  bash "$ROOT_DIR/scripts/build-mobile-runtime.sh"
fi

PUBLISH_RUNTIME_LOCAL="${AQLI_PUBLISH_RUNTIME_LOCAL:-false}"
export AQLI_SKIP_LEGACY_SOURCE_HOTPATCHES="${AQLI_SKIP_LEGACY_SOURCE_HOTPATCHES:-true}"
echo "${BOLD}${GREEN}:) Step 1/2:${RESET} packaging and installing aqli via the local runtime installer"
echo "${BOLD}${YELLOW}:| Install path:${RESET} ${AQLI_BIN_DIR:-$HOME/.local/bin}/aqli"
bash "$ROOT_DIR/scripts/install-local-runtime.sh"

echo "${BOLD}${GREEN}:) Step 2/2:${RESET} optional compatibility publish to local artifact cache"
if [[ "${PUBLISH_RUNTIME_LOCAL}" == "true" ]]; then
  if [[ ${#GRADLE_INSTALL_TASKS[@]} -gt 0 ]]; then
    if ./gradlew "${GRADLE_JAVA_HOME_ARG[@]}" "${GRADLE_INSTALL_TASKS[@]}" \
      -x test \
      -x compileTestJava \
      -x processTestResources \
      -Paqli.backend="${RESOLVED_BACKEND_PROPERTY}" \
      -Paqli.profile="${BUILD_PROFILE}" \
      -Paqli.model.formats="${FORMAT_TARGETS}" \
      -Paqli.llm.types="${LLM_TARGETS}" \
      -Paqli.architecture="${ARCHITECTURE_VALUE}" \
      -Paqli.runtime.target="${RUNTIME_TARGET_VALUE}" \
      -Paqli.mobile.features="${MOBILE_FEATURES:-}"; then
      echo "${GREEN}✓ Published supported runtime artifacts to local artifact cache${RESET}"
    else
      echo "${YELLOW}⚠ Optional Gradle publish failed after local install; CLI install is still complete.${RESET}"
    fi
  else
    echo "${YELLOW}⚠ No supported Gradle runtime modules were selected for publish.${RESET}"
  fi
else
  echo "${YELLOW}⚠ Skipping local artifact publish by default. Set AQLI_PUBLISH_RUNTIME_LOCAL=true to enable it.${RESET}"
fi
if [[ "${MOBILE_PROFILE:-false}" == "true" ]]; then
  echo "${BOLD}${GREEN}:) Preparing Flutter plugin:${RESET} ${MOBILE_PLUGIN_DIR_VALUE}"
  if [[ ! -d "${MOBILE_PLUGIN_DIR_VALUE}" ]]; then
    echo "${YELLOW}:| Mobile plugin directory not found: ${MOBILE_PLUGIN_DIR_VALUE}${RESET}"
  elif command -v flutter >/dev/null 2>&1; then
    (
      cd "${MOBILE_PLUGIN_DIR_VALUE}"
      flutter pub get
    )
  else
    echo "${YELLOW}:| Flutter is not on PATH; skipped mobile/aqli_edge prepare.${RESET}"
  fi
fi
echo "${BOLD}${GREEN}:D Done.${RESET} Aqli is installed locally, and compatibility publish is optional."
