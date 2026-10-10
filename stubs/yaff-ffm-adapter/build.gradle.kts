plugins {
    `java`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

group = "tech.kayys.wayang"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

// No extra resolution tweaks needed for this prototype

tasks.withType<Test> {
    useJUnitPlatform()
}
