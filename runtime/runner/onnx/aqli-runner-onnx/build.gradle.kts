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
    modularity.inferModulePath.set(false)
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("tech.kayys.alkhawarizm:alkhawarizm-error-code:0.1.0-SNAPSHOT")
    implementation("tech.kayys.alkhawarizm:alkhawarizm-error-code:0.1.0-SNAPSHOT")
    implementation("tech.kayys.alkhawarizm:alkhawarizm-spi-model:0.1.0-SNAPSHOT")
    implementation("tech.kayys.alkhawarizm:alkhawarizm-tensor:0.1.0-SNAPSHOT")
    implementation(project(":spi:aqli-spi"))
    implementation(project(":spi:aqli-spi-image"))
    implementation(project(":spi:aqli-spi-runner"))
    implementation("tech.kayys.alkhawarizm:alkhawarizm-model-flux:0.1.0-SNAPSHOT")
    implementation("tech.kayys:suling:0.1.0")
    implementation(project(":spi:aqli-spi-multimodal"))
    implementation(project(":core:aqli-core"))
    implementation(project(":spi:aqli-spi-inference"))
    implementation(project(":tokenizer:aqli-tokenizer-core"))
    implementation(project(":optimization:aqli-plugin-kv-cache"))
    implementation(group = "com.fasterxml.jackson.core", name = "jackson-databind")
    implementation(group = "com.microsoft.onnxruntime", name = "onnxruntime", version = "1.19.2")
    implementation(group = "io.quarkus", name = "quarkus-arc")
    implementation(group = "io.smallrye.reactive", name = "mutiny")
    compileOnly(group = "org.jboss.logging", name = "jboss-logging")
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter")
    testImplementation(project(":repository:aqli-model-repo-hf"))
    testImplementation(project(":core:aqli-core"))
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
