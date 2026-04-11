package uesugi.plugin.animal.domain.response

import uesugi.plugin.animal.core.PersonaGrade
import uesugi.plugin.animal.core.PersonaType
import uesugi.plugin.animal.domain.Persona

data class PersonaResponse(
    val id: String,
    val type: PersonaType,
    val level: String,
    val visible: Boolean,
    val appVisible: Boolean,
    val dropRate: String,
    val grade: PersonaGrade,
    val isEvolutionable: Boolean,
) {
    companion object {
        fun from(persona: Persona): PersonaResponse {
            return PersonaResponse(
                id = persona.id.toString(),
                type = persona.getType(),
                level = persona.level.value.toString(),
                visible = persona.visible,
                appVisible = persona.appVisible,
                dropRate = persona.getType().getDropRate(),
                grade = persona.getType().grade,
                isEvolutionable = persona.isEvolutionable(),
            )
        }
    }
}
