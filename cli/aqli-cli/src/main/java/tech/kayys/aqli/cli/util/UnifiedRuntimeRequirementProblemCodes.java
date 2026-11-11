/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.util.List;

/**
 * Stable problem-code contract for model-family unified runtime requirements.
 */
public final class UnifiedRuntimeRequirementProblemCodes {
    public static final String MISSING_RUNTIME = "unified_runtime_missing";
    public static final String CONFLICTING_MODEL_TYPE_CLAIM =
            "unified_runtime_conflicting_model_type_claim";
    public static final String MANIFEST_INVALID = "unified_runtime_manifest_invalid";
    public static final String MISSING_REQUIRED_MODALITIES =
            "unified_runtime_missing_required_modalities";
    public static final String NOT_PRODUCTION_READY = "unified_runtime_not_production_ready";

    public static final List<String> ORDERED = List.of(
            MISSING_RUNTIME,
            CONFLICTING_MODEL_TYPE_CLAIM,
            MANIFEST_INVALID,
            MISSING_REQUIRED_MODALITIES,
            NOT_PRODUCTION_READY);

    private UnifiedRuntimeRequirementProblemCodes() {
    }
}
