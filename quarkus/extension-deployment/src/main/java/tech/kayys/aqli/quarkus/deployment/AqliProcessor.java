package tech.kayys.aqli.quarkus.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;

public class AqliProcessor {

    @BuildStep
    ExtensionSslNativeSupportBuildItem setup() {
        // Quarkus build step for Aqli integration
        return new ExtensionSslNativeSupportBuildItem(false);
    }
}
