plugins {
    alias(libs.plugins.kotlin.kapt)
}

dependencies {
    kapt(project(":erii-spi"))
    kapt(libs.pf4j)
}
