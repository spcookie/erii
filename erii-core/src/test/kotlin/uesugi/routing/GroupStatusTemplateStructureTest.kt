package uesugi.routing

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupStatusTemplateStructureTest {
    @Test
    fun `group status template uses semantic report structure`() {
        val template = Path.of("src/main/jte/group-status.kte").readText()

        assertTrue(template.contains("<main class=\"page\">"))
        assertTrue(template.contains("<header class=\"header\">"))
        assertTrue(template.contains("<section class=\"section\">"))
        assertTrue(template.contains("<footer class=\"report-footer\">"))
        assertTrue(template.contains("role=\"img\" aria-label=\"群消息与机器人消息趋势图\""))
    }

    @Test
    fun `group status template uses modern javascript declarations`() {
        val template = Path.of("src/main/jte/group-status.kte").readText()

        assertFalse(Regex("(?m)^\\s*var\\s").containsMatchIn(template))
        assertFalse(template.contains("function ("))
        assertTrue(template.contains("const chartDom = document.getElementById('msg-chart');"))
        assertTrue(template.contains("const option ="))
        assertFalse(template.contains("<script>lucide.createIcons();</script>"))
    }

    @Test
    fun `group status template centralizes presentation styles`() {
        val template = Path.of("src/main/jte/group-status.kte").readText()

        assertFalse(template.contains("style=\"margin-bottom: var(--sp-4);\""))
        assertFalse(template.contains("style=\"gap: var(--sp-6);\""))
        assertFalse(template.contains("style=\"width: 100%; height: 260px;\""))
        assertTrue(template.contains("--fill-start:"))
        assertTrue(template.contains("left: var(--fill-start);"))
        assertTrue(template.contains("width: var(--fill-size);"))
        assertTrue(template.contains("color: var(--state-color);"))
        assertTrue(template.contains("background: var(--state-color);"))
    }
}
