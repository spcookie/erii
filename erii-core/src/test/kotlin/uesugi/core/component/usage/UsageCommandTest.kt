package uesugi.core.component.usage

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageCommandTest {
    @Test
    fun `usage command renders current bot and group context`() {
        val source = Path.of("src/main/kotlin/uesugi/plugin/builtin/usage/Usage.kt").readText()

        assertTrue(source.contains("class Usage :"))
        assertTrue(source.contains("get() = \"usage\""))
        assertTrue(source.contains("repository.summary(botId = meta.botId, groupId = meta.groupId)"))
        assertTrue(source.contains("botId = meta.botId"))
        assertTrue(source.contains("botName = meta.roledBot.role.name"))
        assertTrue(source.contains("groupId = meta.groupId"))
        assertTrue(source.contains("groupName = resolveGroupName(meta)"))
    }

    @Test
    fun `usage all command renders all usage without bot and group context`() {
        val source = Path.of("src/main/kotlin/uesugi/plugin/builtin/usage/Usage.kt").readText()

        assertTrue(source.contains("class UsageAll :"))
        assertTrue(source.contains("get() = \"usage-all\""))
        assertTrue(source.contains("repository.summary()"))
        assertTrue(source.contains("buildUsageViewModel(summary)"))
        assertFalse(source.contains("repository.summary(botId = null, groupId = null)"))
    }
}
