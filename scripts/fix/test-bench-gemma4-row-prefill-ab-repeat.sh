#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-gemma4-row-prefill-ab-repeat.XXXXXX")"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

AQLI_BIN="$TMP_DIR/aqli"
cat > "$AQLI_BIN" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

printf 'Platform: Metal\n'
printf '✓ GPU acceleration enabled (Apple M4)\n'
printf 'Model: fake-gemma4-12b\n'
printf 'Provider: safetensor\n'
printf 'AQLI_JAVA_OPTS=%s\n' "${AQLI_JAVA_OPTS:-}"
printf '%s\n' '--------------------------------------------------'
printf 'Jakarta is in Indonesia.\n'

if [[ "${AQLI_JAVA_OPTS:-}" == *"-Daqli.safetensor.prefer_metal_matvec_ffn_prefill_rows=true"* ]]; then
  printf '\n[PROFILE] backend=metal mode=direct load=1.00ms tokenize=2.00ms session=3.00ms ttft=80.00ms engine_ttft=76.00ms prefill=50.00ms decode=52.00ms tpot=26.00ms sampling=2.00ms argmax=1.20ms attention=22.00ms ffn=40.00ms logits=5.00ms steps=2 linear_paths={attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2} logits_paths={metal_matmul_f16=2} ffn_paths={matvec-gated-ffn-prefill-rows:accept:geglu:native_bf16=true:native_rows=12:variant=x4=2} attention_paths={paged_metal=2} argmax_paths={native_argmax_f32=2} ffn_strategy=row_prefill_matvec_active path_status={core=metal, linear=metal, logits=metal, ffn=metal, attention=metal, argmax=metal} path_coverage={core=12/12, linear=6/6, logits=2/2, ffn=2/2, attention=2/2, argmax=2/2}\n'
  printf '[Stream updates: 2, Duration: 35,00s, Speed: 12,00 t/s]\n'
else
  printf '\n[PROFILE] backend=metal mode=direct load=1.00ms tokenize=2.00ms session=3.00ms ttft=100.00ms engine_ttft=96.00ms prefill=80.00ms decode=50.00ms tpot=25.00ms sampling=2.00ms argmax=1.00ms attention=20.00ms ffn=60.00ms logits=5.00ms steps=2 linear_paths={attn_q_proj:metal_matmul_f16=2, ffn_down:metal_matmul_f16=2, logits:metal_matmul_f16=2} logits_paths={metal_matmul_f16=2} ffn_paths={matvec-gated-ffn-prefill-rows:skip:strategy_prefers_fused_geglu_prefill=2, fused-gated-ffn:accept:geglu:native_bf16=true=2} attention_paths={paged_metal=2} argmax_paths={native_argmax_f32=2} ffn_strategy=fused_geglu_prefill_over_row_prefill path_status={core=metal, linear=metal, logits=metal, ffn=metal, attention=metal, argmax=metal} path_coverage={core=12/12, linear=6/6, logits=2/2, ffn=2/2, attention=2/2, argmax=2/2}\n'
  printf '[Stream updates: 2, Duration: 40,00s, Speed: 10,00 t/s]\n'
fi
SH
chmod +x "$AQLI_BIN"

if bash "$ROOT_DIR/scripts/bench-gemma4-row-prefill-ab-repeat.sh" \
    --model fake-model \
    --aqli-bin "$AQLI_BIN" \
    --out-dir "$TMP_DIR/invalid-metric" \
    --aggregate-gate-metrics ttftMs,ffnnMs > "$TMP_DIR/invalid-metric.out" 2> "$TMP_DIR/invalid-metric.err"; then
  echo "Expected invalid aggregate gate metric to fail before running samples" >&2
  cat "$TMP_DIR/invalid-metric.out" >&2
  cat "$TMP_DIR/invalid-metric.err" >&2
  exit 1
fi
if ! grep -qx 'Unknown aggregate gate metric: ffnnMs' "$TMP_DIR/invalid-metric.err"; then
  echo "Expected invalid aggregate gate metric diagnostic" >&2
  cat "$TMP_DIR/invalid-metric.err" >&2
  exit 1
fi

OUT_DIR="$TMP_DIR/repeat"
bash "$ROOT_DIR/scripts/bench-gemma4-row-prefill-ab-repeat.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$OUT_DIR" \
  --repeat 2 \
  --require-promotion-can-promote \
  --require-promotion-action promote,promote-with-watchlist \
  -- --max-tokens 2 --gate-metrics ttftMs,ffnMs --max-regression-percent 10 --max-regression-ms 5 > "$TMP_DIR/repeat.out"

if [[ ! -f "$OUT_DIR/summary.tsv" || ! -f "$OUT_DIR/summary.md" || ! -f "$OUT_DIR/decision.json" || ! -f "$OUT_DIR/promotion-policy.json" || ! -f "$OUT_DIR/promotion-policy.tsv" || ! -f "$OUT_DIR/samples.tsv" || ! -f "$OUT_DIR/sample-decisions.tsv" || ! -f "$OUT_DIR/metrics.tsv" || ! -f "$OUT_DIR/metric-rows.tsv" || ! -f "$OUT_DIR/aggregate-gate-failures.tsv" || ! -f "$OUT_DIR/report.txt" ]]; then
  echo "Expected Gemma4 row-prefill repeat A/B aggregate artifacts" >&2
  find "$OUT_DIR" -maxdepth 3 -type f -print >&2 || true
  exit 1
fi

