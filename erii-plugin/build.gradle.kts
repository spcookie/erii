plugins {
    alias(libs.plugins.kotlin.jvm)
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(project(":erii-spi"))
        compileOnly("org.pf4j:pf4j:3.15.0")
    }
}