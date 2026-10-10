#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPTS_DIR="$ROOT_DIR/scripts"

mkdir -p "$SCRIPTS_DIR"

RESET="$(printf '\033[0m')"
BOLD="$(printf '\033[1m')"
DIM="$(printf '\033[2m')"
GREEN="$(printf '\033[32m')"
YELLOW="$(printf '\033[33m')"
BLUE="$(printf '\033[34m')"
MAGENTA="$(printf '\033[35m')"
CYAN="$(printf '\033[36m')"
REVERSE="$(printf '\033[7m')"

cleanup_terminal() {
  if [[ -n "${ORIGINAL_STTY:-}" ]]; then
    stty "$ORIGINAL_STTY" < /dev/tty 2>/dev/null || true
  fi
  tput cnorm 2>/dev/null || true
  printf '%s' "$RESET"
}

trap cleanup_terminal EXIT

if [[ ! -t 0 || ! -t 1 ]]; then
  echo ":| This script needs an interactive terminal." >&2
  exit 1
fi

if ! command -v tput >/dev/null 2>&1; then
  echo ":( 'tput' is required for the terminal checklist UI." >&2
  exit 1
fi

ORIGINAL_STTY="$(stty -g < /dev/tty 2>/dev/null || true)"

BACKEND_VALUES=(metal cuda vulkan opencl all)
BACKEND_LABELS=(
  "Metal (macOS, CPU)"
  "CUDA (Windows, Linux, etc.)"
  "Vulkan (Linux, Windows, etc.)"
  "OpenCL (Linux, Windows, etc.)"
  "All"
)

FORMAT_VALUES=(safetensor gguf litert onnx all)
FORMAT_LABELS=(
  "Safetensor"
  "GGUF"
  "LiteRT"
  "ONNX"
  "All"
)

LLM_VALUES=(text vision audio multimodal stable-diffusion whispering all)
LLM_LABELS=(
  "Text"
  "Vision (image, video, 3d)"
  "Audio (speech, music)"
  "Multimodal (image+text, video+text, etc.)"
  "Stable Diffusion (image, video, etc.)"
  "Whispering (audio)"
  "All"
)

ARCH_VALUES=(native-java binding)
ARCH_LABELS=("Native Java" "Binding")

PROFILE_VALUES=(release snapshot)
PROFILE_LABELS=("Release" "Snapshot")

ALWAYS_MODULES=(
  ":core:aqli-core"
  ":core:aqli-error-code"
  ":core:aqli-model-repository"
  ":core:aqli-provider-core"
  ":core:aqli-tokenizer-core"
  ":sdk:aqli-sdk"
  ":sdk:aqli-sdk-api"
  ":sdk:aqli-sdk-core"
  ":sdk:aqli-sdk-local"
  ":sdk:aqli-sdk-session"
  ":spi:aqli-spi"
  ":spi:aqli-spi-inference"
  ":spi:aqli-spi-model"
  ":spi:aqli-spi-multimodal"
  ":spi:aqli-spi-plugin"
  ":spi:aqli-spi-provider"
  ":spi:aqli-spi-runtime"
  ":ui:aqli-api"
  ":ui:aqli-cli"
)

BACKEND_PROPERTY=()
PLATFORM_TAGS=()
MODULES=()
WARNINGS=()

append_unique() {
  local target="$1"
  local item="$2"
  eval "local current=(\"\${${target}[@]-}\")"
  local existing
  for existing in "${current[@]}"; do
    [[ "$existing" == "$item" ]] && return 0
  done
  eval "${target}+=(\"\$item\")"
}

append_many_unique() {
  local target="$1"
  shift
  local item
  for item in "$@"; do
    append_unique "$target" "$item"
  done
}

join_by() {
  local sep="$1"
  shift
  local out=""
  local item
  for item in "$@"; do
    if [[ -z "$out" ]]; then
      out="$item"
    else
      out="${out}${sep}${item}"
    fi
  done
  printf '%s' "$out"
}

contains_value() {
  local needle="$1"
  shift
  local item
  for item in "$@"; do
    [[ "$item" == "$needle" ]] && return 0
  done
  return 1
}

