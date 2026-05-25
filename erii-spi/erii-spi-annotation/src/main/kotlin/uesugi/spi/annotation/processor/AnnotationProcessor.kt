package uesugi.spi.annotation.processor

import uesugi.spi.annotation.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.tools.Diagnostic
import javax.tools.StandardLocation

@SupportedAnnotationTypes(
    "uesugi.spi.annotation.Definition",
    "uesugi.spi.annotation.Plugin",
    "uesugi.spi.annotation.Route",
    "uesugi.spi.annotation.Cmd",
    "uesugi.spi.annotation.Passive",
    "uesugi.spi.annotation.Tool",
    "uesugi.spi.annotation.LLMDescription"
)
class AnnotationProcessor : AbstractProcessor() {

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (roundEnv.processingOver()) return true

        // 1. 收集 @file:Definition
        val pluginDefElements = roundEnv.getElementsAnnotatedWith(Definition::class.java)
            .filterIsInstance<PackageElement>()

        if (pluginDefElements.size > 1) {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Only one @file:Definition allowed per plugin"
            )
            return false
        }
        val pluginDef = pluginDefElements.firstOrNull()?.getAnnotation(Definition::class.java)

        // 2. 收集 @Plugin 委托类
        val pluginClasses = roundEnv.getElementsAnnotatedWith(Plugin::class.java)
            .filterIsInstance<TypeElement>()
            .filter { it.kind == ElementKind.CLASS }

        if (pluginClasses.size > 1) {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Only one @Plugin class allowed per plugin"
            )
            return false
        }
        val pluginClass = pluginClasses.firstOrNull()

        // 3. 收集顶层函数（在 *Kt 类中）
        val allKtClasses = processingEnv.elementUtils.getAllPackageElements("")
            .flatMap { it.enclosedElements }
            .filterIsInstance<TypeElement>()
            .filter { it.simpleName.toString().endsWith("Kt") }

        val routeFunctions = mutableListOf<ExecutableElement>()
        val cmdFunctions = mutableListOf<ExecutableElement>()
        val passiveFunctions = mutableListOf<ExecutableElement>()
        val toolFunctions = mutableListOf<ExecutableElement>()

        for (ktClass in allKtClasses) {
            for (method in ktClass.enclosedElements.filterIsInstance<ExecutableElement>()) {
                if (method.getAnnotation(Route::class.java) != null) routeFunctions += method
                if (method.getAnnotation(Cmd::class.java) != null) cmdFunctions += method
                if (method.getAnnotation(Passive::class.java) != null) passiveFunctions += method
                if (method.getAnnotation(Tool::class.java) != null) toolFunctions += method
            }
        }

        // 验证必需信息
        val pluginId = pluginDef?.pluginId?.ifEmpty { null }
            ?: processingEnv.options["plugin.id"]
        val version = pluginDef?.version?.ifEmpty { null }
            ?: processingEnv.options["plugin.version"]

        if (pluginId == null) {
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "pluginId is required")
            return false
        }
        if (version == null) {
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "version is required")
            return false
        }
        if (pluginClass == null) {
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "@Plugin class is required")
            return false
        }

        // 生成文件（后续 Task 中实现）
        generatePluginProperties(pluginDef, pluginClass, pluginId, version)
        generateToolSets(toolFunctions, pluginClass)
        generateRouteExtensions(routeFunctions, pluginClass)
        generateCmdExtensions(cmdFunctions, pluginClass)
        generatePassiveExtensions(passiveFunctions, pluginClass)
        generatePf4jExtensionsFile(
            routeFunctions, cmdFunctions, passiveFunctions, pluginClass
        )

        return true
    }

    private fun generatePluginProperties(
        pluginDef: Definition?, pluginClass: TypeElement,
        pluginId: String, version: String
    ) {
        val fullName = pluginClass.qualifiedName.toString()

        val properties = buildString {
            appendLine("plugin.class=$fullName")
            appendLine("plugin.id=$pluginId")
            appendLine("plugin.version=$version")
            pluginDef?.requires?.takeIf { it.isNotEmpty() }?.let { appendLine("plugin.requires=$it") }
            pluginDef?.dependencies?.takeIf { it.isNotEmpty() }?.let { appendLine("plugin.dependencies=$it") }
            pluginDef?.description?.takeIf { it.isNotEmpty() }?.let { appendLine("plugin.description=$it") }
            pluginDef?.provider?.takeIf { it.isNotEmpty() }?.let { appendLine("plugin.provider=$it") }
            pluginDef?.license?.takeIf { it.isNotEmpty() }?.let { appendLine("plugin.license=$it") }
        }

        val filer = processingEnv.filer
        val resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "plugin.properties")
        resource.openWriter().use { it.write(properties) }

        processingEnv.messager.printMessage(
            Diagnostic.Kind.NOTE,
            "AnnotationProcessor: Generated plugin.properties for $pluginId"
        )
    }

    private fun generateToolSets(toolFunctions: List<ExecutableElement>, pluginClass: TypeElement) {
        val packageName = processingEnv.elementUtils.getPackageOf(pluginClass).toString()
        val grouped = toolFunctions.groupBy {
            it.getAnnotation(Tool::class.java)?.set ?: "default"
        }

        for ((setName, functions) in grouped) {
            val className = "GeneratedToolSet_${setName.replace("-", "_")}"
            val fileContent = buildString {
                appendLine("package $packageName")
                appendLine()
                appendLine("import uesugi.spi.MetaToolSet")
                appendLine("import ai.koog.agents.core.tools.reflect.Tool as KoogTool")
                appendLine("import ai.koog.agents.core.tools.reflect.LLMDescription as KoogLLMDescription")
                appendLine()
                appendLine("class $className : MetaToolSet {")

                for (func in functions) {
                    val toolAnno = func.getAnnotation(Tool::class.java)!!
                    val descAnno = func.getAnnotation(LLMDescription::class.java)
                    val funcName = func.simpleName.toString()
                    val enclosingClass = func.enclosingElement as TypeElement
                    val ktFileName = enclosingClass.simpleName.toString()
                    val toolName = toolAnno.name.ifEmpty { funcName }

                    descAnno?.let { appendLine("    @KoogLLMDescription(\"${it.description.replace("\"", "\\")}\")") }
                    appendLine("    @KoogTool(name = \"$toolName\")")
                    appendLine("    suspend fun $funcName(): String? = $ktFileName.$funcName()")
                    appendLine()
                }

                appendLine("}")
            }

            writeSourceFile("$packageName.$className", fileContent)
        }
    }

    private fun generateRouteExtensions(routeFunctions: List<ExecutableElement>, pluginClass: TypeElement) {
        // 后续 Task 实现
    }

    private fun generateCmdExtensions(cmdFunctions: List<ExecutableElement>, pluginClass: TypeElement) {
        // 后续 Task 实现
    }

    private fun generatePassiveExtensions(passiveFunctions: List<ExecutableElement>, pluginClass: TypeElement) {
        // 后续 Task 实现
    }

    private fun generatePf4jExtensionsFile(
        routeFunctions: List<ExecutableElement>,
        cmdFunctions: List<ExecutableElement>,
        passiveFunctions: List<ExecutableElement>,
        pluginClass: TypeElement
    ) {
        // 后续 Task 实现
    }

    private fun writeSourceFile(qualifiedName: String, content: String) {
        val filer = processingEnv.filer
        val sourceFile = filer.createSourceFile(qualifiedName)
        sourceFile.openWriter().use { it.write(content) }
    }
}
