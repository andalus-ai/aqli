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
    implementation("tech.kayys.aljabr:aljabr-ml-autograd:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-ml-nn:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-tensor:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-ml-core:0.1.0-SNAPSHOT")
    implementation(project(":ml:aqli-ml-estimator"))
    implementation(project(":ml:aqli-ml-preprocessing"))
    implementation(project(":ml:aqli-ml-persistence"))
    implementation(group = "org.slf4j", name = "slf4j-api", version = "2.0.12")
    compileOnly(group = "io.quarkus", name = "quarkus-core")
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.10.2")
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

tasks.jar {
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to "Aqli Pickle SDK",
                "Implementation-Version" to "0.1.0-SNAPSHOT"
            )
        )
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
