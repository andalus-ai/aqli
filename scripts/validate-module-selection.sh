#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SELECTION_FILE="${AQLI_MODULE_SELECTION_FILE:-$ROOT_DIR/scripts/module-selection-current.env}"

if [[ ! -f "$SELECTION_FILE" ]]; then
  echo "Module selection file not found: $SELECTION_FILE" >&2
  exit 2
fi

# shellcheck disable=SC1090
source "$SELECTION_FILE"
read -r -a SELECTED_MODULES <<< "${MODULES:-}"
if [[ ${#SELECTED_MODULES[@]} -eq 0 ]]; then
  echo "No Gradle modules are selected in $SELECTION_FILE" >&2
  exit 2
fi

GRADLE_ARGS=(--console=plain)
if [[ -n "${JAVA_HOME:-}" ]]; then
  GRADLE_ARGS+=("-Dorg.gradle.java.home=${JAVA_HOME}")
fi
if [[ -n "${AQLI_GRADLE_MAX_WORKERS:-1}" ]]; then
  GRADLE_ARGS+=("--max-workers=${AQLI_GRADLE_MAX_WORKERS:-1}")
fi

PROJECTS_OUTPUT="$(mktemp "${TMPDIR:-/tmp}/aqli-gradle-projects.XXXXXX")"
trap 'rm -f "$PROJECTS_OUTPUT"' EXIT

echo "Resolving Gradle projects before validating $SELECTION_FILE"
if ! "$ROOT_DIR/gradlew" "${GRADLE_ARGS[@]}" projects >"$PROJECTS_OUTPUT"; then
  echo "Unable to enumerate Gradle projects; module selection was not validated." >&2
  exit 2
fi

AVAILABLE_PROJECTS="$(sed -n "s/.*Project '\(:[^']*\)'.*/\1/p" "$PROJECTS_OUTPUT")"
if [[ -z "$AVAILABLE_PROJECTS" ]]; then
  echo "Gradle project listing contained no project paths; refusing to validate an empty result." >&2
  exit 2
fi

MISSING_MODULES=()
DUPLICATE_MODULES=()
SEEN_MODULES=()
for module in "${SELECTED_MODULES[@]}"; do
  if [[ ! "$module" =~ ^(:[A-Za-z0-9_-]+)+$ ]]; then
    MISSING_MODULES+=("$module (invalid Gradle project path)")
    continue
  fi

  duplicate=false
  for seen in "${SEEN_MODULES[@]}"; do
    if [[ "$seen" == "$module" ]]; then
      duplicate=true
      break
    fi
  done
  if [[ "$duplicate" == true ]]; then
    DUPLICATE_MODULES+=("$module")
  else
    SEEN_MODULES+=("$module")
  fi

  if ! grep -Fqx -- "$module" <<< "$AVAILABLE_PROJECTS"; then
    MISSING_MODULES+=("$module")
  fi
done

if [[ ${#MISSING_MODULES[@]} -gt 0 || ${#DUPLICATE_MODULES[@]} -gt 0 ]]; then
  echo "Module selection is inconsistent with the Gradle project model." >&2
  if [[ ${#MISSING_MODULES[@]} -gt 0 ]]; then
    echo "Missing or invalid project paths:" >&2
    printf '  - %s\n' "${MISSING_MODULES[@]}" >&2
  fi
  if [[ ${#DUPLICATE_MODULES[@]} -gt 0 ]]; then
    echo "Duplicate project paths:" >&2
    printf '  - %s\n' "${DUPLICATE_MODULES[@]}" >&2
  fi
  echo "Update MODULES in $SELECTION_FILE before publishing selected modules." >&2
  exit 1
fi

echo "Validated ${#SELECTED_MODULES[@]} selected Gradle project paths."
