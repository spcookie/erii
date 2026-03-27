package uesugi.core.agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.MarkdownContentBuilder
import ai.koog.prompt.markdown.markdown
import com.nlf.calendar.Solar
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import uesugi.BotManage
import uesugi.common.DateTimeFormat
import uesugi.common.HistoryRecord
import uesugi.core.state.evolution.LearnedVocabEntity
import uesugi.core.state.memory.FactsEntity
import uesugi.core.state.memory.SummaryEntity
import uesugi.core.state.memory.UserProfileEntity
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
internal suspend fun buildPrompt(context: Context): Prompt {
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

                buildHistoriesPrompt(transient.histories, context.currentBotId)
            }
        }

    }
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

fun MarkdownContentBuilder.buildHistoriesPrompt(histories: List<HistoryRecord>, currentBotId: String) {
    if (histories.isNotEmpty()) {
        header(2, "最近群聊记录")
        line { text("输入：按时间顺序排列的聊天记录，每条包含：发言者昵称和ID，消息内容") }
        line { text("注意：带有*号的表示你自己的发言") }
        bulleted {
            for (history in histories) {
                item {
                    line {
                        text(
                            "${DateTimeFormat.format(history.createdAt)} ${
                                if (currentBotId == history.userId) "*" + BotManage.getBot(
                                    history.userId
                                ).role.name else history.nick
                            }(${history.userId})：${history.content}"
                        )
                    }
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
    h2("约束规则【重要】")
    bulleted {
        item { line { text("你是在聊天，不是在写答案，不是在总结。") } }
        item { line { text("你只能调用工具回复消息或执行任务，但不要一直调用工具频繁发言。") } }
        item { line { text("绝对禁止直接在模型输出中写要发送的文字；所有对外消息必须调用发言工具。") } }
        item { line { text("如果你判断本次不应对外发言，请调用 sendSilent() 作为本次唯一/最终调用，或者直接返回 SILENT") } }
        item { line { text("不应该总是使用文本或表情。") } }
        item { line { text("在群聊中，你应该像真人一样使用多种表达方式。") } }
    }
}