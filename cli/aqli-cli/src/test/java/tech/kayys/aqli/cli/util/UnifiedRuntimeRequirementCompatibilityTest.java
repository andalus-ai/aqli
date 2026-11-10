package tech.kayys.aqli.cli.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnifiedRuntimeRequirementCompatibilityTest {
    @Test
    void readyFactoryUsesSharedReadyContract() {
        UnifiedRuntimeRequirementCompatibility compatibility = UnifiedRuntimeRequirementCompatibility.ready(
                "ready-family",
                "ready_unified",
                List.of("text"),
                true,
                List.of("ready-runtime"),
                List.of("text"));

        assertEquals(UnifiedRuntimeRequirementStatuses.READY, compatibility.status());
        assertTrue(compatibility.compatible());
        assertFalse(compatibility.requiresAttention());
        assertEquals(List.of(), compatibility.problemCodes());
        assertEquals(List.of(), compatibility.remediationHints());
        assertEquals(List.of(), compatibility.effectiveRemediationHints());
    }

    @Test
    void attentionFactoryUsesIssueKindMappingAndNormalizesHint() {
        UnifiedRuntimeRequirementCompatibility compatibility = UnifiedRuntimeRequirementCompatibility.attention(
                "blocked-family",
                "blocked_unified",
                List.of("text", "image"),
                true,
                UnifiedRuntimeRequirementIssueKind.MISSING_RUNTIME,
                List.of(),
                List.of(),
                "  attach runtime  ");

        assertEquals(UnifiedRuntimeRequirementStatuses.MISSING_RUNTIME, compatibility.status());
        assertFalse(compatibility.compatible());
        assertTrue(compatibility.requiresAttention());
        assertEquals(
                List.of(UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME),
                compatibility.problemCodes());
        assertEquals(
                List.of(UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME),
                compatibility.effectiveProblemCodes());
        assertEquals(List.of("attach runtime"), compatibility.remediationHints());
        assertEquals(List.of("attach runtime"), compatibility.effectiveRemediationHints());
    }

    @Test
    void blankStatusUsesSharedUnknownContract() {
        UnifiedRuntimeRequirementCompatibility compatibility = new UnifiedRuntimeRequirementCompatibility(
                "blank-status-family",
                "blank_status_unified",
                List.of(),
                false,
                " ",
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertEquals(UnifiedRuntimeRequirementStatuses.UNKNOWN, compatibility.status());
        assertFalse(compatibility.compatible());
        assertTrue(compatibility.requiresAttention());
        assertEquals(List.of(UnifiedRuntimeRequirementStatuses.UNKNOWN), compatibility.effectiveProblemCodes());
        assertEquals(List.of(), compatibility.effectiveRemediationHints());
    }

    @Test
    void legacyStatusOnlyIssueRequiresAttention() {
        UnifiedRuntimeRequirementCompatibility compatibility = new UnifiedRuntimeRequirementCompatibility(
                "legacy-family",
                "legacy_unified",
                List.of("text"),
                true,
                UnifiedRuntimeRequirementStatuses.MISSING_RUNTIME,
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertFalse(compatibility.compatible());
        assertTrue(compatibility.requiresAttention());
        assertEquals(
                List.of(UnifiedRuntimeRequirementProblemCodes.MISSING_RUNTIME),
                compatibility.effectiveProblemCodes());
        assertEquals(
                List.of("Attach one unified runtime plugin that claims model_type=legacy_unified."),
                compatibility.effectiveRemediationHints());
    }
}
