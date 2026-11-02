#!/bin/bash
#
aqli run --model hf:unsloth/gemma-4-12b-it-GGUF \
  --no-banner \
  --java \
  --prompt "who are you" \
  --max-tokens 50 \
