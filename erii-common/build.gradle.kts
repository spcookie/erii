plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

dependencies {
    compileOnly(libs.mirai.overflow)
    compileOnly(libs.koog.agents)
    compileOnly(libs.typesafe.config)
    compileOnly(libs.koin.core)
    compileOnly(libs.snakeyaml)
    compileOnly(libs.exposed.core)
    compileOnly(libs.exposed.jdbc)
    compileOnly(libs.exposed.dao)
    compileOnly(libs.exposed.json)
    compileOnly(libs.exposed.kotlin.datetime)
}