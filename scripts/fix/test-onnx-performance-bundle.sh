#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-onnx-performance-bundle.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

# shellcheck source=/dev/null
source "$ROOT_DIR/scripts/onnx-performance-bundle.sh"

BUNDLE="${TMP_DIR}/bundle.tsv"
BUNDLE_JSON="${TMP_DIR}/bundle.json"
PRESENT="${TMP_DIR}/present.tsv"
printf 'ok\n' > "$PRESENT"

aqli_onnx_performance_bundle_init "$BUNDLE"
aqli_onnx_performance_bundle_add "$BUNDLE" config "$PRESENT" required 'Config "quoted" artifact'
aqli_onnx_performance_bundle_add "$BUNDLE" optionalMissing "$TMP_DIR/missing-optional.tsv" optional "Optional missing artifact"
aqli_onnx_performance_bundle_add "$BUNDLE" requiredMissing "$TMP_DIR/missing-required.tsv" required "Required missing artifact"
aqli_onnx_performance_bundle_add "$BUNDLE" blank "" optional "Blank path artifact"
aqli_onnx_performance_bundle_write_json "$BUNDLE" "$BUNDLE_JSON"

if ! grep -qx $'kind\tpath\trequired\tstatus\tdescription' "$BUNDLE" \
    || ! grep -qx $'config\t'"$PRESENT"$'\trequired\tpresent\tConfig "quoted" artifact' "$BUNDLE" \
    || ! grep -qx $'optionalMissing\t'"$TMP_DIR"$'/missing-optional.tsv\toptional\tmissing\tOptional missing artifact' "$BUNDLE" \
    || ! grep -qx $'requiredMissing\t'"$TMP_DIR"$'/missing-required.tsv\trequired\tmissing\tRequired missing artifact' "$BUNDLE" \
    || ! grep -qx $'blank\t\toptional\tblank\tBlank path artifact' "$BUNDLE"; then
  echo "Expected bundle TSV rows" >&2
  cat "$BUNDLE" >&2
  exit 1
fi

if command -v jq >/dev/null 2>&1; then
  if ! jq -e '
      (.artifacts | length) == 4
      and .counts.total == "4"
      and .counts.present == "1"
      and .counts.missing == "2"
      and .counts.blank == "1"
      and .counts.requiredMissing == "1"
      and (.artifacts[] | select(.kind == "config").description) == "Config \"quoted\" artifact"
      and (.artifacts[] | select(.kind == "requiredMissing").status) == "missing"
    ' "$BUNDLE_JSON" >/dev/null; then
    echo "Expected bundle JSON rows and counts" >&2
    jq . "$BUNDLE_JSON" >&2 || cat "$BUNDLE_JSON" >&2
    exit 1
  fi
else
  if ! grep -Fq '"kind": "config"' "$BUNDLE_JSON" \
      || ! grep -Fq 'Config \"quoted\" artifact' "$BUNDLE_JSON" \
      || ! grep -Fq '"requiredMissing": "1"' "$BUNDLE_JSON"; then
    echo "Expected bundle JSON rows and counts" >&2
    cat "$BUNDLE_JSON" >&2
    exit 1
  fi
fi

printf 'ONNX performance bundle test passed\n'
