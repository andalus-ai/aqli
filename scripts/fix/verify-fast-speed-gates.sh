#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: verify-fast-speed-gates.sh [options]

Run the installed fast inference speed gates and collect TSV summaries, a
normalized rollup, backend proof and contract reports, ranked slowest-case
report, safetensor stage diagnosis, configuration, command argv, stdout, and
stderr in one directory. This wraps the LiteRT fast gate, Gemma4 LiteRT warm
daemon gate, GGUF fast gate, GGUF Java-native-vs-llama.cpp comparison gate, and
safetensor profile/Metal speed gate.

Options:
  --only MODE        auto|all|both|litert|gemma4-litert|gguf|gguf-compare|safetensor
                     (default: all)
  --summary-dir DIR  Directory for TSV summary artifacts (default: temp dir)
  --aqli-bin PATH  Aqli executable (default: ~/.local/bin/aqli)
  --litert-model ID  LiteRT model id or alias (default: 7c51c9)
  --gemma4-litert-model ID
                     Gemma4 LiteRT model id or alias (default: 0576e9)
  --gemma4-litert-route-summary PATH
                     Verify Gemma4 LiteRT route contracts from an existing TSV
                     summary instead of running generation
  --gemma4-litert-route-baseline-stats PATH
                     Compare Gemma4 LiteRT route-stats.tsv against a baseline
  --gemma4-litert-route-max-regression-ms N
                     Fail when avg route resolution regresses by more than N ms
  --gemma4-litert-route-max-regression-percent N
                     Fail when avg route resolution regresses by more than N percent
  --gguf-model ID    GGUF model id or alias (default: b71c9d)
  --safetensor-model ID
                     Safetensor model id or alias (default: 6f469a)
  --prompt TEXT      Prompt shared by all gates (default: "where is jakarta")
  --litert-expected REGEX
                     Expected LiteRT answer regex (default: Jakarta|Indonesia)
  --gemma4-litert-expected REGEX
                     Expected Gemma4 LiteRT answer regex (default: Jakarta|Indonesia)
  --gguf-expected REGEX
                     Expected GGUF answer regex (default: Indonesia|Jakarta)
  --require-metal    Require Metal backend (default on macOS)
  --no-require-metal Do not require Metal backend
  --warm-only        Require already-warm LiteRT/GGUF daemons and skip cold runs
  --keep-daemon      Leave LiteRT/GGUF daemons running after checks
  --slowest-limit N  Number of slowest rows to print and write (default: 5)
  --safetensor-baseline-stages PATH
                     Compare current safetensor diagnosis stages against a baseline
                     Use latest|auto to resolve baseline-root/name/latest-stages.tsv
  --safetensor-baseline-root DIR
                     Safetensor baseline root for latest/auto resolution
  --safetensor-baseline-name NAME
                     Safetensor baseline name for latest/auto resolution
  --safetensor-max-regression-percent N
                     Fail safetensor comparison when a stage regresses by more than N percent
  --safetensor-max-regression-ms N
                     Fail safetensor comparison when a stage regresses by more than N ms
  --safetensor-min-baseline-ms N
                     Ignore percent regression below this baseline value (default: 1)
  --safetensor-fail-primary-shift
                     Fail when the diagnosed safetensor primary stage changes
  --safetensor-require-metal-paths
                     Require safetensor profile path evidence to prove Metal/GPU kernels
  --safetensor-no-require-metal-paths
                     Do not require safetensor Metal/GPU path evidence
  --safetensor-reject-fallback-paths
                     Reject safetensor profile path evidence containing CPU/Java/fallback markers
  --safetensor-allow-fallback-paths
                     Allow fallback path markers for diagnostics
  --safetensor-preset NAME
                     Apply safetensor benchmark defaults (example: m4-smoke)
  --list-safetensor-presets
                     Print available safetensor benchmark presets and exit
  --continue-on-failure
                     Run remaining selected gates after a failure, then exit non-zero
  --help             Show this help

Environment overrides:
  AQLI_VERIFY_FAST_{LITERT,GGUF,GGUF_COMPARE}_BENCH
  AQLI_VERIFY_FAST_GEMMA4_LITERT_SMOKE
  AQLI_VERIFY_FAST_SAFETENSOR_BENCH
  AQLI_VERIFY_LITERT_{MAX_TOKENS,WARM_THRESHOLD_MS,COLD_THRESHOLD_MS,WARMUP_RUNS}
  AQLI_VERIFY_LITERT_WARM_ONLY=true
  AQLI_VERIFY_LITERT_WARM_{ENGINE_INIT,FIRST_CHUNK,TOTAL}_THRESHOLD_MS
  AQLI_VERIFY_GEMMA4_LITERT_{MAX_TOKENS,WARM_THRESHOLD_MS}
  AQLI_VERIFY_GEMMA4_LITERT_WARM_{FIRST_CHUNK,TOTAL}_THRESHOLD_MS
  AQLI_VERIFY_GEMMA4_LITERT_ROUTE_SOURCE=fast-index-equivalent-litert
    Empty disables the Gemma4 LiteRT route-source contract.
  AQLI_VERIFY_GEMMA4_LITERT_ROUTE_RESOLVE_THRESHOLD_MS=250
    Empty disables the Gemma4 LiteRT warm route-resolution budget contract.
  AQLI_VERIFY_GEMMA4_LITERT_COLD_ROUTE_RESOLVE_THRESHOLD_MS=1000
    Empty falls back to the warm Gemma4 LiteRT route-resolution budget.
  AQLI_VERIFY_GEMMA4_LITERT_ROUTE_ONLY=true
    Verify Gemma4 LiteRT route contracts from a prebuilt summary TSV.
  AQLI_VERIFY_GEMMA4_LITERT_ROUTE_SUMMARY_FILE
    Summary TSV used when Gemma4 LiteRT route-only verification is enabled.
  AQLI_VERIFY_GGUF_{MAX_TOKENS,WARM_THRESHOLD_MS,COLD_THRESHOLD_MS,WARMUP_RUNS}
  AQLI_VERIFY_GGUF_WARM_ONLY=true
  AQLI_VERIFY_GGUF_WARM_{TOKENIZE,PREFILL,DECODE}_THRESHOLD_MS
  AQLI_VERIFY_GGUF_COMPARE_{MAX_TOKENS,THRESHOLD_MS,JAVA_MATVEC_THRESHOLD_MS}
  AQLI_VERIFY_GGUF_COMPARE_FALLBACK_{PREFILL,DECODE}_THRESHOLD_MS
  AQLI_VERIFY_GGUF_COMPARE_JAVA_{READY,CONFIG,PROBE}_REGEX
  AQLI_VERIFY_GGUF_COMPARE_JAVA_REFUSAL=false
  AQLI_VERIFY_SAFETENSOR_{MAX_TOKENS,MIN_SPEED_TPS,TOP_STAGE_THRESHOLD_MS}
  AQLI_VERIFY_SAFETENSOR_{PREFILL,DECODE,TPOT,SAMPLING,ARGMAX}_THRESHOLD_MS
  AQLI_VERIFY_SAFETENSOR_{ATTENTION,FFN,LOGITS}_THRESHOLD_MS
  AQLI_VERIFY_SAFETENSOR_MIN_{CORE,LINEAR,LOGITS,FFN,ATTENTION,ARGMAX}_METAL_COVERAGE
  AQLI_VERIFY_SAFETENSOR_BASELINE_STAGES
  AQLI_VERIFY_SAFETENSOR_{BASELINE_ROOT,BASELINE_NAME}
  AQLI_VERIFY_SAFETENSOR_{MAX_REGRESSION_PERCENT,MAX_REGRESSION_MS,MIN_BASELINE_MS}
  AQLI_VERIFY_SAFETENSOR_FAIL_PRIMARY_SHIFT=true
  AQLI_VERIFY_SAFETENSOR_REQUIRE_PROFILE=false
  AQLI_VERIFY_SAFETENSOR_REQUIRE_METAL_PATHS=false
    Safetensor Metal path proof defaults true on macOS, false elsewhere.
  AQLI_VERIFY_SAFETENSOR_REJECT_FALLBACK_PATHS=false
  AQLI_VERIFY_SAFETENSOR_PRESET=m4-smoke
  AQLI_VERIFY_SAFETENSOR_PRESET_SCRIPT
  AQLI_VERIFY_SLOWEST_LIMIT=5
  AQLI_VERIFY_CONTINUE_ON_FAILURE=true

Examples:
  Verify Gemma4 LiteRT route contracts from a captured route summary without
  running generation:
    verify-fast-speed-gates.sh --only gemma4-litert \
      --gemma4-litert-route-summary /tmp/gemma4-route-summary.tsv

  Use auto mode to run installed gates plus Gemma4 route-summary contracts even
  when the Gemma4 model is not installed locally:
    verify-fast-speed-gates.sh --only auto \
      --gemma4-litert-route-summary /tmp/gemma4-route-summary.tsv

  Disable only the Gemma4 route-source contract while keeping route timing
  budgets active:
    AQLI_VERIFY_GEMMA4_LITERT_ROUTE_SOURCE= \
      verify-fast-speed-gates.sh --only gemma4-litert \
      --gemma4-litert-route-summary /tmp/gemma4-route-summary.tsv

  Gemma4 route-summary TSVs must include these columns, with log as the last
  column:
    case durationMs backend routeResolveMs routeCacheHit selectedArtifact routeSource log
USAGE
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ONLY="all"
SUMMARY_DIR=""
AQLI_BIN="${HOME}/.local/bin/aqli"
LITERT_MODEL="7c51c9"
GEMMA4_LITERT_MODEL="${AQLI_VERIFY_GEMMA4_LITERT_MODEL:-0576e9}"
GGUF_MODEL="b71c9d"
SAFETENSOR_MODEL="${AQLI_VERIFY_SAFETENSOR_MODEL:-6f469a}"
PROMPT="${AQLI_VERIFY_PROMPT:-where is jakarta}"
LITERT_EXPECTED="${AQLI_VERIFY_LITERT_EXPECTED:-Jakarta|Indonesia}"
GEMMA4_LITERT_EXPECTED="${AQLI_VERIFY_GEMMA4_LITERT_EXPECTED:-Jakarta|Indonesia}"
GEMMA4_LITERT_ROUTE_SOURCE="${AQLI_VERIFY_GEMMA4_LITERT_ROUTE_SOURCE-fast-index-equivalent-litert}"
GEMMA4_LITERT_ROUTE_RESOLVE_THRESHOLD_MS="${AQLI_VERIFY_GEMMA4_LITERT_ROUTE_RESOLVE_THRESHOLD_MS:-250}"
GEMMA4_LITERT_COLD_ROUTE_RESOLVE_THRESHOLD_MS="${AQLI_VERIFY_GEMMA4_LITERT_COLD_ROUTE_RESOLVE_THRESHOLD_MS:-1000}"
GEMMA4_LITERT_ROUTE_ONLY="${AQLI_VERIFY_GEMMA4_LITERT_ROUTE_ONLY:-false}"
GEMMA4_LITERT_ROUTE_SUMMARY_FILE="${AQLI_VERIFY_GEMMA4_LITERT_ROUTE_SUMMARY_FILE:-}"
GEMMA4_LITERT_ROUTE_BASELINE_STATS="${AQLI_VERIFY_GEMMA4_LITERT_ROUTE_BASELINE_STATS:-}"
GEMMA4_LITERT_ROUTE_MAX_REGRESSION_MS="${AQLI_VERIFY_GEMMA4_LITERT_ROUTE_MAX_REGRESSION_MS:-}"
GEMMA4_LITERT_ROUTE_MAX_REGRESSION_PERCENT="${AQLI_VERIFY_GEMMA4_LITERT_ROUTE_MAX_REGRESSION_PERCENT:-}"
GGUF_EXPECTED="${AQLI_VERIFY_GGUF_EXPECTED:-Indonesia|Jakarta}"
KEEP_DAEMON=0
WARM_ONLY=0
SLOWEST_LIMIT="${AQLI_VERIFY_SLOWEST_LIMIT:-5}"
CONTINUE_ON_FAILURE="${AQLI_VERIFY_CONTINUE_ON_FAILURE:-false}"
SAFETENSOR_BASELINE_STAGES="${AQLI_VERIFY_SAFETENSOR_BASELINE_STAGES:-}"
SAFETENSOR_BASELINE_STAGES_REQUEST="$SAFETENSOR_BASELINE_STAGES"
SAFETENSOR_BASELINE_ROOT="${AQLI_VERIFY_SAFETENSOR_BASELINE_ROOT:-${ROOT_DIR}/ops/benchmarks/safetensor/baselines}"
SAFETENSOR_BASELINE_NAME="${AQLI_VERIFY_SAFETENSOR_BASELINE_NAME:-}"
SAFETENSOR_MAX_REGRESSION_PERCENT="${AQLI_VERIFY_SAFETENSOR_MAX_REGRESSION_PERCENT:-}"
SAFETENSOR_MAX_REGRESSION_MS="${AQLI_VERIFY_SAFETENSOR_MAX_REGRESSION_MS:-}"
SAFETENSOR_MIN_BASELINE_MS="${AQLI_VERIFY_SAFETENSOR_MIN_BASELINE_MS:-1}"
SAFETENSOR_FAIL_PRIMARY_SHIFT="${AQLI_VERIFY_SAFETENSOR_FAIL_PRIMARY_SHIFT:-false}"
SAFETENSOR_PRESET="${AQLI_VERIFY_SAFETENSOR_PRESET:-}"
SAFETENSOR_PRESET_SCRIPT="${AQLI_VERIFY_SAFETENSOR_PRESET_SCRIPT:-${ROOT_DIR}/scripts/safetensor-performance-presets.sh}"
SAFETENSOR_REJECT_FALLBACK_PATHS="${AQLI_VERIFY_SAFETENSOR_REJECT_FALLBACK_PATHS:-false}"
SAFETENSOR_MIN_SPEED_TPS="${AQLI_VERIFY_SAFETENSOR_MIN_SPEED_TPS:-}"
SAFETENSOR_MIN_CORE_METAL_COVERAGE="${AQLI_VERIFY_SAFETENSOR_MIN_CORE_METAL_COVERAGE:-}"
SAFETENSOR_MIN_LINEAR_METAL_COVERAGE="${AQLI_VERIFY_SAFETENSOR_MIN_LINEAR_METAL_COVERAGE:-}"
SAFETENSOR_MIN_LOGITS_METAL_COVERAGE="${AQLI_VERIFY_SAFETENSOR_MIN_LOGITS_METAL_COVERAGE:-}"
SAFETENSOR_MIN_FFN_METAL_COVERAGE="${AQLI_VERIFY_SAFETENSOR_MIN_FFN_METAL_COVERAGE:-}"
SAFETENSOR_MIN_ATTENTION_METAL_COVERAGE="${AQLI_VERIFY_SAFETENSOR_MIN_ATTENTION_METAL_COVERAGE:-}"
SAFETENSOR_MIN_ARGMAX_METAL_COVERAGE="${AQLI_VERIFY_SAFETENSOR_MIN_ARGMAX_METAL_COVERAGE:-}"
LIST_SAFETENSOR_PRESETS=0
REQUIRE_METAL_EXPLICIT=0
SAFETENSOR_REQUIRE_METAL_PATHS_EXPLICIT=0
SAFETENSOR_REJECT_FALLBACK_PATHS_EXPLICIT=0
if [[ -n "${AQLI_VERIFY_SAFETENSOR_REQUIRE_METAL_PATHS+x}" ]]; then
  SAFETENSOR_REQUIRE_METAL_PATHS_EXPLICIT=1
