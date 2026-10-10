package tech.kayys.aqli.cli.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedRuntimeRequirementReportsTest {
    @Test
    @SuppressWarnings("unchecked")
    void totalsAndRowsShapeSharedJsonPayload() {
        UnifiedRuntimeRequirementCompatibility ready = UnifiedRuntimeRequirementCompatibility.ready(
                "ready-family",
                "ready_unified",
                List.of("text"),
                true,
                List.of("ready-runtime"),
                List.of("text"));
        UnifiedRuntimeRequirementCompatibility missing = UnifiedRuntimeRequirementCompatibility.attention(
                "blocked-family",
                "blocked_unified",
                List.of("text", "image"),
                true,
                UnifiedRuntimeRequirementIssueKind.MISSING_RUNTIME,
                List.of(),
                List.of(),
                "Attach one unified runtime plugin that claims model_type=blocked_unified.");

        Map<String, Object> totals = UnifiedRuntimeRequirementReports.totals(List.of(ready, missing));
        List<Map<String, Object>> reports =
                UnifiedRuntimeRequirementReports.compatibilityReports(List.of(ready, missing));
        Map<String, Object> section =
                UnifiedRuntimeRequirementReports.modelFamilyRequirementSection(List.of(ready, missing));

        assertEquals(List.of(), UnifiedRuntimeRequirementReportContract.validateSection(section));
        Map<String, Object> contract =
                (Map<String, Object>) section.get(
                        UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_CONTRACT);
        assertEquals(
                UnifiedRuntimeRequirementReportFields.CONTRACT_ID,
                contract.get(UnifiedRuntimeRequirementReportFields.Contract.CONTRACT_ID));
        assertEquals(
                UnifiedRuntimeRequirementReportFields.SCHEMA_VERSION,
                contract.get(UnifiedRuntimeRequirementReportFields.Contract.SCHEMA_VERSION));
        assertEquals(
                UnifiedRuntimeRequirementReportFields.schemaFingerprint(),
                contract.get(UnifiedRuntimeRequirementReportFields.Contract.SCHEMA_FINGERPRINT));
        assertEquals(true, contract.get(UnifiedRuntimeRequirementReportFields.Contract.PASSED));
        assertEquals(0, contract.get(UnifiedRuntimeRequirementReportFields.Contract.PROBLEM_COUNT));
        assertEquals(List.of(), contract.get(UnifiedRuntimeRequirementReportFields.Contract.PROBLEMS));
        assertEquals(
                UnifiedRuntimeRequirementReportFields.SCHEMA_VERSION,
                section.get(
                        UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_SCHEMA_VERSION));
        Map<String, Object> schema =
                (Map<String, Object>) section.get(
                        UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_SCHEMA);
        assertEquals(
                UnifiedRuntimeRequirementReportFields.CONTRACT_ID,
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.CONTRACT_ID));
        assertEquals(
                UnifiedRuntimeRequirementReportFields.SCHEMA_VERSION,
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.SCHEMA_VERSION));
        assertEquals(
                UnifiedRuntimeRequirementReportFields.schemaFingerprint(),
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.SCHEMA_FINGERPRINT));
        assertEquals(
                UnifiedRuntimeRequirementReportFields.sectionKeys(),
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.SECTION_KEYS));
        assertEquals(
                UnifiedRuntimeRequirementReportFields.totalsKeys(),
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.TOTALS_KEYS));
        assertEquals(
                UnifiedRuntimeRequirementReportFields.requirementKeys(),
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.REQUIREMENT_KEYS));
        assertEquals(
                UnifiedRuntimeRequirementReportFields.issueKeys(),
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.ISSUE_KEYS));
        assertEquals(
                UnifiedRuntimeRequirementReportFields.affectedRequirementKeys(),
                schema.get(UnifiedRuntimeRequirementReportFields.Schema.AFFECTED_REQUIREMENT_KEYS));
        assertEquals(2, totals.get(UnifiedRuntimeRequirementReportFields.Totals.REQUIREMENT_COUNT));
        assertEquals(1L, totals.get(UnifiedRuntimeRequirementReportFields.Totals.COMPATIBLE_COUNT));
        assertEquals(1L, totals.get(UnifiedRuntimeRequirementReportFields.Totals.ATTENTION_COUNT));
        assertEquals(
                List.of("blocked-family", "ready-family"),
                totals.get(UnifiedRuntimeRequirementReportFields.Totals.FAMILY_IDS));
        assertEquals(
                List.of("ready-family"),
                totals.get(UnifiedRuntimeRequirementReportFields.Totals.COMPATIBLE_FAMILY_IDS));
        assertEquals(
                List.of("blocked-family"),
                totals.get(UnifiedRuntimeRequirementReportFields.Totals.ATTENTION_FAMILY_IDS));
        assertEquals(
                List.of("blocked_unified", "ready_unified"),
                totals.get(UnifiedRuntimeRequirementReportFields.Totals.MODEL_TYPES));
        assertEquals(
                List.of("ready_unified"),
                totals.get(UnifiedRuntimeRequirementReportFields.Totals.COMPATIBLE_MODEL_TYPES));
        assertEquals(
                List.of("blocked_unified"),
                totals.get(UnifiedRuntimeRequirementReportFields.Totals.ATTENTION_MODEL_TYPES));
        Map<String, Long> byStatus =
                (Map<String, Long>) totals.get(UnifiedRuntimeRequirementReportFields.Totals.BY_STATUS);
        assertEquals(1L, byStatus.get(UnifiedRuntimeRequirementStatuses.READY));
        assertEquals(1L, byStatus.get(UnifiedRuntimeRequirementStatuses.MISSING_RUNTIME));
        assertEquals(
                List.of(UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME),
                totals.get(UnifiedRuntimeRequirementReportFields.Totals.PROBLEM_CODES));
        Map<String, Long> problemCodeCounts =
                (Map<String, Long>) totals.get(UnifiedRuntimeRequirementReportFields.Totals.PROBLEM_CODE_COUNTS);
        assertEquals(1L, problemCodeCounts.get(UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME));
        assertEquals(
                List.of("Attach one unified runtime plugin that claims model_type=blocked_unified."),
                totals.get(UnifiedRuntimeRequirementReportFields.Totals.REMEDIATION_HINTS));
        Map<String, Long> remediationHintCounts =
                (Map<String, Long>) totals.get(UnifiedRuntimeRequirementReportFields.Totals.REMEDIATION_HINT_COUNTS);
        assertEquals(
                1L,
                remediationHintCounts.get(
                        "Attach one unified runtime plugin that claims model_type=blocked_unified."));
        List<Map<String, Object>> issues =
                (List<Map<String, Object>>) totals.get(UnifiedRuntimeRequirementReportFields.Totals.ISSUES);
        assertEquals(1, issues.size());
        assertEquals(
                UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME,
                issues.getFirst().get(UnifiedRuntimeRequirementReportFields.Issue.PROBLEM_CODE));
        assertEquals(
                UnifiedRuntimeRequirementStatuses.MISSING_RUNTIME,
                issues.getFirst().get(UnifiedRuntimeRequirementReportFields.Issue.STATUS));
        assertEquals(1L, issues.getFirst().get(UnifiedRuntimeRequirementReportFields.Issue.COUNT));
        List<Map<String, Object>> affectedRequirements =
                (List<Map<String, Object>>) issues.getFirst()
                        .get(UnifiedRuntimeRequirementReportFields.Issue.AFFECTED_REQUIREMENTS);
        assertEquals(1, affectedRequirements.size());
        assertEquals(
                "blocked-family",
                affectedRequirements.getFirst().get(UnifiedRuntimeRequirementReportFields.Requirement.FAMILY_ID));
        assertEquals(
                "blocked_unified",
                affectedRequirements.getFirst().get(UnifiedRuntimeRequirementReportFields.Requirement.MODEL_TYPE));
        assertEquals(
                List.of("text", "image"),
                affectedRequirements.getFirst()
                        .get(UnifiedRuntimeRequirementReportFields.Requirement.REQUIRED_INPUT_MODALITIES));
        assertEquals(
                List.of("blocked-family"),
                issues.getFirst().get(UnifiedRuntimeRequirementReportFields.Issue.FAMILY_IDS));
        assertEquals(
                List.of("blocked_unified"),
                issues.getFirst().get(UnifiedRuntimeRequirementReportFields.Issue.MODEL_TYPES));
        assertEquals(
                List.of("Attach one unified runtime plugin that claims model_type=blocked_unified."),
                issues.getFirst().get(UnifiedRuntimeRequirementReportFields.Issue.REMEDIATION_HINTS));
        assertEquals(2, reports.size());
        assertEquals(
                "blocked-family",
                reports.get(1).get(UnifiedRuntimeRequirementReportFields.Requirement.FAMILY_ID));
        assertEquals(
                "blocked_unified",
                reports.get(1).get(UnifiedRuntimeRequirementReportFields.Requirement.MODEL_TYPE));
        assertEquals(
                List.of(UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME),
                reports.get(1).get(UnifiedRuntimeRequirementReportFields.Requirement.PROBLEM_CODES));
        assertEquals(
                totals,
                section.get(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_TOTALS));
        assertEquals(
                reports,
                section.get(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENTS));
        List<String> recommendations = (List<String>) section.get(
                UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_RECOMMENDATIONS);
        assertEquals(1, recommendations.size());
        assertTrue(recommendations.getFirst().contains("blocked-family->blocked_unified"));
        assertEquals(
                0,
                UnifiedRuntimeRequirementReports.totals(null)
                        .get(UnifiedRuntimeRequirementReportFields.Totals.REQUIREMENT_COUNT));
        assertEquals(List.of(), UnifiedRuntimeRequirementReports.compatibilityReports(null));
        Map<String, Object> emptySection = UnifiedRuntimeRequirementReports.modelFamilyRequirementSection(null);
        Map<String, Object> emptyTotals =
                (Map<String, Object>) emptySection.get(
                        UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_TOTALS);
        assertEquals(0, emptyTotals.get(UnifiedRuntimeRequirementReportFields.Totals.REQUIREMENT_COUNT));
        assertEquals(
                List.of(),
                emptySection.get(
                        UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_RECOMMENDATIONS));
        assertEquals(
                List.of(),
                emptySection.get(UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENTS));
    }

    @Test
    @SuppressWarnings("unchecked")
    void contractValidatorExplainsSchemaDrift() {
        Map<String, Object> section = UnifiedRuntimeRequirementReports.modelFamilyRequirementSection(List.of());
        Map<String, Object> driftedSection = new LinkedHashMap<>(section);
        Map<String, Object> driftedSchema = new LinkedHashMap<>(
                (Map<String, Object>) section.get(
                        UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_SCHEMA));
        driftedSchema.put(
                UnifiedRuntimeRequirementReportFields.Schema.SCHEMA_FINGERPRINT,
                "sha256:drifted");
        driftedSection.put(
                UnifiedRuntimeRequirementReportFields.Section.MODEL_FAMILY_REQUIREMENT_SCHEMA,
                driftedSchema);

        List<String> problems = UnifiedRuntimeRequirementReportContract.validateSection(driftedSection);

        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("schema.schemaFingerprint expected sha256:"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void statusOnlyRowsContributeCanonicalProblemCodes() {
        UnifiedRuntimeRequirementCompatibility legacy = new UnifiedRuntimeRequirementCompatibility(
                "legacy-family",
                "legacy_unified",
                List.of("text"),
                true,
                UnifiedRuntimeRequirementStatuses.MISSING_RUNTIME,
                List.of(),
                List.of(),
                List.of(),
                List.of());

        Map<String, Object> totals = UnifiedRuntimeRequirementReports.totals(List.of(legacy));
        List<Map<String, Object>> reports =
                UnifiedRuntimeRequirementReports.compatibilityReports(List.of(legacy));

        assertEquals(1L, totals.get(UnifiedRuntimeRequirementReportFields.Totals.ATTENTION_COUNT));
        assertEquals(
                List.of(UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME),
                totals.get(UnifiedRuntimeRequirementReportFields.Totals.PROBLEM_CODES));
        Map<String, Long> problemCodeCounts =
                (Map<String, Long>) totals.get(UnifiedRuntimeRequirementReportFields.Totals.PROBLEM_CODE_COUNTS);
        assertEquals(1L, problemCodeCounts.get(UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME));
        assertEquals(
                List.of("Attach one unified runtime plugin that claims model_type=legacy_unified."),
                totals.get(UnifiedRuntimeRequirementReportFields.Totals.REMEDIATION_HINTS));
        Map<String, Long> remediationHintCounts =
                (Map<String, Long>) totals.get(UnifiedRuntimeRequirementReportFields.Totals.REMEDIATION_HINT_COUNTS);
        assertEquals(
                1L,
                remediationHintCounts.get(
                        "Attach one unified runtime plugin that claims model_type=legacy_unified."));
        List<Map<String, Object>> issues =
                (List<Map<String, Object>>) totals.get(UnifiedRuntimeRequirementReportFields.Totals.ISSUES);
        assertEquals(1, issues.size());
        assertEquals(
                UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME,
                issues.getFirst().get(UnifiedRuntimeRequirementReportFields.Issue.PROBLEM_CODE));
        assertEquals(
                UnifiedRuntimeRequirementStatuses.MISSING_RUNTIME,
                issues.getFirst().get(UnifiedRuntimeRequirementReportFields.Issue.STATUS));
        List<Map<String, Object>> affectedRequirements =
                (List<Map<String, Object>>) issues.getFirst()
                        .get(UnifiedRuntimeRequirementReportFields.Issue.AFFECTED_REQUIREMENTS);
        assertEquals(
                "legacy-family",
                affectedRequirements.getFirst().get(UnifiedRuntimeRequirementReportFields.Requirement.FAMILY_ID));
        assertEquals(
                "legacy_unified",
                affectedRequirements.getFirst().get(UnifiedRuntimeRequirementReportFields.Requirement.MODEL_TYPE));
        assertEquals(
                List.of("legacy-family"),
                issues.getFirst().get(UnifiedRuntimeRequirementReportFields.Issue.FAMILY_IDS));
        assertEquals(
                List.of(UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME),
                reports.getFirst().get(UnifiedRuntimeRequirementReportFields.Requirement.PROBLEM_CODES));
        assertEquals(
                List.of("Attach one unified runtime plugin that claims model_type=legacy_unified."),
                reports.getFirst().get(UnifiedRuntimeRequirementReportFields.Requirement.REMEDIATION_HINTS));
    }
}
