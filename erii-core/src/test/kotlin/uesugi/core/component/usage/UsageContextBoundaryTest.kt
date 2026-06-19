package uesugi.core.component.usage

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class UsageContextBoundaryTest {
    @Test
    fun `llm entry boundaries rebind usage context`() {
        assertContains(
            "src/main/kotlin/uesugi/core/message/pipeline/MessagePipeline.kt",
            "UsageContext.withUsage(context.botId, context.groupId)"
        )
        assertContains(
            "src/main/kotlin/uesugi/plugin/PluginContextImpl.kt",
            "UsageContext.withUsage(event.botId, event.groupId)"
        )
        assertContains(
            "src/main/kotlin/uesugi/core/agent/BotAgent.kt",
            "UsageContext.withUsage(event.botId, event.groupId)"
        )
        assertContains(
            "src/main/kotlin/uesugi/core/cron/CronService.kt",
            "UsageContext.withUsage(task.botId, task.groupId)"
        )
        assertContains(
            "src/main/kotlin/uesugi/core/state/emotion/EmotionJob.kt",
            "UsageContext.withUsage(currentBotId, group)"
        )
        assertContains(
            "src/main/kotlin/uesugi/core/state/evolution/EvolutionJob.kt",
            "UsageContext.withUsage(currentBotId, groupId)"
        )
        assertContains(
            "src/main/kotlin/uesugi/core/state/flow/FlowJob.kt",
            "UsageContext.withUsage(currentBotId, groupId)"
        )
        assertContains("src/main/kotlin/uesugi/core/state/meme/MemeJob.kt", "UsageContext.withUsage(botId, groupId)")
        assertContains(
            "src/main/kotlin/uesugi/core/state/memory/MemoryJob.kt",
            "UsageContext.withUsage(currentBotId, groupId)"
        )
        assertContains(
            "src/main/kotlin/uesugi/core/state/summary/SummaryJob.kt",
            "UsageContext.withUsage(currentBotId, groupId)"
        )
        assertContains(
            "src/main/kotlin/uesugi/core/state/volition/VolitionJob.kt",
            "UsageContext.withUsage(currentBotId, groupId)"
        )
    }

    @Test
    fun `usage command queries current bot and group`() {
        assertContains(
            "src/main/kotlin/uesugi/plugin/builtin/usage/Usage.kt",
            "repository.summary(botId = meta.botId, groupId = meta.groupId)"
        )
    }

    private fun assertContains(path: String, expected: String) {
        val source = Path.of(path).readText()
        assertTrue(source.contains(expected), "$path should contain $expected")
    }
}
