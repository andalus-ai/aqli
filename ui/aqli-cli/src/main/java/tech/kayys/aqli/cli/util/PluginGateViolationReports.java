/*
 * MIT License
 *
 * Copyright (c) 2026 Kayys.tech
 */

package tech.kayys.aqli.cli.util;
import tech.kayys.aqli.sdk.route.*;
import tech.kayys.aqli.safetensor.engine.route.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable grouping for combined plugin gate violations.
 */
public final class PluginGateViolationReports {
    private static final List<String> CATEGORY_KEYS = List.of(
            "extension",
            "modelFamily",
            "pluginDirectory",
            "runnerRoute",
            "unifiedRuntime",
            "unifiedRuntimeRequirement",
            "unknown");

    private PluginGateViolationReports() {
    }

    public static Map<String, Integer> categories(PluginGates gates) {
        return categories(gates == null ? List.of() : gates.violations());
    }

    public static Map<String, Integer> categories(List<String> violations) {
        Map<String, Integer> categories = emptyCategories();
        for (String violation : violations == null ? List.<String>of() : violations) {
            String category = category(violation);
            categories.put(category, categories.get(category) + 1);
        }
        return categories;
    }

    public static String category(String violation) {
        String value = violation == null ? "" : violation.strip();
        if (value.startsWith("extension:")) {
            return "extension";
        }
        if (value.startsWith("model-family:")) {
            return "modelFamily";
        }
        if (value.startsWith("plugin-directory:")) {
            return "pluginDirectory";
        }
        if (value.startsWith("runner-route:")) {
            return "runnerRoute";
        }
        if (value.startsWith("unified-runtime-requirement:")) {
            return "unifiedRuntimeRequirement";
        }
        if (value.startsWith("unified-runtime:")) {
            return "unifiedRuntime";
        }
        return "unknown";
    }

    private static Map<String, Integer> emptyCategories() {
        Map<String, Integer> categories = new LinkedHashMap<>();
        for (String key : CATEGORY_KEYS) {
            categories.put(key, 0);
        }
        return categories;
    }
}
