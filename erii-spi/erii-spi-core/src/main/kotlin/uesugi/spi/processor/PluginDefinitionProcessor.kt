package uesugi.spi.processor

import uesugi.spi.PluginDefinition
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.lang.model.SourceVersion
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation

@SupportedAnnotationTypes("uesugi.spi.PluginDefinition")
class PluginDefinitionProcessor : AbstractProcessor() {

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun process(
        annotations: Set<TypeElement>,
        roundEnv: RoundEnvironment
    ): Boolean {
        val annotatedElements = roundEnv.getElementsAnnotatedWith(PluginDefinition::class.java)

        if (annotatedElements.isEmpty()) {
            return true
        }

        if (annotatedElements.size > 1) {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Only one class can be annotated with @PluginDefinition, but found ${annotatedElements.size}",
                annotatedElements.first()
            )
            return false
        }

        for (element in annotatedElements) {
            val annotation = element.getAnnotation(PluginDefinition::class.java)
            if (annotation == null) {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PluginDefinition annotation is missing"
                )
                return false
            }
            val fullName: String = (element as TypeElement).qualifiedName.toString()

            val pluginId = annotation.pluginId.ifEmpty { processingEnv.options["plugin.id"] }

            if (pluginId == null) {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PluginDefinition pluginId is missing"
                )
            }

            val version = annotation.version.ifEmpty { processingEnv.options["plugin.version"] }

            if (version == null) {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.ERROR,
                    "@PluginDefinition version is missing"
                )
            }

            val pluginProperties = buildString {
                appendLine("plugin.class=$fullName")
                appendLine("plugin.id=$pluginId")
                appendLine("plugin.version=$version")
                if (annotation.requires.isNotEmpty()) {
                    appendLine("plugin.requires=${annotation.requires}")
                }
                if (annotation.dependencies.isNotEmpty()) {
                    appendLine("plugin.dependencies=${annotation.dependencies}")
                }
                if (annotation.description.isNotEmpty()) {
                    appendLine("plugin.description=${annotation.description}")
                }
                if (annotation.provider.isNotEmpty()) {
                    appendLine("plugin.provider=${annotation.provider}")
                }
                if (annotation.license.isNotEmpty()) {
                    appendLine("plugin.license=${annotation.license}")
                }
            }

            val filer = processingEnv.filer
            val outputResource = filer.createResource(
                StandardLocation.CLASS_OUTPUT,
                "",
                "plugin.properties"
            )
            outputResource.openWriter().use { writer ->
                writer.write(pluginProperties)
            }

            processingEnv.messager.printMessage(
                Diagnostic.Kind.NOTE,
                "PluginDefinitionProcessor: Generated plugin.properties"
            )
        }

        return true
    }
}
