#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-onnx-profile-summary.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

SUMMARY="${TMP_DIR}/summary.tsv"
write_header() {
  printf 'case\tstatus\texitCode\tdurationMs\tpromptEvalTps\tgenerationTps\tdecodeTps\tttftMs\ttokenLatencyMs\tonnxBackend\tonnxTokenizeMs\tonnxInputPrepMs\tonnxPrefillRunMs\tonnxDecodeRunMs\tonnxOrtRunMs\tonnxLogitsSelectMs\tonnxSamplingMs\tonnxSteps\tcombinedLog\tstderrLog\n'
}

{
  write_header
  printf 'warmup-1\tpass\t0\t2000\t10\t5\t4\t400\t120\tCPU\t5\t5\t50\t90\t120\t8\t9\t1\t/tmp/warmup.log\t/tmp/warmup.err\n'
  printf 'run-1\tpass\t0\t1000\t20\t10\t8\t200\t50\tCoreML\t2\t3\t20\t30\t60\t4\t5\t2\t/tmp/run-1.log\t/tmp/run-1.err\n'
  printf 'run-2\tpass\t0\t1200\t18\t8\t7\t240\t60\tCoreML\t3\t5\t25\t45\t80\t6\t7\t3\t/tmp/run-2.log\t/tmp/run-2.err\n'
} > "$SUMMARY"

DEFAULT_DIR="${TMP_DIR}/default"
bash "$ROOT_DIR/scripts/summarize-onnx-profile-summary.sh" \
  --summary "$SUMMARY" \
  --summary-dir "$DEFAULT_DIR" >/dev/null

extract() {
  local file="$1"
  local case_name="$2"
  local column_name="$3"
  awk -v caseName="$case_name" -v columnName="$column_name" '
    BEGIN { FS = "\t" }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        column[$i] = i
      }
      next
    }
    $1 == caseName {
      print $column[columnName]
      exit
    }
  ' "$file"
}

DEFAULT_AGG="$DEFAULT_DIR/aggregate.tsv"
if [[ "$(extract "$DEFAULT_AGG" measured-mean durationMs)" != "1100.000" \
    || "$(extract "$DEFAULT_AGG" measured-mean generationTps)" != "9.000" \
    || "$(extract "$DEFAULT_AGG" measured-mean onnxDecodeRunMs)" != "37.500" \
    || "$(extract "$DEFAULT_AGG" measured-best durationMs)" != "1000.000" \
    || "$(extract "$DEFAULT_AGG" measured-best generationTps)" != "10.000" \
    || "$(extract "$DEFAULT_AGG" measured-best onnxDecodeRunMs)" != "30.000" \
    || "$(extract "$DEFAULT_AGG" measured-worst durationMs)" != "1200.000" \
    || "$(extract "$DEFAULT_AGG" measured-worst generationTps)" != "8.000" \
    || "$(extract "$DEFAULT_AGG" measured-worst onnxDecodeRunMs)" != "45.000" \
    || "$(extract "$DEFAULT_AGG" measured-mean onnxBackend)" != "CoreML" ]]; then
  echo "Expected measured-only aggregate values" >&2
  cat "$DEFAULT_AGG" >&2
  exit 1
fi

INCLUDE_DIR="${TMP_DIR}/include-warmup"
bash "$ROOT_DIR/scripts/summarize-onnx-profile-summary.sh" \
  --summary "$SUMMARY" \
  --summary-dir "$INCLUDE_DIR" \
  --include-warmup >/dev/null

INCLUDE_AGG="$INCLUDE_DIR/aggregate.tsv"
if [[ "$(extract "$INCLUDE_AGG" measured-mean durationMs)" != "1400.000" \
    || "$(extract "$INCLUDE_AGG" measured-best durationMs)" != "1000.000" \
    || "$(extract "$INCLUDE_AGG" measured-worst durationMs)" != "2000.000" \
    || "$(extract "$INCLUDE_AGG" measured-mean onnxBackend)" != "mixed" ]]; then
  echo "Expected warmup-inclusive aggregate values" >&2
  cat "$INCLUDE_AGG" >&2
  exit 1
fi

LABEL_DIR="${TMP_DIR}/label"
bash "$ROOT_DIR/scripts/summarize-onnx-profile-summary.sh" \
  --summary "$SUMMARY" \
  --summary-dir "$LABEL_DIR" \
  --label current >/dev/null

if ! grep -q '^current-mean	' "$LABEL_DIR/aggregate.tsv" \
    || ! grep -q '^current-best	' "$LABEL_DIR/aggregate.tsv" \
    || ! grep -q '^current-worst	' "$LABEL_DIR/aggregate.tsv"; then
  echo "Expected custom aggregate label" >&2
  cat "$LABEL_DIR/aggregate.tsv" >&2
  exit 1
fi

printf 'ONNX profile summary aggregation test passed\n'
