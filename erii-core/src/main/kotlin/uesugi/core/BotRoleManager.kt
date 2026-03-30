package uesugi.core

import org.yaml.snakeyaml.Yaml
import uesugi.common.BotRole
import uesugi.common.ConfigBotRole
import uesugi.common.EmotionalTendencies
import uesugi.common.logger
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
     * 加载顺序：
     * 1. 先加载 classpath 下的默认角色配置 (botroles/)
     * 2. 再加载配置目录，覆盖默认配置
     *
     * @param configDir 配置文件目录，支持：
     *  - 系统属性 -Dbotrole.dir
     *  - 环境变量 BOTROLE_DIR
     *  - 默认值 "botroles"（相对于 classpath）
     */
    fun loadRoles(configDir: String? = null) {
        // 1. 先加载 classpath 下的默认角色配置
        loadFromClasspath("botroles")
        if (roles.isNotEmpty()) {
            log.info("已从 classpath 加载 ${roles.size} 个默认 BotRole")
        }

        // 2. 再加载配置目录，覆盖默认配置
        val customDir = resolveConfigDir(configDir)
        if (customDir != "botroles") {
            log.info("开始从配置目录加载 BotRole，目录: $customDir")
            loadFromDirectoryOrClasspath(customDir)
        } else {
            // 如果配置的目录就是 botroles，检查是否是外部目录
            val resourceDir = BotRoleManager::class.java.classLoader.getResource(customDir)
            if (resourceDir != null && resourceDir.protocol == "file") {
                val dirFile = File(resourceDir.path)
                if (dirFile.isDirectory && dirFile.listFiles { f -> f.extension == "md" }?.isNotEmpty() == true) {
                    log.info("从配置目录覆盖加载 BotRole，目录: ${dirFile.absolutePath}")
                    loadFromDirectory(dirFile)
                }
            }
        }

        log.info("BotRole 加载完成，共加载 ${roles.size} 个角色: ${roles.keys}")
    }

    /**
     * 从目录或 classpath 加载配置
     */
    private fun loadFromDirectoryOrClasspath(dir: String) {
        val resourceDir = BotRoleManager::class.java.classLoader.getResource(dir)
        if (resourceDir == null) {
            log.warn("BotRole 配置目录不存在: $dir")
            return
        }

        val dirFile = if (resourceDir.protocol == "file") {
            File(resourceDir.path)
        } else {
            null
        }

        if (dirFile != null && dirFile.isDirectory) {
            loadFromDirectory(dirFile)
        } else {
            loadFromClasspath(dir)
        }
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
                    log.info("从目录加载 BotRole: ${role.id} (${role.name})")
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
                                    log.info("从类路径文件加载 BotRole: ${role.id} (${role.name})")
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
        
        你不是聊天机器人。你正在成为某个人。

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
     * 获取默认角色（erii）
     */
    fun getDefaultRole(): BotRole = roles["erii"]
        ?: throw IllegalStateException("未配置默认 BotRole (erii)")

    /**
     * 重新加载角色配置
     */
    fun reload(configDir: String? = null) {
        roles.clear()
        loadRoles(configDir)
    }
}
