package uesugi.spi.annotation.processor

import uesugi.spi.annotation.*
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.annotation.processing.SupportedAnnotationTypes
import javax.lang.model.SourceVersion
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeMirror
import javax.tools.Diagnostic
import javax.tools.StandardLocation

@SupportedAnnotationTypes(
    "uesugi.spi.annotation.Definition",
    "uesugi.spi.annotation.Route",
    "uesugi.spi.annotation.Cmd",
    "uesugi.spi.annotation.Passive",
    "uesugi.spi.annotation.Tool",
    "uesugi.spi.annotation.LLMDescription",
    "uesugi.spi.annotation.OnLoad",
    "uesugi.spi.annotation.OnUnload",
    "uesugi.spi.annotation.OnStart",
    "uesugi.spi.annotation.OnStop"
)
class AnnotationProcessor : AbstractProcessor() {

    companion object {
        private const val COMPLETION_PARAM = $$"$completion"
        private const val DEFAULT_TOOLSET = "default"
        private const val TOOLSET_CLASS_PREFIX = "GeneratedToolSet_"
        private const val KAPT_GENERATED_OPTION = "kapt.kotlin.generated"
        private const val FALLBACK_GEN_DIR = "build/generated/source/kaptKotlin/main"
        private const val PF4J_EXTENSION_PATH = "META-INF/services/org.pf4j.Extension"
    }

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        if (roundEnv.processingOver()) {
            createdDirs.clear()
            return true
        }

        // Collect @file:Definition
        val pluginDefElements = roundEnv.getElementsAnnotatedWith(Definition::class.java)
            .filter { it is PackageElement || (it is TypeElement && it.simpleName.toString().endsWith("Kt")) }

