package tech.kayys.aqli.cli.commands.retrieval;

import java.util.List;
import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots retrieval context and embedding-model options. */
public final class RetrievalContextRunCapability
        implements RunCapability, RunCapabilityOptions<RetrievalContextRunOptions> {

    @Override
    public String id() {
        return "retrieval-context";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder("--rag-context")
                .type(List.class)
                .auxiliaryTypes(String.class)
                .splitRegex("\\|")
                .description("Inline retrieval context block(s), separated with | when repeated in one argument")
                .build());
        commandSpec.addOption(OptionSpec.builder("--rag-file", "--context-file")
                .type(List.class)
                .auxiliaryTypes(String.class)
                .splitRegex(",")
                .description("Text file(s) to inject as retrieval context")
                .build());
        commandSpec.addOption(OptionSpec.builder("--rag-max-chars")
                .type(int.class)
                .defaultValue("12000")
                .description("Maximum retrieval context characters to inject")
                .build());
        commandSpec.addOption(OptionSpec.builder("--embedding-model", "--embed-model")
                .type(String.class)
                .description("Embedding model id/path to attach to embedding or RAG-aware providers")
                .build());
    }

    @Override
    public Class<RetrievalContextRunOptions> optionsType() {
        return RetrievalContextRunOptions.class;
    }

    @Override
    public RetrievalContextRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new RetrievalContextRunOptions(
                parseResult.matchedOptionValue("--rag-context", List.of()),
                parseResult.matchedOptionValue("--rag-file", List.of()),
                parseResult.matchedOptionValue("--rag-max-chars", 12000),
                parseResult.matchedOptionValue("--embedding-model", (String) null));
    }
}
