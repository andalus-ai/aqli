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
LLAMA_SRC="${AQLI_LLAMA_SOURCE_DIR:-$AQLI_HOME_RESOLVED/source/vendor/llama.cpp}"
LLAMA_REPO_URL="${AQLI_LLAMA_REPO_URL:-https://github.com/bhangun/llama.cpp.git}"
LLAMA_REF="${AQLI_LLAMA_REF:-origin/master}"

mkdir -p "$(dirname "$LLAMA_SRC")"

if [ ! -d "$LLAMA_SRC/.git" ]; then
  rm -rf "$LLAMA_SRC"
  git clone --depth 1 -b master "$LLAMA_REPO_URL" "$LLAMA_SRC"
else
  git -C "$LLAMA_SRC" remote set-url origin "$LLAMA_REPO_URL" || true
fi

git -C "$LLAMA_SRC" fetch --depth 1 origin master || git -C "$LLAMA_SRC" fetch --depth 1 origin

TARGET_REF="$LLAMA_REF"
if ! git -C "$LLAMA_SRC" show-ref --verify --quiet "refs/remotes/${TARGET_REF}"; then
  if git -C "$LLAMA_SRC" show-ref --verify --quiet "refs/remotes/origin/main"; then
    TARGET_REF="origin/main"
  elif git -C "$LLAMA_SRC" show-ref --verify --quiet "refs/remotes/origin/master"; then
    TARGET_REF="origin/master"
  else
    TARGET_REF="$(git -C "$LLAMA_SRC" for-each-ref --format='%(refname:short)' refs/remotes/origin | head -n 1)"
  fi
fi

if [ -z "$TARGET_REF" ]; then
  echo "Unable to resolve a remote ref for llama.cpp"
  exit 1
fi

git -C "$LLAMA_SRC" reset --hard "$TARGET_REF"

echo "Prepared llama.cpp source at: $LLAMA_SRC ($TARGET_REF)"