fi
if [[ -n "${AQLI_VERIFY_SAFETENSOR_REJECT_FALLBACK_PATHS+x}" ]]; then
  SAFETENSOR_REJECT_FALLBACK_PATHS_EXPLICIT=1
fi
case "$(uname -s)" in
  Darwin)
    REQUIRE_METAL=1
    SAFETENSOR_REQUIRE_METAL_PATHS="${AQLI_VERIFY_SAFETENSOR_REQUIRE_METAL_PATHS:-true}"
    ;;
  *)
    REQUIRE_METAL=0
    SAFETENSOR_REQUIRE_METAL_PATHS="${AQLI_VERIFY_SAFETENSOR_REQUIRE_METAL_PATHS:-false}"
    ;;
esac

while [[ $# -gt 0 ]]; do
  case "$1" in
    --only) ONLY="$2"; shift 2 ;;
    --only=*) ONLY="${1#*=}"; shift ;;
    --summary-dir) SUMMARY_DIR="$2"; shift 2 ;;
    --summary-dir=*) SUMMARY_DIR="${1#*=}"; shift ;;
    --aqli-bin) AQLI_BIN="$2"; shift 2 ;;
    --aqli-bin=*) AQLI_BIN="${1#*=}"; shift ;;
    --litert-model) LITERT_MODEL="$2"; shift 2 ;;
    --litert-model=*) LITERT_MODEL="${1#*=}"; shift ;;
    --gemma4-litert-model) GEMMA4_LITERT_MODEL="$2"; shift 2 ;;
    --gemma4-litert-model=*) GEMMA4_LITERT_MODEL="${1#*=}"; shift ;;
    --gemma4-litert-route-summary) GEMMA4_LITERT_ROUTE_SUMMARY_FILE="$2"; GEMMA4_LITERT_ROUTE_ONLY=true; shift 2 ;;
    --gemma4-litert-route-summary=*) GEMMA4_LITERT_ROUTE_SUMMARY_FILE="${1#*=}"; GEMMA4_LITERT_ROUTE_ONLY=true; shift ;;
    --gemma4-litert-route-baseline-stats) GEMMA4_LITERT_ROUTE_BASELINE_STATS="$2"; shift 2 ;;
    --gemma4-litert-route-baseline-stats=*) GEMMA4_LITERT_ROUTE_BASELINE_STATS="${1#*=}"; shift ;;
    --gemma4-litert-route-max-regression-ms) GEMMA4_LITERT_ROUTE_MAX_REGRESSION_MS="$2"; shift 2 ;;
    --gemma4-litert-route-max-regression-ms=*) GEMMA4_LITERT_ROUTE_MAX_REGRESSION_MS="${1#*=}"; shift ;;
    --gemma4-litert-route-max-regression-percent) GEMMA4_LITERT_ROUTE_MAX_REGRESSION_PERCENT="$2"; shift 2 ;;
    --gemma4-litert-route-max-regression-percent=*) GEMMA4_LITERT_ROUTE_MAX_REGRESSION_PERCENT="${1#*=}"; shift ;;
    --gguf-model) GGUF_MODEL="$2"; shift 2 ;;
    --gguf-model=*) GGUF_MODEL="${1#*=}"; shift ;;
    --safetensor-model) SAFETENSOR_MODEL="$2"; shift 2 ;;
    --safetensor-model=*) SAFETENSOR_MODEL="${1#*=}"; shift ;;
    --prompt) PROMPT="$2"; shift 2 ;;
    --prompt=*) PROMPT="${1#*=}"; shift ;;
    --litert-expected) LITERT_EXPECTED="$2"; shift 2 ;;
    --litert-expected=*) LITERT_EXPECTED="${1#*=}"; shift ;;
    --gemma4-litert-expected) GEMMA4_LITERT_EXPECTED="$2"; shift 2 ;;
    --gemma4-litert-expected=*) GEMMA4_LITERT_EXPECTED="${1#*=}"; shift ;;
    --gguf-expected) GGUF_EXPECTED="$2"; shift 2 ;;
    --gguf-expected=*) GGUF_EXPECTED="${1#*=}"; shift ;;
    --require-metal) REQUIRE_METAL=1; REQUIRE_METAL_EXPLICIT=1; shift ;;
    --no-require-metal) REQUIRE_METAL=0; REQUIRE_METAL_EXPLICIT=1; shift ;;
    --warm-only) WARM_ONLY=1; shift ;;
    --keep-daemon) KEEP_DAEMON=1; shift ;;
    --slowest-limit) SLOWEST_LIMIT="$2"; shift 2 ;;
    --slowest-limit=*) SLOWEST_LIMIT="${1#*=}"; shift ;;
    --safetensor-baseline-stages) SAFETENSOR_BASELINE_STAGES="$2"; shift 2 ;;
    --safetensor-baseline-stages=*) SAFETENSOR_BASELINE_STAGES="${1#*=}"; shift ;;
    --safetensor-baseline-root) SAFETENSOR_BASELINE_ROOT="$2"; shift 2 ;;
    --safetensor-baseline-root=*) SAFETENSOR_BASELINE_ROOT="${1#*=}"; shift ;;
    --safetensor-baseline-name) SAFETENSOR_BASELINE_NAME="$2"; shift 2 ;;
    --safetensor-baseline-name=*) SAFETENSOR_BASELINE_NAME="${1#*=}"; shift ;;
    --safetensor-max-regression-percent) SAFETENSOR_MAX_REGRESSION_PERCENT="$2"; shift 2 ;;
    --safetensor-max-regression-percent=*) SAFETENSOR_MAX_REGRESSION_PERCENT="${1#*=}"; shift ;;
    --safetensor-max-regression-ms) SAFETENSOR_MAX_REGRESSION_MS="$2"; shift 2 ;;
    --safetensor-max-regression-ms=*) SAFETENSOR_MAX_REGRESSION_MS="${1#*=}"; shift ;;
    --safetensor-min-baseline-ms) SAFETENSOR_MIN_BASELINE_MS="$2"; shift 2 ;;
    --safetensor-min-baseline-ms=*) SAFETENSOR_MIN_BASELINE_MS="${1#*=}"; shift ;;
    --safetensor-fail-primary-shift) SAFETENSOR_FAIL_PRIMARY_SHIFT=true; shift ;;
    --safetensor-require-metal-paths) SAFETENSOR_REQUIRE_METAL_PATHS=true; SAFETENSOR_REQUIRE_METAL_PATHS_EXPLICIT=1; shift ;;
    --safetensor-no-require-metal-paths) SAFETENSOR_REQUIRE_METAL_PATHS=false; SAFETENSOR_REQUIRE_METAL_PATHS_EXPLICIT=1; shift ;;
    --safetensor-reject-fallback-paths) SAFETENSOR_REJECT_FALLBACK_PATHS=true; SAFETENSOR_REJECT_FALLBACK_PATHS_EXPLICIT=1; shift ;;
    --safetensor-allow-fallback-paths) SAFETENSOR_REJECT_FALLBACK_PATHS=false; SAFETENSOR_REJECT_FALLBACK_PATHS_EXPLICIT=1; shift ;;
    --safetensor-preset) SAFETENSOR_PRESET="$2"; shift 2 ;;
    --safetensor-preset=*) SAFETENSOR_PRESET="${1#*=}"; shift ;;
    --list-safetensor-presets) LIST_SAFETENSOR_PRESETS=1; shift ;;
    --continue-on-failure) CONTINUE_ON_FAILURE=true; shift ;;
    --stop-on-failure) CONTINUE_ON_FAILURE=false; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done
SAFETENSOR_BASELINE_STAGES_REQUEST="$SAFETENSOR_BASELINE_STAGES"

case "$ONLY" in
  auto|all|both|litert|gemma4-litert|gguf|gguf-compare|safetensor) ;;
  *) echo "Unknown --only mode: $ONLY" >&2; usage; exit 2 ;;
esac
if [[ ! "$SLOWEST_LIMIT" =~ ^[0-9]+$ ]] || (( SLOWEST_LIMIT < 1 )); then
  echo "Invalid --slowest-limit value: $SLOWEST_LIMIT" >&2
  usage
  exit 2
fi
if [[ -z "$SAFETENSOR_BASELINE_ROOT" ]]; then
  echo "--safetensor-baseline-root must not be empty" >&2
  usage
  exit 2
fi
case "$GEMMA4_LITERT_ROUTE_ONLY" in
  1|true|TRUE|yes|YES|on|ON)
    if [[ -z "$GEMMA4_LITERT_ROUTE_SUMMARY_FILE" ]]; then
      echo "Gemma4 LiteRT route-only verification requires --gemma4-litert-route-summary or AQLI_VERIFY_GEMMA4_LITERT_ROUTE_SUMMARY_FILE" >&2
      usage
      exit 2
    fi
    ;;
