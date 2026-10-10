#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/aqli-test-verify-onnx-performance-bundle.XXXXXX")"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

PASS_DIR="${TMP_DIR}/pass"
mkdir -p "$PASS_DIR"
printf 'present\n' > "$PASS_DIR/config.tsv"
{
  printf 'kind\tpath\trequired\tstatus\tdescription\n'
  printf 'config\t%s/config.tsv\trequired\tpresent\tConfig artifact\n' "$PASS_DIR"
  printf 'optionalLog\t%s/optional.log\toptional\tmissing\tOptional log\n' "$PASS_DIR"
} > "$PASS_DIR/bundle.tsv"

bash "$ROOT_DIR/scripts/verify-onnx-performance-bundle.sh" \
  --bundle "$PASS_DIR/bundle.tsv" \
  --summary-out "$PASS_DIR/verification.tsv" >"$PASS_DIR/stdout.tsv"

if ! cmp -s "$PASS_DIR/verification.tsv" "$PASS_DIR/stdout.tsv" \
    || ! grep -qx $'status\tpass' "$PASS_DIR/verification.tsv" \
    || ! grep -qx $'artifacts\t2' "$PASS_DIR/verification.tsv" \
    || ! grep -qx $'present\t1' "$PASS_DIR/verification.tsv" \
    || ! grep -qx $'missing\t1' "$PASS_DIR/verification.tsv" \
    || ! grep -qx $'requiredMissing\t0' "$PASS_DIR/verification.tsv" \
    || ! grep -qx $'optionalMissing\t1' "$PASS_DIR/verification.tsv"; then
  echo "Expected passing bundle verification summary" >&2
  cat "$PASS_DIR/verification.tsv" >&2
  cat "$PASS_DIR/stdout.tsv" >&2
  exit 1
fi

FAIL_DIR="${TMP_DIR}/fail"
mkdir -p "$FAIL_DIR"
{
  printf 'kind\tpath\trequired\tstatus\tdescription\n'
  printf 'decision\t%s/decision.tsv\trequired\tmissing\tDecision artifact\n' "$FAIL_DIR"
  printf 'optionalLog\t\toptional\tblank\tOptional log\n'
} > "$FAIL_DIR/bundle.tsv"

if bash "$ROOT_DIR/scripts/verify-onnx-performance-bundle.sh" \
    --bundle "$FAIL_DIR/bundle.tsv" \
    --summary-out "$FAIL_DIR/verification.tsv" >"$FAIL_DIR/stdout.tsv"; then
  echo "Expected required-missing bundle verification failure" >&2
  cat "$FAIL_DIR/stdout.tsv" >&2
  exit 1
fi
if ! grep -qx $'status\tfail' "$FAIL_DIR/verification.tsv" \
    || ! grep -qx $'requiredMissing\t1' "$FAIL_DIR/verification.tsv" \
    || ! grep -qx $'optionalMissing\t1' "$FAIL_DIR/verification.tsv" \
    || ! grep -qx $'firstRequiredMissingKind\tdecision' "$FAIL_DIR/verification.tsv" \
    || ! grep -qx $'firstRequiredMissingPath\t'"$FAIL_DIR"'/decision.tsv' "$FAIL_DIR/verification.tsv"; then
  echo "Expected failing bundle verification summary" >&2
  cat "$FAIL_DIR/verification.tsv" >&2
  exit 1
fi

MISSING_SUMMARY="${TMP_DIR}/missing.tsv"
if bash "$ROOT_DIR/scripts/verify-onnx-performance-bundle.sh" \
    --bundle "$TMP_DIR/missing-bundle.tsv" \
    --summary-out "$MISSING_SUMMARY" >"$TMP_DIR/missing.out"; then
  echo "Expected missing bundle verification failure" >&2
  cat "$TMP_DIR/missing.out" >&2
  exit 1
fi
if ! grep -qx $'status\tfail' "$MISSING_SUMMARY" \
    || ! grep -qx $'firstRequiredMissingKind\tbundle' "$MISSING_SUMMARY" \
    || ! grep -qx $'firstRequiredMissingPath\t'"$TMP_DIR"'/missing-bundle.tsv' "$MISSING_SUMMARY"; then
  echo "Expected missing bundle summary" >&2
  cat "$MISSING_SUMMARY" >&2
  exit 1
fi

BAD_BUNDLE="${TMP_DIR}/bad.tsv"
printf 'bad\theader\n' > "$BAD_BUNDLE"
if bash "$ROOT_DIR/scripts/verify-onnx-performance-bundle.sh" \
    --bundle "$BAD_BUNDLE" >"$TMP_DIR/bad.out" 2>"$TMP_DIR/bad.err"; then
  echo "Expected invalid bundle header failure" >&2
  cat "$TMP_DIR/bad.out" >&2
  cat "$TMP_DIR/bad.err" >&2
  exit 1
fi
if ! grep -qx "Invalid bundle header: $BAD_BUNDLE" "$TMP_DIR/bad.err"; then
  echo "Expected invalid bundle header diagnostic" >&2
  cat "$TMP_DIR/bad.err" >&2
  exit 1
fi

printf 'ONNX performance bundle verifier test passed\n'
