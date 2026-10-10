#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SELECTION_FILE="${AQLI_MOBILE_SELECTION_FILE:-$ROOT_DIR/scripts/module-selection-current.env}"

if [[ ! -f "$SELECTION_FILE" ]]; then
  echo ":| Mobile selection file not found: $SELECTION_FILE" >&2
  exit 1
fi

source "$SELECTION_FILE"

MOBILE_PLUGIN_DIR_VALUE="${MOBILE_PLUGIN_DIR:-$ROOT_DIR/../mobile/aqli_edge}"
MOBILE_PLUGIN_DIR_VALUE="$(cd "$MOBILE_PLUGIN_DIR_VALUE" && pwd)"
NATIVE_DIR="$MOBILE_PLUGIN_DIR_VALUE/native"
GENERATED_DIR="$NATIVE_DIR/generated"
GENERATED_INCLUDE_DIR="$GENERATED_DIR/include"
PUBLIC_CONFIG_HEADER="$NATIVE_DIR/include/aqli_edge_runtime_config.h"
DIST_DIR="$NATIVE_DIR/dist"
BUILD_DIR="$NATIVE_DIR/build/host"
ANDROID_BUILD_DIR="$NATIVE_DIR/build/android"
ANDROID_JNILIBS_DIR="$MOBILE_PLUGIN_DIR_VALUE/android/src/main/jniLibs"
PROFILE_JSON="${MOBILE_PROFILE_FILE:-$MOBILE_PLUGIN_DIR_VALUE/assets/aqli_edge_profile.json}"
BUILD_INFO_JSON="$MOBILE_PLUGIN_DIR_VALUE/assets/aqli_edge_build.json"
FORMAT_TARGETS_VALUE="${MOBILE_FORMAT_TARGETS:-${FORMAT_TARGETS:-litert}}"
MOBILE_FEATURES_VALUE="${MOBILE_FEATURES:-text,speech-to-text,vision}"
MODEL_SIDECAR_FILES_VALUE="${AQLI_EDGE_MODEL_SIDECAR_FILES:-${MODEL_SIDECAR_FILES:-tokenizer.json,tokenizer_config.json,special_tokens_map.json,generation_config.json,config.json}}"
BUILD_PROFILE_VALUE="${AQLI_EDGE_BUILD_PROFILE:-${MOBILE_BUILD_PROFILE:-${BUILD_PROFILE:-snapshot}}}"
ANDROID_ABIS_VALUE="${AQLI_EDGE_ANDROID_ABIS:-${ANDROID_ABIS:-arm64-v8a}}"
ANDROID_PLATFORM_VALUE="${AQLI_EDGE_ANDROID_PLATFORM:-android-24}"
HOST_OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
HOST_ARCH="$(uname -m)"
MACOS_DEPLOYMENT_TARGET_VALUE="${AQLI_EDGE_MACOS_DEPLOYMENT_TARGET:-14.0}"
REQUIRED_RUNTIME_SYMBOLS=(
  aqli_edge_runtime_version
  aqli_edge_runtime_profile_json
  aqli_edge_runtime_model_formats_csv
  aqli_edge_runtime_mobile_features_csv
  aqli_edge_runtime_build_profile
  aqli_edge_runtime_delegates_json
  aqli_edge_runtime_last_error
  aqli_edge_runtime_supports_format
  aqli_edge_runtime_supports_feature
  aqli_edge_runtime_open_model
  aqli_edge_runtime_close_model
  aqli_edge_runtime_warm_model
  aqli_edge_runtime_probe_model
  aqli_edge_runtime_adapter_diagnostics
  aqli_edge_runtime_infer
  aqli_edge_runtime_infer_with_options
  aqli_edge_runtime_run_operation
  aqli_edge_runtime_infer_text
)
REQUIRED_ANDROID_RUNTIME_SYMBOLS=(
  "${REQUIRED_RUNTIME_SYMBOLS[@]}"
  Java_tech_kayys_aqli_1edge_AqliEdgePlugin_nativeRuntimeVersion
  Java_tech_kayys_aqli_1edge_AqliEdgePlugin_nativeRuntimeProfileJson
  Java_tech_kayys_aqli_1edge_AqliEdgePlugin_nativeRuntimeDelegatesJson
  Java_tech_kayys_aqli_1edge_AqliEdgePlugin_nativeRuntimeBuildProfile
  Java_tech_kayys_aqli_1edge_AqliEdgePlugin_nativeLastStatus
  Java_tech_kayys_aqli_1edge_AqliEdgePlugin_nativeLastError
  Java_tech_kayys_aqli_1edge_AqliEdgePlugin_nativeCloseModelCache
  Java_tech_kayys_aqli_1edge_AqliEdgePlugin_nativeWarmModel
  Java_tech_kayys_aqli_1edge_AqliEdgePlugin_nativeProbeModel
  Java_tech_kayys_aqli_1edge_AqliEdgePlugin_nativeAdapterDiagnostics
  Java_tech_kayys_aqli_1edge_AqliEdgePlugin_nativeRunOperation
  Java_tech_kayys_aqli_1edge_AqliEdgePlugin_nativeRunText
)
RUNTIME_EXPORTS_VERIFIED=false
RUNTIME_EXPORT_SYMBOL_COUNT=0
ANDROID_RUNTIME_EXPORTS_VERIFIED=false
ANDROID_RUNTIME_EXPORT_LIBRARY_COUNT=0

