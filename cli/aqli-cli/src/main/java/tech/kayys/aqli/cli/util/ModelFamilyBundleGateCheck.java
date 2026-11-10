/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

/**
 * Build-time smoke check for packaged model-family bundle readiness.
 */
public final class ModelFamilyBundleGateCheck {
    private ModelFamilyBundleGateCheck() {
    }

    public static void main(String[] args) throws Exception {
        try (ExternalPluginClasspathScope pluginScope =
                ExternalPluginClasspathScope.open(args, 0, ModelFamilyBundleGateCheck.class)) {
            ModelFamilyBundleGate gate = pluginScope.discoveryClassLoader() == null
                    ? new PluginAvailabilityChecker().getModelFamilyBundleGate()
                    : PluginAvailabilityChecker.getGlobalModelFamilyBundleGate(pluginScope.discoveryClassLoader());
            if (gate.failed()) {
                throw new IllegalStateException(gate.failureMessage());
            }
            System.out.printf(
                    "Model-family bundle gate %s (%d violation(s))%n",
                    gate.status(),
                    gate.violationCount());
        }
    }
}
