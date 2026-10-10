#!/usr/bin/env bash

aqli_onnx_performance_baseline_safe_tsv() {
  local value="${1:-}"
  value="${value//$'\t'/ }"
  value="${value//$'\n'/ }"
  printf '%s' "$value"
}

aqli_onnx_performance_baseline_manifest_value() {
  local manifest="$1"
  local key="$2"
  if [[ -z "$manifest" || ! -f "$manifest" ]]; then
    return 1
  fi
  awk -v key="$key" '
    BEGIN { FS = "\t" }
    NR > 1 && $1 == key {
      value = $0
      sub(/^[^\t]*\t/, "", value)
      print value
      found = 1
      exit
    }
    END { exit found ? 0 : 1 }
  ' "$manifest"
}

aqli_onnx_performance_baseline_key_value() {
  local file="$1"
  local key="$2"
  if [[ -z "$file" || ! -f "$file" ]]; then
    return 1
  fi
  awk -v key="$key" '
    BEGIN { FS = "\t" }
    NR > 1 && $1 == key {
      value = $0
      sub(/^[^\t]*\t/, "", value)
      print value
      found = 1
      exit
    }
    END { exit found ? 0 : 1 }
  ' "$file"
}

aqli_onnx_performance_baseline_resolve_manifest_path() {
  local manifest="$1"
  local candidate="$2"
  if [[ -z "$candidate" ]]; then
    printf ''
    return 0
  fi
  if [[ -f "$candidate" ]]; then
    printf '%s' "$candidate"
    return 0
  fi
  local manifest_dir
  manifest_dir="${manifest%/*}"
  if [[ "$manifest_dir" == "$manifest" ]]; then
    manifest_dir="."
  fi
  if [[ -f "${manifest_dir}/${candidate}" ]]; then
    printf '%s' "${manifest_dir}/${candidate}"
    return 0
  fi
  printf '%s' "$candidate"
}

aqli_onnx_performance_baseline_mtime_utc() {
  local file="$1"
  if [[ -z "$file" || ! -f "$file" ]]; then
    printf ''
    return 0
  fi
  date -u -r "$file" +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || printf ''
}

aqli_onnx_performance_baseline_status() {
  local latest="$1"
  local manifest="$2"
  if [[ ! -f "$latest" ]]; then
    printf 'missing-latest'
  elif [[ ! -f "$manifest" ]]; then
    printf 'missing-manifest'
  else
    printf 'ready'
  fi
}

aqli_onnx_performance_baseline_summary_value() {
  local summary="$1"
  local case_name="$2"
  local column_name="$3"
  if [[ -z "$summary" || ! -f "$summary" ]]; then
    return 1
  fi
  awk -v caseName="$case_name" -v columnName="$column_name" '
    BEGIN { FS = "\t" }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        column[$i] = i
      }
      next
    }
    $1 == caseName {
      if (column[columnName]) {
        print $column[columnName]
        found = 1
      }
      exit
    }
    END { exit found ? 0 : 1 }
  ' "$summary"
}

aqli_onnx_performance_baseline_header() {
  printf 'name\tstatus\tlatest\tmanifest\tmodel\tonnxBackend\texpectBackend\tmaxTokens\truns\twarmupRuns\tprompt\taggregateLabel\tdurationMs\tgenerationTps\tdecodeTps\tttftMs\ttokenLatencyMs\tonnxOrtRunMs\tonnxDecodeRunMs\tprimaryStage\tprimaryMetric\tprimaryValueMs\tprimaryShareOfOrtPercent\tupdatedUtc\n'
}

