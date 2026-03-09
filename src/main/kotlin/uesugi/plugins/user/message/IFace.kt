package uesugi.plugins.user.message

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import com.google.auto.service.AutoService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamoe.mirai.message.data.Face
import uesugi.core.plugin.*
import uesugi.plugins.getGroup

@AutoService(Plugin::class)
class IFace : PassivePlugin {

    companion object {
        val log = KotlinLogging.logger {}
    }

    val mutex = Mutex()

    override fun onLoad(context: PluginContext) {
        context.tool {
            {
                object : MetaToolSet {
                    @Tool
                    @LLMDescription("在群聊中发送一个QQ官方表情，用来表达情绪或对当前对话作出反应。当发送表情比发送文字更自然时可以调用此工具。")
                    suspend fun sendFace(
                        @LLMDescription("要发送的QQ官方表情名称，例如：微笑、大笑、哭、点赞等，应选择最符合当前语气或情绪的表情。")
                        query: String
                    ): String {
                        ensureFace()
                        return if (sendFace(MetaToolSet.meta, query)) {
                            "发送成功"
                        } else {
                            "没有该表情，发送失败"
                        }
                    }
                }
            }
        }
    }

    suspend fun PluginContext.ensureFace() {
        try {
            if (kv.get("init_face") == null) {
                mutex.withLock {
                    if (kv.get("init_face") == null) {
                        log.info { "Initializing face embeddings..." }
                        for ((i, n) in Face.names.withIndex().take(1)) {
                            if (n == "[表情]") continue
                            val name = n.removePrefix("[").removeSuffix("]")
                            val id = i
                            val embedding = vector.embedding(listOf(name), emptyList())
                            vector.upsert(id.toString(), name, "", embedding)
                        }
                        kv.set("init_face", "1")
                        log.info { "Face embeddings initialized." }
                    }
                }
            }
        } catch (e: Exception) {
            log.error(e) { "Face initialization failed." }
        }
    }

    suspend fun PluginContext.sendFace(meta: Meta, query: String): Boolean {
        val embedding = vector.embedding(listOf(query), emptyList())
        val search = vector.search(embedding, 5)
        val (id, content, score) = search.first()
        if (score < 0.5) {
            log.info { "Face score $score is less than 0.5, not sending." }
            return false
        }
        log.info { "Sending face $content, id $id" }
        meta.getGroup().sendMessage(Face(id.toInt()))
        return true
    }

}