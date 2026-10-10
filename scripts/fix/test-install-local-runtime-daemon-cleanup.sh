#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-install-cleanup.XXXXXX")"
FAKE_DAEMON_PID=""
cleanup() {
    if [ -n "$FAKE_DAEMON_PID" ] && kill -0 "$FAKE_DAEMON_PID" >/dev/null 2>&1; then
        kill "$FAKE_DAEMON_PID" >/dev/null 2>&1 || true
    fi
    rm -rf "$TMP_DIR"
}
trap cleanup EXIT

export HOME="$TMP_DIR/home"
export AQLI_BIN_DIR="$TMP_DIR/bin"
export AQLI_LIB_DIR="$TMP_DIR/lib"
export AQLI_RUNTIME_DIR="$TMP_DIR/runtime"

mkdir -p "$HOME/.aqli/run" "$AQLI_BIN_DIR"

FAKE_LOG="$TMP_DIR/fake-aqli.log"
FAKE_DAEMON_TERM_MARKER="$TMP_DIR/fake-daemon-terminated"
cat > "$AQLI_BIN_DIR/aqli" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$FAKE_LOG"
exit 0
SH
chmod +x "$AQLI_BIN_DIR/aqli"
export FAKE_LOG
export FAKE_DAEMON_TERM_MARKER

printf '12345\n999999\ntest-key\n' > "$HOME/.aqli/run/gguf-fast-daemon.port"
printf '12346\n999999\ntest-key\n' > "$HOME/.aqli/run/litert-fast-daemon.port"

if ps -p $$ -o command= >/dev/null 2>&1; then
    bash -c 'trap "printf terminated > \"$FAKE_DAEMON_TERM_MARKER\"; exit 0" TERM; while :; do sleep 1; done' \
        "tech.kayys.aqli.cli.commands.GgufFastRun" "__daemon" &
    FAKE_DAEMON_PID="$!"
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        if ps -p "$FAKE_DAEMON_PID" -o command= 2>/dev/null \
            | grep -q 'tech.kayys.aqli.cli.commands.GgufFastRun.* __daemon'; then
            break
        fi
        sleep 0.1
    done
fi

bash "$ROOT_DIR/scripts/install-local-runtime.sh" --stop-daemons-only >/dev/null

if ! grep -qx '__gguf-daemon-stop' "$FAKE_LOG"; then
    echo "Expected installer cleanup to call GGUF daemon stop" >&2
    exit 1
fi
if ! grep -qx '__daemon-stop' "$FAKE_LOG"; then
    echo "Expected installer cleanup to call LiteRT daemon stop" >&2
    exit 1
fi
if [ -e "$HOME/.aqli/run/gguf-fast-daemon.port" ]; then
    echo "Expected GGUF daemon port file to be removed" >&2
    exit 1
fi
if [ -e "$HOME/.aqli/run/litert-fast-daemon.port" ]; then
    echo "Expected LiteRT daemon port file to be removed" >&2
    exit 1
fi
if [ -n "$FAKE_DAEMON_PID" ]; then
    for _ in 1 2 3 4 5 6 7 8 9 10; do
        if [ -f "$FAKE_DAEMON_TERM_MARKER" ]; then
            break
        fi
        sleep 0.1
    done
    if [ ! -f "$FAKE_DAEMON_TERM_MARKER" ]; then
        echo "Expected orphan GGUF daemon process to be terminated" >&2
        exit 1
    fi
    wait "$FAKE_DAEMON_PID" >/dev/null 2>&1 || true
    FAKE_DAEMON_PID=""
fi

echo "install-local-runtime daemon cleanup test passed"