read_key() {
  local key rest
  stty -echo -icanon time 0 min 0 < /dev/tty
  while true; do
    if IFS= read -rsn1 key < /dev/tty; then
      break
    fi
    sleep 0.01
  done
  if [[ "$key" == $'\x1b' ]]; then
    IFS= read -rsn2 rest < /dev/tty || true
    key+="$rest"
  fi
  stty "$ORIGINAL_STTY" < /dev/tty 2>/dev/null || true
  printf '%s' "$key"
}

draw_menu() {
  local title="$1"
  local subtitle="$2"
  local kind="$3"
  local current="$4"
  shift 4
  local values=("$@")
  local labels_var="${title}_labels"
  local -a labels=()

  case "$title" in
    backend) labels=("${BACKEND_LABELS[@]}") ;;
    format) labels=("${FORMAT_LABELS[@]}") ;;
    llm) labels=("${LLM_LABELS[@]}") ;;
    architecture) labels=("${ARCH_LABELS[@]}") ;;
    profile) labels=("${PROFILE_LABELS[@]}") ;;
  esac

  clear
  printf '%s(•‿•) %s%s\n' "$BOLD$CYAN" "$subtitle" "$RESET"
  printf '%sUse Up/Down to move, Space to toggle, Enter to confirm.%s\n\n' "$DIM$MAGENTA" "$RESET"

  local i value label mark line_style
  for ((i = 0; i < ${#values[@]}; i++)); do
    value="${values[$i]}"
    label="${labels[$i]}"

    if [[ "$kind" == "multi" ]]; then
      if contains_value "$value" "${MENU_SELECTED[@]}"; then
        mark="[x]"
      else
        mark="[ ]"
      fi
    else
      if [[ "${MENU_SELECTED[0]-}" == "$value" ]]; then
        mark="(*)"
      else
        mark="( )"
      fi
    fi

    if [[ "$i" -eq "$current" ]]; then
      line_style="$REVERSE$BOLD"
      printf '%s> %s %s%s\n' "$line_style" "$mark" "$label" "$RESET"
    elif contains_value "$value" "${MENU_SELECTED[@]}"; then
      printf '%s  %s %s%s\n' "$GREEN" "$mark" "$label" "$RESET"
    else
      printf '  %s %s\n' "$mark" "$label"
    fi
  done

  printf '\n%s:) Selected:%s %s\n' "$YELLOW" "$RESET" "$(join_by ", " "${MENU_SELECTED[@]}")"
}

run_menu() {
  local menu_name="$1"
  local subtitle="$2"
  local kind="$3"
  local default_csv="$4"
  shift 4
  local values=("$@")

  MENU_SELECTED=()
  IFS=',' read -r -a MENU_SELECTED <<<"$default_csv"
  local cursor=0

  tput civis 2>/dev/null || true

  while true; do
    draw_menu "$menu_name" "$subtitle" "$kind" "$cursor" "${values[@]}"
    local key
    key="$(read_key)"

    case "$key" in
      $'\x1b[A'|k)
        cursor=$(( (cursor - 1 + ${#values[@]}) % ${#values[@]} ))
        ;;
      $'\x1b[B'|j)
        cursor=$(( (cursor + 1) % ${#values[@]} ))
        ;;
      " ")
        local value="${values[$cursor]}"
        if [[ "$kind" == "multi" ]]; then
          if [[ "$value" == "all" ]]; then
            MENU_SELECTED=("all")
          else
            local next=()
            local item found=0
            for item in "${MENU_SELECTED[@]}"; do
              [[ "$item" == "all" ]] && continue
              if [[ "$item" == "$value" ]]; then
                found=1
              else
                next+=("$item")
              fi
            done
            if [[ "$found" -eq 0 ]]; then
              next+=("$value")
            fi
            if [[ "${#next[@]}" -eq 0 ]]; then
              MENU_SELECTED=("$value")
            else
              MENU_SELECTED=("${next[@]}")
            fi
          fi
        else
          MENU_SELECTED=("$value")
        fi
        ;;
      ""|$'\n'|$'\r')
        if [[ "$kind" == "single" && "${#MENU_SELECTED[@]}" -eq 0 ]]; then
          MENU_SELECTED=("${values[$cursor]}")
        elif [[ "$kind" == "single" ]]; then
          MENU_SELECTED=("${values[$cursor]}")
        elif [[ "${#MENU_SELECTED[@]}" -eq 0 ]]; then
          MENU_SELECTED=("${values[$cursor]}")
        fi
        break
        ;;
    esac
  done
}

expand_all_multi() {
  local values_name="$1"
  local selected_name="$2"
  eval "local selected=(\"\${${selected_name}[@]}\")"
  if contains_value "all" "${selected[@]}"; then
    local expanded=()
    case "$values_name" in
      BACKEND_VALUES) expanded=("${BACKEND_VALUES[@]}") ;;
      FORMAT_VALUES) expanded=("${FORMAT_VALUES[@]}") ;;
      LLM_VALUES) expanded=("${LLM_VALUES[@]}") ;;
    esac
    local filtered=()
    local item
    for item in "${expanded[@]}"; do
      [[ "$item" == "all" ]] && continue
      filtered+=("$item")
    done
    eval "${selected_name}=(\"\${filtered[@]}\")"
  fi
}

resolve_backend() {
  local backend="$1"
  case "$backend" in
    metal)
      append_many_unique BACKEND_PROPERTY cpu metal
      append_many_unique PLATFORM_TAGS macos cpu
      append_many_unique MODULES \
        ":backend:metal:aqli-backend-metal" \
        ":backend:metal:aqli-mlx-binding" \
        ":core:plugin:aqli-plugin-kernel-core"
      ;;
    cuda)
      append_many_unique BACKEND_PROPERTY cpu cuda
      append_many_unique PLATFORM_TAGS linux windows
      append_many_unique MODULES \
        ":backend:cuda:aqli-backend-cuda" \
        ":backend:cuda:aqli-kernel-cuda" \
        ":backend:cuda:aqli-plugin-kernel-cuda" \
        ":core:plugin:aqli-plugin-kernel-core"
      ;;
    vulkan)
      append_many_unique BACKEND_PROPERTY cpu
      append_many_unique PLATFORM_TAGS linux windows
      append_unique WARNINGS "Backend 'vulkan' has no dedicated modules in the current repo."
      ;;
    opencl)
      append_many_unique BACKEND_PROPERTY cpu
      append_many_unique PLATFORM_TAGS linux windows
      append_unique WARNINGS "Backend 'opencl' has no dedicated modules in the current repo."
      ;;
  esac
}

