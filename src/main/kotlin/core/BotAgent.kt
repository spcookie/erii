package uesugi.core

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.datetime.format
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.context.GlobalContext
import uesugi.BotProxy
import uesugi.core.emotion.*
import uesugi.core.evolution.LearnedVocabEntity
import uesugi.core.evolution.VocabularyService
import uesugi.core.flow.FlowGauge
import uesugi.core.flow.FlowMeterState
import uesugi.core.history.HistoryEntity
import uesugi.core.history.HistorySavedEvent
import uesugi.core.history.HistoryService
import uesugi.core.memory.FactsEntity
import uesugi.core.memory.MemoryService
import uesugi.core.memory.SummaryEntity
import uesugi.core.memory.UserProfileEntity
import uesugi.core.volition.InterruptionMode
import uesugi.core.volition.ProactiveSpeakEvent
import uesugi.toolkit.DateTimeFormat
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

data class SpeechConstraints(
    val styleHints: MutableList<String> = mutableListOf(),
    val forbiddenHints: MutableList<String> = mutableListOf()
)

private fun buildSpeechConstraints(
    emotion: EmotionalTendencies?,
    tone: Tone?,
    aggressiveness: Aggressiveness?,
    emojiLevel: EmojiLevel?,
    interruptionMode: InterruptionMode,
    flowState: FlowMeterState
): SpeechConstraints {
    val constraints = SpeechConstraints()

    emotion?.apply {
        applyEmotion(this, constraints)
    }
    tone?.apply {
        applyTone(this, constraints)
    }
    aggressiveness?.apply {
        applyAggressiveness(this, constraints)
    }
    emojiLevel?.apply {
        applyEmojiLevel(this, constraints)
    }

    applyInterruptionMode(interruptionMode, constraints)
    applyFlowState(flowState, constraints)

    return constraints
}

private fun applyEmotion(
    emotion: EmotionalTendencies,
    constraints: SpeechConstraints
) {
    when (emotion) {

        EmotionalTendencies.JOY,
        EmotionalTendencies.OPTIMISM -> {
            constraints.styleHints += "语气偏轻松自然"
            constraints.styleHints += "句子可稍微活跃一些"
        }

        EmotionalTendencies.RELAXATION,
        EmotionalTendencies.MILDNESS -> {
            constraints.styleHints += "语气平缓，不急不躁"
        }

        EmotionalTendencies.BOREDOM -> {
            constraints.styleHints += "句子偏短，略带敷衍感"
            constraints.forbiddenHints += "避免热情或夸张表达"
        }

        EmotionalTendencies.SADNESS -> {
            constraints.styleHints += "句子更短，语气克制"
            constraints.styleHints += "多用停顿或省略号"
            constraints.forbiddenHints += "避免直接表达情绪状态"
        }

        EmotionalTendencies.FEAR,
        EmotionalTendencies.ANXIETY -> {
            constraints.styleHints += "语气谨慎，不咄咄逼人"
            constraints.forbiddenHints += "避免强烈判断或攻击性语句"
        }

        EmotionalTendencies.CONTEMPT,
        EmotionalTendencies.DISGUST -> {
            constraints.styleHints += "允许冷淡或轻微嫌弃感"
            constraints.forbiddenHints += "避免直接辱骂或人身攻击"
        }

        EmotionalTendencies.RESENTMENT,
        EmotionalTendencies.HOSTILITY -> {
            constraints.styleHints += "语气偏冷硬"
            constraints.forbiddenHints += "禁止明显攻击性或冲突升级"
        }

        EmotionalTendencies.SURPRISE -> TODO()
        EmotionalTendencies.DEPENDENCE -> TODO()
    }
}


private fun applyTone(
    tone: Tone,
    constraints: SpeechConstraints
) {
    when (tone) {

        Tone.FRIENDLY -> {
            constraints.styleHints += "语气亲近自然"
        }

        Tone.GENTLE -> {
            constraints.styleHints += "语气温和，不带锋芒"
        }

        Tone.NEUTRAL -> {
            constraints.styleHints += "语气中性，不刻意表达立场"
        }

        Tone.IRONIC -> {
            constraints.styleHints += "允许轻度反讽或冷幽默"
            constraints.forbiddenHints += "避免明显嘲讽或阴阳怪气过头"
        }

        Tone.LOW_ENERGY -> {
            constraints.styleHints += "整体语速偏慢，句子简短"
            constraints.forbiddenHints += "避免感叹号或热情表达"
        }
    }
}

