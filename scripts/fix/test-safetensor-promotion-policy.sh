#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-safetensor-promotion-policy.XXXXXX")"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

PASS_DIR="$TMP_DIR/pass"
mkdir -p "$PASS_DIR"
cat > "$PASS_DIR/decision.json" <<'JSON'
{
  "schemaVersion": 1,
  "generatedAt": "2026-06-10T00:00:00Z",
  "model": "fake-gemma4-12b",
  "summary": {
    "status": "pass",
    "reason": null,
    "recommendation": "promote-current-with-watchlist",
    "samples": {
      "requested": 2,
      "total": 2,
      "passed": 2,
      "failed": 0,
      "error": 0
    },
    "metrics": {
      "failed": 0,
      "unstable": 0
    },
    "gates": {
      "aggregateFailures": 0,
      "p95RegressionFailures": 0,
      "unstableFailures": 0
    }
  },
  "policy": {
    "canPromote": true,
    "action": "promote-with-watchlist"
  },
  "sampleDecisions": [
    {
      "sample": "sample-01",
      "compareAction": "promote-with-watchlist"
    },
    {
      "sample": "sample-02",
      "compareAction": "promote-with-watchlist"
    }
  ],
  "artifacts": {
    "decision": "/tmp/pass/decision.json",
    "sampleDecisions": "/tmp/pass/sample-decisions.tsv"
  }
}
JSON

bash "$ROOT_DIR/scripts/safetensor-promotion-policy.sh" \
  --decision "$PASS_DIR/decision.json"

if [[ ! -f "$PASS_DIR/promotion-policy.json" || ! -f "$PASS_DIR/promotion-policy.tsv" ]]; then
  echo "Expected default promotion policy artifacts beside decision JSON" >&2
  find "$PASS_DIR" -maxdepth 1 -type f -print >&2
  exit 1
fi
if ! jq -e '
      .schemaVersion == 1
      and .model == "fake-gemma4-12b"
      and .sourceDecision == "/tmp/pass/decision.json"
      and .action == "promote-with-watchlist"
      and .canPromote == true
      and .requiresWatchlist == true
      and .reasons == ["watchlist-required"]
      and .guardrails.samples == 2
      and .sampleDecisionCounts.watchlist == 2
      and .sampleDecisionCounts.missing == 0
      and .artifacts.sampleDecisions == "/tmp/pass/sample-decisions.tsv"
    ' "$PASS_DIR/promotion-policy.json" >/dev/null \
    || ! grep -qx $'key\tvalue' "$PASS_DIR/promotion-policy.tsv" \
    || ! grep -qx $'action\tpromote-with-watchlist' "$PASS_DIR/promotion-policy.tsv" \
    || ! grep -qx $'canPromote\ttrue' "$PASS_DIR/promotion-policy.tsv" \
    || ! grep -qx $'requiresWatchlist\ttrue' "$PASS_DIR/promotion-policy.tsv" \
    || ! grep -qx $'reasons\twatchlist-required' "$PASS_DIR/promotion-policy.tsv" \
    || ! grep -qx $'watchlistSampleDecisions\t2' "$PASS_DIR/promotion-policy.tsv"; then
  echo "Expected passing promotion policy output" >&2
  cat "$PASS_DIR/promotion-policy.json" >&2
  cat "$PASS_DIR/promotion-policy.tsv" >&2
  exit 1
fi

bash "$ROOT_DIR/scripts/safetensor-promotion-policy.sh" \
  --decision "$PASS_DIR/decision.json" \
  --out "$PASS_DIR/gated-policy.json" \
  --tsv-out "$PASS_DIR/gated-policy.tsv" \
  --require-can-promote \
  --require-action promote,promote-with-watchlist

if ! grep -qx $'action\tpromote-with-watchlist' "$PASS_DIR/gated-policy.tsv" \
    || ! grep -qx $'canPromote\ttrue' "$PASS_DIR/gated-policy.tsv"; then
  echo "Expected promotable policy gate to pass and write artifacts" >&2
  cat "$PASS_DIR/gated-policy.tsv" >&2
  exit 1
fi

FAIL_DIR="$TMP_DIR/fail"
mkdir -p "$FAIL_DIR/out"
cat > "$FAIL_DIR/decision.json" <<'JSON'
{
  "schemaVersion": 1,
  "generatedAt": "2026-06-10T00:00:00Z",
  "model": "fake-gemma4-12b",
  "summary": {
    "status": "fail",
    "reason": "p95-regression",
    "recommendation": "reject-current",
    "samples": {
      "requested": 2,
      "total": 2,
      "passed": 2,
      "failed": 0,
      "error": 0
    },
    "metrics": {
      "failed": 0,
      "unstable": 1
    },
    "gates": {
      "aggregateFailures": 1,
      "p95RegressionFailures": 1,
      "unstableFailures": 0
    }
  },
  "policy": {
    "canPromote": false,
    "action": "reject"
  },
  "sampleDecisions": [
    {
      "sample": "sample-01",
      "compareAction": null
    },
    {
      "sample": "sample-02",
      "compareAction": null
    }
  ],
  "artifacts": {
    "decision": "/tmp/fail/decision.json"
  }
}
JSON