resolve_format() {
  local format="$1"
  case "$format" in
    safetensor)
      append_many_unique MODULES \
        ":runner:safetensor:aqli-runner-safetensor" \
        ":runner:safetensor:aqli-runner-stable-diffusion" \
        ":runner:safetensor:aqli-safetensor-api" \
        ":runner:safetensor:aqli-safetensor-core" \
        ":runner:safetensor:aqli-safetensor-engine" \
        ":runner:safetensor:aqli-safetensor-loader" \
        ":runner:safetensor:aqli-safetensor-quantization" \
        ":runner:safetensor:aqli-safetensor-spi"
      ;;
    gguf)
      append_many_unique MODULES \
        ":core:plugin:aqli-plugin-runner-gguf" \
        ":runner:gguf:aqli-gguf-converter" \
        ":runner:gguf:aqli-gguf-converter-java" \
        ":runner:gguf:aqli-gguf-core"
      ;;
    litert)
      append_many_unique MODULES \
        ":runner:litert:aqli-litert-core" \
        ":runner:litert:aqli-plugin-runner-litert" \
        ":runner:litert:aqli-runner-litert"
      ;;
    onnx)
      append_many_unique MODULES \
        ":runner:onnx:aqli-ml-export-onnx" \
        ":runner:onnx:aqli-ml-onnx" \
        ":runner:onnx:aqli-plugin-runner-onnx" \
        ":runner:onnx:aqli-runner-onnx"
      ;;
  esac
}

