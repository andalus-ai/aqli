package tech.kayys.aqli.cli.commands.route;

import tech.kayys.aqli.cli.spi.run.RunCapability;
import tech.kayys.aqli.cli.spi.run.RunCapabilityProvider;

/** ServiceLoader provider for route-report CLI options. */
public final class RouteReportRunCapabilityProvider implements RunCapabilityProvider {

    @Override
    public String capabilityId() {
        return "route-report";
    }

    @Override
    public RunCapability create() {
        return new RouteReportRunCapability();
    }
}
