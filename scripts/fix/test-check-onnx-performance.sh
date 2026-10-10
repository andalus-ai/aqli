#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-onnx-performance-check.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

write_fake_script() {
  local path="$1"
  local name="$2"
  cat > "$path" <<SH
#!/usr/bin/env bash
set -euo pipefail
{
  printf 'script\t%s\n' "$name"
  index=0
  for arg in "\$@"; do
    printf '%s\t%s\n' "\$index" "\$arg"
    index=\$((index + 1))
  done
} > "\$AQLI_FAKE_PERFORMANCE_ARGV"
printf 'fake %s\n' "$name"
SH
  chmod +x "$path"
}

GATE_SCRIPT="${TMP_DIR}/gate.sh"
REGRESSION_SCRIPT="${TMP_DIR}/regression.sh"
CAPTURE_SCRIPT="${TMP_DIR}/capture.sh"
COMPARE_SCRIPT="${TMP_DIR}/compare.sh"
BUNDLE_VERIFY_SCRIPT="${TMP_DIR}/bundle-verify.sh"
DIAGNOSE_SCRIPT="${TMP_DIR}/diagnose.sh"
DECISION_SCRIPT="${TMP_DIR}/decision.sh"
write_fake_script "$GATE_SCRIPT" gate
write_fake_script "$REGRESSION_SCRIPT" regression
write_fake_script "$CAPTURE_SCRIPT" capture
write_fake_script "$COMPARE_SCRIPT" compare
write_fake_script "$BUNDLE_VERIFY_SCRIPT" bundle-verify
write_fake_script "$DIAGNOSE_SCRIPT" diagnose
write_fake_script "$DECISION_SCRIPT" decision

bash "$ROOT_DIR/scripts/check-onnx-performance.sh" --list-presets >"${TMP_DIR}/list-presets.tsv"
if ! grep -qx $'name\tdescription' "${TMP_DIR}/list-presets.tsv" \
    || ! grep -qx $'quick\tLight smoke run: 1 token, 1 run, no warmup' "${TMP_DIR}/list-presets.tsv" \
    || ! grep -qx $'coreml-stable\tStable run with CoreML backend expectation' "${TMP_DIR}/list-presets.tsv"; then
  echo "Expected facade preset list" >&2
  cat "${TMP_DIR}/list-presets.tsv" >&2
  exit 1
fi

GATE_ARGV="${TMP_DIR}/gate-argv.tsv"
GATE_DECISION="${TMP_DIR}/gate-decision.tsv"
AQLI_FAKE_PERFORMANCE_ARGV="$GATE_ARGV" \
  bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --model fake-model \
    --gate-script "$GATE_SCRIPT" \
    --regression-script "$REGRESSION_SCRIPT" \
    --capture-script "$CAPTURE_SCRIPT" \
    --expect-backend CoreML \
    --max-duration-ms 1000 \
    --decision-out "$GATE_DECISION" \
    --decision-script "$DECISION_SCRIPT" >"${TMP_DIR}/gate.out"

if ! grep -qx 'mode=gate' "${TMP_DIR}/gate.out" \
    || ! grep -qx $'script\tgate' "$GATE_ARGV" \
    || ! grep -qx $'0\t--model' "$GATE_ARGV" \
    || ! grep -qx $'1\tfake-model' "$GATE_ARGV" \
    || ! grep -qx $'2\t--expect-backend' "$GATE_ARGV" \
    || ! grep -qx $'3\tCoreML' "$GATE_ARGV" \
    || ! grep -qx $'4\t--max-duration-ms' "$GATE_ARGV" \
    || ! grep -qx $'5\t1000' "$GATE_ARGV" \
    || ! grep -qx $'6\t--decision-out' "$GATE_ARGV" \
    || ! grep -qx $'7\t'"$GATE_DECISION" "$GATE_ARGV" \
    || ! grep -qx $'8\t--decision-script' "$GATE_ARGV" \
    || ! grep -qx $'9\t'"$DECISION_SCRIPT" "$GATE_ARGV"; then
  echo "Expected gate mode delegation" >&2
  cat "${TMP_DIR}/gate.out" >&2
  cat "$GATE_ARGV" >&2
  exit 1
fi

REGRESSION_ARGV="${TMP_DIR}/regression-argv.tsv"
AQLI_FAKE_PERFORMANCE_ARGV="$REGRESSION_ARGV" \
  bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --model fake-model \
    --baseline baseline.tsv \
    --gate-script "$GATE_SCRIPT" \
    --regression-script "$REGRESSION_SCRIPT" \
    --capture-script "$CAPTURE_SCRIPT" \
    --max-regression-percent 12 \
    --max-noise-percent 5 \
    --noise-metrics durationMs,generationTps >"${TMP_DIR}/regression.out"