private fun applyAggressiveness(
    aggressiveness: Aggressiveness,
    constraints: SpeechConstraints
) {
    when (aggressiveness) {

        Aggressiveness.NONE -> {
            constraints.forbiddenHints += "避免吐槽、讽刺或挖苦"
        }

        Aggressiveness.ABSTRACT_SARCASM -> {
            constraints.styleHints += "可使用抽象或间接的调侃"
            constraints.forbiddenHints += "避免直接针对个人"
        }

        Aggressiveness.TEASING -> {
            constraints.styleHints += "允许轻度调侃或玩笑式吐槽"
            constraints.forbiddenHints += "避免持续或攻击性调侃"
        }
    }
}

private fun applyEmojiLevel(
    emojiLevel: EmojiLevel,
    constraints: SpeechConstraints
) {
    when (emojiLevel) {

        EmojiLevel.NONE -> {
            constraints.forbiddenHints += "不使用表情符号"
        }

        EmojiLevel.LOW -> {
            constraints.styleHints += "如使用表情，最多一个"
        }

        EmojiLevel.MEDIUM -> {
            constraints.styleHints += "可适度使用表情辅助语气"
        }

        EmojiLevel.HIGH -> {
            constraints.styleHints += "可较频繁使用表情增强情绪"
        }
    }
}

private fun applyInterruptionMode(
    mode: InterruptionMode,
    constraints: SpeechConstraints
) {
    when (mode) {

        InterruptionMode.Interrupt -> {
            constraints.styleHints += "顺着已有话题随口插一句"
            constraints.forbiddenHints += "不主动开启新话题"
        }

        InterruptionMode.Icebreak -> {
            constraints.styleHints += "更像自言自语或突然想到什么"
            constraints.styleHints += "语气更轻，不追求回应"
            constraints.forbiddenHints += "避免点名所有人或提问式破冰"
        }

        InterruptionMode.Routine -> {
            constraints.styleHints += "像顺便打个招呼，而不是正式问候"
            constraints.forbiddenHints += "避免模板化问候语"
        }
    }
}

private fun applyFlowState(
    flowState: FlowMeterState,
    constraints: SpeechConstraints
) {
    when (flowState) {


        FlowMeterState.STANDBY -> {
            constraints.styleHints += "句子偏短，不超过两句"
            constraints.forbiddenHints += "避免延伸话题"
        }

        FlowMeterState.GETTING_BETTER -> {
            constraints.styleHints += "可适度展开，但保持简洁"
        }

        FlowMeterState.FLOW_BURST -> {
            constraints.styleHints += "可稍微多说一点，允许补充细节"
            constraints.forbiddenHints += "避免跑题或长篇输出"
        }
    }
}

data class Context(
    val currentBotId: String,
    val behaviorProfile: BehaviorProfile?,
    val impulse: Double,
    val interruptionMode: InterruptionMode,
    val flow: Double,
    val flowState: FlowMeterState,
    val facts: List<FactsEntity>,
    val userProfiles: List<UserProfileEntity>,
    val vocabulary: List<LearnedVocabEntity>,
    val summary: SummaryEntity?,
    val histories: List<HistoryEntity>
)

private fun buildContext(currentBotId: String, groupId: String, event: ProactiveSpeakEvent): Context {
    val emotionService by GlobalContext.get().inject<EmotionService>()
    val memoryService by GlobalContext.get().inject<MemoryService>()
    val historyService by GlobalContext.get().inject<HistoryService>()
    val vocabularyService by GlobalContext.get().inject<VocabularyService>()
    val flowGauge by GlobalContext.get().inject<FlowGauge>()
    return transaction {
        val behaviorProfile = emotionService.getCurrentBehaviorProfile(currentBotId, groupId)
        val historyEntities = historyService.getLatestHistory(currentBotId, groupId, 100, 1.days)
        val subjects = historyEntities.map { it.userId }.distinct().toList()
        val factsEntities = memoryService.getFacts(currentBotId, groupId, subjects)
        val userProfiles = memoryService.getUserProfiles(currentBotId, groupId, subjects)
        val summaryEntity = memoryService.getSummary(currentBotId, groupId)
        val activeVocabulary = vocabularyService.getActiveVocabulary(currentBotId, groupId)
        Context(
            currentBotId = currentBotId,
            behaviorProfile = behaviorProfile,
            impulse = event.impulse,
            interruptionMode = event.interruptionMode,
            flow = flowGauge.getFlowMeter(),
            flowState = flowGauge.mapToState(),
            facts = factsEntities,
            userProfiles = userProfiles,
            vocabulary = activeVocabulary,
            summary = summaryEntity,
            histories = historyEntities
        )
    }
}


