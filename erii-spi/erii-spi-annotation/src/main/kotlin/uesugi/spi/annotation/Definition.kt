package uesugi.spi.annotation

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.SOURCE)
annotation class Definition(
    val pluginId: String = "",
    val version: String = "",
    val requires: String = "",
    val dependencies: String = "",
    val description: String = "",
    val provider: String = "",
    val license: String = ""
)
