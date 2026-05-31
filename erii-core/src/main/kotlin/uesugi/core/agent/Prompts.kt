package uesugi.core.agent

import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.MarkdownContentBuilder
import ai.koog.prompt.markdown.markdown
import com.nlf.calendar.Solar
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import uesugi.common.BotManage
import uesugi.common.data.HistoryRecord
import uesugi.common.data.MessageType
import uesugi.common.toolkit.DateTimeFormat
import uesugi.core.rule.Rule
import uesugi.core.state.evolution.LearnedVocabEntity
import uesugi.core.state.memory.FactsEntity
import uesugi.core.state.memory.UserProfileEntity
import uesugi.core.state.summary.SummaryEntity
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
                buildConstraintRulePrompt()
                buildRulesPrompt(transient.rules)
                transient.admins.ifNotEmpty {
                    buildAdminInfoPrompt(transient.admins)
                    buildRuleAwarenessPrompt()
                }
                buildMemeAwarenessPrompt(transient.memes)
                buildConstraintsPrompt(constraints)
                buildVocabularyPrompt(transient.vocabulary)
                buildUserProfilesPrompt(transient.userProfiles)
                buildFactsPrompt(transient.facts)
            }
        }
        user {
            markdown {
                buildMetadataPrompt()
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
        h2("群聊常用语（可参考语气与场景，自然融入对话，不必每条都用）")
        for (learnedVocabEntity in vocabulary) {
            bulleted {
                item {
                    line { text("词：${learnedVocabEntity.word}, 含义：${learnedVocabEntity.meaning}，例子：${learnedVocabEntity.example}") }
                }
            }
        }
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
        line { text("输入：按时间顺序排列的聊天记录，每条包含：[发言者昵称](发言者ID): 消息内容") }
        line { text("图片：包含图片ID：image_id") }
        line { text("注意：带有*号的表示你自己的发言") }
        bulleted {
            for (history in histories) {
                item {
                    line {
                        text(
                            "${DateTimeFormat.format(history.createdAt)} [${
                                if (currentBotId == history.userId) "*" + BotManage.getBot(
                                    history.userId
                                ).role.name else history.nick
                            }](${history.userId}): ${if (history.messageType == MessageType.IMAGE) "[image_id:" + history.id + "]" else ""} ${history.content}"
                        )
                    }
                }
            }
        }
    }
}

fun MarkdownContentBuilder.buildConstraintRulePrompt() {
    h2("重要约束")
    bulleted {
        item { line { text("工具调用完毕后，请输出 <end_tern>") } }
        item { line { text("所有对外消息必须调用发言工具。") } }
        item { line { text("如果你判断本次不应对外发言，请调用 sendSilent() 作为本次唯一/最终调用，或者直接返回 SILENT") } }
        item { line { text("你是在聊天，不是在写答案，不是在总结。") } }
        item { line { text("在群聊中，你应该像真人一样使用多种表达方式。") } }
    }
}

fun MarkdownContentBuilder.buildRuleAwarenessPrompt() {
    h2("规则感知")
    bulleted {
        item { line { text("你具备规则感知能力，可以从管理员的自然发言中识别潜在规则。") } }
        item { line { text("当管理员提出对你行为或群聊行为的长期要求时，你需要判断其是否属于规则。") } }

        item { line { text("以下情况通常属于规则：") } }
        item { line { text("1. 对你回复风格的要求（如：委婉、简洁、活泼）") } }
        item { line { text("2. 对群行为的限制或约束（如：禁止刷屏、避免敏感话题）") } }
        item { line { text("3. 明显具有长期约束性质的建议或要求") } }

        item { line { text("理解管理员的规则操作意图：") } }
        item { line { text("1. 创建规则：管理员表达长期要求、约束或偏好时（如以后别这样、记住不能xxx），调用 createRule（格式：规则名::规则内容）") } }
        item { line { text("2. 修改规则：管理员说要更改某规则时（如把xxx规则改成yyy），调用 editRule（格式：规则名::新内容）") } }
        item { line { text("3. 删除规则：管理员说要移除、删除某规则时，调用 deleteRule") } }
        item { line { text("4. 查看规则：管理员想了解当前有哪些规则时，调用 listRules") } }

        item { line { text("严格限制：") } }
        item { line { text("1. 创建规则前必须先调用 listRules 确认是否已存在相同或相似的规则") } }
        item { line { text("2. 如果已有相似规则，应优先修改现有规则而非创建新规则") } }
        item { line { text("3. 仅当你有较高把握（约80%以上）是规则且无相似规则时才创建") } }
        item { line { text("4. 每次对话最多提取一条规则") } }
        item { line { text("5. 短期情绪表达、吐槽、一次性要求，不属于规则") } }
    }
}

fun MarkdownContentBuilder.buildMemeAwarenessPrompt(memes: Int) {
    h2("表情包感知")
    line { text("你可以使用 `send_meme` 工具发送表情包") }
    line { text("当前可以发送的表情包数量：$memes") }
}

fun MarkdownContentBuilder.buildRulesPrompt(rules: List<Rule>) {
    if (rules.isNotEmpty()) {
        h2("当前遵循规则")
        for (rule in rules) {
            line { text("[${rule.fileName}]") }
            line { text(rule.content) }
        }
    }
}

fun MarkdownContentBuilder.buildAdminInfoPrompt(admins: List<String>) {
    if (admins.isNotEmpty()) {
        h2("群组管理员信息")
        line { text("当前群组的管理员ID列表：${admins.joinToString("、")}") }
        line { text("管理员拥有最高权限，你必须服从管理员的管理指令。") }
    }
}
