plugins {
    java
    id("io.quarkus")
}

// Prevent old tech.kayys.aljabr:aqli-spi* published snapshots from landing on the
// classpath alongside the live project modules.  Both carry identical Quarkus CDI extensions
// that would register the same synthetic beans (e.g. KaggleConfig) twice, causing:
//   IllegalStateException: A synthetic bean … is already registered
configurations.all {
    exclude(group = "tech.kayys.aljabr", module = "aqli-spi")
    exclude(group = "tech.kayys.aljabr", module = "aqli-spi-inference")
    exclude(group = "tech.kayys.aljabr", module = "aqli-spi-multimodal")
    exclude(group = "tech.kayys.aljabr", module = "aqli-spi-plugin")
    exclude(group = "tech.kayys.aljabr", module = "aqli-spi-runtime")
    
    // Also exclude obsolete tech.kayys.aqli artifacts that have been merged/refactored
    // (e.g. aqli-runtime-config was split into aqli-model-repo-hf and others)
    exclude(group = "tech.kayys.aqli", module = "aqli-runtime-config")
    exclude(group = "tech.kayys.aqli", module = "aqli-ir")
    
    // Exclude obsolete safetensor artifacts in the old namespace to avoid CDI issues
    // where old engines (like WhisperEngine) try to inject the old excluded SPI.
    exclude(group = "tech.kayys.aqli", module = "aqli-safetensor-audio")
    exclude(group = "tech.kayys.aqli", module = "aqli-safetensor-api")
    exclude(group = "tech.kayys.aqli", module = "aqli-safetensor-core")
    exclude(group = "tech.kayys.aqli", module = "aqli-safetensor-loader")
    exclude(group = "tech.kayys.aqli", module = "aqli-safetensor-spi")
}

val quarkusVersion = rootProject.extra["quarkusVersion"] as String

dependencies {
    implementation(platform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))

    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-cache")
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-logging-json")
    implementation("io.quarkus:quarkus-picocli")
    implementation("io.quarkus:quarkus-mutiny")
    implementation("io.quarkus:quarkus-smallrye-health")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("commons-io:commons-io:2.18.0")
    implementation("org.jline:jline-console:3.26.3")
    implementation("org.jline:jline-reader:3.26.3")
    implementation("org.jline:jline-terminal:3.26.3")
    implementation("org.jline:jline-terminal-jna:3.26.3")
    implementation("com.github.albfernandez:juniversalchardet:2.4.0")

    implementation(project(":sdk:aqli-sdk"))
    implementation(project(":sdk:aqli-sdk-api"))
    implementation(project(":sdk:aqli-sdk-core"))
    implementation(project(":spi:aqli-spi"))
    implementation(project(":spi:aqli-spi-inference"))
    implementation("tech.kayys.aljabr:aljabr-spi-model:0.1.0-SNAPSHOT")
    implementation(project(":spi:aqli-spi-multimodal"))
    implementation(project(":spi:aqli-spi-runtime"))
    implementation(project(":core:aqli-core"))
    implementation(project(":tokenizer:aqli-tokenizer-core"))
    implementation("io.opentelemetry:opentelemetry-api")
    implementation(project(":plugin:aqli-plugin-core"))
    implementation(project(":plugin:aqli-plugin-kernel-core"))
    implementation(project(":plugin:aqli-plugin-runner-core"))
    implementation(project(":runner:gguf:aqli-plugin-runner-gguf"))
    implementation(project(":observability:aqli-observability"))
    implementation(project(":repository:aqli-model-repo-hf"))
    implementation(project(":repository:aqli-model-repo-kaggle"))
    implementation(project(":repository:aqli-model-repo-local"))

    implementation(project(":plugins:log-parser"))
    implementation(project(":runner:litert:aqli-runner-litert"))
    implementation(project(":runner:onnx:aqli-runner-onnx"))
    implementation("tech.kayys.aljabr:aljabr-gguf-core:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-gguf-api:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-gguf-java:0.1.0-SNAPSHOT")
    implementation("tech.kayys:suling:0.1.0")
    implementation("com.google.ai.edge.litertlm:litertlm-jvm:0.11.0")
    implementation(project(":runner:safetensor:aqli-safetensor-engine"))
    implementation("tech.kayys.aljabr:aljabr-safetensor-loader:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-safetensor-spi:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-backend-metal:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-model-gemma:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-model-gemma4:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-model-qwen:0.1.0-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("src/main/java"))
        }
        resources {
            setSrcDirs(listOf("src/main/resources"))
        }
    }
}

tasks.processResources {
    filesMatching("META-INF/aqli-version.properties") {
        filter { line: String ->
            line.replace("\${project.version}", project.version.toString())
        }
    }
}

tasks.jar {
    archiveBaseName.set("aqli")
}

tasks.test {
    useJUnitPlatform()
}
