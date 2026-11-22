package tech.kayys.aqli.cli.commands.response;

/** Immutable response delivery and encoding preferences for one run invocation. */
public record ResponseDeliveryRunOptions(
        boolean streaming,
        boolean jsonOutput,
        boolean openAiSse) {
}
