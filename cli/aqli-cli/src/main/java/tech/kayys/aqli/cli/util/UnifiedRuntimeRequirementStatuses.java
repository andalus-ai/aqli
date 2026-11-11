/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

/**
 * Stable status contract for model-family unified runtime requirements.
 */
public final class UnifiedRuntimeRequirementStatuses {
    public static final String READY = "ready";
    public static final String MISSING_RUNTIME = "missing_runtime";
    public static final String CONFLICTING_RUNTIME = "conflicting_runtime";
    public static final String INVALID_RUNTIME = "invalid_runtime";
    public static final String INSUFFICIENT_MODALITIES = "insufficient_modalities";
    public static final String NOT_PRODUCTION_READY = "not_production_ready";
    public static final String UNKNOWN = "unknown";

    private UnifiedRuntimeRequirementStatuses() {
    }
}
