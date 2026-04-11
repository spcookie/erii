package uesugi.plugin.animal.domain.request

data class PersonaChangeRequest(
    val personaId: String,
    val visible: Boolean,
    val type: VisibleChangeType = VisibleChangeType.DEFAULT
)

enum class VisibleChangeType {
    DEFAULT,
    APP,
    ;
}
