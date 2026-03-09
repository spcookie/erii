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
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.markdown.MarkdownContentBuilder
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import com.nlf.calendar.Solar
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Required
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonNull
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.message.data.At
import okio.Path.Companion.toPath
import okio.buffer
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.BotManage
import uesugi.LOG
import uesugi.config.LLMModelsChoice
import uesugi.core.message.history.HistoryRecord
import uesugi.core.message.history.HistorySavedEvent
import uesugi.core.message.history.HistoryService
import uesugi.core.message.resource.ResourceService
import uesugi.core.state.emotion.*
import uesugi.core.state.emotion.EmojiLevel.*
import uesugi.core.state.evolution.LearnedVocabEntity
import uesugi.core.state.evolution.VocabularyService
import uesugi.core.state.flow.FlowGaugeManager
import uesugi.core.state.flow.FlowMeterState
import uesugi.core.state.meme.MemeResource
import uesugi.core.state.meme.MemoService
import uesugi.core.state.memory.FactsEntity
import uesugi.core.state.memory.MemoryService
import uesugi.core.state.memory.SummaryEntity
import uesugi.core.state.memory.UserProfileEntity
import uesugi.toolkit.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime


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

        NONE -> {
            constraints.forbiddenHints += "不使用 Emoji/表情包"
        }

        LOW -> {
            constraints.styleHints += "如使用 Emoji/表情包，最多一个"
        }

        MEDIUM -> {
            constraints.styleHints += "可适度使用 Emoji/表情包 辅助语气"
        }

        HIGH -> {
            constraints.styleHints += "可较频繁使用 Emoji/表情包 增强情绪"
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
    val moreHistories: suspend () -> List<HistoryRecord>,
    val memo: suspend (String) -> MemeResource?,
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
    val emotionService: EmotionService by ref()
    val memoryService: MemoryService by ref()
    val historyService: HistoryService by ref()
    val resourceService: ResourceService by ref()
    val vocabularyService: VocabularyService by ref()
    val flowGaugeManager: FlowGaugeManager by ref()
    val memoService: MemoService by ref()
    val objectStorage: ObjectStorage by ref()
    return transaction {
        Context(
            currentBotId = currentBotId,
            groupId = groupId,
            echo = echo,
            botRole = BotManage.getBot(currentBotId).role,
            behaviorProfile = {
                withContext(Dispatchers.IO) {
                    emotionService.getCurrentBehaviorProfile(currentBotId, groupId)
                }
            },
            impulse = event.impulse,
            interruptionMode = event.interruptionMode,
            flow = {
                val flowGauge =
                    flowGaugeManager.getOrCreate(currentBotId, groupId, BotManage.getBot(currentBotId).role.emoticon)
                flowGauge.getFlowMeter()
            },
            flowState = {
                val flowGauge =
                    flowGaugeManager.getOrCreate(currentBotId, groupId, BotManage.getBot(currentBotId).role.emoticon)
                flowGauge.mapToState()
            },
            facts = {
                withContext(Dispatchers.IO) {
                    transaction {
                        val records =
                            historyService.getLatestHistory(currentBotId, groupId, 20, 1.days)
                        val subjects = records.map { it.userId }.distinct().toList()
                        memoryService.getFacts(currentBotId, groupId, subjects, 25)
                    }
                }
            },
            userProfiles = {
                withContext(Dispatchers.IO) {
                    transaction {
                        val records =
                            historyService.getLatestHistory(currentBotId, groupId, 20, 1.days)
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
                        historyService.getLatestHistory(currentBotId, groupId, 20, 12.hours)
                    }
                }
            },
            moreHistories = {
                withContext(Dispatchers.IO) {
                    transaction {
                        historyService.getLatestHistory(currentBotId, groupId, 30, 12.hours)
                    }
                }
            },
            memo = { key ->
                withContext(Dispatchers.IO) {
                    val record = memoService.searchByVector(currentBotId, groupId, key, 1)
                        .filter { it.second > 0.5 }
                        .map { it.first }
                        .firstOrNull() ?: return@withContext null
                    memoService.incrementUsageCount(record.id!!)
                    val resource = resourceService.getResource(record.resourceId) ?: return@withContext null
                    val bytes = objectStorage.get(resource.url.toPath())
                        .buffer()
                        .readByteArray()
                    MemeResource(
                        id = record.id,
                        botId = record.botId,
                        groupId = record.groupId,
                        resourceId = record.resourceId,
                        bytes = bytes
                    )
                }
            },
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
    @property:LLMDescription("优先级（0~100，可用于排序）")
    val importance: Int = 50
)

@Serializable
@SerialName("ChatPoints")
data class ChatPoints(
    val chatPoints: List<ChatPoint>
)

private suspend fun buildChatPoint(
    historyEntities: List<HistoryRecord>,
    rules: String? = null,
    currentBotId: String
): List<ChatPoint> {
    val msg =
        historyEntities.map {
            "${if (it.userId == currentBotId) "[我]" else ""}[userId:${it.userId} username: ${
                if (it.userId == currentBotId) BotManage.getBot(
                    currentBotId
                ).role.name else it.nick
            } ${
                it.createdAt.format(
                    DateTimeFormat
                )
            }] ${it.content}"
        }
            .toList()

    val promptExecutor by ref<PromptExecutor>()

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
            5. importance：可选字段（0~100），表示优先级 
            
            注意：带有“[我]”的聊天点表示该消息由机器人发出。
             
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
        model = LLMModelsChoice.Lite,
        fixingParser = StructureFixingParser(
            model = LLMModelsChoice.Lite,
            retries = 2
        ),
        examples = listOf(
            ChatPoints(
                listOf(
                    ChatPoint(
                        userId = "12345",
                        username = "User",
                        topic = "This is a sample chat point.",
                        toneHint = "Neutral",
                        importance = 50
                    )
                )
            )
        )
    )
    return result.getOrThrow().data.chatPoints
}