if ! grep -qx $'status\tpass' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'recommendation\tpromote-current-with-watchlist' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'requestedSamples\t2' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'samples\t2' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'passedSamples\t2' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'failedSamples\t0' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'errorSamples\t0' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'failedMetrics\t0' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'unstableMetrics\t0' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'aggregateGateMetrics\t' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'aggregateGateFailures\t0' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'p95RegressionGateFailures\t0' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'unstableGateFailures\t0' "$OUT_DIR/summary.tsv" \
    || ! grep -qx $'watchlistSamples\t2' "$OUT_DIR/summary.tsv"; then
  echo "Expected repeat A/B summary to aggregate passing watchlist samples" >&2
  cat "$OUT_DIR/summary.tsv" >&2
  cat "$TMP_DIR/repeat.out" >&2
  exit 1
fi

if ! grep -qx $'sample\texitCode\tstatus\trecommendation\tgatedMetrics\tfailedMetrics\tlargestImprovement\tlargestRegression\tffnStrategyTransition\trowPrefillTransition\tsampleDir\treport\tcompareMarkdown\tcompareDecision' "$OUT_DIR/samples.tsv" \
    || ! grep -q $'^sample-01	0	pass	promote-current-with-watchlist	2	0	prefillMs:37.500%	argmaxMs:20.000%	fused_geglu_prefill_over_row_prefill->row_prefill_matvec_active	n/a/n/a->12/x4	' "$OUT_DIR/samples.tsv" \
    || ! grep -q $'^sample-02	0	pass	promote-current-with-watchlist	2	0	prefillMs:37.500%	argmaxMs:20.000%	fused_geglu_prefill_over_row_prefill->row_prefill_matvec_active	n/a/n/a->12/x4	' "$OUT_DIR/samples.tsv" \
    || ! grep -Fq "$OUT_DIR/samples/sample-01/report.txt	$OUT_DIR/samples/sample-01/compare/summary.md	$OUT_DIR/samples/sample-01/compare/decision.json" "$OUT_DIR/samples.tsv" \
    || ! grep -Fq "$OUT_DIR/samples/sample-02/report.txt	$OUT_DIR/samples/sample-02/compare/summary.md	$OUT_DIR/samples/sample-02/compare/decision.json" "$OUT_DIR/samples.tsv"; then
  echo "Expected repeat A/B sample rows to preserve per-sample decisions" >&2
  cat "$OUT_DIR/samples.tsv" >&2
  exit 1
fi

if ! grep -qx $'sample\tcompareStatus\tcompareRecommendation\tcompareAction\tcompareCanPromote\tcompareGatedMetrics\tcompareFailedMetrics\tcompareLargestImprovement\tcompareLargestRegression\tcompareBaselineFfnStrategy\tcompareCurrentFfnStrategy\tcompareBaselineRowPrefill\tcompareCurrentRowPrefill\tcompareMarkdown\tcompareDecision' "$OUT_DIR/sample-decisions.tsv" \
    || ! grep -Fq $'sample-01	pass	promote-current-with-watchlist	promote-with-watchlist	true	2	0	prefillMs:37.500%	argmaxMs:20.000%	fused_geglu_prefill_over_row_prefill	row_prefill_matvec_active	n/a/n/a	12/x4	' "$OUT_DIR/sample-decisions.tsv" \
    || ! grep -Fq "$OUT_DIR/samples/sample-01/compare/summary.md	$OUT_DIR/samples/sample-01/compare/decision.json" "$OUT_DIR/sample-decisions.tsv" \
    || ! grep -Fq $'sample-02	pass	promote-current-with-watchlist	promote-with-watchlist	true	2	0	prefillMs:37.500%	argmaxMs:20.000%	fused_geglu_prefill_over_row_prefill	row_prefill_matvec_active	n/a/n/a	12/x4	' "$OUT_DIR/sample-decisions.tsv"; then
  echo "Expected repeat A/B sample decision rows to summarize per-sample compare decisions" >&2
  cat "$OUT_DIR/sample-decisions.tsv" >&2
  exit 1
fi

if ! grep -qx $'metric\tsamples\tbetter\tworse\tsame\tavgDelta\tmedianDelta\tp95Delta\tstddevDelta\tavgDeltaPercent\tmedianDeltaPercent\tp95RegressionPercent\tstddevDeltaPercent\tmaxImprovementPercent\tmaxRegressionPercent\tfailedGateCount\tunstable' "$OUT_DIR/metrics.tsv" \
    || ! grep -qx $'ttftMs\t2\t2\t0\t0\t-20.000\t-20.000\t-20.000\t0.000\t-20.000\t-20.000\t0.000\t0.000\t20.000\t0.000\t0\tfalse' "$OUT_DIR/metrics.tsv" \
    || ! grep -qx $'prefillMs\t2\t2\t0\t0\t-30.000\t-30.000\t-30.000\t0.000\t-37.500\t-37.500\t0.000\t0.000\t37.500\t0.000\t0\tfalse' "$OUT_DIR/metrics.tsv" \
    || ! grep -qx $'decodeMs\t2\t0\t2\t0\t2.000\t2.000\t2.000\t0.000\t4.000\t4.000\t4.000\t0.000\t0.000\t4.000\t0\tfalse' "$OUT_DIR/metrics.tsv" \
    || ! grep -qx $'ffnMs\t2\t2\t0\t0\t-20.000\t-20.000\t-20.000\t0.000\t-33.333\t-33.333\t0.000\t0.000\t33.333\t0.000\t0\tfalse' "$OUT_DIR/metrics.tsv"; then
  echo "Expected repeat A/B metrics to aggregate deltas across samples" >&2
  cat "$OUT_DIR/metrics.tsv" >&2
  exit 1
