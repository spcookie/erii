package uesugi.plugin

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.mamoe.mirai.message.data.Face
import org.pf4j.Extension
import uesugi.spi.*

@PluginDefinition
class QQFace : AgentPlugin()

@Extension
class QQFaceExtension : PassiveExtension<QQFace> {

    companion object {
        val log = KotlinLogging.logger {}

        private val objectMapper = ObjectMapper()

        private const val INIT_FACE = "init_face"

        private const val MAX_RETRIES = 3

        private const val RETRY_DELAY_MS = 1000L
    }

    private val mutex = Mutex()

    override fun onLoad(context: PluginContext) {
        context.tool {
            {
                object : MetaToolSet {
                    @Tool
                    @LLMDescription("在群聊中发送一个QQ表情，用来表达情绪或对当前对话作出反应。当发送表情比发送文字更自然时可以调用此工具。")
                    suspend fun sendFace(
                        @LLMDescription("要发送的QQ表情名称，例如：微笑、大笑、哭、点赞等，应选择最符合当前语气或情绪的表情。")
                        query: String
                    ): String {
                        ensureFace()
                        return if (sendFace(MetaToolSet.meta, query)) {
                            "表情发送成功"
                        } else {
                            "没有该表情，表情发送失败"
                        }
                    }
                }
            }
        }
    }

    suspend fun PluginContext.ensureFace() {
        try {
            if (kv.get(INIT_FACE) == null) {
                mutex.withLock {
                    if (kv.get(INIT_FACE) == null) {
                        log.info { "Initializing face embeddings from face_config.json..." }

                        // 读取 face_config.json
                        val inputStream = try {
                            config.readResource("face_config.json")
                        } catch (e: Exception) {
                            throw IllegalStateException("face_config.json not found in resources", e)
                        }

                        val jsonNode: JsonNode = objectMapper.readTree(inputStream)
                        val sysface = jsonNode["sysface"]

                        for (face in sysface) {
                            val qSid = face["QSid"].asText()
                            val qDes = face["QDes"].asText().removePrefix("/")
                            val iqLid = face["IQLid"].asText()

                            // 使用 QSid 作为 ID (超级表情)
                            // 同时保存 IQLid 作为备用 (经典表情的索引)
                            val embedding = embeddingWithRetry(listOf(qDes), emptyList())

                            // 存储多个key以支持不同的查询方式
                            vector.upsert("super_$qSid", qDes, qSid, embedding)
                            vector.upsert("iq_$iqLid", qDes, iqLid, embedding)
                            log.info { "Inserted face $qDes, $qSid, $iqLid" }
                        }

                        kv.set(INIT_FACE, "1")
                        log.info { "Face embeddings initialized." }
                    }
                }
            }
        } catch (e: Exception) {
            log.error(e) { "Face initialization failed." }
        }
    }

    /**
     * 带重试的嵌入生成
     */
    private suspend fun PluginContext.embeddingWithRetry(
        texts: List<String>,
        images: List<ByteArray>,
        retryCount: Int = 0
    ): FloatArray {
        return try {
            vector.embedding(texts, images)
        } catch (e: Exception) {
            if (retryCount < MAX_RETRIES) {
                log.warn { "Embedding failed (retry ${retryCount + 1}/$MAX_RETRIES): ${e.message}" }
                delay(RETRY_DELAY_MS * (retryCount + 1)) // 指数退避
                return embeddingWithRetry(texts, images, retryCount + 1)
            } else {
                log.error(e) { "Embedding failed after $MAX_RETRIES retries" }
                throw e
            }
        }
    }

    suspend fun PluginContext.sendFace(meta: Meta, query: String): Boolean {
        val embedding = try {
            embeddingWithRetry(listOf(query), emptyList())
        } catch (e: Exception) {
            log.error(e) { "Failed to get embedding for query: $query" }
            return false
        }
        val search = vector.search(embedding, 5)
        val (id, content, _, score) = search.first()

        if (score < 0.5) {
            log.info { "Face score $score is less than 0.5, not sending." }
            return false
        }

        log.info { "Sending face $content, id $id" }

        // 解析 ID 并发送表情
        val faceId = id
        val miraiFaceId = when {
            faceId.startsWith("super_") -> faceId.removePrefix("super_").toIntOrNull() ?: return false
            faceId.startsWith("iq_") -> faceId.removePrefix("iq_").toIntOrNull() ?: return false
            else -> faceId.toIntOrNull() ?: return false
        }

        return try {
            meta.getGroup().sendMessage(Face(miraiFaceId))
            true
        } catch (e: Exception) {
            log.error(e) { "Failed to send face $miraiFaceId" }
            false
        }
    }

}