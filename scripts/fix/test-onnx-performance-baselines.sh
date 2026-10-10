#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-onnx-performance-baselines.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

BASELINES_SCRIPT="$ROOT_DIR/scripts/onnx-performance-baselines.sh"
BASELINE_ROOT="${TMP_DIR}/baselines"

bash "$BASELINES_SCRIPT" list "$BASELINE_ROOT" >"${TMP_DIR}/empty.tsv"
if [[ "$(wc -l < "${TMP_DIR}/empty.tsv" | tr -d ' ')" != "1" ]] \
    || ! grep -qx $'name\tstatus\tlatest\tmanifest\tmodel\tonnxBackend\texpectBackend\tmaxTokens\truns\twarmupRuns\tprompt\taggregateLabel\tdurationMs\tgenerationTps\tdecodeTps\tttftMs\ttokenLatencyMs\tonnxOrtRunMs\tonnxDecodeRunMs\tprimaryStage\tprimaryMetric\tprimaryValueMs\tprimaryShareOfOrtPercent\tupdatedUtc' "${TMP_DIR}/empty.tsv"; then
  echo "Expected empty baseline list header" >&2
  cat "${TMP_DIR}/empty.tsv" >&2
  exit 1
fi

mkdir -p "$BASELINE_ROOT/webworld" "$BASELINE_ROOT/fast" "$BASELINE_ROOT/no-manifest" "$BASELINE_ROOT/no-latest"
{
  printf 'case\tstatus\texitCode\tdurationMs\tpromptEvalTps\tgenerationTps\tdecodeTps\tttftMs\ttokenLatencyMs\tonnxBackend\tonnxTokenizeMs\tonnxInputPrepMs\tonxUnusedMs\tonnxPrefillRunMs\tonnxDecodeRunMs\tonnxOrtRunMs\tonnxLogitsSelectMs\tonnxSamplingMs\tonnxSteps\tcombinedLog\tstderrLog\n'
  printf 'measured-mean\tpass\t\t1100.000\t19.000\t9.000\t7.500\t220.000\t55.000\tCoreML\t2.500\t4.000\t\t22.500\t37.500\t70.000\t5.000\t6.000\t2.500\t/tmp/summary.tsv\t\n'
  printf 'measured-best\tpass\t\t1000.000\t20.000\t10.000\t8.000\t200.000\t50.000\tCoreML\t2.000\t3.000\t\t20.000\t30.000\t60.000\t4.000\t5.000\t2.000\t/tmp/summary.tsv\t\n'
  printf 'measured-worst\tpass\t\t1200.000\t18.000\t8.000\t7.000\t240.000\t60.000\tCoreML\t3.000\t5.000\t\t25.000\t45.000\t80.000\t6.000\t7.000\t3.000\t/tmp/summary.tsv\t\n'
} > "$BASELINE_ROOT/webworld/latest.tsv"
{
  printf 'key\tvalue\n'
  printf 'model\tonnx-community/WebWorld-8B-Onnx\n'
  printf 'prompt\twhere is jakarta\n'
  printf 'maxTokens\t8\n'
  printf 'runs\t5\n'
  printf 'warmupRuns\t1\n'
  printf 'onnxBackend\tCoreML\n'
  printf 'expectBackend\tCoreML\n'
  printf 'aggregateLabel\tmeasured\n'
  printf 'decision\t%s\n' "$BASELINE_ROOT/webworld/decision.tsv"
  printf 'diagnosis\t%s\n' "$BASELINE_ROOT/webworld/diagnosis.tsv"
} > "$BASELINE_ROOT/webworld/latest-manifest.tsv"
{
  printf 'key\tvalue\n'
  printf 'primaryStage\tdecodeRun\n'
  printf 'primaryMetric\tonnxDecodeRunMs\n'
  printf 'primaryValueMs\t37.500\n'
  printf 'primaryShareOfOrtPercent\t53.571\n'
} > "$BASELINE_ROOT/webworld/decision.tsv"
{
  printf 'key\tvalue\n'
  printf 'primaryStage\tprefillRun\n'
  printf 'primaryMetric\tonnxPrefillRunMs\n'
  printf 'primaryValueMs\t22.500\n'
  printf 'primaryShareOfOrtPercent\t32.143\n'
} > "$BASELINE_ROOT/webworld/diagnosis.tsv"
{
  printf 'case\tstatus\texitCode\tdurationMs\tpromptEvalTps\tgenerationTps\tdecodeTps\tttftMs\ttokenLatencyMs\tonnxBackend\tonnxTokenizeMs\tonnxInputPrepMs\tonxUnusedMs\tonnxPrefillRunMs\tonnxDecodeRunMs\tonnxOrtRunMs\tonnxLogitsSelectMs\tonnxSamplingMs\tonnxSteps\tcombinedLog\tstderrLog\n'
  printf 'measured-mean\tpass\t\t900.000\t21.000\t11.000\t8.500\t180.000\t45.000\tCPU\t2.000\t3.000\t\t18.000\t28.000\t58.000\t4.000\t5.000\t2.000\t/tmp/summary-fast.tsv\t\n'
  printf 'measured-best\tpass\t\t850.000\t22.000\t12.000\t9.000\t170.000\t40.000\tCPU\t1.500\t2.500\t\t16.000\t25.000\t52.000\t3.000\t4.000\t2.000\t/tmp/summary-fast.tsv\t\n'
  printf 'measured-worst\tpass\t\t950.000\t20.000\t10.000\t8.000\t190.000\t50.000\tCPU\t2.500\t3.500\t\t20.000\t31.000\t64.000\t5.000\t6.000\t2.000\t/tmp/summary-fast.tsv\t\n'
} > "$BASELINE_ROOT/fast/latest.tsv"
{
  printf 'key\tvalue\n'
  printf 'model\tfast-model\n'
  printf 'prompt\twhere is jakarta\n'
  printf 'maxTokens\t4\n'
  printf 'runs\t3\n'
  printf 'warmupRuns\t1\n'
  printf 'onnxBackend\tCPU\n'
  printf 'expectBackend\tCPU\n'
  printf 'aggregateLabel\tmeasured\n'
  printf 'diagnosis\tdiagnosis.tsv\n'
} > "$BASELINE_ROOT/fast/latest-manifest.tsv"
{
  printf 'key\tvalue\n'
  printf 'primaryStage\tprefillRun\n'
  printf 'primaryMetric\tonnxPrefillRunMs\n'
  printf 'primaryValueMs\t18.000\n'
  printf 'primaryShareOfOrtPercent\t31.034\n'
} > "$BASELINE_ROOT/fast/diagnosis.tsv"
printf 'aggregate\n' > "$BASELINE_ROOT/no-manifest/latest.tsv"
{
  printf 'key\tvalue\n'
  printf 'model\tmissing-latest-model\n'
} > "$BASELINE_ROOT/no-latest/latest-manifest.tsv"

