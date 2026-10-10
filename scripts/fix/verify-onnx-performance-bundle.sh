#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: verify-onnx-performance-bundle.sh --bundle BUNDLE [options]

Validate an ONNX performance artifact bundle and fail when any required
artifact is missing or blank. The verifier emits a compact key/value summary
for CI logs and optional follow-up automation.

Options:
  --bundle PATH       bundle.tsv to validate (required)
  --summary-out PATH  Write key/value summary TSV to this path
  --help              Show this help
USAGE
}

BUNDLE=""
SUMMARY_OUT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bundle) BUNDLE="$2"; shift 2 ;;
    --bundle=*) BUNDLE="${1#*=}"; shift ;;
    --summary-out) SUMMARY_OUT="$2"; shift 2 ;;
    --summary-out=*) SUMMARY_OUT="${1#*=}"; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; usage; exit 2 ;;
  esac
done

if [[ -z "$BUNDLE" ]]; then
  echo "--bundle is required" >&2
  usage
  exit 2
fi
for cmd in awk mkdir; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 127
  fi
done

if [[ -z "$SUMMARY_OUT" ]]; then
  SUMMARY_OUT="${BUNDLE%/*}/bundle-verification.tsv"
  if [[ "$SUMMARY_OUT" == "$BUNDLE/bundle-verification.tsv" ]]; then
    SUMMARY_OUT="bundle-verification.tsv"
  fi
fi
summary_parent="${SUMMARY_OUT%/*}"
if [[ "$summary_parent" != "$SUMMARY_OUT" ]]; then
  mkdir -p "$summary_parent"
fi

write_missing_bundle_summary() {
  {
    printf 'key\tvalue\n'
    printf 'status\tfail\n'
    printf 'bundle\t%s\n' "$BUNDLE"
    printf 'artifacts\t0\n'
    printf 'present\t0\n'
    printf 'missing\t1\n'
    printf 'blank\t0\n'
    printf 'requiredMissing\t1\n'
    printf 'optionalMissing\t0\n'
    printf 'firstRequiredMissingKind\tbundle\n'
    printf 'firstRequiredMissingPath\t%s\n' "$BUNDLE"
  } > "$SUMMARY_OUT"
}

if [[ ! -f "$BUNDLE" ]]; then
  write_missing_bundle_summary
  cat "$SUMMARY_OUT"
  exit 42
fi

IFS= read -r header < "$BUNDLE" || header=""
if [[ "$header" != $'kind\tpath\trequired\tstatus\tdescription' ]]; then
  echo "Invalid bundle header: $BUNDLE" >&2
  exit 2
fi

awk -v bundle="$BUNDLE" '
  BEGIN {
    FS = "\t"
  }
  NR == 1 {
    next
  }
  NF > 0 {
    artifacts++
    status = $4
    if (status == "present") {
      present++
    } else if (status == "missing") {
      missing++
    } else if (status == "blank") {
      blank++
    }
    if ($3 == "required" && status != "present") {
      requiredMissing++
      if (firstRequiredMissingKind == "") {
        firstRequiredMissingKind = $1
        firstRequiredMissingPath = $2
      }
    } else if ($3 != "required" && status != "present") {
      optionalMissing++
    }
  }
  END {
    print "key\tvalue"
    print "status\t" (requiredMissing > 0 ? "fail" : "pass")
    print "bundle\t" bundle
    print "artifacts\t" artifacts + 0
    print "present\t" present + 0
    print "missing\t" missing + 0
    print "blank\t" blank + 0
    print "requiredMissing\t" requiredMissing + 0
    print "optionalMissing\t" optionalMissing + 0
    print "firstRequiredMissingKind\t" firstRequiredMissingKind
    print "firstRequiredMissingPath\t" firstRequiredMissingPath
  }
' "$BUNDLE" > "$SUMMARY_OUT"

cat "$SUMMARY_OUT"

if awk 'BEGIN { FS = "\t" } $1 == "requiredMissing" && $2 + 0 > 0 { found = 1 } END { exit found ? 0 : 1 }' "$SUMMARY_OUT"; then
  exit 42
fi
