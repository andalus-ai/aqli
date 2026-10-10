#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: safetensor-promotion-policy.sh --decision DECISION_JSON [options]

Evaluate a repeat safetensor benchmark decision.json and write a compact
promotion policy for CI and release automation.

Options:
  --decision PATH   Repeat decision.json artifact (required)
  --out PATH        Promotion policy JSON output
                    (default: beside decision.json as promotion-policy.json)
  --tsv-out PATH    Promotion policy TSV output
                    (default: beside JSON output as promotion-policy.tsv)
  --require-can-promote
                    Exit non-zero unless the policy has canPromote=true
  --require-action CSV
                    Exit non-zero unless the policy action is one of CSV
                    (for example: promote,promote-with-watchlist)
  --help            Show this help
USAGE
}

DECISION_JSON=""
OUT=""
TSV_OUT=""
REQUIRE_CAN_PROMOTE=0
REQUIRE_ACTIONS=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --decision) DECISION_JSON="$2"; shift 2 ;;
    --decision=*) DECISION_JSON="${1#*=}"; shift ;;
    --out) OUT="$2"; shift 2 ;;
    --out=*) OUT="${1#*=}"; shift ;;
    --tsv-out) TSV_OUT="$2"; shift 2 ;;
    --tsv-out=*) TSV_OUT="${1#*=}"; shift ;;
    --require-can-promote) REQUIRE_CAN_PROMOTE=1; shift ;;
    --require-action) REQUIRE_ACTIONS="$2"; shift 2 ;;
    --require-action=*) REQUIRE_ACTIONS="${1#*=}"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$DECISION_JSON" ]]; then
  echo "--decision is required" >&2
  usage
  exit 2
fi
if [[ ! -f "$DECISION_JSON" ]]; then
  echo "Decision JSON not found: $DECISION_JSON" >&2
  exit 2
fi
for cmd in jq mkdir; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

decision_dir="${DECISION_JSON%/*}"
if [[ "$decision_dir" == "$DECISION_JSON" ]]; then
  decision_dir="."
fi
OUT="${OUT:-${decision_dir}/promotion-policy.json}"
if [[ -z "$TSV_OUT" ]]; then
  if [[ "$OUT" == *.json ]]; then
    TSV_OUT="${OUT%.json}.tsv"
  else
    TSV_OUT="${OUT}.tsv"
  fi
fi

out_parent="${OUT%/*}"
if [[ "$out_parent" != "$OUT" ]]; then
  mkdir -p "$out_parent"
fi
tsv_parent="${TSV_OUT%/*}"
if [[ "$tsv_parent" != "$TSV_OUT" ]]; then
  mkdir -p "$tsv_parent"
fi

jq '
  def reason_list:
    [
      (if .summary.status == "fail" then "aggregate-status-failed" else empty end),
      (if (.summary.reason // "") != "" then .summary.reason else empty end),
      (if (.summary.samples.failed // 0) > 0 then "sample-failures" else empty end),
      (if (.summary.samples.error // 0) > 0 then "sample-errors" else empty end),
      (if (.summary.gates.aggregateFailures // 0) > 0 then "aggregate-gate-failures" else empty end),
      (if (.summary.metrics.unstable // 0) > 0 then "unstable-metrics-present" else empty end),
      (if .policy.action == "promote-with-watchlist" then "watchlist-required" else empty end),
      (if .policy.action == "promote" then "all-guardrails-passed" else empty end),
      (if .policy.action == "hold" then "hold-recommended" else empty end),
      (if .policy.action == "collect-more-samples" then "insufficient-signal" else empty end)
    ] | unique;
  {
    schemaVersion: 1,
    generatedAt,
    model,
    sourceDecision: .artifacts.decision,
    action: .policy.action,
    canPromote: .policy.canPromote,
    requiresWatchlist: (.policy.action == "promote-with-watchlist"),
    recommendation: .summary.recommendation,
    status: .summary.status,
    reasons: reason_list,
    guardrails: {
      requestedSamples: .summary.samples.requested,
      samples: .summary.samples.total,
      passedSamples: .summary.samples.passed,
      failedSamples: .summary.samples.failed,
      errorSamples: .summary.samples.error,
      failedMetrics: .summary.metrics.failed,
      unstableMetrics: .summary.metrics.unstable,
      aggregateGateFailures: .summary.gates.aggregateFailures,
      p95RegressionGateFailures: .summary.gates.p95RegressionFailures,
      unstableGateFailures: .summary.gates.unstableFailures
    },
    sampleDecisionCounts: {
      promote: ([.sampleDecisions[]? | select(.compareAction == "promote")] | length),
      watchlist: ([.sampleDecisions[]? | select(.compareAction == "promote-with-watchlist")] | length),
      hold: ([.sampleDecisions[]? | select(.compareAction == "hold")] | length),
      reject: ([.sampleDecisions[]? | select(.compareAction == "reject")] | length),
      collectMore: ([.sampleDecisions[]? | select(.compareAction == "collect-more-samples")] | length),
      missing: ([.sampleDecisions[]? | select(.compareAction == null)] | length)
    },
    artifacts
  }
' "$DECISION_JSON" > "$OUT"

jq -r '
  [
    ["key", "value"],
    ["action", .action],
    ["canPromote", (.canPromote | tostring)],
    ["requiresWatchlist", (.requiresWatchlist | tostring)],
    ["status", .status],
    ["recommendation", .recommendation],
    ["reasons", (.reasons | join("; "))],
    ["samples", (.guardrails.samples | tostring)],
    ["failedSamples", (.guardrails.failedSamples | tostring)],
    ["errorSamples", (.guardrails.errorSamples | tostring)],
    ["aggregateGateFailures", (.guardrails.aggregateGateFailures | tostring)],
    ["unstableMetrics", (.guardrails.unstableMetrics | tostring)],
    ["watchlistSampleDecisions", (.sampleDecisionCounts.watchlist | tostring)],
    ["missingSampleDecisions", (.sampleDecisionCounts.missing | tostring)],
    ["sourceDecision", .sourceDecision]
  ] | .[] | @tsv
' "$OUT" > "$TSV_OUT"

policy_action="$(jq -r '.action // ""' "$OUT")"
policy_can_promote="$(jq -r '.canPromote // false' "$OUT")"

if (( REQUIRE_CAN_PROMOTE == 1 )) && [[ "$policy_can_promote" != "true" ]]; then
  echo "Promotion policy requires canPromote=true but got canPromote=${policy_can_promote}, action=${policy_action}" >&2
  exit 1
fi

REQUIRE_ACTIONS="${REQUIRE_ACTIONS//[[:space:]]/}"
if [[ -n "$REQUIRE_ACTIONS" ]]; then
  previous_ifs="$IFS"
  IFS=","
  action_allowed=0
  for required_action in $REQUIRE_ACTIONS; do
    if [[ "$required_action" == "$policy_action" ]]; then
      action_allowed=1
      break
    fi
  done
  IFS="$previous_ifs"
  if (( action_allowed == 0 )); then
    echo "Promotion policy action '${policy_action}' is not allowed by --require-action '${REQUIRE_ACTIONS}'" >&2
    exit 1
  fi
fi