fi

if ! grep -qx $'metric\tgate\tvalue\tthreshold\treason' "$OUT_DIR/aggregate-gate-failures.tsv" \
    || [[ "$(wc -l < "$OUT_DIR/aggregate-gate-failures.tsv")" -ne 1 ]]; then
  echo "Expected passing repeat A/B run to emit only aggregate gate failure header" >&2
  cat "$OUT_DIR/aggregate-gate-failures.tsv" >&2
  exit 1
fi

if ! grep -Fqx '# Aqli Gemma4 Row-Prefill Repeat A/B' "$OUT_DIR/summary.md" \
    || ! grep -Fqx '| Status | pass |' "$OUT_DIR/summary.md" \
    || ! grep -Fqx '| Recommendation | promote-current-with-watchlist |' "$OUT_DIR/summary.md" \
    || ! grep -Fqx '| Aggregate Gate Failures | 0 |' "$OUT_DIR/summary.md" \
    || ! grep -Fqx '| n/a | n/a | n/a | n/a | No aggregate gate failures. |' "$OUT_DIR/summary.md" \
    || ! grep -Fqx '| `prefillMs` | 2 | 2 | 0 | -30.000 | -30.000 | -30.000 | -37.500 | 0.000 | 0.000 | false |' "$OUT_DIR/summary.md" \
    || ! grep -Fqx '## Samples' "$OUT_DIR/summary.md" \
    || ! grep -Fqx "| \`sample-01\` | \`pass\` | \`promote-current-with-watchlist\` | \`$OUT_DIR/samples/sample-01/report.txt\` | \`$OUT_DIR/samples/sample-01/compare/summary.md\` | \`$OUT_DIR/samples/sample-01/compare/decision.json\` |" "$OUT_DIR/summary.md" \
    || ! grep -Fqx -- "- Sample decisions: \`$OUT_DIR/sample-decisions.tsv\`" "$OUT_DIR/summary.md" \
    || ! grep -Fqx -- "- Decision JSON: \`$OUT_DIR/decision.json\`" "$OUT_DIR/summary.md" \
    || ! grep -Fqx -- "- Promotion policy JSON: \`$OUT_DIR/promotion-policy.json\`" "$OUT_DIR/summary.md" \
    || ! grep -Fqx -- "- Promotion policy TSV: \`$OUT_DIR/promotion-policy.tsv\`" "$OUT_DIR/summary.md" \
    || ! grep -Fqx '## Promotion Policy' "$OUT_DIR/summary.md" \
    || ! grep -Fqx '| Action | promote-with-watchlist |' "$OUT_DIR/summary.md" \
    || ! grep -Fqx '| Can Promote | true |' "$OUT_DIR/summary.md" \
    || ! grep -Fqx '| Requires Watchlist | true |' "$OUT_DIR/summary.md" \
    || ! grep -Fqx '| Policy Exit Code | 0 |' "$OUT_DIR/summary.md" \
    || ! grep -Fqx '| Reasons | watchlist-required |' "$OUT_DIR/summary.md"; then
  echo "Expected passing repeat A/B Markdown summary" >&2
  cat "$OUT_DIR/summary.md" >&2
  exit 1
fi

if ! jq -e \
    --arg markdown "$OUT_DIR/summary.md" \
    --arg decision "$OUT_DIR/decision.json" \
    '
      .schemaVersion == 1
      and .model == "fake-model"
      and .runner.repeat == 2
      and .runner.runnerArgs == ["--max-tokens", "2", "--gate-metrics", "ttftMs,ffnMs", "--max-regression-percent", "10", "--max-regression-ms", "5"]
      and .gates.aggregateGateMetrics == ["all"]
      and .summary.status == "pass"
      and .summary.reason == null
      and .summary.recommendation == "promote-current-with-watchlist"
      and .summary.samples.requested == 2
      and .summary.samples.passed == 2
      and .summary.metrics.unstable == 0
      and .summary.gates.aggregateFailures == 0
      and .policy.canPromote == true
      and .policy.action == "promote-with-watchlist"
      and (.aggregateGateFailures | length) == 0
      and (.samples | length) == 2
      and (.sampleDecisions | length) == 2
      and all(.samples[]; .compareMarkdown != null and (.compareMarkdown | endswith("/compare/summary.md")))
      and all(.samples[]; .compareDecision != null and (.compareDecision | endswith("/compare/decision.json")))
      and all(.sampleDecisions[]; .compareStatus == "pass" and .compareAction == "promote-with-watchlist" and .compareCanPromote == true and .compareGatedMetrics == 2 and .compareFailedMetrics == 0)
      and any(.sampleDecisions[]; .sample == "sample-01" and .compareLargestImprovement == "prefillMs:37.500%" and .compareLargestRegression == "argmaxMs:20.000%")
      and any(.metrics[]; .metric == "prefillMs" and .medianDeltaPercent == -37.5 and .unstable == false)
      and .artifacts.markdown == $markdown
      and .artifacts.decision == $decision
    ' "$OUT_DIR/decision.json" >/dev/null; then
  echo "Expected passing repeat A/B decision JSON" >&2
  cat "$OUT_DIR/decision.json" >&2
  exit 1
fi

