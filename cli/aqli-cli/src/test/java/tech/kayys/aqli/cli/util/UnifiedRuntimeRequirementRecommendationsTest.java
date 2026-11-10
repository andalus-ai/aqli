package tech.kayys.aqli.cli.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedRuntimeRequirementRecommendationsTest {
    @Test
    void problemCodeCatalogKeepsStableRecommendationOrder() {
        assertEquals(List.of(
                        UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME,
                        UnifiedRuntimeRequirementProblemCodes.CONFLICTING_MODEL_TYPE_CLAIM,
                        UnifiedRuntimeRequirementProblemCodes.MANIFEST_INVALID,
                        UnifiedRuntimeRequirementProblemCodes.MISSING_REQUIRED_MODALITIES,
                        UnifiedRuntimeRequirementProblemCodes.NOT_PRODUCTION_READY),
                UnifiedRuntimeRequirementProblemCodes.ORDERED);
        assertEquals(
                (long) UnifiedRuntimeRequirementProblemCodes.ORDERED.size(),
                UnifiedRuntimeRequirementProblemCodes.ORDERED.stream().distinct().count());
        assertEquals(
                UnifiedRuntimeRequirementProblemCodes.ORDERED,
                UnifiedRuntimeRequirementIssueKind.problemCodesInDeclarationOrder());
        assertEquals(
                Optional.of(UnifiedRuntimeRequirementIssueKind.MISSING_RUNTIME),
                UnifiedRuntimeRequirementIssueKind.fromProblemCode(
                        " " + UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME + " "));
        assertEquals(
                Optional.of(UnifiedRuntimeRequirementIssueKind.MISSING_RUNTIME),
                UnifiedRuntimeRequirementIssueKind.fromStatus(
                        " " + UnifiedRuntimeRequirementStatuses.MISSING_RUNTIME + " "));
        assertEquals(
                Optional.empty(),
                UnifiedRuntimeRequirementIssueKind.fromProblemCode("unified_runtime_custom_problem"));
        assertEquals(List.of(
                        UnifiedRuntimeRequirementStatuses.MISSING_RUNTIME,
                        UnifiedRuntimeRequirementStatuses.CONFLICTING_RUNTIME,
                        UnifiedRuntimeRequirementStatuses.INVALID_RUNTIME,
                        UnifiedRuntimeRequirementStatuses.INSUFFICIENT_MODALITIES,
                        UnifiedRuntimeRequirementStatuses.NOT_PRODUCTION_READY),
                UnifiedRuntimeRequirementIssueKind.statusesInDeclarationOrder());
        assertEquals(
                (long) UnifiedRuntimeRequirementIssueKind.values().length,
                UnifiedRuntimeRequirementIssueKind.statusesInDeclarationOrder().stream().distinct().count());
    }

    @Test
    void classifiesProblemCodesIntoActionableRecommendations() {
        UnifiedRuntimeRequirementCompatibility ready = UnifiedRuntimeRequirementCompatibility.ready(
                "ready-family",
                "ready_unified",
                List.of("text"),
                true,
                List.of("ready-runtime"),
                List.of("text"));
        UnifiedRuntimeRequirementCompatibility missing = UnifiedRuntimeRequirementCompatibility.attention(
                "missing-family",
                "missing_unified",
                List.of("text"),
                true,
                UnifiedRuntimeRequirementIssueKind.MISSING_RUNTIME,
                List.of(),
                List.of(),
                "");
        UnifiedRuntimeRequirementCompatibility conflicting = UnifiedRuntimeRequirementCompatibility.attention(
                "conflict-family",
                "conflict_unified",
                List.of("text"),
                true,
                UnifiedRuntimeRequirementIssueKind.CONFLICTING_RUNTIME,
                List.of("first-conflict-runtime", "second-conflict-runtime"),
                List.of("text"),
                "");
        UnifiedRuntimeRequirementCompatibility invalid = UnifiedRuntimeRequirementCompatibility.attention(
                "invalid-family",
                "invalid_unified",
                List.of("text"),
                true,
                UnifiedRuntimeRequirementIssueKind.INVALID_RUNTIME,
                List.of("invalid-runtime"),
                List.of(),
                "");
        UnifiedRuntimeRequirementCompatibility insufficient = UnifiedRuntimeRequirementCompatibility.attention(
                "insufficient-family",
                "insufficient_unified",
                List.of("text", "image"),
                true,
                UnifiedRuntimeRequirementIssueKind.INSUFFICIENT_MODALITIES,
                List.of("text-only-runtime"),
                List.of("text"),
                "");
        UnifiedRuntimeRequirementCompatibility notReady = UnifiedRuntimeRequirementCompatibility.attention(
                "not-ready-family",
                "not_ready_unified",
                List.of("text"),
                true,
                UnifiedRuntimeRequirementIssueKind.NOT_PRODUCTION_READY,
                List.of("experimental-runtime"),
                List.of("text"),
                "");

        List<UnifiedRuntimeRequirementCompatibility> requirements = List.of(
                ready,
                missing,
                conflicting,
                invalid,
                insufficient,
                notReady);
        List<String> recommendations = UnifiedRuntimeRequirementRecommendations.fromRequirements(requirements);

        assertEquals(5, recommendations.size());
        assertTrue(recommendations.get(0).contains("Attach unified runtime plugins"));
        assertTrue(recommendations.get(0).contains("missing-family->missing_unified"));
        assertTrue(recommendations.get(1).contains("Detach duplicate unified runtime plugins"));
        assertTrue(recommendations.get(1).contains("conflict_unified"));
        assertTrue(recommendations.get(2).contains("Fix unified runtime manifest violations"));
        assertTrue(recommendations.get(2).contains("invalid-runtime"));
        assertTrue(recommendations.get(3).contains("all required modalities"));
        assertTrue(recommendations.get(3).contains("insufficient-family->insufficient_unified needs text/image"));
        assertTrue(recommendations.get(4).contains("out of production bundles"));
        assertTrue(recommendations.get(4).contains("not-ready-family->not_ready_unified"));
        assertEquals(
                recommendations,
                UnifiedRuntimeRequirementRecommendations.fromTotals(
                        UnifiedRuntimeRequirementReports.totals(requirements)));
        assertEquals(List.of(), UnifiedRuntimeRequirementRecommendations.fromRequirements(null));
        assertEquals(List.of(), UnifiedRuntimeRequirementRecommendations.fromTotals(null));
        assertEquals(List.of(), UnifiedRuntimeRequirementRecommendations.fromTotals(Map.of()));
    }

    @Test
    void statusOnlyLegacyRequirementsUseActionableRecommendations() {
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

        List<String> recommendations = UnifiedRuntimeRequirementRecommendations.fromRequirements(List.of(legacy));

        assertEquals(1, recommendations.size());
        assertTrue(recommendations.getFirst().contains("Attach unified runtime plugins"));
        assertTrue(recommendations.getFirst().contains("legacy-family->legacy_unified"));
    }
}
