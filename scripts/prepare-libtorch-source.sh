#!/usr/bin/env bash
set -euo pipefail

resolve_aqli_home() {
  if [ -n "${AQLI_HOME:-}" ]; then
    echo "$AQLI_HOME"
    return
  fi
  echo "$HOME/.aqli"
}

AQLI_HOME_RESOLVED="$(resolve_aqli_home)"
LIBTORCH_SRC="${AQLI_LIBTORCH_SOURCE_DIR:-$AQLI_HOME_RESOLVED/source/vendor/libtorch}"

detect_shared_ext() {
  case "$(uname -s)" in
    Darwin) echo ".dylib" ;;
    MINGW*|MSYS*|CYGWIN*) echo ".dll" ;;
    *) echo ".so" ;;
  esac
}

platform_hint() {
  case "$(uname -s):$(uname -m)" in
    Darwin:arm64|Darwin:aarch64)
      echo "Download a macOS Apple Silicon archive such as https://download.pytorch.org/libtorch/nightly/cpu/libtorch-macos-arm64-latest.zip"
      ;;
    Darwin:*)
      echo "Download a macOS LibTorch archive instead of a Linux .so bundle."
      ;;
    Linux:*)
      echo "Download a Linux LibTorch archive instead of macOS or Windows binaries."
      ;;
    *)
      echo "Download a LibTorch archive that matches this platform."
      ;;
  esac
}

validate_libtorch_dir() {
  local dir="$1"
  local expected_ext
  expected_ext="$(detect_shared_ext)"
  local shared_list
  shared_list="$(
    find "$dir" -maxdepth 2 -type f \( -name '*.dylib' -o -name '*.so' -o -name '*.dll' \) -print 2>/dev/null \
      | sed "s#^$dir/##" \
      | sort
  )"

  if [ -z "$shared_list" ]; then
    return 0
  fi

  if printf '%s\n' "$shared_list" | grep -q "${expected_ext}\$"; then
    return 0
  fi

  echo "LibTorch archive is not compatible with this platform."
  echo "Found shared libraries:"
  printf '%s\n' "$shared_list"
  echo "$(platform_hint)"
  return 1
}

mkdir -p "$(dirname "$LIBTORCH_SRC")"

if [ -d "$LIBTORCH_SRC" ]; then
  echo "Using existing libtorch source at: $LIBTORCH_SRC"
  exit 0
fi

if [ -n "${AQLI_LIBTORCH_ARCHIVE_URL:-}" ]; then
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT
  ARCHIVE_PATH="$TMP_DIR/libtorch.zip"
  curl -fsSL "$AQLI_LIBTORCH_ARCHIVE_URL" -o "$ARCHIVE_PATH"
  unzip -q "$ARCHIVE_PATH" -d "$TMP_DIR"
  if [ -d "$TMP_DIR/libtorch" ]; then
    validate_libtorch_dir "$TMP_DIR/libtorch"
    mv "$TMP_DIR/libtorch" "$LIBTORCH_SRC"
    echo "Prepared libtorch source from archive at: $LIBTORCH_SRC"
    exit 0
  fi
  echo "Archive did not contain a top-level 'libtorch' directory: $AQLI_LIBTORCH_ARCHIVE_URL"
  exit 1
fi

echo "No libtorch source prepared. Expected existing directory at: $LIBTORCH_SRC"
echo "Set AQLI_LIBTORCH_ARCHIVE_URL to auto-download in CI when needed."
echo "Continuing without libtorch source; build may skip libtorch-dependent paths."
mkdir -p "$LIBTORCH_SRC/lib"
exit 0
