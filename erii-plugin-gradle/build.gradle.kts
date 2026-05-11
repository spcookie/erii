plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    `maven-publish`
}

group = "uesugi"
version = "0.0.1"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.2.21")
}

gradlePlugin {
    plugins {
        create("eriiPlugin") {
            id = "uesugi.erii-plugin"
            implementationClass = "uesugi.gradle.EriiGradlePlugin"
        }
    }
}
