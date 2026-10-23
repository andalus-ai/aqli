plugins {
    java
}

group = "tech.kayys.aqli"
version = rootProject.version

dependencies {
    implementation(project(":sdk:aqli-sdk-api"))
    implementation("tech.kayys.aljabr:aljabr-safetensor-quantization:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-safetensor-core:0.1.0-SNAPSHOT")
    implementation("io.smallrye.reactive:mutiny:2.5.5")
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1")
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
    implementation("org.jboss.logging:jboss-logging:3.5.3.Final")
}