esac
for value_name in GEMMA4_LITERT_ROUTE_RESOLVE_THRESHOLD_MS GEMMA4_LITERT_COLD_ROUTE_RESOLVE_THRESHOLD_MS GEMMA4_LITERT_ROUTE_MAX_REGRESSION_MS GEMMA4_LITERT_ROUTE_MAX_REGRESSION_PERCENT SAFETENSOR_MAX_REGRESSION_PERCENT SAFETENSOR_MAX_REGRESSION_MS SAFETENSOR_MIN_BASELINE_MS SAFETENSOR_MIN_SPEED_TPS SAFETENSOR_MIN_CORE_METAL_COVERAGE SAFETENSOR_MIN_LINEAR_METAL_COVERAGE SAFETENSOR_MIN_LOGITS_METAL_COVERAGE SAFETENSOR_MIN_FFN_METAL_COVERAGE SAFETENSOR_MIN_ATTENTION_METAL_COVERAGE SAFETENSOR_MIN_ARGMAX_METAL_COVERAGE; do
  value="${!value_name}"
  if [[ -n "$value" && ! "$value" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
    echo "Invalid ${value_name} value: $value" >&2
    usage
    exit 2
  fi
done

for cmd in awk mkdir mktemp tr; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

if (( LIST_SAFETENSOR_PRESETS == 1 )); then
  if [[ ! -f "$SAFETENSOR_PRESET_SCRIPT" ]]; then
    echo "safetensor preset script not found: $SAFETENSOR_PRESET_SCRIPT" >&2
    exit 2
  fi
  bash "$SAFETENSOR_PRESET_SCRIPT" list
  exit 0
fi

if [[ ! -x "$AQLI_BIN" ]]; then
  echo "aqli executable not found or not executable: $AQLI_BIN" >&2
  exit 2
fi

if [[ -z "$SUMMARY_DIR" ]]; then
  SUMMARY_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-fast-speed-gates.XXXXXX")"
else
  mkdir -p "$SUMMARY_DIR"
fi

LITERT_BENCH="${AQLI_VERIFY_FAST_LITERT_BENCH:-${ROOT_DIR}/scripts/bench-litert-fast-run.sh}"
GEMMA4_LITERT_SMOKE="${AQLI_VERIFY_FAST_GEMMA4_LITERT_SMOKE:-${ROOT_DIR}/scripts/smoke-gemma4-litert-warm.sh}"
GGUF_BENCH="${AQLI_VERIFY_FAST_GGUF_BENCH:-${ROOT_DIR}/scripts/bench-gguf-fast-run.sh}"
GGUF_COMPARE_BENCH="${AQLI_VERIFY_FAST_GGUF_COMPARE_BENCH:-${ROOT_DIR}/scripts/bench-gguf-engine-compare.sh}"
SAFETENSOR_BENCH="${AQLI_VERIFY_FAST_SAFETENSOR_BENCH:-${ROOT_DIR}/scripts/bench-safetensor-inference.sh}"
SAFETENSOR_DIAGNOSIS_SCRIPT="${AQLI_VERIFY_SAFETENSOR_DIAGNOSIS_SCRIPT:-${ROOT_DIR}/scripts/diagnose-safetensor-profile-summary.sh}"
SAFETENSOR_COMPARE_SCRIPT="${AQLI_VERIFY_SAFETENSOR_COMPARE_SCRIPT:-${ROOT_DIR}/scripts/compare-safetensor-profile-diagnosis.sh}"
MANIFEST="${SUMMARY_DIR}/manifest.tsv"
RESULTS="${SUMMARY_DIR}/results.tsv"
CONFIG="${SUMMARY_DIR}/config.tsv"
ROLLUP="${SUMMARY_DIR}/rollup.tsv"
BACKEND="${SUMMARY_DIR}/backend.tsv"
CONTRACTS="${SUMMARY_DIR}/contracts.tsv"
SLOWEST="${SUMMARY_DIR}/slowest.tsv"
ROUTE_STATS="${SUMMARY_DIR}/route-stats.tsv"
ROUTE_REGRESSION="${SUMMARY_DIR}/route-regression.tsv"
SAFETENSOR_DIAGNOSIS_DIR="${SUMMARY_DIR}/safetensor-diagnosis"
SAFETENSOR_DIAGNOSIS="${SAFETENSOR_DIAGNOSIS_DIR}/diagnosis.tsv"
SAFETENSOR_DIAGNOSIS_STAGES="${SAFETENSOR_DIAGNOSIS_DIR}/stages.tsv"
SAFETENSOR_DIAGNOSIS_PATHS="${SAFETENSOR_DIAGNOSIS_DIR}/paths.tsv"
SAFETENSOR_DIAGNOSIS_REPORT="${SAFETENSOR_DIAGNOSIS_DIR}/report.txt"
SAFETENSOR_REGRESSION_DIR="${SUMMARY_DIR}/safetensor-regression"
SAFETENSOR_REGRESSION_COMPARISON="${SAFETENSOR_REGRESSION_DIR}/comparison.tsv"
SAFETENSOR_REGRESSION_SUMMARY="${SAFETENSOR_REGRESSION_DIR}/summary.tsv"
SAFETENSOR_REGRESSION_TABLE="${SAFETENSOR_REGRESSION_DIR}/comparison-table.txt"
SAFETENSOR_REGRESSION_REPORT="${SAFETENSOR_REGRESSION_DIR}/report.txt"
RAN_ANY=0
FINAL_EXIT_CODE=0
printf 'gate\tsummary\n' > "$MANIFEST"
printf 'gate\tstatus\texitCode\telapsedMs\tsummary\tstdout\tstderr\targv\treason\n' > "$RESULTS"
printf 'gate\tcase\tdurationMs\tbackend\tmetrics\tlog\n' > "$ROLLUP"
printf 'gate\tcase\tbackend\tmetal\tmetalPathProof\tfallbackPathEvidence\twarmReuse\tpromptCache\tjavaFallbackRefusal\trouteResolveMs\trouteCacheHit\tselectedArtifact\trouteSource\tlog\n' > "$BACKEND"
printf 'gate\tcase\tmetalRequired\tmetal\tmetalStatus\tmetalPathRequired\tmetalPathProof\tmetalPathStatus\tfallbackPathRejectedRequired\tfallbackPathEvidence\tfallbackPathStatus\twarmRequired\twarmReuse\twarmStatus\tpromptCacheRequired\tpromptCache\tpromptCacheStatus\tjavaFallbackRefusalRequired\tjavaFallbackRefusal\tjavaFallbackRefusalStatus\trouteCacheRequired\trouteCacheHit\trouteCacheStatus\tselectedArtifactRequired\tselectedArtifact\tselectedArtifactStatus\trouteResolveMs\trouteSource\tlog\trouteSourceRequired\trouteSourceStatus\trouteResolveBudgetMs\trouteResolveStatus\n' > "$CONTRACTS"
printf 'rank\tgate\tcase\tdurationMs\tbackend\tmetrics\tlog\n' > "$SLOWEST"
printf 'gate\tcase\tcount\tminRouteResolveMs\tavgRouteResolveMs\tmaxRouteResolveMs\trouteSources\n' > "$ROUTE_STATS"
printf 'gate\tcase\tbaselineAvgRouteResolveMs\tcurrentAvgRouteResolveMs\tdeltaMs\tdeltaPercent\tstatus\treason\n' > "$ROUTE_REGRESSION"
ROUTE_ISSUES="${SUMMARY_DIR}/route-issues.tsv"
printf 'gate\tcase\tissue\tactual\texpected\tbudgetMs\tlog\n' > "$ROUTE_ISSUES"
ROUTE_ISSUE_SUMMARY="${SUMMARY_DIR}/route-issue-summary.tsv"
printf 'issue\tcount\tcases\n' > "$ROUTE_ISSUE_SUMMARY"

should_run() {
  local gate="$1"
  case "$ONLY" in
    auto)
      case "$gate" in
        litert) model_present "$LITERT_MODEL" ;;
        gemma4-litert) gemma4_litert_route_summary_ready || model_present "$GEMMA4_LITERT_MODEL" ;;
        gguf) model_present "$GGUF_MODEL" ;;
        safetensor) model_present "$SAFETENSOR_MODEL" ;;
        *) return 1 ;;
      esac
      ;;
    all) return 0 ;;
    both) [[ "$gate" == "litert" || "$gate" == "gguf" ]] ;;
    *) [[ "$gate" == "$ONLY" ]] ;;
  esac
}

skip_reason() {
  local gate="$1"
  case "$ONLY" in
    auto)
      case "$gate" in
        litert)
          model_present "$LITERT_MODEL" && printf 'not-selected' || printf 'model-not-found'
          ;;
        gemma4-litert)
          if gemma4_litert_route_summary_ready || model_present "$GEMMA4_LITERT_MODEL"; then
            printf 'not-selected'
          else
            printf 'model-not-found'
          fi
          ;;
        gguf)
          model_present "$GGUF_MODEL" && printf 'not-selected' || printf 'model-not-found'
          ;;
        safetensor)
          model_present "$SAFETENSOR_MODEL" && printf 'not-selected' || printf 'model-not-found'
          ;;
        *) printf 'auto-skips-compare' ;;
      esac
      ;;
    both)
      case "$gate" in
        litert|gguf) printf 'not-selected' ;;
        gemma4-litert) printf 'mode-both-skips-gemma4-litert' ;;
        gguf-compare) printf 'mode-both-skips-compare' ;;
        safetensor) printf 'mode-both-skips-safetensor' ;;
        *) printf 'not-selected' ;;
      esac
      ;;
    *) printf 'not-selected' ;;
  esac
}

gemma4_litert_route_summary_ready() {
  truthy_env "$GEMMA4_LITERT_ROUTE_ONLY" && [[ -s "$GEMMA4_LITERT_ROUTE_SUMMARY_FILE" ]]
}

model_present() {
  local ref="$1"
  local index_path="${AQLI_VERIFY_MODEL_INDEX:-${HOME}/.aqli/models/index.json}"
  [[ -f "$index_path" ]] || return 1
  awk -v ref="$ref" '
    function json_value(line, value) {
      value = line
      sub(/^[^:]*:[[:space:]]*"/, "", value)
      sub(/".*$/, "", value)
      return value
    }
    {
      lower = tolower($0)
      if (lower ~ /"(id|shortid|name|path)"[[:space:]]*:/) {
        value = json_value($0)
        if (value == ref || tolower(value) == tolower(ref)) {
          found = 1
        }
      }
    }
    END { exit found ? 0 : 1 }
  ' "$index_path"
}

truthy_env() {
  case "$(printf '%s' "${1:-false}" | tr '[:upper:]' '[:lower:]')" in
    1|true|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

safetensor_default_var() {
  local var_name="$1"
  local value="$2"
  if [[ -z "${!var_name:-}" ]]; then
    printf -v "$var_name" '%s' "$value"
  fi
}

apply_safetensor_preset() {
  [[ -n "$SAFETENSOR_PRESET" ]] || return 0
  if [[ ! -f "$SAFETENSOR_PRESET_SCRIPT" ]]; then
    echo "safetensor preset script not found: $SAFETENSOR_PRESET_SCRIPT" >&2
    return 2
  fi
  if ! bash "$SAFETENSOR_PRESET_SCRIPT" validate "$SAFETENSOR_PRESET"; then
    local available_presets
    available_presets="$(bash "$SAFETENSOR_PRESET_SCRIPT" names | awk 'BEGIN { out = "" } { if (out != "") out = out ", "; out = out $0 } END { print out }')"
    echo "Unknown --safetensor-preset: $SAFETENSOR_PRESET" >&2
    echo "Available presets: $available_presets" >&2
    return 2
  fi

  local key value
  while IFS=$'\t' read -r key value; do
    case "$key" in
      profile)
        # The aggregate safetensor gate always runs the profile-enabled bench.
        ;;
      requireProfile)
        safetensor_default_var AQLI_VERIFY_SAFETENSOR_REQUIRE_PROFILE "$value"
        ;;
      requireMetal)
        if (( REQUIRE_METAL_EXPLICIT == 0 )); then
          if truthy_env "$value"; then
            REQUIRE_METAL=1
          else
            REQUIRE_METAL=0
          fi
        fi
        ;;
      requireMetalPaths)
        if (( SAFETENSOR_REQUIRE_METAL_PATHS_EXPLICIT == 0 )); then
          SAFETENSOR_REQUIRE_METAL_PATHS="$value"
        fi
        ;;
      rejectFallbackPaths)
        if (( SAFETENSOR_REJECT_FALLBACK_PATHS_EXPLICIT == 0 )); then
          SAFETENSOR_REJECT_FALLBACK_PATHS="$value"
        fi
        ;;
      maxTokens)
        safetensor_default_var AQLI_VERIFY_SAFETENSOR_MAX_TOKENS "$value"
        ;;
      minSpeedTps)
        safetensor_default_var SAFETENSOR_MIN_SPEED_TPS "$value"
        ;;
      topStageThresholdMs)
        safetensor_default_var AQLI_VERIFY_SAFETENSOR_TOP_STAGE_THRESHOLD_MS "$value"
        ;;
      prefillThresholdMs)
        safetensor_default_var AQLI_VERIFY_SAFETENSOR_PREFILL_THRESHOLD_MS "$value"
        ;;
      decodeThresholdMs)
        safetensor_default_var AQLI_VERIFY_SAFETENSOR_DECODE_THRESHOLD_MS "$value"
        ;;
      tpotThresholdMs)
        safetensor_default_var AQLI_VERIFY_SAFETENSOR_TPOT_THRESHOLD_MS "$value"
        ;;
      samplingThresholdMs)
        safetensor_default_var AQLI_VERIFY_SAFETENSOR_SAMPLING_THRESHOLD_MS "$value"
        ;;
      argmaxThresholdMs)
        safetensor_default_var AQLI_VERIFY_SAFETENSOR_ARGMAX_THRESHOLD_MS "$value"
        ;;
      attentionThresholdMs)
        safetensor_default_var AQLI_VERIFY_SAFETENSOR_ATTENTION_THRESHOLD_MS "$value"
        ;;
      ffnThresholdMs)
        safetensor_default_var AQLI_VERIFY_SAFETENSOR_FFN_THRESHOLD_MS "$value"
        ;;
      logitsThresholdMs)
        safetensor_default_var AQLI_VERIFY_SAFETENSOR_LOGITS_THRESHOLD_MS "$value"
        ;;
      minCoreMetalCoverage)
        safetensor_default_var SAFETENSOR_MIN_CORE_METAL_COVERAGE "$value"
        ;;
      minLinearMetalCoverage)
        safetensor_default_var SAFETENSOR_MIN_LINEAR_METAL_COVERAGE "$value"
        ;;
      minLogitsMetalCoverage)
        safetensor_default_var SAFETENSOR_MIN_LOGITS_METAL_COVERAGE "$value"
        ;;
      minFfnMetalCoverage)
        safetensor_default_var SAFETENSOR_MIN_FFN_METAL_COVERAGE "$value"
        ;;
      minAttentionMetalCoverage)
        safetensor_default_var SAFETENSOR_MIN_ATTENTION_METAL_COVERAGE "$value"
        ;;
      minArgmaxMetalCoverage)
        safetensor_default_var SAFETENSOR_MIN_ARGMAX_METAL_COVERAGE "$value"
        ;;
      *)
        echo "Unknown safetensor preset key: $key" >&2
        return 2
        ;;
    esac
  done < <(bash "$SAFETENSOR_PRESET_SCRIPT" defaults "$SAFETENSOR_PRESET")
}

slugify() {
  local value="$1"
  local slug
  slug="$(printf '%s' "$value" | tr '/:[:space:]' '___' | tr -cd 'A-Za-z0-9._-')"
  if [[ -z "$slug" ]]; then
    slug="model"
  fi
  printf '%s' "$slug"
}

resolve_safetensor_baseline() {
  if [[ -z "$SAFETENSOR_BASELINE_STAGES" ]]; then
    return 0
  fi
  case "$(printf '%s' "$SAFETENSOR_BASELINE_STAGES" | tr '[:upper:]' '[:lower:]')" in
    latest|auto)
      local baseline_name="$SAFETENSOR_BASELINE_NAME"
      if [[ -z "$baseline_name" ]]; then
        baseline_name="$(slugify "$SAFETENSOR_MODEL")"
      fi
      SAFETENSOR_BASELINE_NAME="$baseline_name"
      SAFETENSOR_BASELINE_STAGES="${SAFETENSOR_BASELINE_ROOT%/}/${baseline_name}/latest-stages.tsv"
      ;;
  esac
}

record_manifest() {
  local gate="$1"
  local summary="$2"
  RAN_ANY=1
  printf '%s\t%s\n' "$gate" "$summary" >> "$MANIFEST"
}

record_skip() {
  local gate="$1"
  local reason="$2"
  printf '%s\tskip\t0\t0\t\t\t\t\t%s\n' "$gate" "$reason" >> "$RESULTS"
}

