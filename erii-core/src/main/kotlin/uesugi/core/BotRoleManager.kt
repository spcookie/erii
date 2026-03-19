package uesugi.core

import org.yaml.snakeyaml.Yaml
import uesugi.common.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * BotRole 管理器，负责从配置文件动态加载角色
 */
object BotRoleManager {
    private val roles = ConcurrentHashMap<String, BotRole>()
    private val log = logger()

    /**
     * 加载所有角色配置
     * @param configDir 配置文件目录，支持：
     *  - 系统属性 -Dbotrole.dir
     *  - 环境变量 BOTROLE_DIR
     *  - 默认值 "botroles"（相对于 classpath）
     */
    fun loadRoles(configDir: String? = null) {
        val dir = resolveConfigDir(configDir)
        log.info("开始加载 BotRole 配置文件，目录: $dir")

        val resourceDir = BotRoleManager::class.java.classLoader.getResource(dir)
        if (resourceDir == null) {
            log.warn("BotRole 配置目录不存在: $dir")
            return
        }

        val dirFile = if (resourceDir.protocol == "file") {
            File(resourceDir.path)
        } else {
            // 从 JAR 中读取
            null
        }

        if (dirFile != null && dirFile.isDirectory) {
            loadFromDirectory(dirFile)
        } else {
            loadFromClasspath(dir)
        }

        log.info("BotRole 加载完成，共加载 ${roles.size} 个角色: ${roles.keys}")
    }

    private fun resolveConfigDir(configDir: String?): String {
        return configDir
            ?: System.getProperty("botrole.dir")
            ?: System.getenv("BOTROLE_DIR")
            ?: "botroles"
    }

    private fun loadFromDirectory(dir: File) {
        dir.listFiles { file -> file.extension == "md" }?.forEach { file ->
            try {
                val content = file.readText()
                val role = parseMarkdown(content, file.nameWithoutExtension)
                if (role != null) {
                    roles[role.id] = role
                    log.info("已加载 BotRole: ${role.id} (${role.name})")
                }
            } catch (e: Exception) {
                log.error("加载 BotRole 配置文件失败: ${file.name}", e)
            }
        }
    }

    private fun loadFromClasspath(dir: String) {
        val classLoader = BotRoleManager::class.java.classLoader
        val resources = classLoader.getResources(dir)
        while (resources.hasMoreElements()) {
            val resource = resources.nextElement()
            try {
                val jarUrl = resource.toString()
                // 获取 JAR 文件并列出条目
                if (jarUrl.startsWith("jar:")) {
                    val jarFileUrl = jarUrl.substring(4, jarUrl.indexOf("!"))
                    val jarFile = java.util.jar.JarFile(java.net.URLDecoder.decode(jarFileUrl, "UTF-8"))
                    val entries = jarFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val entryName = entry.name
                        // 检查是否在 botroles 目录下且是 .md 文件
                        if (entryName.startsWith(dir) && entryName.endsWith(".md") && !entry.isDirectory) {
                            val fileName = entryName.substring(entryName.lastIndexOf("/") + 1)
                            val inputStream = jarFile.getInputStream(entry)
                            inputStream.use { inputStream ->
                                val content = inputStream.bufferedReader().readText()
                                val role = parseMarkdown(content, fileName.removeSuffix(".md"))
                                if (role != null) {
                                    roles[role.id] = role
                                    log.info("已加载 BotRole: ${role.id} (${role.name})")
                                }
                            }
                        }
                    }
                    jarFile.close()
                }
            } catch (e: Exception) {
                log.error("读取 BotRole 资源失败: $resource", e)
            }
        }
    }

    /**
     * 解析 Markdown + Front Matter 格式的配置文件
     */
    private fun parseMarkdown(content: String, defaultId: String): BotRole? {
        // 分离 Front Matter 和正文
        val fmRegex = Regex("^---\\n([\\s\\S]*?)\\n---\\n([\\s\\S]*)$")
        val match = fmRegex.find(content) ?: return null

        val frontMatter = match.groupValues[1]
        val body = match.groupValues[2].trim()

        // 解析 YAML
        val yaml = Yaml()

        @Suppress("UNCHECKED_CAST")
        val data = yaml.load<Map<String, Any>>(frontMatter)

        val id = (data["id"] as? String) ?: defaultId
        val name = (data["name"] as? String) ?: id
        val character = (data["character"] as? String) ?: ""
        val emoticonName = (data["emoticon"] as? String)?.uppercase()
        val emoticon = try {
            emoticonName?.let { EmotionalTendencies.valueOf(it) } ?: EmotionalTendencies.MILDNESS
        } catch (_: IllegalArgumentException) {
            log.warn("未知的 EmotionalTendencies: $emoticonName，使用默认值 MILDNESS")
            EmotionalTendencies.MILDNESS
        }

        // 组装 personality，将 body 作为人格描述的一部分
        val personality = """
        你是群聊中的一名成员，名字叫 $name。
        你在群聊中的ID是：{{botId}}。

        $body

        ## 话题偏好

        $character
        """.trimIndent()

        return ConfigBotRole(
            id = id,
            name = name,
            personalityTemplate = personality,
            character = character,
            emoticon = emoticon
        )
    }

    /**
     * 获取指定 ID 的角色
     */
    fun getRole(id: String): BotRole? = roles[id]

    /**
     * 获取所有已加载的角色
     */
    fun getAllRoles(): Map<String, BotRole> = roles.toMap()

    /**
     * 获取默认角色，如果未配置则返回 Erii
     */
    fun getDefaultRole(): BotRole = roles["erii"] ?: Erii

    /**
     * 重新加载角色配置
     */
    fun reload(configDir: String? = null) {
        roles.clear()
        loadRoles(configDir)
    }
}
