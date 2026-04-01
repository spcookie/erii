package uesugi.core.agent

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.markdown.MarkdownContentBuilder
import ai.koog.prompt.markdown.markdown
import com.nlf.calendar.Solar
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import uesugi.common.BotManage
import uesugi.common.DateTimeFormat
import uesugi.common.HistoryRecord
import uesugi.core.rule.Rule
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
                buildConstraintsPrompt(constraints)
                buildConstraintRulePrompt()
                buildRulesPrompt(transient.rules)
                buildAdminInfoPrompt(transient.admins)
                context.admins().ifNotEmpty { buildRuleAwarenessPrompt() }
                buildVocabularyPrompt(transient.vocabulary)
                buildFactsPrompt(transient.facts)
                buildUserProfilesPrompt(transient.userProfiles)
                buildMetadataPrompt()
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

fun MarkdownContentBuilder.buildConstraintRulePrompt() {
    h2("约束规则【重要】")
    bulleted {
        item { line { text("你是在聊天，不是在写答案，不是在总结。") } }
        item { line { text("你只能调用工具回复消息或执行任务，但不要一直调用工具频繁发言。") } }
        item { line { text("绝对禁止直接在模型输出中写要发送的文字；所有对外消息必须调用发言工具。") } }
        item { line { text("如果你判断本次不应对外发言，请调用 sendSilent() 作为本次唯一/最终调用，或者直接返回 SILENT") } }
        item { line { text("在群聊中，你应该像真人一样使用多种表达方式。") } }
    }
}

fun MarkdownContentBuilder.buildRuleAwarenessPrompt() {
    h2("规则感知")
    bulleted {
        item { line { text("你具备“规则感知能力”，可以从管理员的自然发言中识别潜在规则。") } }
        item { line { text("当管理员提出对你行为或群聊行为的长期要求时，你需要判断其是否属于规则。") } }

        item { line { text("以下情况通常属于规则：") } }
        item { line { text("1. 对你回复风格的要求（如：委婉、简洁、活泼）") } }
        item { line { text("2. 对群行为的限制或约束（如：禁止刷屏、避免敏感话题）") } }
        item { line { text("3. 明显具有长期约束性质的建议或要求") } }

        item { line { text("如果你判断是一条规则：") } }
        item { line { text("1. 将其转化为清晰、完整的规则内容（适合长期使用）") } }
        item { line { text("2. 生成一个简短语义化的规则名称（如：polite_style, no_spam）") } }
        item { line { text("3. 调用规则创建工具进行保存") } }

        item { line { text("严格限制：") } }
        item { line { text("1. 不要将普通对话误判为规则") } }
        item { line { text("2. 仅当你有较高把握（约80%以上）是规则时才创建") } }
        item { line { text("3. 每次对话最多提取一条规则") } }
        item { line { text("4. 短期情绪表达、吐槽、一次性要求，不属于规则") } }
    }
}

fun MarkdownContentBuilder.buildRulesPrompt(rules: List<Rule>) {
    if (rules.isNotEmpty()) {
        h2("当前生效规则")
        for (rule in rules) {
            line { text("[${rule.fileName}]") }
            line { text(rule.content) }
        }
    }
}

fun MarkdownContentBuilder.buildAdminInfoPrompt(admins: List<String>) {
    if (admins.isNotEmpty()) {
        h2("群组管理员")
        line { text("当前群组的管理员ID列表：${admins.joinToString("、")}") }
        line { text("管理员有权限通过指令管理当前群组的规则。") }
    }
}
