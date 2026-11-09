#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/../aqli-runner-gguf-llamacpp/scripts/build-llama-cpp.sh" "$@"