if ! grep -qx 'mode=regression' "${TMP_DIR}/regression.out" \
    || ! grep -qx $'script\tregression' "$REGRESSION_ARGV" \
    || ! grep -qx $'2\t--baseline' "$REGRESSION_ARGV" \
    || ! grep -qx $'3\tbaseline.tsv' "$REGRESSION_ARGV" \
    || ! grep -qx $'4\t--max-regression-percent' "$REGRESSION_ARGV" \
    || ! grep -qx $'5\t12' "$REGRESSION_ARGV" \
    || ! grep -qx $'6\t--max-noise-percent' "$REGRESSION_ARGV" \
    || ! grep -qx $'7\t5' "$REGRESSION_ARGV" \
    || ! grep -qx $'8\t--noise-metrics' "$REGRESSION_ARGV" \
    || ! grep -qx $'9\tdurationMs,generationTps' "$REGRESSION_ARGV"; then
  echo "Expected regression mode delegation" >&2
  cat "${TMP_DIR}/regression.out" >&2
  cat "$REGRESSION_ARGV" >&2
  exit 1
fi

DECISION_ARGV="${TMP_DIR}/decision-argv.tsv"
AQLI_FAKE_PERFORMANCE_ARGV="$DECISION_ARGV" \
  bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --model fake-model \
    --baseline baseline.tsv \
    --regression-script "$REGRESSION_SCRIPT" \
    --decision-script "$DECISION_SCRIPT" >"${TMP_DIR}/decision.out"

if ! grep -qx 'mode=regression' "${TMP_DIR}/decision.out" \
    || ! grep -qx $'script\tregression' "$DECISION_ARGV" \
    || ! grep -qx $'4\t--decision-script' "$DECISION_ARGV" \
    || ! grep -qx $'5\t'"$DECISION_SCRIPT" "$DECISION_ARGV"; then
  echo "Expected decision helper override delegation" >&2
  cat "${TMP_DIR}/decision.out" >&2
  cat "$DECISION_ARGV" >&2
  exit 1
fi

REGRESSION_DIAGNOSE_ARGV="${TMP_DIR}/regression-diagnose-argv.tsv"
AQLI_FAKE_PERFORMANCE_ARGV="$REGRESSION_DIAGNOSE_ARGV" \
  bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --model fake-model \
    --baseline baseline.tsv \
    --regression-script "$REGRESSION_SCRIPT" \
    --diagnose-script "$DIAGNOSE_SCRIPT" >"${TMP_DIR}/regression-diagnose.out"

if ! grep -qx 'mode=regression' "${TMP_DIR}/regression-diagnose.out" \
    || ! grep -qx $'script\tregression' "$REGRESSION_DIAGNOSE_ARGV" \
    || ! grep -qx $'4\t--diagnose-script' "$REGRESSION_DIAGNOSE_ARGV" \
    || ! grep -qx $'5\t'"$DIAGNOSE_SCRIPT" "$REGRESSION_DIAGNOSE_ARGV"; then
  echo "Expected regression diagnosis helper override delegation" >&2
  cat "${TMP_DIR}/regression-diagnose.out" >&2
  cat "$REGRESSION_DIAGNOSE_ARGV" >&2
  exit 1
fi

PRESET_ARGV="${TMP_DIR}/preset-argv.tsv"
AQLI_FAKE_PERFORMANCE_ARGV="$PRESET_ARGV" \
  bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --preset coreml-stable \
    --model fake-model \
    --baseline baseline.tsv \
    --runs 2 \
    --regression-script "$REGRESSION_SCRIPT" >"${TMP_DIR}/preset.out"

if ! grep -qx 'mode=regression' "${TMP_DIR}/preset.out" \
    || ! grep -qx 'preset=coreml-stable' "${TMP_DIR}/preset.out" \
    || ! grep -qx $'script\tregression' "$PRESET_ARGV" \
    || ! grep -qx $'0\t--runs' "$PRESET_ARGV" \
    || ! grep -qx $'1\t5' "$PRESET_ARGV" \
    || ! grep -qx $'2\t--warmup-runs' "$PRESET_ARGV" \
    || ! grep -qx $'3\t1' "$PRESET_ARGV" \
    || ! grep -qx $'4\t--expect-backend' "$PRESET_ARGV" \
    || ! grep -qx $'5\tCoreML' "$PRESET_ARGV" \
    || ! grep -qx $'6\t--max-noise-percent' "$PRESET_ARGV" \
    || ! grep -qx $'7\t10' "$PRESET_ARGV" \
    || ! grep -qx $'8\t--noise-metrics' "$PRESET_ARGV" \
    || ! grep -qx $'9\tdurationMs,generationTps,onnxOrtRunMs' "$PRESET_ARGV" \
    || ! grep -qx $'10\t--model' "$PRESET_ARGV" \
    || ! grep -qx $'11\tfake-model' "$PRESET_ARGV" \
    || ! grep -qx $'12\t--baseline' "$PRESET_ARGV" \
    || ! grep -qx $'13\tbaseline.tsv' "$PRESET_ARGV" \
    || ! grep -qx $'14\t--runs' "$PRESET_ARGV" \
    || ! grep -qx $'15\t2' "$PRESET_ARGV"; then
  echo "Expected preset defaults before user overrides" >&2
  cat "${TMP_DIR}/preset.out" >&2
  cat "$PRESET_ARGV" >&2
  exit 1
fi

