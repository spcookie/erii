package uesugi.plugins.user.game.steamwatcher

import org.mapdb.Serializer
import uesugi.toolkit.MapDB
import uesugi.toolkit.logger
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

object AvatarCache {

    private val log = logger()

    val cache = MapDB.Cache
        .hashMap("avatars")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.BYTE_ARRAY)
        .expireAfterCreate(1, TimeUnit.HOURS)
        .createOrOpen()


    fun getAvatarImage(url: String): BufferedImage? {
        try {
            val fileName = url.substringAfterLast('/').substringBeforeLast('_') + ".png"

            val bytes = cache.getOrElse(fileName) { null }
            if (bytes != null) {
                val bufferedImage = ImageIO.read(bytes.inputStream())
                log.debug("Loading avatar from cache: $fileName")
                return bufferedImage
            }


            log.info("Avatar cache miss, downloading from: $url")
            val image = ImageIO.read(URL(url))
            if (image != null) {
                try {
                    // Save the downloaded image to the cache as a PNG file
                    ByteArrayOutputStream().use {
                        ImageIO.write(image, "png", it)
                        cache.put(fileName, it.toByteArray())
                    }
                    log.info("Avatar cached successfully at: $fileName")
                } catch (e: Exception) {
                    log.warn("Failed to save avatar to cache: ${e.message}")
                }
            }
            return image
        } catch (e: Exception) {
            log.warn("Failed to get or download avatar from URL $url: ${e.message}")
            return null
        }
    }
}