if ! jq -e \
    --arg decision "$OUT_DIR/decision.json" \
    --arg sample_decisions "$OUT_DIR/sample-decisions.tsv" \
    '
      .schemaVersion == 1
      and .model == "fake-model"
      and .sourceDecision == $decision
      and .action == "promote-with-watchlist"
      and .canPromote == true
      and .requiresWatchlist == true
      and .reasons == ["watchlist-required"]
      and .guardrails.samples == 2
      and .guardrails.aggregateGateFailures == 0
      and .sampleDecisionCounts.watchlist == 2
      and .sampleDecisionCounts.missing == 0
      and .artifacts.sampleDecisions == $sample_decisions
    ' "$OUT_DIR/promotion-policy.json" >/dev/null \
    || ! grep -qx $'action\tpromote-with-watchlist' "$OUT_DIR/promotion-policy.tsv" \
    || ! grep -qx $'canPromote\ttrue' "$OUT_DIR/promotion-policy.tsv" \
    || ! grep -qx $'requiresWatchlist\ttrue' "$OUT_DIR/promotion-policy.tsv" \
    || ! grep -qx $'reasons\twatchlist-required' "$OUT_DIR/promotion-policy.tsv" \
    || ! grep -qx $'watchlistSampleDecisions\t2' "$OUT_DIR/promotion-policy.tsv"; then
  echo "Expected passing repeat A/B promotion policy" >&2
  cat "$OUT_DIR/promotion-policy.json" >&2
  cat "$OUT_DIR/promotion-policy.tsv" >&2
  exit 1
fi

if ! grep -Fqx "artifacts.samples=$OUT_DIR/samples.tsv" "$TMP_DIR/repeat.out" \
    || ! grep -Fqx "artifacts.sampleDecisions=$OUT_DIR/sample-decisions.tsv" "$TMP_DIR/repeat.out" \
    || ! grep -Fqx "artifacts.metrics=$OUT_DIR/metrics.tsv" "$TMP_DIR/repeat.out" \
    || ! grep -Fqx "artifacts.aggregateGateFailures=$OUT_DIR/aggregate-gate-failures.tsv" "$TMP_DIR/repeat.out" \
    || ! grep -Fqx "artifacts.markdown=$OUT_DIR/summary.md" "$TMP_DIR/repeat.out" \
    || ! grep -Fqx "artifacts.decision=$OUT_DIR/decision.json" "$TMP_DIR/repeat.out" \
    || ! grep -Fqx "artifacts.promotionPolicy=$OUT_DIR/promotion-policy.json" "$TMP_DIR/repeat.out" \
    || ! grep -Fqx "artifacts.promotionPolicyTsv=$OUT_DIR/promotion-policy.tsv" "$TMP_DIR/repeat.out" \
    || ! grep -Fqx 'status=pass' "$TMP_DIR/repeat.out" \
    || ! grep -Fqx 'recommendation=promote-current-with-watchlist' "$TMP_DIR/repeat.out" \
    || ! grep -Fqx 'promotionAction=promote-with-watchlist' "$TMP_DIR/repeat.out" \
    || ! grep -Fqx 'promotionCanPromote=true' "$TMP_DIR/repeat.out" \
    || ! grep -Fqx 'promotionRequiresWatchlist=true' "$TMP_DIR/repeat.out" \
    || ! grep -Fqx 'promotionPolicyExitCode=0' "$TMP_DIR/repeat.out" \
    || ! grep -Fqx 'promotionReasons=watchlist-required' "$TMP_DIR/repeat.out" \
    || ! grep -Fqx 'samples=2' "$TMP_DIR/repeat.out" \
    || ! grep -Fqx 'passedSamples=2' "$TMP_DIR/repeat.out" \
    || ! grep -Fqx 'unstableMetrics=0' "$TMP_DIR/repeat.out" \
    || ! grep -Fqx 'aggregateGateFailures=0' "$TMP_DIR/repeat.out"; then
  echo "Expected repeat A/B stdout to summarize aggregate artifacts" >&2
  cat "$TMP_DIR/repeat.out" >&2
  exit 1
fi

PROMOTION_ACTION_FAIL_OUT_DIR="$TMP_DIR/repeat-promotion-action-fail"
if bash "$ROOT_DIR/scripts/bench-gemma4-row-prefill-ab-repeat.sh" \
    --model fake-model \
    --aqli-bin "$AQLI_BIN" \
    --out-dir "$PROMOTION_ACTION_FAIL_OUT_DIR" \
    --repeat 2 \
    --require-promotion-action promote \
    -- --max-tokens 2 --gate-metrics ttftMs,ffnMs --max-regression-percent 10 --max-regression-ms 5 > "$TMP_DIR/repeat-promotion-action-fail.out" 2> "$TMP_DIR/repeat-promotion-action-fail.err"; then
  echo "Expected repeat A/B promotion action gate to fail after writing artifacts" >&2
  cat "$TMP_DIR/repeat-promotion-action-fail.out" >&2
  cat "$TMP_DIR/repeat-promotion-action-fail.err" >&2
  exit 1
fi

if [[ ! -f "$PROMOTION_ACTION_FAIL_OUT_DIR/promotion-policy.json" || ! -f "$PROMOTION_ACTION_FAIL_OUT_DIR/promotion-policy.tsv" ]]; then
  echo "Expected promotion action gate failure to preserve promotion policy artifacts" >&2
  find "$PROMOTION_ACTION_FAIL_OUT_DIR" -maxdepth 2 -type f -print >&2 || true
  exit 1