bash "$BASELINES_SCRIPT" list "$BASELINE_ROOT" >"${TMP_DIR}/list.tsv"

if ! awk -F '\t' -v root="$BASELINE_ROOT" '
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      column[$i] = i
    }
    next
  }
  $column["name"] == "webworld" {
    found = 1
    if ($column["status"] != "ready") exit 2
    if ($column["latest"] != root "/webworld/latest.tsv") exit 3
    if ($column["manifest"] != root "/webworld/latest-manifest.tsv") exit 4
    if ($column["model"] != "onnx-community/WebWorld-8B-Onnx") exit 5
    if ($column["onnxBackend"] != "CoreML") exit 6
    if ($column["expectBackend"] != "CoreML") exit 7
    if ($column["maxTokens"] != "8") exit 8
    if ($column["runs"] != "5") exit 9
    if ($column["warmupRuns"] != "1") exit 10
    if ($column["prompt"] != "where is jakarta") exit 11
    if ($column["aggregateLabel"] != "measured") exit 12
    if ($column["durationMs"] != "1100.000") exit 13
    if ($column["generationTps"] != "9.000") exit 14
    if ($column["decodeTps"] != "7.500") exit 15
    if ($column["ttftMs"] != "220.000") exit 16
    if ($column["tokenLatencyMs"] != "55.000") exit 17
    if ($column["onnxOrtRunMs"] != "70.000") exit 18
    if ($column["onnxDecodeRunMs"] != "37.500") exit 19
    if ($column["primaryStage"] != "decodeRun") exit 20
    if ($column["primaryMetric"] != "onnxDecodeRunMs") exit 21
    if ($column["primaryValueMs"] != "37.500") exit 22
    if ($column["primaryShareOfOrtPercent"] != "53.571") exit 23
    if ($column["updatedUtc"] == "") exit 24
  }
  END { exit found ? 0 : 1 }
' "${TMP_DIR}/list.tsv"; then
  echo "Expected ready webworld baseline row" >&2
  cat "${TMP_DIR}/list.tsv" >&2
  exit 1
fi

if ! awk -F '\t' '
  NR == 1 {
    for (i = 1; i <= NF; i++) {
      column[$i] = i
    }
    next
  }
  $column["name"] == "no-manifest" {
    found = 1
    if ($column["status"] != "missing-manifest") exit 2
    if ($column["model"] != "") exit 3
    if ($column["primaryStage"] != "") exit 9
    if ($column["aggregateLabel"] != "measured") exit 6
    if ($column["durationMs"] != "") exit 7
  }
  $column["name"] == "no-latest" {
    foundMissingLatest = 1
    if ($column["status"] != "missing-latest") exit 4
    if ($column["model"] != "missing-latest-model") exit 5
    if ($column["primaryStage"] != "") exit 10
    if ($column["durationMs"] != "") exit 8
  }
  END { exit found && foundMissingLatest ? 0 : 1 }
' "${TMP_DIR}/list.tsv"; then
  echo "Expected incomplete baseline rows" >&2
  cat "${TMP_DIR}/list.tsv" >&2
  exit 1
