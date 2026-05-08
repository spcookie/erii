package uesugi.plugin.rollpig

import kotlinx.coroutines.runBlocking
import uesugi.spi.ArgParserHolder
import uesugi.spi.Meta
import java.time.LocalDate

class RollPigArgParser : ArgParserHolder<RollPigContext>() {

    private lateinit var context: RollPigContext

    override fun init(meta: Meta, context: RollPigContext) {
        this.context = context
    }

    override fun run() {
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