fi
if ! grep -qx $'status\tpass' "$PROMOTION_ACTION_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'recommendation\tpromote-current-with-watchlist' "$PROMOTION_ACTION_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'action\tpromote-with-watchlist' "$PROMOTION_ACTION_FAIL_OUT_DIR/promotion-policy.tsv" \
    || ! grep -qx $'canPromote\ttrue' "$PROMOTION_ACTION_FAIL_OUT_DIR/promotion-policy.tsv" \
    || ! grep -Fqx '| Policy Exit Code | 1 |' "$PROMOTION_ACTION_FAIL_OUT_DIR/summary.md" \
    || ! grep -Fqx 'status=pass' "$TMP_DIR/repeat-promotion-action-fail.out" \
    || ! grep -Fqx 'promotionAction=promote-with-watchlist' "$TMP_DIR/repeat-promotion-action-fail.out" \
    || ! grep -Fqx 'promotionCanPromote=true' "$TMP_DIR/repeat-promotion-action-fail.out" \
    || ! grep -Fqx 'promotionPolicyExitCode=1' "$TMP_DIR/repeat-promotion-action-fail.out" \
    || ! grep -Fqx "artifacts.promotionPolicy=$PROMOTION_ACTION_FAIL_OUT_DIR/promotion-policy.json" "$TMP_DIR/repeat-promotion-action-fail.out" \
    || ! grep -Fqx "Promotion policy action 'promote-with-watchlist' is not allowed by --require-action 'promote'" "$TMP_DIR/repeat-promotion-action-fail.err"; then
  echo "Expected repeat A/B promotion action gate diagnostics" >&2
  cat "$PROMOTION_ACTION_FAIL_OUT_DIR/summary.tsv" >&2
  cat "$PROMOTION_ACTION_FAIL_OUT_DIR/summary.md" >&2
  cat "$PROMOTION_ACTION_FAIL_OUT_DIR/promotion-policy.tsv" >&2
  cat "$TMP_DIR/repeat-promotion-action-fail.out" >&2
  cat "$TMP_DIR/repeat-promotion-action-fail.err" >&2
  exit 1
fi

FAIL_OUT_DIR="$TMP_DIR/repeat-fail"
if bash "$ROOT_DIR/scripts/bench-gemma4-row-prefill-ab-repeat.sh" \
    --model fake-model \
    --aqli-bin "$AQLI_BIN" \
    --out-dir "$FAIL_OUT_DIR" \
    --repeat 2 \
    -- --max-tokens 2 --gate-metrics decodeMs --max-regression-ms 1 > "$TMP_DIR/repeat-fail.out" 2> "$TMP_DIR/repeat-fail.err"; then
  echo "Expected repeat A/B aggregate failure for repeated decode regression" >&2
  cat "$TMP_DIR/repeat-fail.out" >&2
  cat "$TMP_DIR/repeat-fail.err" >&2
  exit 1
fi

if ! grep -qx $'status\tfail' "$FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'recommendation\treject-current' "$FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'samples\t2' "$FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'failedSamples\t2' "$FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'failedMetrics\t2' "$FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'unstableMetrics\t0' "$FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'aggregateGateFailures\t0' "$FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'rejectSamples\t2' "$FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'decodeMs\t2\t0\t2\t0\t2.000\t2.000\t2.000\t0.000\t4.000\t4.000\t4.000\t0.000\t0.000\t4.000\t2\tfalse' "$FAIL_OUT_DIR/metrics.tsv" \
    || ! grep -Fqx 'status=fail' "$TMP_DIR/repeat-fail.out" \
    || ! grep -Fqx 'recommendation=reject-current' "$TMP_DIR/repeat-fail.out" \
    || ! grep -Fqx 'failedSamples=2' "$TMP_DIR/repeat-fail.out" \
    || ! grep -Fqx 'unstableMetrics=0' "$TMP_DIR/repeat-fail.out" \
    || ! grep -Fqx 'aggregateGateFailures=0' "$TMP_DIR/repeat-fail.out"; then
  echo "Expected repeat A/B aggregate to reject repeated decode regression" >&2
  cat "$FAIL_OUT_DIR/summary.tsv" >&2
  cat "$FAIL_OUT_DIR/metrics.tsv" >&2
  cat "$TMP_DIR/repeat-fail.out" >&2
  cat "$TMP_DIR/repeat-fail.err" >&2
  exit 1
fi

P95_FAIL_OUT_DIR="$TMP_DIR/repeat-p95-fail"
if bash "$ROOT_DIR/scripts/bench-gemma4-row-prefill-ab-repeat.sh" \
    --model fake-model \
    --aqli-bin "$AQLI_BIN" \
    --out-dir "$P95_FAIL_OUT_DIR" \
    --repeat 2 \
    --max-p95-regression-percent 15 \
    -- --max-tokens 2 > "$TMP_DIR/repeat-p95-fail.out" 2> "$TMP_DIR/repeat-p95-fail.err"; then
  echo "Expected repeat A/B aggregate failure for p95 regression" >&2
  cat "$TMP_DIR/repeat-p95-fail.out" >&2
  cat "$TMP_DIR/repeat-p95-fail.err" >&2
  exit 1
fi

