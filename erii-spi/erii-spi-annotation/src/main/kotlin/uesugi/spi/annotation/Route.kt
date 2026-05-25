package uesugi.spi.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Route(
    val method: String,
    val path: String,
    val toolSets: Array<String> = ["default"]
)
