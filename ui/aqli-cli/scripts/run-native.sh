#!/usr/bin/env bash
set -euo pipefail

BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/target"
BIN="$BIN_DIR/aqli"

if [[ ! -x "$BIN" ]]; then
  echo "aqli native binary not found at $BIN" >&2
  echo "Build it with: mvn -f inference-aqli/ui/aqli-cli/pom.xml -Pnative -DskipTests clean package" >&2
  exit 1
fi

# Avoid deprecated console env keys leaking into native runtime.
unset QUARKUS_LOG_CONSOLE_JSON
unset QUARKUS_LOG_CONSOLE_ENABLE

# Default to disabling file logging in sandboxed runs.
: "${AQLI_CLI_FILE_LOG_ENABLED:=false}"
export AQLI_CLI_FILE_LOG_ENABLED

exec "$BIN" "$@"