resolve_llm() {
  local llm="$1"
  case "$llm" in
    text)
      append_many_unique MODULES \
        ":models:aqli-model-gemma" \
        ":models:aqli-model-llama" \
        ":models:aqli-model-mistral" \
        ":models:aqli-model-phi" \
        ":models:aqli-model-qwen" \
        ":core:aqli-model-runner" \
        ":core:aqli-model-repo-hf" \
        ":core:aqli-model-repo-local"
      ;;
    vision)
      append_many_unique MODULES \
        ":ml:aqli-ml-cnn" \
        ":ml:aqli-ml-vision"
      ;;
    audio)
      append_many_unique MODULES \
        ":ml:aqli-ml-nlp" \
        ":ml:aqli-ml-audio"
      ;;
    multimodal)
      append_many_unique MODULES \
        ":models:aqli-model-qwen" \
        ":spi:aqli-spi-multimodal" \
        ":ml:aqli-ml-multimodal"
      ;;
    stable-diffusion)
      append_many_unique MODULES \
        ":runner:diffuser:aqli-diffuser" \
        ":runner:safetensor:aqli-runner-stable-diffusion"
      ;;
    whispering)
      append_many_unique MODULES \
        ":runner:onnx:aqli-runner-onnx" \
        ":ml:aqli-ml-nlp" \
        ":ml:aqli-ml-audio"
      ;;
  esac
}

resolve_architecture() {
  local arch="$1"
  case "$arch" in
    native-java)
      append_many_unique MODULES \
        ":core:aqli-tensor" \
        ":core:aqli-model-repo-hf" \
        ":core:aqli-model-repo-kaggle" \
        ":core:aqli-model-repo-local" \
        ":runner:gguf:aqli-gguf-core" \
        ":runner:safetensor:aqli-safetensor-core" \
        ":runner:safetensor:aqli-safetensor-engine"
      ;;
    binding)
      append_many_unique MODULES \
        ":backend:metal:aqli-mlx-binding" \
        ":runner:litert:aqli-litert-core" \
        ":runner:onnx:aqli-runner-onnx"
      ;;
  esac
}

sort_unique_lines() {
  printf '%s\n' "$@" | awk 'NF && !seen[$0]++' | sort
}

run_menu backend "Backend targeted" multi "metal" "${BACKEND_VALUES[@]}"
BACKEND_TARGETS=("${MENU_SELECTED[@]}")

run_menu format "Model format targeted" multi "all" "${FORMAT_VALUES[@]}"
FORMAT_TARGETS=("${MENU_SELECTED[@]}")

run_menu llm "LLM type" multi "all" "${LLM_VALUES[@]}"
LLM_TARGETS=("${MENU_SELECTED[@]}")

run_menu architecture "Model architecture" multi "native-java,binding" "${ARCH_VALUES[@]}"
ARCHITECTURE_TARGETS=("${MENU_SELECTED[@]}")
if [[ "${#ARCHITECTURE_TARGETS[@]}" -eq 0 ]]; then
  ARCHITECTURE_TARGETS=("native-java" "binding")
fi

run_menu profile "Other options" single "snapshot" "${PROFILE_VALUES[@]}"
BUILD_PROFILE="${MENU_SELECTED[0]}"

expand_all_multi BACKEND_VALUES BACKEND_TARGETS
expand_all_multi FORMAT_VALUES FORMAT_TARGETS
expand_all_multi LLM_VALUES LLM_TARGETS

append_many_unique MODULES "${ALWAYS_MODULES[@]}"

for item in "${BACKEND_TARGETS[@]}"; do
  resolve_backend "$item"
done

for item in "${FORMAT_TARGETS[@]}"; do
  resolve_format "$item"
done

for item in "${LLM_TARGETS[@]}"; do
  resolve_llm "$item"
done

for item in "${ARCHITECTURE_TARGETS[@]}"; do
  resolve_architecture "$item"
done

BACKEND_TARGETS_SORTED=($(sort_unique_lines "${BACKEND_TARGETS[@]-}"))
FORMAT_TARGETS_SORTED=($(sort_unique_lines "${FORMAT_TARGETS[@]-}"))
LLM_TARGETS_SORTED=($(sort_unique_lines "${LLM_TARGETS[@]-}"))
ARCHITECTURE_TARGETS_SORTED=($(sort_unique_lines "${ARCHITECTURE_TARGETS[@]-}"))
MODULES_SORTED=($(sort_unique_lines "${MODULES[@]-}"))
WARNINGS_SORTED=($(sort_unique_lines "${WARNINGS[@]-}"))

RESOLVED_BACKEND_PROPERTY="$(join_by "," "${BACKEND_PROPERTY[@]-}")"
if [[ -z "$RESOLVED_BACKEND_PROPERTY" ]]; then
  RESOLVED_BACKEND_PROPERTY="cpu"
