#!/usr/bin/env bash

aqli_onnx_performance_bundle_safe_tsv_field() {
  local value="${1:-}"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

aqli_onnx_performance_bundle_init() {
  local out="$1"
  printf 'kind\tpath\trequired\tstatus\tdescription\n' > "$out"
}

aqli_onnx_performance_bundle_json_path() {
  local out="$1"
  if [[ "$out" == *.tsv ]]; then
    printf '%s' "${out%.tsv}.json"
  else
    printf '%s' "${out}.json"
  fi
}

aqli_onnx_performance_bundle_add() {
  local out="$1"
  local kind="$2"
  local path="${3:-}"
  local required="${4:-required}"
  local description="${5:-}"
  local status="missing"

  if [[ -z "$path" ]]; then
    status="blank"
  elif [[ -e "$path" ]]; then
    status="present"
  fi

  printf '%s\t%s\t%s\t%s\t%s\n' \
    "$(aqli_onnx_performance_bundle_safe_tsv_field "$kind")" \
    "$(aqli_onnx_performance_bundle_safe_tsv_field "$path")" \
    "$(aqli_onnx_performance_bundle_safe_tsv_field "$required")" \
    "$(aqli_onnx_performance_bundle_safe_tsv_field "$status")" \
    "$(aqli_onnx_performance_bundle_safe_tsv_field "$description")" >> "$out"
}

aqli_onnx_performance_bundle_write_json() {
  local bundle="$1"
  local out="${2:-}"

  if [[ -z "$out" ]]; then
    out="$(aqli_onnx_performance_bundle_json_path "$bundle")"
  fi
  local parent="${out%/*}"
  if [[ "$parent" != "$out" ]]; then
    mkdir -p "$parent"
  fi

  awk '
    BEGIN {
      FS = "\t"
      print "{"
      print "  \"artifacts\": ["
    }
    function json_escape(value) {
      gsub(/\\/, "\\\\", value)
      gsub(/"/, "\\\"", value)
      gsub(/\r/, "\\r", value)
      gsub(/\n/, "\\n", value)
      gsub(/\t/, "\\t", value)
      return value
    }
    NR == 1 {
      next
    }
    NF > 0 {
      if (count > 0) {
        print ","
      }
      printf "    {\"kind\": \"%s\", \"path\": \"%s\", \"required\": \"%s\", \"status\": \"%s\", \"description\": \"%s\"}", \
        json_escape($1), json_escape($2), json_escape($3), json_escape($4), json_escape($5)
      count++
      status = $4
      totals[status]++
      if ($3 == "required" && status != "present") {
        requiredMissing++
      }
    }
    END {
      print ""
      print "  ],"
      printf "  \"counts\": {\"total\": \"%d\", \"present\": \"%d\", \"missing\": \"%d\", \"blank\": \"%d\", \"requiredMissing\": \"%d\"}\n", \
        count + 0, totals["present"] + 0, totals["missing"] + 0, totals["blank"] + 0, requiredMissing + 0
      print "}"
    }
  ' "$bundle" > "$out"
}
