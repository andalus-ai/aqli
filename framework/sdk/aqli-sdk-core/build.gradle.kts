plugins {
    java
}

dependencies {
    implementation("tech.kayys.aljabr:aljabr-tensor:0.1.0-SNAPSHOT")
    implementation(project(":sdk:aqli-sdk-api"))
    implementation(project(":core:aqli-core"))
    implementation(project(":plugin:aqli-plugin-kernel-core"))
    implementation(project(":spi:aqli-spi"))
    implementation(project(":spi:aqli-spi-inference"))
    implementation("tech.kayys.aljabr:aljabr-spi-model:0.1.0-SNAPSHOT")
    implementation(project(":spi:aqli-spi-multimodal"))
    implementation(project(":spi:aqli-spi-plugin"))
    
    implementation("io.smallrye.reactive:mutiny:2.5.5")
    implementation("org.jboss.logging:jboss-logging:3.6.1.Final")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1")
}