bash "$ROOT_DIR/scripts/safetensor-promotion-policy.sh" \
  --decision "$FAIL_DIR/decision.json" \
  --out "$FAIL_DIR/out/policy.json" \
  --tsv-out "$FAIL_DIR/out/policy.tsv"

if ! jq -e '
      .action == "reject"
      and .canPromote == false
      and .requiresWatchlist == false
      and (.reasons | index("aggregate-status-failed") != null)
      and (.reasons | index("aggregate-gate-failures") != null)
      and (.reasons | index("p95-regression") != null)
      and (.reasons | index("unstable-metrics-present") != null)
      and .guardrails.aggregateGateFailures == 1
      and .guardrails.p95RegressionGateFailures == 1
      and .sampleDecisionCounts.missing == 2
    ' "$FAIL_DIR/out/policy.json" >/dev/null \
    || ! grep -qx $'action\treject' "$FAIL_DIR/out/policy.tsv" \
    || ! grep -qx $'canPromote\tfalse' "$FAIL_DIR/out/policy.tsv" \
    || ! grep -qx $'aggregateGateFailures\t1' "$FAIL_DIR/out/policy.tsv" \
    || ! grep -qx $'missingSampleDecisions\t2' "$FAIL_DIR/out/policy.tsv"; then
  echo "Expected rejecting promotion policy output" >&2
  cat "$FAIL_DIR/out/policy.json" >&2
  cat "$FAIL_DIR/out/policy.tsv" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/safetensor-promotion-policy.sh" \
    --decision "$FAIL_DIR/decision.json" \
    --out "$FAIL_DIR/out/require-can-promote.json" \
    --tsv-out "$FAIL_DIR/out/require-can-promote.tsv" \
    --require-can-promote > "$FAIL_DIR/out/require-can-promote.out" 2> "$FAIL_DIR/out/require-can-promote.err"; then
  echo "Expected require-can-promote gate to fail rejected policy" >&2
  cat "$FAIL_DIR/out/require-can-promote.out" >&2
  cat "$FAIL_DIR/out/require-can-promote.err" >&2
  exit 1
fi
if [[ ! -f "$FAIL_DIR/out/require-can-promote.json" || ! -f "$FAIL_DIR/out/require-can-promote.tsv" ]] \
    || ! grep -qx 'Promotion policy requires canPromote=true but got canPromote=false, action=reject' "$FAIL_DIR/out/require-can-promote.err"; then
  echo "Expected require-can-promote gate to write artifacts before failing" >&2
  find "$FAIL_DIR/out" -maxdepth 1 -type f -print >&2
  cat "$FAIL_DIR/out/require-can-promote.err" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/safetensor-promotion-policy.sh" \
    --decision "$FAIL_DIR/decision.json" \
    --out "$FAIL_DIR/out/require-action.json" \
    --tsv-out "$FAIL_DIR/out/require-action.tsv" \
    --require-action promote,promote-with-watchlist > "$FAIL_DIR/out/require-action.out" 2> "$FAIL_DIR/out/require-action.err"; then
  echo "Expected require-action gate to fail rejected policy" >&2
  cat "$FAIL_DIR/out/require-action.out" >&2
  cat "$FAIL_DIR/out/require-action.err" >&2
  exit 1
fi
if [[ ! -f "$FAIL_DIR/out/require-action.json" || ! -f "$FAIL_DIR/out/require-action.tsv" ]] \
    || ! grep -qx "Promotion policy action 'reject' is not allowed by --require-action 'promote,promote-with-watchlist'" "$FAIL_DIR/out/require-action.err"; then
  echo "Expected require-action gate to write artifacts before failing" >&2
  find "$FAIL_DIR/out" -maxdepth 1 -type f -print >&2
  cat "$FAIL_DIR/out/require-action.err" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/safetensor-promotion-policy.sh" \
    --decision "$TMP_DIR/missing.json" > "$TMP_DIR/missing.out" 2> "$TMP_DIR/missing.err"; then
  echo "Expected missing decision JSON to fail" >&2
  cat "$TMP_DIR/missing.out" >&2
  cat "$TMP_DIR/missing.err" >&2
  exit 1
fi
if ! grep -qx "Decision JSON not found: $TMP_DIR/missing.json" "$TMP_DIR/missing.err"; then
  echo "Expected missing decision diagnostic" >&2
  cat "$TMP_DIR/missing.err" >&2
  exit 1
fi

printf 'safetensor promotion policy test passed\n'
