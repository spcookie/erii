plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    `maven-publish`
}

group = "uesugi"
version = "1.0.0"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.3.20")
    implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.8")
}

gradlePlugin {
    plugins {
        create("eriiPlugin") {
            id = "uesugi.erii-plugin"
            implementationClass = "uesugi.gradle.EriiGradlePlugin"
        }
    }
}
