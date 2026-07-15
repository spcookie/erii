package uesugi.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path
import okio.buffer
import okio.source
import uesugi.config.StorePathConfig
import uesugi.core.component.storage.LocalObjectStorage
import uesugi.spi.Blob
import uesugi.spi.PluginDef
import java.io.InputStream

internal class BlobImpl(val defined: PluginDef) : Blob {

    private val default by lazy {
        LocalObjectStorage(
            baseDir = StorePathConfig.resolveOkio("object", "plugins", defined.name)
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
