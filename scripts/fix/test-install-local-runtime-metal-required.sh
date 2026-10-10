#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [ "$(uname -s)" != "Darwin" ]; then
    printf 'install-local-runtime metal-required test skipped on non-Darwin\n'
    exit 0
fi

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-install-metal-required.XXXXXX")"
cleanup() {
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

FAKE_BIN="$TMP_DIR/bin"
mkdir -p "$FAKE_BIN" "$TMP_DIR/home" "$TMP_DIR/aqli-bin" "$TMP_DIR/libs" "$TMP_DIR/runtime"

cat > "$FAKE_BIN/make" <<'SH'
#!/usr/bin/env bash
printf 'fake make invoked: %s\n' "$*" >> "$AQLI_FAKE_MAKE_LOG"
exit 42
SH
chmod +x "$FAKE_BIN/make"

OUTPUT="$TMP_DIR/output.log"
export AQLI_FAKE_MAKE_LOG="$TMP_DIR/make.log"

set +e
HOME="$TMP_DIR/home" \
PATH="$FAKE_BIN:$PATH" \
AQLI_BIN_DIR="$TMP_DIR/aqli-bin" \
AQLI_LIB_DIR="$TMP_DIR/libs" \
AQLI_RUNTIME_DIR="$TMP_DIR/runtime" \
AQLI_SKIP_BUILD=true \
    bash "$ROOT_DIR/scripts/install-local-runtime.sh" >"$OUTPUT" 2>&1
status=$?
set -e

if [ "$status" -eq 0 ]; then
    echo "Expected install-local-runtime to fail when Metal bridge make fails" >&2
    cat "$OUTPUT" >&2
    exit 1
fi
if ! grep -q 'Metal native bridge build/install failed' "$OUTPUT"; then
    echo "Expected fatal Metal build failure message" >&2
    cat "$OUTPUT" >&2
    exit 1
fi
if ! grep -q 'AQLI_ALLOW_METAL_BUILD_FAILURE=true' "$OUTPUT"; then
    echo "Expected diagnostic escape-hatch guidance" >&2
    cat "$OUTPUT" >&2
    exit 1
fi
if ! grep -q 'fake make invoked: -C .*/backend/metal/aqli-backend-metal/src/main/cpp/metal install INSTALL_DIR=' "$AQLI_FAKE_MAKE_LOG"; then
    echo "Expected installer to invoke Metal Makefile through make -C" >&2
    cat "$AQLI_FAKE_MAKE_LOG" >&2
    exit 1
fi

printf 'install-local-runtime metal-required test passed\n'
