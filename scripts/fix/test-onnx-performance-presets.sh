#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-onnx-performance-presets.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

PRESETS_SCRIPT="$ROOT_DIR/scripts/onnx-performance-presets.sh"

bash "$PRESETS_SCRIPT" list >"${TMP_DIR}/list.tsv"
if ! grep -qx $'name\tdescription' "${TMP_DIR}/list.tsv" \
    || ! grep -qx $'quick\tLight smoke run: 1 token, 1 run, no warmup' "${TMP_DIR}/list.tsv" \
    || ! grep -qx $'stable\tStable run: 5 runs, 1 warmup, noise gate for regression/capture' "${TMP_DIR}/list.tsv" \
    || ! grep -qx $'coreml-stable\tStable run with CoreML backend expectation' "${TMP_DIR}/list.tsv"; then
  echo "Expected preset list TSV" >&2
  cat "${TMP_DIR}/list.tsv" >&2
  exit 1
fi

bash "$PRESETS_SCRIPT" args stable gate >"${TMP_DIR}/stable-gate.args"
if ! grep -qx -- '--runs' "${TMP_DIR}/stable-gate.args" \
    || ! grep -qx '5' "${TMP_DIR}/stable-gate.args" \
    || ! grep -qx -- '--warmup-runs' "${TMP_DIR}/stable-gate.args" \
    || ! grep -qx '1' "${TMP_DIR}/stable-gate.args" \
    || grep -q -- '--max-noise-percent' "${TMP_DIR}/stable-gate.args" \
    || grep -q -- '--noise-metrics' "${TMP_DIR}/stable-gate.args"; then
  echo "Expected stable gate args without noise gate" >&2
  cat "${TMP_DIR}/stable-gate.args" >&2
  exit 1
fi

bash "$PRESETS_SCRIPT" args coreml-stable regression >"${TMP_DIR}/coreml-regression.args"
if ! grep -qx -- '--expect-backend' "${TMP_DIR}/coreml-regression.args" \
    || ! grep -qx 'CoreML' "${TMP_DIR}/coreml-regression.args" \
    || ! grep -qx -- '--max-noise-percent' "${TMP_DIR}/coreml-regression.args" \
    || ! grep -qx '10' "${TMP_DIR}/coreml-regression.args" \
    || ! grep -qx -- '--noise-metrics' "${TMP_DIR}/coreml-regression.args" \
    || ! grep -qx 'durationMs,generationTps,onnxOrtRunMs' "${TMP_DIR}/coreml-regression.args"; then
  echo "Expected coreml-stable regression args" >&2
  cat "${TMP_DIR}/coreml-regression.args" >&2
  exit 1
fi

if bash "$PRESETS_SCRIPT" validate unknown; then
  echo "Expected invalid preset validation failure" >&2
  exit 1
fi
if bash "$PRESETS_SCRIPT" args unknown gate >"${TMP_DIR}/unknown.out" 2>"${TMP_DIR}/unknown.err"; then
  echo "Expected invalid preset args failure" >&2
  cat "${TMP_DIR}/unknown.out" >&2
  cat "${TMP_DIR}/unknown.err" >&2
  exit 1
fi
if ! grep -qx 'Unknown preset: unknown' "${TMP_DIR}/unknown.err"; then
  echo "Expected invalid preset args error" >&2
  cat "${TMP_DIR}/unknown.err" >&2
  exit 1
fi

printf 'ONNX performance presets test passed\n'
