package uesugi.common.message

object CommandUtil {
    private val COMMAND_REGEX = Regex("^\\s*/(\\S+)(?:\\s+.*)?$")
    private val AT_COMMAND_REGEX = Regex("^\\s*@[0-9A-Za-z]+\\s*/(\\S+)(?:\\s+.*)?$")

    fun isCommand(text: String) = COMMAND_REGEX.matches(text)

    fun parseCommand(text: String): String? =
        COMMAND_REGEX.matchEntire(text)
            ?.destructured
            ?.component1()

    fun isAtCommand(text: String) = AT_COMMAND_REGEX.matches(text)

    fun parseAtCommand(text: String): String? =
        AT_COMMAND_REGEX.matchEntire(text)
            ?.destructured
            ?.component1()
}
