#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "Resolved backend targets: metal"
echo "Resolved format targets: safetensor,gguf,litert,onnx"
echo "Resolved LLM targets: text,vision,audio,multimodal,stable-diffusion,whispering"
echo "Architecture: binding"
echo "Profile: snapshot"
echo "Selection manifest: /Users/bhangun/Workspace/workkayys/Products/Wayang/wayang-platform/aqli/scripts/module-selection-metal-safetensor-gguf-litert-onnx-text-vision-audio-multimodal-stable-diffusion-whispering-binding.json"


./gradlew :backend:metal:aqli-backend-metal:publishToMavenLocal :backend:metal:aqli-mlx-binding:publishToMavenLocal :core:aqli-core:publishToMavenLocal :core:aqli-error-code:publishToMavenLocal :core:aqli-model-repository:publishToMavenLocal :core:aqli-provider-core:publishToMavenLocal :core:aqli-tokenizer-core:publishToMavenLocal :core:plugin:aqli-plugin-kernel-core:publishToMavenLocal :core:plugin:aqli-plugin-runner-gguf:publishToMavenLocal :models:aqli-model-gemma:publishToMavenLocal :models:aqli-model-llama:publishToMavenLocal :models:aqli-model-mistral:publishToMavenLocal :models:aqli-model-phi:publishToMavenLocal :models:aqli-model-qwen:publishToMavenLocal :models:aqli-model-repo-hf:publishToMavenLocal :models:aqli-model-repo-local:publishToMavenLocal :models:aqli-model-runner:publishToMavenLocal :runner:diffuser:aqli-diffuser:publishToMavenLocal :runner:gguf:aqli-gguf-converter:publishToMavenLocal :runner:gguf:aqli-gguf-converter-java:publishToMavenLocal :runner:gguf:aqli-gguf-core:publishToMavenLocal :runner:litert:aqli-litert-core:publishToMavenLocal :runner:litert:aqli-plugin-runner-litert:publishToMavenLocal :runner:litert:aqli-runner-litert:publishToMavenLocal :runner:onnx:aqli-ml-export-onnx:publishToMavenLocal :runner:onnx:aqli-ml-onnx:publishToMavenLocal :runner:onnx:aqli-plugin-runner-onnx:publishToMavenLocal :runner:onnx:aqli-runner-onnx:publishToMavenLocal :runner:safetensor:aqli-runner-safetensor:publishToMavenLocal :runner:safetensor:aqli-runner-stable-diffusion:publishToMavenLocal :runner:safetensor:aqli-safetensor-api:publishToMavenLocal :runner:safetensor:aqli-safetensor-core:publishToMavenLocal :runner:safetensor:aqli-safetensor-engine:publishToMavenLocal :runner:safetensor:aqli-safetensor-loader:publishToMavenLocal :runner:safetensor:aqli-safetensor-quantization:publishToMavenLocal :runner:safetensor:aqli-safetensor-spi:publishToMavenLocal :sdk:aqli-sdk:publishToMavenLocal :sdk:aqli-sdk-api:publishToMavenLocal :sdk:aqli-sdk-core:publishToMavenLocal :sdk:aqli-sdk-local:publishToMavenLocal :sdk:aqli-sdk-session:publishToMavenLocal :spi:aqli-spi:publishToMavenLocal :spi:aqli-spi-inference:publishToMavenLocal :spi:aqli-spi-model:publishToMavenLocal :spi:aqli-spi-multimodal:publishToMavenLocal :spi:aqli-spi-plugin:publishToMavenLocal :spi:aqli-spi-provider:publishToMavenLocal :spi:aqli-spi-runtime:publishToMavenLocal :ml:aqli-ml-nlp:publishToMavenLocal :ml:aqli-ml-audio:publishToMavenLocal :ml:aqli-ml-cnn:publishToMavenLocal :ml:aqli-ml-multimodal:publishToMavenLocal :ml:aqli-ml-vision:publishToMavenLocal :ui:aqli-api:publishToMavenLocal :ui:aqli-cli:publishToMavenLocal \
  -Paqli.backend=cpu,metal \
  -Paqli.profile=snapshot \
  -Paqli.model.formats=all \
  -Paqli.llm.types=all \
  -Paqli.architecture=binding
