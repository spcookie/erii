plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
}

group = "uesugi"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    kapt("org.pf4j:pf4j:3.15.0")
    implementation("org.pf4j:pf4j:3.15.0")
    compileOnly(project(":erii-common"))
    compileOnly(libs.jobrunr)
    compileOnly(libs.clikt)
    compileOnly(libs.okio)
    compileOnly(libs.koog.agents)
    compileOnly(libs.exposed.core)
    compileOnly(libs.exposed.jdbc)
    compileOnly(libs.ktor.server.core)
    compileOnly(libs.ktor.client.serialization.jackson)
    compileOnly(libs.ktor.server.content.negotiation)
    compileOnly(libs.ktor.server.netty)
    compileOnly(libs.ktor.client.core)
    compileOnly(libs.clikt)
    compileOnly(libs.typesafe.config)
}