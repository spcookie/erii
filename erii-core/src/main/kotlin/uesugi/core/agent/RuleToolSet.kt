package uesugi.core.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
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

    private fun isAdmin(): Boolean = userId in admins

    private fun checkPermission(): String? {
        return if (!isAdmin()) "权限不足：仅管理员可执行此操作" else null
    }

    /**
     * 解析规则定义字符串
     * @return Triple(fileName, content, errorMessage) - errorMessage 为 null 表示解析成功
     */
    private fun parseRuleDefinition(ruleDefinition: String): Triple<String, String, String?> {
        val parts = ruleDefinition.split("::", limit = 2)
        if (parts.size != 2) {
            return Triple("", "", "格式错误：请使用 '文件名::内容' 格式")
        }
        val fileName = parts[0].trim()
        val content = parts[1].trim()
        if (fileName.isBlank()) {
            return Triple("", "", "文件名不能为空")
        }
        if (content.isBlank()) {
            return Triple("", "", "规则内容不能为空")
        }
        return Triple(fileName, content, null)
    }

    @Tool
    @LLMDescription("列出当前 bot+group 的所有规则文件名和内容摘要")
    fun listRules(): String {
        val rules = RuleManager.getRulesForBotGroup(botId, groupId)
        if (rules.isEmpty()) {
            return "当前没有规则文件"
        }
        return buildString {
            appendLine("当前规则列表 (共 ${rules.size} 个):")
            for (rule in rules) {
                appendLine("- ${rule.fileName}")
                val summary = rule.content.take(100).replace("\n", " ")
                appendLine("  摘要: $summary${if (rule.content.length > 100) "..." else ""}")
            }
        }.trim()
    }

    @Tool
    @LLMDescription("读取指定文件名的规则完整内容")
    fun readRule(
        @LLMDescription("规则文件名") fileName: String
    ): String {
        val rules = RuleManager.getRulesForBotGroup(botId, groupId)
        val rule = rules.find { it.fileName == fileName || it.fileName == "$fileName.md" }
            ?: return "未找到规则文件: $fileName"
        return buildString {
            appendLine("规则文件: ${rule.fileName}")
            appendLine("---")
            append(rule.content)
        }
    }

    @Tool
    @LLMDescription("创建新规则。使用 '文件名::内容' 格式，用 :: 分隔文件名和内容")
    fun createRule(
        @LLMDescription("规则定义，格式为 '文件名::内容'") ruleDefinition: String
    ): String {
        checkPermission()?.let { return it }

        val (fileName, content, error) = parseRuleDefinition(ruleDefinition)
        if (error != null) return error

        // 检查是否已存在同名规则
        val existingRules = RuleManager.getRulesForBotGroup(botId, groupId)
        val targetFileName = if (fileName.endsWith(".md")) fileName else "$fileName.md"
        if (existingRules.any { it.fileName == targetFileName }) {
            return "规则文件已存在: $targetFileName，请使用 editRule 修改"
        }

        val meta = RuleMeta(
            global = false,
            botId = botId,
            groupId = groupId
        )

        RuleManager.saveRule(fileName, meta, content)
        return "规则创建成功: $targetFileName"
    }

    @Tool
    @LLMDescription("编辑现有规则。使用 '文件名::新内容' 格式，用 :: 分隔文件名和新内容")
    fun editRule(
        @LLMDescription("规则定义，格式为 '文件名::新内容'") ruleDefinition: String
    ): String {
        checkPermission()?.let { return it }

        val (fileName, newContent, error) = parseRuleDefinition(ruleDefinition)
        if (error != null) return error

        // 检查规则是否存在
        val existingRules = RuleManager.getRulesForBotGroup(botId, groupId)
        val targetFileName = if (fileName.endsWith(".md")) fileName else "$fileName.md"
        val existingRule = existingRules.find { it.fileName == targetFileName }
            ?: return "未找到规则文件: $targetFileName，请先使用 createRule 创建"

        // 检查是否为 classpath 规则（不可编辑）
        if (existingRule.filePath.startsWith("classpath:")) {
            return "无法编辑内置规则: $targetFileName"
        }

        val meta = RuleMeta(
            global = false,
            botId = botId,
            groupId = groupId
        )

        RuleManager.saveRule(fileName, meta, newContent)
        return "规则更新成功: $targetFileName"
    }

    @Tool
    @LLMDescription("删除指定文件名的规则")
    fun deleteRule(
        @LLMDescription("规则文件名") fileName: String
    ): String {
        checkPermission()?.let { return it }

        if (fileName.isBlank()) {
            return "文件名不能为空"
        }

        val deleted = RuleManager.deleteRule(fileName, botId, groupId)
        return if (deleted) {
            "规则删除成功: $fileName"
        } else {
            "删除失败：规则不存在或无法删除（可能是内置规则）"
        }
    }
}
