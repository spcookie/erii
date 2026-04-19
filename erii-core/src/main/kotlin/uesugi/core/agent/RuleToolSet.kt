package uesugi.core.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import io.github.oshai.kotlinlogging.KotlinLogging
import uesugi.core.rule.RuleManager
import uesugi.core.rule.RuleMeta

/**
 * 规则管理工具集
 * 允许 AI Agent 管理当前 bot+group 的规则文件
 */
class RuleToolSet(
    private val botId: String,
    private val groupId: String,
    private val userId: String,
    private val admins: List<String>,
) : ToolSet {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private fun ensureAdmin(): String? = if (userId in admins) null else "权限不足：仅管理员可执行此操作"

    private fun normalizeFileName(fileName: String) = if (fileName.endsWith(".md")) fileName else "$fileName.md"

    private fun buildRuleMeta() = RuleMeta(global = false, botId = botId, groupId = groupId)

    private fun parseRuleDefinition(ruleDefinition: String): Triple<String, String, String?> {
        val parts = ruleDefinition.split("::", limit = 2)
        if (parts.size != 2) return Triple("", "", "格式错误：请使用 '文件名::内容' 格式")
        val (fileName, content) = parts[0].trim() to parts[1].trim()
        if (fileName.isBlank()) return Triple("", "", "文件名不能为空")
        if (content.isBlank()) return Triple("", "", "规则内容不能为空")
        return Triple(fileName, content, null)
    }

    @Tool
    @LLMDescription("列出当前 bot+group 的所有规则文件名和内容摘要")
    fun listRules(): String {
        val rules = RuleManager.getRulesForBotGroup(botId, groupId)
        if (rules.isEmpty()) return "当前没有规则文件"
        return buildString {
            appendLine("当前规则列表 (共 ${rules.size} 个):")
            rules.forEach { rule ->
                appendLine("- ${rule.fileName}")
                val summary = rule.content.take(100).replace("\n", " ")
                appendLine("  摘要: $summary${if (rule.content.length > 100) "..." else ""}")
            }
        }.trim()
    }

    @Tool
    @LLMDescription("读取指定文件名的规则完整内容")
    fun readRule(@LLMDescription("规则文件名") fileName: String): String {
        val rules = RuleManager.getRulesForBotGroup(botId, groupId)
        val rule = rules.find { it.fileName == fileName || it.fileName == normalizeFileName(fileName) }
            ?: return "未找到规则文件: $fileName"
        return buildString {
            appendLine("规则文件: ${rule.fileName}")
            appendLine("---")
            append(rule.content)
        }
    }

    @Tool
    @LLMDescription("创建新规则。使用 '文件名::内容' 格式，用 :: 分隔文件名和内容")
    fun createRule(@LLMDescription("规则定义，格式为 '文件名::内容'") ruleDefinition: String): String {
        ensureAdmin()?.let { return it }

        val (fileName, content, error) = parseRuleDefinition(ruleDefinition)
        if (error != null) return error

        val targetFileName = normalizeFileName(fileName)
        if (RuleManager.getRulesForBotGroup(botId, groupId).any { it.fileName == targetFileName }) {
            return "规则文件已存在: $targetFileName，请使用 editRule 修改"
        }

        RuleManager.saveRule(fileName, buildRuleMeta(), content)
        log.info { "Rule created: botId=$botId, groupId=$groupId, userId=$userId, fileName=$targetFileName" }
        return "规则创建成功: $targetFileName"
    }

    @Tool
    @LLMDescription("编辑现有规则。使用 '文件名::新内容' 格式，用 :: 分隔文件名和新内容")
    fun editRule(@LLMDescription("规则定义，格式为 '文件名::新内容'") ruleDefinition: String): String {
        ensureAdmin()?.let { return it }

        val (fileName, newContent, error) = parseRuleDefinition(ruleDefinition)
        if (error != null) return error

        val targetFileName = normalizeFileName(fileName)
        val existingRule = RuleManager.getRulesForBotGroup(botId, groupId).find { it.fileName == targetFileName }
            ?: return "未找到规则文件: $targetFileName，请先使用 createRule 创建"

        if (existingRule.filePath.startsWith("classpath:")) {
            return "无法编辑内置规则: $targetFileName"
        }

        RuleManager.saveRule(fileName, buildRuleMeta(), newContent)
        log.info { "Rule updated: botId=$botId, groupId=$groupId, userId=$userId, fileName=$targetFileName" }
        return "规则更新成功: $targetFileName"
    }

    @Tool
    @LLMDescription("删除指定文件名的规则")
    fun deleteRule(@LLMDescription("规则文件名") fileName: String): String {
        ensureAdmin()?.let { return it }
        if (fileName.isBlank()) return "文件名不能为空"

        val deleted = RuleManager.deleteRule(fileName, botId, groupId)
        return if (deleted) {
            log.info { "Rule deleted: botId=$botId, groupId=$groupId, userId=$userId, fileName=$fileName" }
            "规则删除成功: $fileName"
        } else {
            "删除失败：规则不存在或无法删除（可能是内置规则）"
        }
    }
}
