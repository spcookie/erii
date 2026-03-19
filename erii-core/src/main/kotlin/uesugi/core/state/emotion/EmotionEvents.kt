package uesugi.core.state.emotion

import uesugi.common.PAD

data class EmotionChangeEvent(
    val botMark: String,
    val groupId: String,
    val pad: PAD
)