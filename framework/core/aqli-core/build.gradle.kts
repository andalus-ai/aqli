plugins {
    java
}

// Read Feature Flags from gradle.properties or command line
val backends: List<String> = project.findProperty("aqli.backend")?.toString()?.split(",") ?: listOf("cpu")

val enableInference: Boolean = project.findProperty("aqli.inference")?.toString()?.toBoolean() ?: true

println("⚙️ Configuring aqli-core build:")
println("   - Backends: $backends")
println("   - Inference Enabled: $enableInference")

val quarkusVersion = rootProject.extra["quarkusVersion"] as String

dependencies {
    implementation(platform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
    implementation("io.quarkus:quarkus-arc")
    implementation("io.smallrye.config:smallrye-config:3.10.1")
    implementation("tech.kayys.aljabr:aljabr-tensor:0.1.0-SNAPSHOT")
    // Optional intra-build project dependencies — only add if those projects are present
    val spiAqli = findProject(":spi:aqli-spi")
    if (spiAqli != null) {
        add("implementation", spiAqli)
    } else {
        println("   [Warning] spi:aqli-spi not present; skipping project dependency")
    }
    val spiInference = findProject(":spi:aqli-spi-inference")
    if (spiInference != null) {
        add("implementation", spiInference)
    } else {
        println("   [Warning] spi:aqli-spi-inference not present; skipping project dependency")
    }
    val spiModel = findProject(":spi:aqli-spi-model")
    if (spiModel != null) {
        add("implementation", spiModel)
    } else {
        println("   [Warning] spi:aqli-spi-model not present; skipping project dependency")
    }
    // Depend on published alkhawarizm core aggregator for shared math/tensor/tokenizer foundations
    implementation("tech.kayys.aljabr:aljabr-core:0.1.0-SNAPSHOT")
    
    implementation("io.smallrye.reactive:mutiny:2.5.5")
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.2")
}

tasks.test {
    useJUnitPlatform()
}

// Dynamically include/exclude packages based on feature flags
sourceSets {
    main {
        java {


            // Exclude inference/runner modules if not needed
            if (!enableInference) {
                println("   [Optimizer] Excluded 'runtime' and 'diffusion' packages.")
                exclude("tech/kayys/aqli/runtime/**")
                exclude("tech/kayys/aqli/diffusion/**")
            }

            // Exclude backend implementations that are not requested
            if (!backends.contains("cuda")) {
                println("   [Optimizer] Excluded 'backend/cuda' package.")
                exclude("tech/kayys/aqli/backend/cuda/**")
            }
            if (!backends.contains("metal")) {
                println("   [Optimizer] Excluded 'backend/metal' package.")
                exclude("tech/kayys/aqli/backend/metal/**")
            }
            if (!backends.contains("cpu")) {
                println("   [Optimizer] Excluded 'backend/cpu' package.")
                exclude("tech/kayys/aqli/backend/cpu/**")
            }
        }
    }
}
