package uesugi.core.evolution

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext
import uesugi.toolkit.logger

/**
 * 提炼层服务 - 梗与黑话提取引擎 (The Extractor)
 *
 * 使用 LLM 周期性总结，从群聊记录中提取流行语、梗、黑话
 * 这是"模因进化系统"的核心部分
 */
class ExtractionAgent {

    companion object {
        private val log = logger()
    }

    @Serializable
    data class SlangWordList(
        val words: List<SlangWord>
    )

    /**
     * 从群聊消息中提取流行语
     *
     * 使用 LLM 分析最近的聊天记录，识别出群友喜欢用的口头禅、梗、黑话
     * 排除普通日常用语，只提取有特色的网络用语
     *
     * @param messages 群聊消息列表
     * @return 提取到的流行语列表，通常 3-5 个
     */
    suspend fun extractSlangWords(messages: List<String>): List<SlangWord> {
        if (messages.isEmpty()) {
            log.debug("消息列表为空，无法提取流行语")
            return emptyList()
        }

        log.debug("开始提取流行语, 消息数=${messages.size}")

        val messagesText = messages.joinToString("\n")

        return try {
            log.debug("调用 LLM 执行流行语提取...")

            val userPromptObj = prompt("提取群聊流行语") {
                system(
                    """
                    你是一名**群聊用语分析专家**。
                    
                    任务：根据以下群聊记录，提取出 **0~15 个群友最近常用的口头禅、流行语、梗、黑话或昵称**，要求如下：
                    
                    1. 排除普通日常用语（如“吃饭”“睡觉”“早安”“谢谢”“再见”等）。
                    2. 仅提取有特色的网络用语、梗、方言、特有昵称或黑话。
                    3. 对每个词，提供以下信息：
                    
                       * `word`：词语本身
                       * `type`：词性，如 `adjective`（形容词）、`exclamation`（感叹词）、`noun`（名词）、`nickname`（昵称）等
                       * `meaning`：简明解释
                       * `example`：在群聊中实际使用的示例
                    
                    请直接输出 JSON，不要添加额外说明。
                    """.trimIndent()
                )
                user(
                    """
                群聊记录如下：
                $messagesText
                """.trimIndent()
                )
            }

            val promptExecutor: PromptExecutor by GlobalContext.get().inject()

            val result = promptExecutor.executeStructured<SlangWordList>(
                prompt = userPromptObj,
                model = GoogleModels.Gemini2_5Flash,
                fixingParser = StructureFixingParser(
                    model = GoogleModels.Gemini2_5FlashLite,
                    retries = 2
                )
            )

            val slangWords = result.getOrThrow().data.words
            log.debug("流行语提取成功, 提取数量=${slangWords.size}, 词汇=${slangWords.joinToString(", ") { word -> word.word }}")
            slangWords
        } catch (e: Exception) {
            log.error("提取流行语失败", e)
            emptyList()
        }
    }
}