private suspend fun buildPrompt(context: Context, chatPoints: List<ChatPoint>?): Prompt {
    val transient = context.toTransient()
    val constraints = buildConstraint(context, transient)

    return prompt("群聊机器人") {
        system {
            markdown {
                text(context.botRole.personality(context.currentBotId))
                horizontalRule()
                buildConstraintsPrompt(constraints)
                horizontalRule()
                buildVocabularyPrompt(transient.vocabulary)
                buildFactsPrompt(transient.facts)
                horizontalRule()
                buildUserProfilesPrompt(transient.userProfiles)
                horizontalRule()
                buildFusion()
                horizontalRule()
                buildMetadataPrompt()
                horizontalRule()
                buildConstraintRulePrompt()
            }
        }
        user {
            markdown {
                buildSummaryPrompt(transient.summary)

                if (!chatPoints.isNullOrEmpty()) {
                    buildChatPointsPrompt(chatPoints)
                }

                buildHistoriesPrompt(transient.histories, context.currentBotId)
            }
        }

    }
}

private fun buildConstraint(
    context: Context,
    transient: Context.Transient
): SpeechConstraints {
    val behaviorProfile = transient.behaviorProfile
    val constraints = buildSpeechConstraints(
        behaviorProfile?.emotion,
        behaviorProfile?.tone,
        behaviorProfile?.aggressiveness,
        behaviorProfile?.emojiLevel,
        context.interruptionMode,
        transient.flowState
    )
    return constraints
}

@OptIn(ExperimentalTime::class)
fun MarkdownContentBuilder.buildMetadataPrompt() {
    header(2, "元数据")
    val instant = Clock.System.now()
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    var solar = Solar.fromYmd(
        localDateTime.year,
        localDateTime.month.number,
        localDateTime.day
    )
    var lunar = solar.lunar
    line { text("当前日期时间: ${DateTimeFormat.format(localDateTime)}") }

    line { text("当前星期: 星期${solar.weekInChinese}") }
    buildList {
        addAll(solar.festivals)
        addAll(solar.otherFestivals)
        addAll(lunar.festivals)
        addAll(lunar.otherFestivals)
    }.joinToString("、")
        .takeIf { it.isNotBlank() }
        ?.let {
            line { text("当前节日: $it") }
        }
    val maxDays = 365
    var count = 0
    val max = 2
    var inc = 0
    while (count++ < maxDays) {
        solar = solar.next(1)
        lunar = solar.lunar
        val festivals = buildList {
            addAll(solar.festivals)
            addAll(solar.otherFestivals)
            addAll(lunar.festivals)
            addAll(lunar.otherFestivals)
        }
        if (festivals.isNotEmpty()) {
            var label = ""
            repeat(inc + 1) { label += "下" }
            label += "一次节日"
            val time =
                "${solar.month}/${solar.day} ${lunar.monthInChinese}月${lunar.dayInChinese} 星期${solar.weekInChinese}"
            line {
                text(
                    "$label[$time]: ${
                        festivals.joinToString(
                            "、"
                        )
                    }"
                )
            }
            inc++
            if (inc >= max) {
                break
            }
        }
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
                    line { text("词：${learnedVocabEntity.word}, 含义：${learnedVocabEntity.meaning}，例子：${learnedVocabEntity.example}") }
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
                    line { text(fact.description) }
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
            chatPoints.sortedByDescending { it.importance }
                .forEach { cp ->
                    val username = cp.username?.let { "@$it" } ?: "用户${cp.userId}"
                    item { line { text("$username 提到“${cp.topic}” → ${cp.toneHint} 优先级：${cp.importance}") } }
                }
        }
    }
}