aqli_onnx_performance_baseline_rows() {
  local root="${1:-ops/benchmarks/onnx/baselines}"
  local name_filter="${2:-}"
  local dir name latest manifest status updated model onnx_backend expect_backend max_tokens runs warmup_runs prompt
  local aggregate_label case_name duration_ms generation_tps decode_tps ttft_ms token_latency_ms onnx_ort_run_ms onnx_decode_run_ms
  local decision diagnosis primary_stage primary_metric primary_value_ms primary_share_of_ort_percent

  if [[ ! -d "$root" ]]; then
    return 0
  fi

  shopt -s nullglob
  for dir in "$root"/*; do
    [[ -d "$dir" ]] || continue
    name="${dir##*/}"
    if [[ -n "$name_filter" && "$name" != "$name_filter" ]]; then
      continue
    fi
    latest="${dir}/latest.tsv"
    manifest="${dir}/latest-manifest.tsv"
    status="$(aqli_onnx_performance_baseline_status "$latest" "$manifest")"
    updated="$(aqli_onnx_performance_baseline_mtime_utc "$latest")"
    model="$(aqli_onnx_performance_baseline_manifest_value "$manifest" model 2>/dev/null || true)"
    onnx_backend="$(aqli_onnx_performance_baseline_manifest_value "$manifest" onnxBackend 2>/dev/null || true)"
    expect_backend="$(aqli_onnx_performance_baseline_manifest_value "$manifest" expectBackend 2>/dev/null || true)"
    max_tokens="$(aqli_onnx_performance_baseline_manifest_value "$manifest" maxTokens 2>/dev/null || true)"
    runs="$(aqli_onnx_performance_baseline_manifest_value "$manifest" runs 2>/dev/null || true)"
    warmup_runs="$(aqli_onnx_performance_baseline_manifest_value "$manifest" warmupRuns 2>/dev/null || true)"
    prompt="$(aqli_onnx_performance_baseline_manifest_value "$manifest" prompt 2>/dev/null || true)"
    aggregate_label="$(aqli_onnx_performance_baseline_manifest_value "$manifest" aggregateLabel 2>/dev/null || true)"
    if [[ -z "$aggregate_label" ]]; then
      aggregate_label="measured"
    fi
    case_name="${aggregate_label}-mean"
    duration_ms="$(aqli_onnx_performance_baseline_summary_value "$latest" "$case_name" durationMs 2>/dev/null || true)"
    generation_tps="$(aqli_onnx_performance_baseline_summary_value "$latest" "$case_name" generationTps 2>/dev/null || true)"
    decode_tps="$(aqli_onnx_performance_baseline_summary_value "$latest" "$case_name" decodeTps 2>/dev/null || true)"
    ttft_ms="$(aqli_onnx_performance_baseline_summary_value "$latest" "$case_name" ttftMs 2>/dev/null || true)"
    token_latency_ms="$(aqli_onnx_performance_baseline_summary_value "$latest" "$case_name" tokenLatencyMs 2>/dev/null || true)"
    onnx_ort_run_ms="$(aqli_onnx_performance_baseline_summary_value "$latest" "$case_name" onnxOrtRunMs 2>/dev/null || true)"
    onnx_decode_run_ms="$(aqli_onnx_performance_baseline_summary_value "$latest" "$case_name" onnxDecodeRunMs 2>/dev/null || true)"
    decision="$(aqli_onnx_performance_baseline_manifest_value "$manifest" decision 2>/dev/null || true)"
    diagnosis="$(aqli_onnx_performance_baseline_manifest_value "$manifest" diagnosis 2>/dev/null || true)"
    decision="$(aqli_onnx_performance_baseline_resolve_manifest_path "$manifest" "$decision")"
    diagnosis="$(aqli_onnx_performance_baseline_resolve_manifest_path "$manifest" "$diagnosis")"
    primary_stage="$(aqli_onnx_performance_baseline_key_value "$decision" primaryStage 2>/dev/null || aqli_onnx_performance_baseline_key_value "$diagnosis" primaryStage 2>/dev/null || true)"
    primary_metric="$(aqli_onnx_performance_baseline_key_value "$decision" primaryMetric 2>/dev/null || aqli_onnx_performance_baseline_key_value "$diagnosis" primaryMetric 2>/dev/null || true)"
    primary_value_ms="$(aqli_onnx_performance_baseline_key_value "$decision" primaryValueMs 2>/dev/null || aqli_onnx_performance_baseline_key_value "$diagnosis" primaryValueMs 2>/dev/null || true)"
    primary_share_of_ort_percent="$(aqli_onnx_performance_baseline_key_value "$decision" primaryShareOfOrtPercent 2>/dev/null || aqli_onnx_performance_baseline_key_value "$diagnosis" primaryShareOfOrtPercent 2>/dev/null || true)"
    printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
      "$(aqli_onnx_performance_baseline_safe_tsv "$name")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$status")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$latest")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$manifest")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$model")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$onnx_backend")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$expect_backend")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$max_tokens")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$runs")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$warmup_runs")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$prompt")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$aggregate_label")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$duration_ms")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$generation_tps")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$decode_tps")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$ttft_ms")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$token_latency_ms")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$onnx_ort_run_ms")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$onnx_decode_run_ms")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$primary_stage")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$primary_metric")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$primary_value_ms")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$primary_share_of_ort_percent")" \
      "$(aqli_onnx_performance_baseline_safe_tsv "$updated")"
  done
  shopt -u nullglob
}

