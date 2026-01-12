package plugins.steamwatcher

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import uesugi.toolkit.logger
import java.awt.image.BufferedImage
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

object AvatarCache {

    private val log = logger()

    val cache: Cache<String, BufferedImage> = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build()


    fun getAvatarImage(url: String): BufferedImage? {
        try {
            val fileName = url.substringAfterLast('/').substringBeforeLast('_') + ".png"

            val bufferedImage = cache.getIfPresent(fileName)
            if (bufferedImage != null) {
                log.debug("Loading avatar from cache: $fileName")
                return bufferedImage
            }


            log.info("Avatar cache miss, downloading from: $url")
            val image = ImageIO.read(URL(url))
            if (image != null) {
                try {
                    // Save the downloaded image to the cache as a PNG file
                    cache.put(fileName, image)
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