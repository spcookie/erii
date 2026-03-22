plugins {
    alias(libs.plugins.kotlin.kapt)
}

dependencies {
    kapt(libs.pf4j)
    implementation(libs.google.genai)
    implementation(libs.kotlinx.coroutines.core)
}