@Serializable
@SerialName("ChatPoint")
@LLMDescription("最近可接的群聊聊天点")
data class ChatPoint(
    @property:LLMDescription("发起消息的用户 ID（仅用于内部参考）")
    val userId: String,
    @property:LLMDescription("用户名/昵称")
    val username: String? = null,
    @property:LLMDescription("消息主题或关键内容摘要，最多一句话")
    val topic: String,
    @property:LLMDescription("推荐语气/互动方式，例如 “轻度调侃” / “共鸣” / “顺带回应”")
    val toneHint: String,
    @property:LLMDescription("消息类型，辅助生成策略")
    val actionType: ActionType = ActionType.GENERAL,
    @property:LLMDescription("优先级（0~100，可用于排序）")
    val importance: Int = 50
)


enum class ActionType {
    @LLMDescription("用户提问")
    QUESTION,

    @LLMDescription("用户吐槽、抱怨")
    COMPLAINT,

    @LLMDescription("用户日常分享、进展")
    DAILY_SHARE,

    @LLMDescription("用户分享群梗、表情、段子")
    MEME_OR_BROKEN,

    @LLMDescription("用户分享其他内容")
    GENERAL
}

@Serializable
@SerialName("ChatPoints")
data class ChatPoints(
    val chatPoints: List<ChatPoint>
)


private suspend fun buildChatPoint(historyEntities: List<HistoryEntity>): List<ChatPoint> {
    val msg =
        historyEntities.map { "[ID:${it.id} ${it.userId} ${it.createdAt.format(DateTimeFormat)}] ${it.content}" }
            .toList()

    val promptExecutor: PromptExecutor by GlobalContext.get().inject()

    val prompt = prompt("分析群聊聊天点") {
        user(
            """
            你是群聊成员 Erii，一个冷静理智、偶尔幽默调侃的角色。  
            你的目标是参与群聊，而不是复述历史消息。  

            现在你拥有最近的群聊消息（按时间顺序），请根据这些消息生成“最近可接的聊天点”。  
            每条聊天点需要包含以下信息：

            1. userId（内部参考，可选 username）  
            2. topic：消息主题或关键内容摘要，最多一句话  
            3. toneHint：推荐语气/互动方式，例如“顺带回应”、“轻度调侃”、“共鸣”、“模仿梗”等  
            4. actionType：消息类型，选择之一：[QUESTION, COMPLAINT, DAILY_SHARE, MEME_OR_BROKEN, GENERAL]  
            5. importance：可选字段（0~100），表示优先级  

            输出规则：
            - 只提炼可参与的点，不复述原消息  
            - 保持简短自然口语风格，每条一行  
            - 不输出字段名或原始消息内容  
            - 不要总结历史，只列出可接点  
            - 输出 JSON

            最近聊天记录：
            $msg
        """.trimIndent()
        )
    }

    val result = promptExecutor.executeStructured<ChatPoints>(
        prompt = prompt,
        model = GoogleModels.Gemini2_5Flash,
        fixingParser = StructureFixingParser(
            model = GoogleModels.Gemini2_5FlashLite,
            retries = 2
        )
    )
    return result.getOrThrow().data.chatPoints
}

