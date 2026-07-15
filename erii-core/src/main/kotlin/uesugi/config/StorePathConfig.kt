package uesugi.config

import okio.Path
import okio.Path.Companion.toPath
import java.nio.file.Paths
import java.nio.file.Path as NioPath

object StorePathConfig {
    const val JVM_PROPERTY = "erii.store.dir"
    const val ENV_VAR = "ERII_STORE_DIR"
    const val DEFAULT_DIR = "./store"

    private val baseDirValue by lazy {
        System.getProperty(JVM_PROPERTY)?.trim()?.takeIf { it.isNotEmpty() }
            ?: System.getenv(ENV_VAR)?.trim()?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_DIR
    }

    fun resolve(vararg segments: String): NioPath =
        segments.fold(Paths.get(baseDirValue)) { path, segment -> path.resolve(segment) }

    fun resolveOkio(vararg segments: String): Path =
        segments.fold(baseDirValue.toPath()) { path, segment -> path.resolve(segment) }

    fun h2JdbcUrl(options: String): String = "jdbc:h2:file:${resolve("data")};$options"
}
