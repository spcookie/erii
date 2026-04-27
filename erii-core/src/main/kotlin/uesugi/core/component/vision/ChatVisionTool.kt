package uesugi.core.component.vision

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import okio.buffer
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.data.HistoryEntity
import uesugi.common.toolkit.ref
import uesugi.core.component.storage.ObjectStorage
import java.util.*

object ChatVisionTool : ToolSet {

    private val objectStorage by ref<ObjectStorage>()

    @Tool
    @LLMDescription(
        """
    每当您需要分析、描述或从图像中提取信息时，都必须使用此工具，
    包括当您从用户输入或任何与任务相关的图像中获取图像时。

    一款由大语言模型 (LLM) 驱动的视觉工具，可以根据您的指令分析和解读本地文件或 URL 中的图像内容。
    仅支持 JPEG、PNG 和 WebP 格式。不支持其他格式（例如 PDF、GIF、PSD、SVG）。
    """
    )
    suspend fun understandImage(
        @LLMDescription("用于描述您希望从图像中分析或提取的内容的文本提示") prompt: String,
        @LLMDescription("图像的 ID，从聊天信息中获取") imageId: String
    ): String {
        return withContext(Dispatchers.IO) {
            val path = transaction {
                HistoryEntity.findById(imageId.toInt())
                    ?.resource?.url
                    ?.toPath()
            }

            if (path == null) {
                return@withContext "根据图像ID $imageId 未获取图像信息"
            }

            val bytes = objectStorage.get(path)
                .buffer()
                .readByteArray()
            val base64 = Base64.getEncoder().encodeToString(bytes)
            val extension = path.name.substringAfterLast(".").lowercase()
            val mimeType = when (extension) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            val imageData = "data:$mimeType;base64,$base64"
            VisionManager.get().vision(prompt, imageData)
        }
    }

}