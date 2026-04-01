plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "uesugi"
version = "0.0.1"

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