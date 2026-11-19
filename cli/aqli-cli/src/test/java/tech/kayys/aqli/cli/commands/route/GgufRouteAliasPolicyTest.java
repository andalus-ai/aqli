package tech.kayys.aqli.cli.commands.route;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GgufRouteAliasPolicyTest {

    @Test
    void recognizesProviderAliasesCaseInsensitivelyAndWithWhitespace() {
        for (String alias : new String[] {
                "gguf", "llamacpp", "llama.cpp", "llama-cpp", "java", "java-native", "jvm"
        }) {
            assertTrue(GgufRouteAliasPolicy.isProviderAlias("  " + alias.toUpperCase() + " "));
        }
    }

    @Test
    void rejectsUnknownOrMissingProviders() {
        assertFalse(GgufRouteAliasPolicy.isProviderAlias(null));
        assertFalse(GgufRouteAliasPolicy.isProviderAlias(""));
        assertFalse(GgufRouteAliasPolicy.isProviderAlias("openai"));
    }

    @Test
    void recognizesPluginAliasesWithoutAddingProviderOnlyNames() {
        for (String alias : new String[] {
                "gguf", "gguf-runner", "llamacpp", "llama.cpp", "llama-cpp"
        }) {
            assertTrue(GgufRouteAliasPolicy.isPluginAlias("  " + alias.toUpperCase() + " "));
        }
        assertFalse(GgufRouteAliasPolicy.isPluginAlias("java"));
        assertFalse(GgufRouteAliasPolicy.isPluginAlias("jvm"));
    }

    @Test
    void rejectsUnknownOrMissingPlugins() {
        assertFalse(GgufRouteAliasPolicy.isPluginAlias(null));
        assertFalse(GgufRouteAliasPolicy.isPluginAlias(""));
        assertFalse(GgufRouteAliasPolicy.isPluginAlias("openai"));
    }
}
