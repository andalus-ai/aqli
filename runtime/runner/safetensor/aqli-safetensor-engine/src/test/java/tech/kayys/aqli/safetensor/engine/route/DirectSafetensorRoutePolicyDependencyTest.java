package tech.kayys.aqli.safetensor.engine.route;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class DirectSafetensorRoutePolicyDependencyTest {

    @Test
    void localModelResolverIsInjectedRatherThanStoredAsGlobalState() {
        assertThrows(
                NoSuchFieldException.class,
                () -> DirectSafetensorRoutePolicy.class.getDeclaredField("resolver"));

        assertDoesNotThrow(() -> DirectSafetensorRoutePolicy.class.getMethod(
                "selectGemma4TextAlternateRuntime",
                String.class,
                String.class,
                String.class,
                boolean.class,
                boolean.class,
                Predicate.class,
                LocalModelResolver.class));

        assertDoesNotThrow(() -> DirectSafetensorRoutePolicy.class.getMethod(
                "selectCommunityTextGgufAlternateRuntime",
                String.class,
                String.class,
                String.class,
                boolean.class,
                boolean.class,
                Predicate.class,
                LocalModelResolver.class));

        assertDoesNotThrow(() -> DirectSafetensorRoutePolicy.class.getMethod(
                "selectGemma4MobileQatAlternateRuntime",
                String.class,
                String.class,
                String.class,
                boolean.class,
                boolean.class,
                Predicate.class,
                Function.class));
    }
}
