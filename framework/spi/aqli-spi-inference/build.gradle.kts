plugins {
    `java-library`
}

dependencies {
    api("tech.kayys.aljabr:aljabr-spi-inference:0.1.0-SNAPSHOT")
    implementation(project(":spi:aqli-spi"))
    implementation("tech.kayys.aljabr:aljabr-error-code:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-spi-model:0.1.0-SNAPSHOT")
    implementation(project(":spi:aqli-spi-multimodal"))
    implementation("io.smallrye.reactive:mutiny:2.5.5")
    implementation("org.reactivestreams:reactive-streams:1.0.4")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.jetbrains:annotations:24.0.1")
    
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
