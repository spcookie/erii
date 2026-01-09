package uesugi

import ai.koog.agents.core.agent.asMermaidDiagram
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import com.fasterxml.jackson.databind.JsonNode
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.core.buildContext
import uesugi.core.buildPrompt
import uesugi.core.history.HistoryEntity
import uesugi.core.history.MessageType
import uesugi.core.volition.InterruptionMode
import uesugi.core.volition.ProactiveSpeakEvent
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ApplicationTest {

    @Test
    fun testRoot() {
        val diagram = strategy("聊天") {
            val setupContext by nodeAppendPrompt<String>("setupContext") {
                messages(
                    buildPrompt(
                        buildContext(
                            BotProxy.currentBot.id.toString(),
                            "1053148332",
                            ProactiveSpeakEvent(10.0, InterruptionMode.Interrupt)
                        )
                    ).messages
                )
            }

            val nodeSendInput by nodeLLMRequest()
            val nodeExecuteTool by nodeExecuteTool()
            val nodeSendToolResult by nodeLLMSendToolResult()

            edge(nodeStart forwardTo setupContext)
            edge(setupContext forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
            edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
            edge(nodeExecuteTool forwardTo nodeSendToolResult)
//            edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
//            edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
            edge(nodeSendToolResult forwardTo nodeFinish)
        }.asMermaidDiagram()
        println(diagram)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun test_debug() {
        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                jackson()
            }
        }
        runBlocking {
            val response = client.post("http://127.0.0.1:6099/api/Debug/call/debug-primary") {
                contentType(ContentType.Application.Json)
                bearerAuth("\"eyJEYXRhIjp7IkNyZWF0ZWRUaW1lIjoxNzY3ODgxNDUzMzE5LCJIYXNoRW5jb2RlZCI6ImRlMmZiYTc5YjJhOGJhZDdlYzIxNzhiOGVlOWQ4NjlhNzBkZDFhOWI3ZDQzY2E5MGY1NzYzZDcyZjliMmRkZjEifSwiSG1hYyI6IjcwMjZjZjNiODFlOGJmNWViNmZkMWM4NjQzNTg4ZTBiNjE1NTAxMzAzYjM0ZDZlOTEzYTVmMDkwZDBmMTk5NjYifQ==\"")
                setBody(
                    mapOf(
                        "action" to "get_group_msg_history",
                        "params" to mapOf(
                            "group_id" to "1053148332",
                            "count" to 500,
                            "reverse_order" to false
                        )
                    )
                )
            }.body<JsonNode>()
            val nodes = response.get("data").get("data").get("messages")

            val dataSourceConfig = HikariConfig().apply {
                jdbcUrl = "jdbc:h2:file:./store/data;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL"
                driverClassName = "org.h2.Driver"
                maximumPoolSize = 6
                isReadOnly = false
            }
            val dataSource = HikariDataSource(dataSourceConfig)
            val database = Database.connect(dataSource)

            transaction(database) {
                for (message in nodes) {
                    val userId = message.get("user_id").asText()
                    val content = buildList {
                        message.get("message").forEach { msg ->
                            val type = msg.get("type").asText()
                            val data = msg.get("data")
                            when (type) {
                                "text" -> {
                                    val content = data.get("text").asText()
                                    add(content)
                                }

                                "image" -> {
                                    var content = data.get("summary").asText()
                                    if (content.isEmpty()) {
                                        content = data.get("file").asText()
                                    }
                                    add(content)
                                }

                                "face" -> {
                                    var content = data.get("raw").get("faceText").asText()
                                    if (content == "null") {
                                        content = "face表情"
                                    }
                                    add(content)
                                }

                                "at" -> {
                                    val content = data.get("qq").asText()
                                    add("@$content")

                                }

                                "file" -> {
                                    val content = data.get("file").asText()
                                    add(content)
                                }
                            }
                        }
                    }.joinToString("\n\n")
                    val time = message.get("time").asLong()
                    val localDateTime = Instant.fromEpochSeconds(time).toLocalDateTime(TimeZone.currentSystemDefault())
                    HistoryEntity.new {
                        botMark = "2125232116"
                        groupId = "1053148332"
                        this.userId = userId
                        this.content = content
                        messageType = MessageType.TEXT
                        createdAt = localDateTime
                    }
                }
            }


        }
    }

}