GATE_PRESET_ARGV="${TMP_DIR}/gate-preset-argv.tsv"
AQLI_FAKE_PERFORMANCE_ARGV="$GATE_PRESET_ARGV" \
  bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --preset stable \
    --model fake-model \
    --gate-script "$GATE_SCRIPT" >"${TMP_DIR}/gate-preset.out"

if ! grep -qx 'mode=gate' "${TMP_DIR}/gate-preset.out" \
    || ! grep -qx 'preset=stable' "${TMP_DIR}/gate-preset.out" \
    || grep -q -- '--max-noise-percent' "$GATE_PRESET_ARGV" \
    || grep -q -- '--noise-metrics' "$GATE_PRESET_ARGV" \
    || ! grep -qx $'0\t--runs' "$GATE_PRESET_ARGV" \
    || ! grep -qx $'1\t5' "$GATE_PRESET_ARGV" \
    || ! grep -qx $'2\t--warmup-runs' "$GATE_PRESET_ARGV" \
    || ! grep -qx $'3\t1' "$GATE_PRESET_ARGV"; then
  echo "Expected gate preset without noise-only arguments" >&2
  cat "${TMP_DIR}/gate-preset.out" >&2
  cat "$GATE_PRESET_ARGV" >&2
  exit 1
fi

CAPTURE_ARGV="${TMP_DIR}/capture-argv.tsv"
AQLI_FAKE_PERFORMANCE_ARGV="$CAPTURE_ARGV" \
  bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --model fake-model \
    --capture-baseline \
    --gate-script "$GATE_SCRIPT" \
    --regression-script "$REGRESSION_SCRIPT" \
    --capture-script "$CAPTURE_SCRIPT" \
    --baseline-root baselines \
    --name webworld \
    --max-noise-percent 15 \
    --decision-script "$DECISION_SCRIPT" >"${TMP_DIR}/capture.out"

if ! grep -qx 'mode=capture' "${TMP_DIR}/capture.out" \
    || ! grep -qx $'script\tcapture' "$CAPTURE_ARGV" \
    || grep -q -- '--capture-baseline' "$CAPTURE_ARGV" \
    || ! grep -qx $'2\t--max-noise-percent' "$CAPTURE_ARGV" \
    || ! grep -qx $'3\t15' "$CAPTURE_ARGV" \
    || ! grep -qx $'4\t--decision-script' "$CAPTURE_ARGV" \
    || ! grep -qx $'5\t'"$DECISION_SCRIPT" "$CAPTURE_ARGV" \
    || ! grep -qx $'6\t--baseline-root' "$CAPTURE_ARGV" \
    || ! grep -qx $'7\tbaselines' "$CAPTURE_ARGV" \
    || ! grep -qx $'8\t--name' "$CAPTURE_ARGV" \
    || ! grep -qx $'9\twebworld' "$CAPTURE_ARGV"; then
  echo "Expected capture mode delegation" >&2
  cat "${TMP_DIR}/capture.out" >&2
  cat "$CAPTURE_ARGV" >&2
  exit 1
fi

LATEST_ROOT="${TMP_DIR}/baselines"
mkdir -p "$LATEST_ROOT/webworld"
printf 'latest baseline\n' > "$LATEST_ROOT/webworld/latest.tsv"

bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
  --list-baselines \
  --baseline-root "$LATEST_ROOT" >"${TMP_DIR}/list-baselines.tsv"
if ! grep -qx $'name\tstatus\tlatest\tmanifest\tmodel\tonnxBackend\texpectBackend\tmaxTokens\truns\twarmupRuns\tprompt\taggregateLabel\tdurationMs\tgenerationTps\tdecodeTps\tttftMs\ttokenLatencyMs\tonnxOrtRunMs\tonnxDecodeRunMs\tprimaryStage\tprimaryMetric\tprimaryValueMs\tprimaryShareOfOrtPercent\tupdatedUtc' "${TMP_DIR}/list-baselines.tsv" \
    || ! grep -q '^webworld	missing-manifest	' "${TMP_DIR}/list-baselines.tsv"; then
  echo "Expected facade baseline list" >&2
  cat "${TMP_DIR}/list-baselines.tsv" >&2
  exit 1
fi

bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
  --list-baselines \
  --baseline-root "$LATEST_ROOT" \
  --format table \
  --sort name >"${TMP_DIR}/list-baselines-table.txt"
if ! grep -q '^name[[:space:]][[:space:]]*status[[:space:]][[:space:]]*onnxBackend' "${TMP_DIR}/list-baselines-table.txt" \
    || ! grep -q '^webworld[[:space:]][[:space:]]*missing-manifest' "${TMP_DIR}/list-baselines-table.txt"; then
  echo "Expected facade baseline table list" >&2
  cat "${TMP_DIR}/list-baselines-table.txt" >&2
  exit 1
fi

LATEST_ARGV="${TMP_DIR}/latest-argv.tsv"
AQLI_FAKE_PERFORMANCE_ARGV="$LATEST_ARGV" \
  bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --model fake-model \
    --use-latest-baseline \
    --baseline-root "$LATEST_ROOT" \
    --name webworld \
    --gate-script "$GATE_SCRIPT" \
    --regression-script "$REGRESSION_SCRIPT" \
    --capture-script "$CAPTURE_SCRIPT" >"${TMP_DIR}/latest.out"

