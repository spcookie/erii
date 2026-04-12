package uesugi.plugin.animal.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import kotlinx.serialization.Serializable
import uesugi.plugin.animal.core.FieldType
import uesugi.plugin.animal.core.IdGenerator

@Serializable
class Field(
    private val id: Long,

    val fieldType: FieldType,

    private var isChoose: Boolean,

    @kotlinx.serialization.Transient
    @JsonIgnore
    var user: User? = null,
) {

    fun isChoose(): Boolean = this.isChoose

    fun choose() {
        this.isChoose = true
    }

    fun unChoose() {
        this.isChoose = false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Field) return false

        return fieldType == other.fieldType
    }

    override fun hashCode(): Int {
        return fieldType.hashCode()
    }

    fun fillBackground(): String = this.fieldType.fillBackground()

    fun loadComponent(name: String, totalCount: Long): String =
        this.fieldType.loadComponent(name, totalCount)

    fun drawBorder(): String = this.fieldType.drawBorder()

    companion object {
        fun from(user: User, fieldType: FieldType): Field {
            return Field(
                id = IdGenerator.generate(),
                fieldType = fieldType,
                isChoose = false,
                user = user,
            )
        }
    }
}