if ! grep -qx $'status\tfail' "$P95_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'reason\tp95-regression' "$P95_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'recommendation\treject-current' "$P95_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'failedSamples\t0' "$P95_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'aggregateGateFailures\t1' "$P95_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'p95RegressionGateFailures\t1' "$P95_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'unstableGateFailures\t0' "$P95_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'argmaxMs\tp95-regression\t20.000\t15\tp95RegressionPercent=20.000 exceeded 15' "$P95_FAIL_OUT_DIR/aggregate-gate-failures.tsv" \
    || ! grep -Fqx '| `argmaxMs` | `p95-regression` | 20.000 | 15 | p95RegressionPercent=20.000 exceeded 15 |' "$P95_FAIL_OUT_DIR/summary.md" \
    || ! grep -Fqx 'status=fail' "$TMP_DIR/repeat-p95-fail.out" \
    || ! grep -Fqx 'reason=p95-regression' "$TMP_DIR/repeat-p95-fail.out" \
    || ! grep -Fqx 'promotionAction=reject' "$TMP_DIR/repeat-p95-fail.out" \
    || ! grep -Fqx 'promotionCanPromote=false' "$TMP_DIR/repeat-p95-fail.out" \
    || ! grep -Fqx "artifacts.aggregateGateFailures=$P95_FAIL_OUT_DIR/aggregate-gate-failures.tsv" "$TMP_DIR/repeat-p95-fail.out" \
    || ! grep -Fqx "artifacts.decision=$P95_FAIL_OUT_DIR/decision.json" "$TMP_DIR/repeat-p95-fail.out" \
    || ! grep -Fqx 'p95RegressionGateFailures=1' "$TMP_DIR/repeat-p95-fail.out"; then
  echo "Expected repeat A/B aggregate to reject p95 regression budget breach" >&2
  cat "$P95_FAIL_OUT_DIR/summary.tsv" >&2
  cat "$P95_FAIL_OUT_DIR/metrics.tsv" >&2
  cat "$TMP_DIR/repeat-p95-fail.out" >&2
  cat "$TMP_DIR/repeat-p95-fail.err" >&2
  exit 1
fi

if ! jq -e '
      .summary.status == "fail"
      and .summary.reason == "p95-regression"
      and .summary.gates.aggregateFailures == 1
      and .summary.gates.p95RegressionFailures == 1
      and .policy.canPromote == false
      and .policy.action == "reject"
      and all(.samples[]; .compareMarkdown != null and (.compareMarkdown | endswith("/compare/summary.md")))
      and all(.samples[]; .compareDecision != null and (.compareDecision | endswith("/compare/decision.json")))
      and all(.sampleDecisions[]; .compareStatus == "pass" and .compareAction == "promote-with-watchlist")
      and any(.aggregateGateFailures[]; .metric == "argmaxMs" and .gate == "p95-regression" and .value == 20 and .threshold == 15)
    ' "$P95_FAIL_OUT_DIR/decision.json" >/dev/null; then
  echo "Expected p95 failure decision JSON" >&2
  cat "$P95_FAIL_OUT_DIR/decision.json" >&2
  exit 1
fi

if ! jq -e '
      .action == "reject"
      and .canPromote == false
      and .requiresWatchlist == false
      and (.reasons | index("aggregate-gate-failures") != null)
      and (.reasons | index("aggregate-status-failed") != null)
      and (.reasons | index("p95-regression") != null)
      and .guardrails.aggregateGateFailures == 1
      and .guardrails.p95RegressionGateFailures == 1
      and .sampleDecisionCounts.watchlist == 2
      and .sampleDecisionCounts.missing == 0
    ' "$P95_FAIL_OUT_DIR/promotion-policy.json" >/dev/null \
    || ! grep -qx $'action\treject' "$P95_FAIL_OUT_DIR/promotion-policy.tsv" \
    || ! grep -qx $'canPromote\tfalse' "$P95_FAIL_OUT_DIR/promotion-policy.tsv" \
    || ! grep -qx $'aggregateGateFailures\t1' "$P95_FAIL_OUT_DIR/promotion-policy.tsv"; then
  echo "Expected p95 failure promotion policy" >&2
  cat "$P95_FAIL_OUT_DIR/promotion-policy.json" >&2
  cat "$P95_FAIL_OUT_DIR/promotion-policy.tsv" >&2
  exit 1
fi

P95_FILTER_PASS_OUT_DIR="$TMP_DIR/repeat-p95-filter-pass"
bash "$ROOT_DIR/scripts/bench-gemma4-row-prefill-ab-repeat.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$P95_FILTER_PASS_OUT_DIR" \
  --repeat 2 \
  --max-p95-regression-percent 15 \
  --aggregate-gate-metrics ttftMs,ffnMs \
  -- --max-tokens 2 > "$TMP_DIR/repeat-p95-filter-pass.out"

if ! grep -qx $'status\tpass' "$P95_FILTER_PASS_OUT_DIR/summary.tsv" \
    || ! grep -qx $'recommendation\tpromote-current-with-watchlist' "$P95_FILTER_PASS_OUT_DIR/summary.tsv" \
    || ! grep -qx $'aggregateGateMetrics\tttftMs,ffnMs' "$P95_FILTER_PASS_OUT_DIR/summary.tsv" \
    || ! grep -qx $'aggregateGateFailures\t0' "$P95_FILTER_PASS_OUT_DIR/summary.tsv" \
    || ! grep -qx $'p95RegressionGateFailures\t0' "$P95_FILTER_PASS_OUT_DIR/summary.tsv" \
    || [[ "$(wc -l < "$P95_FILTER_PASS_OUT_DIR/aggregate-gate-failures.tsv")" -ne 1 ]] \
    || ! grep -Fqx 'status=pass' "$TMP_DIR/repeat-p95-filter-pass.out" \
    || ! grep -Fqx 'aggregateGateMetrics=ttftMs,ffnMs' "$TMP_DIR/repeat-p95-filter-pass.out" \
    || ! grep -Fqx 'p95RegressionGateFailures=0' "$TMP_DIR/repeat-p95-filter-pass.out"; then
  echo "Expected aggregate metric filter to ignore non-target p95 regressions" >&2
  cat "$P95_FILTER_PASS_OUT_DIR/summary.tsv" >&2
  cat "$P95_FILTER_PASS_OUT_DIR/metrics.tsv" >&2
  cat "$TMP_DIR/repeat-p95-filter-pass.out" >&2
  exit 1