record_gate_result() {
  local gate="$1"
  local exit_code="$2"
  local elapsed_ms="$3"
  local summary="$4"
  local stdout_log="$5"
  local stderr_log="$6"
  local argv_log="$7"
  local reason="$8"
  local status="fail"
  if [[ "$exit_code" -eq 0 ]]; then
    status="pass"
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$gate" "$status" "$exit_code" "$elapsed_ms" "$summary" "$stdout_log" "$stderr_log" "$argv_log" "$(safe_tsv_field "$reason")" >> "$RESULTS"
}

handle_gate_failure() {
  local gate="$1"
  local exit_code="$2"
  local reason="$3"
  local stderr_log="$4"
  local reason_suffix=""
  if [[ -n "$reason" ]]; then
    reason_suffix=" (${reason})"
  fi
  echo "FAIL: ${gate} speed gate failed${reason_suffix}; artifacts are in ${SUMMARY_DIR}; stderr=${stderr_log}" >&2
  if truthy_env "$CONTINUE_ON_FAILURE"; then
    if [[ "$FINAL_EXIT_CODE" -eq 0 ]]; then
      FINAL_EXIT_CODE="$exit_code"
    fi
    return 0
  fi
  print_artifact_footer
  exit "$exit_code"
}

run_gate_command() {
  local gate="$1"
  local summary="$2"
  shift 2
  local stdout_log="${SUMMARY_DIR}/${gate}.out"
  local stderr_log="${SUMMARY_DIR}/${gate}.err"
  local argv_log="${SUMMARY_DIR}/${gate}.argv.tsv"
  local start_seconds="$SECONDS"
  local exit_code elapsed_ms reason
  reason=""
  write_argv_log "$argv_log" "$@"
  set +e
  "$@" >"$stdout_log" 2>"$stderr_log"
  exit_code=$?
  set -e
  elapsed_ms=$(( (SECONDS - start_seconds) * 1000 ))
  append_rollup "$gate" "$summary"
  refresh_backend_report
  refresh_route_reports
  refresh_slowest_report
  if [[ "$gate" == "safetensor" ]]; then
    refresh_safetensor_diagnosis "$summary"
  fi
  local contract_reason
  contract_reason="$(contract_failure_reason "$gate")"
  if [[ "$gate" == "gemma4-litert" && "$exit_code" -eq 0 && -z "$contract_reason" ]]; then
    local route_regression_exit
    set +e
    refresh_route_regression
    route_regression_exit=$?
    set -e
    if [[ "$route_regression_exit" -ne 0 ]]; then
      exit_code="$route_regression_exit"
      reason="route-regression"
    fi
  fi
  if [[ "$gate" == "safetensor" && "$exit_code" -eq 0 && -z "$contract_reason" ]]; then
    local regression_exit
    set +e
    refresh_safetensor_regression
    regression_exit=$?
    set -e
    if [[ "$regression_exit" -ne 0 ]]; then
      exit_code="$regression_exit"
      reason="safetensor-regression"
    fi
  fi
  if [[ "$exit_code" -eq 0 && -n "$contract_reason" ]]; then
    exit_code=1
    reason="$contract_reason"
  fi
  record_gate_result "$gate" "$exit_code" "$elapsed_ms" "$summary" "$stdout_log" "$stderr_log" "$argv_log" "$reason"
  if [[ "$exit_code" -ne 0 ]]; then
    handle_gate_failure "$gate" "$exit_code" "$reason" "$stderr_log"
  fi
}

validate_gemma4_route_summary_schema() {
  local summary="$1"
  if [[ ! -e "$summary" ]]; then
    printf 'missing-file\n'
    return 1
  fi
  if [[ ! -f "$summary" ]]; then
    printf 'not-file\n'
    return 1
  fi
  if [[ ! -r "$summary" ]]; then
    printf 'not-readable\n'
    return 1
  fi
  awk '
    BEGIN {
      FS = "\t"
      required_count = split("case durationMs backend routeResolveMs routeCacheHit selectedArtifact routeSource log", required, " ")
    }
    function append(list, value) {
      return list == "" ? value : list "+" value
    }
    function numeric(value) {
      return value ~ /^[0-9]+([.][0-9]+)?$/
    }
    function placeholder(value) {
      return tolower(value) ~ /^(n\/a|na|none|null|unknown|unset)$/
    }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        header[$i] = i
      }
      missing = ""
      for (i = 1; i <= required_count; i++) {
        if (!(required[i] in header)) {
          missing = append(missing, required[i])
        }
      }
      if (("log" in header) && header["log"] != NF) {
        missing = append(missing, "log-last")
      }
      if (missing != "") {
        print "missing-columns:" missing
        failed = 1
        exit 1
      }
      next
    }
    NF > 0 {
      data_rows++
      empty = ""
      for (i = 1; i <= required_count; i++) {
        if ($(header[required[i]]) == "") {
          empty = append(empty, required[i])
        }
      }
      if (empty != "") {
        print "empty-fields:line" NR ":" empty
        failed = 1
        exit 1
      }
      invalid = ""
      if (!numeric($(header["durationMs"]))) {
        invalid = append(invalid, "durationMs")
      }
      if (!numeric($(header["routeResolveMs"]))) {
        invalid = append(invalid, "routeResolveMs")
      }
      if ($(header["routeCacheHit"]) != "true" && $(header["routeCacheHit"]) != "false") {
        invalid = append(invalid, "routeCacheHit")
      }
      if ($(header["selectedArtifact"]) != "litertlm") {
        invalid = append(invalid, "selectedArtifact")
      }
      if (placeholder($(header["routeSource"]))) {
        invalid = append(invalid, "routeSource")
      }
      if (invalid != "") {
        print "invalid-fields:line" NR ":" invalid
        failed = 1
        exit 1
      }
    }
    END {
      if (failed) {
        exit 1
      }
      if (NR == 0) {
        print "empty"
        exit 1
      }
      if (data_rows == 0) {
        print "no-data"
        exit 1
      }
    }
  ' "$summary"
}

run_summary_gate() {
  local gate="$1"
  local summary="$2"
  local source_summary="$3"
  local stdout_log="${SUMMARY_DIR}/${gate}.out"
  local stderr_log="${SUMMARY_DIR}/${gate}.err"
  local argv_log="${SUMMARY_DIR}/${gate}.argv.tsv"
  local start_seconds="$SECONDS"
  local elapsed_ms reason exit_code
  reason=""
  exit_code=0
  write_argv_log "$argv_log" "summary-only" "--source-summary" "$source_summary" "--summary-file" "$summary"
  printf 'sourceSummary\t%s\n' "$(safe_tsv_field "$source_summary")" > "$stdout_log"
  : > "$stderr_log"
  if [[ "$gate" == "gemma4-litert" ]]; then
    local schema_reason
    if ! schema_reason="$(validate_gemma4_route_summary_schema "$source_summary")"; then
      exit_code=2
      reason="summary-schema:${schema_reason}"
      printf 'schemaError\t%s\n' "$(safe_tsv_field "$schema_reason")" >> "$stderr_log"
    fi
  fi
  if [[ "$exit_code" -eq 0 ]]; then
    awk '1' "$source_summary" > "$summary"
  fi
  elapsed_ms=$(( (SECONDS - start_seconds) * 1000 ))
  if [[ "$exit_code" -eq 0 ]]; then
    append_rollup "$gate" "$summary"
    refresh_backend_report
    refresh_route_reports
    refresh_slowest_report
    local contract_reason
    contract_reason="$(contract_failure_reason "$gate")"
    if [[ -z "$contract_reason" ]]; then
      local route_regression_exit
      set +e
      refresh_route_regression
      route_regression_exit=$?
      set -e
      if [[ "$route_regression_exit" -ne 0 ]]; then
        exit_code="$route_regression_exit"
        reason="route-regression"
      fi
    fi
    if [[ "$exit_code" -eq 0 && -n "$contract_reason" ]]; then
      exit_code=1
      reason="$contract_reason"
    fi
  fi
  record_gate_result "$gate" "$exit_code" "$elapsed_ms" "$summary" "$stdout_log" "$stderr_log" "$argv_log" "$reason"
  if [[ "$exit_code" -ne 0 ]]; then
    handle_gate_failure "$gate" "$exit_code" "$reason" "$stderr_log"
  fi
}

contract_failure_reason() {
  local gate="$1"
  [[ -f "$CONTRACTS" && -s "$CONTRACTS" ]] || return 0
  local reason
  reason="$(awk -v gate="$gate" '
    BEGIN { FS = "\t" }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        header[i] = $i
      }
      next
    }
    $1 == gate {
      for (i = 1; i <= NF; i++) {
        if (header[i] ~ /Status$/ && $i == "fail") {
          if (!seen[header[i]]) {
            if (reason != "") {
              reason = reason ","
            }
            reason = reason header[i]
            seen[header[i]] = 1
          }
        }
      }
    }
    END {
      if (reason != "") {
        print reason
      }
    }
  ' "$CONTRACTS")"
  [[ -n "$reason" ]] || return 0
  if [[ "$gate" == "safetensor" && -f "$SAFETENSOR_DIAGNOSIS_PATHS" ]]; then
    reason="$(decorate_safetensor_contract_reason "$reason")"
  fi
  if [[ "$gate" == "gemma4-litert" ]]; then
    reason="$(decorate_gemma4_route_contract_reason "$reason")"
  fi
  printf 'contract:%s\n' "$reason"
}

decorate_gemma4_route_contract_reason() {
  local reason="$1"
  awk -v reason="$reason" '
    BEGIN { FS = "\t" }
    function append(list, value) {
      if (value == "") {
        return list
      }
      return list == "" ? value : list "+" value
    }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        header[$i] = i
      }
      next
    }
    $1 == "gemma4-litert" {
      if ($(header["routeCacheStatus"]) == "fail") {
        cacheIssues = append(cacheIssues, $2 ":" $(header["routeCacheHit"]) "->true")
      }
      if ($(header["selectedArtifactStatus"]) == "fail") {
        artifactIssues = append(artifactIssues, $2 ":" $(header["selectedArtifact"]) "->litertlm")
      }
      if ($(header["routeSourceStatus"]) == "fail") {
        sourceIssues = append(sourceIssues, $2 ":" $(header["routeSource"]) "->" $(header["routeSourceRequired"]))
      }
      if ($(header["routeResolveStatus"]) == "fail") {
        resolveIssues = append(resolveIssues, $2 ":" $(header["routeResolveMs"]) "/" $(header["routeResolveBudgetMs"]) "ms")
      }
    }
    END {
      decorated = reason
      if (cacheIssues != "") {
        gsub(/routeCacheStatus/, "routeCacheStatus:" cacheIssues, decorated)
      }
      if (artifactIssues != "") {
        gsub(/selectedArtifactStatus/, "selectedArtifactStatus:" artifactIssues, decorated)
      }
      if (sourceIssues != "") {
        gsub(/routeSourceStatus/, "routeSourceStatus:" sourceIssues, decorated)
      }
      if (resolveIssues != "") {
        gsub(/routeResolveStatus/, "routeResolveStatus:" resolveIssues, decorated)
      }
      print decorated
    }
  ' "$CONTRACTS"
}

decorate_safetensor_contract_reason() {
  local reason="$1"
  awk -v reason="$reason" '
    BEGIN { FS = "\t" }
    function append(list, value) {
      if (value == "") {
        return list
      }
      return list == "" ? value : list "+" value
    }
    NR == 1 { next }
    $4 != "true" {
      metalGroups = append(metalGroups, $2)
    }
    $5 != "false" {
      fallbackGroups = append(fallbackGroups, $2)
    }
    END {
      count = split(reason, parts, ",")
      out = ""
      for (i = 1; i <= count; i++) {
        item = parts[i]
        if (item == "metalPathStatus" && metalGroups != "") {
          item = item ":" metalGroups
        } else if (item == "fallbackPathStatus" && fallbackGroups != "") {
          item = item ":" fallbackGroups
        }
        out = out == "" ? item : out "," item
      }
      print out
    }
  ' "$SAFETENSOR_DIAGNOSIS_PATHS"
}

