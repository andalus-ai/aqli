package tech.kayys.aqli.cli.commands.tts;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots operational TTS diagnostics, warmup, and cache-maintenance options. */
public final class TtsOperationsRunCapability
        implements RunCapability, RunCapabilityOptions<TtsOperationsRunOptions> {

    @Override
    public String id() {
        return "tts-operations";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--tts-diagnostics", "--tts-preflight")
                .type(boolean.class)
                .description("Inspect local MOSS TTS model, codec, voices, and audio encoders without generating audio")
                .build());
        commandSpec.addOption(OptionSpec.builder("--tts-warmup", "--onnx-warmup")
                .type(boolean.class)
                .description("Warm MOSS TTS ONNX assets and sessions without generating audio")
                .build());
        commandSpec.addOption(OptionSpec.builder("--tts-cache-prune", "--onnx-cache-prune")
                .type(boolean.class)
                .description("Preview removal of stale MOSS TTS optimized graph cache files")
                .build());
        commandSpec.addOption(OptionSpec.builder("--tts-cache-prune-apply", "--onnx-cache-prune-apply")
                .type(boolean.class)
                .description("Actually delete stale MOSS TTS optimized graph cache files")
                .build());
    }

    @Override
    public Class<TtsOperationsRunOptions> optionsType() {
        return TtsOperationsRunOptions.class;
    }

    @Override
    public TtsOperationsRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new TtsOperationsRunOptions(
                parseResult.matchedOptionValue("--tts-diagnostics", false),
                parseResult.matchedOptionValue("--tts-warmup", false),
                parseResult.matchedOptionValue("--tts-cache-prune", false),
                parseResult.matchedOptionValue("--tts-cache-prune-apply", false));
    }
}
