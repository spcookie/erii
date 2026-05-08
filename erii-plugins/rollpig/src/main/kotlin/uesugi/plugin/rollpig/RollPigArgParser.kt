package uesugi.plugin.rollpig

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import kotlinx.coroutines.runBlocking
import uesugi.spi.ArgParserHolder
import uesugi.spi.Meta
import java.time.LocalDate

class RollPigArgParser : ArgParserHolder<RollPigContext>() {

    private lateinit var context: RollPigContext

    override val invokeWithoutSubcommand = true

    init {
        subcommands(RollPigResetCommand())
    }

    override fun init(meta: Meta, context: RollPigContext) {
        this.context = context
    }

    override fun run() {
        currentContext.findOrSetObject { context }
        if (currentContext.invokedSubcommand != null) return
        runBlocking {
            val userId = context.senderId.toString()
            val today = LocalDate.now().toString()
            val pig = context.service.rollPigForUser(userId, today)

            val imageBytes = context.service.renderPigImage(pig)
            if (imageBytes != null) {
                context.sendImage(imageBytes)
                context.sendText("这是你的今日小猪：")
            } else {
                context.sendText("【今日小猪】\n名称：${pig.name}\n描述：${pig.description}\n解析：${pig.analysis}")
            }
        }
    }
}

class RollPigResetCommand : CliktCommand("reset") {

    override fun run() {
        val ctx = currentContext.findObject<RollPigContext>() ?: return
        if (!ctx.isAdmin) return
        runBlocking {
            ctx.store.clearTodayCache()
            ctx.sendText("今日小猪已重置，可以重新抽取啦！")
        }
    }
}