safe_tsv_field() {
  local value="${1:-}"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

write_argv_log() {
  local path="$1"
  shift
  local index=0
  printf 'index\targ\n' > "$path"
  while [[ $# -gt 0 ]]; do
    printf '%s\t%s\n' "$index" "$(safe_tsv_field "$1")" >> "$path"
    index=$((index + 1))
    shift
  done
}

append_rollup() {
  local gate="$1"
  local summary="$2"
  [[ -f "$summary" && -s "$summary" ]] || return 0
  awk -v gate="$gate" '
    BEGIN { FS = OFS = "\t" }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        header[i] = $i
      }
      next
    }
    NF > 0 {
      metrics = ""
      for (i = 4; i < NF; i++) {
        if (header[i] == "" || header[i] == "log") {
          continue
        }
        if (metrics != "") {
          metrics = metrics ","
        }
        metrics = metrics header[i] "=" $i
      }
      print gate, $1, $2, $3, metrics, $NF
    }
  ' "$summary" >> "$ROLLUP"
}

refresh_slowest_report() {
  local limit="$SLOWEST_LIMIT"
  awk -v limit="$limit" '
    BEGIN {
      FS = OFS = "\t"
      if (limit !~ /^[0-9]+$/ || limit < 1) {
        limit = 5
      }
      print "rank", "gate", "case", "durationMs", "backend", "metrics", "log"
    }
    NR == 1 {
      next
    }
    NF >= 6 && $3 ~ /^[0-9]+([.][0-9]+)?$/ {
      row_count++
      duration[row_count] = $3 + 0
      row[row_count] = $0
    }
    END {
      max_rank = (row_count < limit) ? row_count : limit
      for (rank = 1; rank <= max_rank; rank++) {
        best = 0
        for (i = 1; i <= row_count; i++) {
          if (used[i]) {
            continue
          }
          if (best == 0 || duration[i] > duration[best]) {
            best = i
          }
        }
        if (best == 0) {
          break
        }
        used[best] = 1
        print rank, row[best]
      }
    }
  ' "$ROLLUP" > "$SLOWEST"
}

refresh_safetensor_diagnosis() {
  local summary="$1"
  [[ -f "$summary" && -s "$summary" ]] || return 0
  [[ -f "$SAFETENSOR_DIAGNOSIS_SCRIPT" ]] || return 0
  mkdir -p "$SAFETENSOR_DIAGNOSIS_DIR"
  if ! bash "$SAFETENSOR_DIAGNOSIS_SCRIPT" \
      --summary "$summary" \
      --summary-dir "$SAFETENSOR_DIAGNOSIS_DIR" \
      --paths-out "$SAFETENSOR_DIAGNOSIS_PATHS" \
      >"${SAFETENSOR_DIAGNOSIS_DIR}/diagnosis.stdout.log" \
      2>"${SAFETENSOR_DIAGNOSIS_DIR}/diagnosis.stderr.log"; then
    echo "WARN: safetensor diagnosis could not be generated; stderr=${SAFETENSOR_DIAGNOSIS_DIR}/diagnosis.stderr.log" >&2
  fi
}

refresh_safetensor_regression() {
  [[ -n "$SAFETENSOR_BASELINE_STAGES" ]] || return 0
  if [[ ! -f "$SAFETENSOR_BASELINE_STAGES" ]]; then
    echo "ERROR: safetensor baseline stages not found: $SAFETENSOR_BASELINE_STAGES" >&2
    return 2
  fi
  if [[ ! -f "$SAFETENSOR_DIAGNOSIS_STAGES" ]]; then
    echo "ERROR: safetensor current diagnosis stages not found: $SAFETENSOR_DIAGNOSIS_STAGES" >&2
    return 2
  fi
  if [[ ! -f "$SAFETENSOR_COMPARE_SCRIPT" ]]; then
    echo "ERROR: safetensor comparison script not found: $SAFETENSOR_COMPARE_SCRIPT" >&2
    return 2
  fi

  local -a cmd=(
    bash "$SAFETENSOR_COMPARE_SCRIPT"
    --baseline-stages "$SAFETENSOR_BASELINE_STAGES"
    --current-stages "$SAFETENSOR_DIAGNOSIS_STAGES"
    --summary-dir "$SAFETENSOR_REGRESSION_DIR"
    --min-baseline-ms "$SAFETENSOR_MIN_BASELINE_MS"
  )
  if [[ -n "$SAFETENSOR_MAX_REGRESSION_PERCENT" ]]; then
    cmd+=(--max-regression-percent "$SAFETENSOR_MAX_REGRESSION_PERCENT")
  fi
  if [[ -n "$SAFETENSOR_MAX_REGRESSION_MS" ]]; then
    cmd+=(--max-regression-ms "$SAFETENSOR_MAX_REGRESSION_MS")
  fi
  if truthy_env "$SAFETENSOR_FAIL_PRIMARY_SHIFT"; then
    cmd+=(--fail-primary-shift)
  fi

  mkdir -p "$SAFETENSOR_REGRESSION_DIR"
  "${cmd[@]}" \
    >"${SAFETENSOR_REGRESSION_DIR}/comparison.stdout.log" \
    2>"${SAFETENSOR_REGRESSION_DIR}/comparison.stderr.log"
}

refresh_backend_report() {
  awk '
    BEGIN {
      FS = OFS = "\t"
      print "gate", "case", "backend", "metal", "metalPathProof", "fallbackPathEvidence", "warmReuse", "promptCache", "javaFallbackRefusal", "routeResolveMs", "routeCacheHit", "selectedArtifact", "routeSource", "log"
    }
    function metric_value(metrics, key, parts, count, i, prefix) {
      count = split(metrics, parts, ",")
      prefix = key "="
      for (i = 1; i <= count; i++) {
        if (index(parts[i], prefix) == 1) {
          return substr(parts[i], length(prefix) + 1)
        }
      }
      return "n/a"
    }
    function has_metal_path(value) {
      return value ~ /(Metal|metal|MTL|mtl|MPS|mps|GPU|gpu)/
    }
    function has_fallback_path(value) {
      return tolower(value) ~ /(cpu|java|accelerate|fallback|skip|reject|unavailable)/
    }
    NR == 1 {
      next
    }
    NF >= 6 {
      profile_metal = metric_value($5, "profileMetal")
      if (profile_metal == "true" || profile_metal == "false") {
        metal = profile_metal
      } else {
        metal = ($4 ~ /(Metal|MTL|metal|gpu|GPU)/) ? "true" : "false"
      }
      warm = metric_value($5, "warmEngine")
      if (warm == "n/a") {
        warm = metric_value($5, "warmSession")
      }
      metal_path_proof = "n/a"
      fallback_path_evidence = "n/a"
      if ($1 == "safetensor") {
        linear_paths = metric_value($5, "linearPaths")
        logits_paths = metric_value($5, "logitsPaths")
        ffn_paths = metric_value($5, "ffnPaths")
        attention_paths = metric_value($5, "attentionPaths")
        metal_path_proof = (has_metal_path(linear_paths) && has_metal_path(logits_paths) && has_metal_path(ffn_paths) && has_metal_path(attention_paths)) ? "true" : "false"
        fallback_path_evidence = (has_fallback_path(linear_paths) || has_fallback_path(logits_paths) || has_fallback_path(ffn_paths) || has_fallback_path(attention_paths)) ? "true" : "false"
      }
      prompt_cache = metric_value($5, "promptCache")
      java_refusal = metric_value($5, "javaFallbackRefusal")
      route_resolve_ms = metric_value($5, "routeResolveMs")
      route_cache_hit = metric_value($5, "routeCacheHit")
      selected_artifact = metric_value($5, "selectedArtifact")
      route_source = metric_value($5, "routeSource")
      print $1, $2, $4, metal, metal_path_proof, fallback_path_evidence, warm, prompt_cache, java_refusal, route_resolve_ms, route_cache_hit, selected_artifact, route_source, $6
    }
  ' "$ROLLUP" > "$BACKEND"
}

refresh_route_stats_report() {
  awk '
    BEGIN {
      FS = OFS = "\t"
      print "gate", "case", "count", "minRouteResolveMs", "avgRouteResolveMs", "maxRouteResolveMs", "routeSources"
    }
    NR == 1 {
      next
    }
    $1 == "gemma4-litert" && $10 ~ /^[0-9]+([.][0-9]+)?$/ {
      key = $1 SUBSEP $2
      if (!(key in count)) {
        ordered_count++
        order[ordered_count] = key
      }
      if (!(key in count) || ($10 + 0) < min[key]) {
        min[key] = $10 + 0
      }
      if (!(key in count) || ($10 + 0) > max[key]) {
        max[key] = $10 + 0
      }
      count[key]++
      sum[key] += $10 + 0
      if ($13 != "" && $13 != "n/a" && !seen_source[key SUBSEP $13]) {
        sources[key] = sources[key] == "" ? $13 : sources[key] "," $13
        seen_source[key SUBSEP $13] = 1
      }
      gate[key] = $1
      case_name[key] = $2
    }
    END {
      for (i = 1; i <= ordered_count; i++) {
        key = order[i]
        printf "%s\t%s\t%d\t%.3f\t%.3f\t%.3f\t%s\n", gate[key], case_name[key], count[key], min[key], sum[key] / count[key], max[key], sources[key]
      }
    }
  ' "$BACKEND" > "$ROUTE_STATS"
}

refresh_contract_report() {
  local require_metal="false"
  local require_safetensor_metal_paths="false"
  local reject_safetensor_fallback_paths="false"
  local litert_warm_only="false"
  local gguf_warm_only="false"
  local gguf_prompt_cache="true"
  local compare_java_refusal="true"
  if [[ "$REQUIRE_METAL" -eq 1 ]]; then
    require_metal="true"
  fi
  if truthy_env "$SAFETENSOR_REQUIRE_METAL_PATHS"; then
    require_safetensor_metal_paths="true"
  fi
  if truthy_env "$SAFETENSOR_REJECT_FALLBACK_PATHS"; then
    reject_safetensor_fallback_paths="true"
  fi
  if [[ "$WARM_ONLY" -eq 1 ]] || truthy_env "${AQLI_VERIFY_LITERT_WARM_ONLY:-false}"; then
    litert_warm_only="true"
  fi
  if [[ "$WARM_ONLY" -eq 1 ]] || truthy_env "${AQLI_VERIFY_GGUF_WARM_ONLY:-false}"; then
    gguf_warm_only="true"
  fi
  if ! truthy_env "${AQLI_VERIFY_GGUF_PROMPT_CACHE:-true}"; then
    gguf_prompt_cache="false"
  fi
  if ! truthy_env "${AQLI_VERIFY_GGUF_COMPARE_JAVA_REFUSAL:-true}"; then
    compare_java_refusal="false"
  fi

  awk \
    -v requireMetal="$require_metal" \
    -v requireSafetensorMetalPaths="$require_safetensor_metal_paths" \
    -v rejectSafetensorFallbackPaths="$reject_safetensor_fallback_paths" \
    -v litertWarmOnly="$litert_warm_only" \
    -v ggufWarmOnly="$gguf_warm_only" \
    -v ggufPromptCache="$gguf_prompt_cache" \
    -v compareJavaRefusal="$compare_java_refusal" \
    -v gemma4RouteSource="$GEMMA4_LITERT_ROUTE_SOURCE" \
    -v gemma4RouteResolveBudgetMs="$GEMMA4_LITERT_ROUTE_RESOLVE_THRESHOLD_MS" \
    -v gemma4ColdRouteResolveBudgetMs="$GEMMA4_LITERT_COLD_ROUTE_RESOLVE_THRESHOLD_MS" '
    BEGIN {
      FS = OFS = "\t"
      print "gate", "case", "metalRequired", "metal", "metalStatus", "metalPathRequired", "metalPathProof", "metalPathStatus", "fallbackPathRejectedRequired", "fallbackPathEvidence", "fallbackPathStatus", "warmRequired", "warmReuse", "warmStatus", "promptCacheRequired", "promptCache", "promptCacheStatus", "javaFallbackRefusalRequired", "javaFallbackRefusal", "javaFallbackRefusalStatus", "routeCacheRequired", "routeCacheHit", "routeCacheStatus", "selectedArtifactRequired", "selectedArtifact", "selectedArtifactStatus", "routeResolveMs", "routeSource", "log", "routeSourceRequired", "routeSourceStatus", "routeResolveBudgetMs", "routeResolveStatus"
    }
    function numeric(value) {
      return value ~ /^[0-9]+([.][0-9]+)?$/
    }
    function bool_status(required, actual) {
      if (required != "true") {
        return "n/a"
      }
      return (actual == "true") ? "pass" : "fail"
    }
    function prompt_cache_status(required, actual) {
      if (required != "true") {
        return "n/a"
      }
      return (actual == "hit" || actual == "prefix-hit") ? "pass" : "fail"
    }
    function fallback_absent_status(required, actual) {
      if (required != "true") {
        return "n/a"
      }
      return (actual == "false") ? "pass" : "fail"
    }
    function java_refusal_status(required, actual) {
      if (required != "true") {
        return "n/a"
      }
      return (actual == "checked") ? "pass" : "fail"
    }
    function route_cache_status(required, actual) {
      if (required != "true") {
        return "n/a"
      }
      return (actual == "true") ? "pass" : "fail"
    }
    function selected_artifact_status(required, actual) {
      if (required != "true") {
        return "n/a"
      }
      return (actual == "litertlm") ? "pass" : "fail"
    }
    function route_source_status(required, actual) {
      if (required == "" || required == "n/a") {
        return "n/a"
      }
      return (actual == required) ? "pass" : "fail"
    }
    function route_resolve_status(budget_ms, actual_ms) {
      if (budget_ms == "" || budget_ms == "n/a") {
        return "n/a"
      }
      if (!numeric(actual_ms)) {
        return "fail"
      }
      return ((actual_ms + 0) <= (budget_ms + 0)) ? "pass" : "fail"
    }
    NR == 1 {
      next
    }
    NF >= 14 {
      gate = $1
      case_name = $2
      metal = $4
      metal_path_proof = $5
      fallback_path_evidence = $6
      warm = $7
      prompt_cache = $8
      java_refusal = $9
      route_resolve_ms = $10
      route_cache_hit = $11
      selected_artifact = $12
      route_source = $13
      log_path = $14

      warm_required = "false"
      if (gate == "litert" && (case_name == "warm" || (litertWarmOnly == "true" && case_name ~ /^warmup/))) {
        warm_required = "true"
      }
      if (gate == "gemma4-litert" && case_name == "warm") {
        warm_required = "true"
      }
      if (gate == "gguf" && (case_name == "warm" || (ggufWarmOnly == "true" && case_name ~ /^warmup/))) {
        warm_required = "true"
      }

      prompt_required = "false"
      if (gate == "gguf" && ggufPromptCache == "true" && (case_name == "warm" || case_name == "warmup1" || (ggufWarmOnly == "true" && case_name ~ /^warmup/))) {
        prompt_required = "true"
      }

      java_required = "false"
      if (gate == "gguf-compare" && compareJavaRefusal == "true") {
        java_required = "true"
      }

      metal_path_required = (gate == "safetensor" && requireSafetensorMetalPaths == "true") ? "true" : "false"
      fallback_path_rejected_required = (gate == "safetensor" && rejectSafetensorFallbackPaths == "true") ? "true" : "false"
      route_cache_required = (gate == "gemma4-litert") ? "true" : "false"
      selected_artifact_required = (gate == "gemma4-litert") ? "true" : "false"
      route_source_required = (gate == "gemma4-litert" && gemma4RouteSource != "") ? gemma4RouteSource : "n/a"
      route_resolve_budget_ms = (gate == "gemma4-litert" && gemma4RouteResolveBudgetMs != "") ? gemma4RouteResolveBudgetMs : "n/a"
      if (gate == "gemma4-litert" && case_name == "cold" && gemma4ColdRouteResolveBudgetMs != "") {
        route_resolve_budget_ms = gemma4ColdRouteResolveBudgetMs
      }
      print gate, case_name, requireMetal, metal, bool_status(requireMetal, metal), metal_path_required, metal_path_proof, bool_status(metal_path_required, metal_path_proof), fallback_path_rejected_required, fallback_path_evidence, fallback_absent_status(fallback_path_rejected_required, fallback_path_evidence), warm_required, warm, bool_status(warm_required, warm), prompt_required, prompt_cache, prompt_cache_status(prompt_required, prompt_cache), java_required, java_refusal, java_refusal_status(java_required, java_refusal), route_cache_required, route_cache_hit, route_cache_status(route_cache_required, route_cache_hit), selected_artifact_required, selected_artifact, selected_artifact_status(selected_artifact_required, selected_artifact), route_resolve_ms, route_source, log_path, route_source_required, route_source_status(route_source_required, route_source), route_resolve_budget_ms, route_resolve_status(route_resolve_budget_ms, route_resolve_ms)
    }
  ' "$BACKEND" > "$CONTRACTS"
}

refresh_route_issues_report() {
  awk '
    BEGIN {
      FS = OFS = "\t"
      print "gate", "case", "issue", "actual", "expected", "budgetMs", "log"
    }
    NR == 1 {
      next
    }
    $1 == "gemma4-litert" {
      if ($23 == "fail") {
        print $1, $2, "routeCache", $22, "true", "n/a", $29
      }
      if ($26 == "fail") {
        print $1, $2, "selectedArtifact", $25, "litertlm", "n/a", $29
      }
      if ($31 == "fail") {
        print $1, $2, "routeSource", $28, $30, "n/a", $29
      }
      if ($33 == "fail") {
        print $1, $2, "routeResolve", $27, "lte", $32, $29
      }
    }
  ' "$CONTRACTS" > "$ROUTE_ISSUES"
}

refresh_route_issue_summary_report() {
  awk '
    BEGIN {
      FS = OFS = "\t"
      print "issue", "count", "cases"
    }
    NR == 1 {
      next
    }
    {
      issue = $3
      case_name = $2
      if (!(issue in count)) {
        ordered_count++
        order[ordered_count] = issue
      }
      count[issue]++
      if (!seen_case[issue SUBSEP case_name]) {
        cases[issue] = cases[issue] == "" ? case_name : cases[issue] "," case_name
        seen_case[issue SUBSEP case_name] = 1
      }
    }
    END {
      for (i = 1; i <= ordered_count; i++) {
        issue = order[i]
        print issue, count[issue], cases[issue]
      }
    }
  ' "$ROUTE_ISSUES" > "$ROUTE_ISSUE_SUMMARY"
}

refresh_route_reports() {
  refresh_route_stats_report
  refresh_contract_report
  refresh_route_issues_report
  refresh_route_issue_summary_report
}

refresh_route_regression() {
  [[ -n "$GEMMA4_LITERT_ROUTE_BASELINE_STATS" ]] || return 0
  if [[ ! -f "$GEMMA4_LITERT_ROUTE_BASELINE_STATS" ]]; then
    echo "ERROR: Gemma4 route baseline stats not found: $GEMMA4_LITERT_ROUTE_BASELINE_STATS" >&2
    return 2
  fi
  if [[ -z "$GEMMA4_LITERT_ROUTE_MAX_REGRESSION_MS" && -z "$GEMMA4_LITERT_ROUTE_MAX_REGRESSION_PERCENT" ]]; then
    return 0
  fi
  awk \
    -v maxRegressionMs="$GEMMA4_LITERT_ROUTE_MAX_REGRESSION_MS" \
    -v maxRegressionPercent="$GEMMA4_LITERT_ROUTE_MAX_REGRESSION_PERCENT" '
    BEGIN {
      FS = OFS = "\t"
      print "gate", "case", "baselineAvgRouteResolveMs", "currentAvgRouteResolveMs", "deltaMs", "deltaPercent", "status", "reason"
    }
    function numeric(value) {
      return value ~ /^[0-9]+([.][0-9]+)?$/
    }
    function append(list, value) {
      return list == "" ? value : list "," value
    }
    FNR == 1 {
      next
    }
    FNR == NR && $1 == "gemma4-litert" && numeric($5) {
      key = $1 SUBSEP $2
      baseline_avg[key] = $5 + 0
      next
    }
    FNR != NR && $1 == "gemma4-litert" && numeric($5) {
      key = $1 SUBSEP $2
      current_avg = $5 + 0
      if (!(key in baseline_avg)) {
        next
      }
      split(key, parts, SUBSEP)
      delta_ms = current_avg - baseline_avg[key]
      delta_percent = baseline_avg[key] > 0 ? (delta_ms / baseline_avg[key]) * 100 : 0
      status = "pass"
      reason = ""
      if (maxRegressionMs != "" && delta_ms > (maxRegressionMs + 0)) {
        status = "fail"
        reason = append(reason, sprintf("deltaMs %.3f > %.3f", delta_ms, maxRegressionMs + 0))
      }
      if (maxRegressionPercent != "" && delta_percent > (maxRegressionPercent + 0)) {
        status = "fail"
        reason = append(reason, sprintf("deltaPercent %.3f > %.3f", delta_percent, maxRegressionPercent + 0))
      }
      if (status == "fail") {
        failed = 1
      }
      printf "%s\t%s\t%.3f\t%.3f\t%.3f\t%.3f\t%s\t%s\n", parts[1], parts[2], baseline_avg[key], current_avg, delta_ms, delta_percent, status, reason
    }
    END {
      if (failed) {
        exit 1
      }
    }
  ' "$GEMMA4_LITERT_ROUTE_BASELINE_STATS" "$ROUTE_STATS" > "$ROUTE_REGRESSION"
}

print_artifact_footer() {
  echo "summaryDir=$SUMMARY_DIR"
  echo "artifacts.config=$CONFIG"
  echo "artifacts.results=$RESULTS"
  echo "artifacts.rollup=$ROLLUP"
  echo "artifacts.backend=$BACKEND"
  echo "artifacts.contracts=$CONTRACTS"
  echo "artifacts.slowest=$SLOWEST"
  echo "artifacts.routeStats=$ROUTE_STATS"
  echo "artifacts.routeRegression=$ROUTE_REGRESSION"
  echo "artifacts.routeIssues=$ROUTE_ISSUES"
  echo "artifacts.routeIssueSummary=$ROUTE_ISSUE_SUMMARY"
  if [[ -f "$SAFETENSOR_DIAGNOSIS" ]]; then
    echo "artifacts.safetensorDiagnosis=$SAFETENSOR_DIAGNOSIS"
    echo "artifacts.safetensorDiagnosisStages=$SAFETENSOR_DIAGNOSIS_STAGES"
    echo "artifacts.safetensorDiagnosisPaths=$SAFETENSOR_DIAGNOSIS_PATHS"
    echo "artifacts.safetensorDiagnosisReport=$SAFETENSOR_DIAGNOSIS_REPORT"
  fi
  if [[ -f "$SAFETENSOR_REGRESSION_SUMMARY" ]]; then
    echo "artifacts.safetensorRegression=$SAFETENSOR_REGRESSION_COMPARISON"
    echo "artifacts.safetensorRegressionSummary=$SAFETENSOR_REGRESSION_SUMMARY"
    echo "artifacts.safetensorRegressionTable=$SAFETENSOR_REGRESSION_TABLE"
    echo "artifacts.safetensorRegressionReport=$SAFETENSOR_REGRESSION_REPORT"
  fi
  if awk 'NR > 1 { found = 1 } END { exit found ? 0 : 1 }' "$BACKEND"; then
    echo "backend:"
    awk '
      BEGIN { FS = "\t" }
      NR == 1 { next }
      {
        printf "  %s/%s metal=%s metalPathProof=%s fallbackPathEvidence=%s warmReuse=%s promptCache=%s javaFallbackRefusal=%s routeResolveMs=%s routeCacheHit=%s selectedArtifact=%s routeSource=%s backend=%s log=%s\n", $1, $2, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $3, $14
      }
    ' "$BACKEND"
  fi
  if awk 'NR > 1 { found = 1 } END { exit found ? 0 : 1 }' "$CONTRACTS"; then
    echo "contracts:"
    awk '
      BEGIN { FS = "\t" }
      NR == 1 { next }
      {
        printf "  %s/%s metal=%s metalPath=%s fallbackPath=%s warm=%s promptCache=%s javaRefusal=%s routeCache=%s selectedArtifact=%s routeResolveMs=%s routeSource=%s routeSourceStatus=%s routeResolveBudgetMs=%s routeResolveStatus=%s log=%s\n", $1, $2, $5, $8, $11, $14, $17, $20, $23, $26, $27, $28, $31, $32, $33, $29
      }
    ' "$CONTRACTS"
  fi
  if awk 'NR > 1 { found = 1 } END { exit found ? 0 : 1 }' "$SLOWEST"; then
    echo "slowest:"
    awk '
      BEGIN { FS = "\t" }
      NR == 1 { next }
      {
        printf "  #%s %s/%s duration=%sms backend=%s log=%s\n", $1, $2, $3, $4, $5, $7
      }
    ' "$SLOWEST"
  fi
  if awk 'NR > 1 { found = 1 } END { exit found ? 0 : 1 }' "$ROUTE_STATS"; then
    echo "routeStats:"
    awk '
      BEGIN { FS = "\t" }
      NR == 1 { next }
      {
        printf "  %s/%s count=%s min=%sms avg=%sms max=%sms routeSources=%s\n", $1, $2, $3, $4, $5, $6, $7
      }
    ' "$ROUTE_STATS"
  fi
  if awk 'NR > 1 { found = 1 } END { exit found ? 0 : 1 }' "$ROUTE_REGRESSION"; then
    echo "routeRegression:"
    awk '
      BEGIN { FS = "\t" }
      NR == 1 { next }
      {
        printf "  %s/%s baselineAvg=%sms currentAvg=%sms delta=%sms deltaPercent=%s status=%s reason=%s\n", $1, $2, $3, $4, $5, $6, $7, $8
      }
    ' "$ROUTE_REGRESSION"
  fi
  if awk 'NR > 1 { found = 1 } END { exit found ? 0 : 1 }' "$ROUTE_ISSUES"; then
    echo "routeIssues:"
    awk '
      BEGIN { FS = "\t" }
      NR == 1 { next }
      {
        printf "  %s/%s issue=%s actual=%s expected=%s budgetMs=%s log=%s\n", $1, $2, $3, $4, $5, $6, $7
      }
    ' "$ROUTE_ISSUES"
  fi
  if awk 'NR > 1 { found = 1 } END { exit found ? 0 : 1 }' "$ROUTE_ISSUE_SUMMARY"; then
    echo "routeIssueSummary:"
    awk '
      BEGIN { FS = "\t" }
      NR == 1 { next }
      {
        printf "  issue=%s count=%s cases=%s\n", $1, $2, $3
      }
    ' "$ROUTE_ISSUE_SUMMARY"
  fi
  if [[ -f "$SAFETENSOR_DIAGNOSIS" ]] && awk 'NR > 1 { found = 1 } END { exit found ? 0 : 1 }' "$SAFETENSOR_DIAGNOSIS"; then
    echo "safetensorDiagnosis:"
    awk '
      BEGIN { FS = "\t" }
      $1 == "primaryStage" { stage = $2 }
      $1 == "primaryMetric" { metric = $2 }
      $1 == "primaryValueMs" { value = $2 }
      $1 == "recommendation" {
        recommendation = $0
        sub(/^[^\t]*\t/, "", recommendation)
      }
      END {
        if (stage != "") {
          printf "  primaryStage=%s primaryMetric=%s primaryValueMs=%s recommendation=%s\n", stage, metric, value, recommendation
        }
      }
    ' "$SAFETENSOR_DIAGNOSIS"
  fi
  if [[ -f "$SAFETENSOR_DIAGNOSIS_PATHS" ]] && awk 'NR > 1 { found = 1 } END { exit found ? 0 : 1 }' "$SAFETENSOR_DIAGNOSIS_PATHS"; then
    echo "safetensorPaths:"
    awk '
      BEGIN { FS = "\t" }
      NR == 1 { next }
      {
        printf "  %s scope=%s status=%s metal=%s fallback=%s primary=%s evidence=%s\n", $2, $3, $6, $4, $5, $7, $8
      }
    ' "$SAFETENSOR_DIAGNOSIS_PATHS"
  fi
  if [[ -f "$SAFETENSOR_DIAGNOSIS_PATHS" ]] && awk 'BEGIN { FS = "\t" } NR > 1 && $6 != "pass" { found = 1 } END { exit found ? 0 : 1 }' "$SAFETENSOR_DIAGNOSIS_PATHS"; then
    echo "safetensorPathIssues:"
    awk '
      BEGIN { FS = "\t" }
      NR == 1 { next }
      $6 != "pass" {
        printf "  %s scope=%s status=%s metal=%s fallback=%s primary=%s action=%s evidence=%s\n", $2, $3, $6, $4, $5, $7, $9, $8
      }
    ' "$SAFETENSOR_DIAGNOSIS_PATHS"
  fi
  if [[ -f "$SAFETENSOR_REGRESSION_SUMMARY" ]] && awk 'NR > 1 { found = 1 } END { exit found ? 0 : 1 }' "$SAFETENSOR_REGRESSION_SUMMARY"; then
    echo "safetensorRegression:"
    awk '
      BEGIN { FS = "\t" }
      $1 == "status" { status = $2 }
      $1 == "reason" { reason = $2 }
      $1 == "primaryStageChanged" { primaryChanged = $2 }
      $1 == "largestSlowdownStage" { slowdownStage = $2 }
      $1 == "largestSlowdownMetric" { slowdownMetric = $2 }
      $1 == "largestSlowdownMs" { slowdownMs = $2 }
      $1 == "largestSlowdownPercent" { slowdownPercent = $2 }
      END {
        if (status != "") {
          printf "  status=%s reason=%s primaryStageChanged=%s largestSlowdown=%s/%s deltaMs=%s deltaPercent=%s\n", status, reason, primaryChanged, slowdownStage, slowdownMetric, slowdownMs, slowdownPercent
        }
      }
    ' "$SAFETENSOR_REGRESSION_SUMMARY"
  fi
}

config_row() {
  printf '%s\t%s\n' "$(safe_tsv_field "$1")" "$(safe_tsv_field "${2:-}")" >> "$CONFIG"
}

write_config() {
  printf 'key\tvalue\n' > "$CONFIG"
  config_row rootDir "$ROOT_DIR"
  config_row platform "$(uname -s)"
  config_row only "$ONLY"
  config_row summaryDir "$SUMMARY_DIR"
  config_row aqliBin "$AQLI_BIN"
  config_row litert.model "$LITERT_MODEL"
  config_row gemma4Litert.model "$GEMMA4_LITERT_MODEL"
  config_row gguf.model "$GGUF_MODEL"
  config_row safetensor.model "$SAFETENSOR_MODEL"
  config_row prompt "$PROMPT"
  config_row litert.expected "$LITERT_EXPECTED"
  config_row gemma4Litert.expected "$GEMMA4_LITERT_EXPECTED"
  config_row gguf.expected "$GGUF_EXPECTED"
  config_row requireMetal "$([[ "$REQUIRE_METAL" -eq 1 ]] && printf true || printf false)"
  config_row warmOnly "$([[ "$WARM_ONLY" -eq 1 ]] && printf true || printf false)"
  config_row keepDaemon "$([[ "$KEEP_DAEMON" -eq 1 ]] && printf true || printf false)"
  config_row continueOnFailure "$CONTINUE_ON_FAILURE"
  config_row slowestLimit "$SLOWEST_LIMIT"
  config_row modelIndex "${AQLI_VERIFY_MODEL_INDEX:-${HOME}/.aqli/models/index.json}"
  config_row litert.bench "$LITERT_BENCH"
  config_row gemma4Litert.smoke "$GEMMA4_LITERT_SMOKE"
  config_row gguf.bench "$GGUF_BENCH"
  config_row ggufCompare.bench "$GGUF_COMPARE_BENCH"
  config_row safetensor.bench "$SAFETENSOR_BENCH"
  config_row litert.maxTokens "${AQLI_VERIFY_LITERT_MAX_TOKENS:-10}"
  config_row litert.warmThresholdMs "${AQLI_VERIFY_LITERT_WARM_THRESHOLD_MS:-1500}"
  config_row litert.coldThresholdMs "${AQLI_VERIFY_LITERT_COLD_THRESHOLD_MS:-60000}"
  config_row litert.warmupRuns "${AQLI_VERIFY_LITERT_WARMUP_RUNS:-1}"
  config_row litert.warmOnly "${AQLI_VERIFY_LITERT_WARM_ONLY:-false}"
  config_row litert.warmEngineInitThresholdMs "${AQLI_VERIFY_LITERT_WARM_ENGINE_INIT_THRESHOLD_MS:-}"
  config_row litert.warmFirstChunkThresholdMs "${AQLI_VERIFY_LITERT_WARM_FIRST_CHUNK_THRESHOLD_MS:-}"
  config_row litert.warmProfileTotalThresholdMs "${AQLI_VERIFY_LITERT_WARM_TOTAL_THRESHOLD_MS:-}"
  config_row gemma4Litert.maxTokens "${AQLI_VERIFY_GEMMA4_LITERT_MAX_TOKENS:-30}"
  config_row gemma4Litert.warmThresholdMs "${AQLI_VERIFY_GEMMA4_LITERT_WARM_THRESHOLD_MS:-1000}"
  config_row gemma4Litert.warmFirstChunkThresholdMs "${AQLI_VERIFY_GEMMA4_LITERT_WARM_FIRST_CHUNK_THRESHOLD_MS:-250}"
  config_row gemma4Litert.warmProfileTotalThresholdMs "${AQLI_VERIFY_GEMMA4_LITERT_WARM_TOTAL_THRESHOLD_MS:-1000}"
  config_row gemma4Litert.routeSource "$GEMMA4_LITERT_ROUTE_SOURCE"
  config_row gemma4Litert.routeResolveThresholdMs "$GEMMA4_LITERT_ROUTE_RESOLVE_THRESHOLD_MS"
  config_row gemma4Litert.coldRouteResolveThresholdMs "$GEMMA4_LITERT_COLD_ROUTE_RESOLVE_THRESHOLD_MS"
  config_row gemma4Litert.routeOnly "$GEMMA4_LITERT_ROUTE_ONLY"
  config_row gemma4Litert.routeSummaryFile "$GEMMA4_LITERT_ROUTE_SUMMARY_FILE"
  config_row gemma4Litert.routeBaselineStats "$GEMMA4_LITERT_ROUTE_BASELINE_STATS"
  config_row gemma4Litert.routeMaxRegressionMs "$GEMMA4_LITERT_ROUTE_MAX_REGRESSION_MS"
  config_row gemma4Litert.routeMaxRegressionPercent "$GEMMA4_LITERT_ROUTE_MAX_REGRESSION_PERCENT"
  config_row gguf.maxTokens "${AQLI_VERIFY_GGUF_MAX_TOKENS:-1}"
  config_row gguf.warmThresholdMs "${AQLI_VERIFY_GGUF_WARM_THRESHOLD_MS:-1500}"
  config_row gguf.coldThresholdMs "${AQLI_VERIFY_GGUF_COLD_THRESHOLD_MS:-60000}"
  config_row gguf.warmupRuns "${AQLI_VERIFY_GGUF_WARMUP_RUNS:-1}"
  config_row gguf.warmOnly "${AQLI_VERIFY_GGUF_WARM_ONLY:-false}"
  config_row gguf.promptCache "${AQLI_VERIFY_GGUF_PROMPT_CACHE:-true}"
  config_row gguf.warmTokenizeThresholdMs "${AQLI_VERIFY_GGUF_WARM_TOKENIZE_THRESHOLD_MS:-}"
  config_row gguf.warmPrefillThresholdMs "${AQLI_VERIFY_GGUF_WARM_PREFILL_THRESHOLD_MS:-}"
  config_row gguf.warmDecodeThresholdMs "${AQLI_VERIFY_GGUF_WARM_DECODE_THRESHOLD_MS:-}"
  config_row ggufCompare.maxTokens "${AQLI_VERIFY_GGUF_COMPARE_MAX_TOKENS:-10}"
  config_row ggufCompare.thresholdMs "${AQLI_VERIFY_GGUF_COMPARE_THRESHOLD_MS:-10000}"
  config_row ggufCompare.javaMatvecThresholdMs "${AQLI_VERIFY_GGUF_COMPARE_JAVA_MATVEC_THRESHOLD_MS:-50}"
  config_row ggufCompare.fallbackPrefillThresholdMs "${AQLI_VERIFY_GGUF_COMPARE_FALLBACK_PREFILL_THRESHOLD_MS:-}"
  config_row ggufCompare.fallbackDecodeThresholdMs "${AQLI_VERIFY_GGUF_COMPARE_FALLBACK_DECODE_THRESHOLD_MS:-}"
  config_row ggufCompare.javaReadyRegex "${AQLI_VERIFY_GGUF_COMPARE_JAVA_READY_REGEX:-}"
  config_row ggufCompare.javaConfigRegex "${AQLI_VERIFY_GGUF_COMPARE_JAVA_CONFIG_REGEX:-}"
  config_row ggufCompare.javaProbeRegex "${AQLI_VERIFY_GGUF_COMPARE_JAVA_PROBE_REGEX:-}"
  config_row ggufCompare.javaRefusal "${AQLI_VERIFY_GGUF_COMPARE_JAVA_REFUSAL:-true}"
  config_row safetensor.preset "$SAFETENSOR_PRESET"
  config_row safetensor.presetScript "$SAFETENSOR_PRESET_SCRIPT"
  config_row safetensor.maxTokens "${AQLI_VERIFY_SAFETENSOR_MAX_TOKENS:-2}"
  config_row safetensor.requireProfile "${AQLI_VERIFY_SAFETENSOR_REQUIRE_PROFILE:-true}"
  config_row safetensor.requireMetalPaths "$SAFETENSOR_REQUIRE_METAL_PATHS"
  config_row safetensor.rejectFallbackPaths "$SAFETENSOR_REJECT_FALLBACK_PATHS"
  config_row safetensor.minSpeedTps "$SAFETENSOR_MIN_SPEED_TPS"
  config_row safetensor.topStageThresholdMs "${AQLI_VERIFY_SAFETENSOR_TOP_STAGE_THRESHOLD_MS:-}"
  config_row safetensor.prefillThresholdMs "${AQLI_VERIFY_SAFETENSOR_PREFILL_THRESHOLD_MS:-}"
  config_row safetensor.decodeThresholdMs "${AQLI_VERIFY_SAFETENSOR_DECODE_THRESHOLD_MS:-}"
  config_row safetensor.tpotThresholdMs "${AQLI_VERIFY_SAFETENSOR_TPOT_THRESHOLD_MS:-}"
  config_row safetensor.samplingThresholdMs "${AQLI_VERIFY_SAFETENSOR_SAMPLING_THRESHOLD_MS:-}"
  config_row safetensor.argmaxThresholdMs "${AQLI_VERIFY_SAFETENSOR_ARGMAX_THRESHOLD_MS:-}"
  config_row safetensor.attentionThresholdMs "${AQLI_VERIFY_SAFETENSOR_ATTENTION_THRESHOLD_MS:-}"
  config_row safetensor.ffnThresholdMs "${AQLI_VERIFY_SAFETENSOR_FFN_THRESHOLD_MS:-}"
  config_row safetensor.logitsThresholdMs "${AQLI_VERIFY_SAFETENSOR_LOGITS_THRESHOLD_MS:-}"
  config_row safetensor.minCoreMetalCoverage "$SAFETENSOR_MIN_CORE_METAL_COVERAGE"
  config_row safetensor.minLinearMetalCoverage "$SAFETENSOR_MIN_LINEAR_METAL_COVERAGE"
  config_row safetensor.minLogitsMetalCoverage "$SAFETENSOR_MIN_LOGITS_METAL_COVERAGE"
  config_row safetensor.minFfnMetalCoverage "$SAFETENSOR_MIN_FFN_METAL_COVERAGE"
  config_row safetensor.minAttentionMetalCoverage "$SAFETENSOR_MIN_ATTENTION_METAL_COVERAGE"
  config_row safetensor.minArgmaxMetalCoverage "$SAFETENSOR_MIN_ARGMAX_METAL_COVERAGE"
  config_row safetensor.diagnosisScript "$SAFETENSOR_DIAGNOSIS_SCRIPT"
  config_row safetensor.diagnosisDir "$SAFETENSOR_DIAGNOSIS_DIR"
  config_row safetensor.diagnosisPaths "$SAFETENSOR_DIAGNOSIS_PATHS"
  config_row safetensor.compareScript "$SAFETENSOR_COMPARE_SCRIPT"
  config_row safetensor.baselineStagesRequest "$SAFETENSOR_BASELINE_STAGES_REQUEST"
  config_row safetensor.baselineRoot "$SAFETENSOR_BASELINE_ROOT"
  config_row safetensor.baselineName "$SAFETENSOR_BASELINE_NAME"
  config_row safetensor.baselineStages "$SAFETENSOR_BASELINE_STAGES"
  config_row safetensor.regressionDir "$SAFETENSOR_REGRESSION_DIR"
  config_row safetensor.maxRegressionPercent "$SAFETENSOR_MAX_REGRESSION_PERCENT"
  config_row safetensor.maxRegressionMs "$SAFETENSOR_MAX_REGRESSION_MS"
  config_row safetensor.minBaselineMs "$SAFETENSOR_MIN_BASELINE_MS"
  config_row safetensor.failPrimaryShift "$SAFETENSOR_FAIL_PRIMARY_SHIFT"
}

run_litert_gate() {
  local summary="${SUMMARY_DIR}/litert-fast.tsv"
  local cmd=(
    bash "$LITERT_BENCH"
    --aqli-bin "$AQLI_BIN"
    --model "$LITERT_MODEL"
    --prompt "$PROMPT"
    --expected "$LITERT_EXPECTED"
    --max-tokens "${AQLI_VERIFY_LITERT_MAX_TOKENS:-10}"
    --warm-threshold-ms "${AQLI_VERIFY_LITERT_WARM_THRESHOLD_MS:-1500}"
    --cold-threshold-ms "${AQLI_VERIFY_LITERT_COLD_THRESHOLD_MS:-60000}"
    --warmup-runs "${AQLI_VERIFY_LITERT_WARMUP_RUNS:-1}"
  )
  if [[ "$REQUIRE_METAL" -eq 1 ]]; then
    cmd+=(--require-metal)
  else
    cmd+=(--no-require-metal)
  fi
  if [[ -n "${AQLI_VERIFY_LITERT_WARM_ENGINE_INIT_THRESHOLD_MS:-}" ]]; then
    cmd+=(--warm-engine-init-threshold-ms "${AQLI_VERIFY_LITERT_WARM_ENGINE_INIT_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_LITERT_WARM_FIRST_CHUNK_THRESHOLD_MS:-}" ]]; then
    cmd+=(--warm-first-chunk-threshold-ms "${AQLI_VERIFY_LITERT_WARM_FIRST_CHUNK_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_LITERT_WARM_TOTAL_THRESHOLD_MS:-}" ]]; then
    cmd+=(--warm-profile-total-threshold-ms "${AQLI_VERIFY_LITERT_WARM_TOTAL_THRESHOLD_MS}")
  fi
  if [[ "$WARM_ONLY" -eq 1 ]] || truthy_env "${AQLI_VERIFY_LITERT_WARM_ONLY:-false}"; then
    cmd+=(--warm-only)
  fi
  cmd+=(--summary-file "$summary")
  if [[ "$KEEP_DAEMON" -eq 1 ]]; then
    cmd+=(--keep-daemon)
  fi
  echo ">>> LiteRT fast speed gate"
  record_manifest litert "$summary"
  run_gate_command litert "$summary" "${cmd[@]}"
}

