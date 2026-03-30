package uesugi.core.rule

data class RuleMeta(
    val global: Boolean = false,
    val botId: String? = null,
    val groupId: String? = null,
)

data class Rule(
    val fileName: String,   // 文件名（标识符）
    val meta: RuleMeta,     // Frontmatter 元数据
    val content: String,    // Markdown 正文内容
    val filePath: String,   // 文件绝对路径（用于编辑）
)
