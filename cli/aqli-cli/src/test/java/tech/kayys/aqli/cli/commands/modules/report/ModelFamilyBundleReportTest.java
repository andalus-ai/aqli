package tech.kayys.aqli.cli.commands.modules.report;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelFamilyBundleReportTest {
    @Test
    void sortsAliasPolicyRowsForStableJsonOutput() {
        Map<String, List<String>> aliases = Map.of(
                "zeta", List.of("family-z"),
                "alpha", List.of("family-a"));

        List<Map<String, Object>> rows = ModelFamilyBundleReport.aliasFamilyMapReport(
                aliases,
                "missingFamilies");

        assertEquals(List.of("alpha", "zeta"),
                rows.stream().map(row -> row.get("alias")).toList());
        assertEquals(List.of("family-a"), rows.getFirst().get("missingFamilies"));
        assertEquals(List.of("family-z"), rows.getLast().get("missingFamilies"));
    }
}