fun MarkdownContentBuilder.buildHistoriesPrompt(histories: List<HistoryRecord>, currentBotId: String) {
    if (histories.isNotEmpty()) {
        header(2, "最近群聊记录")
        line { text("注意：标有 [我] 的为当前自己的发言，括号中显示用户ID") }
        bulleted {
            for (history in histories) {
                item {
                    line { text("${if (currentBotId == history.userId) "[我]${BotManage.getBot(history.userId).role.name}" else history.nick}(${history.userId})：${history.content}") }
                }
            }
        }
    }
}

fun MarkdownContentBuilder.buildFusion() {
    h2("群聊融合机制（优先级高于表达细节）")
    bulleted {
        item { line { text("你的语气应贴近当前群聊最近发言的节奏与风格") } }
        item { line { text("果群聊偏随意 → 你更随意") } }
        item { line { text("如果群聊偏认真 → 你更收敛") } }
        item { line { text("不要使用明显“角色化语言模板”") } }
        item { line { text("允许轻微模仿群友用词，但不要完全复制") } }
    }
    line { text("原则：你是群里的成员，不是设定展示者") }
}

fun MarkdownContentBuilder.buildConstraintRulePrompt() {
    h2("工具调用规则")
    numbered {
        item("你应该调用工具回复消息。")
        item("不允许直接输出消息。")
    }
}

suspend fun isRelevanceContinue(histories: List<HistoryRecord>, currentBotId: String): Boolean {
    val promptExecutor by ref<PromptExecutor>()

    val prompt = prompt("relevance-continue") {
        system(
            """
                你是一个群聊行为判断器。

                任务：
                根据“最近的聊天记录”，判断：
                在被 @ 后，机器人是否应该继续参与当前对话。

                注意：
                - 机器人不是主持人。
                - 机器人不会为了存在感发言。
                - 机器人只在自然、有延续性的情况下参与。

                请严格按照以下步骤思考：

                第一步：判断关联度
                如果最新消息：
                1. 直接回应或引用机器人的发言
                2. 明显延续机器人刚参与的话题
                3. 出现机器人擅长接住的情绪变化

                → 视为“强关联”。

                如果只是同一大话题，但已经转为他人之间对话
                → 视为“弱关联”。

                如果已经转为新话题
                → 视为“无关联”。

                第二步：判断插入自然度
                - 当前是否形成他人之间稳定对话流？
                - 机器人插话是否会显得突兀或打断节奏？

                如果会打断节奏，即使是强关联，也判定为“不参与”。

                最终输出规则：
                - 如果 强关联 且 插入自然 → 输出：CONTINUE
                - 否则 → 输出：SILENT

                只输出一个单词：
                CONTINUE
                或
                SILENT

                不要输出解释。
                不要输出分析过程。
                不要输出其他内容。
            """.trimIndent()
        )
        user {
            markdown {
                buildHistoriesPrompt(histories, currentBotId)
            }
        }
    }

    val responses = promptExecutor.execute(prompt, LLMModelsChoice.Lite)
    val content = responses.filterIsInstance<Message.Assistant>().firstOrNull()?.content
    LOG.debug("Relevance continue determine LLM response: $content")
    return content != null && content.contains("CONTINUE")
}

