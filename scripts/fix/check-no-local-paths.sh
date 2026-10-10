#!/usr/bin/env bash
set -euo pipefail

# Guard against machine-specific/invalid markdown paths.
# Supports running from either inference-aqli root or monorepo root.

if [ -d "./docs" ] && [ -d "./ui" ]; then
  REPO_ROOT="."
elif [ -d "./inference-aqli/docs" ] && [ -d "./inference-aqli/ui" ]; then
  REPO_ROOT="./inference-aqli"
else
  echo "Unable to locate inference-aqli root."
  exit 1
fi

PATTERN='/Users/|file://~|file:///Users/|file:////inference-aqli/|//inference-aqli/'
FILES_LIST="$(mktemp)"
trap 'rm -f "$FILES_LIST"' EXIT

find "$REPO_ROOT" \
  \( -path '*/target/*' -o -path '*/build/*' -o -path '*/src/test/*' \) -prune \
  -o -type f \( -name '*.md' -o -name '*.markdown' \) -print > "$FILES_LIST"

if [ ! -s "$FILES_LIST" ]; then
  echo "No markdown files found."
  exit 0
fi

if xargs rg -n "$PATTERN" < "$FILES_LIST"; then
  echo
  echo "Found forbidden local/invalid path patterns in markdown."
  echo "Disallowed: /Users/, file://~, file:///Users/, file:////inference-aqli/, //inference-aqli/"
  exit 1
fi

echo "Markdown path hygiene check passed."
