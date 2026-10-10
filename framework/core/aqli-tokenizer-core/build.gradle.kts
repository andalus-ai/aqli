plugins {
    `java-library`
}

dependencies {
    implementation("tech.kayys.alkhawarizm:alkhawarizm-tensor:0.1.0-SNAPSHOT")
    api("tech.kayys.alkhawarizm:alkhawarizm-spi-tokenizer:0.1.0-SNAPSHOT")
    implementation(project(":spi:aqli-spi"))
    implementation("tech.kayys.alkhawarizm:alkhawarizm-spi-model:0.1.0-SNAPSHOT")
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1")
    implementation("org.jboss.logging:jboss-logging:3.5.3.Final")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
}
