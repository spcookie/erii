package uesugi.common.message

object CommandUtil {
    private val COMMAND_REGEX = Regex("^\\s*/(\\S+)(?:\\s+.*)?$")

    fun isCommand(text: String) = COMMAND_REGEX.matches(text)

    fun parseCommand(text: String): String? =
        COMMAND_REGEX.matchEntire(text)
            ?.destructured
            ?.component1()
}
