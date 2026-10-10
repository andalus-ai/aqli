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

    implementation(project(":spi:aqli-spi-inference"))
    implementation("io.smallrye.reactive:mutiny:2.5.5")


    implementation(project(":spi:aqli-spi"))
    implementation(project(":spi:aqli-spi-plugin"))
    implementation(project(":plugin:aqli-plugin-optimization-core"))

    implementation(project(":core:aqli-core"))
    implementation("tech.kayys.alkhawarizm:alkhawarizm-error-code:0.1.0-SNAPSHOT")
    implementation(project(":core:aqli-core"))
    implementation("tech.kayys.alkhawarizm:alkhawarizm-tensor:0.1.0-SNAPSHOT")
    implementation("tech.kayys.alkhawarizm:alkhawarizm-core:0.1.0-SNAPSHOT")

    implementation("tech.kayys.alkhawarizm:alkhawarizm-spi-model:0.1.0-SNAPSHOT")
    implementation(project(":optimization:aqli-plugin-kv-cache"))
    
    implementation(group = "io.quarkus", name = "quarkus-rest")
    implementation(group = "io.quarkus", name = "quarkus-arc")
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
