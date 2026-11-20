package tech.kayys.aqli.cli.commands.route;

import java.util.Objects;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParseResult;
import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityOptions;

/** Registers and snapshots route-report CLI options. */
public final class RouteReportRunCapability
        implements RunCapability, RunCapabilityOptions<RouteReportRunOptions> {

    @Override
    public String id() {
        return "route-report";
    }

    @Override
    public void registerOptions(CommandSpec commandSpec) {
        Objects.requireNonNull(commandSpec, "commandSpec");
        commandSpec.addOption(OptionSpec.builder(
                        "--route-report-json", "--route-report", "--dry-run-route")
                .type(boolean.class)
                .description("Resolve runner/provider/model routing, print JSON, and exit before inference")
                .build());
        commandSpec.addOption(OptionSpec.builder(
                        "--route-report-allow-pull", "--dry-run-route-allow-pull")
                .type(boolean.class)
                .description("Allow route-report mode to resolve/pull missing models through repository providers")
                .build());
        commandSpec.addOption(OptionSpec.builder(
                        "--route-report-require-local", "--dry-run-route-require-local")
                .type(boolean.class)
                .description("Exit non-zero when route-report mode cannot resolve a local runnable model artifact")
                .build());
    }

    @Override
    public Class<RouteReportRunOptions> optionsType() {
        return RouteReportRunOptions.class;
    }

    @Override
    public RouteReportRunOptions snapshot(ParseResult parseResult) {
        Objects.requireNonNull(parseResult, "parseResult");
        return new RouteReportRunOptions(
                parseResult.matchedOptionValue("--route-report-json", false),
                parseResult.matchedOptionValue("--route-report-allow-pull", false),
                parseResult.matchedOptionValue("--route-report-require-local", false));
    }
}
