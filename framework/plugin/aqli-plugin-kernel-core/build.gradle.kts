plugins {
    java
}

dependencies {
    implementation("tech.kayys.aljabr:aljabr-tensor:0.1.0-SNAPSHOT")
    implementation(project(":spi:aqli-spi"))
    implementation(project(":spi:aqli-spi-plugin"))
    implementation("org.jboss.logging:jboss-logging:3.5.3.Final")
    implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1")
    implementation("jakarta.inject:jakarta.inject-api:2.0.1")
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
}
