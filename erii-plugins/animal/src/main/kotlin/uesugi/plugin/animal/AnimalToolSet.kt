package uesugi.plugin.animal

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import com.github.ajalt.clikt.core.main
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import uesugi.common.BotManage
import uesugi.common.toolkit.BrowserScraperHolder
import uesugi.common.toolkit.ConfigHolder
import uesugi.plugin.animal.service.AnimalService
import uesugi.plugin.animal.store.AnimalStore
import uesugi.spi.MetaToolSet
import uesugi.spi.getGroup

class AnimalToolSet(
    private val store: AnimalStore,
    private val service: AnimalService,
    private val serverPort: Int,
    private val serverBasePath: String,
) : MetaToolSet {

    private val log = KotlinLogging.logger {}

    private fun createAnimalContext(): AnimalContext {
        val userId = MetaToolSet.meta.senderId?.toLongOrNull() ?: 0L
        val senderNick = MetaToolSet.meta.senderId ?: "User"
        val botId = MetaToolSet.meta.botId
        val configKey = BotManage.getConfigKey(botId)
        val botConfig = ConfigHolder.getOnebotBots()[configKey]
        // 内网 host 用于 Playwright，外网 host 用于 JSON 访问
        val serverHost = botConfig?.serverHost ?: "hostmachine"
        val externalHost = botConfig?.externalHost ?: serverHost

        return AnimalContext(
            store = store,
            service = service,
            groupId = MetaToolSet.meta.groupId,
            senderId = userId,
            senderNick = senderNick,
            sendMessage = { msg ->
                runBlocking {
                    MetaToolSet.meta.getGroup().sendMessage(msg)
                }
            },
            createImage = { bytes ->
                runBlocking {
                    val imageRes = bytes.inputStream().use { it.toExternalResource() }
                    imageRes.use { res ->
                        MetaToolSet.meta.getGroup().uploadImage(res)
                    }
                }
            },
            serverUrl = "http://${externalHost}:${serverPort}${serverBasePath}",
            takeScreenshot = { url ->
                // 外网 URL 替换为内网 host 给 Playwright 截图
                val playwrightUrl = url.replace("http://${externalHost}", "http://${serverHost}")
                BrowserScraperHolder.getInstance().takeFullScreenshot(
                    url = playwrightUrl,
                    width = 100,
                    height = 30,
                    quality = 100
                )
            }
        )
    }

    private fun runCommand(argv: List<String>): String? {
        return try {
            val ctx = createAnimalContext()
            val parser = AnimalArgParser()
            parser.init(MetaToolSet.meta, ctx)
            parser.main(argv)
            null
        } catch (e: Exception) {
            log.error(e) { "Failed to run command: ${argv.joinToString(" ")}" }
            "执行失败：${e.message}"
        }
    }

    @Tool
    @LLMDescription("注册用户，获取一只随机宠物")
    fun registerAnimal(): String? {
        return runCommand(listOf("register"))
    }

    @Tool
    @LLMDescription("查看用户的宠物列表")
    fun listAnimals(): String? {
        return runCommand(listOf("list"))
    }

    @Tool
    @LLMDescription("查看宠物农场")
    fun viewFarm(): String? {
        return runCommand(listOf("farm"))
    }

    @Tool
    @LLMDescription("查看单只宠物的详细信息，需要宠物ID参数")
    fun viewAnimal(petId: Long): String? {
        return runCommand(listOf("line", petId.toString()))
    }

    @Tool
    @LLMDescription("使用金币抽宠物，100金币抽1次，1000金币抽10次")
    fun drawAnimal(count: Int = 1): String? {
        return runCommand(listOf("draw", count.toString()))
    }

    @Tool
    @LLMDescription("查看用户金币余额")
    fun viewCoins(): String? {
        return runCommand(listOf("coins"))
    }

    @Tool
    @LLMDescription("售卖宠物，需要宠物ID参数")
    fun sellAnimal(petId: Long): String? {
        return runCommand(listOf("sell", petId.toString()))
    }

    @Tool
    @LLMDescription("设置农场显示的宠物，visible=true显示，visible=false隐藏")
    fun setFarmPet(petId: Long, visible: Boolean): String? {
        val visibleStr = if (visible) "on" else "off"
        return runCommand(listOf("setfarm", petId.toString(), visibleStr))
    }

    @Tool
    @LLMDescription("查看用户已解锁的背景列表")
    fun listFields(): String? {
        return runCommand(listOf("field", "list"))
    }

    @Tool
    @LLMDescription("设置背景，需要背景类型参数如 SNOWY_FIELD")
    fun setField(fieldType: String): String? {
        return runCommand(listOf("field", "set", fieldType))
    }
}