if ! grep -qx 'mode=regression' "${TMP_DIR}/latest.out" \
    || ! grep -qx "baseline=$LATEST_ROOT/webworld/latest.tsv" "${TMP_DIR}/latest.out" \
    || ! grep -qx $'script\tregression' "$LATEST_ARGV" \
    || ! grep -qx $'0\t--model' "$LATEST_ARGV" \
    || ! grep -qx $'1\tfake-model' "$LATEST_ARGV" \
    || ! grep -qx $'2\t--baseline' "$LATEST_ARGV" \
    || ! grep -qx $'3\t'"$LATEST_ROOT"'/webworld/latest.tsv' "$LATEST_ARGV"; then
  echo "Expected latest baseline delegation" >&2
  cat "${TMP_DIR}/latest.out" >&2
  cat "$LATEST_ARGV" >&2
  exit 1
fi

DRY_PLAN="${TMP_DIR}/plans/latest-plan.tsv"
DRY_EXEC_ARGV="${TMP_DIR}/dry-exec-argv.tsv"
AQLI_FAKE_PERFORMANCE_ARGV="$DRY_EXEC_ARGV" \
  bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --model fake-model \
    --use-latest-baseline \
    --baseline-root "$LATEST_ROOT" \
    --name webworld \
    --regression-script "$REGRESSION_SCRIPT" \
    --max-noise-percent 9 \
    --dry-run \
    --plan-out "$DRY_PLAN" >"${TMP_DIR}/dry.out"

if [[ -f "$DRY_EXEC_ARGV" ]]; then
  echo "Expected dry-run not to execute selected script" >&2
  cat "$DRY_EXEC_ARGV" >&2
  exit 1
fi
if ! grep -qx 'mode=regression' "${TMP_DIR}/dry.out" \
    || ! grep -qx "baseline=$LATEST_ROOT/webworld/latest.tsv" "${TMP_DIR}/dry.out" \
    || ! grep -qx "plan=$DRY_PLAN" "${TMP_DIR}/dry.out" \
    || ! grep -qx 'dryRun=true' "${TMP_DIR}/dry.out"; then
  echo "Expected dry-run output" >&2
  cat "${TMP_DIR}/dry.out" >&2
  exit 1
fi
if ! grep -qx $'section\tkey\tvalue' "$DRY_PLAN" \
    || ! grep -qx $'config\tmode\tregression' "$DRY_PLAN" \
    || ! grep -qx $'config\tscript\t'"$REGRESSION_SCRIPT" "$DRY_PLAN" \
    || ! grep -qx $'config\tmodel\tfake-model' "$DRY_PLAN" \
    || ! grep -qx $'config\tbaseline\t'"$LATEST_ROOT"'/webworld/latest.tsv' "$DRY_PLAN" \
    || ! grep -qx $'config\tbaselineRoot\t'"$LATEST_ROOT" "$DRY_PLAN" \
    || ! grep -qx $'config\tbaselineName\twebworld' "$DRY_PLAN" \
    || ! grep -qx $'config\tbaselineScript\t'"$ROOT_DIR"'/scripts/onnx-performance-baselines.sh' "$DRY_PLAN" \
    || ! grep -qx $'config\tpresetScript\t'"$ROOT_DIR"'/scripts/onnx-performance-presets.sh' "$DRY_PLAN" \
    || ! grep -qx $'config\tdryRun\t1' "$DRY_PLAN" \
    || ! grep -qx $'argv\t0\t'"$REGRESSION_SCRIPT" "$DRY_PLAN" \
    || ! grep -qx $'argv\t1\t--model' "$DRY_PLAN" \
    || ! grep -qx $'argv\t2\tfake-model' "$DRY_PLAN" \
    || ! grep -qx $'argv\t3\t--max-noise-percent' "$DRY_PLAN" \
    || ! grep -qx $'argv\t4\t9' "$DRY_PLAN" \
    || ! grep -qx $'argv\t5\t--baseline' "$DRY_PLAN" \
    || ! grep -qx $'argv\t6\t'"$LATEST_ROOT"'/webworld/latest.tsv' "$DRY_PLAN"; then
  echo "Expected dry-run plan TSV" >&2
  cat "$DRY_PLAN" >&2
  exit 1
fi

AUTO_ROOT="${TMP_DIR}/auto-baselines"
mkdir -p "$AUTO_ROOT/onnx-community_WebWorld-8B-Onnx"
printf 'latest baseline\n' > "$AUTO_ROOT/onnx-community_WebWorld-8B-Onnx/latest.tsv"
AUTO_ARGV="${TMP_DIR}/auto-argv.tsv"
AQLI_FAKE_PERFORMANCE_ARGV="$AUTO_ARGV" \
  bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --model onnx-community/WebWorld-8B-Onnx \
    --baseline auto \
    --baseline-root "$AUTO_ROOT" \
    --gate-script "$GATE_SCRIPT" \
    --regression-script "$REGRESSION_SCRIPT" \
    --capture-script "$CAPTURE_SCRIPT" >"${TMP_DIR}/auto.out"

