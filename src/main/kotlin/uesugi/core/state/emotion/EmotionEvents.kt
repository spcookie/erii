package uesugi.core.state.emotion

data class EmotionChangeEvent(
    val botMark: String,
    val groupId: String,
    val pad: uesugi.core.state.emotion.PAD
)