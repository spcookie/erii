package uesugi.spi

/**
 * 插件定义注解，用于在编译时生成 plugin.properties 文件
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class PluginDefinition(
    val pluginId: String = "",
    val version: String = "",
    val requires: String = "",
    val dependencies: String = "",
    val description: String = "",
    val provider: String = "",
    val license: String = ""
)