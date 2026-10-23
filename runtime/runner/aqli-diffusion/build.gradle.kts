plugins {
    java
}

dependencies {
    implementation("tech.kayys.aljabr:aljabr-tensor:0.1.0-SNAPSHOT")
    implementation("tech.kayys.aljabr:aljabr-3d:0.1.0-SNAPSHOT")
    implementation(project(":core:aqli-core"))
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}
