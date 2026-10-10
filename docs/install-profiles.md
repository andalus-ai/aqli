# Aqli Install Profiles

Aqli can be built and installed with different runtime preferences. Use an
install profile when you want the installer to choose defaults for backend,
model formats, architecture, and runtime behavior.

## Release Installer

```bash
curl -fsSL https://github.com/bhangun/aqli/releases/latest/download/install.sh | bash -s -- --profile auto
curl -fsSL https://github.com/bhangun/aqli/releases/latest/download/install.sh | bash -s -- --profile metal
curl -fsSL https://github.com/bhangun/aqli/releases/latest/download/install.sh | bash -s -- --profile cpu
```

You can also use an environment variable:

```bash
AQLI_INSTALL_PROFILE=metal \
  curl -fsSL https://github.com/bhangun/aqli/releases/latest/download/install.sh | bash
```

The installer writes the selected profile to:

```text
$AQLI_HOME/config/profile.env
$AQLI_HOME/config/profile.json
```

On macOS and Linux release installs, `/usr/local/bin/aqli` is a small wrapper
that sources `profile.env` before it launches the installed runtime binary.

## Source Build Installer

For source builds, run the builder UI and choose an install preference profile:

```bash
./create-builder-script.sh
./scripts/install-local-macos.sh
```

The builder writes the resolved module selection to:

```text
scripts/module-selection-current.env
```

## Profiles

| Profile | Best for | Main defaults |
| --- | --- | --- |
| `auto` | Most users | Detects `metal` on macOS, `cuda` on NVIDIA Linux, otherwise `cpu` |
| `cpu` | Portable installs and CI | CPU backend, GGUF, ONNX, safetensor, text models |
| `metal` | Apple Silicon local AI | Metal backend, GGUF, LiteRT, ONNX, safetensor, text and multimodal |
| `cuda` | NVIDIA workstations and servers | CUDA backend, GGUF, ONNX, safetensor, all LLM types |
| `mobile` | Flutter/mobile edge work | Mobile runtime, LiteRT, ONNX, text, speech-to-text, vision |
| `full` | Development builds | All runtime families, all formats, all model types |

Use `--list-profiles` to print the release installer choices.
