package tech.kayys.aqli.cli.commands.modules.report;

import tech.kayys.aqli.cli.util.UnifiedRuntimeRequirementReportFields;
import tech.kayys.aqli.spi.multimodal.UnifiedRuntimeRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedRuntimeReadinessReportTest {
    @Test
    void reportsEmptyRegistryWithoutInventingRuntimeReadiness() {
        Map<String, Object> report = UnifiedRuntimeReadinessReport.report(
                UnifiedRuntimeRegistry.of(List.of()),
                "packaged",
                List.of());

        assertEquals("packaged", report.get("registryScope"));
        assertEquals(0, report.get("runtimeCount"));
        assertEquals(0L, report.get("validCount"));
        assertEquals(0L, report.get("invalidCount"));
        assertEquals(0L, report.get("productionReadyCount"));
        assertEquals(List.of(), report.get("modelTypes"));
        assertEquals(List.of(), report.get("runtimes"));
        assertEquals(List.of(), report.get("conflicts"));
        assertEquals(List.of(), report.get("contractViolations"));
        assertTrue(report.containsKey(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENTS));
    }

    @Test
    void normalizesNullRegistryAndRequirementRows() {
        Map<String, Object> report = UnifiedRuntimeReadinessReport.report(null, "external", null);

        assertEquals("external", report.get("registryScope"));
        assertEquals(0, report.get("runtimeCount"));
        assertEquals(List.of(), report.get("runtimes"));
        assertEquals(List.of(), report.get("conflicts"));
    }
}
