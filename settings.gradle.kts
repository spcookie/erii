rootProject.name = "erii"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("erii-common")
include("erii-spi")
include("erii-core")
include("erii-plugins")
include("erii-plugins:speech")

include("erii-plugins:lolisuki")
include("erii-plugins:seeddream")
include("erii-plugins:qa")
include("erii-plugins:qq-face")
include("erii-plugins:net-ease-music")
include("erii-plugins:reminder")