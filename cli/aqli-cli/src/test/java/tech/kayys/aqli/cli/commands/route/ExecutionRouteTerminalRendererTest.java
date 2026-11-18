package tech.kayys.aqli.cli.commands.route;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import tech.kayys.aqli.sdk.route.RunnerRouteReportFields;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionRouteTerminalRendererTest {

    @Test
    void rendersProviderTransitionBackendAndRunnerDetails() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requested_provider", "libtorch");
        metadata.put("effective_provider", "safetensor");
        metadata.put("execution_backend", "java");
        metadata.put("provider_bridge_mode", "safetensors_direct");
        metadata.put("provider_bridge_reason", "native checkpoint execution");
        metadata.put(
                RunnerRouteReportFields.metadataKey(RunnerRouteReportFields.Report.NORMALIZED_RUNNER),
                "direct");
        metadata.put(
                RunnerRouteReportFields.metadataKey(RunnerRouteReportFields.Report.MODE),
                "forced");

        assertEquals(List.of(
                "Execution route: libtorch -> safetensor (backend=java) (runner=direct, mode=forced) "
                        + "[safetensors_direct: native checkpoint execution]",
                "--------------------------------------------------"),
                ExecutionRouteTerminalRenderer.render(metadata, false));
    }

    @Test
    void omitsAutomaticRunnerMode() {
        Map<String, Object> metadata = Map.of(
                "effective_provider", "cpu",
                RunnerRouteReportFields.metadataKey(RunnerRouteReportFields.Report.NORMALIZED_RUNNER), "direct",
                RunnerRouteReportFields.metadataKey(RunnerRouteReportFields.Report.MODE), "auto");

        assertEquals("Execution route: cpu (runner=direct)",
                ExecutionRouteTerminalRenderer.render(metadata, false).get(0));
    }

    @Test
    void suppressesOutputForNoBannerOrMissingMetadata() {
        assertTrue(ExecutionRouteTerminalRenderer.render(Map.of("effective_provider", "cpu"), true).isEmpty());
        assertTrue(ExecutionRouteTerminalRenderer.render(Map.of(), false).isEmpty());
        assertTrue(ExecutionRouteTerminalRenderer.render(null, false).isEmpty());
    }

    @Test
    void printWritesOnlyRenderedLines() {
        List<String> output = new ArrayList<>();

        ExecutionRouteTerminalRenderer.print(Map.of("effective_provider", "cpu"), false, output::add);

        assertEquals(List.of(
                "Execution route: cpu",
                "--------------------------------------------------"), output);
    }
}
