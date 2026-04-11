package uesugi.plugin.animal.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import uesugi.plugin.animal.core.IdGenerator
import uesugi.plugin.animal.core.Mode
import uesugi.plugin.animal.core.PersonaEvolution
import uesugi.plugin.animal.core.PersonaType
import uesugi.plugin.animal.domain.value.Level

class Persona(
    val id: Long,

    private var type: PersonaType,

    val level: Level,

    var visible: Boolean,

    var appVisible: Boolean = false,

    @JsonIgnore
    var user: User? = null,

    var version: Long? = null,
) {

    constructor(
        type: PersonaType,
        level: Long,
        visible: Boolean
    ) : this(id = IdGenerator.generate(), type = type, level = Level(level), visible = visible)

    constructor(
        type: PersonaType,
        level: Long,
        visible: Boolean,
        user: User,
    ) : this(
        id = IdGenerator.generate(),
        type = type,
        level = Level(level),
        visible = visible,
        user = user
    )

    fun getType() = this.type

    fun toSvgForce(mode: Mode): String = type.load(
        name = user!!.getName(),
        contributionCount = user!!.contributionCount(),
        animationId = this.id,
        level = this.level(),
        mode = mode,
    )

    fun toSvg(mode: Mode): String {
        if (!visible) {
            return ""
        }

        return type.load(
            name = user!!.getName(),
            contributionCount = user!!.contributionCount(),
            animationId = this.id,
            level = this.level(),
            mode = mode,
        )
    }

    fun level(): Long = level.value

    fun isEvolutionable(): Boolean {
        return level() >= EVOLUTION_REQUIRED_LEVEL && getType().personaEvolution != PersonaEvolution.nothing
    }

    fun evolution() {
        require(type.personaEvolution != PersonaEvolution.nothing) {
            "Evolution fail cause, not support evolution type :${type.name}"
        }
        require(level() >= EVOLUTION_REQUIRED_LEVEL) {
            "Cannot evolution persona cause, ${level()} is not enough level for evolution."
        }

        val evolutionedPersonaType = this.type.randomEvolution()
        this.type = evolutionedPersonaType
        this.level.value -= 100
    }

    companion object {
        private const val EVOLUTION_REQUIRED_LEVEL = 100L
    }
}
