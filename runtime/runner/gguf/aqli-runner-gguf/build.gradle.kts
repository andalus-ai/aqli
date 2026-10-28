plugins {
    java
}

dependencies {
    implementation(project(":core:aqli-core"))
    implementation(project(":spi:aqli-spi"))
    implementation(project(":spi:aqli-spi-runner"))
    implementation(project(":spi:aqli-spi-plugin"))
    implementation(project(":spi:aqli-spi-inference"))
    implementation(project(":spi:aqli-spi-image"))
    // added to fix provider capabilities and metrics errors
    implementation(project(":plugin:aqli-plugin-runner-core"))
    // added for ManifestStore
    implementation(project(":repository:aqli-model-repo-local"))
    
    // Aljabr owns generated llama.cpp/ggml FFM bindings and the reusable GGUF backend.
    implementation("tech.kayys.aljabr:aljabr-gguf-core:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-spi-model:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-gguf-llamacpp:0.1.0-SNAPSHOT")
    
    implementation("org.jboss.logging:jboss-logging")
    implementation("io.smallrye.reactive:mutiny:2.5.5")
    implementation("io.micrometer:micrometer-core:1.14.4")
    implementation("com.hubspot.jinjava:jinjava:2.7.3")
    
    // Jakarta / MicroProfile for CDI/JAX-RS annotations
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1")
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
    implementation("org.eclipse.microprofile.fault-tolerance:microprofile-fault-tolerance-api:4.0.2")
    implementation("io.quarkus:quarkus-arc:3.7.1")
    implementation("io.opentelemetry:opentelemetry-api:1.34.1")
    implementation("org.eclipse.microprofile.fault-tolerance:microprofile-fault-tolerance-api:4.0.2")
    
    testImplementation(project(":sdk:aqli-sdk-core"))
}
