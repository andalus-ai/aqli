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
    implementation("tech.kayys.aljabr:aljabr-nn:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-tensor:0.1.0-SNAPSHOT")
    implementation(project(":sdk:aqli-sdk-api"))
    implementation(project(":runner:litert:aqli-runner-litert"))
    // LiteRT's public SDK methods expose these request/response DTOs. Keep the
    // compatibility SPI on the published API classpath; its API dependency
    // also exposes the canonical Aljabr inference SPI to downstream consumers.
    api(project(":spi:aqli-spi-inference"))
    implementation(group = "com.fasterxml.jackson.core", name = "jackson-databind")
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter")
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

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