aqli_onnx_performance_baseline_sort_key() {
  case "$1" in
    name) printf '1:text:asc' ;;
    status) printf '2:text:asc' ;;
    latest) printf '3:text:asc' ;;
    manifest) printf '4:text:asc' ;;
    model) printf '5:text:asc' ;;
    onnxBackend) printf '6:text:asc' ;;
    expectBackend) printf '7:text:asc' ;;
    maxTokens) printf '8:number:asc' ;;
    runs) printf '9:number:desc' ;;
    warmupRuns) printf '10:number:desc' ;;
    prompt) printf '11:text:asc' ;;
    aggregateLabel) printf '12:text:asc' ;;
    durationMs) printf '13:number:asc' ;;
    generationTps) printf '14:number:desc' ;;
    decodeTps) printf '15:number:desc' ;;
    ttftMs) printf '16:number:asc' ;;
    tokenLatencyMs) printf '17:number:asc' ;;
    onnxOrtRunMs) printf '18:number:asc' ;;
    onnxDecodeRunMs) printf '19:number:asc' ;;
    primaryStage) printf '20:text:asc' ;;
    primaryMetric) printf '21:text:asc' ;;
    primaryValueMs) printf '22:number:asc' ;;
    primaryShareOfOrtPercent) printf '23:number:desc' ;;
    updatedUtc) printf '24:text:desc' ;;
    *)
      return 1
      ;;
  esac
}

aqli_onnx_performance_baseline_sort_rows() {
  local rows="$1"
  local sort_spec="${2:-name}"
  local direction_override=""
  local key_info field index kind direction tab sort_field
  if [[ "$sort_spec" == -* ]]; then
    direction_override="desc"
    sort_spec="${sort_spec#-}"
  elif [[ "$sort_spec" == +* ]]; then
    direction_override="asc"
    sort_spec="${sort_spec#+}"
  fi
  field="$sort_spec"
  if ! key_info="$(aqli_onnx_performance_baseline_sort_key "$field")"; then
    echo "Unknown baseline sort field: $field" >&2
    echo "Available sort fields: name, status, model, onnxBackend, expectBackend, updatedUtc, durationMs, generationTps, decodeTps, ttftMs, tokenLatencyMs, onnxOrtRunMs, onnxDecodeRunMs, primaryStage, primaryMetric, primaryValueMs, primaryShareOfOrtPercent" >&2
    return 2
  fi
  IFS=':' read -r index kind direction <<< "$key_info"
  if [[ -n "$direction_override" ]]; then
    direction="$direction_override"
  fi
  tab="$(printf '\t')"
  if [[ "$kind" == "number" ]]; then
    awk -v index="$index" -v direction="$direction" '
      BEGIN { FS = OFS = "\t" }
      function numeric(value) {
        return value ~ /^-?[0-9]+([.][0-9]+)?$/
      }
      {
        missing = numeric($index) ? 0 : 1
        key = numeric($index) ? $index + 0 : 0
        if (direction == "desc") {
          key = -key
        }
        print missing, key, $0
      }
    ' "$rows" | LC_ALL=C sort -t "$tab" -k "1,1n" -k "2,2g" -k "3,3" | cut -f3-
  else
    sort_field=$((index + 1))
    if [[ "$direction" == "desc" ]]; then
      awk -v index="$index" 'BEGIN { FS = OFS = "\t" } { print ($index == "" ? 1 : 0), $0 }' "$rows" \
        | LC_ALL=C sort -t "$tab" -k "1,1n" -k "${sort_field},${sort_field}r" -k "2,2" \
        | cut -f2-
    else
      awk -v index="$index" 'BEGIN { FS = OFS = "\t" } { print ($index == "" ? 1 : 0), $0 }' "$rows" \
        | LC_ALL=C sort -t "$tab" -k "1,1n" -k "${sort_field},${sort_field}" -k "2,2" \
        | cut -f2-
    fi
  fi
}

