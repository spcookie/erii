package uesugi.core.component.usage

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class UsageCommandTest {
    @Test
    fun `usage command renders current bot and group context`() {
        val source = Path.of("src/main/kotlin/uesugi/plugin/builtin/usage/Usage.kt").readText()

        assertTrue(source.contains("class Usage :"))
        assertTrue(source.contains("get() = \"usage\""))
        assertTrue(source.contains("resolveGroupName(meta)"))
        assertTrue(source.contains("URLEncoder.encode(meta.roledBot.role.name, \"UTF-8\")"))
        assertTrue(source.contains("URLEncoder.encode(groupName, \"UTF-8\")"))
        assertTrue(source.contains("append(\"?botId=\${meta.botId}\")"))
        assertTrue(source.contains("append(\"&groupId=\${meta.groupId}\")"))
        assertTrue(source.contains("renderUsage(meta, url)"))
    }

    @Test
    fun `usage all command renders all usage without bot and group context`() {
        val source = Path.of("src/main/kotlin/uesugi/plugin/builtin/usage/Usage.kt").readText()

        assertTrue(source.contains("class UsageAll :"))
        assertTrue(source.contains("get() = \"usage-all\""))
        assertTrue(source.contains("http://\${externalHost}:\${port}/usage\""))
        assertTrue(source.contains("renderUsage(meta, url)"))
    }
}
