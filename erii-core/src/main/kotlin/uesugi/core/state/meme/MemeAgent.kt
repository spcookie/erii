package uesugi.core.state.meme

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext
import uesugi.common.LLMModelChoice
import uesugi.common.toolkit.logger
import kotlin.time.ExperimentalTime

/**
 * 表情包分析 Agent
 *
 * 使用 LLM 分析表情包的描述、用途、标签
 */
class MemeAgent {

    companion object {
        private val log = logger()
    }

    @Serializable
    data class MemoAnalysis(
        val description: String,  // 描述
        val purpose: String,       // 用途
        val tags: List<String>     // 标签
    )

    /**
     * 分析表情包内容
     *
     * @param contexts 表情包在群聊中出现的上下文列表
     * @param imageBytes 表情包图片字节（可选，仅视觉模型使用）
     * @param imageFormat 图片格式如 "png"、"gif" 等（可选）
     * @return 表情包分析结果
     */
    @OptIn(ExperimentalTime::class)
    suspend fun analyzeMeme(
        contexts: List<String>,
        imageBytes: ByteArray? = null,
        imageFormat: String? = null
    ): MemoAnalysis? {
        if (contexts.isEmpty()) {
            log.debug("上下文为空，无法分析表情包")
            return null
        }

        val contextText = contexts.joinToString("\n---\n")
        val supportsVision = LLMModelChoice.Pro.supports(LLMCapability.Vision.Image)
        val hasImage = supportsVision && imageBytes != null && imageFormat != null

        log.debug("开始分析表情包, 上下文数量=${contexts.size}, 视觉支持=$supportsVision, 有图片=$hasImage")

        return try {
            log.debug("调用 LLM 执行表情包分析...")

            val userPromptObj = prompt("__meme_analysis__", LLMParams(maxTokens = 65536)) {
                system(
                    """
                    你是一名**表情包分析专家**。

                    任务：根据以下表情包在群聊中出现的上下文${if (hasImage) "以及表情包图片本身" else ""}，分析表情包的：

                    1. `description`：用 1-2 句话描述这个表情包的内容/画面/含义
                    2. `purpose`：表情包的典型使用场景或用途（如"用于嘲讽"、"用于打招呼"、"用于表达无语"、"用于炫耀"等）
                    3. `tags`：3-5 个关键词标签

                    请直接输出 JSON，不要添加额外说明。
                    """.trimIndent()
                )
                user {
                    if (hasImage) {
                        text("表情包图片:")
                        image(
                            AttachmentSource.Image(
                                content = AttachmentContent.Binary.Bytes(imageBytes),
                                format = imageFormat
                            )
                        )
                    }
                    text(
                        """
                    表情包出现的上下文:
                    """.trimIndent()
                    )
                    text(contextText)
                }
            }

            val promptExecutor: PromptExecutor by GlobalContext.get().inject()

            val result = promptExecutor.executeStructured<MemoAnalysis>(
                prompt = userPromptObj,
                model = LLMModelChoice.Pro,
                fixingParser = StructureFixingParser(
                    model = LLMModelChoice.Lite,
                    retries = 2
                )
            )

            val analysis = result.getOrThrow().data
            log.debug(
                "表情包分析成功: description={}, purpose={}, tags={}",
                analysis.description,
                analysis.purpose,
                analysis.tags
            )
            analysis
        } catch (e: Exception) {
            log.error("表情包分析失败", e)
            null
        }
    }

}
