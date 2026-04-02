package uesugi.core.rule

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.net.URLDecoder
import java.util.jar.JarFile

object RuleLoader {

    private const val FRONTMATTER_DELIMITER = "---"
    private const val DEFAULT_RULES_DIR = "rules"
    private const val CLASSPATH_PREFIX = "classpath:"

    /**
     * 加载所有规则文件
     * - 从系统属性 config.rules.dir 指定的目录加载
     * - 从环境变量 CONFIG_RULES_DIR 指定的目录加载
     * - 从类路径 rules/index.idx 索引指定的规则加载
     */
    fun loadRules(): List<Rule> {
        val rules = mutableListOf<Rule>()

        // 1. 先加载 classpath 下 rules/index.idx 索引指定的规则
        loadFromClasspathIndex(rules)

        // 2. 加载文件系统中的规则
        val rulesDir = resolveConfigDir()
        val rulesDirFile = File(rulesDir)
        if (rulesDirFile.exists() && rulesDirFile.isDirectory) {
            rulesDirFile.listFiles { file -> file.extension == "md" }?.forEach { file ->
                parseRuleFile(file.name, file.readText(), file.absolutePath).let { rules.add(it) }
            }
        }

        return rules
    }

    private fun resolveConfigDir(configDir: String? = null): String {
        return configDir
            ?: System.getProperty("config.rules.dir")
            ?: System.getenv("CONFIG_RULES_DIR")
            ?: DEFAULT_RULES_DIR
    }

    /**
     * 从 classpath 索引文件加载规则
     * 读取 rules/index.idx 索引文件，按索引加载指定的 md 文件
     */
    private fun loadFromClasspathIndex(rules: MutableList<Rule>) {
        val classLoader = Thread.currentThread().contextClassLoader ?: RuleLoader::class.java.classLoader
        val indexResource = classLoader.getResource("$DEFAULT_RULES_DIR/index.idx")
        if (indexResource == null) {
            // 索引文件不存在，尝试从 JAR 枚举方式加载
            loadRulesFromJar(classLoader, rules)
            return
        }

        try {
            val indexContent = indexResource.openStream().bufferedReader().readText()
            val fileNames = indexContent.lines().map { it.trim() }.filter { it.isNotEmpty() && it.endsWith(".md") }

            for (fileName in fileNames) {
                val resourcePath = "$DEFAULT_RULES_DIR/$fileName"
                val resource = classLoader.getResource(resourcePath)
                if (resource != null) {
                    try {
                        val content = resource.openStream().bufferedReader().readText()
                        val classpathPath = "$CLASSPATH_PREFIX$resourcePath"
                        parseRuleFile(fileName, content, classpathPath).let { rules.add(it) }
                    } catch (_: Exception) {
                        // 忽略单个文件加载失败
                    }
                }
            }
        } catch (_: Exception) {
            // 索引加载失败，尝试降级到枚举方式
            loadRulesFromJar(classLoader, rules)
        }
    }

    /**
     * 从 classpath 加载规则（支持 JAR 和文件系统两种形式）
     */
    private fun loadRulesFromJar(classLoader: ClassLoader, rules: MutableList<Rule>) {
        val resources = classLoader.getResources(DEFAULT_RULES_DIR)
        while (resources.hasMoreElements()) {
            val resource = resources.nextElement()
            try {
                val resourceUrl = resource.toString()
                when {
                    // JAR 内资源：遍历 JAR 条目
                    resourceUrl.startsWith("jar:") -> {
                        val jarFileUrl = resourceUrl.substring(4, resourceUrl.indexOf("!")).removePrefix("file:")
                        val jarFile = JarFile(URLDecoder.decode(jarFileUrl, "UTF-8"))
                        jarFile.use { jarFile ->
                            val entries = jarFile.entries()
                            while (entries.hasMoreElements()) {
                                val entry = entries.nextElement()
                                val entryName = entry.name
                                // 检查是否在 rules 目录下且是 .md 文件
                                if (entryName.startsWith(DEFAULT_RULES_DIR) &&
                                    entryName.endsWith(".md") &&
                                    !entry.isDirectory
                                ) {
                                    val fileName = entryName.substring(entryName.lastIndexOf("/") + 1)
                                    jarFile.getInputStream(entry).use { inputStream ->
                                        val content = inputStream.bufferedReader().readText()
                                        val classpathPath = "$CLASSPATH_PREFIX$entryName"
                                        parseRuleFile(fileName, content, classpathPath).let { rules.add(it) }
                                    }
                                }
                            }
                        }
                    }
                    // 文件系统资源（IDE 开发环境）
                    resourceUrl.startsWith("file:") -> {
                        val dir = File(resource.toURI())
                        if (dir.isDirectory) {
                            dir.listFiles { file -> file.extension == "md" }?.forEach { file ->
                                val classpathPath = "$CLASSPATH_PREFIX$DEFAULT_RULES_DIR/${file.name}"
                                parseRuleFile(file.name, file.readText(), classpathPath).let { rules.add(it) }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // 忽略单个资源加载失败，继续处理其他资源
            }
        }
    }

    /**
     * 解析单个规则文件
     */
    private fun parseRuleFile(fileName: String, fileContent: String, filePath: String): Rule {
        val (meta, content) = parseFrontmatter(fileContent)
        return Rule(
            fileName = fileName,
            meta = meta,
            content = content.trim(),
            filePath = filePath
        )
    }

    /**
     * 解析 YAML Frontmatter
     * 返回 (RuleMeta, Markdown正文)
     */
    private fun parseFrontmatter(content: String): Pair<RuleMeta, String> {
        val trimmed = content.trim()

        if (!trimmed.startsWith(FRONTMATTER_DELIMITER)) {
            return RuleMeta() to content
        }

        val secondDelimiterIndex = trimmed.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length)
        if (secondDelimiterIndex == -1) {
            return RuleMeta() to content
        }

        val yamlContent = trimmed.substring(FRONTMATTER_DELIMITER.length, secondDelimiterIndex).trim()
        val markdownContent = trimmed.substring(secondDelimiterIndex + FRONTMATTER_DELIMITER.length)

        val meta = parseYamlToMeta(yamlContent)
        return meta to markdownContent
    }

    /**
     * 使用 SnakeYAML 解析元数据
     */
    private fun parseYamlToMeta(yamlContent: String): RuleMeta {
        if (yamlContent.isBlank()) {
            return RuleMeta()
        }

        return try {
            val yaml = Yaml()
            val map: Map<String, Any?> = yaml.load(yamlContent) ?: emptyMap()

            RuleMeta(
                global = (map["global"] as? Boolean) ?: false,
                botId = map["botId"]?.toString(),
                groupId = map["groupId"]?.toString()
            )
        } catch (_: Exception) {
            RuleMeta()
        }
    }

    /**
     * 获取文件系统规则目录
     */
    fun getRulesDirectory(): File {
        val rulesDir = resolveConfigDir()
        return File(rulesDir).also {
            if (!it.exists()) it.mkdirs()
        }
    }
}
