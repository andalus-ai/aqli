plugins {
    `java-library`
}

group = "tech.kayys.aqli"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    // Model metadata is owned by Aljabr; the SDK API only re-exports its
    // stable model SPI and has no dependency on a runner or transport.
    api("tech.kayys.aljabr:aljabr-spi-model:0.1.0-SNAPSHOT")
}