if ! grep -qx 'mode=regression' "${TMP_DIR}/auto.out" \
    || ! grep -qx "baseline=$AUTO_ROOT/onnx-community_WebWorld-8B-Onnx/latest.tsv" "${TMP_DIR}/auto.out" \
    || ! grep -qx $'3\t'"$AUTO_ROOT"'/onnx-community_WebWorld-8B-Onnx/latest.tsv' "$AUTO_ARGV"; then
  echo "Expected auto baseline slug delegation" >&2
  cat "${TMP_DIR}/auto.out" >&2
  cat "$AUTO_ARGV" >&2
  exit 1
fi

COMPARE_ROOT="${TMP_DIR}/compare-baselines"
mkdir -p "$COMPARE_ROOT/before" "$COMPARE_ROOT/after"
printf 'before\n' > "$COMPARE_ROOT/before/latest.tsv"
printf 'after\n' > "$COMPARE_ROOT/after/latest.tsv"
COMPARE_ARGV="${TMP_DIR}/compare-argv.tsv"
COMPARE_DECISION="${TMP_DIR}/compare-decision.tsv"
AQLI_FAKE_PERFORMANCE_ARGV="$COMPARE_ARGV" \
  bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --compare-baselines before after \
    --baseline-root "$COMPARE_ROOT" \
    --compare-script "$COMPARE_SCRIPT" \
    --max-regression-percent 7 \
    --metrics durationMs \
    --decision-out "$COMPARE_DECISION" \
    --decision-script "$DECISION_SCRIPT" >"${TMP_DIR}/compare.out"

if ! grep -qx 'mode=compare-baselines' "${TMP_DIR}/compare.out" \
    || ! grep -qx "baseline=$COMPARE_ROOT/before/latest.tsv" "${TMP_DIR}/compare.out" \
    || ! grep -qx "current=$COMPARE_ROOT/after/latest.tsv" "${TMP_DIR}/compare.out" \
    || ! grep -qx $'script\tcompare' "$COMPARE_ARGV" \
    || ! grep -qx $'0\t--max-regression-percent' "$COMPARE_ARGV" \
    || ! grep -qx $'1\t7' "$COMPARE_ARGV" \
    || ! grep -qx $'2\t--metrics' "$COMPARE_ARGV" \
    || ! grep -qx $'3\tdurationMs' "$COMPARE_ARGV" \
    || ! grep -qx $'4\t--decision-out' "$COMPARE_ARGV" \
    || ! grep -qx $'5\t'"$COMPARE_DECISION" "$COMPARE_ARGV" \
    || ! grep -qx $'6\t--decision-script' "$COMPARE_ARGV" \
    || ! grep -qx $'7\t'"$DECISION_SCRIPT" "$COMPARE_ARGV" \
    || ! grep -qx $'8\t--baseline' "$COMPARE_ARGV" \
    || ! grep -qx $'9\t'"$COMPARE_ROOT"'/before/latest.tsv' "$COMPARE_ARGV" \
    || ! grep -qx $'10\t--current' "$COMPARE_ARGV" \
    || ! grep -qx $'11\t'"$COMPARE_ROOT"'/after/latest.tsv' "$COMPARE_ARGV"; then
  echo "Expected compare-baselines delegation" >&2
  cat "${TMP_DIR}/compare.out" >&2
  cat "$COMPARE_ARGV" >&2
  exit 1
fi

BUNDLE_PATH="${TMP_DIR}/bundle.tsv"
BUNDLE_SUMMARY="${TMP_DIR}/bundle-summary.tsv"
printf 'kind\tpath\trequired\tstatus\tdescription\n' > "$BUNDLE_PATH"
BUNDLE_ARGV="${TMP_DIR}/bundle-argv.tsv"
AQLI_FAKE_PERFORMANCE_ARGV="$BUNDLE_ARGV" \
  bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --verify-bundle "$BUNDLE_PATH" \
    --bundle-summary-out "$BUNDLE_SUMMARY" \
    --bundle-verify-script "$BUNDLE_VERIFY_SCRIPT" >"${TMP_DIR}/bundle.out"

if ! grep -qx 'mode=verify-bundle' "${TMP_DIR}/bundle.out" \
    || ! grep -qx "bundle=$BUNDLE_PATH" "${TMP_DIR}/bundle.out" \
    || ! grep -qx "bundleSummary=$BUNDLE_SUMMARY" "${TMP_DIR}/bundle.out" \
    || ! grep -qx $'script\tbundle-verify' "$BUNDLE_ARGV" \
    || ! grep -qx $'0\t--bundle' "$BUNDLE_ARGV" \
    || ! grep -qx $'1\t'"$BUNDLE_PATH" "$BUNDLE_ARGV" \
    || ! grep -qx $'2\t--summary-out' "$BUNDLE_ARGV" \
    || ! grep -qx $'3\t'"$BUNDLE_SUMMARY" "$BUNDLE_ARGV"; then
  echo "Expected bundle verification mode delegation" >&2
  cat "${TMP_DIR}/bundle.out" >&2
  cat "$BUNDLE_ARGV" >&2
  exit 1
fi

