plugins {
    `java-library`
}

val quarkusVersion = rootProject.extra["quarkusVersion"] as String

dependencies {
    api(platform("io.quarkus.platform:quarkus-bom:$quarkusVersion"))
    // This is deliberately a CLI-only SPI. Picocli types must never cross into SDK APIs.
    api("info.picocli:picocli")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
