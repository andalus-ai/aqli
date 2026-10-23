plugins {
    java
}

dependencies {
    implementation(project(":sdk:aqli-sdk-core"))
    implementation(project(":sdk:aqli-sdk-api"))
    implementation(project(":sdk:aqli-sdk-protobuf"))
    implementation("io.grpc:grpc-protobuf:1.64.0")
    implementation("io.grpc:grpc-stub:1.64.0")
    implementation("io.grpc:grpc-netty-shaded:1.64.0")
    implementation("tech.kayys.aljabr:aljabr-spi-model:0.1.0-SNAPSHOT")
    implementation(project(":spi:aqli-spi-inference"))
    implementation("io.smallrye.reactive:mutiny:2.5.5")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
}
