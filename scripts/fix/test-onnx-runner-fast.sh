#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: test-onnx-runner-fast.sh [options]

Run the focused ONNX runner unit-test gate with isolated Gradle cache,
structured artifacts, Gradle busy diagnostics, and a hard timeout. This is
intended for fast runner correctness/performance-loop checks, not live model
downloads or Hugging Face integration tests.

Options:
  --summary-dir DIR       Directory for artifacts (default: temp dir)
  --project-cache-dir DIR Gradle project cache directory (default: summary-dir/gradle-project-cache)
  --gradle-bin PATH       Gradle executable (default: ./gradlew)
  --task PATH             Gradle test task (default: :runner:onnx:aqli-runner-onnx:test)
  --tests PATTERN         Add/replace a --tests pattern; repeatable
  --no-default-tests      Do not include the default focused ONNX tests
  --all-onnx-tests        Run the ONNX test task without --tests filters
  --timeout-seconds N     Kill the Gradle command after N seconds (default: 180)
  --skip-busy-check       Skip preflight check for already-running Gradle jobs
  --help                  Show this help

Examples:
  ./aqli/scripts/test-onnx-runner-fast.sh
  ./aqli/scripts/test-onnx-runner-fast.sh --tests tech.kayys.aqli.onnx.runner.OnnxStopTokensTest
USAGE
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SUMMARY_DIR=""
PROJECT_CACHE_DIR=""
GRADLE_BIN="${ROOT_DIR}/gradlew"
TASK=":runner:onnx:aqli-runner-onnx:test"
TIMEOUT_SECONDS="${AQLI_ONNX_TEST_TIMEOUT_SECONDS:-180}"
SKIP_BUSY_CHECK=0
INCLUDE_DEFAULT_TESTS=1
ALL_ONNX_TESTS=0
TEST_PATTERN_COUNT=0
declare -a TEST_PATTERNS=()

DEFAULT_TEST_PATTERNS=(
  "tech.kayys.aqli.onnx.runner.OnnxRuntimeRunnerDeviceSelectionTest"
  "tech.kayys.aqli.onnx.binding.OnnxRuntimeBindingPointerBufferTest"
  "tech.kayys.aqli.onnx.runner.OnnxTokenHistoryTest"
  "tech.kayys.aqli.onnx.runner.OnnxInferenceProfileTest"
  "tech.kayys.aqli.onnx.runner.OnnxAttentionMaskScratchTest"
  "tech.kayys.aqli.onnx.runner.OnnxPositionIdsScratchTest"
  "tech.kayys.aqli.onnx.runner.OnnxInputIdsScratchTest"
  "tech.kayys.aqli.onnx.runner.OnnxGeneratedTokensTest"
  "tech.kayys.aqli.onnx.runner.OnnxStopTokensTest"
)

while [[ $# -gt 0 ]]; do
  case "$1" in
    --summary-dir) SUMMARY_DIR="$2"; shift 2 ;;
    --summary-dir=*) SUMMARY_DIR="${1#*=}"; shift ;;
    --project-cache-dir) PROJECT_CACHE_DIR="$2"; shift 2 ;;
    --project-cache-dir=*) PROJECT_CACHE_DIR="${1#*=}"; shift ;;
    --gradle-bin) GRADLE_BIN="$2"; shift 2 ;;
    --gradle-bin=*) GRADLE_BIN="${1#*=}"; shift ;;
    --task) TASK="$2"; shift 2 ;;
    --task=*) TASK="${1#*=}"; shift ;;
    --tests) TEST_PATTERNS+=("$2"); TEST_PATTERN_COUNT=$((TEST_PATTERN_COUNT + 1)); shift 2 ;;
    --tests=*) TEST_PATTERNS+=("${1#*=}"); TEST_PATTERN_COUNT=$((TEST_PATTERN_COUNT + 1)); shift ;;
    --no-default-tests) INCLUDE_DEFAULT_TESTS=0; shift ;;
    --all-onnx-tests) ALL_ONNX_TESTS=1; INCLUDE_DEFAULT_TESTS=0; TEST_PATTERNS=(); TEST_PATTERN_COUNT=0; shift ;;
    --timeout-seconds) TIMEOUT_SECONDS="$2"; shift 2 ;;
    --timeout-seconds=*) TIMEOUT_SECONDS="${1#*=}"; shift ;;
    --skip-busy-check) SKIP_BUSY_CHECK=1; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ ! "$TIMEOUT_SECONDS" =~ ^[0-9]+$ ]] || (( TIMEOUT_SECONDS < 1 )); then
  echo "Invalid --timeout-seconds value: $TIMEOUT_SECONDS" >&2
  exit 2
