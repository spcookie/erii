plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    compileOnly(project(":erii-common"))
    compileOnly(libs.pf4j)
    compileOnly(libs.mirai.overflow)
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
    compileOnly(libs.typesafe.config)
}