        if (pluginDefElements.size > 1) {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "Only one @file:Definition allowed per plugin"
            )
            return false
        }
        val pluginDef = pluginDefElements.firstOrNull()?.getAnnotation(Definition::class.java)

        // Collect annotated functions
        val routeFunctions = roundEnv.getElementsAnnotatedWith(Route::class.java)
            .filterIsInstance<ExecutableElement>()
            .filter { checkVisibility(it, "Route") }
            .toList()

        val cmdFunctions = roundEnv.getElementsAnnotatedWith(Cmd::class.java)
            .filterIsInstance<ExecutableElement>()
            .filter { checkVisibility(it, "Cmd") }
            .toList()

        val passiveFunctions = roundEnv.getElementsAnnotatedWith(Passive::class.java)
            .filterIsInstance<ExecutableElement>()
            .filter { checkVisibility(it, "Passive") }
            .toList()

        val toolFunctions = roundEnv.getElementsAnnotatedWith(Tool::class.java)
            .filterIsInstance<ExecutableElement>()
            .filter { checkVisibility(it, "Tool") }
            .toList()

        // Collect lifecycle functions (named: referenced by value, global: applied to all extensions)
        val onLoadElements = roundEnv.getElementsAnnotatedWith(OnLoad::class.java)
            .filterIsInstance<ExecutableElement>()
            .filter { checkVisibility(it, "OnLoad") }
            .toList()
        val onLoadFunctions = onLoadElements
            .filter { it.getAnnotation(OnLoad::class.java)!!.value.isNotEmpty() }
            .associateBy { it.getAnnotation(OnLoad::class.java)!!.value }
        val globalOnLoadFunctions = onLoadElements
            .filter { it.getAnnotation(OnLoad::class.java)!!.value.isEmpty() }
            .map { "${it.simpleName}()" }

        val onUnloadElements = roundEnv.getElementsAnnotatedWith(OnUnload::class.java)
            .filterIsInstance<ExecutableElement>()
            .filter { checkVisibility(it, "OnUnload") }
            .toList()
        val onUnloadFunctions = onUnloadElements
            .filter { it.getAnnotation(OnUnload::class.java)!!.value.isNotEmpty() }
            .associateBy { it.getAnnotation(OnUnload::class.java)!!.value }
        val globalOnUnloadFunctions = onUnloadElements
            .filter { it.getAnnotation(OnUnload::class.java)!!.value.isEmpty() }
            .map { "${it.simpleName}()" }

        // Collect plugin lifecycle functions
        val onStartFuncs = roundEnv.getElementsAnnotatedWith(OnStart::class.java)
            .filterIsInstance<ExecutableElement>()
            .filter { checkVisibility(it, "OnStart") }
            .toList()
        if (onStartFuncs.size > 1) {
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "Only one @OnStart function allowed per plugin")
            return false
        }
        val onStartFunc = onStartFuncs.firstOrNull()

        val onStopFuncs = roundEnv.getElementsAnnotatedWith(OnStop::class.java)
            .filterIsInstance<ExecutableElement>()
            .filter { checkVisibility(it, "OnStop") }
            .toList()
        if (onStopFuncs.size > 1) {
            processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, "Only one @OnStop function allowed per plugin")
            return false
        }
        val onStopFunc = onStopFuncs.firstOrNull()

        // Determine package from annotated functions
        val anyAnnotated = (routeFunctions + cmdFunctions + passiveFunctions + toolFunctions).firstOrNull()
        val pkgName = if (anyAnnotated != null) {
            processingEnv.elementUtils.getPackageOf(anyAnnotated).toString()
        } else {
            processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, "No annotated functions found, skipping")
            return true
        }
        // Generate files
        generatePluginDelegate(pkgName, onStartFunc, onStopFunc)
        generatePluginProperties(pluginDef, pkgName)
        generateToolSets(toolFunctions, pkgName)
        generateRouteExtensions(
            routeFunctions,
            pkgName,
            onLoadFunctions,
            onUnloadFunctions,
            globalOnLoadFunctions,
            globalOnUnloadFunctions
        )
        generateCmdExtensions(
            cmdFunctions,
            pkgName,
            onLoadFunctions,
            onUnloadFunctions,
            globalOnLoadFunctions,
            globalOnUnloadFunctions
        )
        generatePassiveExtensions(
            passiveFunctions,
            pkgName,
            onLoadFunctions,
            onUnloadFunctions,
            globalOnLoadFunctions,
            globalOnUnloadFunctions
        )
        generatePf4jExtensionsFile(routeFunctions, cmdFunctions, passiveFunctions, pkgName)

        return true
    }

    private fun generatePluginDelegate(
        pkgName: String,
        onStartFunc: ExecutableElement?, onStopFunc: ExecutableElement?
    ) {
        val className = "GeneratedPlugin"
        val needsRunBlocking = (onStartFunc != null && isSuspendFunction(onStartFunc)) ||
                (onStopFunc != null && isSuspendFunction(onStopFunc))
        val fileContent = buildString {
            appendLine("package $pkgName")
            appendLine()
            appendLine("import uesugi.spi.AgentPlugin")
            if (needsRunBlocking) {
                appendLine("import kotlinx.coroutines.runBlocking")
            }
            appendLine()
            appendLine("class $className : AgentPlugin() {")
            if (onStartFunc != null) {
                appendLine("    override fun start() {")
                appendLine("        super.start()")
                if (isSuspendFunction(onStartFunc)) {
                    appendLine("        runBlocking { ${onStartFunc.simpleName}() }")
                } else {
                    appendLine("        ${onStartFunc.simpleName}()")
                }
                appendLine("    }")
            }
            if (onStopFunc != null) {
                appendLine("    override fun stop() {")
                if (isSuspendFunction(onStopFunc)) {
                    appendLine("        runBlocking { ${onStopFunc.simpleName}() }")
                } else {
                    appendLine("        ${onStopFunc.simpleName}()")
                }
                appendLine("        super.stop()")
                appendLine("    }")
            }
            appendLine("}")
        }
        writeSourceFile("$pkgName.$className", fileContent)
    }

    private fun generatePluginProperties(pluginDef: Definition?, pkgName: String) {
        val generatedPluginClass = "$pkgName.GeneratedPlugin"
        val options = processingEnv.options
        val pluginId = pluginDef?.pluginId?.ifEmpty { options["plugin.id"] } ?: options["plugin.id"]
        val version = pluginDef?.version?.ifEmpty { options["plugin.version"] } ?: options["plugin.version"]

        val properties = buildString {
            appendLine("plugin.class=$generatedPluginClass")
            if (pluginId != null) appendLine("plugin.id=$pluginId")
            if (version != null) appendLine("plugin.version=$version")
            pluginDef?.requires?.takeIf { it.isNotEmpty() }?.let { appendLine("plugin.requires=$it") }
            pluginDef?.dependencies?.takeIf { it.isNotEmpty() }?.let { appendLine("plugin.dependencies=$it") }
            pluginDef?.description?.takeIf { it.isNotEmpty() }?.let { appendLine("plugin.description=$it") }
            pluginDef?.provider?.takeIf { it.isNotEmpty() }?.let { appendLine("plugin.provider=$it") }
            pluginDef?.license?.takeIf { it.isNotEmpty() }?.let { appendLine("plugin.license=$it") }
        }

        val filer = processingEnv.filer
        val resource = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "plugin.properties")
        resource.openWriter().use { it.write(properties) }
    }

    private fun generateToolSets(toolFunctions: List<ExecutableElement>, pkgName: String) {
        val grouped = toolFunctions.groupBy {
            it.getAnnotation(Tool::class.java)?.set ?: DEFAULT_TOOLSET
        }

        for ((setName, functions) in grouped) {
            val className = toolSetClassName(setName)
            val fileContent = buildString {
                appendLine("package $pkgName")
                appendLine()
                appendLine("import uesugi.spi.MetaToolSet")
                appendLine("import ai.koog.agents.core.tools.annotations.Tool as KoogTool")
                appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription as KoogLLMDescription")
                if (functions.any { !isSuspendFunction(it) }) {
                    appendLine("import kotlinx.coroutines.Dispatchers")
                    appendLine("import kotlinx.coroutines.withContext")
                }
                // Import top-level functions with alias to avoid name collision
                for (func in functions) {
                    appendLine("import $pkgName.${func.simpleName} as _erii_${func.simpleName}")
                }
                appendLine()
                appendLine("class $className : MetaToolSet {")

                for (func in functions) {
                    val descAnno = func.getAnnotation(LLMDescription::class.java)
                    val funcName = func.simpleName.toString()
                    val funcIsSuspend = isSuspendFunction(func)

                    val userParams = func.parameters.filter { it.simpleName.toString() != COMPLETION_PARAM }
                    val paramDecls = mutableListOf<String>()
                    val argNames = mutableListOf<String>()
                    for (param in userParams) {
                        val paramType = toKotlinType(param.asType())
                        val paramName = param.simpleName.toString()
                        val paramDesc = param.getAnnotation(LLMDescription::class.java)
                        val line = buildString {
                            paramDesc?.let { appendLine("        @KoogLLMDescription(\"${it.description.escapeForLiteral()}\")") }
                            append("        $paramName: $paramType")
                        }
                        paramDecls.add(line)
                        argNames.add(paramName)
                    }

                    descAnno?.let { appendLine("    @KoogLLMDescription(\"${it.description.escapeForLiteral()}\")") }
                    appendLine("    @KoogTool")

                    val argsStr = argNames.joinToString(", ")
                    val callExpr = if (funcIsSuspend) {
                        "_erii_$funcName($argsStr)"
                    } else {
                        "withContext(Dispatchers.IO) { _erii_$funcName($argsStr) }"
                    }

                    if (paramDecls.isEmpty()) {
                        appendLine("    suspend fun $funcName(): String? = $callExpr")
                    } else {
                        appendLine("    suspend fun $funcName(")
                        appendLine(paramDecls.joinToString(",\n"))
                        appendLine("    ): String? = $callExpr")
                    }
                    appendLine()
                }

                appendLine("}")
            }

            writeSourceFile("$pkgName.$className", fileContent)
        }
    }

    // === Generator helpers ===

    private fun StringBuilder.emitExtensionImports(handlerHasMeta: Boolean, handlerIsSuspend: Boolean) {
        appendLine("import org.pf4j.Extension")
        appendLine("import uesugi.spi.*")
        appendLine("import uesugi.spi.annotation.withPluginContext")
        when {
            !handlerHasMeta && handlerIsSuspend -> {
                appendLine("import uesugi.spi.annotation.withMeta")
            }

            !handlerHasMeta && !handlerIsSuspend -> {
                appendLine("import uesugi.spi.annotation.withMetaIO")
            }

            handlerHasMeta && !handlerIsSuspend -> {
                appendLine("import kotlinx.coroutines.Dispatchers")
                appendLine("import kotlinx.coroutines.withContext")
            }
        }
    }

    private fun StringBuilder.emitHandlerInvocation(
        callExpr: String, handlerIsSuspend: Boolean, handlerHasMeta: Boolean, indent: String
    ) {
        when {
            !handlerHasMeta && handlerIsSuspend -> {
                appendLine(indent + "withMeta(meta) {")
                appendLine("$indent    $callExpr")
                appendLine("$indent}")
            }

            !handlerHasMeta && !handlerIsSuspend -> {
                appendLine(indent + "withMetaIO(meta) {")
                appendLine("$indent    $callExpr")
                appendLine("$indent}")
            }

            handlerHasMeta && !handlerIsSuspend -> {
                appendLine(indent + "withContext(Dispatchers.IO) { $callExpr }")
            }

            else -> {
                appendLine("$indent$callExpr")
            }
        }
    }

    private fun StringBuilder.emitToolSetRegistrations(
        toolSets: Array<String>, packageName: String, indent: String
    ) {
        if (toolSets.isNotEmpty() && toolSets[0] != DEFAULT_TOOLSET) {
            for (setName in toolSets) {
                val tsClassName = toolSetClassName(setName)
                appendLine("${indent}context.tool { { $packageName.$tsClassName() } }")
            }
        }
    }

    private fun StringBuilder.emitLifecycleCalls(calls: List<String>, indent: String) {
        for (call in calls) {
            appendLine("$indent$call")
        }
    }

    private fun generateRouteExtensions(
        routeFunctions: List<ExecutableElement>, pkgName: String,
        onLoadFunctions: Map<String, ExecutableElement>, onUnloadFunctions: Map<String, ExecutableElement>,
        globalOnLoadCalls: List<String>, globalOnUnloadCalls: List<String>
    ) {
        val pluginClassName = "GeneratedPlugin"

        for (func in routeFunctions) {
            val routeAnno = func.getAnnotation(Route::class.java)!!
            val funcName = func.simpleName.toString()
            val className = "GeneratedRoute_${funcName}"
            val onLoadCalls = resolveLifecycle(routeAnno.onLoad, onLoadFunctions, "OnLoad", funcName)
            val onUnloadCalls = resolveLifecycle(routeAnno.onUnload, onUnloadFunctions, "OnUnload", funcName)
            val handlerIsSuspend = isSuspendFunction(func)
            val handlerHasMeta = hasMetaParam(func)
            val callExpr = if (handlerHasMeta) "$funcName(meta)" else "$funcName()"

            val fileContent = buildString {
                appendLine("package $pkgName")
                appendLine()
                emitExtensionImports(handlerHasMeta, handlerIsSuspend)
                appendLine()
                appendLine("@Extension")
                appendLine("class $className : RouteExtension<$pluginClassName> {")
                appendLine("    override val matcher: Pair<String, String>")
                appendLine("        get() = \"${routeAnno.path}\" to \"${routeAnno.method}\"")
                appendLine()
                appendLine("    override fun onLoad(context: PluginContext) {")
                emitLifecycleCalls(onLoadCalls + globalOnLoadCalls, "        ")
                appendLine("        context.chain { meta ->")
                appendLine("            withPluginContext(context) {")
                emitHandlerInvocation(callExpr, handlerIsSuspend, handlerHasMeta, "                ")
                appendLine("            }")
                appendLine("        }")
                emitToolSetRegistrations(routeAnno.toolSets, pkgName, "        ")
                appendLine("    }")
                appendLine()
                appendLine("    override fun onUnload() {")
                emitLifecycleCalls(onUnloadCalls + globalOnUnloadCalls, "        ")
                appendLine("    }")
                appendLine("}")
            }

            writeSourceFile("$pkgName.$className", fileContent)
        }
    }

    private fun generateCmdExtensions(
        cmdFunctions: List<ExecutableElement>, pkgName: String,
        onLoadFunctions: Map<String, ExecutableElement>, onUnloadFunctions: Map<String, ExecutableElement>,
        globalOnLoadCalls: List<String>, globalOnUnloadCalls: List<String>
    ) {
        val pluginClassName = "GeneratedPlugin"

        for (func in cmdFunctions) {
            val cmdAnno = func.getAnnotation(Cmd::class.java)!!
            val funcName = func.simpleName.toString()
            val className = "GeneratedCmd_${cmdAnno.name.replace("-", "_")}"
            val aliasList = cmdAnno.alias.joinToString(", ") { "\"$it\"" }
            val onLoadCalls = resolveLifecycle(cmdAnno.onLoad, onLoadFunctions, "OnLoad", funcName)
            val onUnloadCalls = resolveLifecycle(cmdAnno.onUnload, onUnloadFunctions, "OnUnload", funcName)
            val handlerIsSuspend = isSuspendFunction(func)
            val handlerHasMeta = hasMetaParam(func)
            val callExpr = if (handlerHasMeta) "$funcName(holder.args, meta)" else "$funcName(holder.args)"

            val fileContent = buildString {
                appendLine("package $pkgName")
                appendLine()
                emitExtensionImports(handlerHasMeta, handlerIsSuspend)
                appendLine()
                appendLine("@Extension")
                appendLine("class $className : SlashCmdExtension<$pluginClassName> {")
                appendLine("    override val cmd: String")
                appendLine("        get() = \"${cmdAnno.name}\"")
                if (cmdAnno.alias.isNotEmpty()) {
                    appendLine("    override val alias: List<String>")
                    appendLine("        get() = listOf($aliasList)")
                }
                appendLine()
                appendLine("    override fun onLoad(context: PluginContext) {")
                emitLifecycleCalls(onLoadCalls + globalOnLoadCalls, "        ")
                appendLine("        context.chain { meta ->")
                appendLine("            val holder = meta.parser(Unit)")
                appendLine("            withPluginContext(context) {")
                emitHandlerInvocation(callExpr, handlerIsSuspend, handlerHasMeta, "                ")
                appendLine("            }")
                appendLine("        }")
                emitToolSetRegistrations(cmdAnno.toolSets, pkgName, "        ")
                appendLine("    }")
                appendLine()
                appendLine("    override fun onUnload() {")
                emitLifecycleCalls(onUnloadCalls + globalOnUnloadCalls, "        ")
                appendLine("    }")
                appendLine("}")
            }

            writeSourceFile("$pkgName.$className", fileContent)
        }
    }

    private fun generatePassiveExtensions(
        passiveFunctions: List<ExecutableElement>, pkgName: String,
        onLoadFunctions: Map<String, ExecutableElement>, onUnloadFunctions: Map<String, ExecutableElement>,
        globalOnLoadCalls: List<String>, globalOnUnloadCalls: List<String>
    ) {
        val pluginClassName = "GeneratedPlugin"

        for (func in passiveFunctions) {
            val passiveAnno = func.getAnnotation(Passive::class.java)!!
            val funcName = func.simpleName.toString()
            val className = "GeneratedPassive_${funcName}"
            val onLoadCalls = resolveLifecycle(passiveAnno.onLoad, onLoadFunctions, "OnLoad", funcName)
            val onUnloadCalls = resolveLifecycle(passiveAnno.onUnload, onUnloadFunctions, "OnUnload", funcName)
            val handlerIsSuspend = isSuspendFunction(func)
            val handlerHasMeta = hasMetaParam(func)
            val callExpr = if (handlerHasMeta) "$funcName(meta)" else "$funcName()"

            val fileContent = buildString {
                appendLine("package $pkgName")
                appendLine()
                emitExtensionImports(handlerHasMeta, handlerIsSuspend)
                appendLine()
                appendLine("@Extension")
                appendLine("class $className : PassiveExtension<$pluginClassName> {")
                appendLine("    override fun onLoad(context: PluginContext) {")
                emitLifecycleCalls(onLoadCalls + globalOnLoadCalls, "        ")
                appendLine("        context.chain { meta ->")
                appendLine("            withPluginContext(context) {")
                emitHandlerInvocation(callExpr, handlerIsSuspend, handlerHasMeta, "                ")
                appendLine("            }")
                appendLine("        }")
                emitToolSetRegistrations(passiveAnno.toolSets, pkgName, "        ")
                appendLine("    }")
                appendLine()
                appendLine("    override fun onUnload() {")
                emitLifecycleCalls(onUnloadCalls + globalOnUnloadCalls, "        ")
                appendLine("    }")
                appendLine("}")
            }

            writeSourceFile("$pkgName.$className", fileContent)
        }
    }

    private fun generatePf4jExtensionsFile(
        routeFunctions: List<ExecutableElement>,
        cmdFunctions: List<ExecutableElement>,
        passiveFunctions: List<ExecutableElement>,
        pkgName: String
    ) {

        val extensionClasses = buildList {
            for (func in routeFunctions) {
                add("$pkgName.GeneratedRoute_${func.simpleName}")
            }
            for (func in cmdFunctions) {
                val cmdName = func.getAnnotation(Cmd::class.java)!!.name.replace("-", "_")
                add("$pkgName.GeneratedCmd_$cmdName")
            }
            for (func in passiveFunctions) {
                add("$pkgName.GeneratedPassive_${func.simpleName}")
            }
        }

        if (extensionClasses.isEmpty()) return

        val content = extensionClasses.joinToString("\n") { it }
        val filer = processingEnv.filer
        val resource = filer.createResource(
            StandardLocation.CLASS_OUTPUT,
            "",
            PF4J_EXTENSION_PATH
        )
        resource.openWriter().use { it.write(content) }
    }

    private fun checkVisibility(func: ExecutableElement, annotationName: String): Boolean {
        if (func.modifiers.contains(Modifier.PRIVATE)) {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "@$annotationName function '${func.simpleName}' must not be private. " +
                        "Top-level functions annotated with @$annotationName must be public or internal."
            )
            return false
        }
        return true
    }

    private fun isSuspendFunction(func: ExecutableElement): Boolean {
        return func.parameters.any { it.simpleName.toString() == COMPLETION_PARAM }
    }

    private fun hasMetaParam(func: ExecutableElement): Boolean {
        return func.parameters.any {
            it.simpleName.toString() != COMPLETION_PARAM && it.asType().toString() == "uesugi.spi.Meta"
        }
    }

    private fun toKotlinType(typeMirror: TypeMirror): String {
        return when (val raw = typeMirror.toString()) {
            "long", "java.lang.Long" -> "Long"
            "java.lang.String" -> "String"
            "int", "java.lang.Integer" -> "Int"
            "boolean", "java.lang.Boolean" -> "Boolean"
            "float", "java.lang.Float" -> "Float"
            "double", "java.lang.Double" -> "Double"
            "byte", "java.lang.Byte" -> "Byte"
            "short", "java.lang.Short" -> "Short"
            "char", "java.lang.Character" -> "Char"
            "void", "java.lang.Void" -> "Unit"
            else -> raw
        }
    }

    private fun String.escapeForLiteral(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")

    private fun toolSetClassName(name: String): String =
        "${TOOLSET_CLASS_PREFIX}${name.replace("-", "_")}"

    private fun resolveLifecycle(
        names: Array<String>,
        functions: Map<String, ExecutableElement>,
        annotationName: String,
        callerName: String
    ): List<String> = names.mapNotNull { name ->
        val func = functions[name]
        if (func == null) {
            processingEnv.messager.printMessage(
                Diagnostic.Kind.ERROR,
                "@$annotationName(\"$name\") not found, referenced by '$callerName'"
            )
            null
        } else {
            "${func.simpleName}()"
        }
    }

    private val createdDirs = mutableSetOf<java.io.File>()

    private fun writeSourceFile(qualifiedName: String, content: String) {
        val ktGeneratedDir = processingEnv.options[KAPT_GENERATED_OPTION]
            ?: run {
                processingEnv.messager.printMessage(
                    Diagnostic.Kind.WARNING,
                    "$KAPT_GENERATED_OPTION not set, generating in $FALLBACK_GEN_DIR"
                )
                FALLBACK_GEN_DIR
            }
        val packagePath = qualifiedName.substringBeforeLast(".").replace(".", "/")
        val className = qualifiedName.substringAfterLast(".")
        val targetDir = java.io.File(ktGeneratedDir, packagePath)
        if (targetDir !in createdDirs) {
            targetDir.mkdirs()
            createdDirs += targetDir
        }
        val sourceFile = java.io.File(targetDir, "$className.kt")
        sourceFile.writeText(content)
    }
}
