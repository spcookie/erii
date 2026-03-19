package uesugi.plugins.user.image.nano

data class ContentPart(
    val content: String,
    val type: Type,
    val role: Role
) {
    enum class Type {
        TEXT, IMAGE
    }

    enum class Role {
        AI, ME
    }
}
