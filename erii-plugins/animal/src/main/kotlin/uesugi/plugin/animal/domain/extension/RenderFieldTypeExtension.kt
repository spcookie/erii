package uesugi.plugin.animal.domain.extension

import uesugi.plugin.animal.core.FieldType


object RenderFieldTypeExtension {

    fun FieldType.isRenderField(): Boolean {
        return this in renderFields
    }

    private val renderFields = FieldType.entries.asSequence()
        .filter { it != FieldType.LOGO_SHOWING }
        .filter { it != FieldType.FOLDER }
        .filter { it != FieldType.RED_SOFA }
        .filter { it != FieldType.RED_COMPUTER }
}
