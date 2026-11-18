package tech.kayys.aqli.cli.commands;

import tech.kayys.aqli.sdk.route.RunnerRouteReport;
import tech.kayys.aqli.safetensor.engine.route.DirectSafetensorRoutePolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Coordinates the ordered alternate-runtime checks for a locally resolved model.
 *
 * <p>The underlying route policy remains the source of selection rules. This
 * class only applies its decisions in the same order and carries the resulting
 * model/provider/format and route-report state back to the CLI adapter.</p>
 */
public final class AlternateRuntimeSelectionCoordinator {

    private AlternateRuntimeSelectionCoordinator() {
    }

    public record Options(
            String providerId,
            String modelId,
            String localPath,
            String format,
            boolean providerExplicit,
            boolean preferAlternateRuntime,
            RunnerRouteReport routeReport) {
    }

    public record Result(
            String providerId,
            String modelId,
            String localPath,
            String format,
            RunnerRouteReport routeReport,
            List<String> notices) {
        public Result {
            notices = List.copyOf(notices);
        }
    }

    public static Result select(
            Options options,
            CliLocalModelResolver localModelResolver,
            Predicate<String> providerActive,
            Predicate<String> runtimeRouteActive) {
        String providerId = options.providerId();
        String modelId = options.modelId();
        String localPath = options.localPath();
        String format = options.format();
        RunnerRouteReport routeReport = options.routeReport();
        List<String> notices = new ArrayList<>();

        DirectSafetensorRoutePolicy.AlternateRuntimeSelection gemma3 =
                DirectSafetensorRoutePolicy.selectGemma3AlternateRuntime(
                        providerId, modelId, localPath, options.providerExplicit(),
                        options.preferAlternateRuntime(), providerActive);
        addNotice(notices, gemma3);
        if (gemma3.selected()) {
            String previousProviderId = providerId;
            String previousFormat = format;
            providerId = gemma3.provider();
            localPath = gemma3.localPath();
            format = gemma3.format();
            routeReport = routeReport.withRuntimeRedirect(
                    previousProviderId, previousFormat, providerId, format,
                    gemma3.reason(), gemma3.cacheHit(), gemma3.cacheKind());
            if ("gguf".equalsIgnoreCase(format)) {
                modelId = localPath;
            }
        }

        DirectSafetensorRoutePolicy.AlternateRuntimeSelection gemma4MobileQat =
                DirectSafetensorRoutePolicy.selectGemma4MobileQatAlternateRuntime(
                        providerId, modelId, localPath, options.providerExplicit(),
                        options.preferAlternateRuntime(), runtimeRouteActive,
                        localModelResolver::resolveLiteRtModel);
        addNotice(notices, gemma4MobileQat);
        if (gemma4MobileQat.selected()) {
            String previousProviderId = providerId;
            String previousFormat = format;
            providerId = gemma4MobileQat.provider();
            localPath = gemma4MobileQat.localPath();
            format = gemma4MobileQat.format();
            routeReport = routeReport.withRuntimeRedirect(
                    previousProviderId, previousFormat, providerId, format,
                    gemma4MobileQat.reason(), gemma4MobileQat.cacheHit(),
                    gemma4MobileQat.cacheKind());
        }

        DirectSafetensorRoutePolicy.AlternateRuntimeSelection nativeBf16Matvec =
                DirectSafetensorRoutePolicy.selectGemma4TextAlternateRuntime(
                        providerId, modelId, localPath, options.providerExplicit(),
                        options.preferAlternateRuntime(), runtimeRouteActive,
                        localModelResolver);
        addNotice(notices, nativeBf16Matvec);
        if (nativeBf16Matvec.selected()) {
            String previousProviderId = providerId;
            String previousFormat = format;
            providerId = nativeBf16Matvec.provider();
            localPath = nativeBf16Matvec.localPath();
            modelId = localPath;
            format = nativeBf16Matvec.format();
            routeReport = routeReport.withRuntimeRedirect(
                    previousProviderId, previousFormat, providerId, format,
                    nativeBf16Matvec.reason(), nativeBf16Matvec.cacheHit(),
                    nativeBf16Matvec.cacheKind());
        }

        DirectSafetensorRoutePolicy.AlternateRuntimeSelection communityTextGguf =
                DirectSafetensorRoutePolicy.selectCommunityTextGgufAlternateRuntime(
                        providerId, modelId, localPath, options.providerExplicit(),
                        options.preferAlternateRuntime(), runtimeRouteActive,
                        localModelResolver);
        addNotice(notices, communityTextGguf);
        if (communityTextGguf.selected()) {
            String previousProviderId = providerId;
            String previousFormat = format;
            providerId = communityTextGguf.provider();
            localPath = communityTextGguf.localPath();
            modelId = localPath;
            format = communityTextGguf.format();
            routeReport = routeReport.withRuntimeRedirect(
                    previousProviderId, previousFormat, providerId, format,
                    communityTextGguf.reason(), communityTextGguf.cacheHit(),
                    communityTextGguf.cacheKind());
        }

        return new Result(providerId, modelId, localPath, format, routeReport, notices);
    }

    private static void addNotice(
            List<String> notices,
            DirectSafetensorRoutePolicy.AlternateRuntimeSelection selection) {
        if (selection.hasNotice()) {
            notices.add(selection.notice());
        }
    }
}