class ChatToolSet(
    val context: Context,
    private val scope: CoroutineScope,
    private val sendMessage: suspend (String) -> Unit,
    private val sendImage: suspend (ByteArray) -> Unit,
    private val sendAt: suspend (Long) -> Unit
) : ToolSet {

    companion object {
        private val log = logger()
    }

    @LLMDescription("消息类型。只能为 TEXT 或 MEME。")
    enum class SentenceType {
        TEXT,
        MEME,
        AT
    }

    @Serializable
    @LLMDescription(
        """
    单条消息结构。
    规则：
    - 不允许使用换行连接多句
    - 如果有多句话，请拆分为多个 Sentence
    """
    )
    data class Sentence(
        @property:LLMDescription("消息类型，必须填写")
        @Required
        val sentenceType: SentenceType,

        @property:LLMDescription("当 type=TEXT 时必须填写文本消息。只能包含一句话。")
        val content: String? = null,

        @property:LLMDescription("当 type=AT 时必须填写At消息。@某人。填写用户ID")
        val at: Long? = null,

        @property:LLMDescription("当 type=MEME 时必须填写表情包标签。用于向量匹配的语义标签。2-6 个字的抽象语义。示例：震惊、无语、嘲讽、大笑")
        val tag: String? = null,

        @property:LLMDescription("当 type=MEME 时必须填写表情包替代文本。若匹配不到表情包时发送的替代文本。必须是自然语言句子。")
        val alt: String? = null
    )

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
    internal suspend fun sendAndReceive(@LLMDescription("回复文本/表情包消息，默认 1~6 句。") sentences: List<Sentence>): String? {
        try {
            val contents = sentences.map {
                when (it.sentenceType) {
                    SentenceType.TEXT -> {
                        it.content ?: it.alt ?: ""
                    }

                    SentenceType.MEME -> {
                        it.alt ?: it.content ?: ""
                    }

                    SentenceType.AT -> {
                        it.at?.let { userId ->
                            "@$userId"
                        } ?: ""
                    }
                }
            }
            EventBus.postSync(
                AgentBeforeSendAndReceiveEvent(
                    context.currentBotId,
                    context.groupId,
                    context.echo,
                    contents
                )
            )

            val recordsMutex = Mutex()
            val records = mutableListOf<HistoryRecord>()

            val relevanceMutex = Mutex()
            var relevanceContinue = false

            val historyChannel = Channel<HistorySavedEvent>(Channel.CONFLATED)
            val historyJob = EventBus.subscribeAsync<HistorySavedEvent>(scope) { event ->
                val record = event.historyRecord
                if (record.groupId == context.groupId && record.userId != context.currentBotId) {
                    recordsMutex.withLock { records += record }
                    if (relevanceMutex.tryLock() && !relevanceContinue) {
                        try {
                            relevanceContinue = isRelevanceContinue(context.histories(), context.currentBotId)
                            if (relevanceContinue) {
                                historyChannel.send(event)
                                historyChannel.close()
                            }
                        } catch (e: Exception) {
                            log.warn("Failed to determine relevance continue", e)
                        } finally {
                            relevanceMutex.unlock()
                        }
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

            suspend fun sendSentence(sentence: Sentence) {
                when (sentence.sentenceType) {
                    SentenceType.TEXT -> {
                        sendMessage(sentence.content ?: sentence.alt ?: sentence.tag ?: "")
                    }

                    SentenceType.MEME -> {
                        val memo = sentence.tag?.let {
                            context.memo(it)
                        }
                        if (memo != null) {
                            sendImage(memo.bytes)
                        } else {
                            sendMessage(sentence.alt ?: sentence.tag ?: sentence.content ?: "")
                        }
                    }

                    SentenceType.AT -> {
                        if (sentence.at != null) {
                            sendAt(sentence.at)
                        } else {
                            sendMessage(sentence.content ?: sentence.alt ?: sentence.tag ?: "")
                        }
                    }
                }
            }

            val deferred = scope.async {
                var skip = false
                var content: String? = null
                for ((i, sentence) in sentences.withIndex()) {
                    if (i == 1) {
                        sendSentence(sentence)
                    } else {
                        select {
                            historyChannel.onReceiveCatching { result ->
                                if (result.isSuccess) {
                                    skip = true
                                    content = result.getOrThrow().historyRecord.content
                                    log.info("Sending, received reply from getHistory: $content")
                                    EventBus.postSync(
                                        AgentReceiveReplyEvent(
                                            context.currentBotId,
                                            context.groupId,
                                            echo = context.echo,
                                            content ?: ""
                                        )
                                    )
                                }
                            }

                            chatChannel.onReceiveCatching { result ->
                                if (result.isSuccess) {
                                    skip = true
                                    content = result.getOrThrow().urgent.input
                                    log.info("Sending, received reply from chat: $content")
                                    EventBus.postSync(
                                        AgentReceiveReplyEvent(
                                            context.currentBotId,
                                            context.groupId,
                                            context.echo,
                                            content ?: ""
                                        )
                                    )
                                }
                            }

                            when (sentence.sentenceType) {
                                SentenceType.TEXT -> {
                                    onTimeout(calcHumanTypingDelay(sentence.content ?: "").milliseconds) {
                                        sendSentence(sentence)
                                    }
                                }

                                SentenceType.MEME -> {
                                    onTimeout(0) {
                                        sendSentence(sentence)
                                    }
                                }

                                SentenceType.AT -> {
                                    onTimeout(0) {
                                        sendSentence(sentence)
                                    }
                                }
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
                        contents
                    )
                )
                if (!skip) {
                    select {
                        historyChannel.onReceiveCatching { result ->
                            if (result.isSuccess) {
                                content = result.getOrThrow().historyRecord.content
                                log.info("Received reply from getHistory: $content")
                                EventBus.postSync(
                                    AgentReceiveReplyEvent(
                                        context.currentBotId,
                                        context.groupId,
                                        echo = context.echo,
                                        content ?: ""
                                    )
                                )
                            }
                        }

                        chatChannel.onReceiveCatching { result ->
                            if (result.isSuccess) {
                                content = result.getOrThrow().urgent.input
                                skip = true
                                log.info("Received reply from chat: $content")
                                EventBus.postSync(
                                    AgentReceiveReplyEvent(
                                        context.currentBotId,
                                        context.groupId,
                                        context.echo,
                                        content ?: ""
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
                Pair(content != null, records)
            }

            val await = deferred.await()
            return if (await.first) {
                val second = await.second
                recordsMutex.withLock {
                    buildString {
                        for (record in second) {
                            appendLine("${record.userId}: ${record.content}")
                        }
                    }
                }
            } else {
                null
            }
        } finally {
            EventBus.postSync(AgentSendAndReceiveFinallyEvent(context.currentBotId, context.groupId, context.echo))
        }
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
                你是在聊天。
                不是在写答案。
                不是在总结。
                不是在完成任务。
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
                            log.warn("BotAgent: Reject grab and dispatch fallback, {}", it)
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
        val promptExecutor by ref<PromptExecutor>()

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

                            val currentBot = BotManage.getBot(event.botId)
                            val groupId = event.groupId
                            val bot = currentBot.refBot

                            val sendMessage: suspend (String) -> Unit = { message ->
                                bot.getGroupOrFail(groupId.toLong()).sendMessage(message)
                            }

                            val sendImage: suspend (ByteArray) -> Unit = { bytes ->
                                bytes.inputStream().use { image ->
                                    bot.getGroupOrFail(groupId.toLong()).sendImage(image)
                                }
                            }

                            val sendAt: suspend (Long) -> Unit = { id ->
                                bot.getGroupOrFail(groupId.toLong()).sendMessage(At(id))
                            }

                            val context = buildContext(event)

                            var chatPoints: List<ChatPoint>? = null
                            buildList {
                                this += async {
                                    try {
                                        if (event.chatPointRule != null) {
                                            log.info("Bot agent build chat point rule")
                                            chatPoints = buildChatPoint(
                                                context.moreHistories(),
                                                event.chatPointRule,
                                                event.botId
                                            )
                                        }
                                    } catch (e: Exception) {
                                        log.warn("build chat point rule error: {}", e.message, e)
                                    }
                                }
                            }.awaitAll()

                            val buildPrompt = buildPrompt(context, chatPoints)
                            val prompt = prompt("constraint") {
                                messages(buildPrompt.messages)
                            }

                            val chatToolSet = ChatToolSet(
                                context,
                                toolScope,
                                sendMessage,
                                sendImage,
                                sendAt
                            )

                            val toolSets = event.toolSets?.invoke(chatToolSet)

                            var requireSend = false

                            val aiAgent = AIAgent(
                                promptExecutor = promptExecutor,
                                agentConfig = AIAgentConfig(
                                    prompt = prompt,
                                    model = LLMModelsChoice.Pro,
                                    maxAgentIterations = 50,
                                ),
                                toolRegistry = ToolRegistry {
                                    tools(chatToolSet.asTools())
                                    if (event.webSearch) {
                                        tools(WebSearchTool.asTools())
                                    }
                                    toolSets?.let { t -> t.forEach { tools(it.asTools()) } }
                                },
                                strategy = strategy("chat") {
                                    val nodeSendInput by nodeLLMRequest()
                                    val nodeExecuteTool by nodeExecuteTool()
                                    val nodeSendToolResult by nodeLLMSendToolResult()

                                    edge(nodeStart forwardTo nodeSendInput)
                                    edge(nodeSendInput forwardTo nodeFinish onAssistantMessage {
                                        requireSend = true
                                        true
                                    })
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
                                        } else {
                                            log.info("Bot agent onLLMCallCompleted")
                                        }
                                    }
                                }
                            }

                            aiAgent.run(event.input ?: DEFAULT_INPUT)

                            if (requireSend) {
                                val emoticon = listOf(
                                    "(×_×)",
                                    "(@_@)",
                                    "(；￣Д￣)",
                                    "(⊙_⊙;)",
                                    "(>_<)",
                                    "(￣□￣;)",
                                    "(╯°□°）╯︵ ┻━┻",
                                    "(⊙＿⊙')",
                                    "(；・∀・)",
                                    "(._.)"
                                )
                                sendMessage(emoticon.random())
                            }
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