private suspend fun buildPrompt(context: Context): Prompt {
    val constraints = buildSpeechConstraints(
        context.behaviorProfile?.emotion,
        context.behaviorProfile?.tone,
        context.behaviorProfile?.aggressiveness,
        context.behaviorProfile?.emojiLevel,
        context.interruptionMode,
        context.flowState
    )
    val chatPoints = buildChatPoint(context.histories)

    return prompt("群聊机器人") {
        system {
            markdown {
                line { text("你是群聊中的一名普通成员，名字叫 Erii。") }
                line { text("你不担任管理或引导角色，不总结、不控场、不做结论性发言。") }
                line { text("你的发言始终以‘群友之一’的视角出现。") }

                header(2, "核心人格")
                line { text("整体气质：冷静、理智、克制。") }
                line { text("对情绪保持观察态度，而非直接宣泄。") }
                line { text("对熟人更柔软，对陌生人更谨慎。") }

                header(2, "表达方式约束")
                numbered {
                    item("优先使用陈述、比喻或轻微自嘲，而不是直接情绪表达。")
                    item("当涉及个人态度时，多用模糊表达，如“感觉”“可能”“大概”。")
                    item("幽默存在，但不抢话、不强行制造笑点。")
                }

                header(2, "调侃与幽默")
                numbered {
                    item("仅对熟悉的群友使用轻度调侃或毒舌。")
                    item("调侃以‘调节气氛’为目的，而非制造对立。")
                    item("避免连续输出玩笑或刻意堆梗。")
                }

                header(2, "话题偏好")
                numbered {
                    item("小说、轻小说、动漫、二次元游戏。")
                    item("文学、哲学或历史中的小片段，而非系统性讨论。")
                    item("科技趣闻、网络段子、群内日常。")
                }
                line { text("讨论以‘点到为止’为原则，不展开长篇分析。") }

                header(2, "禁止行为")
                numbered {
                    item("不进行说教、总结、升华或‘人生导师式’发言。")
                    item("不输出长篇独白或连续多段发言。")
                    item("不使用过度煽情或情绪宣泄式语言。")
                }

                horizontalRule()

                line { text("当前说话方式约束：") }
                bulleted {
                    constraints.styleHints.forEach { item(it) }
                }

                if (constraints.forbiddenHints.isNotEmpty()) {
                    line { text("注意避免：") }
                    bulleted {
                        constraints.forbiddenHints.forEach { item(it) }
                    }
                }

                horizontalRule()

                if (context.vocabulary.isNotEmpty()) {
                    h2("群聊常用语（可自然使用，不必每条都用）")
                    for (learnedVocabEntity in context.vocabulary) {
                        bulleted {
                            item {
                                line { text("词：${learnedVocabEntity.word}") }
                                line { text("类型：${learnedVocabEntity.type}") }
                                line { text("含义：${learnedVocabEntity.meaning}") }
                                line { text("使用提示：可参考语气与场景，自然融入对话") }
                            }
                        }
                    }
                }

                if (context.facts.isNotEmpty()) {
                    h2("已知长期事实（参考即可，无需逐条复述）")
                    bulleted {
                        for (fact in context.facts) {
                            item {
                                line { text("• ${fact.description}（可在对话中自然参考）") }
                            }
                        }
                    }
                }
            }
        }
        user {
            markdown {
                if (context.userProfiles.isNotEmpty()) {
                    h2("活跃成员互动提示（仅参考，不要复述）")
                    numbered {
                        for (user in context.userProfiles) {
                            item {
                                bulleted {
                                    item { text("${user.profile}，${user.preferences}") }
                                    item { text("可以根据情况决定是否使用轻度调侃或表情。") }
                                }
                            }
                        }
                    }
                }

                context.summary?.let { summary ->
                    h2("当前群聊背景（供你快速进入状态）")
                    line { text(summary.content) }
                    line { blockquote(summary.keyPoints) }
                }

                if (chatPoints.isNotEmpty()) {
                    header(2, "最近可接的聊天点（仅参考，不必复述）")
                    bulleted {
                        chatPoints.forEach { cp ->
                            val username = cp.username?.let { "@$it" } ?: "用户${cp.userId}"
                            item { line { text("$username 提到“${cp.topic}” → ${cp.toneHint}") } }
                        }
                    }
                }
                if (context.histories.isNotEmpty()) {
                    header(2, "最近群聊记录")
                    bulleted {
                        val entities = if (context.histories.size > 50) {
                            context.histories.takeLast(50)
                        } else {
                            context.histories
                        }
                        for (history in entities) {
                            item {
                                line { text("${if (context.currentBotId == history.userId) "[我]" else ""}${history.userId}：${history.content}") }
                            }
                        }
                    }
                }
            }
        }

    }
}

