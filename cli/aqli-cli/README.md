# Aqli CLI

> The command-line interface for the [Aqli inference engine](../../README.md).  
> Supports local GGUF, SafeTensors, LiteRT, ONNX, and cloud providers from one unified tool.

---

## Table of Contents

- [Install](#install)
- [Build from Source](#build-from-source)
- [Commands Overview](#commands-overview)
- [Model Management](#model-management)
  - [pull](#aqli-pull)
  - [list](#aqli-list)
  - [show](#aqli-show)
  - [delete](#aqli-delete)
- [Inference](#inference)
  - [run](#aqli-run)
  - [chat](#aqli-chat)
  - [embed](#aqli-embed)
- [Advanced](#advanced)
  - [convert](#aqli-convert)
  - [serve](#aqli-serve)
  - [mcp](#aqli-mcp)
  - [providers](#aqli-providers)
- [Environment Variables](#environment-variables)

---

## Install

```bash
# macOS / Linux
curl -fsSL https://github.com/bhangun/aqli/releases/latest/download/install.sh | bash

# Homebrew
brew tap bhangun/aqli && brew install aqli

# Windows (native executable)
Invoke-WebRequest -Uri "https://github.com/bhangun/aqli/releases/latest/download/aqli-windows-x64.exe" -OutFile "aqli.exe"
```

---

## Build from Source

```bash
# JVM build (Gradle)
./gradlew :ui:aqli-cli:quarkusBuild -x test

# Native image (requires GraalVM 25)
./gradlew :ui:aqli-cli:buildNative -x test
```

> **Note**: The GGUF runner uses Panama FFM to bridge llama.cpp.  
> GraalVM native builds require `reachability-metadata.json` in `aqli-ext-runner-gguf`.

---

## Commands Overview

| Command | Description |
|---------|-------------|
| `aqli pull` | Download a model from HuggingFace / Kaggle |
| `aqli list` | List locally installed models |
| `aqli show` | Inspect model metadata |
| `aqli delete` | Remove a local model |
| `aqli run` | Single-shot inference |
| `aqli chat` | Interactive multi-turn chat |
| `aqli embed` | Generate embeddings |
| `aqli convert` | Convert a model to GGUF |
| `aqli serve` | Start an OpenAI-compatible API server |
| `aqli mcp` | Manage MCP tool servers |
| `aqli providers` | List available inference providers |
| `aqli system info` | Show system and resource info |

---

## Model Management

### `aqli pull`

Download a model from HuggingFace (or a local path).  
The CLI **automatically detects the model's task type** from the HuggingFace `pipeline_tag`  
(e.g. `text-generation` → `text`, `automatic-speech-recognition` → `stt`).

```bash
# Pull a GGUF chat model (task type auto-detected: text)
aqli pull hf:Qwen/Qwen2.5-7B-Instruct-GGUF

# Pull a vision model (auto-detected: vision)
aqli pull hf:HuggingFaceTB/SmolVLM-256M-Instruct

# Pull a speech model (auto-detected: stt)
aqli pull hf:Systran/faster-whisper-large-v3

# Manually override task category
aqli pull hf:myorg/my-tts-model --task-type tts

# Filter which files to download (glob, useful for large GGUF repos)
aqli pull hf:bartowski/gemma-3-12b-it-GGUF --include "*Q4_K_M*"
```

**Supported task types**: `text`, `vision`, `stt`, `tts`, `ocr`, `multimodal`, `embedding`

---

### `aqli list`

List all locally installed models. The `TASK` column shows the model's task category.

```bash
aqli list
```

```
ID      GROUP          NAME                       ARCH         FORMAT     TASK       SIZE         MODIFIED
───────────────────────────────────────────────────────────────────────────────────────────────────────────────
c961e2  google         gemma-3-12b-it-Q4_K_M      llama        GGUF       text       7.32 GB      2026-08-10
3f1a2b  Systran        faster-whisper-large-v3    whisper      SAFETENS.  stt        3.09 GB      2026-08-09
8d4c11  HuggingFaceTB  SmolVLM-256M-Instruct      smolvlm      SAFETENS.  vision     489 MB       2026-08-08
```

**Flags:**

| Flag | Description |
|------|-------------|
| `-t`, `--task-type` | Filter by task: `text`, `vision`, `stt`, `tts`, `embedding`, etc. |
| `--runnable-only` | Show only models runnable in local Java runtime |
| `-f`, `--format` | Output format: `table` (default) or `json` |
| `-l`, `--limit` | Maximum results (default: 50) |

```bash
# Show only vision models
aqli list -t vision

# Show only speech-to-text models in JSON
aqli list --task-type stt --format json

# Show only runnable models
aqli list --runnable-only
```

**TASK column colour coding** (terminal):

| Task | Colour |
|------|--------|
| `text` | white |
| `vision` / `ocr` | magenta |
| `tts` | green |
| `stt` | blue |
| `multimodal` | cyan |
| `embedding` | gray |

---

### `aqli show`

Show detailed metadata for a model (architecture, quantization, size, task type, files, …).

```bash
aqli show c961e2
aqli show Qwen/Qwen2.5-7B-Instruct-GGUF
aqli show --json Qwen/Qwen2.5-7B-Instruct-GGUF
```

---

### `aqli delete`

Remove a locally installed model.

```bash
aqli delete c961e2
aqli delete Qwen/Qwen2.5-7B-Instruct-GGUF
```

---

## Inference

### `aqli run`

Single-shot inference. Most useful for scripting and piping.

```bash
aqli run --model c961e2 --prompt "Explain quantum entanglement in two sentences."

# Suppress banner and status lines (script-friendly)
aqli run --model c961e2 --no-banner --no-info --prompt "Hello"

# Print only the model's text (strip thinking tokens and metadata)
aqli run --model c961e2 --only-text --prompt "What is 2+2?"

# Raw token stream (includes <|thought|> tokens, no stripping)
aqli run --model c961e2 --raw --prompt "Solve: 15 * 7"

# Set temperature and max output tokens
aqli run --model c961e2 --temperature 0.3 --max-tokens 512 --prompt "Write a haiku."

# Use a system prompt
aqli run --model c961e2\
  --system "You are a concise assistant. Answer in one sentence." \
  --prompt "What is the capital of Japan?"

# Pass tools (OpenAI-style JSON file)
aqli run --model c961e2 --tool-file ./tools.json --tool-choice auto \
  --prompt "What is the weather in Jakarta?"

# Use MCP tools
aqli run --model c961e2 --mcp-tool filesystem/read_file \
  --prompt "Read the project README."
```

**Output flags:**

| Flag | Effect |
|------|--------|
| `--no-banner` | Suppress the startup banner |
| `--no-info` | Suppress model/provider info line |
| `--only-text` | Print only the assistant text (strips thinking tokens, stats) |
| `--raw` | Raw token stream; no post-processing |

> **Streaming note**: The native GGUF fast-path (`llama.cpp` JNI) returns the full response  
> buffer at once. True token-by-token streaming is available via the Java-based SafeTensors /  
> LiteRT runners or the `aqli serve` API endpoint.

---

### `aqli chat`

Interactive multi-turn chat session.

```bash
aqli chat --model c961e2

# With a persistent session (KV-cache across turns)
aqli chat --model c961e2 --session

# With RAG context from local files
aqli chat --model c961e2 \
  --rag-file ./docs/install.md \
  --rag-context "Prefer local install profiles." \
  --embedding-model text-embedding-3-small

# Quiet mode (suppress stats per turn)
aqli chat --model c961e2 --quiet
```

**In-chat commands:**

| Command | Action |
|---------|--------|
| `/reset` | Clear conversation history |
| `/exit` or `exit` | Quit |
| `\` at line end | Continue input on next line |

---

### `aqli embed`

Generate embeddings from text or a file.

```bash
aqli embed --model text-embedding-3-small --input "Hello world"
aqli embed --model text-embedding-3-small --input-file ./docs/README.md \
  --json --output /tmp/readme-embedding.json
```

---

## Advanced

### `aqli convert`

Convert a SafeTensors/PyTorch model to GGUF locally.

```bash
aqli convert --input ~/models/llama-2-7b --output ~/conversions --quant q4_k_m
aqli convert --input ~/models/llama-2-7b --output ~/conversions --dry-run
aqli convert --input ~/models/llama-2-7b --output ~/conversions --json-pretty
```

---

### `aqli serve`

Start an OpenAI-compatible local API server.

```bash
aqli serve --model c961e2 --port 8080

# With CORS enabled for browser clients
aqli serve --model c961e2 --port 8080 --cors
```

---

### `aqli mcp`

Manage MCP (Model Context Protocol) tool servers.

```bash
# Add a server
aqli mcp add --name image-downloader \
  --command node --args-json '["/path/to/index.js"]'

aqli mcp add --from-registry qpd-v/mcp-image-downloader
aqli mcp add --from-url https://example.com/mcp-servers.json

# Manage
aqli mcp list
aqli mcp show image-downloader --json
aqli mcp doctor
aqli mcp test image-downloader
aqli mcp enable image-downloader
aqli mcp disable image-downloader
aqli mcp remove image-downloader

# Import / export
aqli mcp export --file /tmp/mcp-servers.json
aqli mcp import --file /tmp/mcp-servers.json --merge
```

**Enterprise mode** (centralised DB-backed registry):

```bash
AQLI_ENTERPRISE_ENABLED=true \
AQLI_MCP_REGISTRY_MODE=remote \
AQLI_MCP_REGISTRY_API_BASE_URL=http://localhost:8080 \
AQLI_MCP_REGISTRY_API_TOKEN=<bearer-token> \
AQLI_TENANT_ID=<tenant-id> \
aqli mcp list
```

---

### `aqli providers`

List available inference providers and their health status.

```bash
aqli providers
```

```
ID              NAME                 VERSION    STATUS
------------------------------------------------------------
gguf            GGUF (llama.cpp)     1.1.0      HEALTHY
safetensor      SafeTensor Runner    1.0.0      HEALTHY
litert          LiteRT Runner        1.0.0      HEALTHY
onnx            ONNX Runner          1.0.0      HEALTHY
gemini          Google Gemini        1.0.0      HEALTHY
cerebras        Cerebras             1.0.0      HEALTHY
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `AQLI_HOME` | `~/.aqli` | Root data directory |
| `AQLI_LOG_DIR` | `~/.aqli/logs` | Log output directory |
| `AQLI_CLI_FILE_LOG` | `false` | Write logs to file |
| `AQLI_CLI_LOG_FILE` | `~/.aqli/logs/cli.log` | Log file path |
| `AQLI_LLAMA_LIB_DIR` | auto-detected | Directory containing `libllama.dylib` |
| `AQLI_LLAMA_LIB_PATH` | auto-detected | Full path to the llama native library |
| `GGUF_GPU_ENABLED` | `false` | Enable Metal / CUDA GPU acceleration |
| `GGUF_GPU_LAYERS` | `-1` (all) | Layers to offload to GPU |
| `GGUF_THREADS` | CPU count | CPU threads for inference |
| `GGUF_BATCH_SIZE` | `512` | Token batch size |
| `AQLI_ENTERPRISE_ENABLED` | `false` | Enable enterprise/remote MCP registry |

---

## Model Storage Layout

```
~/.aqli/models/
├── manifests/          # <modelId>.json — metadata, taskType, pipeline_tag
├── gguf/               # GGUF weight files  (auto-scanned)
├── safetensors/        # SafeTensors repos  (auto-scanned)
├── litert/             # LiteRT .tflite     (auto-scanned)
├── onnx/               # ONNX models        (auto-scanned)
└── index.json          # Cached model index (refreshed on every aqli list)
```

---

## License

MIT © Kayys.tech
