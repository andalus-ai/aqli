/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import tech.kayys.aqli.plugin.core.ExtensionAvailabilityContractReport;

/**
 * Build-time smoke check for packaged extension availability providers.
 */
public final class ExtensionAvailabilityContractCheck {
    private ExtensionAvailabilityContractCheck() {
    }

    public static void main(String[] args) throws Exception {
        try (ExternalPluginClasspathScope pluginScope =
                ExternalPluginClasspathScope.open(args, 0, ExtensionAvailabilityContractCheck.class)) {
            ExtensionAvailabilityContractReport report =
                    PluginAvailabilityChecker.getGlobalExtensionAvailabilityContractReport(
                            pluginScope.discoveryClassLoader());
            if (report.failed()) {
                throw new IllegalStateException(
                        "Extension availability provider contracts failed:\n  - "
                                + String.join("\n  - ", report.summaries()));
            }
            System.out.printf(
                    "Extension availability provider contracts %s (%d violation(s))%n",
                    report.status(),
                    report.violationCount());
        }
    }
}