BUNDLE_PLAN="${TMP_DIR}/plans/bundle-plan.tsv"
bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
  --verify-bundle "$BUNDLE_PATH" \
  --bundle-summary-out "$BUNDLE_SUMMARY" \
  --bundle-verify-script "$BUNDLE_VERIFY_SCRIPT" \
  --dry-run \
  --plan-out "$BUNDLE_PLAN" >"${TMP_DIR}/bundle-dry.out"

if ! grep -qx 'mode=verify-bundle' "${TMP_DIR}/bundle-dry.out" \
    || ! grep -qx "bundle=$BUNDLE_PATH" "${TMP_DIR}/bundle-dry.out" \
    || ! grep -qx "bundleSummary=$BUNDLE_SUMMARY" "${TMP_DIR}/bundle-dry.out" \
    || ! grep -qx "plan=$BUNDLE_PLAN" "${TMP_DIR}/bundle-dry.out" \
    || ! grep -qx 'dryRun=true' "${TMP_DIR}/bundle-dry.out" \
    || ! grep -qx $'config\tmode\tverify-bundle' "$BUNDLE_PLAN" \
    || ! grep -qx $'config\tscript\t'"$BUNDLE_VERIFY_SCRIPT" "$BUNDLE_PLAN" \
    || ! grep -qx $'config\tverifyBundle\t'"$BUNDLE_PATH" "$BUNDLE_PLAN" \
    || ! grep -qx $'config\tbundleSummaryOut\t'"$BUNDLE_SUMMARY" "$BUNDLE_PLAN" \
    || ! grep -qx $'argv\t1\t--bundle' "$BUNDLE_PLAN" \
    || ! grep -qx $'argv\t2\t'"$BUNDLE_PATH" "$BUNDLE_PLAN" \
    || ! grep -qx $'argv\t3\t--summary-out' "$BUNDLE_PLAN" \
    || ! grep -qx $'argv\t4\t'"$BUNDLE_SUMMARY" "$BUNDLE_PLAN"; then
  echo "Expected bundle dry-run plan TSV" >&2
  cat "${TMP_DIR}/bundle-dry.out" >&2
  cat "$BUNDLE_PLAN" >&2
  exit 1
fi

DIAGNOSE_SUMMARY="${TMP_DIR}/profile-aggregate.tsv"
DIAGNOSIS_DIR="${TMP_DIR}/diagnosis"
DIAGNOSIS_OUT="${DIAGNOSIS_DIR}/diagnosis.tsv"
DIAGNOSIS_STAGES_OUT="${DIAGNOSIS_DIR}/stages.tsv"
printf 'case\tstatus\nmeasured-mean\tpass\n' > "$DIAGNOSE_SUMMARY"
DIAGNOSE_ARGV="${TMP_DIR}/diagnose-argv.tsv"
AQLI_FAKE_PERFORMANCE_ARGV="$DIAGNOSE_ARGV" \
  bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --diagnose-summary "$DIAGNOSE_SUMMARY" \
    --diagnosis-summary-dir "$DIAGNOSIS_DIR" \
    --diagnosis-out "$DIAGNOSIS_OUT" \
    --diagnosis-stages-out "$DIAGNOSIS_STAGES_OUT" \
    --diagnosis-case measured-mean \
    --diagnosis-include-warmup \
    --diagnose-script "$DIAGNOSE_SCRIPT" >"${TMP_DIR}/diagnose.out"

if ! grep -qx 'mode=diagnose' "${TMP_DIR}/diagnose.out" \
    || ! grep -qx "summary=$DIAGNOSE_SUMMARY" "${TMP_DIR}/diagnose.out" \
    || ! grep -qx "diagnosis=$DIAGNOSIS_OUT" "${TMP_DIR}/diagnose.out" \
    || ! grep -qx "stages=$DIAGNOSIS_STAGES_OUT" "${TMP_DIR}/diagnose.out" \
    || ! grep -qx $'script\tdiagnose' "$DIAGNOSE_ARGV" \
    || ! grep -qx $'0\t--summary' "$DIAGNOSE_ARGV" \
    || ! grep -qx $'1\t'"$DIAGNOSE_SUMMARY" "$DIAGNOSE_ARGV" \
    || ! grep -qx $'2\t--summary-dir' "$DIAGNOSE_ARGV" \
    || ! grep -qx $'3\t'"$DIAGNOSIS_DIR" "$DIAGNOSE_ARGV" \
    || ! grep -qx $'4\t--out' "$DIAGNOSE_ARGV" \
    || ! grep -qx $'5\t'"$DIAGNOSIS_OUT" "$DIAGNOSE_ARGV" \
    || ! grep -qx $'6\t--stages-out' "$DIAGNOSE_ARGV" \
    || ! grep -qx $'7\t'"$DIAGNOSIS_STAGES_OUT" "$DIAGNOSE_ARGV" \
    || ! grep -qx $'8\t--case' "$DIAGNOSE_ARGV" \
    || ! grep -qx $'9\tmeasured-mean' "$DIAGNOSE_ARGV" \
    || ! grep -qx $'10\t--include-warmup' "$DIAGNOSE_ARGV"; then
  echo "Expected diagnose mode delegation" >&2
  cat "${TMP_DIR}/diagnose.out" >&2
  cat "$DIAGNOSE_ARGV" >&2
  exit 1
