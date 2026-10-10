#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "Backend targets: metal"
echo "Resolved backend targets: metal"
echo "Resolved format targets: safetensor,gguf,litert,onnx"
echo "Resolved LLM targets: text,vision,audio,multimodal,stable-diffusion,whispering"
echo "Architecture: binding"
echo "Profile: snapshot"
echo "Selection manifest: /Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/aqli/scripts/module-selection-metal-safetensor-gguf-litert-onnx-text-vision-audio-multimodal-stable-diffusion-whispering-binding.json"


./gradlew clean :backend:metal:aqli-backend-metal:build :backend:metal:aqli-mlx-binding:build :core:aqli-core:build :core:aqli-error-code:build :core:aqli-model-repository:build :core:aqli-provider-core:build :core:aqli-tokenizer-core:build :core:plugin:aqli-plugin-kernel-core:build :core:plugin:aqli-plugin-runner-gguf:build :models:aqli-model-gemma:build :models:aqli-model-llama:build :models:aqli-model-mistral:build :models:aqli-model-phi:build :models:aqli-model-qwen:build :models:aqli-model-repo-hf:build :models:aqli-model-repo-local:build :models:aqli-model-runner:build :runner:diffuser:aqli-diffuser:build :runner:gguf:aqli-gguf-converter:build :runner:gguf:aqli-gguf-converter-java:build :runner:gguf:aqli-gguf-core:build :runner:litert:aqli-litert-core:build :runner:litert:aqli-plugin-runner-litert:build :runner:litert:aqli-runner-litert:build :runner:onnx:aqli-ml-export-onnx:build :runner:onnx:aqli-ml-onnx:build :runner:onnx:aqli-plugin-runner-onnx:build :runner:onnx:aqli-runner-onnx:build :runner:safetensor:aqli-runner-safetensor:build :runner:safetensor:aqli-runner-stable-diffusion:build :runner:safetensor:aqli-safetensor-api:build :runner:safetensor:aqli-safetensor-core:build :runner:safetensor:aqli-safetensor-engine:build :runner:safetensor:aqli-safetensor-loader:build :runner:safetensor:aqli-safetensor-quantization:build :runner:safetensor:aqli-safetensor-spi:build :sdk:aqli-sdk:build :sdk:aqli-sdk-api:build :sdk:aqli-sdk-core:build :sdk:aqli-sdk-local:build :sdk:aqli-sdk-session:build :spi:aqli-spi:build :spi:aqli-spi-inference:build :spi:aqli-spi-model:build :spi:aqli-spi-multimodal:build :spi:aqli-spi-plugin:build :spi:aqli-spi-provider:build :spi:aqli-spi-runtime:build :ml:aqli-ml-nlp:build :ml:aqli-ml-audio:build :ml:aqli-ml-cnn:build :ml:aqli-ml-multimodal:build :ml:aqli-ml-vision:build :ui:aqli-api:build :ui:aqli-cli:build \
  -Paqli.backend=cpu,metal \
  -Paqli.profile=snapshot \
  -Paqli.model.formats=all \
  -Paqli.llm.types=all \
  -Paqli.architecture=binding
