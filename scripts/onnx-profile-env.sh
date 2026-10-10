#!/usr/bin/env bash

aqli_onnx_profile_env_safe_tsv_field() {
  local value="${1:-}"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

aqli_onnx_profile_env_uname() {
  local flag="$1"
  local fallback="${2:-unknown}"
  local value=""
  if command -v uname >/dev/null 2>&1; then
    value="$(uname "$flag" 2>/dev/null | awk 'NR == 1 { print; exit }')"
  fi
  printf '%s' "${value:-$fallback}"
}

aqli_onnx_profile_env_java_version() {
  local value=""
  if command -v java >/dev/null 2>&1; then
    value="$(java -version 2>&1 | awk 'NR == 1 { print; exit }')"
  fi
  printf '%s' "${value:-unknown}"
}

aqli_onnx_profile_env_root_dir() {
  local root=""
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." 2>/dev/null && pwd)"
  printf '%s' "$root"
}

aqli_onnx_profile_env_gradle_value() {
  local root="$1"
  local key="$2"
  local file="${root%/}/build.gradle.kts"
  local value=""
  if [[ -f "$file" ]]; then
    value="$(awk -v key="$key" '
      index($0, "extra[\"" key "\"]") {
        value = $0
        sub(/^.*= *"/, "", value)
        sub(/".*$/, "", value)
        print value
        exit
      }
    ' "$file")"
  fi
  printf '%s' "${value:-unknown}"
}

aqli_onnx_profile_env_git_value() {
  local root="$1"
  local field="$2"
  local value=""
  if [[ -z "$root" || ! -d "$root" ]] || ! command -v git >/dev/null 2>&1; then
    printf 'unavailable'
    return 0
  fi
  if ! git -C "$root" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    printf 'unavailable'
    return 0
  fi
  case "$field" in
    commit)
      value="$(git -C "$root" rev-parse --short=12 HEAD 2>/dev/null || true)"
      ;;
    commitFull)
      value="$(git -C "$root" rev-parse HEAD 2>/dev/null || true)"
      ;;
    branch)
      value="$(git -C "$root" rev-parse --abbrev-ref HEAD 2>/dev/null || true)"
      ;;
    dirty)
      if [[ -n "$(git -C "$root" status --porcelain 2>/dev/null)" ]]; then
        value="dirty"
      else
        value="clean"
      fi
      ;;
    *)
      value="unknown"
      ;;
  esac
  printf '%s' "${value:-unknown}"
}

aqli_onnx_profile_env_write_row() {
  local key="$1"
  local value="${2:-}"
  printf '%s\t%s\n' \
    "$(aqli_onnx_profile_env_safe_tsv_field "$key")" \
    "$(aqli_onnx_profile_env_safe_tsv_field "$value")"
}

aqli_onnx_profile_env_write() {
  local out="$1"
  local aqli_bin="${2:-}"
  local root_dir="${3:-}"
  if [[ -z "$root_dir" ]]; then
    root_dir="$(aqli_onnx_profile_env_root_dir)"
  fi
  {
    printf 'key\tvalue\n'
    aqli_onnx_profile_env_write_row generatedAt "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    aqli_onnx_profile_env_write_row rootDir "$root_dir"
    aqli_onnx_profile_env_write_row aqliVersion "$(aqli_onnx_profile_env_gradle_value "$root_dir" aqliVersion)"
    aqli_onnx_profile_env_write_row quarkusVersion "$(aqli_onnx_profile_env_gradle_value "$root_dir" quarkusVersion)"
    aqli_onnx_profile_env_write_row gitCommit "$(aqli_onnx_profile_env_git_value "$root_dir" commit)"
    aqli_onnx_profile_env_write_row gitCommitFull "$(aqli_onnx_profile_env_git_value "$root_dir" commitFull)"
    aqli_onnx_profile_env_write_row gitBranch "$(aqli_onnx_profile_env_git_value "$root_dir" branch)"
    aqli_onnx_profile_env_write_row gitDirty "$(aqli_onnx_profile_env_git_value "$root_dir" dirty)"
    aqli_onnx_profile_env_write_row hostOs "$(aqli_onnx_profile_env_uname -s)"
    aqli_onnx_profile_env_write_row hostArch "$(aqli_onnx_profile_env_uname -m)"
    aqli_onnx_profile_env_write_row kernelRelease "$(aqli_onnx_profile_env_uname -r)"
    aqli_onnx_profile_env_write_row javaVersion "$(aqli_onnx_profile_env_java_version)"
    aqli_onnx_profile_env_write_row javaHome "${JAVA_HOME:-}"
    aqli_onnx_profile_env_write_row aqliJavaOpts "${AQLI_JAVA_OPTS:-}"
    aqli_onnx_profile_env_write_row aqliBin "$aqli_bin"
  } > "$out"
}
