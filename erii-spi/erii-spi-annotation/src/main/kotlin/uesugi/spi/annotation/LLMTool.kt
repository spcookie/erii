package uesugi.spi.annotation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class LLMTool(
    val name: String = "",
    val set: String = "default"
)
