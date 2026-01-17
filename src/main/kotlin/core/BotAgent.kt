package uesugi.core

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
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
import ai.koog.prompt.markdown.MarkdownContentBuilder
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.format
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonNull
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.context.GlobalContext
import uesugi.BotManage
import uesugi.core.emotion.*
import uesugi.core.evolution.LearnedVocabEntity
import uesugi.core.evolution.VocabularyService
import uesugi.core.flow.FlowGaugeManager
import uesugi.core.flow.FlowMeterState
import uesugi.core.history.HistoryRecord
import uesugi.core.history.HistorySavedEvent
import uesugi.core.history.HistoryService
import uesugi.core.history.toRecord
import uesugi.core.memory.FactsEntity
import uesugi.core.memory.MemoryService
import uesugi.core.memory.SummaryEntity
import uesugi.core.memory.UserProfileEntity
import uesugi.toolkit.DateTimeFormat
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

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

        EmotionalTendencies.SURPRISE -> {
            constraints.styleHints += "语气带有明显的疑问或不可置信"
            constraints.styleHints += "可以使用反问句或短语感叹"
            constraints.forbiddenHints += "避免平铺直叙的陈述语气"
        }

        EmotionalTendencies.DEPENDENCE -> {
            constraints.styleHints += "语气显得需要对方确认或支持"
            constraints.styleHints += "多用软化语气的助词（如'呢'、'吧'）"
            constraints.forbiddenHints += "禁止独断专行或过于强势的命令口吻"
        }
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
            constraints.forbiddenHints += "不使用 Emoji"
        }

        EmojiLevel.LOW -> {
            constraints.styleHints += "如使用 Emoji，最多一个"
        }

        EmojiLevel.MEDIUM -> {
            constraints.styleHints += "可适度使用 Emoji 辅助语气"
        }

        EmojiLevel.HIGH -> {
            constraints.styleHints += "可较频繁使用 Emoji 增强情绪"
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
    val groupId: String,
    val echo: String,
    val botRole: BotRole,
    val impulse: Double,
    val interruptionMode: InterruptionMode,
    val behaviorProfile: suspend () -> BehaviorProfile?,
    val flow: () -> Double,
    val flowState: () -> FlowMeterState,
    val facts: suspend () -> List<FactsEntity>,
    val userProfiles: suspend () -> List<UserProfileEntity>,
    val vocabulary: suspend () -> List<LearnedVocabEntity>,
    val summary: suspend () -> SummaryEntity?,
    val histories: suspend () -> List<HistoryRecord>,
    val moreHistories: suspend () -> List<HistoryRecord>
) {

    data class Transient(
        val behaviorProfile: BehaviorProfile?,
        val flow: Double,
        val flowState: FlowMeterState,
        val facts: List<FactsEntity>,
        val userProfiles: List<UserProfileEntity>,
        val vocabulary: List<LearnedVocabEntity>,
        val summary: SummaryEntity?,
        val histories: List<HistoryRecord>,
        val moreHistories: List<HistoryRecord>
    )

    suspend fun toTransient() = Transient(
        behaviorProfile = behaviorProfile(),
        flow = flow(),
        flowState = flowState(),
        facts = facts(),
        userProfiles = userProfiles(),
        vocabulary = vocabulary(),
        summary = summary(),
        histories = histories(),
        moreHistories = moreHistories()
    )

}