run_gemma4_litert_gate() {
  local summary="${SUMMARY_DIR}/gemma4-litert-warm.tsv"
  if truthy_env "$GEMMA4_LITERT_ROUTE_ONLY"; then
    echo ">>> Gemma4 LiteRT route-summary contract gate"
    record_manifest gemma4-litert "$summary"
    run_summary_gate gemma4-litert "$summary" "$GEMMA4_LITERT_ROUTE_SUMMARY_FILE"
    return 0
  fi
  local cmd=(
    bash "$GEMMA4_LITERT_SMOKE"
    --aqli-bin "$AQLI_BIN"
    --model "$GEMMA4_LITERT_MODEL"
    --prompt "$PROMPT"
    --expected "$GEMMA4_LITERT_EXPECTED"
    --max-tokens "${AQLI_VERIFY_GEMMA4_LITERT_MAX_TOKENS:-30}"
    --warm-threshold-ms "${AQLI_VERIFY_GEMMA4_LITERT_WARM_THRESHOLD_MS:-1000}"
    --warm-first-chunk-threshold-ms "${AQLI_VERIFY_GEMMA4_LITERT_WARM_FIRST_CHUNK_THRESHOLD_MS:-250}"
    --warm-profile-total-threshold-ms "${AQLI_VERIFY_GEMMA4_LITERT_WARM_TOTAL_THRESHOLD_MS:-1000}"
  )
  if [[ "$REQUIRE_METAL" -eq 1 ]]; then
    cmd+=(--require-metal)
  else
    cmd+=(--no-require-metal)
  fi
  if [[ "$KEEP_DAEMON" -eq 1 ]]; then
    cmd+=(--keep-daemon)
  fi
  cmd+=(-- --summary-file "$summary")
  echo ">>> Gemma4 LiteRT warm daemon speed gate"
  record_manifest gemma4-litert "$summary"
  run_gate_command gemma4-litert "$summary" "${cmd[@]}"
}

