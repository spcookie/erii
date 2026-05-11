plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "uesugi"
version = "0.0.1"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

dependencies {
    compileOnly(project(":erii-common"))
    compileOnly(libs.pf4j)
    compileOnly(libs.mirai.overflow)
    compileOnly(libs.clikt)
    compileOnly(libs.okio)
    compileOnly(libs.koog.agents)
    compileOnly(libs.exposed.core)
    compileOnly(libs.exposed.jdbc)
    compileOnly(libs.ktor.server.core)
    compileOnly(libs.ktor.client.core)
    compileOnly(libs.typesafe.config)
}