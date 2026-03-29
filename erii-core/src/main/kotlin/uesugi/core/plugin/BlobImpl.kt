package uesugi.core.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.source
import uesugi.core.component.LocalObjectStorage
import uesugi.spi.Blob
import uesugi.spi.PluginDef
import java.io.InputStream

internal class BlobImpl(val defined: PluginDef) : Blob {

    private val default by lazy {
        LocalObjectStorage(
            baseDir = "./store/object/plugins".toPath().resolve(defined.name)
        )
    }

    override suspend fun get(path: Path): InputStream = withContext(Dispatchers.IO) {
        default.get(path).buffer().inputStream()
    }

    override suspend fun set(path: Path, value: InputStream) {
        withContext(Dispatchers.IO) {
            default.put(path, value.source())
        }
    }

    override suspend fun delete(path: Path) {
        withContext(Dispatchers.IO) {
            default.delete(path)
        }
    }

    override fun close() {
    }

}
