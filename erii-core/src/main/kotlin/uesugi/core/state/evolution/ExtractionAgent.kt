package uesugi.core.state.evolution

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext
import uesugi.common.LLMModelChoice
import uesugi.common.toolkit.logger
import kotlin.time.ExperimentalTime

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
    @OptIn(ExperimentalTime::class)
    suspend fun extractSlangWords(messages: List<String>): List<SlangWord> {
        if (messages.isEmpty()) {
            log.debug("消息列表为空，无法提取流行语")
            return emptyList()
        }

        log.debug("开始提取流行语, 消息数=${messages.size}")

        val messagesText = messages.joinToString("\n")

        return try {
            log.debug("调用 LLM 执行流行语提取...")

            val userPromptObj = prompt("__evolution_slang_extract__", LLMParams(maxTokens = 65536)) {
                system(
                    """
                    你是一名**群聊热词 / 梗 / 口头禅分析专家**，擅长从群聊记录中识别群友最近真实使用的特色表达。

                    # Task

                    请根据我提供的【群聊记录】，提取出 **0~10 个** 群友最近常用的：

                    * 口头禅
                    * 流行语
                    * 网络梗
                    * 黑话
                    * 方言特色表达
                    * 群内特有昵称
                    * 群内反复使用的特殊称呼或固定说法

                    只提取**有明显群聊特色、语境特色或梗属性**的词语。

                    # Extraction Rules

                    ## 必须满足

                    一个词语必须至少满足以下条件之一，才允许提取：

                    1. 在群聊中多次出现，且不是普通日常词。
                    2. 明显属于网络流行语、梗、黑话、抽象话、贴吧/二次元/游戏/技术圈特有表达。
                    3. 是群内成员之间反复使用的特殊昵称、外号、代称。
                    4. 在上下文中具有特殊含义，而不是字面普通含义。
                    5. 是群友表达情绪、调侃、阴阳怪气、玩梗时常用的固定说法。

                    ## 必须排除

                    以下内容不要提取：

                    1. **纯数字、QQ号、用户ID、群号、消息ID**
                       例如：`1253925634`、`130`、`269`、`2125232116`

                    2. **普通日常用语**
                       例如：吃饭、睡觉、早安、谢谢、再见、啥、什么、可以、不是、真的、感觉

                    3. **普通单字、普通短词、无特殊语义的词**
                       例如：好、行、来、看、整、搞、状态、列表

                    4. **机器人命令、系统命令、接口参数、路径**
                       例如：`/status`、`/list`、`/farm`、`/usage`

                    5. **普通英文单词或功能词**
                       例如：animal、status、farm、list、config、help
                       除非它们在群聊中被明确当成梗、外号或黑话使用。

                    6. **普通技术名词、型号、缩写**
                       例如：STM32、32、Java、Spring、Linux
                       除非它们在群聊中明显被玩梗或作为群内黑话使用。

                    7. **无法从上下文确认含义的词**
                       不要强行猜测。
                       如果只能写”可能是””也许是””疑似”，则不要提取。

                    8. **只出现一次且没有明显梗属性的词**
                       即使看起来有点特别，也不要提取。

                    # Quality Rules

                    1. 宁缺毋滥。没有合适词语时，返回空数组 `[]`。
                    2. 不要为了凑满 10 个而提取低质量词。
                    3. `meaning` 必须基于群聊上下文，不允许凭空解释。
                    4. `example` 必须来自群聊原文，不能改写、编造。
                    5. 对昵称类词语，只有在它明显是群友之间的称呼、外号、代称时才提取。
                    6. 对命令类文本，默认排除，除非它已经脱离命令功能，被群友当作梗来使用。
                    7. 对数字类文本，默认排除，除非它是明确的梗，例如”114514”这类有固定网络语义的数字梗。
                    8. 对普通词，如果没有特殊语境含义，不要提取。

                    # Output Format

                    请直接输出 JSON 数组，不要添加任何额外说明、Markdown 或解释。

                    每个元素格式如下：

                    [
                      {
                        “word”: “词语本身”,
                        “type”: “slang | meme | catchphrase | nickname | dialect | abbreviation | vulgar_slang | internet_phrase | group_jargon”,
                        “meaning”: “结合群聊上下文给出的简明解释”,
                        “example”: “群聊中的原始示例句”
                      }
                    ]
                    """.trimIndent()
                )
                user {
                    text(
                        """
                    群聊记录如下：
                    """.trimIndent()
                    )
                    text(messagesText)
                }
            }

            val promptExecutor: PromptExecutor by GlobalContext.get().inject()

            val result = promptExecutor.executeStructured<SlangWordList>(
                prompt = userPromptObj,
                model = LLMModelChoice.Pro,
                fixingParser = StructureFixingParser(
                    model = LLMModelChoice.Lite,
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
