package tech.kayys.aqli.cli.commands;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.sdk.route.RunnerRouteBenchmarkCache;
import tech.kayys.aqli.sdk.route.RunnerRoutePolicy;
import tech.kayys.aqli.sdk.route.RunnerRouteReportContract;
import tech.kayys.aqli.safetensor.engine.route.*;

import org.junit.jupiter.api.Test;
import tech.kayys.aqli.cli.util.PluginGates;
import tech.kayys.aqli.sdk.route.RoutePreflightDiagnosticFields;
import tech.kayys.aqli.sdk.route.RouteReportPayloadContract;
import tech.kayys.aqli.sdk.route.RouteReportPayloadFields;
import tech.kayys.aqli.sdk.route.RunnerRouteReportContract;
import tech.kayys.aqli.sdk.route.RunnerRouteReportFields;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerRoutePolicyTest {
    @Test
    void omittedRunnerKeepsAutoDetectionInputsUntouched() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);

        assertTrue(selection.valid());
        assertEquals("auto", selection.runner());
        assertFalse(selection.explicit());
        assertEquals(null, selection.providerId());
        assertEquals(null, selection.format());
        assertFalse(selection.preferAlternateRuntime());
        assertFalse(selection.forceGguf());
    }

    @Test
    void explicitAutoAlsoKeepsProviderAndFormatUntouched() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select("auto", "onnx", "onnx", true, false, false);

        assertTrue(selection.valid());
        assertEquals("auto", selection.runner());
        assertFalse(selection.explicit());
        assertEquals("onnx", selection.providerId());
        assertEquals("onnx", selection.format());
    }

    @Test
    void safetensorRunnerForcesSafetensorProviderAndFormat() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select("safetensors", null, null, false, false, false);

        assertTrue(selection.valid());
        assertEquals("safetensor", selection.runner());
        assertTrue(selection.explicit());
        assertEquals("safetensor", selection.providerId());
        assertEquals("safetensors", selection.format());
        assertFalse(selection.forceGguf());
    }

    @Test
    void ggufRunnerForcesGgufProviderFormatAndConversionPreference() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select("gguf", null, null, false, false, false);

        assertTrue(selection.valid());
        assertEquals("gguf", selection.runner());
        assertEquals("gguf", selection.providerId());
        assertEquals("gguf", selection.format());
        assertTrue(selection.forceGguf());
    }

    @Test
    void litertRunnerForcesLitertProviderAndFormat() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select("tflite", null, null, false, false, false);

        assertTrue(selection.valid());
        assertEquals("litert", selection.runner());
        assertEquals("litert", selection.providerId());
        assertEquals("litert", selection.format());
    }

    @Test
    void hybridRunnerKeepsAutoProviderSelectionButEnablesAlternateRuntimePreference() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select("hybrid", null, null, false, false, false);

        assertTrue(selection.valid());
        assertEquals("hybrid", selection.runner());
        assertTrue(selection.explicit());
        assertEquals(null, selection.providerId());
        assertEquals(null, selection.format());
        assertTrue(selection.preferAlternateRuntime());
    }

    @Test
    void fixedRunnerRejectsConflictingExplicitProvider() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select("gguf", "litert", null, true, false, false);

        assertFalse(selection.valid());
        assertTrue(selection.error().contains("--runner gguf conflicts with --provider litert"));
    }

    @Test
    void unsupportedRunnerIsRejectedWithSupportedValues() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select("magic", null, null, false, false, false);

        assertFalse(selection.valid());
        assertTrue(selection.error().contains("Supported runners"));
        assertTrue(selection.error().contains("safetensor"));
    }

    @Test
    void routeReportCapturesRequestedAndEffectiveRoute() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select("safetensors", null, null, false, false, false);

        RunnerRouteReport report = RunnerRouteReport.from(
                        "safetensors",
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("safetensor", "safetensors");

        assertEquals("safetensors",
                report.toMap().get(RunnerRouteReportFields.Report.REQUESTED_RUNNER));
        assertEquals("safetensor",
                report.toMap().get(RunnerRouteReportFields.Report.NORMALIZED_RUNNER));
        assertEquals(RunnerRouteReportFields.SelectionSource.RUNNER_CLI,
                report.toMap().get(RunnerRouteReportFields.Report.SELECTION_SOURCE));
        assertEquals("safetensor",
                report.toMap().get(RunnerRouteReportFields.Report.EFFECTIVE_PROVIDER));
        assertEquals(false,
                report.toMap().get(RunnerRouteReportFields.Report.RUNTIME_REDIRECTED));
        assertEquals("fixed", report.toMap().get(RunnerRouteReportFields.Report.MODE));
        assertEquals(RunnerRouteReportFields.RouteProfileStatus.CANDIDATE,
                report.toMap().get(RunnerRouteReportFields.Report.ROUTE_PROFILE_STATUS));
        assertEquals(RunnerRouteReportFields.RouteProfileSource.HEURISTIC,
                report.toMap().get(RunnerRouteReportFields.Report.ROUTE_PROFILE_SOURCE));
        assertEquals("gguf",
                report.toMap().get(RunnerRouteReportFields.Report.ROUTE_PROFILE_PROVIDER));
        assertEquals("gguf",
                report.toMap().get(RunnerRouteReportFields.Report.ROUTE_PROFILE_FORMAT));
        assertEquals("safetensor", report.toMetadata().get(
                RunnerRouteReportFields.metadataKey(RunnerRouteReportFields.Report.NORMALIZED_RUNNER)));
        assertEquals("safetensor", report.toMetadata().get(RunnerRouteReportFields.Report.EFFECTIVE_PROVIDER));
        assertTrue(report.toMetadata().containsKey(RunnerRouteReportFields.VALIDATION_METADATA_ROOT));
        assertEquals(true, report.toMetadata().get(
                RunnerRouteReportFields.validationMetadataKey(RunnerRouteReportFields.Validation.PASSED)));
        assertEquals(0, report.toMetadata().get(
                RunnerRouteReportFields.validationMetadataKey(RunnerRouteReportFields.Validation.PROBLEM_COUNT)));
        assertEquals(true, RunnerRouteReportContract.validateReport(report.toMap()).isEmpty());
        assertEquals(true, RunnerRouteReportContract.validateSchema(RunnerRouteReportContract.schema()).isEmpty());
    }

    @Test
    void routeReportCapturesProviderSelectionSourceWhenRunnerIsOmitted() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, "gguf", "gguf", true, false, false);

        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        "gguf",
                        true,
                        "gguf",
                        selection)
                .withEffectiveRoute("gguf", "gguf");

        assertEquals(RunnerRouteReportFields.SelectionSource.PROVIDER_CLI,
                report.toMap().get(RunnerRouteReportFields.Report.SELECTION_SOURCE));
        assertEquals(false,
                report.toMap().get(RunnerRouteReportFields.Report.AUTO_DETECTED));
        assertEquals(true, RunnerRouteReportContract.validateReport(report.toMap()).isEmpty());
    }

    @Test
    void routeReportCapturesRuntimeRedirect() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);

        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("safetensor", "safetensors")
                .withRuntimeRedirect(
                        "safetensor",
                        "safetensors",
                        "gguf",
                        "gguf",
                        "Detected Qwen safetensor checkpoint with local GGUF runtime artifact");

        assertEquals(true,
                report.toMap().get(RunnerRouteReportFields.Report.RUNTIME_REDIRECTED));
        assertEquals("safetensor",
                report.toMap().get(RunnerRouteReportFields.Report.RUNTIME_REDIRECT_FROM_PROVIDER));
        assertEquals("safetensors",
                report.toMap().get(RunnerRouteReportFields.Report.RUNTIME_REDIRECT_FROM_FORMAT));
        assertEquals("gguf",
                report.toMap().get(RunnerRouteReportFields.Report.RUNTIME_REDIRECT_TO_PROVIDER));
        assertEquals("gguf",
                report.toMap().get(RunnerRouteReportFields.Report.RUNTIME_REDIRECT_TO_FORMAT));
        assertEquals("Detected Qwen safetensor checkpoint with local GGUF runtime artifact",
                report.toMap().get(RunnerRouteReportFields.Report.RUNTIME_REDIRECT_REASON));
        assertEquals(false,
                report.toMap().get(RunnerRouteReportFields.Report.RUNTIME_REDIRECT_CACHE_HIT));
        assertFalse(report.toMap().containsKey(RunnerRouteReportFields.Report.RUNTIME_REDIRECT_CACHE_KIND));
        assertEquals("gguf",
                report.toMap().get(RunnerRouteReportFields.Report.EFFECTIVE_PROVIDER));
        assertEquals(RunnerRouteReportFields.RouteProfileStatus.REDIRECTED,
                report.toMap().get(RunnerRouteReportFields.Report.ROUTE_PROFILE_STATUS));
        assertEquals(RunnerRouteReportFields.RouteProfileSource.RUNTIME_REDIRECT,
                report.toMap().get(RunnerRouteReportFields.Report.ROUTE_PROFILE_SOURCE));
        assertEquals("gguf",
                report.toMap().get(RunnerRouteReportFields.Report.ROUTE_PROFILE_PROVIDER));
        assertEquals("gguf",
                report.toMap().get(RunnerRouteReportFields.Report.ROUTE_PROFILE_FORMAT));
        assertEquals(true, RunnerRouteReportContract.validateReport(report.toMap()).isEmpty());
        assertEquals(true, report.toMetadata().get(
                RunnerRouteReportFields.validationMetadataKey(RunnerRouteReportFields.Validation.PASSED)));
    }

    @Test
    void runnerRouteContractRejectsUnknownSelectionSource() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                null,
                null,
                false,
                null,
                selection);
        Map<String, Object> malformed = new LinkedHashMap<>(report.toMap());
        malformed.put(RunnerRouteReportFields.Report.SELECTION_SOURCE, "magic");

        List<String> problems = RunnerRouteReportContract.validateReport(malformed);

        assertEquals(1, problems.size());
        assertEquals("runner route report selection_source has unknown value: magic", problems.get(0));
    }

    @Test
    void routeReportCapturesRuntimeRedirectCacheHit() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);

        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("safetensor", "safetensors")
                .withRuntimeRedirect(
                        "safetensor",
                        "safetensors",
                        "gguf",
                        "gguf",
                        "Direct safetensor route policy selected alternate gguf runtime artifact.",
                        true,
                        "community_text_gguf");

        assertEquals(true,
                report.toMap().get(RunnerRouteReportFields.Report.RUNTIME_REDIRECTED));
        assertEquals(true,
                report.toMap().get(RunnerRouteReportFields.Report.RUNTIME_REDIRECT_CACHE_HIT));
        assertEquals("community_text_gguf",
                report.toMap().get(RunnerRouteReportFields.Report.RUNTIME_REDIRECT_CACHE_KIND));
        assertEquals(true, RunnerRouteReportContract.validateReport(report.toMap()).isEmpty());
        assertEquals(true, report.toMetadata().get(
                RunnerRouteReportFields.metadataKey(RunnerRouteReportFields.Report.RUNTIME_REDIRECT_CACHE_HIT)));
        assertEquals("community_text_gguf", report.toMetadata().get(
                RunnerRouteReportFields.metadataKey(RunnerRouteReportFields.Report.RUNTIME_REDIRECT_CACHE_KIND)));
        assertEquals(RunnerRouteReportFields.RouteProfileSource.ARTIFACT_CACHE,
                report.toMap().get(RunnerRouteReportFields.Report.ROUTE_PROFILE_SOURCE));
    }

    @Test
    void routeReportPayloadExposesStableDryRunJsonShape() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("safetensor", "safetensors")
                .withRuntimeRedirect(
                        "safetensor",
                        "safetensors",
                        "gguf",
                        "gguf",
                        "Detected local GGUF runtime artifact");

        Map<String, Object> payload = RouteReportPayloads.routeReportPayload(
                report,
                "Qwen/Qwen3-4B-Instruct",
                "/models/qwen3-q4.gguf",
                "/models/qwen3-q4.gguf",
                "gguf",
                "gguf");
        Map<?, ?> route = (Map<?, ?>) payload.get(RunnerRouteReportFields.METADATA_ROOT);
        Map<?, ?> validation = (Map<?, ?>) payload.get(RunnerRouteReportFields.VALIDATION_METADATA_ROOT);
        Map<?, ?> diagnosticsValidation =
                (Map<?, ?>) payload.get(RoutePreflightDiagnosticFields.VALIDATION_ROOT);
        Map<?, ?> payloadValidation = (Map<?, ?>) payload.get(RouteReportPayloadFields.VALIDATION_ROOT);

        assertEquals("aqli_route_report", payload.get("type"));
        assertEquals(true, payload.get("dry_run"));
        assertEquals(false, payload.get("mutation_allowed"));
        assertEquals("local-only", payload.get("resolution_mode"));
        assertEquals(true, payload.get("resolved_local"));
        assertEquals("resolved", payload.get("resolution_status"));
        assertEquals(false, payload.get("require_local"));
        assertEquals(true, payload.get("preflight_passed"));
        assertEquals("passed", payload.get("preflight_status"));
        assertEquals(0, payload.get("exit_code"));
        assertEquals(0, payload.get("preflight_problem_count"));
        assertEquals(List.of(), payload.get(RouteReportPayloadFields.Payload.PREFLIGHT_PROBLEM_CODES));
        assertEquals(List.of(), payload.get(
                RouteReportPayloadFields.Payload.PREFLIGHT_MISSING_RUNTIME_CAPABILITIES));
        assertEquals(List.of(), payload.get("preflight_problems"));
        assertEquals(0, payload.get(RouteReportPayloadFields.Payload.NEXT_ACTION_COUNT));
        assertEquals(List.of(), payload.get(RouteReportPayloadFields.Payload.NEXT_ACTION_KINDS));
        assertEquals(List.of(), payload.get("next_actions"));
        assertEquals("Qwen/Qwen3-4B-Instruct", payload.get("requested_model"));
        assertEquals("gguf", payload.get("provider"));
        assertEquals("gguf", payload.get("format"));
        assertEquals(RunnerRoutePolicy.AUTO,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_RUNNER));
        assertEquals(RunnerRouteReportFields.SelectionSource.AUTO,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_SELECTION_SOURCE));
        assertEquals(true, payload.get(RouteReportPayloadFields.Payload.ROUTE_REDIRECTED));
        assertEquals("Detected local GGUF runtime artifact",
                payload.get(RouteReportPayloadFields.Payload.ROUTE_REDIRECT_REASON));
        assertEquals(RunnerRouteReportFields.RouteProfileStatus.REDIRECTED,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_PROFILE_STATUS));
        assertEquals(RunnerRouteReportFields.RouteProfileSource.RUNTIME_REDIRECT,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_PROFILE_SOURCE));
        assertEquals("gguf", payload.get(RouteReportPayloadFields.Payload.ROUTE_PROFILE_PROVIDER));
        assertEquals("gguf", payload.get(RouteReportPayloadFields.Payload.ROUTE_PROFILE_FORMAT));
        assertEquals("Detected local GGUF runtime artifact",
                payload.get(RouteReportPayloadFields.Payload.ROUTE_PROFILE_REASON));
        assertTrue(String.valueOf(payload.get(RouteReportPayloadFields.Payload.ROUTE_PROFILE_ADVICE))
                .contains("production-oriented runtime artifact"));
        assertEquals(true, route.get(RunnerRouteReportFields.Report.RUNTIME_REDIRECTED));
        assertEquals("gguf", route.get(RunnerRouteReportFields.Report.RUNTIME_REDIRECT_TO_PROVIDER));
        assertEquals(false, route.get(RunnerRouteReportFields.Report.RUNTIME_REDIRECT_CACHE_HIT));
        assertEquals(false, payload.get(RouteReportPayloadFields.Payload.ROUTE_ARTIFACT_CACHE_HIT));
        assertEquals(RouteReportPayloadFields.RouteArtifactCacheState.MISS,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_ARTIFACT_CACHE_STATE));
        assertFalse(payload.containsKey(RouteReportPayloadFields.Payload.ROUTE_ARTIFACT_CACHE_KIND));
        assertEquals(true, validation.get(RunnerRouteReportFields.Validation.PASSED));
        assertEquals(0, validation.get(RunnerRouteReportFields.Validation.PROBLEM_COUNT));
        assertEquals(RoutePreflightDiagnosticFields.schemaFingerprint(),
                diagnosticsValidation.get(RoutePreflightDiagnosticFields.Validation.SCHEMA_FINGERPRINT));
        assertEquals(true, diagnosticsValidation.get(RoutePreflightDiagnosticFields.Validation.PASSED));
        assertEquals(0, diagnosticsValidation.get(RoutePreflightDiagnosticFields.Validation.PROBLEM_COUNT));
        assertEquals(true, RouteReportPayloadContract.validatePayload(payload).isEmpty());
        assertEquals(true, RouteReportPayloadContract.validateValidationReport(payloadValidation).isEmpty());
        assertEquals(RouteReportPayloadFields.schemaFingerprint(),
                payloadValidation.get(RouteReportPayloadFields.Validation.SCHEMA_FINGERPRINT));
        assertEquals(true, payloadValidation.get(RouteReportPayloadFields.Validation.PASSED));
        assertEquals(0, payloadValidation.get(RouteReportPayloadFields.Validation.PROBLEM_COUNT));
    }

    @Test
    void routeReportPayloadSummarizesRuntimeArtifactCacheHit() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("safetensor", "safetensors")
                .withRuntimeRedirect(
                        "safetensor",
                        "safetensors",
                        "gguf",
                        "gguf",
                        "Direct safetensor route policy selected alternate gguf runtime artifact.",
                        true,
                        "gemma4_text_gguf");

        Map<String, Object> payload = RouteReportPayloads.routeReportPayload(
                report,
                "google/gemma-4-E2B-it",
                "/models/gemma-4-E2B-it-Q4_K_M.gguf",
                "/models/gemma-4-E2B-it-Q4_K_M.gguf",
                "gguf",
                "gguf");

        assertEquals(true, payload.get(RouteReportPayloadFields.Payload.ROUTE_ARTIFACT_CACHE_HIT));
        assertEquals("gemma4_text_gguf",
                payload.get(RouteReportPayloadFields.Payload.ROUTE_ARTIFACT_CACHE_KIND));
        assertEquals(RouteReportPayloadFields.RouteArtifactCacheState.HIT,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_ARTIFACT_CACHE_STATE));
        assertEquals(RunnerRouteReportFields.RouteProfileStatus.REDIRECTED,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_PROFILE_STATUS));
        assertEquals(RunnerRouteReportFields.RouteProfileSource.ARTIFACT_CACHE,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_PROFILE_SOURCE));
        assertEquals(true, RouteReportPayloadContract.validatePayload(payload).isEmpty());
    }

    @Test
    void routeReportPayloadSummarizesRunnerSelectionSource() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, "gguf", "gguf", true, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        "gguf",
                        true,
                        "gguf",
                        selection)
                .withEffectiveRoute("gguf", "gguf");

        Map<String, Object> payload = RouteReportPayloads.routeReportPayload(
                report,
                "local-model",
                "/models/local.gguf",
                "/models/local.gguf",
                "gguf",
                "gguf");

        assertEquals(RunnerRouteReportFields.SelectionSource.PROVIDER_CLI,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_SELECTION_SOURCE));
        assertEquals(RunnerRoutePolicy.AUTO,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_RUNNER));
        assertEquals(true, RouteReportPayloadContract.validatePayload(payload).isEmpty());
    }

    @Test
    void routeReportPayloadMarksLocalOnlyMissWithoutLocalPath() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute(null, null);

        Map<String, Object> payload = RouteReportPayloads.routeReportPayload(
                report,
                "google/gemma-4-12B-it",
                "google/gemma-4-12B-it",
                null,
                null,
                null,
                false,
                true,
                false);

        assertEquals(false, payload.get("mutation_allowed"));
        assertEquals("local-only", payload.get("resolution_mode"));
        assertEquals(false, payload.get("resolved_local"));
        assertEquals("not_local", payload.get("resolution_status"));
        assertEquals(false, payload.get("require_local"));
        assertEquals(true, payload.get("preflight_passed"));
        assertEquals("passed", payload.get("preflight_status"));
        assertEquals(0, payload.get("exit_code"));
        assertEquals(0, payload.get("preflight_problem_count"));
        assertEquals(List.of(), payload.get("preflight_problems"));
        assertEquals(false, payload.get(RouteReportPayloadFields.Payload.ROUTE_ARTIFACT_CACHE_HIT));
        assertEquals(RunnerRoutePolicy.AUTO,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_RUNNER));
        assertEquals(RunnerRouteReportFields.SelectionSource.AUTO,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_SELECTION_SOURCE));
        assertEquals(false, payload.get(RouteReportPayloadFields.Payload.ROUTE_REDIRECTED));
        assertFalse(payload.containsKey(RouteReportPayloadFields.Payload.ROUTE_REDIRECT_REASON));
        assertEquals(RunnerRouteReportFields.RouteProfileStatus.UNAVAILABLE,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_PROFILE_STATUS));
        assertEquals(RunnerRouteReportFields.RouteProfileSource.NONE,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_PROFILE_SOURCE));
        assertFalse(payload.containsKey(RouteReportPayloadFields.Payload.ROUTE_PROFILE_PROVIDER));
        assertEquals(RouteReportPayloadFields.RouteArtifactCacheState.NOT_APPLICABLE,
                payload.get(RouteReportPayloadFields.Payload.ROUTE_ARTIFACT_CACHE_STATE));
        assertEquals(2, payload.get(RouteReportPayloadFields.Payload.NEXT_ACTION_COUNT));
        assertEquals(List.of(
                        RoutePreflightDiagnosticFields.ActionKind.PULL_MODEL,
                        RoutePreflightDiagnosticFields.ActionKind.ALLOW_PULL_RESOLUTION),
                payload.get(RouteReportPayloadFields.Payload.NEXT_ACTION_KINDS));
        List<?> actions = (List<?>) payload.get("next_actions");
        assertEquals(2, actions.size());
        Map<?, ?> pullAction = (Map<?, ?>) actions.get(0);
        Map<?, ?> allowPullAction = (Map<?, ?>) actions.get(1);
        assertEquals("pull_model", pullAction.get("kind"));
        assertEquals(List.of("aqli", "pull", "google/gemma-4-12B-it"), pullAction.get("argv"));
        assertEquals("allow_pull_resolution", allowPullAction.get("kind"));
        assertEquals(
                List.of("aqli", "run", "--model", "google/gemma-4-12B-it",
                        "--route-report-json", "--route-report-allow-pull"),
                allowPullAction.get("argv"));
        assertEquals(false, payload.containsKey("local_path"));
    }

    @Test
    void routeReportPayloadCanFailStrictLocalPreflight() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute(null, null);

        Map<String, Object> payload = RouteReportPayloads.routeReportPayload(
                report,
                "google/gemma-4-12B-it",
                "google/gemma-4-12B-it",
                null,
                null,
                null,
                false,
                true,
                true);

        assertEquals(false, payload.get("mutation_allowed"));
        assertEquals("local-only", payload.get("resolution_mode"));
        assertEquals(false, payload.get("resolved_local"));
        assertEquals("not_local", payload.get("resolution_status"));
        assertEquals(true, payload.get("require_local"));
        assertEquals(false, payload.get("preflight_passed"));
        assertEquals("failed", payload.get("preflight_status"));
        assertEquals(2, payload.get("exit_code"));
        assertEquals(1, payload.get("preflight_problem_count"));
        assertEquals(List.of(RoutePreflightDiagnosticFields.ProblemCode.MODEL_NOT_LOCAL),
                payload.get(RouteReportPayloadFields.Payload.PREFLIGHT_PROBLEM_CODES));
        List<?> problems = (List<?>) payload.get("preflight_problems");
        assertEquals("model_not_local", ((Map<?, ?>) problems.get(0)).get("code"));
        assertEquals(2, payload.get(RouteReportPayloadFields.Payload.NEXT_ACTION_COUNT));
        assertEquals(List.of(
                        RoutePreflightDiagnosticFields.ActionKind.PULL_MODEL,
                        RoutePreflightDiagnosticFields.ActionKind.ALLOW_PULL_RESOLUTION),
                payload.get(RouteReportPayloadFields.Payload.NEXT_ACTION_KINDS));
        List<?> actions = (List<?>) payload.get("next_actions");
        assertEquals(2, actions.size());
        assertEquals("pull_model", ((Map<?, ?>) actions.get(0)).get("kind"));
        assertEquals("allow_pull_resolution", ((Map<?, ?>) actions.get(1)).get("kind"));
        assertEquals(false, payload.containsKey("local_path"));
    }

    @Test
    void routeReportPayloadFailsStrictPreflightWhenLocalRouteIsNotRunnable() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute(null, null);

        Map<String, Object> payload = RouteReportPayloads.routeReportPayload(
                report,
                "/models/unknown.bin",
                "/models/unknown.bin",
                "/models/unknown.bin",
                null,
                null,
                false,
                false,
                true);

        assertEquals(true, payload.get("resolved_local"));
        assertEquals("resolved", payload.get("resolution_status"));
        assertEquals(false, payload.get("preflight_passed"));
        assertEquals("failed", payload.get("preflight_status"));
        assertEquals(2, payload.get("exit_code"));
        assertEquals(2, payload.get("preflight_problem_count"));
        assertEquals(List.of(
                        RoutePreflightDiagnosticFields.ProblemCode.PROVIDER_NOT_RESOLVED,
                        RoutePreflightDiagnosticFields.ProblemCode.FORMAT_NOT_RESOLVED),
                payload.get(RouteReportPayloadFields.Payload.PREFLIGHT_PROBLEM_CODES));
        List<?> problems = (List<?>) payload.get("preflight_problems");
        assertEquals("provider_not_resolved", ((Map<?, ?>) problems.get(0)).get("code"));
        assertEquals("format_not_resolved", ((Map<?, ?>) problems.get(1)).get("code"));
        assertEquals(1, payload.get(RouteReportPayloadFields.Payload.NEXT_ACTION_COUNT));
        assertEquals(List.of(RoutePreflightDiagnosticFields.ActionKind.INSPECT_MODULES),
                payload.get(RouteReportPayloadFields.Payload.NEXT_ACTION_KINDS));
        List<?> actions = (List<?>) payload.get("next_actions");
        assertEquals(1, actions.size());
        assertEquals("inspect_modules", ((Map<?, ?>) actions.get(0)).get("kind"));
    }

    @Test
    void routePreflightReportDeduplicatesAdditionalActionsWithoutReordering() {
        RoutePreflightReport preflight = RoutePreflightReport.evaluate(
                "/models/local.safetensors",
                "/models/local.safetensors",
                "/models/local.safetensors",
                null,
                null,
                false,
                true);
        RoutePreflightAction duplicateInspectModules = new RoutePreflightAction(
                RoutePreflightDiagnosticFields.ActionKind.INSPECT_MODULES,
                "route_not_runnable",
                "Inspect attached provider and runner plugins before serving this local artifact.",
                List.of("aqli", "modules", "--json"));
        RoutePreflightAction directRouteInspectModules = new RoutePreflightAction(
                RoutePreflightDiagnosticFields.ActionKind.INSPECT_MODULES,
                RoutePreflightDiagnosticFields.ProblemCode.DIRECT_ROUTE_VALIDATION_FAILED,
                "Inspect attached model-family and runtime plugins for Gemma 4 packed-expert routing support.",
                List.of("aqli", "modules", "--json"));

        RoutePreflightReport merged = preflight.withAdditionalActions(List.of(
                duplicateInspectModules,
                duplicateInspectModules,
                directRouteInspectModules));

        List<Map<String, Object>> actions = merged.nextActionMaps();
        assertEquals(2, actions.size());
        assertEquals("route_not_runnable", actions.get(0).get("reason"));
        assertEquals(RoutePreflightDiagnosticFields.ProblemCode.DIRECT_ROUTE_VALIDATION_FAILED,
                actions.get(1).get("reason"));
    }

    @Test
    void routeReportPayloadContractAcceptsStructuredPreflightDiagnosticDetails() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute(null, null);
        Map<String, Object> payload = new LinkedHashMap<>(RouteReportPayloads.routeReportPayload(
                report,
                "/models/unknown.bin",
                "/models/unknown.bin",
                "/models/unknown.bin",
                null,
                null,
                false,
                false,
                true));

        Map<String, Object> problem = mutableMapAt(payload, "preflight_problems", 0);
        problem.put(RoutePreflightDiagnosticFields.Problem.DETAILS, Map.of(
                RoutePreflightDiagnosticFields.ProblemDetail.MODEL_FAMILY, "gemma4",
                RoutePreflightDiagnosticFields.ProblemDetail.RUNTIME_ROUTE, "safetensor_direct",
                RoutePreflightDiagnosticFields.ProblemDetail.CHECKPOINT_PROFILE, "gemma4_text",
                RoutePreflightDiagnosticFields.ProblemDetail.MISSING_RUNTIME_CAPABILITY,
                RoutePreflightDiagnosticFields.MissingRuntimeCapability.GEMMA4_TEXT_SAFETENSOR_HEADERS));
        payload.put("preflight_problems", List.of(problem));
        payload.put(RouteReportPayloadFields.Payload.PREFLIGHT_PROBLEM_COUNT, 1);
        payload.put(
                RouteReportPayloadFields.Payload.PREFLIGHT_PROBLEM_CODES,
                List.of(RoutePreflightDiagnosticFields.ProblemCode.PROVIDER_NOT_RESOLVED));
        payload.put(
                RouteReportPayloadFields.Payload.PREFLIGHT_MISSING_RUNTIME_CAPABILITIES,
                List.of(RoutePreflightDiagnosticFields.MissingRuntimeCapability.GEMMA4_TEXT_SAFETENSOR_HEADERS));

        Map<String, Object> action = mutableMapAt(payload, "next_actions", 0);
        action.put(RoutePreflightDiagnosticFields.Action.DETAILS, Map.of(
                RoutePreflightDiagnosticFields.ProblemDetail.MODEL_FAMILY, "gemma4",
                RoutePreflightDiagnosticFields.ProblemDetail.RUNTIME_ROUTE, "safetensor_direct",
                RoutePreflightDiagnosticFields.ProblemDetail.CHECKPOINT_PROFILE, "gemma4_text",
                RoutePreflightDiagnosticFields.ProblemDetail.MISSING_RUNTIME_CAPABILITY,
                RoutePreflightDiagnosticFields.MissingRuntimeCapability.GEMMA4_TEXT_SAFETENSOR_HEADERS));
        payload.put("next_actions", List.of(action));
        payload.put(
                RouteReportPayloadFields.Payload.NEXT_ACTION_KINDS,
                List.of(RoutePreflightDiagnosticFields.ActionKind.INSPECT_MODULES));

        assertTrue(RouteReportPayloadContract.validatePayload(payload).isEmpty());
    }

    @Test
    void routeReportPayloadSummarizesMissingRuntimeCapabilitiesFromPreflightDetails() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("safetensor", "safetensors");
        Map<String, Object> details = Map.of(
                RoutePreflightDiagnosticFields.ProblemDetail.MODEL_FAMILY, "gemma4",
                RoutePreflightDiagnosticFields.ProblemDetail.RUNTIME_ROUTE, "safetensor_direct",
                RoutePreflightDiagnosticFields.ProblemDetail.CHECKPOINT_PROFILE, "gemma4_text",
                RoutePreflightDiagnosticFields.ProblemDetail.MISSING_RUNTIME_CAPABILITY,
                RoutePreflightDiagnosticFields.MissingRuntimeCapability.GEMMA4_TEXT_SAFETENSOR_HEADERS);
        RoutePreflightReport preflight = new RoutePreflightReport(
                true,
                List.of(
                        new RoutePreflightProblem(
                                RoutePreflightDiagnosticFields.ProblemCode.GEMMA4_TEXT_HEADER_MISMATCH,
                                "error",
                                "Gemma 4 unified safetensor text preflight failed.",
                                details),
                        new RoutePreflightProblem(
                                RoutePreflightDiagnosticFields.ProblemCode.GEMMA4_TEXT_HEADER_MISMATCH,
                                "error",
                                "Gemma 4 unified safetensor text preflight still lacks coverage.",
                                details)),
                List.of());

        Map<String, Object> payload = RouteReportPayloads.routeReportPayload(
                report,
                "google/gemma-4-12B-it",
                "/models/gemma-4-12B-it",
                "/models/gemma-4-12B-it",
                "safetensor",
                "safetensors",
                false,
                false,
                preflight);

        assertEquals(
                List.of(RoutePreflightDiagnosticFields.ProblemCode.GEMMA4_TEXT_HEADER_MISMATCH),
                payload.get(RouteReportPayloadFields.Payload.PREFLIGHT_PROBLEM_CODES));
        assertEquals(
                List.of(RoutePreflightDiagnosticFields.MissingRuntimeCapability.GEMMA4_TEXT_SAFETENSOR_HEADERS),
                payload.get(RouteReportPayloadFields.Payload.PREFLIGHT_MISSING_RUNTIME_CAPABILITIES));
        assertEquals(false, payload.get(RouteReportPayloadFields.Payload.PREFLIGHT_PASSED));
        assertTrue(RouteReportPayloadContract.validatePayload(payload).isEmpty());
    }

    @Test
    void routeReportPayloadContractRejectsUnknownFields() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("gguf", "gguf");
        Map<String, Object> payload = new LinkedHashMap<>(RouteReportPayloads.routeReportPayload(
                report,
                "local-model",
                "/models/local.gguf",
                "/models/local.gguf",
                "gguf",
                "gguf"));

        payload.put("mystery", true);

        List<String> problems = RouteReportPayloadContract.validatePayload(payload);
        assertEquals(1, problems.size());
        assertEquals("route report payload contains unknown field: mystery", problems.get(0));
    }

    @Test
    void routeReportPayloadContractRejectsMalformedRouteRedirectSummary() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("safetensor", "safetensors")
                .withRuntimeRedirect(
                        "safetensor",
                        "safetensors",
                        "gguf",
                        "gguf",
                        "Detected local GGUF runtime artifact");
        Map<String, Object> payload = new LinkedHashMap<>(RouteReportPayloads.routeReportPayload(
                report,
                "local-model",
                "/models/local.gguf",
                "/models/local.gguf",
                "gguf",
                "gguf"));

        payload.put(RouteReportPayloadFields.Payload.ROUTE_REDIRECTED, false);
        payload.put(RouteReportPayloadFields.Payload.ROUTE_REDIRECT_REASON, "different");

        List<String> problems = RouteReportPayloadContract.validatePayload(payload);
        assertEquals(2, problems.size());
        assertTrue(problems.contains(
                "payload.route_redirected must match runner_route.runtime_redirected"));
        assertTrue(problems.contains(
                "payload.route_redirect_reason must match runner_route.runtime_redirect_reason"));
    }

    @Test
    void routeReportPayloadContractRejectsMalformedRouteProfileSummary() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("gguf", "gguf");
        Map<String, Object> payload = new LinkedHashMap<>(RouteReportPayloads.routeReportPayload(
                report,
                "local-model",
                "/models/local.gguf",
                "/models/local.gguf",
                "gguf",
                "gguf"));

        payload.put(RouteReportPayloadFields.Payload.ROUTE_PROFILE_STATUS, "warm");
        payload.put(RouteReportPayloadFields.Payload.ROUTE_PROFILE_SOURCE, "magic");
        payload.put(RouteReportPayloadFields.Payload.ROUTE_PROFILE_PROVIDER, "litert");

        List<String> problems = RouteReportPayloadContract.validatePayload(payload);
        assertEquals(5, problems.size());
        assertTrue(problems.contains("payload.route_profile_status has unknown value: warm"));
        assertTrue(problems.contains("payload.route_profile_source has unknown value: magic"));
        assertTrue(problems.contains(
                "payload.route_profile_status must match runner_route.route_profile_status"));
        assertTrue(problems.contains(
                "payload.route_profile_source must match runner_route.route_profile_source"));
        assertTrue(problems.contains(
                "payload.route_profile_provider must match runner_route.route_profile_provider"));
    }

    @Test
    void routeReportPayloadContractRejectsMalformedRouteRunner() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select("gguf", null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        "gguf",
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("gguf", "gguf");
        Map<String, Object> payload = new LinkedHashMap<>(RouteReportPayloads.routeReportPayload(
                report,
                "local-model",
                "/models/local.gguf",
                "/models/local.gguf",
                "gguf",
                "gguf"));

        payload.put(RouteReportPayloadFields.Payload.ROUTE_RUNNER, "magic");

        List<String> problems = RouteReportPayloadContract.validatePayload(payload);
        assertEquals(2, problems.size());
        assertTrue(problems.contains("payload.route_runner has unknown value: magic"));
        assertTrue(problems.contains("payload.route_runner must match runner_route.normalized_runner"));
    }

    @Test
    void routeReportPayloadContractRejectsMalformedRouteSelectionSource() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("gguf", "gguf");
        Map<String, Object> payload = new LinkedHashMap<>(RouteReportPayloads.routeReportPayload(
                report,
                "local-model",
                "/models/local.gguf",
                "/models/local.gguf",
                "gguf",
                "gguf"));

        payload.put(RouteReportPayloadFields.Payload.ROUTE_SELECTION_SOURCE, "magic");

        List<String> problems = RouteReportPayloadContract.validatePayload(payload);
        assertEquals(2, problems.size());
        assertTrue(problems.contains("payload.route_selection_source has unknown value: magic"));
        assertTrue(problems.contains(
                "payload.route_selection_source must match runner_route.selection_source"));
    }

    @Test
    void routeReportPayloadContractRejectsMalformedRouteArtifactCacheSummary() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("gguf", "gguf");
        Map<String, Object> payload = new LinkedHashMap<>(RouteReportPayloads.routeReportPayload(
                report,
                "local-model",
                "/models/local.gguf",
                "/models/local.gguf",
                "gguf",
                "gguf"));

        payload.put(RouteReportPayloadFields.Payload.ROUTE_ARTIFACT_CACHE_HIT, "yes");
        payload.put(RouteReportPayloadFields.Payload.ROUTE_ARTIFACT_CACHE_STATE, "warm");
        payload.put(RouteReportPayloadFields.Payload.ROUTE_ARTIFACT_CACHE_KIND, 42);

        List<String> problems = RouteReportPayloadContract.validatePayload(payload);
        assertEquals(3, problems.size());
        assertTrue(problems.contains("payload.route_artifact_cache_hit must be a boolean"));
        assertTrue(problems.contains("payload.route_artifact_cache_state has unknown value: warm"));
        assertTrue(problems.contains("payload.route_artifact_cache_kind must be a string"));
    }

    @Test
    void routeReportPayloadContractRejectsMalformedPreflightDiagnostics() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute(null, null);
        Map<String, Object> payload = new LinkedHashMap<>(RouteReportPayloads.routeReportPayload(
                report,
                "/models/unknown.bin",
                "/models/unknown.bin",
                "/models/unknown.bin",
                null,
                null,
                false,
                false,
                true));

        Map<String, Object> problem = mutableMapAt(payload, "preflight_problems", 0);
        problem.put(RoutePreflightDiagnosticFields.Problem.CODE, "new_problem");
        problem.put(RoutePreflightDiagnosticFields.Problem.DETAILS, Map.of(
                RoutePreflightDiagnosticFields.ProblemDetail.MISSING_RUNTIME_CAPABILITY, "warp_drive",
                "surprise", true));
        problem.put("extra", true);
        payload.put("preflight_problems", List.of(problem));
        payload.put(
                RouteReportPayloadFields.Payload.PREFLIGHT_PROBLEM_CODES,
                List.of("warp_drive"));
        payload.put(
                RouteReportPayloadFields.Payload.PREFLIGHT_MISSING_RUNTIME_CAPABILITIES,
                List.of(RoutePreflightDiagnosticFields.MissingRuntimeCapability.GEMMA4_PACKED_MOE_ROUTER));

        Map<String, Object> action = mutableMapAt(payload, "next_actions", 0);
        action.put(RoutePreflightDiagnosticFields.Action.KIND, "new_action");
        action.put(RoutePreflightDiagnosticFields.Action.ARGV, "aqli modules --json");
        action.put(RoutePreflightDiagnosticFields.Action.DETAILS, Map.of(
                RoutePreflightDiagnosticFields.ProblemDetail.MISSING_RUNTIME_CAPABILITY, "warp_drive",
                "surprise", true));
        payload.put("next_actions", List.of(action));
        payload.put(
                RouteReportPayloadFields.Payload.NEXT_ACTION_COUNT,
                2);
        payload.put(
                RouteReportPayloadFields.Payload.NEXT_ACTION_KINDS,
                List.of("warp_drive"));

        List<String> problems = RouteReportPayloadContract.validatePayload(payload);
        assertEquals(15, problems.size());
        assertTrue(problems.contains(
                "payload.preflight_problem_count must match preflight_problems size: expected 1 but was 2"));
        assertTrue(problems.contains(
                "payload.preflight_problem_codes[0] has unknown value: warp_drive"));
        assertTrue(problems.contains(
                "payload.preflight_problem_codes must match preflight_problems.code: expected [new_problem] "
                        + "but was [warp_drive]"));
        assertTrue(problems.contains(
                "payload.next_action_kinds[0] has unknown value: warp_drive"));
        assertTrue(problems.contains(
                "payload.next_action_count must match next_actions size: expected 1 but was 2"));
        assertTrue(problems.contains(
                "payload.next_action_kinds must match next_actions.kind: expected [new_action] "
                        + "but was [warp_drive]"));
        assertTrue(problems.contains(
                "payload.preflight_problems[0] contains unknown field: extra"));
        assertTrue(problems.contains(
                "payload.preflight_problems[0].details contains unknown field: surprise"));
        assertTrue(problems.contains(
                "payload.preflight_problems[0].details.missingRuntimeCapability has unknown value: warp_drive"));
        assertTrue(problems.contains(
                "payload.preflight_problems[0].code has unknown value: new_problem"));
        assertTrue(problems.contains(
                "payload.next_actions[0].kind has unknown value: new_action"));
        assertTrue(problems.contains(
                "payload.next_actions[0].argv must be a list"));
        assertTrue(problems.contains(
                "payload.next_actions[0].details contains unknown field: surprise"));
        assertTrue(problems.contains(
                "payload.next_actions[0].details.missingRuntimeCapability has unknown value: warp_drive"));
        assertTrue(problems.contains(
                "payload.preflight_missing_runtime_capabilities must match "
                        + "preflight_problems.details.missingRuntimeCapability: expected [warp_drive] "
                        + "but was [gemma4_packed_moe_router]"));
    }

    @Test
    void routeReportPayloadContractRejectsMalformedPreflightDiagnosticValidation() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("gguf", "gguf");
        Map<String, Object> payload = new LinkedHashMap<>(RouteReportPayloads.routeReportPayload(
                report,
                "local-model",
                "/models/local.gguf",
                "/models/local.gguf",
                "gguf",
                "gguf"));

        Map<String, Object> diagnosticsValidation = new LinkedHashMap<>();
        diagnosticsValidation.put(RoutePreflightDiagnosticFields.Validation.CONTRACT_ID, "wrong");
        diagnosticsValidation.put(RoutePreflightDiagnosticFields.Validation.SCHEMA_VERSION,
                RoutePreflightDiagnosticFields.SCHEMA_VERSION);
        diagnosticsValidation.put(RoutePreflightDiagnosticFields.Validation.SCHEMA_FINGERPRINT,
                RoutePreflightDiagnosticFields.schemaFingerprint());
        diagnosticsValidation.put(RoutePreflightDiagnosticFields.Validation.PASSED, "true");
        diagnosticsValidation.put(RoutePreflightDiagnosticFields.Validation.FAILED, false);
        diagnosticsValidation.put(RoutePreflightDiagnosticFields.Validation.PROBLEM_COUNT, 0);
        diagnosticsValidation.put(RoutePreflightDiagnosticFields.Validation.PROBLEMS, List.of());
        diagnosticsValidation.put("extra", true);
        payload.put(RoutePreflightDiagnosticFields.VALIDATION_ROOT, diagnosticsValidation);

        List<String> problems = RouteReportPayloadContract.validatePayload(payload);
        assertEquals(3, problems.size());
        assertTrue(problems.contains(
                "payload.route_preflight_diagnostics_validation contains unknown field: extra"));
        assertTrue(problems.contains(
                "payload.route_preflight_diagnostics_validation.contractId expected "
                        + RoutePreflightDiagnosticFields.CONTRACT_ID + " but was wrong"));
        assertTrue(problems.contains(
                "payload.route_preflight_diagnostics_validation.passed must be a boolean"));
    }

    @Test
    void routeReportPayloadContractRejectsMalformedPayloadValidation() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("gguf", "gguf");
        Map<String, Object> payload = new LinkedHashMap<>(RouteReportPayloads.routeReportPayload(
                report,
                "local-model",
                "/models/local.gguf",
                "/models/local.gguf",
                "gguf",
                "gguf"));

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put(RouteReportPayloadFields.Validation.CONTRACT_ID, "wrong");
        validation.put(RouteReportPayloadFields.Validation.SCHEMA_VERSION,
                RouteReportPayloadFields.SCHEMA_VERSION);
        validation.put(RouteReportPayloadFields.Validation.SCHEMA_FINGERPRINT,
                RouteReportPayloadFields.schemaFingerprint());
        validation.put(RouteReportPayloadFields.Validation.PASSED, "true");
        validation.put(RouteReportPayloadFields.Validation.FAILED, false);
        validation.put(RouteReportPayloadFields.Validation.PROBLEM_COUNT, 0);
        validation.put(RouteReportPayloadFields.Validation.PROBLEMS, List.of());
        validation.put("extra", true);
        payload.put(RouteReportPayloadFields.VALIDATION_ROOT, validation);

        List<String> problems = RouteReportPayloadContract.validatePayload(payload);
        assertEquals(3, problems.size());
        assertTrue(problems.contains(
                "payload.route_report_validation contains unknown field: extra"));
        assertTrue(problems.contains(
                "payload.route_report_validation.contractId expected "
                        + RouteReportPayloadFields.CONTRACT_ID + " but was wrong"));
        assertTrue(problems.contains(
                "payload.route_report_validation.passed must be a boolean"));
    }

    @Test
    void routeReportPayloadContractRejectsStaleValidationSummaries() {
        RunnerRoutePolicy.Selection selection =
                RunnerRoutePolicy.select(null, null, null, false, false, false);
        RunnerRouteReport report = RunnerRouteReport.from(
                        null,
                        null,
                        false,
                        null,
                        selection)
                .withEffectiveRoute("gguf", "gguf");
        Map<String, Object> payload = new LinkedHashMap<>(RouteReportPayloads.routeReportPayload(
                report,
                "local-model",
                "/models/local.gguf",
                "/models/local.gguf",
                "gguf",
                "gguf"));

        Map<String, Object> diagnosticsValidation = mutableMap(payload.get(
                RoutePreflightDiagnosticFields.VALIDATION_ROOT));
        diagnosticsValidation.put(RoutePreflightDiagnosticFields.Validation.PASSED, true);
        diagnosticsValidation.put(RoutePreflightDiagnosticFields.Validation.FAILED, false);
        diagnosticsValidation.put(RoutePreflightDiagnosticFields.Validation.PROBLEM_COUNT, 0);
        diagnosticsValidation.put(RoutePreflightDiagnosticFields.Validation.PROBLEMS,
                List.of("diagnostics fixture problem"));
        payload.put(RoutePreflightDiagnosticFields.VALIDATION_ROOT, diagnosticsValidation);

        Map<String, Object> payloadValidation = mutableMap(payload.get(RouteReportPayloadFields.VALIDATION_ROOT));
        payloadValidation.put(RouteReportPayloadFields.Validation.PASSED, true);
        payloadValidation.put(RouteReportPayloadFields.Validation.FAILED, false);
        payloadValidation.put(RouteReportPayloadFields.Validation.PROBLEM_COUNT, 0);
        payloadValidation.put(RouteReportPayloadFields.Validation.PROBLEMS,
                List.of("payload fixture problem"));
        payload.put(RouteReportPayloadFields.VALIDATION_ROOT, payloadValidation);

        List<String> problems = RouteReportPayloadContract.validatePayload(payload);
        assertEquals(6, problems.size());
        assertTrue(problems.contains(
                "payload.route_preflight_diagnostics_validation.problemCount "
                        + "must match problems size: expected 1 but was 0"));
        assertTrue(problems.contains(
                "payload.route_preflight_diagnostics_validation.passed "
                        + "must match problems emptiness: expected false but was true"));
        assertTrue(problems.contains(
                "payload.route_preflight_diagnostics_validation.failed "
                        + "must match problems emptiness: expected true but was false"));
        assertTrue(problems.contains(
                "payload.route_report_validation.problemCount "
                        + "must match problems size: expected 1 but was 0"));
        assertTrue(problems.contains(
                "payload.route_report_validation.passed "
                        + "must match problems emptiness: expected false but was true"));
        assertTrue(problems.contains(
                "payload.route_report_validation.failed "
                        + "must match problems emptiness: expected true but was false"));
    }

    @Test
    void runnerRouteContractBundleRejectsMalformedReport() {
        Map<String, Object> report = new LinkedHashMap<>(RunnerRouteContractBundle.report());
        report.put(RunnerRouteContractBundle.FIELD_CONTRACT_ID, "wrong");
        report.put(RunnerRouteContractBundle.FIELD_VALIDATION_ROOTS, List.of("unexpected_root"));

        List<String> problems = RunnerRouteContractBundle.validateReport(report);
        assertEquals(2, problems.size());
        assertTrue(problems.contains(
                "contractId expected " + RunnerRouteContractBundle.CONTRACT_ID + " but was wrong"));
        assertTrue(problems.stream().anyMatch(problem -> problem.startsWith("validationRoots expected ")
                && problem.endsWith("but was [unexpected_root]")));

        PluginGates gate = RunnerRouteContractBundle.applyGate(
                new PluginGates(true, "passed", 0, List.of(), "passed", "passed", 0, 0),
                report);

        assertFalse(gate.passed());
        assertEquals("runner_route_contract_bundle_failed", gate.status());
        assertTrue(gate.violations().toString().contains("runner-route: contract bundle failed"));
    }

    private static Map<String, Object> mutableMapAt(Map<String, Object> payload, String key, int index) {
        Object value = payload.get(key);
        assertTrue(value instanceof List<?>);
        List<?> values = (List<?>) value;
        assertTrue(values.get(index) instanceof Map<?, ?>);
        Map<?, ?> source = (Map<?, ?>) values.get(index);
        return mutableMap(source);
    }

    private static Map<String, Object> mutableMap(Object value) {
        assertTrue(value instanceof Map<?, ?>);
        Map<?, ?> source = (Map<?, ?>) value;
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((entryKey, entryValue) -> copy.put(String.valueOf(entryKey), entryValue));
        return copy;
    }
}
