#!/usr/bin/env bash
# =============================================================================
# check-boundaries.sh — Aqli Architectural Boundary Enforcement
# =============================================================================
# Runs four static-analysis checks against the Aqli source tree to enforce
# module-level architectural boundaries.  Each check emits ✅ / ❌.  All
# failures are collected and the script exits non-zero only at the very end,
# so every check is always reported.
#
# Usage:
#   ./scripts/check-boundaries.sh          # from anywhere
#   bash scripts/check-boundaries.sh       # explicit interpreter
# =============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# Locate the Aqli root regardless of where the caller invoked the script
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AQLI_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${AQLI_ROOT}"

# ---------------------------------------------------------------------------
# Color helpers (tput when available, plain fallback)
# ---------------------------------------------------------------------------
if command -v tput &>/dev/null && tput setaf 1 &>/dev/null; then
  RED="$(tput setaf 1)"
  GREEN="$(tput setaf 2)"
  YELLOW="$(tput setaf 3)"
  BOLD="$(tput bold)"
  RESET="$(tput sgr0)"
else
  RED="" GREEN="" YELLOW="" BOLD="" RESET=""
fi

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
PASS_ICON="✅"
FAIL_ICON="❌"
WARN_ICON="⚠️ "

pass()  { echo "${GREEN}${PASS_ICON}  $*${RESET}"; }
fail()  { echo "${RED}${FAIL_ICON}  $*${RESET}"; }
warn()  { echo "${YELLOW}${WARN_ICON} $*${RESET}"; }
header(){ echo "${BOLD}$*${RESET}"; }

# Tracks how many checks pass/fail
TOTAL=0
PASSED=0
FAILURES=()

run_check() {
  # run_check <description> <command_that_returns_output_on_violation>
  local description="$1"
  shift
  TOTAL=$(( TOTAL + 1 ))

  local output
  # 'grep' exits 1 when nothing matches — that is actually a PASS here,
  # so we must not let set -e kill us on an empty result.
  output=$("$@" 2>/dev/null || true)

  if [[ -z "${output}" ]]; then
    pass "${description}"
    PASSED=$(( PASSED + 1 ))
  else
    fail "${description}"
    # Indent and print every offending line in red
    while IFS= read -r line; do
      echo "    ${RED}${line}${RESET}"
    done <<< "${output}"
    FAILURES+=("${description}")
  fi
}

# =============================================================================
# Banner
# =============================================================================
echo
echo "${BOLD}=============================================================${RESET}"
echo "${BOLD}  Aqli — Architectural Boundary Checks${RESET}"
echo "${BOLD}  Root: ${AQLI_ROOT}${RESET}"
echo "${BOLD}=============================================================${RESET}"
echo

# =============================================================================
# Check 1 — No framework module may depend on runtime modules
# =============================================================================
# Framework layers (core/sdk/spi/plugin/observability) must not pull in
# runtime-side modules (runner, repository, optimization, adapter, runtime).
header "Check 1: No framework module depends on runtime modules"
run_check \
  "framework → runtime dependency isolation" \
  bash -c "grep -rn 'project(\":' framework/ --include='*.kts' \
    | grep -vE '^[^:]+://|\bspi:|\bcore:|\bsdk:|\bplugin:|\bobservability:' \
    | grep -E 'runner:|repository:|optimization:|adapter:|runtime:'"

# =============================================================================
# Check 2 — No Aqli source file declares an Aljabr package
# =============================================================================
# Package declarations must not bleed across family namespaces.
header "Check 2: No Aqli source declares an Aljabr package"
run_check \
  "Aljabr package leakage into Aqli source" \
  grep -rn '^package tech\.kayys\.aljabr' --include='*.java' framework/ runtime/

# =============================================================================
# Check 3 — No stale core:aqli-model-repo-* references
# =============================================================================
# These coordinates were retired; any surviving reference is a build error.
header "Check 3: No stale core:aqli-model-repo-* references"
run_check \
  "Stale core:aqli-model-repo references" \
  grep -rn 'core:aqli-model-repo' --include='*.kts' .

# =============================================================================
# Check 4 — No shadow runtime.data imports
# =============================================================================
# Internal runtime.data types must not be imported outside the runtime layer.
header "Check 4: No shadow runtime.data imports in runtime source"
run_check \
  "Shadow tech.kayys.aqli.runtime.data imports" \
  grep -rn 'tech\.kayys\.aqli\.runtime\.data' --include='*.java' runtime/

# =============================================================================
# Summary
# =============================================================================
echo
echo "${BOLD}=============================================================${RESET}"
if [[ ${#FAILURES[@]} -eq 0 ]]; then
  echo "${GREEN}${BOLD}  ${PASSED}/${TOTAL} checks passed — all boundaries clean.${RESET}"
  echo "${BOLD}=============================================================${RESET}"
  echo
  exit 0
else
  echo "${RED}${BOLD}  ${PASSED}/${TOTAL} checks passed — ${#FAILURES[@]} boundary violation(s) found:${RESET}"
  for f in "${FAILURES[@]}"; do
    echo "${RED}    • ${f}${RESET}"
  done
  echo "${BOLD}=============================================================${RESET}"
  echo
  exit 1
fi
