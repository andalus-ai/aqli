plugins {
    `java-library`
}

dependencies {
    api("tech.kayys.alkhawarizm:alkhawarizm-spi-plugin:0.1.0-SNAPSHOT")
    implementation(project(":spi:aqli-spi"))
    implementation(project(":spi:aqli-spi-inference"))
    implementation("io.smallrye.reactive:mutiny:2.5.5")
    implementation("org.slf4j:slf4j-api:2.0.12")
}