mkdir -p "$GENERATED_INCLUDE_DIR" "$DIST_DIR" "$(dirname "$PROFILE_JSON")" "$(dirname "$PUBLIC_CONFIG_HEADER")"

csv_has() {
  local csv=",$1,"
  local needle="$2"
  [[ "$csv" == *",$needle,"* ]]
}

truthy() {
  case "${1:-}" in
    1|true|TRUE|yes|YES|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}

native_shared_library() {
  local path="$1"
  local kind
  [[ -f "$path" ]] || return 1
  kind="$(native_library_kind "$path")"
  case "$HOST_OS" in
    darwin)
      [[ "$kind" == Mach-O* && "$kind" == *"dynamically linked shared library"* ]]
      ;;
    linux)
      [[ "$kind" == ELF* && "$kind" == *"shared object"* ]]
      ;;
    *)
      return 0
      ;;
  esac
}

native_library_kind() {
  local path="$1"
  file -Lb "$path" 2>/dev/null || printf 'unreadable'
}

android_shared_library() {
  local path="$1"
  local kind
  [[ -f "$path" ]] || return 1
  kind="$(native_library_kind "$path")"
  [[ "$kind" == ELF* && "$kind" == *"shared object"* ]]
}

trim_value() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

normalize_android_abis_csv() {
  local raw="$1"
  local item
  local csv=""
  raw="${raw//;/,}"
  raw="${raw// /,}"
  IFS=',' read -r -a parts <<< "$raw"
  for item in "${parts[@]}"; do
    item="$(trim_value "$item")"
    case "$item" in
      "") continue ;;
      arm64-v8a|armeabi-v7a|x86|x86_64) ;;
      *)
        echo ":| Warning: ignoring unsupported Android ABI: $item" >&2
        continue
        ;;
    esac
    if [[ ",$csv," != *",$item,"* ]]; then
      csv="${csv:+$csv,}$item"
    fi
  done
  if [[ -z "$csv" ]]; then
    csv="arm64-v8a"
  fi
  printf '%s' "$csv"
}

find_android_ndk_toolchain_file() {
  local ndk_root
  local sdk_root
  local candidate
  local override="${AQLI_EDGE_ANDROID_NDK_HOME:-${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}}"

  if [[ -n "$override" ]]; then
    candidate="$override/build/cmake/android.toolchain.cmake"
    if [[ -f "$candidate" ]]; then
      printf '%s' "$candidate"
      return 0
    fi
  fi

  for sdk_root in \
    "${ANDROID_HOME:-}" \
    "${ANDROID_SDK_ROOT:-}" \
    "$HOME/Library/Android/sdk"; do
    [[ -n "$sdk_root" && -d "$sdk_root/ndk" ]] || continue
    while IFS= read -r ndk_root; do
      candidate="$ndk_root/build/cmake/android.toolchain.cmake"
      if [[ -f "$candidate" ]]; then
        printf '%s' "$candidate"
        return 0
      fi
    done < <(find "$sdk_root/ndk" -mindepth 1 -maxdepth 1 -type d -print | sort -r)
  done

  return 1
}

android_ndk_tool_executable() {
  local toolchain_file="$1"
  local tool_name="$2"
  local ndk_root
  local host_tag
  local candidate

  ndk_root="${toolchain_file%/build/cmake/android.toolchain.cmake}"
  case "$HOST_OS" in
    darwin) host_tag="darwin-x86_64" ;;
    linux) host_tag="linux-x86_64" ;;
    *) return 1 ;;
  esac

  candidate="$ndk_root/toolchains/llvm/prebuilt/$host_tag/bin/$tool_name"
  [[ -x "$candidate" ]] || return 1
  printf '%s' "$candidate"
}

android_llvm_nm_executable() {
  android_ndk_tool_executable "$1" llvm-nm
}

android_llvm_strip_executable() {
  android_ndk_tool_executable "$1" llvm-strip
}

darwin_dylib_install_name() {
  local path="$1"
  otool -D "$path" 2>/dev/null | tail -n 1 | xargs basename 2>/dev/null || true
}

runtime_symbol_table() {
  local path="$1"
  case "$HOST_OS" in
    darwin)
      nm -gU "$path" 2>/dev/null || true
      ;;
    linux)
      nm -D --defined-only "$path" 2>/dev/null || true
      ;;
    *)
      printf ''
      ;;
  esac
}