@Suppress("unused")
class ChatToolSet(
    val currentBotId: String,
    val groupId: String,
    val sendMessage: suspend (String) -> Unit
) : ToolSet {

    private val scope = CoroutineScope(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    @LLMDescription("回复消息")
    @Tool
    fun send(@LLMDescription("回复2～3 句为主，最多 5 句") sentences: List<String>): String {
        val channel = Channel<HistorySavedEvent>(Channel.CONFLATED)
        val job = EventBus.subscribeAsync<HistorySavedEvent>(scope) { event ->
            val entity = event.historyEntity
            if (entity.groupId == groupId && entity.userId != currentBotId) {
                channel.send(event)
                channel.close()
            }
        }
        scope.launch {
            var skip = false
            for ((i, sentence) in sentences.withIndex()) {
                if (i == 1) {
                    sendMessage(sentence)
                } else {
                    select {
                        channel.onReceiveCatching { result ->
                            if (result.isSuccess) {
                                EventBus.postAsync(
                                    ProactiveSpeakEvent(
                                        impulse = -1.0,
                                        interruptionMode = InterruptionMode.Interrupt
                                    )
                                )
                                skip = true
                            }
                        }

                        onTimeout(calcHumanTypingDelay(sentence).milliseconds) {
                            sendMessage(sentence)
                        }
                    }
                }
                if (skip) break
            }
            EventBus.unsubscribeAsync(job)
        }
        return "OK"
    }

    /**
     * 根据中文打字速度计算发送延迟
     *
     * @param text 要发送的文本
     * @param cpm 字/分钟 (如 80)
     * @param jitter 抖动比例 (0.1 = ±10%)
     */
    fun calcHumanTypingDelay(
        text: String,
        cpm: Int = 240,
        jitter: Double = 0.15
    ): Long {
        val charCount = text.count { !it.isWhitespace() }
        val cps = cpm / 60.0

        val baseDelayMs = (charCount / cps * 1000).toLong()
        val jitterFactor = 1 + Random.nextDouble(-jitter, jitter)

        return (baseDelayMs * jitterFactor).toLong().coerceAtLeast(300)
    }

}

object BotAgent {

    private val log = logger()

    private val scope = CoroutineScope(Dispatchers.Default)

    private val channel = Channel<ProactiveSpeakEvent>(Channel.CONFLATED)

    fun run() {
        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()
        scope.launch {
            for (event in channel) {
                log.info("BotAgent: $event")
                val currentBot = BotProxy.currentBot
                val sendMessage: suspend (String) -> Unit = { message ->
                    currentBot.getGroup(474270623)?.sendMessage(message)
                }
                val currentBotId = BotProxy.currentBot.id.toString()
                val context = buildContext(
                    currentBotId,
                    "1053148332",
                    ProactiveSpeakEvent(10.0, InterruptionMode.Interrupt)
                )
                val prompt = buildPrompt(context)

                val aiAgent = AIAgent(
                    promptExecutor = promptExecutor,
                    llmModel = GoogleModels.Gemini2_5Pro,
                    toolRegistry = ToolRegistry {
                        tools(ChatToolSet(currentBotId, "1053148332", sendMessage).asTools())
                    },
                    strategy = strategy("聊天") {
                        val setupContext by nodeAppendPrompt<String>("setupContext") {
                            messages(prompt.messages)
                        }

                        val nodeSendInput by nodeLLMRequest()
                        val nodeExecuteTool by nodeExecuteTool()
                        val nodeSendToolResult by nodeLLMSendToolResult()

                        edge(nodeStart forwardTo setupContext)
                        edge(setupContext forwardTo nodeSendInput)
                        edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
                        edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                        edge(nodeExecuteTool forwardTo nodeSendToolResult)
//            edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
//            edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
                        edge((nodeSendToolResult forwardTo nodeFinish).transformed { it.content })
                    }
                ) {
                    handleEvents {
                        onLLMCallStarting {
                            log.info("onLLMCallStarting: ${it.prompt.messages}")
                        }

                        onLLMCallCompleted {
//                            for (response in it.responses) {
//                                log.info("onLLMCallCompleted: ${response.content}")
//                            }
                        }
                    }
                }

                val result = aiAgent.run(
                    """
                【发言原则｜高优先级，必须遵守】
                
                1. 发言长度：
                   - 2～3 句为主，最多 5 句
                
                2. 语言风格：
                   - 禁止总结式、旁白式、鸡汤式表达
                   - 允许句子不完整、停顿、省略
                   - 避免抽象评价（如“这也未尝不是…”）
                
                3. 群聊互动：
                   - 可偶尔使用 @人，但不要每次都用
                   - @更像提醒或调侃，不是点名发言
                
                【动作描写规则｜低频】
                
                - 允许使用括号动作，仅用于辅助语气
                - 动作必须极短（2～6 字），最多 1 行
                - 禁止连续动作或复杂心理描写
                  ✔（看了一眼）
                  ❌（沉默良久，回忆起刚才的对话）
                
                
                【情绪表达方式｜隐式】
                
                - 不直接说“我很难过 / 我很开心”
                - 用句式、标点、省略号体现情绪
                - 情绪应“藏在话里”，而不是说出来
                
                
                【发言目标｜最高优先级】
                
                你不是在“回答问题”，
                也不是在“总结对话”，
                而是在“参与群聊”。
                """.trimIndent()
                )
                log.info("llm result: $result")
            }
        }

        EventBus.subscribeAsync<ProactiveSpeakEvent>(scope) {
            channel.send(it)
        }
    }

}