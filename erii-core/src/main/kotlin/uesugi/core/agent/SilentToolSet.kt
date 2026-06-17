package uesugi.core.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import uesugi.common.ChatMessage

object SilentToolSet : ToolSet {
    @ChatMessage
    @Tool
    @LLMDescription("本次选择不发言。当你判断不需要回复时，必须调用此工具结束交互，禁止直接返回文本。")
    fun sendSilent(): String? {
        return null
    }
}