verify_runtime_exports() {
  local path="$1"
  local symbols
  local symbol
  local needle
  local -a missing=()

  if [[ ! -f "$path" ]]; then
    echo ":| Native runtime library was not produced: $path" >&2
    return 1
  fi
  if ! native_shared_library "$path"; then
    echo ":| Native runtime output is not a shared library: $path ($(native_library_kind "$path"))" >&2
    return 1
  fi
  if ! command -v nm >/dev/null 2>&1; then
    echo ":| Cannot verify native runtime exports because nm is not installed" >&2
    return 1
  fi

  symbols="$(runtime_symbol_table "$path")"
  if [[ -z "$symbols" ]]; then
    echo ":| Cannot read native runtime exports from: $path" >&2
    return 1
  fi

  for symbol in "${REQUIRED_RUNTIME_SYMBOLS[@]}"; do
    case "$HOST_OS" in
      darwin) needle="_$symbol" ;;
      *) needle="$symbol" ;;
    esac
    if [[ "$symbols" != *"$needle"* ]]; then
      missing+=("$symbol")
    fi
  done

  if (( ${#missing[@]} > 0 )); then
    echo ":| Native runtime is missing required exports:" >&2
    for symbol in "${missing[@]}"; do
      echo "   - $symbol" >&2
    done
    echo ":| Check mobile/aqli_edge/native/CMakeLists.txt is building src/aqli_edge_runtime.c" >&2
    return 1
  fi

  RUNTIME_EXPORTS_VERIFIED=true
  RUNTIME_EXPORT_SYMBOL_COUNT=${#REQUIRED_RUNTIME_SYMBOLS[@]}
  echo ":) Verified Aqli Edge runtime exports: $RUNTIME_EXPORT_SYMBOL_COUNT symbols"
}

verify_android_runtime_exports() {
  local path="$1"
  local toolchain_file="$2"
  local symbols
  local symbol
  local nm_bin
  local -a missing=()

  if [[ ! -f "$path" ]]; then
    echo ":| Android native runtime library was not produced: $path" >&2
    return 1
  fi
  if ! android_shared_library "$path"; then
    echo ":| Android runtime output is not an ELF shared library: $path ($(native_library_kind "$path"))" >&2
    return 1
  fi

  nm_bin="$(android_llvm_nm_executable "$toolchain_file" || true)"
  if [[ -z "$nm_bin" ]]; then
    echo ":| Cannot verify Android runtime exports because NDK llvm-nm was not found" >&2
    return 1
  fi

  symbols="$("$nm_bin" -D --defined-only "$path" 2>/dev/null || true)"
  if [[ -z "$symbols" ]]; then
    echo ":| Cannot read Android runtime exports from: $path" >&2
    return 1
  fi

  for symbol in "${REQUIRED_ANDROID_RUNTIME_SYMBOLS[@]}"; do
    if [[ "$symbols" != *"$symbol"* ]]; then
      missing+=("$symbol")
    fi
  done

  if (( ${#missing[@]} > 0 )); then
    echo ":| Android runtime is missing required exports for $(basename "$(dirname "$path")"):" >&2
    for symbol in "${missing[@]}"; do
      echo "   - $symbol" >&2
    done
    echo ":| Check mobile/aqli_edge/android/src/main/cpp/CMakeLists.txt and aqli_edge_jni.c" >&2
    return 1
  fi

  ANDROID_RUNTIME_EXPORTS_VERIFIED=true
  ANDROID_RUNTIME_EXPORT_LIBRARY_COUNT=$((ANDROID_RUNTIME_EXPORT_LIBRARY_COUNT + 1))
}

strip_android_runtime_library() {
  local path="$1"
  local toolchain_file="$2"
  local strip_bin

  strip_bin="$(android_llvm_strip_executable "$toolchain_file" || true)"
  if [[ -z "$strip_bin" ]]; then
    echo ":| Warning: Android NDK llvm-strip was not found; keeping unstripped runtime: $path" >&2
    return 0
  fi
  "$strip_bin" --strip-unneeded "$path"
}

json_array_from_csv() {
  local csv="$1"
  local out="["
  local item
  IFS=',' read -r -a parts <<< "$csv"
  for item in "${parts[@]}"; do
    [[ -z "$item" ]] && continue
    if [[ "$out" != "[" ]]; then
      out+=", "
    fi
    out+="\"${item}\""
  done
  out+="]"
  printf '%s' "$out"
}

json_string_literal() {
  local input="$1"
  printf '%s' "$input" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

json_native_libraries_array() {
  local out="["
  local entry
  local label
  local path
  local kind
  for entry in "$@"; do
    IFS='|' read -r label path kind <<< "$entry"
    if [[ "$out" != "[" ]]; then
      out+=", "
    fi
    out+="{\"label\":\"$(json_string_literal "$label")\",\"path\":\"$(json_string_literal "$path")\",\"kind\":\"$(json_string_literal "$kind")\"}"
  done
  out+="]"
  printf '%s' "$out"
}

c_string_literal() {
  local input="$1"
  printf '%s' "$input" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

record_invalid_native_library() {
  local label="$1"
  local path="$2"
  local optional="${3:-false}"
  local kind
  kind="$(native_library_kind "$path")"
  if [[ "$optional" == "true" ]]; then
    OPTIONAL_INVALID_NATIVE_LIBS+=("$label|$path|$kind")
    echo ":| Warning: ignoring optional non-native $label library: $path ($kind)" >&2
  else
    INVALID_NATIVE_LIBS+=("$label|$path|$kind")
    echo ":| Warning: skipping non-native $label library: $path ($kind)" >&2
  fi
}

packaged_runtime_format() {
  local name
  name="$(basename "$1")"
  case "$name" in
    libaqli_edge_runtime.*) printf 'bridge' ;;
    libLiteRt.*|libLiteRtMetalAccelerator.*|libtensorflowlite_c.*) printf 'litert' ;;
    libonnxruntime.*) printf 'onnx' ;;
    *) printf '' ;;
  esac
}

prune_unselected_runtime_library() {
  local path="$1"
  local target_format
  target_format="$(packaged_runtime_format "$path")"
  case "$target_format" in
    ""|bridge) return 1 ;;
    litert|onnx)
      if ! csv_has "$FORMAT_TARGETS_VALUE" "$target_format"; then
        echo ":) Removing stale unselected $target_format runtime: $path"
        rm -f "$path"
        return 0
      fi
      ;;
  esac
  return 1
}

prune_unselected_macos_libraries() {
  local destination_lib
  for destination_lib in "$MOBILE_PLUGIN_DIR_VALUE/macos/Libraries"/*.dylib; do
    [[ -e "$destination_lib" || -L "$destination_lib" ]] || continue
    prune_unselected_runtime_library "$destination_lib" || true
  done
}

prune_unselected_android_libraries() {
  local destination_lib
  [[ -d "$ANDROID_JNILIBS_DIR" ]] || return 0
  while IFS= read -r -d '' destination_lib; do
    prune_unselected_runtime_library "$destination_lib" || true
  done < <(find "$ANDROID_JNILIBS_DIR" -type f \( -name 'libLiteRt.so' -o -name 'libtensorflowlite_c.so' -o -name 'libonnxruntime.so' \) -print0)
}

copy_macos_runtime_library() {
  local source_path="$1"
  local destination_name="${2:-$(basename "$source_path")}"
  local destination_path="$MOBILE_PLUGIN_DIR_VALUE/macos/Libraries/$destination_name"

  rm -f "$destination_path"
  cp -fP "$source_path" "$destination_path"
}

package_macos_onnx_runtime_libraries() {
  local libraries_dir="$MOBILE_PLUGIN_DIR_VALUE/macos/Libraries"
  local source_path
  local install_name
  local alias_path="$libraries_dir/libonnxruntime.dylib"
  local target_name
  local target_path

  [[ "$HOST_OS" == "darwin" ]] || return 0

  for source_path in "${ONNX_PACKAGED_LIBS[@]}"; do
    [[ "$(basename "$source_path")" == "libonnxruntime.dylib" ]] || continue
    install_name="$(darwin_dylib_install_name "$source_path")"
    if [[ -n "$install_name" && "$install_name" != "libonnxruntime.dylib" ]]; then
      target_name="$install_name"
      target_path="$libraries_dir/$target_name"
      rm -f "$alias_path" "$target_path"
      cp -fL "$source_path" "$target_path"
      ln -s "$target_name" "$alias_path"
      echo ":) Packaged ONNX runtime as versioned dylib plus alias: $(basename "$alias_path") -> $target_name"
      return 0
    fi
    copy_macos_runtime_library "$source_path"
    return 0
  done

  for source_path in "${ONNX_PACKAGED_LIBS[@]}"; do
    [[ -f "$source_path" ]] || continue
    copy_macos_runtime_library "$source_path"
  done
}

android_delegate_source_library() {
  local abi="$1"
  local format="$2"
  case "$format" in
    litert) printf '%s/%s/libLiteRt.so' "$ANDROID_LIB_DIR" "$abi" ;;
    onnx) printf '%s/%s/libonnxruntime.so' "$ANDROID_LIB_DIR" "$abi" ;;
    *) return 1 ;;
  esac
}

android_runtime_source_available() {
  local abi="$1"
  local format="$2"
  local source_path

  csv_has "$FORMAT_TARGETS_VALUE" "$format" || return 1
  source_path="$(android_delegate_source_library "$abi" "$format")"
  android_shared_library "$source_path"
}

build_android_runtime_for_abi() {
  local abi="$1"
  local toolchain_file="$2"
  local build_dir="$ANDROID_BUILD_DIR/$abi"
  local output_dir="$build_dir/out"
  local output_path="$output_dir/libaqli_edge_runtime.so"
  local destination_path="$ANDROID_JNILIBS_DIR/$abi/libaqli_edge_runtime.so"
  local android_litert_runtime_cmake=OFF
  local android_litert_text_cmake=OFF
  local android_onnx_runtime_cmake=OFF
  local android_onnx_text_cmake=OFF

  if android_runtime_source_available "$abi" "litert"; then
    android_litert_runtime_cmake=ON
    if truthy "${AQLI_EDGE_ENABLE_LITERT_TEXT_GENERATION:-false}"; then
      android_litert_text_cmake=ON
    fi
  fi
  if android_runtime_source_available "$abi" "onnx"; then
    android_onnx_runtime_cmake=ON
    if truthy "${AQLI_EDGE_ENABLE_ONNX_TEXT_GENERATION:-false}"; then
      android_onnx_text_cmake=ON
    fi
  fi

  cmake \
    -S "$MOBILE_PLUGIN_DIR_VALUE/android/src/main/cpp" \
    -B "$build_dir" \
    -DCMAKE_TOOLCHAIN_FILE="$toolchain_file" \
    -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM="$ANDROID_PLATFORM_VALUE" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_LIBRARY_OUTPUT_DIRECTORY="$output_dir" \
    -DAQLI_EDGE_ENABLE_LITERT_RUNTIME="$android_litert_runtime_cmake" \
    -DAQLI_EDGE_ENABLE_LITERT_TEXT_GENERATION="$android_litert_text_cmake" \
    -DAQLI_EDGE_ENABLE_ONNX_RUNTIME="$android_onnx_runtime_cmake" \
    -DAQLI_EDGE_ENABLE_ONNX_TEXT_GENERATION="$android_onnx_text_cmake"
  cmake --build "$build_dir" --config Release --target aqli_edge_runtime

  if [[ ! -f "$output_path" ]]; then
    output_path="$(find "$build_dir" -type f -name 'libaqli_edge_runtime.so' -print -quit)"
  fi

  strip_android_runtime_library "$output_path" "$toolchain_file"
  verify_android_runtime_exports "$output_path" "$toolchain_file"
  mkdir -p "$ANDROID_JNILIBS_DIR/$abi"
  cp -fL "$output_path" "$destination_path"
  ANDROID_PACKAGED_LIBS+=("$destination_path")
  ANDROID_BRIDGE_RUNTIME_AVAILABLE=true
  echo ":) Packaged Android Aqli Edge bridge: $abi/libaqli_edge_runtime.so"
}

package_android_runtime_bridge() {
  local toolchain_file
  local abi
  local -a abis

  toolchain_file="$(find_android_ndk_toolchain_file || true)"
  if [[ -z "$toolchain_file" ]]; then
    ANDROID_BRIDGE_RUNTIME_ERROR="Android NDK not found; set AQLI_EDGE_ANDROID_NDK_HOME, ANDROID_NDK_HOME, ANDROID_NDK_ROOT, ANDROID_HOME, or ANDROID_SDK_ROOT"
    echo ":| Warning: $ANDROID_BRIDGE_RUNTIME_ERROR" >&2
    return 0
  fi

  ANDROID_NDK_TOOLCHAIN_FILE="$toolchain_file"
  IFS=',' read -r -a abis <<< "$ANDROID_ABIS_CSV"
  for abi in "${abis[@]}"; do
    [[ -n "$abi" ]] || continue
    build_android_runtime_for_abi "$abi" "$toolchain_file"
  done
}

ANDROID_ABIS_CSV="$(normalize_android_abis_csv "$ANDROID_ABIS_VALUE")"

PROFILE_JSON_TEXT="$(printf '{"runtimeTarget":"%s","pluginPath":"mobile/aqli_edge","modelFormats":%s,"mobileFeatures":%s,"modelSidecarFiles":%s}' \
  "${RUNTIME_TARGET:-mobile}" \
  "$(json_array_from_csv "$FORMAT_TARGETS_VALUE")" \
  "$(json_array_from_csv "$MOBILE_FEATURES_VALUE")" \
  "$(json_array_from_csv "$MODEL_SIDECAR_FILES_VALUE")")"

{
  printf '{\n'
  printf '  "runtimeTarget": "%s",\n' "${RUNTIME_TARGET:-mobile}"
  printf '  "pluginPath": "mobile/aqli_edge",\n'
  printf '  "modelFormats": %s,\n' "$(json_array_from_csv "$FORMAT_TARGETS_VALUE")"
  printf '  "mobileFeatures": %s,\n' "$(json_array_from_csv "$MOBILE_FEATURES_VALUE")"
  printf '  "modelSidecarFiles": %s\n' "$(json_array_from_csv "$MODEL_SIDECAR_FILES_VALUE")"
  printf '}\n'
} > "$PROFILE_JSON"

for config_header in "$GENERATED_INCLUDE_DIR/aqli_edge_runtime_config.h" "$PUBLIC_CONFIG_HEADER"; do
  {
    printf '#ifndef AQLI_EDGE_RUNTIME_CONFIG_H_\n'
    printf '#define AQLI_EDGE_RUNTIME_CONFIG_H_\n\n'
    printf '#define AQLI_EDGE_PROFILE_JSON "%s"\n' "$(c_string_literal "$PROFILE_JSON_TEXT")"
    printf '#define AQLI_EDGE_MODEL_FORMATS_CSV "%s"\n' "$FORMAT_TARGETS_VALUE"
    printf '#define AQLI_EDGE_MOBILE_FEATURES_CSV "%s"\n' "$MOBILE_FEATURES_VALUE"
    printf '#define AQLI_EDGE_BUILD_PROFILE "%s"\n' "$BUILD_PROFILE_VALUE"
    printf '\n#endif  // AQLI_EDGE_RUNTIME_CONFIG_H_\n'
  } > "$config_header"
done

LITERT_LIB_DIR="${AQLI_EDGE_LITERT_LIB_DIR:-$HOME/.aqli/libs}"
ONNX_LIB_DIR="${AQLI_EDGE_ONNX_LIB_DIR:-$HOME/.aqli/libs}"
ANDROID_LIB_DIR="${AQLI_EDGE_ANDROID_LIB_DIR:-$HOME/.aqli/libs/android}"
LITERT_RUNTIME_AVAILABLE=false
ONNX_RUNTIME_AVAILABLE=false
ANDROID_LITERT_RUNTIME_AVAILABLE=false
ANDROID_ONNX_RUNTIME_AVAILABLE=false
ANDROID_BRIDGE_RUNTIME_AVAILABLE=false
ANDROID_BRIDGE_RUNTIME_ERROR=""
ANDROID_NDK_TOOLCHAIN_FILE=""
LITERT_TEXT_GENERATION_AVAILABLE=false
ONNX_TEXT_GENERATION_AVAILABLE=false
declare -a LITERT_PACKAGED_LIBS=()
declare -a ONNX_PACKAGED_LIBS=()
declare -a ANDROID_PACKAGED_LIBS=()
declare -a INVALID_NATIVE_LIBS=()
declare -a OPTIONAL_INVALID_NATIVE_LIBS=()
declare -a ONNX_SEARCH_DIRS=("$ONNX_LIB_DIR")
if [[ "$ONNX_LIB_DIR" != "$HOME/.aqli/libs/onnxruntime" ]]; then
  ONNX_SEARCH_DIRS+=("$HOME/.aqli/libs/onnxruntime")
fi

if csv_has "$FORMAT_TARGETS_VALUE" "litert"; then
  case "$HOST_OS" in
    darwin)
      if [[ -f "$LITERT_LIB_DIR/libLiteRt.dylib" ]]; then
        LITERT_RUNTIME_AVAILABLE=true
        for lib_name in libLiteRt.dylib libLiteRtMetalAccelerator.dylib libtensorflowlite_c.dylib; do
          lib_path="$LITERT_LIB_DIR/$lib_name"
          if [[ -f "$lib_path" ]]; then
            if native_shared_library "$lib_path"; then
              LITERT_PACKAGED_LIBS+=("$lib_path")
            else
              if [[ "$lib_name" == "libtensorflowlite_c.dylib" ]]; then
                record_invalid_native_library "LiteRT" "$lib_path" true
              else
                record_invalid_native_library "LiteRT" "$lib_path" false
              fi
            fi
          fi
        done
      fi
      ;;
    linux)
      if [[ -f "$LITERT_LIB_DIR/libLiteRt.so" ]]; then
        LITERT_RUNTIME_AVAILABLE=true
        for lib_name in libLiteRt.so libLiteRtMetalAccelerator.so libtensorflowlite_c.so; do
          lib_path="$LITERT_LIB_DIR/$lib_name"
          if [[ -f "$lib_path" ]]; then
            if native_shared_library "$lib_path"; then
              LITERT_PACKAGED_LIBS+=("$lib_path")
            else
              if [[ "$lib_name" == "libtensorflowlite_c.so" ]]; then
                record_invalid_native_library "LiteRT" "$lib_path" true
              else
                record_invalid_native_library "LiteRT" "$lib_path" false
              fi
            fi
          fi
        done
      fi
      ;;
  esac
fi

if csv_has "$FORMAT_TARGETS_VALUE" "onnx"; then
  case "$HOST_OS" in
    darwin)
      for lib_dir in "${ONNX_SEARCH_DIRS[@]}"; do
        if [[ "$ONNX_RUNTIME_AVAILABLE" == false && -f "$lib_dir/libonnxruntime.dylib" ]] && native_shared_library "$lib_dir/libonnxruntime.dylib"; then
          install_name="$(darwin_dylib_install_name "$lib_dir/libonnxruntime.dylib")"
          ONNX_RUNTIME_AVAILABLE=true
          ONNX_PACKAGED_LIBS+=("$lib_dir/libonnxruntime.dylib")
          if [[ -n "$install_name" && "$install_name" != "libonnxruntime.dylib" && -f "$lib_dir/$install_name" ]] && native_shared_library "$lib_dir/$install_name"; then
            ONNX_PACKAGED_LIBS+=("$lib_dir/$install_name")
          fi
        fi
      done
      ;;
    linux)
      for lib_dir in "${ONNX_SEARCH_DIRS[@]}"; do
        if [[ "$ONNX_RUNTIME_AVAILABLE" == false && -f "$lib_dir/libonnxruntime.so" ]] && native_shared_library "$lib_dir/libonnxruntime.so"; then
          ONNX_RUNTIME_AVAILABLE=true
          ONNX_PACKAGED_LIBS+=("$lib_dir/libonnxruntime.so")
        fi
      done
      ;;
  esac
fi

if [[ "$LITERT_RUNTIME_AVAILABLE" == true ]] && truthy "${AQLI_EDGE_ENABLE_LITERT_TEXT_GENERATION:-false}"; then
  LITERT_TEXT_GENERATION_AVAILABLE=true
fi
if [[ "$ONNX_RUNTIME_AVAILABLE" == true ]] && truthy "${AQLI_EDGE_ENABLE_ONNX_TEXT_GENERATION:-false}"; then
  ONNX_TEXT_GENERATION_AVAILABLE=true
fi

litert_runtime_cmake=OFF
litert_text_cmake=OFF
onnx_runtime_cmake=OFF
onnx_text_cmake=OFF
if [[ "$LITERT_RUNTIME_AVAILABLE" == true ]]; then
  litert_runtime_cmake=ON
fi
if [[ "$LITERT_TEXT_GENERATION_AVAILABLE" == true ]]; then
  litert_text_cmake=ON
fi
if [[ "$ONNX_RUNTIME_AVAILABLE" == true ]]; then
  onnx_runtime_cmake=ON
fi
if [[ "$ONNX_TEXT_GENERATION_AVAILABLE" == true ]]; then
  onnx_text_cmake=ON
fi

cmake_args=(
  -S "$NATIVE_DIR"
  -B "$BUILD_DIR"
  -DCMAKE_BUILD_TYPE=Release
  -DCMAKE_INSTALL_PREFIX="$DIST_DIR"
  -DAQLI_EDGE_GENERATED_INCLUDE_DIR="$GENERATED_INCLUDE_DIR"
  -DAQLI_EDGE_ENABLE_LITERT_RUNTIME="$litert_runtime_cmake"
  -DAQLI_EDGE_ENABLE_LITERT_TEXT_GENERATION="$litert_text_cmake"
  -DAQLI_EDGE_ENABLE_ONNX_RUNTIME="$onnx_runtime_cmake"
  -DAQLI_EDGE_ENABLE_ONNX_TEXT_GENERATION="$onnx_text_cmake"
)
if [[ "$HOST_OS" == "darwin" ]]; then
  cmake_args+=(
    -DCMAKE_OSX_DEPLOYMENT_TARGET="${MACOS_DEPLOYMENT_TARGET_VALUE}"
  )
fi
cmake "${cmake_args[@]}"
cmake --build "$BUILD_DIR" --config Release
ctest --test-dir "$BUILD_DIR" --output-on-failure
cmake --install "$BUILD_DIR" --config Release

case "$HOST_OS" in
  darwin) LIB_NAME="libaqli_edge_runtime.dylib" ;;
  linux) LIB_NAME="libaqli_edge_runtime.so" ;;
  *) LIB_NAME="aqli_edge_runtime.dll" ;;
esac

LIB_PATH="$DIST_DIR/lib/$LIB_NAME"
verify_runtime_exports "$LIB_PATH"

mkdir -p "$MOBILE_PLUGIN_DIR_VALUE/macos/Libraries"
prune_unselected_macos_libraries
for destination_lib in "$MOBILE_PLUGIN_DIR_VALUE/macos/Libraries"/*.dylib; do
  [[ -e "$destination_lib" || -L "$destination_lib" ]] || continue
  if ! native_shared_library "$destination_lib"; then
    kind="$(native_library_kind "$destination_lib")"
    echo ":| Warning: removing non-native packaged library: $destination_lib ($kind)" >&2
    rm -f "$destination_lib"
  fi
done
if [[ -f "$LIB_PATH" && "$HOST_OS" == "darwin" ]]; then
  cp "$LIB_PATH" "$MOBILE_PLUGIN_DIR_VALUE/macos/Libraries/$LIB_NAME"
  for packaged_lib in "${LITERT_PACKAGED_LIBS[@]}"; do
    if [[ -f "$packaged_lib" ]]; then
      copy_macos_runtime_library "$packaged_lib"
    fi
  done
  package_macos_onnx_runtime_libraries
fi

package_android_runtime_bridge
prune_unselected_android_libraries
if [[ -d "$ANDROID_LIB_DIR" ]]; then
  while IFS= read -r -d '' android_lib; do
    abi="$(basename "$(dirname "$android_lib")")"
    android_lib_name="$(basename "$android_lib")"
    android_label=""
    optional_android_invalid=false
    case "$abi" in
      arm64-v8a|armeabi-v7a|x86|x86_64) ;;
      *) continue ;;
    esac

    if [[ ",$ANDROID_ABIS_CSV," != *",$abi,"* ]]; then
      continue
    fi

    case "$android_lib_name" in
      libLiteRt.so|libtensorflowlite_c.so)
        if ! csv_has "$FORMAT_TARGETS_VALUE" "litert"; then
          continue
        fi
        android_label="LiteRT"
        if [[ "$android_lib_name" == "libtensorflowlite_c.so" ]]; then
          optional_android_invalid=true
        fi
        ;;
      libonnxruntime.so)
        if ! csv_has "$FORMAT_TARGETS_VALUE" "onnx"; then
          continue
        fi
        android_label="ONNX"
        ;;
      *)
        continue
        ;;
    esac

    if ! android_shared_library "$android_lib"; then
      if [[ "$optional_android_invalid" == "true" ]]; then
        record_invalid_native_library "$android_label" "$android_lib" true
      else
        record_invalid_native_library "$android_label" "$android_lib" false
      fi
      continue
    fi

    case "$android_lib_name" in
      libLiteRt.so) ANDROID_LITERT_RUNTIME_AVAILABLE=true ;;
      libonnxruntime.so) ANDROID_ONNX_RUNTIME_AVAILABLE=true ;;
    esac

    mkdir -p "$ANDROID_JNILIBS_DIR/$abi"
    cp -fL "$android_lib" "$ANDROID_JNILIBS_DIR/$abi/$android_lib_name"
    ANDROID_PACKAGED_LIBS+=("$ANDROID_JNILIBS_DIR/$abi/$android_lib_name")
  done < <(find "$ANDROID_LIB_DIR" -type f \( -name 'libLiteRt.so' -o -name 'libtensorflowlite_c.so' -o -name 'libonnxruntime.so' \) -print0)
fi

invalid_native_libraries_json="[]"
optional_invalid_native_libraries_json="[]"
if (( ${#INVALID_NATIVE_LIBS[@]} > 0 )); then
  invalid_native_libraries_json="$(json_native_libraries_array "${INVALID_NATIVE_LIBS[@]}")"
fi
if (( ${#OPTIONAL_INVALID_NATIVE_LIBS[@]} > 0 )); then
  optional_invalid_native_libraries_json="$(json_native_libraries_array "${OPTIONAL_INVALID_NATIVE_LIBS[@]}")"
fi

{
  printf '{\n'
  printf '  "runtime": "aqli_edge_native",\n'
  printf '  "version": "0.1.0",\n'
  printf '  "runtimeOrigin": "aqli-builder-native-c",\n'
  printf '  "runtimeIntegration": "flutter-method-channel-ffi-native-c",\n'
  printf '  "aqliRelationship": "generated-by-aqli-builder",\n'
  printf '  "usesAqliCli": false,\n'
  printf '  "usesAqliJvmRuntime": false,\n'
  printf '  "host": "%s-%s",\n' "$HOST_OS" "$HOST_ARCH"
  printf '  "buildProfile": "%s",\n' "$BUILD_PROFILE_VALUE"
  printf '  "macosDeploymentTarget": "%s",\n' "$MACOS_DEPLOYMENT_TARGET_VALUE"
  printf '  "libraryPath": "%s",\n' "$LIB_PATH"
  printf '  "headerPath": "%s",\n' "$DIST_DIR/include/aqli_edge_runtime.h"
  printf '  "compiled": true,\n'
  printf '  "runtimeExportsVerified": %s,\n' "$RUNTIME_EXPORTS_VERIFIED"
  printf '  "runtimeExportSymbolCount": %s,\n' "$RUNTIME_EXPORT_SYMBOL_COUNT"
  printf '  "litertRuntimeLinked": %s,\n' "$LITERT_RUNTIME_AVAILABLE"
  printf '  "litertTextGenerationRunnable": %s,\n' "$LITERT_TEXT_GENERATION_AVAILABLE"
  printf '  "onnxRuntimeLinked": %s,\n' "$ONNX_RUNTIME_AVAILABLE"
  printf '  "onnxTextGenerationRunnable": %s,\n' "$ONNX_TEXT_GENERATION_AVAILABLE"
  printf '  "androidBridgeRuntimePackaged": %s,\n' "$ANDROID_BRIDGE_RUNTIME_AVAILABLE"
  printf '  "androidRuntimeExportsVerified": %s,\n' "$ANDROID_RUNTIME_EXPORTS_VERIFIED"
  printf '  "androidRuntimeExportLibraryCount": %s,\n' "$ANDROID_RUNTIME_EXPORT_LIBRARY_COUNT"
  printf '  "androidAbis": %s,\n' "$(json_array_from_csv "$ANDROID_ABIS_CSV")"
  printf '  "androidPlatform": "%s",\n' "$ANDROID_PLATFORM_VALUE"
  printf '  "androidNdkToolchainFile": "%s",\n' "$(json_string_literal "$ANDROID_NDK_TOOLCHAIN_FILE")"
  printf '  "androidLiteRtRuntimePackaged": %s,\n' "$ANDROID_LITERT_RUNTIME_AVAILABLE"
  printf '  "androidOnnxRuntimePackaged": %s,\n' "$ANDROID_ONNX_RUNTIME_AVAILABLE"
  printf '  "androidBridgeRuntimeError": "%s",\n' "$(json_string_literal "$ANDROID_BRIDGE_RUNTIME_ERROR")"
  printf '  "invalidNativeLibraries": %s,\n' "$invalid_native_libraries_json"
  printf '  "optionalInvalidNativeLibraries": %s,\n' "$optional_invalid_native_libraries_json"
  printf '  "modelFormats": %s,\n' "$(json_array_from_csv "$FORMAT_TARGETS_VALUE")"
  printf '  "mobileFeatures": %s,\n' "$(json_array_from_csv "$MOBILE_FEATURES_VALUE")"
  printf '  "modelSidecarFiles": %s\n' "$(json_array_from_csv "$MODEL_SIDECAR_FILES_VALUE")"
  printf '}\n'
} > "$BUILD_INFO_JSON"

echo ":) Built Aqli Edge native runtime: $LIB_PATH"
echo ":) LiteRT runtime: linked=$LITERT_RUNTIME_AVAILABLE runnable=$LITERT_TEXT_GENERATION_AVAILABLE libs=${#LITERT_PACKAGED_LIBS[@]}"
echo ":) ONNX runtime: linked=$ONNX_RUNTIME_AVAILABLE runnable=$ONNX_TEXT_GENERATION_AVAILABLE libs=${#ONNX_PACKAGED_LIBS[@]}"
echo ":) Android bridge: packaged=$ANDROID_BRIDGE_RUNTIME_AVAILABLE abis=$ANDROID_ABIS_CSV exportsVerified=$ANDROID_RUNTIME_EXPORTS_VERIFIED"
echo ":) Android native libs: litert=$ANDROID_LITERT_RUNTIME_AVAILABLE onnx=$ANDROID_ONNX_RUNTIME_AVAILABLE libs=${#ANDROID_PACKAGED_LIBS[@]}"
echo ":) Wrote Flutter build metadata: $BUILD_INFO_JSON"
