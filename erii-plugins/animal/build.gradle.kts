plugins {
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "uesugi.plugin"
version = "0.0.1"

dependencies {
    kapt(project(":erii-spi"))
    kapt(libs.pf4j)
    implementation("com.github.f4b6a3:tsid-creator:5.1.1")
}