fi

DIAGNOSE_PLAN="${TMP_DIR}/plans/diagnose-plan.tsv"
bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
  --diagnose-summary "$DIAGNOSE_SUMMARY" \
  --diagnosis-out "$DIAGNOSIS_OUT" \
  --diagnose-script "$DIAGNOSE_SCRIPT" \
  --dry-run \
  --plan-out "$DIAGNOSE_PLAN" >"${TMP_DIR}/diagnose-dry.out"

if ! grep -qx 'mode=diagnose' "${TMP_DIR}/diagnose-dry.out" \
    || ! grep -qx "summary=$DIAGNOSE_SUMMARY" "${TMP_DIR}/diagnose-dry.out" \
    || ! grep -qx "diagnosis=$DIAGNOSIS_OUT" "${TMP_DIR}/diagnose-dry.out" \
    || ! grep -qx "plan=$DIAGNOSE_PLAN" "${TMP_DIR}/diagnose-dry.out" \
    || ! grep -qx 'dryRun=true' "${TMP_DIR}/diagnose-dry.out" \
    || ! grep -qx $'config\tmode\tdiagnose' "$DIAGNOSE_PLAN" \
    || ! grep -qx $'config\tscript\t'"$DIAGNOSE_SCRIPT" "$DIAGNOSE_PLAN" \
    || ! grep -qx $'config\tdiagnoseSummary\t'"$DIAGNOSE_SUMMARY" "$DIAGNOSE_PLAN" \
    || ! grep -qx $'config\tdiagnoseScript\t'"$DIAGNOSE_SCRIPT" "$DIAGNOSE_PLAN" \
    || ! grep -qx $'config\tdiagnosisOut\t'"$DIAGNOSIS_OUT" "$DIAGNOSE_PLAN" \
    || ! grep -qx $'argv\t1\t--summary' "$DIAGNOSE_PLAN" \
    || ! grep -qx $'argv\t2\t'"$DIAGNOSE_SUMMARY" "$DIAGNOSE_PLAN" \
    || ! grep -qx $'argv\t3\t--out' "$DIAGNOSE_PLAN" \
    || ! grep -qx $'argv\t4\t'"$DIAGNOSIS_OUT" "$DIAGNOSE_PLAN"; then
  echo "Expected diagnose dry-run plan TSV" >&2
  cat "${TMP_DIR}/diagnose-dry.out" >&2
  cat "$DIAGNOSE_PLAN" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --compare-baselines before missing \
    --baseline-root "$COMPARE_ROOT" \
    --compare-script "$COMPARE_SCRIPT" >"${TMP_DIR}/compare-missing.out" 2>"${TMP_DIR}/compare-missing.err"; then
  echo "Expected missing compare baseline failure" >&2
  cat "${TMP_DIR}/compare-missing.out" >&2
  cat "${TMP_DIR}/compare-missing.err" >&2
  exit 1
fi
if ! grep -qx 'Compare current baseline not found: missing' "${TMP_DIR}/compare-missing.err"; then
  echo "Expected missing compare baseline error" >&2
  cat "${TMP_DIR}/compare-missing.err" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --verify-bundle "$BUNDLE_PATH" \
    --model fake-model \
    --bundle-verify-script "$BUNDLE_VERIFY_SCRIPT" >"${TMP_DIR}/bundle-mixed.out" 2>"${TMP_DIR}/bundle-mixed.err"; then
  echo "Expected verify-bundle mixed mode failure" >&2
  cat "${TMP_DIR}/bundle-mixed.out" >&2
  cat "${TMP_DIR}/bundle-mixed.err" >&2
  exit 1
fi
if ! grep -q '^--verify-bundle is mutually exclusive with model, baseline, capture, compare, and diagnose modes$' "${TMP_DIR}/bundle-mixed.err"; then
  echo "Expected verify-bundle mixed mode error" >&2
  cat "${TMP_DIR}/bundle-mixed.err" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --diagnose-summary "$DIAGNOSE_SUMMARY" \
    --model fake-model \
    --diagnose-script "$DIAGNOSE_SCRIPT" >"${TMP_DIR}/diagnose-mixed.out" 2>"${TMP_DIR}/diagnose-mixed.err"; then
  echo "Expected diagnose mixed mode failure" >&2
  cat "${TMP_DIR}/diagnose-mixed.out" >&2
  cat "${TMP_DIR}/diagnose-mixed.err" >&2
  exit 1
fi
if ! grep -q '^--diagnose-summary is mutually exclusive with model, baseline, capture, compare, and bundle modes$' "${TMP_DIR}/diagnose-mixed.err"; then
  echo "Expected diagnose mixed mode error" >&2
  cat "${TMP_DIR}/diagnose-mixed.err" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --compare-baselines before after \
    --baseline-root "$COMPARE_ROOT" \
    --diagnose-script "$DIAGNOSE_SCRIPT" \
    --compare-script "$COMPARE_SCRIPT" >"${TMP_DIR}/compare-diagnose-script.out" 2>"${TMP_DIR}/compare-diagnose-script.err"; then
  echo "Expected compare diagnose-script failure" >&2
  cat "${TMP_DIR}/compare-diagnose-script.out" >&2
  cat "${TMP_DIR}/compare-diagnose-script.err" >&2
  exit 1
