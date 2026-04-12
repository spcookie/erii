package uesugi.plugin.animal

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import uesugi.plugin.animal.core.Mode
import uesugi.plugin.animal.store.AnimalStore
import uesugi.spi.PluginContext

class AnimalHtmlRenderer(
    private val store: AnimalStore,
    private val context: PluginContext
) {

    fun registerHtmlRoutes() {
        context.server.route {
            // 宠物详情HTML路由
            get("/pet/{groupId}/{userId}/{petId}") {
                val groupId = call.parameters["groupId"] ?: return@get
                val userId = call.parameters["userId"]?.toLongOrNull() ?: return@get
                val petId = call.parameters["petId"]?.toLongOrNull() ?: return@get

                val html = runCatching {
                    getPetHtml(groupId, userId, petId)
                }.getOrNull()

                if (html != null) {
                    call.respondText(html, contentType = ContentType.Text.Html)
                } else {
                    call.respondText("Pet not found", status = HttpStatusCode.NotFound)
                }
            }

            // 农场HTML路由
            get("/farm/{groupId}/{userId}") {
                val groupId = call.parameters["groupId"] ?: return@get
                val userId = call.parameters["userId"]?.toLongOrNull() ?: return@get

                val html = runCatching {
                    getFarmHtml(groupId, userId)
                }.getOrNull()

                if (html != null) {
                    call.respondText(html, contentType = ContentType.Text.Html)
                } else {
                    call.respondText("Farm not found", status = HttpStatusCode.NotFound)
                }
            }

            // 宠物列表HTML路由
            get("/list/{groupId}/{userId}") {
                val groupId = call.parameters["groupId"] ?: return@get
                val userId = call.parameters["userId"]?.toLongOrNull() ?: return@get

                val html = runCatching {
                    getListHtml(groupId, userId)
                }.getOrNull()

                if (html != null) {
                    call.respondText(html, contentType = ContentType.Text.Html)
                } else {
                    call.respondText("List not found", status = HttpStatusCode.NotFound)
                }
            }
        }
    }

    private fun renderHtml(svgContent: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
            </head>
            <body>
            $svgContent
            </body>
            </html>
            """.trimIndent()
    }

    suspend fun getPetHtml(groupId: String, userId: Long, petId: Long): String? {
        val user = store.getUser(groupId, userId) ?: return null
        val svg = user.createLineAnimation(petId, Mode.LINE)
        return renderHtml(svg)
    }

    suspend fun getFarmHtml(groupId: String, userId: Long): String? {
        val user = store.getUser(groupId, userId) ?: return null
        val svg = user.createFarmAnimation()
        return renderHtml(svg)
    }

    suspend fun getListHtml(groupId: String, userId: Long): String? {
        val user = store.getUser(groupId, userId) ?: return null
        val svg = user.createListAnimation(Mode.NONE)
        return renderHtml(svg)
    }
}
