plugins {
    `java-library`
    `maven-publish`
}

group = "tech.kayys.aqli"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(project(":plugin:aqli-plugin-runner-core"))
    implementation(project(":spi:aqli-spi-inference"))
    implementation("tech.kayys.aljabr:aljabr-safetensor-spi:0.1.0-SNAPSHOT")
    implementation(project(":runner:safetensor:aqli-safetensor-engine"))
    implementation("tech.kayys.aljabr:aljabr-safetensor-core:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-safetensor-api:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-safetensor-quantization:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-spi-model:0.1.0-SNAPSHOT")
    implementation(project(":spi:aqli-spi-multimodal"))
    implementation(project(":core:aqli-core"))
    implementation("tech.kayys.tafkir:tafkir-quantizer-gptq:0.1.0-SNAPSHOT")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("io.smallrye.reactive:mutiny:2.5.5")
    implementation("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1")
    implementation("org.jboss.logging:jboss-logging:3.5.3.Final")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}