fi

STUB_RUNNER="$TMP_DIR/stub-ab-runner.sh"
cat > "$STUB_RUNNER" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

out_dir=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --out-dir) out_dir="$2"; shift 2 ;;
    --out-dir=*) out_dir="${1#*=}"; shift ;;
    *) shift ;;
  esac
done
if [[ -z "$out_dir" ]]; then
  echo "--out-dir is required" >&2
  exit 2
fi

sample="$(basename "$out_dir")"
mkdir -p "$out_dir/compare"
{
  printf 'case\tmetric\tbaseline\tcurrent\tdelta\tdeltaPercent\tdirection\ttrend\tbaselineFfnStrategy\tcurrentFfnStrategy\tbaselineRowPrefill\tcurrentRowPrefill\tgateStatus\tgateReason\n'
  if [[ "$sample" == "sample-01" ]]; then
    printf 'metal-deterministic\tdecodeMs\t50.000\t48.000\t-2.000\t-4.000\tlower\tbetter\tfused_geglu_prefill_over_row_prefill\trow_prefill_matvec_active\tn/a/n/a\t12/x4\tnot-configured\t\n'
  else
    printf 'metal-deterministic\tdecodeMs\t50.000\t52.000\t2.000\t4.000\tlower\tworse\tfused_geglu_prefill_over_row_prefill\trow_prefill_matvec_active\tn/a/n/a\t12/x4\tnot-configured\t\n'
  fi
} > "$out_dir/compare/comparison.tsv"

{
  echo "status=pass"
  echo "recommendation=promote-current-with-watchlist"
  echo "gatedMetrics=0"
  echo "failedMetrics=0"
  echo "largestImprovement=decodeMs:4.000%"
  echo "largestRegression=decodeMs:4.000%"
  echo "ffnStrategyTransition=fused_geglu_prefill_over_row_prefill->row_prefill_matvec_active"
  echo "rowPrefillTransition=n/a/n/a->12/x4"
} > "$out_dir/report.txt"
SH
chmod +x "$STUB_RUNNER"

UNSTABLE_FAIL_OUT_DIR="$TMP_DIR/repeat-unstable-fail"
if bash "$ROOT_DIR/scripts/bench-gemma4-row-prefill-ab-repeat.sh" \
    --model fake-model \
    --aqli-bin "$AQLI_BIN" \
    --out-dir "$UNSTABLE_FAIL_OUT_DIR" \
    --repeat 2 \
    --runner-script "$STUB_RUNNER" \
    --fail-unstable-metrics > "$TMP_DIR/repeat-unstable-fail.out" 2> "$TMP_DIR/repeat-unstable-fail.err"; then
  echo "Expected repeat A/B aggregate failure for unstable metric" >&2
  cat "$TMP_DIR/repeat-unstable-fail.out" >&2
  cat "$TMP_DIR/repeat-unstable-fail.err" >&2
  exit 1
fi

if ! grep -qx $'status\tfail' "$UNSTABLE_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'reason\tunstable-metrics' "$UNSTABLE_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'recommendation\treject-current' "$UNSTABLE_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'unstableMetrics\t1' "$UNSTABLE_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'aggregateGateFailures\t1' "$UNSTABLE_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'p95RegressionGateFailures\t0' "$UNSTABLE_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'unstableGateFailures\t1' "$UNSTABLE_FAIL_OUT_DIR/summary.tsv" \
    || ! grep -qx $'decodeMs\t2\t1\t1\t0\t0.000\t0.000\t2.000\t2.000\t0.000\t0.000\t4.000\t4.000\t4.000\t4.000\t0\ttrue' "$UNSTABLE_FAIL_OUT_DIR/metrics.tsv" \
    || ! grep -qx $'decodeMs\tunstable-metrics\t4.000\tstable\tmetric has mixed better/worse samples or high variance' "$UNSTABLE_FAIL_OUT_DIR/aggregate-gate-failures.tsv" \
    || ! grep -Fqx '| `decodeMs` | `unstable-metrics` | 4.000 | stable | metric has mixed better/worse samples or high variance |' "$UNSTABLE_FAIL_OUT_DIR/summary.md" \
    || ! grep -Fqx 'status=fail' "$TMP_DIR/repeat-unstable-fail.out" \
    || ! grep -Fqx 'reason=unstable-metrics' "$TMP_DIR/repeat-unstable-fail.out" \
    || ! grep -Fqx 'promotionAction=reject' "$TMP_DIR/repeat-unstable-fail.out" \
    || ! grep -Fqx 'promotionCanPromote=false' "$TMP_DIR/repeat-unstable-fail.out" \
    || ! grep -Fqx "artifacts.aggregateGateFailures=$UNSTABLE_FAIL_OUT_DIR/aggregate-gate-failures.tsv" "$TMP_DIR/repeat-unstable-fail.out" \
    || ! grep -Fqx "artifacts.decision=$UNSTABLE_FAIL_OUT_DIR/decision.json" "$TMP_DIR/repeat-unstable-fail.out" \
    || ! grep -Fqx 'unstableGateFailures=1' "$TMP_DIR/repeat-unstable-fail.out"; then
  echo "Expected repeat A/B aggregate to reject unstable metric evidence" >&2
  cat "$UNSTABLE_FAIL_OUT_DIR/summary.tsv" >&2
  cat "$UNSTABLE_FAIL_OUT_DIR/metrics.tsv" >&2
  cat "$TMP_DIR/repeat-unstable-fail.out" >&2
  cat "$TMP_DIR/repeat-unstable-fail.err" >&2
  exit 1
