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

./gradlew :backend:metal:aqli-backend-metal:publishToMavenLocal :backend:metal:aqli-mlx-binding:publishToMavenLocal :core:aqli-core:publishToMavenLocal :core:aqli-error-code:publishToMavenLocal :core:aqli-model-repository:publishToMavenLocal :core:aqli-provider-core:publishToMavenLocal :core:aqli-tokenizer-core:publishToMavenLocal :core:plugin:aqli-plugin-kernel-core:publishToMavenLocal :models:aqli-model-gemma:publishToMavenLocal :models:aqli-model-llama:publishToMavenLocal :models:aqli-model-mistral:publishToMavenLocal :models:aqli-model-phi:publishToMavenLocal :models:aqli-model-qwen:publishToMavenLocal :models:aqli-model-repo-hf:publishToMavenLocal :models:aqli-model-repo-local:publishToMavenLocal :models:aqli-model-runner:publishToMavenLocal :runner:litert:aqli-litert-core:publishToMavenLocal :runner:onnx:aqli-ml-export-onnx:publishToMavenLocal :runner:onnx:aqli-ml-onnx:publishToMavenLocal :runner:onnx:aqli-plugin-runner-onnx:publishToMavenLocal :runner:onnx:aqli-runner-onnx:publishToMavenLocal :sdk:aqli-sdk:publishToMavenLocal :sdk:aqli-sdk-api:publishToMavenLocal :sdk:aqli-sdk-core:publishToMavenLocal :sdk:aqli-sdk-local:publishToMavenLocal :sdk:aqli-sdk-session:publishToMavenLocal :spi:aqli-spi:publishToMavenLocal :spi:aqli-spi-inference:publishToMavenLocal :spi:aqli-spi-model:publishToMavenLocal :spi:aqli-spi-multimodal:publishToMavenLocal :spi:aqli-spi-plugin:publishToMavenLocal :spi:aqli-spi-provider:publishToMavenLocal :spi:aqli-spi-runtime:publishToMavenLocal :ui:aqli-api:publishToMavenLocal :ui:aqli-cli:publishToMavenLocal \
  -Paqli.backend=cpu,metal \
  -Paqli.profile=snapshot \
  -Paqli.model.formats=onnx \
  -Paqli.llm.types=text \
  -Paqli.architecture=binding
