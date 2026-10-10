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
    :backend:*|:ml:*|:trainer:*|:examples:aqli-ml-examples|:models:aqli-model-common|:runner:onnx:aqli-ml-export-onnx|:runner:onnx:aqli-ml-onnx|:runner:diffuser:aqli-diffuser) return 0 ;;
    *) return 1 ;;
  esac
}

is_quarkus_module() {
  local module="$1"
  local quarkus_list="${QUARKUS_MODULES:-}"
  [[ " ${quarkus_list} " == *" ${module} "* ]]
}

read -r -a MODULE_ARRAY <<< "${MODULES:-}"
SUPPORTED_MODULES=()
QUARKUS_BUILD_MODULES=()
SKIPPED_MODULES=()

for module in "${MODULE_ARRAY[@]}"; do
  if is_legacy_gradle_module "$module"; then
    SKIPPED_MODULES+=("$module")
  elif is_quarkus_module "$module"; then
    QUARKUS_BUILD_MODULES+=("$module")
  else
    SUPPORTED_MODULES+=("$module")
  fi
done

# Standard build tasks (clean + build for each non-Quarkus module)
GRADLE_BUILD_TASKS=(clean)
for module in "${SUPPORTED_MODULES[@]}"; do
  GRADLE_BUILD_TASKS+=("${module}:build")
done

# Quarkus modules need quarkusBuild (produces the uber-jar)
for module in "${QUARKUS_BUILD_MODULES[@]}"; do
  GRADLE_BUILD_TASKS+=("${module}:quarkusBuild")
done

echo "${BOLD}${CYAN}:) Resolved backend targets:${RESET} ${BACKEND_TARGETS}"
echo "${BOLD}${CYAN}:) Resolved format targets:${RESET} ${FORMAT_TARGETS}"
echo "${BOLD}${CYAN}:) Resolved LLM targets:${RESET} ${LLM_TARGETS}"
echo "${BOLD}${MAGENTA}:) Architecture:${RESET} ${ARCHITECTURE_VALUE}"
echo "${BOLD}${MAGENTA}:) Profile:${RESET} ${BUILD_PROFILE}"
echo "${BOLD}${MAGENTA}:) Java home:${RESET} ${JAVA_HOME:-$(command -v java)}"
echo "${BOLD}${GREEN}:) Selection manifest:${RESET} ${ROOT_DIR}/scripts/module-selection-current.env"
echo "$GREEN:) Module manifest resolved cleanly.$RESET"

if [[ ${#SKIPPED_MODULES[@]} -gt 0 ]]; then
  echo "${BOLD}${YELLOW}:| Warning:${RESET} skipping legacy Gradle modules in wrapper build:"
  for module in "${SKIPPED_MODULES[@]}"; do
    echo "   - ${module}"
  done
fi

if [[ ${#QUARKUS_BUILD_MODULES[@]} -gt 0 ]]; then
  echo "${BOLD}${CYAN}:) Quarkus modules (quarkusBuild):${RESET}"
  for module in "${QUARKUS_BUILD_MODULES[@]}"; do
    echo "   - ${module}"
  done
fi

./gradlew "${GRADLE_JAVA_HOME_ARG[@]}" "${GRADLE_BUILD_TASKS[@]}" \
  -x test \
  -Paqli.backend="${RESOLVED_BACKEND_PROPERTY}" \
  -Paqli.profile="${BUILD_PROFILE}" \
  -Paqli.model.formats="${FORMAT_TARGETS}" \
  -Paqli.llm.types="${LLM_TARGETS}" \
  -Paqli.architecture="${ARCHITECTURE_VALUE}"

# Confirm the CLI jar was produced
CLI_JAR="${ROOT_DIR}/ui/aqli-cli/build/aqli.jar"
if [[ ${#QUARKUS_BUILD_MODULES[@]} -gt 0 ]]; then
  if [[ -f "${CLI_JAR}" ]]; then
    JAR_SIZE=$(du -sh "${CLI_JAR}" | cut -f1)
    echo ""
    echo "${BOLD}${GREEN}:) aqli.jar ready:${RESET} ${CLI_JAR} (${JAR_SIZE})"
    echo "${BOLD}${GREEN}:) Run with:${RESET} java -jar --add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED ${CLI_JAR} run --model <model> --prompt \"<prompt>\""
  else
    echo "${BOLD}${YELLOW}:| Warning:${RESET} quarkusBuild ran but ${CLI_JAR} was not found."
    exit 1
  fi
fi
