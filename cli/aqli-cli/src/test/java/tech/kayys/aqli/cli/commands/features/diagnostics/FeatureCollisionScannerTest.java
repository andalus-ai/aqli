package tech.kayys.aqli.cli.commands.features.diagnostics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeatureCollisionScannerTest {
    @Test void reportsCrossJarCollisionsInStableOrder() {
        var a=new FeatureCollisionScanner.JarClaims("/a.jar","same",List.of("example.Pipeline"),List.of(),List.of(),
                List.of(new FeatureCollisionScanner.CapabilityClaim("feature","pipeline","example.Impl","provider-a","capabilities[0]:pipeline")));
        var b=new FeatureCollisionScanner.JarClaims("/b.jar","same",List.of("example.Pipeline"),List.of(),List.of(),
                List.of(new FeatureCollisionScanner.CapabilityClaim("feature","pipeline","example.Impl","provider-b","capabilities[0]:pipeline")));
        var report=FeatureCollisionScanner.scan(List.of(a,b),"pipeline-service","adapter-service","model-family-service");
        assertEquals(List.of("extension_id","feature_id","implementation_class","pipeline_id","provider_class"),
                report.items().stream().map(FeatureCollisionScanner.Item::kind).toList());
        assertEquals("warning",report.toMap().get("status"));
    }
    @Test void ignoresBlankClaimsAndDuplicatesWithinOneJar() {
        var jar=new FeatureCollisionScanner.JarClaims("/a.jar","same",List.of("example.Pipeline","example.Pipeline"),List.of(),List.of(),
                List.of(new FeatureCollisionScanner.CapabilityClaim("feature","pipeline","","","capabilities[0]:pipeline")));
        var report=FeatureCollisionScanner.scan(List.of(jar),"pipeline-service","adapter-service","model-family-service");
        assertTrue(report.items().isEmpty()); assertEquals("ok",report.toMap().get("status"));
    }
}
