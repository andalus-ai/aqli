package tech.kayys.aqli.cli.commands.route;

/** Immutable snapshot of route-report execution options. */
public record RouteReportRunOptions(
        boolean enabled,
        boolean allowPull,
        boolean requireLocal) {
}
