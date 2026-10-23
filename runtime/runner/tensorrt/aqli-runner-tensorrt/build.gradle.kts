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
    implementation("tech.kayys.aljabr:aljabr-spi-model:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-core:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-tensor:0.1.0-SNAPSHOT")
    implementation(project(":spi:aqli-spi"))
    implementation(project(":spi:aqli-spi-inference"))
    implementation("tech.kayys.aljabr:aljabr-error-code:0.1.0-SNAPSHOT")
    implementation(project(":core:aqli-core"))
    implementation(project(":optimization:aqli-plugin-kv-cache"))
    implementation(group = "io.quarkus", name = "quarkus-arc")
    implementation(group = "io.smallrye.reactive", name = "mutiny")
    compileOnly(group = "org.jboss.logging", name = "jboss-logging")
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
