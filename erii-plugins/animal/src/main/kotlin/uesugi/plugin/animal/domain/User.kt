package uesugi.plugin.animal.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import org.slf4j.LoggerFactory
import uesugi.plugin.animal.core.*
import uesugi.plugin.animal.domain.extension.RenderFieldTypeExtension.isRenderField
import uesugi.plugin.animal.domain.request.VisibleChangeType
import uesugi.plugin.animal.domain.response.PersonaResponse
import uesugi.plugin.animal.domain.value.Contribution
import uesugi.plugin.animal.domain.value.Level
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

class User(
    val id: Long,

    private var name: String,

    val personas: MutableList<Persona> = mutableListOf(),

    private val contributions: MutableList<Contribution> = mutableListOf(),

    private var visit: Long,

    val fields: MutableSet<Field> = mutableSetOf(),

    private var lastPersonaGivePoint: Int,


    ) {

    init {
        personas.forEach { it.user = this }
    }


    fun getName(): String = this.name

    fun updateName(name: String) {
        this.name = name
    }


    fun addPersona(id: Long, personaType: PersonaType, level: Int): PersonaResponse {
        val persona = Persona(
            id = id,
            type = personaType,
            level = Level(level.toLong()),
            visible = personas.size < 30,
            user = this,
        )

        this.personas.add(persona)

        return PersonaResponse.from(persona)
    }

    fun mergePersona(increasePersonaId: Long, deletePersonaId: Long): Persona {
        require(increasePersonaId != deletePersonaId) {
            "increasePersonaId \"$increasePersonaId\", deletePersonaId \"$deletePersonaId\" must be different"
        }

        val increasePersona = personas.first { it.id == increasePersonaId }
        val deletePersona = personas.first { it.id == deletePersonaId }

        increasePersona.level.value += max(deletePersona.level.value / 2, 1)

        deletePersona(deletePersona.id)

        return increasePersona
    }

    fun mergePersonaV2(increasePersonaId: Long, deletePersonaIds: List<Long>): Persona {
        require(deletePersonaIds.contains(increasePersonaId).not()) {
            "increasePersonaId \"$increasePersonaId\", deletePersonaId \"$deletePersonaIds\" must be different"
        }

        val increasePersona = personas.first { it.id == increasePersonaId }
        val deletePersonas = personas.filter { it.id in deletePersonaIds }

        val increaseLevel = deletePersonas.sumOf { deletePersona ->
            max(deletePersona.level() / 2, 1)
        }

        increasePersona.level.value += increaseLevel

        deletePersonas.forEach { deletePersona(it.id) }

        return increasePersona
    }

    fun deletePersona(personaId: Long): PersonaResponse {
        val persona = this.personas.find { it.id == personaId }
            ?: throw IllegalArgumentException("Cannot find persona by id \"$personaId\"")

        this.personas.remove(persona)
        check(personas.isNotEmpty()) { "Cannot delete last pet user must have least 1 pet. \"$name\"" }

        return PersonaResponse.from(persona)
    }

    @JsonIgnore
    fun isContributionUpdatedLongAgo(): Boolean {
        val currentYear = instant().toZonedDateTime(ZoneId.of("UTC")).year
        val currentYearContribution =
            contributions.firstOrNull { it.year == currentYear } ?: return true

        return currentYearContribution.lastUpdatedContribution.isBefore(
            Instant.now().minus(10, ChronoUnit.MINUTES)
        )
    }

    fun changePersonaVisible(
        personaId: Long,
        visible: Boolean,
        visibleChangeType: VisibleChangeType
    ): Persona {
        val persona = personas.find { it.id == personaId }
            ?: throw IllegalArgumentException("Cannot find persona by id \"$personaId\"")

        when (visibleChangeType) {
            VisibleChangeType.APP -> run {
                persona.appVisible = visible
            }

            VisibleChangeType.DEFAULT -> run {
                persona.visible = visible
            }
        }

        val visiblePersonas = personas.filter { it.visible }

        require(visiblePersonas.size < MAX_PERSONA_COUNT) {
            "Persona count must be under \"$MAX_PERSONA_COUNT\" but, current persona count is \"${visiblePersonas.size}\""
        }

        return persona
    }

    fun updateContribution(contribution: Int): Int {
        val beforeContribution = contributions.totalCount()
        val currentYear = ZonedDateTime.now(ZoneId.of("UTC")).year
        val currentYearContribution =
            contributions.firstOrNull { it.year == currentYear }
                ?: run {
                    val currentYearContribution = Contribution(currentYear, 0, Instant.now())
                    contributions.add(currentYearContribution)
                    currentYearContribution
                }

        val newContribution = contribution - currentYearContribution.contribution

        currentYearContribution.contribution += newContribution
        lastPersonaGivePoint += newContribution
        currentYearContribution.lastUpdatedContribution = Instant.now()
        levelUpPersonas(newContribution)


        return newContribution
    }

    private fun levelUpPersonas(newContribution: Int) {
        repeat(newContribution) {
            runCatching {
                val persona = personas.random()
                persona.level.value++
            }.onFailure {
                it.printStackTrace()
                throw it
            }
        }
    }

    fun giveNewPersona() {
        if (lastPersonaGivePoint < FOR_NEW_PERSONA_COUNT) {
            return
        }
        lastPersonaGivePoint %= FOR_NEW_PERSONA_COUNT.toInt()

        val newPersona = getRandomPersona()
        personas.add(newPersona)
    }

    fun giveNewPersonaByType(personaType: PersonaType) {
        personas.add(getPersona(personaType))
    }

    private fun getRandomPersona() = getPersona(PersonaType.random())

    private fun getPersona(personaType: PersonaType): Persona {
        return Persona(
            type = personaType,
            level = 0,
            visible = personas.size < MAX_PERSONA_COUNT,
            user = this,
        )
    }

    fun increaseVisitCount() {
        visit += 1
    }

    fun createLineAnimation(personaId: Long, mode: Mode): String {
        val builder = StringBuilder().openLine()

        val persona = personas.find { it.id >= personaId }
            ?: throw IllegalArgumentException("Cannot find persona by id \"$personaId\"")
        builder.append(persona.toSvgForce(mode))

        return builder.closeSvg()
    }

    private fun StringBuilder.openLine(): StringBuilder {
        return this.append("<svg fill=\"none\" overflow=\"visible\" xmlns=\"http://www.w3.org/2000/svg\">")
    }


    fun createFarmAnimation(): String {
        val field = getOrCreateDefaultFieldIfAbsent()

        val builder = StringBuilder().openFarm()
            .append(field.fillBackground())

        personas.asSequence()
            .forEach { builder.append(it.toSvg(Mode.FARM)) }

        return builder.append(field.loadComponent(name, contributions.totalCount()))
            .append(field.drawBorder())
            .closeSvg()
    }

    fun contributionCount(): Long = contributions.totalCount()

    fun changeField(fieldType: FieldType) {
        getOrCreateDefaultFieldIfAbsent()

        unChooseField()
        chooseField(fieldType)
    }

    private fun unChooseField() {
        getOrCreateDefaultFieldIfAbsent()

        fields.first { it.isChoose() }.unChoose()
    }

    fun addField(fieldType: FieldType) {
        require(fieldType.isRenderField()) {
            "Cannot add field cause \"$fieldType\" is not render field."
        }
        require(fields.any { it.fieldType == fieldType }.not()) {
            "Duplicated add field request."
        }

        getOrCreateDefaultFieldIfAbsent()

        this.fields.add(Field.from(this, fieldType))
    }

    private fun getOrCreateDefaultFieldIfAbsent() = fields.firstOrNull { it.isChoose() } ?: run {
        this.fields.add(Field.from(this, FieldType.WHITE_FIELD))
        this.chooseField(FieldType.WHITE_FIELD)

        fields.first { it.fieldType == FieldType.WHITE_FIELD }
    }

    private fun chooseField(fieldType: FieldType) {
        this.fields.first { it.fieldType == fieldType }.choose()
    }

    fun deleteField(fieldType: FieldType) {
        fields.firstOrNull { it.fieldType == fieldType }
            ?.let { fields.remove(it) }
    }

    private fun List<Contribution>.totalCount(): Long {
        var totalCount = 0L
        this.forEach { totalCount += it.contribution }
        return totalCount
    }

    private fun StringBuilder.openFarm(): StringBuilder =
        this.append("<svg width=\"600\" height=\"300\" viewBox=\"0 0 600 300\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">")

    private fun StringBuilder.closeSvg(): String = this
        .append("</svg>")
        .toString()

    fun evolution(personaId: Long): Persona {
        val persona = personas.firstOrNull {
            it.id == personaId
        }
            ?: throw IllegalArgumentException("Cannot evolution persona cause cannot find matched persona by id: \"$personaId\"")

        persona.evolution()
        return persona
    }

    fun isEvolutionable(personaId: Long): Boolean {
        val persona = personas.firstOrNull {
            it.id == personaId
        }
            ?: throw IllegalArgumentException("Cannot evolution persona cause cannot find matched persona by id: \"$personaId\"")

        return persona.isEvolutionable()
    }

    companion object {
        private const val MAX_PERSONA_COUNT = 30L
        private const val MAX_INIT_PERSONA_COUNT = 10L
        private const val FOR_NEW_PERSONA_COUNT = 30L
        private const val FOR_INIT_PERSONA_COUNT = 100L

        private val nameConvention = Regex("[^a-zA-Z0-9-]")

        private val logger = LoggerFactory.getLogger(this::class.simpleName)

        fun newUser(
            name: String,
            contributions: Map<Int, Int>,
        ): User {
            require(!nameConvention.containsMatchIn(name)) {
                throw IllegalArgumentException("Not supported word contained in \"${name}\"")
            }

            val user = User(
                id = IdGenerator.generate(),
                name = name,
                personas = createPersonas(contributions),
                contributions = contributions.map {
                    val year = it.key
                    val contribution = it.value
                    Contribution(year, contribution, Instant.now())
                }.toMutableList(),
                visit = 1,
                lastPersonaGivePoint = (totalContributionCount(contributions) % FOR_NEW_PERSONA_COUNT).toInt(),
            )

            user.addField(FieldType.WHITE_FIELD)

            return user
        }

        private fun createPersonas(contributions: Map<Int, Int>): MutableList<Persona> {
            val totalContributionCount = totalContributionCount(contributions)
            val personas = mutableListOf<Persona>()
            repeat(
                min(
                    MAX_INIT_PERSONA_COUNT,
                    max((totalContributionCount / FOR_INIT_PERSONA_COUNT), 1)
                ).toInt()
            ) {
                personas.add(Persona(PersonaType.random(), 0, true))
            }
            return personas
        }

        private fun totalContributionCount(contributions: Map<Int, Int>): Long {
            var totalCount = 0L
            contributions.forEach { totalCount += it.value }
            return totalCount
        }
    }
}
