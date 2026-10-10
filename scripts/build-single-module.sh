#!/usr/bin/env bash
set -euo pipefail
if [ "$#" -ne 1 ]; then
  echo "Usage: $0 :path:to:module  (e.g. :core:aqli-core)"
  exit 2
fi
MODULE="$1"
REPO_ROOT="$(pwd)"
# compute relative path from module path
REL="${MODULE//://}"
REL="${REL#/}"
ABS_PATH="$REPO_ROOT/$REL"
TMP_SETTINGS="/tmp/aqli-single-settings-$(date +%s).gradle.kts"
cat > "$TMP_SETTINGS" <<EOF
rootProject.name = "aqli-single"
include("$MODULE")
project("$MODULE").projectDir = file("$ABS_PATH")
EOF
echo "Using temp settings: $TMP_SETTINGS"
./gradlew "$MODULE:build" --no-daemon --settings-file "$TMP_SETTINGS"
RC=$?
rm -f "$TMP_SETTINGS"
exit $RC