fi
if [[ ! -x "$GRADLE_BIN" ]]; then
  echo "Gradle executable not found or not executable: $GRADLE_BIN" >&2
  exit 2
fi

for cmd in awk date mkdir mktemp ps sleep; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

if [[ -z "$SUMMARY_DIR" ]]; then
  SUMMARY_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-onnx-runner-fast.XXXXXX")"
else
  mkdir -p "$SUMMARY_DIR"
fi
if [[ -z "$PROJECT_CACHE_DIR" ]]; then
  PROJECT_CACHE_DIR="${SUMMARY_DIR}/gradle-project-cache"
fi
mkdir -p "$PROJECT_CACHE_DIR"

CONFIG="${SUMMARY_DIR}/config.tsv"
RESULTS="${SUMMARY_DIR}/results.tsv"
ARGV="${SUMMARY_DIR}/argv.tsv"
STDOUT_LOG="${SUMMARY_DIR}/stdout.log"
STDERR_LOG="${SUMMARY_DIR}/stderr.log"
PROCESS_REPORT="${SUMMARY_DIR}/gradle-processes.tsv"
LOCK_REPORT="${SUMMARY_DIR}/locks.tsv"
REPORT="${SUMMARY_DIR}/report.txt"

safe_tsv_field() {
  local value="${1:-}"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

config_row() {
  printf '%s\t%s\n' "$(safe_tsv_field "$1")" "$(safe_tsv_field "${2:-}")" >> "$CONFIG"
}

write_argv_log() {
  local index=0
  printf 'index\targ\n' > "$ARGV"
  while [[ $# -gt 0 ]]; do
    printf '%s\t%s\n' "$index" "$(safe_tsv_field "$1")" >> "$ARGV"
    index=$((index + 1))
    shift
  done
}

write_gradle_process_report() {
  printf 'pid\tppid\tcommand\n' > "$PROCESS_REPORT"
  if [[ "$SKIP_BUSY_CHECK" -eq 1 ]]; then
    return 0
  fi
  local ps_output ps_status
  set +e
  ps_output="$(ps -axo pid=,ppid=,command= 2>&1)"
  ps_status=$?
  set -e
  if [[ "$ps_status" -ne 0 ]]; then
    printf 'ps unavailable: %s\n' "$(safe_tsv_field "$ps_output")" >&2
    return 0
  fi
  printf '%s\n' "$ps_output" \
    | awk -v root="$ROOT_DIR" '
        {
          pid = $1
          ppid = $2
          $1 = ""
          $2 = ""
          sub(/^[[:space:]]+/, "")
          command = $0
          if (index(command, root) == 0) {
            next
          }
          if (command ~ /[g]radlew|[o]rg.gradle|[G]radleDaemon|[G]radleWorkerMain|[g]radle-launcher/) {
            print pid "\t" ppid "\t" command
          }
        }
      ' >> "$PROCESS_REPORT"
}

gradle_busy() {
  awk 'NR > 1 { found = 1 } END { exit found ? 0 : 1 }' "$PROCESS_REPORT"
}

write_lock_report() {
  local lock_path="${ROOT_DIR}/.gradle/noVersion/buildLogic.lock"
  local lock_exists="false"
  local busy="false"
  [[ -e "$lock_path" ]] && lock_exists="true"
  gradle_busy && busy="true"
  {
    printf 'kind\tpath\texists\tbusy\tprocessReport\n'
    printf 'buildLogic\t%s\t%s\t%s\t%s\n' "$lock_path" "$lock_exists" "$busy" "$PROCESS_REPORT"
  } > "$LOCK_REPORT"
}

kill_process_tree() {
  local pid="$1"
  local signal="$2"
  local children child
  set +e
  children="$(ps -axo pid=,ppid= 2>/dev/null | awk -v parent="$pid" '$2 == parent { print $1 }')"
  set -e
  for child in $children; do
    kill_process_tree "$child" "$signal"
  done
  kill "-$signal" "$pid" >/dev/null 2>&1 || true
}

run_with_timeout() {
  local start_seconds="$SECONDS"
  local elapsed_seconds=0
  local exit_code=0
  local timed_out=0
  "$@" >"$STDOUT_LOG" 2>"$STDERR_LOG" &
  local child_pid=$!

  while kill -0 "$child_pid" >/dev/null 2>&1; do
    elapsed_seconds=$((SECONDS - start_seconds))
    if (( elapsed_seconds >= TIMEOUT_SECONDS )); then
      timed_out=1
      {
        echo
        echo "Timed out after ${TIMEOUT_SECONDS}s; terminating Gradle process tree rooted at ${child_pid}."
      } >> "$STDERR_LOG"
      kill_process_tree "$child_pid" TERM
      sleep 2
      kill_process_tree "$child_pid" KILL
      break
    fi
    sleep 1
  done

  set +e
  wait "$child_pid"
  exit_code=$?
  set -e

  if [[ "$timed_out" -eq 1 ]]; then
    exit_code=124
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$([[ "$exit_code" -eq 0 ]] && printf pass || printf fail)" \
    "$exit_code" \
    "$(( (SECONDS - start_seconds) * 1000 ))" \
    "$STDOUT_LOG" \
    "$STDERR_LOG" \
    "$([[ "$timed_out" -eq 1 ]] && printf timeout || printf '')" >> "$RESULTS"
  return "$exit_code"
}

declare -a GRADLE_ARGS=("$GRADLE_BIN" "--no-daemon" "--no-parallel" "--max-workers=1" "--project-cache-dir" "$PROJECT_CACHE_DIR" "$TASK")
if [[ "$ALL_ONNX_TESTS" -eq 0 ]]; then
  if [[ "$INCLUDE_DEFAULT_TESTS" -eq 1 ]]; then
    if (( TEST_PATTERN_COUNT > 0 )); then
      TEST_PATTERNS=("${DEFAULT_TEST_PATTERNS[@]}" "${TEST_PATTERNS[@]}")
    else
      TEST_PATTERNS=("${DEFAULT_TEST_PATTERNS[@]}")
    fi
    TEST_PATTERN_COUNT="${#TEST_PATTERNS[@]}"
  fi
  if [[ "$TEST_PATTERN_COUNT" -eq 0 ]]; then
    echo "No test patterns selected; use --all-onnx-tests or add --tests PATTERN" >&2
    exit 2
  fi
  for pattern in "${TEST_PATTERNS[@]}"; do
    GRADLE_ARGS+=("--tests" "$pattern")
  done
fi

{
  printf 'key\tvalue\n'
} > "$CONFIG"
config_row generatedAt "$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
config_row rootDir "$ROOT_DIR"
config_row gradleBin "$GRADLE_BIN"
config_row task "$TASK"
config_row summaryDir "$SUMMARY_DIR"
config_row projectCacheDir "$PROJECT_CACHE_DIR"
config_row timeoutSeconds "$TIMEOUT_SECONDS"
config_row skipBusyCheck "$SKIP_BUSY_CHECK"
config_row allOnnxTests "$ALL_ONNX_TESTS"
if (( TEST_PATTERN_COUNT > 0 )); then
  for pattern in "${TEST_PATTERNS[@]}"; do
    config_row test "$pattern"
  done
fi

printf 'status\texitCode\telapsedMs\tstdout\tstderr\treason\n' > "$RESULTS"
write_argv_log "${GRADLE_ARGS[@]}"
write_gradle_process_report
write_lock_report

if [[ "$SKIP_BUSY_CHECK" -eq 0 ]] && gradle_busy; then
  printf 'busy\t73\t0\t%s\t%s\tgradle-process-running\n' "$STDOUT_LOG" "$STDERR_LOG" >> "$RESULTS"
  {
    echo "Gradle is already active for this Aqli checkout; refusing to start another focused ONNX test run."
    echo "summaryDir=$SUMMARY_DIR"
    echo "artifacts.config=$CONFIG"
    echo "artifacts.results=$RESULTS"
    echo "artifacts.argv=$ARGV"
    echo "artifacts.processes=$PROCESS_REPORT"
    echo "artifacts.locks=$LOCK_REPORT"
  } | tee "$REPORT" >&2
  exit 73
fi

FINAL_EXIT=0
run_with_timeout "${GRADLE_ARGS[@]}" || FINAL_EXIT=$?

{
  echo "Aqli ONNX runner fast test gate"
  echo "summaryDir=$SUMMARY_DIR"
  echo "artifacts.config=$CONFIG"
  echo "artifacts.results=$RESULTS"
  echo "artifacts.argv=$ARGV"
  echo "artifacts.stdout=$STDOUT_LOG"
  echo "artifacts.stderr=$STDERR_LOG"
  echo "artifacts.processes=$PROCESS_REPORT"
  echo "artifacts.locks=$LOCK_REPORT"
  echo
  awk 'BEGIN { FS = "\t" } NR == 1 { next } { printf "status=%s exitCode=%s elapsedMs=%s reason=%s\n", $1, $2, $3, $6 }' "$RESULTS"
} | tee "$REPORT"

exit "$FINAL_EXIT"