fi

BACKEND_SLUG="$(join_by "-" "${BACKEND_TARGETS_SORTED[@]}")"
FORMAT_SLUG="$(join_by "-" "${FORMAT_TARGETS_SORTED[@]}")"
LLM_SLUG="$(join_by "-" "${LLM_TARGETS_SORTED[@]}")"
ARCHITECTURE_TARGET_CSV="$(join_by "," "${ARCHITECTURE_TARGETS_SORTED[@]}")"
ARCHITECTURE_SLUG="$(join_by "-" "${ARCHITECTURE_TARGETS_SORTED[@]}")"
SCRIPT_SLUG="$(join_by "-" "$BACKEND_SLUG" "$FORMAT_SLUG" "$LLM_SLUG" "$ARCHITECTURE_SLUG")"

SELECTION_FILE="$SCRIPTS_DIR/module-selection-current.env"
BUILD_SCRIPT="$SCRIPTS_DIR/build-macos.sh"
INSTALL_SCRIPT="$SCRIPTS_DIR/install-local-macos.sh"

{
  printf 'BACKEND_TARGETS="%s"\n' "$(join_by "," "${BACKEND_TARGETS_SORTED[@]}")"
  printf 'FORMAT_TARGETS="%s"\n' "$(join_by "," "${FORMAT_TARGETS_SORTED[@]}")"
  printf 'LLM_TARGETS="%s"\n' "$(join_by "," "${LLM_TARGETS_SORTED[@]}")"
  printf 'ARCHITECTURE_TARGETS="%s"\n' "$ARCHITECTURE_TARGET_CSV"
  printf 'ARCHITECTURE_TARGET="%s"\n' "$ARCHITECTURE_TARGET_CSV"
  printf 'BUILD_PROFILE="%s"\n' "$BUILD_PROFILE"
  printf 'RESOLVED_BACKEND_PROPERTY="%s"\n' "$RESOLVED_BACKEND_PROPERTY"
  printf 'MODULES="%s"\n' "$(join_by " " "${MODULES_SORTED[@]}")"
  printf 'WARNINGS="%s"\n' "$(join_by " | " "${WARNINGS_SORTED[@]}")"
  printf 'SCRIPT_SLUG="%s"\n' "$SCRIPT_SLUG"
} > "$SELECTION_FILE"