run_gguf_gate() {
  local summary="${SUMMARY_DIR}/gguf-fast.tsv"
  local cmd=(
    bash "$GGUF_BENCH"
    --aqli-bin "$AQLI_BIN"
    --model "$GGUF_MODEL"
    --prompt "$PROMPT"
    --expected "$GGUF_EXPECTED"
    --max-tokens "${AQLI_VERIFY_GGUF_MAX_TOKENS:-1}"
    --warm-threshold-ms "${AQLI_VERIFY_GGUF_WARM_THRESHOLD_MS:-1500}"
    --cold-threshold-ms "${AQLI_VERIFY_GGUF_COLD_THRESHOLD_MS:-60000}"
    --warmup-runs "${AQLI_VERIFY_GGUF_WARMUP_RUNS:-1}"
  )
  if [[ "$REQUIRE_METAL" -eq 1 ]]; then
    cmd+=(--require-metal)
  else
    cmd+=(--no-require-metal)
  fi
  case "$(printf '%s' "${AQLI_VERIFY_GGUF_PROMPT_CACHE:-true}" | tr '[:upper:]' '[:lower:]')" in
    0|false|no|off) ;;
    *) cmd+=(--require-prompt-cache) ;;
  esac
  if [[ -n "${AQLI_VERIFY_GGUF_WARM_TOKENIZE_THRESHOLD_MS:-}" ]]; then
    cmd+=(--warm-tokenize-threshold-ms "${AQLI_VERIFY_GGUF_WARM_TOKENIZE_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_GGUF_WARM_PREFILL_THRESHOLD_MS:-}" ]]; then
    cmd+=(--warm-prefill-threshold-ms "${AQLI_VERIFY_GGUF_WARM_PREFILL_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_GGUF_WARM_DECODE_THRESHOLD_MS:-}" ]]; then
    cmd+=(--warm-decode-threshold-ms "${AQLI_VERIFY_GGUF_WARM_DECODE_THRESHOLD_MS}")
  fi
  if [[ "$WARM_ONLY" -eq 1 ]] || truthy_env "${AQLI_VERIFY_GGUF_WARM_ONLY:-false}"; then
    cmd+=(--warm-only)
  fi
  cmd+=(--summary-file "$summary")
  if [[ "$KEEP_DAEMON" -eq 1 ]]; then
    cmd+=(--keep-daemon)
  fi
  echo ">>> GGUF fast speed gate"
  record_manifest gguf "$summary"
  run_gate_command gguf "$summary" "${cmd[@]}"
}

