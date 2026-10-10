#!/bin/bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/install-local-macos.sh"

if [[ ! -x "$SCRIPT_PATH" ]]; then
  echo "Missing $SCRIPT_PATH"
  echo "Run ./create-builder-script.sh to generate it."
  exit 1
fi

exec "$SCRIPT_PATH" "$@"