{
  echo '#!/bin/bash'
  echo 'set -euo pipefail'
  echo
  echo 'RESET="$(printf '\''\033[0m'\'')"'
  echo 'BOLD="$(printf '\''\033[1m'\'')"'
  echo 'GREEN="$(printf '\''\033[32m'\'')"'
  echo 'CYAN="$(printf '\''\033[36m'\'')"'
  echo 'YELLOW="$(printf '\''\033[33m'\'')"'
  echo 'MAGENTA="$(printf '\''\033[35m'\'')"'
  echo
  echo 'ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"'
  echo 'cd "$ROOT_DIR"'
  echo 'source "$ROOT_DIR/scripts/module-selection-current.env"'
  echo 'ARCHITECTURE_VALUE="${ARCHITECTURE_TARGETS:-${ARCHITECTURE_TARGET:-native-java,binding}}"'
  echo 'if command -v /usr/libexec/java_home >/dev/null 2>&1; then'
  echo '  JAVA_25_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"'
  echo '  if [[ -n "${JAVA_25_HOME}" ]]; then'
  echo '    export JAVA_HOME="${JAVA_25_HOME}"'
  echo '    export PATH="${JAVA_HOME}/bin:${PATH}"'
  echo '  fi'
  echo 'fi'
  echo 'GRADLE_JAVA_HOME_ARG=()'
  echo 'if [[ -n "${JAVA_HOME:-}" ]]; then'
  echo '  GRADLE_JAVA_HOME_ARG+=("-Dorg.gradle.java.home=${JAVA_HOME}")'
  echo 'fi'
  echo 'append_java_tool_option() {'
  echo '  local option="$1"'
  echo '  case " ${JAVA_TOOL_OPTIONS:-} " in'
  echo '    *" ${option} "*) ;;'
  echo '    *) JAVA_TOOL_OPTIONS="${option}${JAVA_TOOL_OPTIONS:+ ${JAVA_TOOL_OPTIONS}}" ;;'
  echo '  esac'
  echo '}'
  echo 'append_java_tool_option "--enable-native-access=ALL-UNNAMED"'
  echo 'append_java_tool_option "--add-modules=jdk.incubator.vector"'
  echo 'export JAVA_TOOL_OPTIONS'
  echo 'is_legacy_gradle_module() {'
  echo '  case "$1" in'
  echo '    :ml:*|:trainer:*|:examples:aqli-ml-examples|:models:aqli-model-common|:runner:onnx:aqli-ml-export-onnx|:runner:onnx:aqli-ml-onnx|:runner:diffuser:aqli-diffuser) return 0 ;;'
  echo '    *) return 1 ;;'
  echo '  esac'
  echo '}'
  echo 'read -r -a MODULE_ARRAY <<< "${MODULES:-}"'
  echo 'SUPPORTED_MODULES=()'
  echo 'SKIPPED_MODULES=()'
  echo 'for module in "${MODULE_ARRAY[@]}"; do'
  echo '  if is_legacy_gradle_module "$module"; then'
  echo '    SKIPPED_MODULES+=("$module")'
  echo '  else'
  echo '    SUPPORTED_MODULES+=("$module")'
  echo '  fi'
  echo 'done'
  echo 'GRADLE_BUILD_TASKS=(clean)'
  echo 'for module in "${SUPPORTED_MODULES[@]}"; do'
  echo '  GRADLE_BUILD_TASKS+=("${module}:build")'
  echo 'done'
  echo
  echo 'echo "${BOLD}${CYAN}:) Resolved backend targets:${RESET} ${BACKEND_TARGETS}"'
  echo 'echo "${BOLD}${CYAN}:) Resolved format targets:${RESET} ${FORMAT_TARGETS}"'
  echo 'echo "${BOLD}${CYAN}:) Resolved LLM targets:${RESET} ${LLM_TARGETS}"'
  echo 'echo "${BOLD}${MAGENTA}:) Architecture:${RESET} ${ARCHITECTURE_VALUE}"'
  echo 'echo "${BOLD}${MAGENTA}:) Profile:${RESET} ${BUILD_PROFILE}"'
  echo 'echo "${BOLD}${MAGENTA}:) Java home:${RESET} ${JAVA_HOME:-$(command -v java)}"'
  echo 'echo "${BOLD}${MAGENTA}:) Build mode:${RESET} Gradle-only install"'
  echo 'echo "${BOLD}${GREEN}:) Selection manifest:${RESET} ${ROOT_DIR}/scripts/module-selection-current.env"'
  if [[ "${#WARNINGS_SORTED[@]}" -eq 0 ]]; then
    echo 'echo "$GREEN:) Module manifest resolved cleanly.$RESET"'
  else
    echo 'IFS=" | " read -r -a WARNINGS_ARR <<< "${WARNINGS}"'
    echo 'for item in "${WARNINGS_ARR[@]}"; do echo "${YELLOW}:| Warning:${RESET} ${item}"; done'
  fi
  echo
  echo 'if [[ ${#SKIPPED_MODULES[@]} -gt 0 ]]; then'
  echo '  echo "${BOLD}${YELLOW}:| Warning:${RESET} skipping legacy Gradle modules in wrapper build:"'
  echo '  for module in "${SKIPPED_MODULES[@]}"; do'
  echo '    echo "   - ${module}"'
  echo '  done'
  echo 'fi'
  echo
  echo './gradlew "${GRADLE_JAVA_HOME_ARG[@]}" "${GRADLE_BUILD_TASKS[@]}" \'
  echo '  -Paqli.backend="${RESOLVED_BACKEND_PROPERTY}" \'
  echo '  -Paqli.profile="${BUILD_PROFILE}" \'
  echo '  -Paqli.model.formats="${FORMAT_TARGETS}" \'
  echo '  -Paqli.llm.types="${LLM_TARGETS}" \'
  echo '  -Paqli.architecture="${ARCHITECTURE_VALUE}"'
} > "$BUILD_SCRIPT"

