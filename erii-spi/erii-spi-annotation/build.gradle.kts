plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "uesugi"
version = "1.0.0"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

dependencies {
    implementation(project(":erii-spi:erii-spi-core"))
    implementation(libs.ksp.api)
    compileOnly(libs.koog.agents)
    compileOnly(libs.pf4j)
    compileOnly(libs.ktor.server.core)
    compileOnly(libs.ktor.client.core)
    compileOnly(libs.clikt)
    compileOnly(libs.okio)
    compileOnly(libs.exposed.core)
    compileOnly(libs.exposed.jdbc)
    compileOnly(libs.typesafe.config)
}
