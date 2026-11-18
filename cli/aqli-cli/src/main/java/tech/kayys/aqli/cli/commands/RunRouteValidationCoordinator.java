package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.sdk.route.RoutePreflightReport;
import tech.kayys.aqli.sdk.route.RunnerRouteReport;
import tech.kayys.aqli.safetensor.engine.route.DirectSafetensorRoutePolicy;
import tech.kayys.aqli.safetensor.engine.route.DirectSafetensorRoutePreflight;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Coordinates route validation and preflight evaluation for the {@code run} CLI adapter.
 *
 * <p>This class does not select a runtime route or execute inference. It validates the
 * selected route and prepares the report consumed by the CLI's route-report output.</p>
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

    public static PreflightResult preparePreflight(
            RunnerRouteReport routeReport,
            String requestedModelRef,
            String modelId,
            String localPath,
            String selectedProviderId,
            String format,
            boolean directExecution,
            boolean allowPull,
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
        return new PreflightResult(executionProviderId, effectiveRouteReport, preflight);
    }

    public record PreflightResult(
            String executionProviderId,
            RunnerRouteReport routeReport,
            RoutePreflightReport preflight) {
    }
}
