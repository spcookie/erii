package uesugi.plugin.animal.cmd

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.buildMessageChain
import uesugi.plugin.animal.core.FieldType
import uesugi.plugin.animal.service.AnimalService
import uesugi.plugin.animal.store.AnimalStore
import uesugi.spi.ArgParserHolder
import uesugi.spi.Meta

data class AnimalContext(
    val store: AnimalStore,
    val service: AnimalService,
    val groupId: String,
    val senderId: Long,
    val senderNick: String,
    val sendMessage: (MessageChain) -> Unit,
    val sendImage: (ByteArray) -> Unit,
    val serverUrl: String,
    val takeScreenshot: (String) -> ByteArray?
)

class AnimalArgParser : ArgParserHolder<AnimalContext>() {

    override fun init(meta: Meta, context: AnimalContext) {
        currentContext.findOrSetObject { context }
        subcommands(
            Register(),
            ListPets(),
            Farm(),
            Coins(),
            Line(),
            Draw(),
            Sell(),
            SetFarm(),
            Field()
        )
    }

    override fun run() {
        // no-op
    }
}

class Register : CliktCommand("register") {
    override fun run() {
        val ctx = currentContext.findObject<AnimalContext>() ?: return
        runBlocking {
            val user = ctx.service.registerUser(ctx.groupId, ctx.senderId, ctx.senderNick)
            val pet = user.personas.firstOrNull()
            val petTypeName = pet?.getType()?.name ?: "未知宠物"

            // 发送注册成功消息
            ctx.sendMessage(buildMessageChain {
                +"注册成功！你获得了 $petTypeName！"
            })

            // 发送宠物截图
            pet?.let {
                val url = "${ctx.serverUrl}/pet/${ctx.groupId}/${ctx.senderId}/${it.id}"
                val screenshot = ctx.takeScreenshot(url)
                screenshot?.let { bytes -> ctx.sendImage(bytes) }
                ctx.sendMessage(buildMessageChain { +url })
            }
        }
    }
}

class ListPets : CliktCommand("list") {
    override fun run() {
        val ctx = currentContext.findObject<AnimalContext>() ?: return
        runBlocking {
            val pets = ctx.service.getUserPets(ctx.groupId, ctx.senderId)
            if (pets.isEmpty()) {
                ctx.sendMessage(buildMessageChain { +"你还没有宠物" })
                return@runBlocking
            }
            val petList = pets.joinToString("\n") { pet ->
                val price = ctx.service.calculatePetPrice(pet)
                "• [${pet.id}] ${pet.getType().name} Lv.${pet.level()} 价格:$price"
            }
            val user = ctx.store.getUser(ctx.groupId, ctx.senderId)
            ctx.sendMessage(buildMessageChain {
                +"""
                |你的宠物（共 ${pets.size} 只）:
                |$petList
                |金币: ${user?.coins ?: 0}
                |累计贡献度: ${user?.contributionCount() ?: 0}
            """.trimMargin()
            })
        }
    }
}

class Farm : CliktCommand("farm") {
    override fun run() {
        val ctx = currentContext.findObject<AnimalContext>() ?: return
        runBlocking {
            val user = ctx.store.getUser(ctx.groupId, ctx.senderId) ?: run {
                ctx.sendMessage(buildMessageChain { +"你还没有注册宠物" })
                return@runBlocking
            }

            // 发送农场预览消息
            ctx.sendMessage(buildMessageChain {
                +"""
                |农场预览:
                |用户名: ${user.getName()}
                |宠物数: ${user.personas.size}
                |累计贡献: ${user.contributionCount()}
                |背景: ${user.getSelectedField().name}
            """.trimMargin()
            })

            // 发送农场截图
            val url = "${ctx.serverUrl}/farm/${ctx.groupId}/${ctx.senderId}"
            val screenshot = ctx.takeScreenshot(url)
            screenshot?.let { bytes -> ctx.sendImage(bytes) }
            ctx.sendMessage(buildMessageChain { +url })
        }
    }
}

class Coins : CliktCommand("coins") {
    override fun run() {
        val ctx = currentContext.findObject<AnimalContext>() ?: return
        runBlocking {
            val coins = ctx.service.getCoins(ctx.groupId, ctx.senderId)
            ctx.sendMessage(buildMessageChain { +"你当前有 $coins 金币" })
        }
    }
}

class Line : CliktCommand("line") {
    private val petIdArg: String? by argument().optional()
    val petId: Long? get() = petIdArg?.toLongOrNull()

