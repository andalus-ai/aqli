plugins {
    java
    id("io.quarkus")
}

val quarkusVersion = rootProject.extra["quarkusVersion"] as String

group = "tech.kayys.aqli"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation(platform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
    implementation("io.quarkus:quarkus-grpc")
    implementation(project(":spi:aqli-spi"))
    implementation(project(":spi:aqli-spi-inference"))
    implementation(project(":core:aqli-core"))
    implementation(project(":tokenizer:aqli-tokenizer-core"))
    implementation(project(":repository:aqli-model-database"))
    implementation(project(":sdk:aqli-sdk-api"))
    implementation(project(":sdk:aqli-sdk-core"))
    implementation("tech.kayys.aljabr:aljabr-spi-model:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-core:0.1.0-SNAPSHOT")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
