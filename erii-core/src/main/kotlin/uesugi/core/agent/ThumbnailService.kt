package uesugi.core.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.coobird.thumbnailator.Thumbnails
import okio.Buffer
import okio.Path.Companion.toPath
import okio.buffer
import org.mapdb.Serializer
import uesugi.common.data.ResourceRecord
import uesugi.core.component.storage.MapDB
import uesugi.core.component.storage.ObjectStorage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class ThumbnailService(
    private val objectStorage: ObjectStorage,
    private val maxWidth: Int = 1024,
    private val maxHeight: Int = 1024,
) {

    private val pathMap by lazy {
        MapDB.Cache.hashMap("image_thumbnail_path")
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.STRING)
            .createOrOpen()
    }

    suspend fun getThumbnail(resource: ResourceRecord): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val originalPath = resource.url
            val cachedThumbPath = pathMap[originalPath]
            if (cachedThumbPath != null && objectStorage.exists(cachedThumbPath.toPath())) {
                return@withContext objectStorage.get(cachedThumbPath.toPath()).buffer().readByteArray()
            }

            val originalBytes = objectStorage.get(originalPath.toPath()).buffer().readByteArray()
            val format = extractImageFormat(resource.fileName)
            val thumbBytes = generateThumbnail(originalBytes, format)
                ?: return@withContext null

            val thumbPath = "./thumbnails/${md5(originalPath)}.$format"
            objectStorage.put(thumbPath.toPath(), Buffer().write(thumbBytes))
            pathMap[originalPath] = thumbPath

            thumbBytes
        }.getOrNull()
    }

    private fun generateThumbnail(bytes: ByteArray, format: String): ByteArray? {
        return try {
            writeThumbnail(bytes, format)
        } catch (_: Exception) {
            // 原格式不支持时回退到 png
            runCatching {
                writeThumbnail(bytes, "png")
            }.getOrNull()
        }
    }

    private fun writeThumbnail(bytes: ByteArray, format: String): ByteArray {
        val output = ByteArrayOutputStream()
        Thumbnails.of(ByteArrayInputStream(bytes))
            .size(maxWidth, maxHeight)
            .outputFormat(format)
            .toOutputStream(output)
        return output.toByteArray()
    }

    private fun extractImageFormat(fileName: String): String {
        return fileName.substringAfterLast(".", "")
            .lowercase()
            .takeIf { it.isNotEmpty() } ?: "png"
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