    override fun run() {
        val ctx = currentContext.findObject<AnimalContext>() ?: return
        runBlocking {
            val pet = ctx.service.viewPet(ctx.groupId, ctx.senderId, petId ?: 0L) ?: run {
                ctx.sendMessage(buildMessageChain { +"找不到该宠物" })
                return@runBlocking
            }
            val canEvolve = pet.getType().personaEvolution.weight > 0
            val evolutionInfo = if (canEvolve) "可进化！" else "不可进化"

            // 发送宠物详情消息
            ctx.sendMessage(buildMessageChain {
                +"""
                |宠物详情:
                |类型: ${pet.getType().name}
                |等级: Lv.${pet.level()}
                |$evolutionInfo
            """.trimMargin()
            })

            // 发送宠物截图
            val url = "${ctx.serverUrl}/pet/${ctx.groupId}/${ctx.senderId}/${pet.id}"
            val screenshot = ctx.takeScreenshot(url)
            screenshot?.let { bytes -> ctx.sendImage(bytes) }
            ctx.sendMessage(buildMessageChain { +url })
        }
    }
}

class Draw : CliktCommand("draw") {
    private val countArg: String? by argument().optional()
    val count: Int get() = countArg?.toIntOrNull() ?: 1

    override fun run() {
        val ctx = currentContext.findObject<AnimalContext>() ?: return
        runBlocking {
            val result = ctx.service.drawPet(ctx.groupId, ctx.senderId, count)
            ctx.sendMessage(buildMessageChain { +result.getOrElse { "抽宠失败：$it" } })
        }
    }
}

class Sell : CliktCommand("sell") {
    private val petIdArg: String? by argument().optional()
    val petId: Long get() = petIdArg?.toLongOrNull() ?: 0L

    override fun run() {
        val ctx = currentContext.findObject<AnimalContext>() ?: return
        runBlocking {
            val result = ctx.service.sellPet(ctx.groupId, ctx.senderId, petId)
            ctx.sendMessage(buildMessageChain { +result.getOrElse { "售卖失败：$it" } })
        }
    }
}

class SetFarm : CliktCommand("setfarm") {
    private val petIdArg: String? by argument().optional()
    private val visibleArg: String? by argument().optional()
    val petId: Long get() = petIdArg?.toLongOrNull() ?: 0L
    val visible: Boolean get() = visibleArg?.lowercase()?.let { it != "off" && it != "false" && it != "0" } ?: true

    override fun run() {
        val ctx = currentContext.findObject<AnimalContext>() ?: return
        runBlocking {
            val result = ctx.service.setFarmPet(ctx.groupId, ctx.senderId, petId, visible)
            ctx.sendMessage(buildMessageChain { +result.getOrElse { "设置失败：$it" } })
        }
    }
}

class Field : CliktCommand("field") {
    init {
        subcommands(FieldList(), FieldSet())
    }

    override fun run() {
        // Field has subcommands, this won't be called
    }
}

class FieldList : CliktCommand("list") {
    override fun run() {
        val ctx = currentContext.findObject<AnimalContext>() ?: return
        runBlocking {
            val user = ctx.store.getUser(ctx.groupId, ctx.senderId) ?: run {
                ctx.sendMessage(buildMessageChain { +"用户不存在" })
                return@runBlocking
            }
            val fields = user.fields
            val selectedField = user.getSelectedField()
            val fieldList = fields.joinToString("\n") { field ->
                val marker = if (field.fieldType == selectedField) " [当前]" else ""
                "• ${field.fieldType.name}$marker"
            }
            ctx.sendMessage(buildMessageChain {
                +"""
                |已解锁的背景（共 ${fields.size} 个）:
                |$fieldList
            """.trimMargin()
            })
        }
    }
}

class FieldSet : CliktCommand("set") {
    private val fieldTypeArg: String? by argument().optional()
    val fieldType: String get() = fieldTypeArg ?: ""

    override fun run() {
        val ctx = currentContext.findObject<AnimalContext>() ?: return
        runBlocking {
            if (fieldType.isEmpty()) {
                ctx.sendMessage(buildMessageChain { +"请指定背景类型" })
                return@runBlocking
            }
            val field = try {
                FieldType.valueOf(fieldType.uppercase())
            } catch (e: Exception) {
                ctx.sendMessage(buildMessageChain { +"未知的背景类型: $fieldType" })
                return@runBlocking
            }
            val result = ctx.service.setField(ctx.groupId, ctx.senderId, field)
            ctx.sendMessage(buildMessageChain { +result.getOrElse { "设置失败：$it" } })
        }
    }
}