run_gguf_compare_gate() {
  local summary="${SUMMARY_DIR}/gguf-compare.tsv"
  local cmd=(
    bash "$GGUF_COMPARE_BENCH"
    --aqli-bin "$AQLI_BIN"
    --model "$GGUF_MODEL"
    --prompt "$PROMPT"
    --expected "$GGUF_EXPECTED"
    --max-tokens "${AQLI_VERIFY_GGUF_COMPARE_MAX_TOKENS:-10}"
    --threshold-ms "${AQLI_VERIFY_GGUF_COMPARE_THRESHOLD_MS:-10000}"
    --java-matvec-threshold-ms "${AQLI_VERIFY_GGUF_COMPARE_JAVA_MATVEC_THRESHOLD_MS:-50}"
  )
  if [[ "$REQUIRE_METAL" -eq 1 ]]; then
    cmd+=(--require-metal)
  else
    cmd+=(--no-require-metal)
  fi
  if [[ -n "${AQLI_VERIFY_GGUF_COMPARE_FALLBACK_PREFILL_THRESHOLD_MS:-}" ]]; then
    cmd+=(--fallback-prefill-threshold-ms "${AQLI_VERIFY_GGUF_COMPARE_FALLBACK_PREFILL_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_GGUF_COMPARE_FALLBACK_DECODE_THRESHOLD_MS:-}" ]]; then
    cmd+=(--fallback-decode-threshold-ms "${AQLI_VERIFY_GGUF_COMPARE_FALLBACK_DECODE_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_GGUF_COMPARE_JAVA_READY_REGEX:-}" ]]; then
    cmd+=(--java-ready-regex "${AQLI_VERIFY_GGUF_COMPARE_JAVA_READY_REGEX}")
  fi
  if [[ -n "${AQLI_VERIFY_GGUF_COMPARE_JAVA_CONFIG_REGEX:-}" ]]; then
    cmd+=(--java-config-regex "${AQLI_VERIFY_GGUF_COMPARE_JAVA_CONFIG_REGEX}")
  fi
  if [[ -n "${AQLI_VERIFY_GGUF_COMPARE_JAVA_PROBE_REGEX:-}" ]]; then
    cmd+=(--java-probe-regex "${AQLI_VERIFY_GGUF_COMPARE_JAVA_PROBE_REGEX}")
  fi
  case "$(printf '%s' "${AQLI_VERIFY_GGUF_COMPARE_JAVA_REFUSAL:-true}" | tr '[:upper:]' '[:lower:]')" in
    0|false|no|off) cmd+=(--no-verify-java-refusal) ;;
    *) cmd+=(--verify-java-refusal) ;;
  esac
  cmd+=(--summary-file "$summary")
  echo ">>> GGUF Java-native vs llama.cpp speed gate"
  record_manifest gguf-compare "$summary"
  run_gate_command gguf-compare "$summary" "${cmd[@]}"
}

run_safetensor_gate() {
  local summary="${SUMMARY_DIR}/safetensor-fast.tsv"
  local out_dir="${SUMMARY_DIR}/safetensor-bench"
  local cmd=(
    bash "$SAFETENSOR_BENCH"
    --aqli-bin "$AQLI_BIN"
    --model "$SAFETENSOR_MODEL"
    --out-dir "$out_dir"
    --label safetensor-fast
    --det-prompt "$PROMPT"
    --max-tokens "${AQLI_VERIFY_SAFETENSOR_MAX_TOKENS:-2}"
    --quick
    --profile
    --summary-file "$summary"
  )
  if truthy_env "${AQLI_VERIFY_SAFETENSOR_REQUIRE_PROFILE:-true}"; then
    cmd+=(--require-profile)
  fi
  if [[ "$REQUIRE_METAL" -eq 1 ]]; then
    cmd+=(--require-metal)
  fi
  if truthy_env "$SAFETENSOR_REQUIRE_METAL_PATHS"; then
    cmd+=(--require-metal-paths)
  fi
  if truthy_env "$SAFETENSOR_REJECT_FALLBACK_PATHS"; then
    cmd+=(--reject-fallback-paths)
  fi
  if [[ -n "$SAFETENSOR_MIN_SPEED_TPS" ]]; then
    cmd+=(--min-speed-tps "$SAFETENSOR_MIN_SPEED_TPS")
  fi
  if [[ -n "${AQLI_VERIFY_SAFETENSOR_TOP_STAGE_THRESHOLD_MS:-}" ]]; then
    cmd+=(--max-top-stage-ms "${AQLI_VERIFY_SAFETENSOR_TOP_STAGE_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_SAFETENSOR_PREFILL_THRESHOLD_MS:-}" ]]; then
    cmd+=(--max-prefill-ms "${AQLI_VERIFY_SAFETENSOR_PREFILL_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_SAFETENSOR_DECODE_THRESHOLD_MS:-}" ]]; then
    cmd+=(--max-decode-ms "${AQLI_VERIFY_SAFETENSOR_DECODE_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_SAFETENSOR_TPOT_THRESHOLD_MS:-}" ]]; then
    cmd+=(--max-tpot-ms "${AQLI_VERIFY_SAFETENSOR_TPOT_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_SAFETENSOR_SAMPLING_THRESHOLD_MS:-}" ]]; then
    cmd+=(--max-sampling-ms "${AQLI_VERIFY_SAFETENSOR_SAMPLING_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_SAFETENSOR_ARGMAX_THRESHOLD_MS:-}" ]]; then
    cmd+=(--max-argmax-ms "${AQLI_VERIFY_SAFETENSOR_ARGMAX_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_SAFETENSOR_ATTENTION_THRESHOLD_MS:-}" ]]; then
    cmd+=(--max-attention-ms "${AQLI_VERIFY_SAFETENSOR_ATTENTION_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_SAFETENSOR_FFN_THRESHOLD_MS:-}" ]]; then
    cmd+=(--max-ffn-ms "${AQLI_VERIFY_SAFETENSOR_FFN_THRESHOLD_MS}")
  fi
  if [[ -n "${AQLI_VERIFY_SAFETENSOR_LOGITS_THRESHOLD_MS:-}" ]]; then
    cmd+=(--max-logits-ms "${AQLI_VERIFY_SAFETENSOR_LOGITS_THRESHOLD_MS}")
  fi
  if [[ -n "$SAFETENSOR_MIN_CORE_METAL_COVERAGE" ]]; then
    cmd+=(--min-core-metal-coverage "$SAFETENSOR_MIN_CORE_METAL_COVERAGE")
  fi
  if [[ -n "$SAFETENSOR_MIN_LINEAR_METAL_COVERAGE" ]]; then
    cmd+=(--min-linear-metal-coverage "$SAFETENSOR_MIN_LINEAR_METAL_COVERAGE")
  fi
  if [[ -n "$SAFETENSOR_MIN_LOGITS_METAL_COVERAGE" ]]; then
    cmd+=(--min-logits-metal-coverage "$SAFETENSOR_MIN_LOGITS_METAL_COVERAGE")
  fi
  if [[ -n "$SAFETENSOR_MIN_FFN_METAL_COVERAGE" ]]; then
    cmd+=(--min-ffn-metal-coverage "$SAFETENSOR_MIN_FFN_METAL_COVERAGE")
  fi
  if [[ -n "$SAFETENSOR_MIN_ATTENTION_METAL_COVERAGE" ]]; then
    cmd+=(--min-attention-metal-coverage "$SAFETENSOR_MIN_ATTENTION_METAL_COVERAGE")
  fi
  if [[ -n "$SAFETENSOR_MIN_ARGMAX_METAL_COVERAGE" ]]; then
    cmd+=(--min-argmax-metal-coverage "$SAFETENSOR_MIN_ARGMAX_METAL_COVERAGE")
  fi
  echo ">>> Safetensor profile/Metal speed gate"
  record_manifest safetensor "$summary"
  run_gate_command safetensor "$summary" "${cmd[@]}"
}

apply_safetensor_preset
resolve_safetensor_baseline
write_config

if should_run litert; then
  run_litert_gate
else
  record_skip litert "$(skip_reason litert)"
fi
if should_run gemma4-litert; then
  run_gemma4_litert_gate
else
  record_skip gemma4-litert "$(skip_reason gemma4-litert)"
fi
if should_run gguf; then
  run_gguf_gate
else
  record_skip gguf "$(skip_reason gguf)"
fi
if should_run gguf-compare; then
  run_gguf_compare_gate
else
  record_skip gguf-compare "$(skip_reason gguf-compare)"
fi
if should_run safetensor; then
  run_safetensor_gate
else
  record_skip safetensor "$(skip_reason safetensor)"
fi

if [[ "$RAN_ANY" -ne 1 ]]; then
  echo "WARN: no fast speed gates matched --only=${ONLY}; summaryDir=${SUMMARY_DIR}" >&2
fi
if [[ "$FINAL_EXIT_CODE" -ne 0 ]]; then
  echo "FAIL: one or more fast speed gates failed; artifacts are in ${SUMMARY_DIR}" >&2
  print_artifact_footer
  exit "$FINAL_EXIT_CODE"
fi
echo "PASS: fast speed gates passed"
print_artifact_footer
