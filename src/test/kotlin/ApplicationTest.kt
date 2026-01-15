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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.core.history.HistoryEntity
import uesugi.core.history.MessageType
import uesugi.toolkit.EventBus
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ApplicationTest {

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
                bearerAuth("\"eyJEYXRhIjp7IkNyZWF0ZWRUaW1lIjoxNzY4NDUzMTc1NjU0LCJIYXNoRW5jb2RlZCI6ImIxNzNkMTU1NjU0YTcxNDRkNjBkNmNhNDg4NWY2YmI0MzNkYjE5Yzc3ODlhNmU2OGQ1M2I0YTI5MGM0NjBlZmQifSwiSG1hYyI6IjJkZWY1MjdlYmU1ZjA2N2NhZjcyMDZjMTJmNmYyYTNiOGM5YmUyZGNiZjg4ZDM5YjA0M2ExOTgwNDc5MDYzOGMifQ==\"")
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
                    val card = message.get("sender").get("card").asText()
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
                        this.nick = card
                        messageType = MessageType.TEXT
                        createdAt = localDateTime
                    }
                }
            }
        }
    }

    @Test
    fun test_event_bus(): Unit = runBlocking {
        open class EventTest(open val message: String)
        data class EventTest_1(
            val aaa: String,
            override val message: String
        ) : EventTest(message)

        val scope = CoroutineScope(Dispatchers.Default)

        EventBus.subscribeAsync<EventTest>(scope) {
            println(it.message)
            if (it is EventTest_1) {
                println(it.aaa)
            }
        }
        delay(1000)

        EventBus.postAsync(EventTest_1("aaa", "bbb"))

        delay(2000)
    }

    @Test
    fun test_edge() {
        println(strategy("chat") {

            val nodeSendInput by nodeLLMRequest()
            val nodeExecuteTool by nodeExecuteTool()
            val nodeSendToolResult by nodeLLMSendToolResult()

            edge(nodeStart forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
            edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
            edge(nodeExecuteTool forwardTo nodeSendToolResult onCondition {
                it.content != "null"
            })
            edge(nodeExecuteTool forwardTo nodeFinish onCondition {
                it.content == "null"
            } transformed { it.content })
            edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
            edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
        }.asMermaidDiagram())
    }

}
