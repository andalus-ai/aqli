package tech.kayys.aqli.cli.commands.route;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import tech.kayys.aqli.sdk.route.RunnerRoutePolicy;
import tech.kayys.aqli.sdk.route.RunnerRouteReportFields;

/**
 * Formats resolved execution-route metadata for human-readable CLI output.
 *
 * Provider and runner metadata semantics are defined by the runtime; this class
 * owns only the terminal presentation of those values.
 */
public final class ExecutionRouteTerminalRenderer {

    private static final String SEPARATOR = "--------------------------------------------------";

    private ExecutionRouteTerminalRenderer() {
    }

    /**
     * Returns the execution-route lines to display.
     *
     * @param metadata resolved execution-route metadata
     * @param noBanner whether route presentation is suppressed
     * @return zero or two terminal lines
     */
    public static List<String> render(Map<String, Object> metadata, boolean noBanner) {
        if (metadata == null || metadata.isEmpty() || noBanner) {
            return List.of();
        }

        Object requested = metadata.get("requested_provider");
        Object effective = metadata.get("effective_provider");
        Object backend = metadata.get("execution_backend");
        Object bridgeMode = metadata.get("provider_bridge_mode");
        Object bridgeReason = metadata.get("provider_bridge_reason");
        Object runnerRoute = metadata.get(
                RunnerRouteReportFields.metadataKey(RunnerRouteReportFields.Report.NORMALIZED_RUNNER));
        Object runnerMode = metadata.get(
                RunnerRouteReportFields.metadataKey(RunnerRouteReportFields.Report.MODE));

        if (requested == null && effective == null && backend == null && bridgeMode == null && runnerRoute == null) {
            return List.of();
        }

        StringBuilder line = new StringBuilder("Execution route: ");
        if (requested != null && effective != null
                && !String.valueOf(requested).equalsIgnoreCase(String.valueOf(effective))) {
            line.append(requested).append(" -> ").append(effective);
        } else if (effective != null) {
            line.append(effective);
        } else if (requested != null) {
            line.append(requested);
        } else {
            line.append(backend);
        }
        if (backend != null) {
            line.append(" (backend=").append(backend).append(")");
        }
        if (runnerRoute != null) {
            line.append(" (runner=").append(runnerRoute);
            if (runnerMode != null && !RunnerRoutePolicy.AUTO.equals(String.valueOf(runnerMode))) {
                line.append(", mode=").append(runnerMode);
            }
            line.append(")");
        }
        if (bridgeMode != null) {
            line.append(" [").append(bridgeMode);
            if (bridgeReason != null) {
                line.append(": ").append(bridgeReason);
            }
            line.append("]");
        }
        return List.of(line.toString(), SEPARATOR);
    }

    /**
     * Writes the formatted route lines to the supplied terminal output.
     */
    public static void print(Map<String, Object> metadata, boolean noBanner, Consumer<String> output) {
        if (output == null) {
            throw new IllegalArgumentException("output must not be null");
        }
        render(metadata, noBanner).forEach(output);
    }
}
