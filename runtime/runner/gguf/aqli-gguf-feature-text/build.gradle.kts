plugins {
    java
}

dependencies {
    implementation(project(":core:aqli-core"))
    implementation(project(":spi:aqli-spi"))
    implementation(project(":spi:aqli-spi-runner"))
    implementation(project(":spi:aqli-spi-plugin"))
    implementation(project(":spi:aqli-spi-inference"))
    implementation(project(":runner:gguf:aqli-runner-gguf"))
    implementation(project(":runner:gguf:aqli-plugin-runner-gguf"))

    // Add other typical dependencies
    implementation("org.jboss.logging:jboss-logging")
    implementation("io.smallrye.reactive:mutiny:2.5.5")
    implementation("io.micrometer:micrometer-core:1.14.4")
    
    testImplementation(project(":sdk:aqli-sdk-core"))
}
