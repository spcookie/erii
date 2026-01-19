package uesugi.plugins.nano

data class ContentPart(
    val content: String,
    val type: Type
) {
    enum class Type {
        TEXT, IMAGE
    }
}