fi

if ! jq -e '
      .summary.status == "fail"
      and .summary.reason == "unstable-metrics"
      and .summary.metrics.unstable == 1
      and .summary.gates.unstableFailures == 1
      and .policy.canPromote == false
      and .policy.action == "reject"
      and all(.samples[]; .compareMarkdown == null)
      and all(.samples[]; .compareDecision == null)
      and all(.sampleDecisions[]; .compareStatus == null and .compareAction == null and .compareMarkdown == null and .compareDecision == null)
      and any(.metrics[]; .metric == "decodeMs" and .unstable == true and .worse == 1 and .better == 1)
      and any(.aggregateGateFailures[]; .metric == "decodeMs" and .gate == "unstable-metrics" and .threshold == "stable")
    ' "$UNSTABLE_FAIL_OUT_DIR/decision.json" >/dev/null; then
  echo "Expected unstable failure decision JSON" >&2
  cat "$UNSTABLE_FAIL_OUT_DIR/decision.json" >&2
  exit 1
fi

if ! jq -e '
      .action == "reject"
      and .canPromote == false
      and .requiresWatchlist == false
      and (.reasons | index("aggregate-gate-failures") != null)
      and (.reasons | index("aggregate-status-failed") != null)
      and (.reasons | index("unstable-metrics") != null)
      and (.reasons | index("unstable-metrics-present") != null)
      and .guardrails.unstableMetrics == 1
      and .guardrails.unstableGateFailures == 1
      and .sampleDecisionCounts.watchlist == 0
      and .sampleDecisionCounts.missing == 2
    ' "$UNSTABLE_FAIL_OUT_DIR/promotion-policy.json" >/dev/null \
    || ! grep -qx $'action\treject' "$UNSTABLE_FAIL_OUT_DIR/promotion-policy.tsv" \
    || ! grep -qx $'missingSampleDecisions\t2' "$UNSTABLE_FAIL_OUT_DIR/promotion-policy.tsv"; then
  echo "Expected unstable failure promotion policy" >&2
  cat "$UNSTABLE_FAIL_OUT_DIR/promotion-policy.json" >&2
  cat "$UNSTABLE_FAIL_OUT_DIR/promotion-policy.tsv" >&2
  exit 1
fi

UNSTABLE_FILTER_PASS_OUT_DIR="$TMP_DIR/repeat-unstable-filter-pass"
bash "$ROOT_DIR/scripts/bench-gemma4-row-prefill-ab-repeat.sh" \
  --model fake-model \
  --aqli-bin "$AQLI_BIN" \
  --out-dir "$UNSTABLE_FILTER_PASS_OUT_DIR" \
  --repeat 2 \
  --runner-script "$STUB_RUNNER" \
  --fail-unstable-metrics \
  --aggregate-gate-metrics ffnMs > "$TMP_DIR/repeat-unstable-filter-pass.out"

if ! grep -qx $'status\tpass' "$UNSTABLE_FILTER_PASS_OUT_DIR/summary.tsv" \
    || ! grep -qx $'recommendation\tpromote-current-with-watchlist' "$UNSTABLE_FILTER_PASS_OUT_DIR/summary.tsv" \
    || ! grep -qx $'unstableMetrics\t1' "$UNSTABLE_FILTER_PASS_OUT_DIR/summary.tsv" \
    || ! grep -qx $'aggregateGateMetrics\tffnMs' "$UNSTABLE_FILTER_PASS_OUT_DIR/summary.tsv" \
    || ! grep -qx $'aggregateGateFailures\t0' "$UNSTABLE_FILTER_PASS_OUT_DIR/summary.tsv" \
    || ! grep -qx $'unstableGateFailures\t0' "$UNSTABLE_FILTER_PASS_OUT_DIR/summary.tsv" \
    || [[ "$(wc -l < "$UNSTABLE_FILTER_PASS_OUT_DIR/aggregate-gate-failures.tsv")" -ne 1 ]] \
    || ! grep -Fqx 'status=pass' "$TMP_DIR/repeat-unstable-filter-pass.out" \
    || ! grep -Fqx 'aggregateGateMetrics=ffnMs' "$TMP_DIR/repeat-unstable-filter-pass.out" \
    || ! grep -Fqx 'unstableMetrics=1' "$TMP_DIR/repeat-unstable-filter-pass.out" \
    || ! grep -Fqx 'unstableGateFailures=0' "$TMP_DIR/repeat-unstable-filter-pass.out"; then
  echo "Expected aggregate metric filter to ignore non-target instability" >&2
  cat "$UNSTABLE_FILTER_PASS_OUT_DIR/summary.tsv" >&2
  cat "$UNSTABLE_FILTER_PASS_OUT_DIR/metrics.tsv" >&2
  cat "$TMP_DIR/repeat-unstable-filter-pass.out" >&2
  exit 1
fi

printf 'Gemma4 row-prefill repeat A/B benchmark test passed\n'
