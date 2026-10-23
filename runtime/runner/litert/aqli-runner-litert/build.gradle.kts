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
    google()
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(project(":core:aqli-core"))
    implementation(project(":spi:aqli-spi"))
    implementation(project(":spi:aqli-spi-inference"))
    implementation("tech.kayys.aljabr:aljabr-spi-model:0.1.0-SNAPSHOT")
    implementation(project(":spi:aqli-spi-multimodal"))
    implementation("tech.kayys.aljabr:aljabr-error-code:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-tensor:0.1.0-SNAPSHOT")
    implementation(project(":tokenizer:aqli-tokenizer-core"))
    implementation("tech.kayys.aljabr:aljabr-backend-metal:0.1.0-SNAPSHOT")
    implementation("io.smallrye.reactive:mutiny:2.5.5")
    implementation("io.smallrye.config:smallrye-config:3.10.1")
    implementation("org.jboss.logging:jboss-logging:3.6.1.Final")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    runtimeOnly("com.google.ai.edge.litertlm:litertlm-jvm:0.11.0")
    compileOnly("jakarta.inject:jakarta.inject-api:2.0.1")
    compileOnly("io.quarkus:quarkus-arc")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("org.slf4j:slf4j-simple:2.0.12")
    testImplementation("tech.kayys.aljabr:aljabr-safetensor-loader:0.1.0-SNAPSHOT")
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
    maxHeapSize = "4g"
    jvmArgs(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED",
        "--add-modules=jdk.incubator.vector"
    )
}

tasks.register<JavaExec>("runLiteRtRegressionTests") {
    group = "verification"
    description = "Runs the focused native LiteRT regression tests without Gradle's Test reporting layer."
    dependsOn(tasks.named("testClasses"))
    mainClass.set("tech.kayys.aqli.provider.litert.LiteRTRegressionMain")
    classpath = sourceSets["test"].runtimeClasspath
    jvmArgs(
        "-Xmx4g",
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED",
        "--add-modules=jdk.incubator.vector"
    )
}
