package uesugi.common

/**
 * Marks a tool function as sending a chat message to the group.
 * Tools annotated with [ChatMessage] are recognized by BotAgent as chat-sending tools,
 * preventing fallback emoticon when they are called.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ChatMessage
