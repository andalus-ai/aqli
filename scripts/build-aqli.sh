#!/usr/bin/env bash
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
  if [[ -n "$JAVA_25_HOME" ]]; then
    export JAVA_HOME="$JAVA_25_HOME"
    export PATH="$JAVA_HOME/bin:$PATH"
  fi
fi

GRADLE_JAVA_HOME_ARG=()
if [[ -n "${JAVA_HOME:-}" ]]; then
  GRADLE_JAVA_HOME_ARG+=("-Dorg.gradle.java.home=${JAVA_HOME}")
fi
GRADLE_MAX_WORKERS_ARG=()
if [[ -n "${AQLI_GRADLE_MAX_WORKERS:-1}" ]]; then
  GRADLE_MAX_WORKERS_ARG+=("--max-workers=${AQLI_GRADLE_MAX_WORKERS:-1}")
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

echo "${BOLD}${CYAN}:) Resolved backend targets:${RESET} ${BACKEND_TARGETS}"
echo "${BOLD}${CYAN}:) Resolved format targets:${RESET} ${FORMAT_TARGETS}"
echo "${BOLD}${CYAN}:) Resolved LLM targets:${RESET} ${LLM_TARGETS}"
echo "${BOLD}${MAGENTA}:) Architecture:${RESET} ${ARCHITECTURE_VALUE}"
echo "${BOLD}${MAGENTA}:) Profile:${RESET} ${BUILD_PROFILE}"
echo "${BOLD}${MAGENTA}:) Java home:${RESET} ${JAVA_HOME:-$(command -v java)}"

# Build the composition roots. Gradle resolves their current project dependencies;
# do not maintain a second hand-written list of module paths here.
GRADLE_TASKS=(
  :cli:aqli-cli:quarkusBuild
  :api:aqli-api:quarkusBuild
)

./gradlew "${GRADLE_JAVA_HOME_ARG[@]}" "${GRADLE_MAX_WORKERS_ARG[@]}" "${GRADLE_TASKS[@]}" \
  -x test \
  -Paqli.backend="${RESOLVED_BACKEND_PROPERTY}" \
  -Paqli.profile="${BUILD_PROFILE}" \
  -Paqli.model.formats="${FORMAT_TARGETS}" \
  -Paqli.llm.types="${LLM_TARGETS}" \
  -Paqli.architecture="${ARCHITECTURE_VALUE}"

CLI_JAR="${ROOT_DIR}/cli/aqli-cli/build/aqli.jar"
if [[ -f "$CLI_JAR" ]]; then
  JAR_SIZE="$(du -sh "$CLI_JAR" | cut -f1)"
  echo ""
  echo "${BOLD}${GREEN}:) aqli.jar ready:${RESET} ${CLI_JAR} (${JAR_SIZE})"
  echo "${BOLD}${GREEN}:) Run with:${RESET} java --add-modules=jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar ${CLI_JAR} run --model <model> --prompt \"<prompt>\""
else
  echo "${BOLD}${YELLOW}:| Warning:${RESET} Gradle completed but ${CLI_JAR} was not found."
  exit 1
fi
