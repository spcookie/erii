package uesugi.core.state.meme

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext
import uesugi.config.LLMModelsChoice
import uesugi.toolkit.logger

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
     * @return 表情包分析结果
     */
    suspend fun analyzeMeme(contexts: List<String>): MemoAnalysis? {
        if (contexts.isEmpty()) {
            log.debug("上下文为空，无法分析表情包")
            return null
        }

        val contextText = contexts.joinToString("\n---\n")

        log.debug("开始分析表情包, 上下文数量=${contexts.size}")

        return try {
            log.debug("调用 LLM 执行表情包分析...")

            val userPromptObj = prompt("分析表情包") {
                system(
                    """
                    你是一名**表情包分析专家**。

                    任务：根据以下表情包在群聊中出现的上下文，分析表情包的：

                    1. `description`：用 1-2 句话描述这个表情包的内容/画面/含义
                    2. `purpose`：表情包的典型使用场景或用途（如"用于嘲讽"、"用于打招呼"、"用于表达无语"、"用于炫耀"等）
                    3. `tags`：3-5 个关键词标签

                    请直接输出 JSON，不要添加额外说明。
                    """.trimIndent()
                )
                user(
                    """
                    表情包出现的上下文:
                    $contextText
                    """.trimIndent()
                )
            }

            val promptExecutor: PromptExecutor by GlobalContext.get().inject()

            val result = promptExecutor.executeStructured<MemoAnalysis>(
                prompt = userPromptObj,
                model = LLMModelsChoice.Flash,
                fixingParser = StructureFixingParser(
                    model = LLMModelsChoice.Lite,
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

    /**
     * 将用户输入的搜索文本转换为表情包描述
     *
     * @param userQuery 用户输入的搜索文本（如"搞笑的表情包"）
     * @return 转换后的描述关键词
     */
    suspend fun transformSearchQuery(userQuery: String): String? {
        if (userQuery.isBlank()) {
            return null
        }

        log.debug("转换搜索查询: $userQuery")

        return try {
            val userPromptObj = prompt("转换搜索查询") {
                system(
                    """
                    你是一个搜索关键词转换器。

                    任务：将用户输入的搜索文本转换为表情包描述关键词。

                    例如：
                    - 输入"搞笑的表情包" -> 输出"搞笑、幽默、逗趣"
                    - 输入"可以用来嘲讽别人的" -> 输出"嘲讽、讽刺、挖苦"
                    - 输入"表示无语" -> 输出"无语、无奈、汗"

                    直接输出关键词，用顿号分隔，不要有额外说明。
                    """.trimIndent()
                )
                user(userQuery)
            }

            val promptExecutor: PromptExecutor by GlobalContext.get().inject()

            val result = promptExecutor.execute(
                prompt = userPromptObj,
                model = LLMModelsChoice.Flash
            )

            val response = result.first()
            log.debug("查询转换结果: {}", response)
            response.content
        } catch (e: Exception) {
            log.error("查询转换失败", e)
            userQuery // 失败时返回原始查询
        }
    }
}
