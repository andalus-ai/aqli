package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.sdk.route.RoutePreflightReport;
import tech.kayys.aqli.sdk.route.RouteReportPayloads;
import tech.kayys.aqli.sdk.route.RunnerRouteReport;
import tech.kayys.aqli.safetensor.engine.route.DirectSafetensorRoutePolicy;
import tech.kayys.aqli.safetensor.engine.route.DirectSafetensorRoutePreflight;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Coordinates selected-route validation and route-report preparation for the
 * {@code run} CLI adapter.
 *
 * <p>This class does not select a runtime route or execute inference. It prepares
 * the structured route-report payload; terminal output and process lifecycle remain
 * the responsibility of the CLI command.</p>
 */
public final class RunRouteValidationCoordinator {

    private RunRouteValidationCoordinator() {
    }

    public static DirectSafetensorRoutePolicy.RouteValidation validateExecutionRoute(
            String providerId,
            String modelId,
            String localPath) {
        return DirectSafetensorRoutePolicy.validateGemma3ExecutionRoute(
                providerId, modelId, localPath);
    }

    public static RouteReportResult prepareRouteReport(
            RunnerRouteReport routeReport,
            String requestedModelRef,
            String modelId,
            String localPath,
            String selectedProviderId,
            String format,
            boolean directExecution,
            boolean allowPull,
            boolean repositoryResolutionSkipped,
            boolean requireLocal,
            boolean hasInputImages,
            boolean ocrMode,
            UnaryOperator<RunnerRouteReport> routeProfile) {

        String executionProviderId = selectedProviderId;
        RunnerRouteReport effectiveRouteReport =
                routeReport.withEffectiveRoute(executionProviderId, format);
        if (directExecution) {
            executionProviderId = "safetensor";
            effectiveRouteReport =
                    effectiveRouteReport.withEffectiveRoute(executionProviderId, format);
        }
        if (routeProfile != null) {
            effectiveRouteReport = routeProfile.apply(effectiveRouteReport);
        }

        RoutePreflightReport preflight = RoutePreflightReport.evaluate(
                requestedModelRef,
                modelId,
                localPath,
                executionProviderId,
                format,
                allowPull,
                requireLocal);
        preflight = DirectSafetensorRoutePreflight.applyGemma4UnifiedValidation(
                preflight,
                executionProviderId,
                modelId,
                localPath,
                hasInputImages,
                ocrMode);

        Map<String, Object> payload = RouteReportPayloads.routeReportPayload(
                effectiveRouteReport,
                requestedModelRef,
                modelId,
                localPath,
                executionProviderId,
                format,
                allowPull,
                repositoryResolutionSkipped,
                preflight);
        return new RouteReportResult(payload, preflight.exitCode());
    }

    public record RouteReportResult(Map<String, Object> payload, int exitCode) {
    }
}
