# AQLI CLI

The AQLI command-line application provides model management, inference, benchmarking, runtime diagnostics and API-server operations. It is implemented in `cli/aqli-cli` (Gradle project `:cli:aqli-cli`).

Supported formats and accelerators depend on the selected runner, model, host and build profile. A command being present does not guarantee that every model/provider combination is available.

## Requirements

- JDK 25
- Gradle wrapper (included in the repository)
- Platform-specific native libraries only for the runner/backend that requires them

## Build from source

```bash
git clone https://github.com/andalus-ai/aqli.git
cd aqli
./gradlew :cli:aqli-cli:quarkusBuild
```

Run the generated JVM CLI:

```bash
java --enable-native-access=ALL-UNNAMED --add-modules=jdk.incubator.vector \
  -jar cli/aqli-cli/build/aqli.jar --help
```

Native-image builds require a supported GraalVM/native-image setup and runner-specific reachability metadata:

```bash
./gradlew :cli:aqli-cli:buildNative
```

## Top-level commands

The following commands are registered by the current `AqliCommand`:

| Command | Purpose |
|---|---|
| `run` | Single-request inference |
| `chat` | Interactive conversation |
| `pull` | Download a model |
| `list`, `show`, `delete` | Discover and manage local models |
| `model` | Model-management operations |
| `prepare`, `prewarm` | Prepare models and warm runtimes |
| `benchmark`, `route-benchmarks` | Measure inference and routing |
| `embed`, `batch`, `multimodal` | Embedding, batch and multimodal inference |
| `jobs`, `stats`, `logs`, `info` | Runtime operations and diagnostics |
| `modules` | Inspect module/component availability |
| `extensions` | Discover and manage extensions and features |
| `safetensors`, `litert`, `onnx`, `quantize` | Format and runtime-specific operations |
| `register`, `server`, `serve`, `stop` | Model registration and server lifecycle |

Aliases include `features` for `extensions` and `components` for `modules`. Use the installed command's help output for the exact options:

```bash
aqli --help
aqli run --help
aqli pull --help
aqli serve --help
```

`convert`, `mcp`, and `providers` are not currently registered top-level commands. Model preparation/conversion workflows belong to `prepare` where supported. MCP server configuration is provided through inherited options and environment variables, not an `aqli mcp` subcommand.

## Common workflows

### Download a model

```bash
aqli pull hf:Qwen/Qwen2.5-7B-Instruct-GGUF
```

### List installed models

```bash
aqli list
aqli list --task-type vision
```

### Run inference

```bash
aqli run \
  --model hf:unsloth/gemma-4-12b-it-GGUF \
  --no-banner \
  --prompt "Who are you?" \
  --max-tokens 50
```

### Start the API server

```bash
aqli serve --model <model-id> --port 8080
```

### Inspect runtime capabilities

```bash
aqli info
aqli modules
aqli extensions
aqli stats
```

## Configuration

Hugging Face authentication can be provided through `HF_TOKEN`, `HUGGING_FACE_HUB_TOKEN`, or `AQLI_REPOSITORY_HUGGINGFACE_TOKEN`.

MCP server configuration can be supplied through the inherited `--mcp-servers-json` and `--mcp-servers-file` options, or the `AQLI_MCP_SERVERS_JSON` and `AQLI_MCP_SERVERS_FILE` environment variables.

For GGUF native library discovery, `AQLI_LLAMA_LIB_DIR` can point to the directory containing the required native libraries when the selected runner uses them.

## Related documentation

- [Repository quickstart](../../docs/QUICKSTART.md)
- [API quick reference](../../docs/API_QUICK_REFERENCE.md)
- [API examples](../../docs/API_EXAMPLES.md)
- [Module ownership matrix](../../docs/architecture/MODULE_OWNERSHIP_MATRIX.md)
