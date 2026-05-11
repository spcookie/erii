pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "erii-plugins"

include("lolisuki")
include("animal")
include("net-ease-music")
include("qq-face")
include("rollpig")
include("seeddream")
include("speech")