{
  echo '#!/bin/bash'
  echo 'set -euo pipefail'
  echo
  echo 'RESET="$(printf '\''\033[0m'\'')"'
  echo 'BOLD="$(printf '\''\033[1m'\'')"'
  echo 'GREEN="$(printf '\''\033[32m'\'')"'
  echo 'CYAN="$(printf '\''\033[36m'\'')"'
  echo 'YELLOW="$(printf '\''\033[33m'\'')"'
  echo 'MAGENTA="$(printf '\''\033[35m'\'')"'
  echo
  echo 'ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"'
  echo 'cd "$ROOT_DIR"'
  echo 'source "$ROOT_DIR/scripts/module-selection-current.env"'
  echo 'ARCHITECTURE_VALUE="${ARCHITECTURE_TARGETS:-${ARCHITECTURE_TARGET:-native-java,binding}}"'
  echo 'if command -v /usr/libexec/java_home >/dev/null 2>&1; then'
  echo '  JAVA_25_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"'
  echo '  if [[ -n "${JAVA_25_HOME}" ]]; then'
  echo '    export JAVA_HOME="${JAVA_25_HOME}"'
  echo '    export PATH="${JAVA_HOME}/bin:${PATH}"'
  echo '  fi'
  echo 'fi'
  echo 'GRADLE_JAVA_HOME_ARG=()'
  echo 'if [[ -n "${JAVA_HOME:-}" ]]; then'
  echo '  GRADLE_JAVA_HOME_ARG+=("-Dorg.gradle.java.home=${JAVA_HOME}")'
  echo 'fi'
  echo 'append_java_tool_option() {'
  echo '  local option="$1"'
  echo '  case " ${JAVA_TOOL_OPTIONS:-} " in'
  echo '    *" ${option} "*) ;;'
  echo '    *) JAVA_TOOL_OPTIONS="${option}${JAVA_TOOL_OPTIONS:+ ${JAVA_TOOL_OPTIONS}}" ;;'
  echo '  esac'
  echo '}'
  echo 'append_java_tool_option "--enable-native-access=ALL-UNNAMED"'
  echo 'append_java_tool_option "--add-modules=jdk.incubator.vector"'
  echo 'export JAVA_TOOL_OPTIONS'
  echo 'is_legacy_gradle_module() {'
  echo '  case "$1" in'
  echo '    :ml:*|:trainer:*|:examples:aqli-ml-examples|:models:aqli-model-common|:runner:onnx:aqli-ml-export-onnx|:runner:onnx:aqli-ml-onnx|:runner:diffuser:aqli-diffuser) return 0 ;;'
  echo '    *) return 1 ;;'
  echo '  esac'
  echo '}'
  echo 'read -r -a MODULE_ARRAY <<< "${MODULES:-}"'
  echo 'SUPPORTED_MODULES=()'
  echo 'SKIPPED_MODULES=()'
  echo 'for module in "${MODULE_ARRAY[@]}"; do'
  echo '  if is_legacy_gradle_module "$module"; then'
  echo '    SKIPPED_MODULES+=("$module")'
  echo '  else'
  echo '    SUPPORTED_MODULES+=("$module")'
  echo '  fi'
  echo 'done'
  echo 'GRADLE_INSTALL_TASKS=()'
  echo 'for module in "${SUPPORTED_MODULES[@]}"; do'
  echo '  GRADLE_INSTALL_TASKS+=("${module}:publishToMavenLocal")'
  echo 'done'
  echo
  echo 'echo "${BOLD}${CYAN}:) Resolved backend targets:${RESET} ${BACKEND_TARGETS}"'
  echo 'echo "${BOLD}${CYAN}:) Resolved format targets:${RESET} ${FORMAT_TARGETS}"'
  echo 'echo "${BOLD}${CYAN}:) Resolved LLM targets:${RESET} ${LLM_TARGETS}"'
  echo 'echo "${BOLD}${MAGENTA}:) Architecture:${RESET} ${ARCHITECTURE_VALUE}"'
  echo 'echo "${BOLD}${MAGENTA}:) Profile:${RESET} ${BUILD_PROFILE}"'
  echo 'echo "${BOLD}${MAGENTA}:) Java home:${RESET} ${JAVA_HOME:-$(command -v java)}"'
  echo 'echo "${BOLD}${GREEN}:) Selection manifest:${RESET} ${ROOT_DIR}/scripts/module-selection-current.env"'
  if [[ "${#WARNINGS_SORTED[@]}" -eq 0 ]]; then
    echo 'echo "$GREEN:) Module manifest resolved cleanly.$RESET"'
  else
    echo 'IFS=" | " read -r -a WARNINGS_ARR <<< "${WARNINGS}"'
    echo 'for item in "${WARNINGS_ARR[@]}"; do echo "${YELLOW}:| Warning:${RESET} ${item}"; done'
  fi
  echo
  echo 'if [[ ${#SKIPPED_MODULES[@]} -gt 0 ]]; then'
  echo '  echo "${BOLD}${YELLOW}:| Warning:${RESET} skipping legacy Gradle modules in local install wrapper:"'
  echo '  for module in "${SKIPPED_MODULES[@]}"; do'
  echo '    echo "   - ${module}"'
  echo '  done'
  echo 'fi'
  echo
  echo 'PUBLISH_RUNTIME_LOCAL="${AQLI_PUBLISH_RUNTIME_LOCAL:-false}"'
  echo 'export AQLI_SKIP_LEGACY_SOURCE_HOTPATCHES="${AQLI_SKIP_LEGACY_SOURCE_HOTPATCHES:-true}"'
  echo 'echo "${BOLD}${GREEN}:) Step 1/2:${RESET} packaging and installing aqli via the macOS local installer"'
  echo 'echo "${BOLD}${YELLOW}:| Install path:${RESET} ${AQLI_BIN_DIR:-$HOME/.local/bin}/aqli"'
  echo 'bash "$ROOT_DIR/scripts/install-local-runtime.sh"'
  echo
  echo 'echo "${BOLD}${GREEN}:) Step 2/2:${RESET} optional compatibility publish to local artifact cache"'
  echo 'if [[ "${PUBLISH_RUNTIME_LOCAL}" == "true" ]]; then'
  echo '  if [[ ${#GRADLE_INSTALL_TASKS[@]} -gt 0 ]]; then'
  echo '    if ./gradlew "${GRADLE_JAVA_HOME_ARG[@]}" "${GRADLE_INSTALL_TASKS[@]}" \'
  echo '      -Paqli.backend="${RESOLVED_BACKEND_PROPERTY}" \'
  echo '      -Paqli.profile="${BUILD_PROFILE}" \'
  echo '      -Paqli.model.formats="${FORMAT_TARGETS}" \'
  echo '      -Paqli.llm.types="${LLM_TARGETS}" \'
  echo '      -Paqli.architecture="${ARCHITECTURE_VALUE}"; then'
  echo '      echo "${GREEN}✓ Published supported runtime artifacts to local artifact cache${RESET}"'
  echo '    else'
  echo '      echo "${YELLOW}⚠ Optional Gradle publish failed after local install; CLI install is still complete.${RESET}"'
  echo '    fi'
  echo '  else'
  echo '    echo "${YELLOW}⚠ No supported Gradle runtime modules were selected for publish.${RESET}"'
  echo '  fi'
  echo 'else'
  echo '  echo "${YELLOW}⚠ Skipping local artifact publish by default. Set AQLI_PUBLISH_RUNTIME_LOCAL=true to enable it.${RESET}"'
  echo 'fi'
  echo 'echo "${BOLD}${GREEN}:D Done.${RESET} Aqli is installed locally, and compatibility publish is optional."'
} > "$INSTALL_SCRIPT"

chmod +x "$BUILD_SCRIPT" "$INSTALL_SCRIPT"

clear
printf '%s:) Generated%s %s\n' "$GREEN" "$RESET" "$BUILD_SCRIPT"
printf '%s:) Generated%s %s\n' "$GREEN" "$RESET" "$INSTALL_SCRIPT"
printf '%s:) Saved selection manifest to%s %s\n' "$CYAN" "$RESET" "$SELECTION_FILE"
if [[ "${#WARNINGS_SORTED[@]}" -eq 0 ]]; then
  printf '%s:D All manifest selections resolved without warnings.%s\n' "$MAGENTA" "$RESET"
else
  for item in "${WARNINGS_SORTED[@]}"; do
    printf '%s:| Warning:%s %s\n' "$YELLOW" "$RESET" "$item"
  done
fi