aqli_onnx_performance_baseline_table() {
  awk '
    BEGIN {
      FS = "\t"
      selected[1] = "name"
      selected[2] = "status"
      selected[3] = "onnxBackend"
      selected[4] = "primaryStage"
      selected[5] = "durationMs"
      selected[6] = "generationTps"
      selected[7] = "onnxOrtRunMs"
      selected[8] = "updatedUtc"
      selected[9] = "model"
      selectedCount = 9
      for (i = 1; i <= selectedCount; i++) {
        width[i] = length(selected[i])
      }
    }
    NR == 1 {
      for (i = 1; i <= NF; i++) {
        column[$i] = i
      }
      next
    }
    {
      rowCount++
      for (i = 1; i <= selectedCount; i++) {
        value = column[selected[i]] ? $(column[selected[i]]) : ""
        cell[rowCount, i] = value
        if (length(value) > width[i]) {
          width[i] = length(value)
        }
      }
    }
    function emit_value(value, columnIndex) {
      printf "%-*s%s", width[columnIndex], value, (columnIndex == selectedCount ? "\n" : "  ")
    }
    function emit_separator(columnIndex, j) {
      for (j = 1; j <= width[columnIndex]; j++) {
        printf "-"
      }
      printf "%s", (columnIndex == selectedCount ? "\n" : "  ")
    }
    END {
      for (i = 1; i <= selectedCount; i++) {
        emit_value(selected[i], i)
      }
      for (i = 1; i <= selectedCount; i++) {
        emit_separator(i)
      }
      for (row = 1; row <= rowCount; row++) {
        for (i = 1; i <= selectedCount; i++) {
          emit_value(cell[row, i], i)
        }
      }
    }
  '
}

aqli_onnx_performance_baseline_list() {
  local root="${1:-ops/benchmarks/onnx/baselines}"
  local name_filter="${2:-}"
  local format="${3:-tsv}"
  local sort_spec="${4:-name}"
  local rows_file list_file
  if [[ "$format" != "tsv" && "$format" != "table" ]]; then
    echo "Unknown baseline list format: $format" >&2
    echo "Available formats: tsv, table" >&2
    return 2
  fi
  rows_file="$(mktemp "${TMPDIR:-/tmp}/aqli-onnx-baselines-rows.XXXXXX")"
  list_file="$(mktemp "${TMPDIR:-/tmp}/aqli-onnx-baselines-list.XXXXXX")"
  aqli_onnx_performance_baseline_rows "$root" "$name_filter" > "$rows_file"
  aqli_onnx_performance_baseline_header > "$list_file"
  if ! aqli_onnx_performance_baseline_sort_rows "$rows_file" "$sort_spec" >> "$list_file"; then
    rm -f "$rows_file" "$list_file"
    return 2
  fi
  if [[ "$format" == "table" ]]; then
    aqli_onnx_performance_baseline_table < "$list_file"
  else
    cat "$list_file"
  fi
  rm -f "$rows_file" "$list_file"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -euo pipefail
  command="${1:-list}"
  [[ $# -gt 0 ]] && shift
  case "$command" in
    list|--list)
      root="ops/benchmarks/onnx/baselines"
      name=""
      format="tsv"
      sort_spec="name"
      positional=0
      while [[ $# -gt 0 ]]; do
        case "$1" in
          --root) root="$2"; shift 2 ;;
          --root=*) root="${1#*=}"; shift ;;
          --name) name="$2"; shift 2 ;;
          --name=*) name="${1#*=}"; shift ;;
          --format) format="$2"; shift 2 ;;
          --format=*) format="${1#*=}"; shift ;;
          --sort) sort_spec="$2"; shift 2 ;;
          --sort=*) sort_spec="${1#*=}"; shift ;;
          *)
            positional=$((positional + 1))
            case "$positional" in
              1) root="$1" ;;
              2) name="$1" ;;
              3) format="$1" ;;
              4) sort_spec="$1" ;;
              *) echo "Unexpected argument: $1" >&2; exit 2 ;;
            esac
            shift
            ;;
        esac
      done
      aqli_onnx_performance_baseline_list "$root" "$name" "$format" "$sort_spec"
      ;;
    *)
      echo "Usage: onnx-performance-baselines.sh [list [ROOT [NAME [FORMAT [SORT]]]]]" >&2
      echo "       onnx-performance-baselines.sh list --root ROOT --name NAME --format tsv|table --sort FIELD" >&2
      exit 2
      ;;
  esac
fi