fi
if ! grep -q '^--diagnose-script is only supported with --diagnose-summary, --capture-baseline, or regression modes$' "${TMP_DIR}/compare-diagnose-script.err"; then
  echo "Expected compare diagnose-script error" >&2
  cat "${TMP_DIR}/compare-diagnose-script.err" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --verify-bundle "$BUNDLE_PATH" \
    --preset stable \
    --bundle-verify-script "$BUNDLE_VERIFY_SCRIPT" >"${TMP_DIR}/bundle-preset.out" 2>"${TMP_DIR}/bundle-preset.err"; then
  echo "Expected verify-bundle preset failure" >&2
  cat "${TMP_DIR}/bundle-preset.out" >&2
  cat "${TMP_DIR}/bundle-preset.err" >&2
  exit 1
fi
if ! grep -qx '^--preset is not supported with --verify-bundle$' "${TMP_DIR}/bundle-preset.err"; then
  echo "Expected verify-bundle preset error" >&2
  cat "${TMP_DIR}/bundle-preset.err" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --model missing-model \
    --use-latest-baseline \
    --baseline-root "$LATEST_ROOT" \
    --gate-script "$GATE_SCRIPT" \
    --regression-script "$REGRESSION_SCRIPT" >"${TMP_DIR}/missing-latest.out" 2>"${TMP_DIR}/missing-latest.err"; then
  echo "Expected missing latest baseline failure" >&2
  cat "${TMP_DIR}/missing-latest.out" >&2
  cat "${TMP_DIR}/missing-latest.err" >&2
  exit 1
fi
if ! grep -q '^Latest ONNX baseline not found: ' "${TMP_DIR}/missing-latest.err" \
    || ! grep -q '^Capture it first with --capture-baseline, or pass --baseline PATH\.$' "${TMP_DIR}/missing-latest.err"; then
  echo "Expected missing latest baseline error" >&2
  cat "${TMP_DIR}/missing-latest.err" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --baseline baseline.tsv \
    --gate-script "$GATE_SCRIPT" >"${TMP_DIR}/missing-model.out" 2>"${TMP_DIR}/missing-model.err"; then
  echo "Expected missing model failure" >&2
  cat "${TMP_DIR}/missing-model.out" >&2
  cat "${TMP_DIR}/missing-model.err" >&2
  exit 1
fi
if ! grep -q '^--model is required$' "${TMP_DIR}/missing-model.err"; then
  echo "Expected missing model error" >&2
  cat "${TMP_DIR}/missing-model.err" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --compare-baselines before after \
    --baseline-root "$COMPARE_ROOT" \
    --preset stable \
    --compare-script "$COMPARE_SCRIPT" >"${TMP_DIR}/compare-preset.out" 2>"${TMP_DIR}/compare-preset.err"; then
  echo "Expected compare preset failure" >&2
  cat "${TMP_DIR}/compare-preset.out" >&2
  cat "${TMP_DIR}/compare-preset.err" >&2
  exit 1
fi
if ! grep -qx '^--preset is not supported with --compare-baselines$' "${TMP_DIR}/compare-preset.err"; then
  echo "Expected compare preset error" >&2
  cat "${TMP_DIR}/compare-preset.err" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --model fake-model \
    --preset maybe \
    --gate-script "$GATE_SCRIPT" >"${TMP_DIR}/bad-preset.out" 2>"${TMP_DIR}/bad-preset.err"; then
  echo "Expected bad preset failure" >&2
  cat "${TMP_DIR}/bad-preset.out" >&2
  cat "${TMP_DIR}/bad-preset.err" >&2
  exit 1
fi
if ! grep -q '^Unknown --preset: maybe$' "${TMP_DIR}/bad-preset.err" \
    || ! grep -q '^Available presets: quick, stable, coreml-stable$' "${TMP_DIR}/bad-preset.err"; then
  echo "Expected bad preset error" >&2
  cat "${TMP_DIR}/bad-preset.err" >&2
  exit 1
fi

if bash "$ROOT_DIR/scripts/check-onnx-performance.sh" \
    --model fake-model \
    --baseline baseline.tsv \
    --capture-baseline \
    --gate-script "$GATE_SCRIPT" >"${TMP_DIR}/exclusive.out" 2>"${TMP_DIR}/exclusive.err"; then
  echo "Expected mutually exclusive mode failure" >&2
  cat "${TMP_DIR}/exclusive.out" >&2
  cat "${TMP_DIR}/exclusive.err" >&2
  exit 1
fi
if ! grep -q '^--baseline/--use-latest-baseline and --capture-baseline are mutually exclusive$' "${TMP_DIR}/exclusive.err"; then
  echo "Expected mutually exclusive mode error" >&2
  cat "${TMP_DIR}/exclusive.err" >&2
  exit 1
fi

printf 'ONNX performance facade test passed\n'
