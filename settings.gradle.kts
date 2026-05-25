rootProject.name = "erii"

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

include("erii-common")
include("erii-spi")
include("erii-core")
include("erii-plugin-gradle")
include("erii-spi:erii-spi-core")