fi

bash "$BASELINES_SCRIPT" list "$BASELINE_ROOT" webworld >"${TMP_DIR}/filtered.tsv"
if [[ "$(wc -l < "${TMP_DIR}/filtered.tsv" | tr -d ' ')" != "2" ]] \
    || ! grep -q '^webworld	ready	' "${TMP_DIR}/filtered.tsv"; then
  echo "Expected filtered baseline list" >&2
  cat "${TMP_DIR}/filtered.tsv" >&2
  exit 1
fi

bash "$BASELINES_SCRIPT" list --root "$BASELINE_ROOT" --sort durationMs >"${TMP_DIR}/sort-duration.tsv"
if ! awk -F '\t' 'NR == 1 { next } NR == 2 { exit $1 == "fast" ? 0 : 1 }' "${TMP_DIR}/sort-duration.tsv"; then
  echo "Expected duration sort to put fastest measured baseline first" >&2
  cat "${TMP_DIR}/sort-duration.tsv" >&2
  exit 1
fi

bash "$BASELINES_SCRIPT" list --root "$BASELINE_ROOT" --sort generationTps >"${TMP_DIR}/sort-generation.tsv"
if ! awk -F '\t' 'NR == 1 { next } NR == 2 { exit $1 == "fast" ? 0 : 1 }' "${TMP_DIR}/sort-generation.tsv"; then
  echo "Expected generationTps sort to put highest throughput baseline first" >&2
  cat "${TMP_DIR}/sort-generation.tsv" >&2
  exit 1
fi

bash "$BASELINES_SCRIPT" list --root "$BASELINE_ROOT" --sort=-durationMs >"${TMP_DIR}/sort-duration-desc.tsv"
if ! awk -F '\t' 'NR == 1 { next } NR == 2 { exit $1 == "webworld" ? 0 : 1 }' "${TMP_DIR}/sort-duration-desc.tsv"; then
  echo "Expected descending duration sort to put slowest measured baseline first" >&2
  cat "${TMP_DIR}/sort-duration-desc.tsv" >&2
  exit 1
fi

bash "$BASELINES_SCRIPT" list --root "$BASELINE_ROOT" --sort primaryValueMs >"${TMP_DIR}/sort-primary-value.tsv"
if ! awk -F '\t' 'NR == 1 { next } NR == 2 { exit $1 == "fast" ? 0 : 1 }' "${TMP_DIR}/sort-primary-value.tsv"; then
  echo "Expected primaryValueMs sort to put smallest diagnosed stage first" >&2
  cat "${TMP_DIR}/sort-primary-value.tsv" >&2
  exit 1
fi

bash "$BASELINES_SCRIPT" list --root "$BASELINE_ROOT" --sort=-primaryShareOfOrtPercent >"${TMP_DIR}/sort-primary-share.tsv"
if ! awk -F '\t' 'NR == 1 { next } NR == 2 { exit $1 == "webworld" ? 0 : 1 }' "${TMP_DIR}/sort-primary-share.tsv"; then
  echo "Expected primaryShareOfOrtPercent sort to put largest diagnosed share first" >&2
  cat "${TMP_DIR}/sort-primary-share.tsv" >&2
  exit 1
fi

bash "$BASELINES_SCRIPT" list --root "$BASELINE_ROOT" --format table --sort durationMs >"${TMP_DIR}/table.txt"
if ! grep -q '^name' "${TMP_DIR}/table.txt" \
    || ! grep -q '^fast[[:space:]][[:space:]]*ready[[:space:]][[:space:]]*CPU[[:space:]][[:space:]]*prefillRun[[:space:]][[:space:]]*900.000[[:space:]][[:space:]]*11.000[[:space:]][[:space:]]*58.000' "${TMP_DIR}/table.txt"; then
  echo "Expected human-readable table output" >&2
  cat "${TMP_DIR}/table.txt" >&2
  exit 1
fi

if bash "$BASELINES_SCRIPT" list --root "$BASELINE_ROOT" --format xml >"${TMP_DIR}/bad-format.out" 2>"${TMP_DIR}/bad-format.err"; then
  echo "Expected bad baseline list format failure" >&2
  exit 1
fi
if ! grep -qx 'Unknown baseline list format: xml' "${TMP_DIR}/bad-format.err"; then
  echo "Expected bad baseline list format error" >&2
  cat "${TMP_DIR}/bad-format.err" >&2
  exit 1
fi

if bash "$BASELINES_SCRIPT" list --root "$BASELINE_ROOT" --sort unknown >"${TMP_DIR}/bad-sort.out" 2>"${TMP_DIR}/bad-sort.err"; then
  echo "Expected bad baseline sort failure" >&2
  exit 1
fi
if ! grep -qx 'Unknown baseline sort field: unknown' "${TMP_DIR}/bad-sort.err"; then
  echo "Expected bad baseline sort error" >&2
  cat "${TMP_DIR}/bad-sort.err" >&2
  exit 1
fi

printf 'ONNX performance baselines test passed\n'
