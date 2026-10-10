#!/bin/bash
set -euo pipefail

RESET="\033[0m"
GREEN="\033[32m"
YELLOW="\033[33m"
CYAN="\033[36m"
MAGENTA="\033[35m"
BOLD="\033[1m"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo -e "${BOLD}${CYAN}:) Resolved backend targets:${RESET} metal"
echo -e "${BOLD}${CYAN}:) Resolved format targets:${RESET} onnx"
echo -e "${BOLD}${CYAN}:) Resolved LLM targets:${RESET} text"
echo -e "${BOLD}${MAGENTA}:) Architecture:${RESET} binding"
echo -e "${BOLD}${MAGENTA}:) Profile:${RESET} snapshot"
echo -e "${BOLD}${GREEN}:) Selection manifest:${RESET} /Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/aqli/scripts/module-selection-metal-onnx-text-binding.json"
echo ":) Module manifest resolved cleanly."

./gradlew clean :backend:metal:aqli-backend-metal:build :backend:metal:aqli-mlx-binding:build :core:aqli-core:build :core:aqli-error-code:build :core:aqli-model-repository:build :core:aqli-provider-core:build :core:aqli-tokenizer-core:build :core:plugin:aqli-plugin-kernel-core:build :models:aqli-model-gemma:build :models:aqli-model-llama:build :models:aqli-model-mistral:build :models:aqli-model-phi:build :models:aqli-model-qwen:build :models:aqli-model-repo-hf:build :models:aqli-model-repo-local:build :models:aqli-model-runner:build :runner:litert:aqli-litert-core:build :runner:onnx:aqli-ml-export-onnx:build :runner:onnx:aqli-ml-onnx:build :runner:onnx:aqli-plugin-runner-onnx:build :runner:onnx:aqli-runner-onnx:build :sdk:aqli-sdk:build :sdk:aqli-sdk-api:build :sdk:aqli-sdk-core:build :sdk:aqli-sdk-local:build :sdk:aqli-sdk-session:build :spi:aqli-spi:build :spi:aqli-spi-inference:build :spi:aqli-spi-model:build :spi:aqli-spi-multimodal:build :spi:aqli-spi-plugin:build :spi:aqli-spi-provider:build :spi:aqli-spi-runtime:build :ui:aqli-api:build :ui:aqli-cli:build \
  -Paqli.backend=cpu,metal \
  -Paqli.profile=snapshot \
  -Paqli.model.formats=onnx \
  -Paqli.llm.types=text \
  -Paqli.architecture=binding