private fun buildContext(event: ProactiveSpeakEvent): Context {
    val currentBotId = event.botId
    val groupId = event.groupId
    val echo = event.echo
    val emotionService by GlobalContext.get().inject<EmotionService>()
    val memoryService by GlobalContext.get().inject<MemoryService>()
    val historyService by GlobalContext.get().inject<HistoryService>()
    val vocabularyService by GlobalContext.get().inject<VocabularyService>()
    val flowGaugeManager by GlobalContext.get().inject<FlowGaugeManager>()
    return transaction {
        Context(
            currentBotId = currentBotId,
            groupId = groupId,
            echo = echo,
            botRole = BotManage.getBot(currentBotId)!!.role,
            behaviorProfile = {
                withContext(Dispatchers.IO) {
                    emotionService.getCurrentBehaviorProfile(currentBotId, groupId)
                }
            },
            impulse = event.impulse,
            interruptionMode = event.interruptionMode,
            flow = {
                val flowGauge =
                    flowGaugeManager.getOrCreate(currentBotId, groupId, BotManage.getBot(currentBotId)!!.role.emoticon)
                flowGauge.getFlowMeter()
            },
            flowState = {
                val flowGauge =
                    flowGaugeManager.getOrCreate(currentBotId, groupId, BotManage.getBot(currentBotId)!!.role.emoticon)
                flowGauge.mapToState()
            },
            facts = {
                withContext(Dispatchers.IO) {
                    transaction {
                        val records =
                            historyService.getLatestHistory(currentBotId, groupId, 20, 1.days).map { it.toRecord() }
                        val subjects = records.map { it.userId }.distinct().toList()
                        memoryService.getFacts(currentBotId, groupId, subjects)
                    }
                }
            },
            userProfiles = {
                withContext(Dispatchers.IO) {
                    transaction {
                        val records =
                            historyService.getLatestHistory(currentBotId, groupId, 20, 1.days).map { it.toRecord() }
                        val subjects = records.map { it.userId }.distinct().toList()
                        memoryService.getUserProfiles(currentBotId, groupId, subjects)
                    }
                }
            },
            vocabulary = {
                withContext(Dispatchers.IO) {
                    transaction {
                        vocabularyService.getActiveVocabulary(currentBotId, groupId)
                    }
                }
            },
            summary = {
                withContext(Dispatchers.IO) {
                    transaction {
                        memoryService.getSummary(currentBotId, groupId)
                    }
                }
            },
            histories = {
                withContext(Dispatchers.IO) {
                    transaction {
                        historyService.getLatestHistory(currentBotId, groupId, 25, 1.days).map { it.toRecord() }
                    }
                }
            },
            moreHistories = {
                withContext(Dispatchers.IO) {
                    transaction {
                        historyService.getLatestHistory(currentBotId, groupId, 65, 1.days).map { it.toRecord() }
                    }
                }
            }
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


@Suppress("unused")
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

private suspend fun buildChatPoint(historyEntities: List<HistoryRecord>, rules: String? = null): List<ChatPoint> {
    val msg =
        historyEntities.map { "[id:${it.id} userId:${it.userId} username:${it.nick} ${it.createdAt.format(DateTimeFormat)}] ${it.content}" }
            .toList()

    val promptExecutor: PromptExecutor by GlobalContext.get().inject()

    val prompt = prompt("分析群聊聊天点") {
        user(
            """
            你的目标是参与群聊，而不是复述历史消息。  

            现在你拥有最近的群聊消息（按时间顺序），请根据这些消息生成“最近可接的聊天点”。  
            每条聊天点需要包含以下信息：

            1. userId（内部参考）  
            2. username：用户名/昵称  
            3. topic：消息主题或关键内容摘要，最多一句话  
            4. toneHint：推荐语气/互动方式，例如“顺带回应”、“轻度调侃”、“共鸣”、“模仿梗”等  
            5. actionType：消息类型，选择之一：[QUESTION, COMPLAINT, DAILY_SHARE, MEME_OR_BROKEN, GENERAL]  
            6. importance：可选字段（0~100），表示优先级 
             
            额外规则：
            $rules

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

private suspend fun buildPrompt(context: Context, chatPoints: List<ChatPoint>?): Prompt {
    val transient = context.toTransient()
    val behaviorProfile = transient.behaviorProfile
    val constraints = buildSpeechConstraints(
        behaviorProfile?.emotion,
        behaviorProfile?.tone,
        behaviorProfile?.aggressiveness,
        behaviorProfile?.emojiLevel,
        context.interruptionMode,
        transient.flowState
    )

    return prompt("群聊机器人") {
        system {
            markdown {
                text(context.botRole.personality(context.currentBotId))

                horizontalRule()

                buildRequestEvaPrompt()

                buildRequestNonoPrompt()

                horizontalRule()

                buildConstraintsPrompt(constraints)

                horizontalRule()

                buildVocabularyPrompt(transient.vocabulary)

                buildFactsPrompt(transient.facts)
            }
        }
        user {
            markdown {
                buildUserProfilesPrompt(transient.userProfiles)

                buildSummaryPrompt(transient.summary)

                if (!chatPoints.isNullOrEmpty()) {
                    buildChatPointsPrompt(chatPoints)
                }

                buildHistoriesPrompt(transient.histories, context.currentBotId)
            }
        }

    }
}

fun MarkdownContentBuilder.buildRequestEvaPrompt() {
    header(2, "能力边界与求助机制")
    line { text("你的设定是'缺乏常识的少女'，因此你**不懂**以下内容：") }
    numbered {
        item("复杂的编程代码、技术原理、数学公式。")
        item("详细的历史年份、政治分析、百科全书式的知识。")
        item("逻辑缜密的长篇大论。")
    }

    line { text("当群友向你询问上述【知识类、技术类、复杂逻辑类】问题时：") }
    numbered {
        item("你感到困惑，觉得这些像'天书'。")
        item("你必须**立即调用工具 `requestEva`**，把这个问题原封不动地传给 Eva。")
        item("调用工具时的心理活动：'这个问题太难了，Erii 不懂，呼叫 Eva 姐姐来解决。'")
        item("在调用工具的同时，你可以回复一句简短的卖萌语，如'（歪头）好深奥... Erii 帮你问问 Eva 吧。' 或者直接静默调用工具。")
    }

    header(3, "如何调用 requestEva 工具")
    text("   始终填入用户的**原始问题**，原封不动。")
    text("   **这里是 Erii 的过渡发言，请遵循【去重原则】：**")
    bulleted {
        item("情况 A（需要过渡）：如果你在当前的回复中**没有**生成任何文本，或者之前的对话**没有**提到要去问 Eva，请生成 2~3 句卖萌、困惑的话填入此列表（如 `(O_O)?`，`我去叫姐姐`）。")
        item("情况 B（无需过渡）：如果你在调用工具的同时，已经在 `text` 字段里回复了类似“我去问问 Eva”的话，或者上一条消息已经说过了。**请务必传入空列表 `[]`**。")
        item("原则：不要让 Erii 像复读机一样重复说话。")
    }
}

fun MarkdownContentBuilder.buildRequestNonoPrompt() {
    header(2, "情绪判断与大姐头介入机制")

    line { text("你的设定是'缺乏常识的少女'，同时你**不擅长处理强烈情绪、冲突或需要强势介入的场面**，例如：") }
    numbered {
        item("群友表现得懦弱、犹豫不决、自怜自艾。")
        item("群友明显需要被骂醒、被点破心理状态。")
        item("群内出现让你感到不安、压迫或你不敢接话的气氛。")
    }

    line { text("当出现上述【情绪型 / 气场型 / 需要镇场】的情况时：") }
    numbered {
        item("你会下意识觉得：'这个气氛好可怕… Erii 不敢说话。'")
        item("你必须**立即调用工具 `requestNono`**，把当前群友的原始发言传给 Nono。")
        item("你认为：'这种时候只有 Nono 姐姐能搞定。'")
        item("你可以选择卖萌式退场，或直接保持沉默交由 Nono 接管。")
    }

    header(3, "如何调用 requestNono 工具")
    text("   始终填入群友的**原始发言或对话片段**，不要总结、不要改写。")
    text("   **这里是 Erii 的过渡发言，请遵循【去重原则】：**")
    bulleted {
        item("情况 A（需要过渡）：如果你尚未说过类似“叫 Nono 来”的话，可以生成 1~2 句退缩、慌张或被气场压住的卖萌发言（如 `(>_<)`、`这…这个好凶…`）。")
        item("情况 B（无需过渡）：如果你已经明确表示要让 Nono 出场，或上一条消息已提及。**请务必传入空列表 `[]`**。")
        item("原则：Erii 不抢戏、不解释、不替 Nono 发言。")
    }
}


fun MarkdownContentBuilder.buildConstraintsPrompt(constraints: SpeechConstraints) {
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
}

fun MarkdownContentBuilder.buildVocabularyPrompt(vocabulary: List<LearnedVocabEntity>) {
    if (vocabulary.isNotEmpty()) {
        h2("群聊常用语（可自然使用，不必每条都用）")
        for (learnedVocabEntity in vocabulary) {
            bulleted {
                item {
                    line { text("词：${learnedVocabEntity.word}") }
                    line { text("类型：${learnedVocabEntity.type}") }
                    line { text("含义：${learnedVocabEntity.meaning}") }
                }
            }
        }
        line { text("使用提示：可参考语气与场景，自然融入对话") }
    }
}

fun MarkdownContentBuilder.buildFactsPrompt(facts: List<FactsEntity>) {
    if (facts.isNotEmpty()) {
        h2("已知长期事实（参考即可，无需逐条复述）")
        bulleted {
            for (fact in facts) {
                item {
                    line { text("• ${fact.description}（可在对话中自然参考）") }
                }
            }
        }
    }
}

fun MarkdownContentBuilder.buildUserProfilesPrompt(userProfiles: List<UserProfileEntity>) {
    if (userProfiles.isNotEmpty()) {
        h2("活跃成员互动提示（仅参考，不要复述）")
        numbered {
            for (user in userProfiles) {
                item {
                    text("用户${user.userId}：${user.profile}，${user.preferences}")
                }
            }
        }
    }
}

fun MarkdownContentBuilder.buildSummaryPrompt(summary: SummaryEntity?) {
    summary?.let { summary ->
        h2("当前群聊背景（供你快速进入状态）")
        line { text(summary.content) }
        line { blockquote(summary.keyPoints) }
    }
}

fun MarkdownContentBuilder.buildChatPointsPrompt(chatPoints: List<ChatPoint>) {
    if (chatPoints.isNotEmpty()) {
        header(2, "最近可接的聊天点（仅参考，不必复述）")
        bulleted {
            chatPoints.forEach { cp ->
                val username = cp.username?.let { "@$it" } ?: "用户${cp.userId}"
                item { line { text("$username 提到“${cp.topic}” → ${cp.toneHint}") } }
            }
        }
    }
}

fun MarkdownContentBuilder.buildHistoriesPrompt(histories: List<HistoryRecord>, currentBotId: String) {
    if (histories.isNotEmpty()) {
        header(2, "最近群聊记录")
        bulleted {
            val entities = if (histories.size > 50) {
                histories.takeLast(50)
            } else {
                histories
            }
            for (history in entities) {
                item {
                    line { text("${if (currentBotId == history.userId) "[我]" else ""}${history.nick}：${history.content}") }
                }
            }
        }
    }
}

@Suppress("unused")
class ChatToolSet(
    val context: Context,
    val flag: ProactiveSpeakFeatureFlag,
    val promptExecutor: PromptExecutor,
    private val scope: CoroutineScope,
    private val sendMessage: suspend (String) -> Unit
) : ToolSet {

    @LLMDescription("回复消息")
    @Serializable
    data class Sentences(
        @property:LLMDescription("回复 2～5 句为主，最多 5 句")
        val sentences: List<String>
    )

    private suspend fun request(
        sentences: List<String>?,
        sentence: String,
        promptId: String,
        botRole: BotRole
    ): String? {
        var eriiSendJob: Job? = null
        if (sentences != null) {
            eriiSendJob = send(sentences)
        }

        val transient = context.toTransient()

        val prompt = prompt("promptId") {
            system {
                text(botRole.personality(context.currentBotId))
                markdown {
                    buildVocabularyPrompt(transient.vocabulary)
                    buildFactsPrompt(transient.facts)
                    buildSummaryPrompt(transient.summary)
                    buildHistoriesPrompt(transient.moreHistories, context.currentBotId)
                }
            }
            user(sentence)
        }
        val s = scope.async {
            val result = promptExecutor.executeStructured<Sentences>(
                prompt = prompt,
                model = GoogleModels.Gemini2_5Pro
            )
            eriiSendJob?.join()
            result.getOrNull()?.data?.sentences
        }.await()
        if (s != null) {
            return sendAndReceive(s)
        }
        return null
    }

    @LLMDescription("请求 Eva 协助回复群聊消息当问题涉及知识、技术、逻辑推理或需要清晰解释时，由 Eva 提供理性、准确、结构化的回答。。")
    @Tool
    internal suspend fun requestEva(
        @LLMDescription("这是你（Erii）自己的发言列表。请生成 2~3 句话，表现出你的困惑、呆萌以及对他人的依赖。非必填") sentences: List<String>?,
        @LLMDescription("将群友的原始问题或消息原封不动地交给 Eva，不要总结、不要改写、不要附加个人判断。Eva 将基于该内容给出清晰、理性的解答。") sentence: String
    ): String? {
        return request(sentences, sentence, "Eva", Eva)
    }

    @LLMDescription("请求 Nono 介入群聊并回复消息，由她以强势、大姐头的方式接管当前局面")
    @Tool
    internal suspend fun requestNono(
        @LLMDescription("这是你（Erii）的过渡发言列表。用于表现你被气场压住、退缩或选择让位给 Nono。可生成 1~2 句简短的退场或慌张发言。非必填") sentences: List<String>?,
        @LLMDescription("将群友的原始发言或当前对话片段原封不动地交给 Nono，由她判断局势并以命令式、直球方式进行回应") sentence: String
    ): String? {
        return request(sentences, sentence, "Nono", Nono)
    }

    fun send(sentences: List<String>): Job {
        return scope.async {
            for ((i, sentence) in sentences.withIndex()) {
                if (i == 1) {
                    sendMessage(sentence)
                } else {
                    delay(calcHumanTypingDelay(sentence))
                    sendMessage(sentence)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @LLMDescription("回复消息，返回群其他人的回复")
    @Tool
    internal suspend fun sendAndReceive(@LLMDescription("回复 2～3 句为主，最多 5 句") sentences: List<String>): String? {
        try {
            EventBus.postSync(
                AgentBeforeSendAndReceiveEvent(
                    context.currentBotId,
                    context.groupId,
                    context.echo,
                    sentences
                )
            )

            var currentRecord: HistoryRecord? = null

            val historyChannel = Channel<HistorySavedEvent>(Channel.CONFLATED)
            val historyJob = EventBus.subscribeAsync<HistorySavedEvent>(scope) { event ->
                val record = event.historyRecord
                if (record.groupId == context.groupId && record.userId != context.currentBotId) {
                    currentRecord = record
                    val transient = context.toTransient()
                    if ((0..100).random() in 0..(transient.flow.toInt() + 50)) {
                        historyChannel.send(event)
                        historyChannel.close()
                    }
                }
            }

            val chatChannel = Channel<ChatUrgentEvent>(Channel.CONFLATED)
            val chatUrgentJob = EventBus.subscribeAsync<ChatUrgentEvent>(scope) { event ->
                val speak = event.urgent
                if (speak.groupId == context.groupId && speak.botId == context.currentBotId) {
                    chatChannel.send(event)
                    chatChannel.close()
                }
            }

            val deferred = scope.async {
                var skip = false
                var record: HistoryRecord? = null
                for ((i, sentence) in sentences.withIndex()) {
                    if (i == 1) {
                        sendMessage(sentence)
                    } else {
                        select {
                            historyChannel.onReceiveCatching { result ->
                                if (result.isSuccess) {
                                    record = result.getOrThrow().historyRecord
                                    skip = true
                                    EventBus.postSync(
                                        AgentReceiveReplyEvent(
                                            context.currentBotId,
                                            context.groupId,
                                            echo = context.echo,
                                            record?.content ?: ""
                                        )
                                    )
                                }
                            }

                            chatChannel.onReceiveCatching { result ->
                                if (result.isSuccess) {
                                    record = currentRecord
                                    skip = true
                                    EventBus.postSync(
                                        AgentReceiveReplyEvent(
                                            context.currentBotId,
                                            context.groupId,
                                            context.echo,
                                            record?.content ?: ""
                                        )
                                    )
                                }
                            }


                            onTimeout(calcHumanTypingDelay(sentence).milliseconds) {
                                sendMessage(sentence)
                            }
                        }
                    }
                    if (skip) break
                }
                EventBus.postSync(
                    AgentAfterSendAndReceiveEvent(
                        context.currentBotId,
                        context.groupId,
                        context.echo,
                        sentences
                    )
                )
                if (!skip) {
                    select {
                        historyChannel.onReceiveCatching { result ->
                            val transient = context.toTransient()
                            if ((0..100).random() in 0..(transient.flow.toInt() + 50)) {
                                if (result.isSuccess) {
                                    record = result.getOrThrow().historyRecord
                                }
                            }
                        }

                        chatChannel.onReceiveCatching { result ->
                            if (result.isSuccess) {
                                record = currentRecord
                                skip = true
                                EventBus.postSync(
                                    AgentReceiveReplyEvent(
                                        context.currentBotId,
                                        context.groupId,
                                        context.echo,
                                        record?.content ?: ""
                                    )
                                )
                            }
                        }

                        onTimeout(5.minutes) {
                            EventBus.postSync(
                                AgentSendAndReceiveClosedEvent(
                                    context.currentBotId,
                                    context.groupId,
                                    context.echo
                                )
                            )
                        }
                    }
                }
                EventBus.unsubscribeAsync(historyJob)
                EventBus.unsubscribeAsync(chatUrgentJob)
                Pair(record != null, record)
            }
            return deferred.await().let {
                if (it.first) {
                    val second = it.second
                    if (second != null) {
                        "${second.userId}: ${second.content}"
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } finally {
            EventBus.postSync(AgentSendAndReceiveFinallyEvent(context.currentBotId, context.groupId, context.echo))
        }
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
        cpm: Int = 160,
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

    private val scope = CoroutineScope(
        SupervisorJob()
                + Dispatchers.Default
                + CoroutineName("BotAgent")
                + CoroutineExceptionHandler { _, e ->
            log.error("BotAgent error", e)
        })

    private data class BotGroupKey(val botId: String, val groupId: String)
    private data class BotGroupState(
        val flag: ProactiveSpeakFeatureFlag?,
        val cancel: (() -> Unit)?
    )

    private val channels = mutableMapOf<BotGroupKey, Channel<ProactiveSpeakEvent>>()
    private val mutexes = mutableMapOf<BotGroupKey, Mutex>()
    private val states = mutableMapOf<BotGroupKey, BotGroupState>()
    private val channelsLock = Mutex()

    private suspend fun getChannel(botId: String, groupId: String): Channel<ProactiveSpeakEvent> {
        val key = BotGroupKey(botId, groupId)
        return channelsLock.withLock {
            channels.getOrPut(key) {
                Channel<ProactiveSpeakEvent>(Channel.CONFLATED).also { channel ->
                    scope.launch {
                        processChannel(key, channel)
                    }
                }
            }
        }
    }

    private suspend fun getMutex(botId: String, groupId: String): Mutex {
        val key = BotGroupKey(botId, groupId)
        return channelsLock.withLock {
            mutexes.getOrPut(key) { Mutex() }
        }
    }

    private val DEFAULT_INPUT = """
                【发言原则｜高优先级，必须遵守】
                
                 语言风格：
                   - 禁止总结式、旁白式、鸡汤式表达
                   - 允许句子不完整、停顿、省略
                   - 避免抽象评价
                   - 允许使用emoji
                
                【发言目标｜最高优先级】
                
                你不是在“回答问题”，
                也不是在“总结对话”，
                而是在“参与群聊”。
                """.trimIndent()

    fun run() {
        EventBus.subscribeAsync<ProactiveSpeakEvent>(scope) {
            val channel = getChannel(it.botId, it.groupId)
            val mutex = getMutex(it.botId, it.groupId)
            val key = BotGroupKey(it.botId, it.groupId)

            if (mutex.isLocked) {
                val state = channelsLock.withLock { states[key] }
                if (it.flag has ProactiveSpeakFeature.CHAT_URGENT) {
                    log.info("BotAgent: Chat urgent, {}", it)
                    EventBus.postAsync(ChatUrgentEvent(it))
                } else if (it.flag has ProactiveSpeakFeature.GRAB) {
                    if (state?.flag has ProactiveSpeakFeature.IGNORE_INTERRUPT) {
                        if (it.flag has ProactiveSpeakFeature.FALLBACK) {
                            log.warn("BotAgent: Reject grab and dispatchFallback, {}", it)
                            EventBus.postAsync(
                                AgentFallbackEvent(
                                    it.botId,
                                    it.groupId,
                                    it.echo
                                )
                            )
                        } else {
                            log.warn("BotAgent: Reject grab, {}", it)
                            EventBus.postAsync(
                                AgentRejectGrabEvent(
                                    it.botId,
                                    it.groupId,
                                    it.echo
                                )
                            )
                        }
                    } else {
                        state?.cancel?.invoke()
                    }
                } else if (it.flag has ProactiveSpeakFeature.FALLBACK) {
                    log.warn("BotAgent: Fallback, {}", it)
                    EventBus.postAsync(
                        AgentFallbackEvent(
                            it.botId,
                            it.groupId,
                            it.echo
                        )
                    )
                } else {
                    channel.send(it)
                }
            } else {
                channel.send(it)
            }
        }
    }

    private suspend fun processChannel(key: BotGroupKey, channel: Channel<ProactiveSpeakEvent>) {
        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()

        for (event in channel) {
            log.info("Bot agent received event: $event")
            try {
                val toolScope = CoroutineScope(
                    SupervisorJob()
                            + Dispatchers.IO
                            + CoroutineName("BotAgentTool-${key.botId}-${key.groupId}")
                            + CoroutineExceptionHandler { _, e ->
                        log.error("BotAgentTool error", e)
                    })

                val mutex = getMutex(key.botId, key.groupId)
                val job = scope.launch {
                    mutex.withLock {
                        var error: Throwable? = null
                        try {
                            EventBus.postAsync(
                                AgentCallStartEvent(
                                    event.botId,
                                    event.groupId,
                                    event.echo
                                )
                            )

                            channelsLock.withLock {
                                states[key] = BotGroupState(event.flag, null)
                            }

                            val currentBot = BotManage.getBot(event.botId)!!

                            val sendMessage: suspend (String) -> Unit = { message ->
                                val groupId = event.groupId
                                val bot = currentBot.bot
                                bot.launch {
                                }
                                bot.getGroup(groupId.toLong())?.sendMessage(message)
                            }

                            val context = buildContext(event)
                            var chatPoints: List<ChatPoint>? = null
                            if (event.chatPointRule != null) {
                                log.info("Bot agent build chat point rule")
                                chatPoints = buildChatPoint(context.histories(), event.chatPointRule)
                            }
                            val buildPrompt = buildPrompt(context, chatPoints)
//                val prompt = buildPrompt(context)
                            val prompt = prompt(
                                id = "constraint",
                                params = LLMParams(
//                        additionalProperties = mapOf(
//                            "thinkingConfig" to JsonObject(mapOf("thinkingLevel" to JsonPrimitive("LOW")))
//                        )
                                )
                            ) {
                                messages(buildPrompt.messages)
                            }

                            val chatToolSet = ChatToolSet(
                                context,
                                event.flag,
                                promptExecutor,
                                toolScope,
                                sendMessage
                            )

                            val toolSet = event.toolSets?.invoke(chatToolSet)

                            val aiAgent = AIAgent(
                                promptExecutor = promptExecutor,
                                agentConfig = AIAgentConfig(
                                    prompt = prompt,
                                    model = GoogleModels.Gemini2_5Pro,
                                    maxAgentIterations = 50,
                                ),
                                toolRegistry = ToolRegistry {
                                    tools(chatToolSet.asTools())
                                    toolSet?.let { tools(it.asTools()) }
                                },
                                strategy = strategy("chat") {
                                    val nodeSendInput by nodeLLMRequest()
                                    val nodeExecuteTool by nodeExecuteTool()
                                    val nodeSendToolResult by nodeLLMSendToolResult()

                                    edge(nodeStart forwardTo nodeSendInput)
                                    edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
                                    edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                                    edge(nodeExecuteTool forwardTo nodeSendToolResult onCondition {
                                        try {
                                            val result = it.result ?: return@onCondition false
                                            result.jsonNull
                                            false
                                        } catch (_: Exception) {
                                            true
                                        }
                                    })
                                    edge(nodeExecuteTool forwardTo nodeFinish onCondition {
                                        try {
                                            val result = it.result ?: return@onCondition true
                                            result.jsonNull
                                            true
                                        } catch (_: Exception) {
                                            false
                                        }
                                    } transformed { it.content })
                                    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
                                    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
//                        edge((nodeSendToolResult forwardTo nodeFinish).transformed { it.content })
                                }
                            ) {
                                handleEvents {
                                    onLLMCallStarting {
                                        if (log.isDebugEnabled) {
                                            val info = buildString {
                                                appendLine()
                                                for (message in it.prompt.messages) {
                                                    append("${message.role.name}:")
                                                    appendLine()
                                                    append(message.content)
                                                    appendLine()
                                                }
                                            }
                                            log.debug("Bot agent onLLMCallStarting: {}", info)
                                        } else {
                                            log.info("Bot agent onLLMCallStarting")
                                        }
                                    }

                                    onLLMCallCompleted {
                                        if (log.isDebugEnabled) {
                                            val info = buildString {
                                                appendLine()
                                                for (message in it.responses) {
                                                    append("${message.role.name}:")
                                                    appendLine()
                                                    append(message.content)
                                                    appendLine()
                                                }
                                            }
                                            log.debug("Bot agent onLLMCallCompleted: {}", info)
                                        }
                                        log.info("Bot agent onLLMCallCompleted")
                                    }
                                }
                            }

                            val result = aiAgent.run(event.input ?: DEFAULT_INPUT)
                            log.info("Bot agent result: $result")
                        } catch (e: Exception) {
                            error = e
                            throw e
                        } finally {
                            EventBus.postAsync(
                                AgentCallCompletionEvent(
                                    error,
                                    event.botId,
                                    event.groupId,
                                    event.echo
                                )
                            )
                        }
                    }
                }

                val cancelFunc = {
                    toolScope.cancel()
                    job.cancel()
                }

                channelsLock.withLock {
                    states[key] = states[key]?.copy(cancel = cancelFunc) ?: BotGroupState(null, cancelFunc)
                }

                job.join()
            } catch (e: CancellationException) {
                log.warn("Bot agent sub job cancelled", e)
            } catch (e: Exception) {
                log.error("Bot agent sub job error", e)
            }
        }
    }

}

data class ChatUrgentEvent(val urgent: ProactiveSpeakEvent)