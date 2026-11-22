package tech.kayys.aqli.cli.commands.request;

/** Immutable response-cache behavior for one inference run. */
public record ResponseCacheRunOptions(boolean bypassCache) {
}
