@file:OptIn(KspExperimental::class)

package uesugi.spi.annotation.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier

class KspAnnotationProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {

    private companion object {
        const val DEFAULT_TOOLSET = "default"
        const val TOOLSET_CLASS_PREFIX = "GeneratedToolSet_"
        const val PF4J_EXTENSION_PATH = "META-INF/services/org.pf4j.Extension"

        const val ANN_ROUTE = "uesugi.spi.annotation.Route"
        const val ANN_CMD = "uesugi.spi.annotation.Cmd"
        const val ANN_PASSIVE = "uesugi.spi.annotation.Passive"
        const val ANN_TOOL = "uesugi.spi.annotation.Tool"
        const val ANN_DEFINITION = "uesugi.spi.annotation.Definition"
        const val ANN_LLM_DESC = "uesugi.spi.annotation.LLMDescription"
        const val ANN_ON_LOAD = "uesugi.spi.annotation.OnLoad"
        const val ANN_ON_UNLOAD = "uesugi.spi.annotation.OnUnload"
        const val ANN_ON_START = "uesugi.spi.annotation.OnStart"
        const val ANN_ON_STOP = "uesugi.spi.annotation.OnStop"
    }

    private var processed = false

    // ========== Entry ==========

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()

        val (pkgName, defAnno) = findFileDefinition(resolver) ?: run {
            logger.warn("No @file:Definition found, skipping KSP processing")
            return emptyList()
        }

        val routeFunctions = resolver.getSymbolsWithAnnotation(ANN_ROUTE)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.checkVisibility("Route") }
            .toList()

        val cmdFunctions = resolver.getSymbolsWithAnnotation(ANN_CMD)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.checkVisibility("Cmd") }
            .toList()

        val passiveFunctions = resolver.getSymbolsWithAnnotation(ANN_PASSIVE)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.checkVisibility("Passive") }
            .toList()

        val toolFunctions = resolver.getSymbolsWithAnnotation(ANN_TOOL)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.checkVisibility("Tool") }
            .toList()

        // Lifecycle functions
        val onLoadFunctions = resolver.getSymbolsWithAnnotation(ANN_ON_LOAD)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.checkVisibility("OnLoad") }
            .toList()
        val namedOnLoad = onLoadFunctions
            .filter { it.findAnnotation(ANN_ON_LOAD)?.stringArg("value")?.isNotEmpty() == true }
            .associateBy { it.findAnnotation(ANN_ON_LOAD)!!.stringArg("value") }
        val globalOnLoad = onLoadFunctions
            .filter { it.findAnnotation(ANN_ON_LOAD)?.stringArg("value")?.isEmpty() != false }
            .map { "${it.simpleName.asString()}()" }

        val onUnloadFunctions = resolver.getSymbolsWithAnnotation(ANN_ON_UNLOAD)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.checkVisibility("OnUnload") }
            .toList()
        val namedOnUnload = onUnloadFunctions
            .filter { it.findAnnotation(ANN_ON_UNLOAD)?.stringArg("value")?.isNotEmpty() == true }
            .associateBy { it.findAnnotation(ANN_ON_UNLOAD)!!.stringArg("value") }
        val globalOnUnload = onUnloadFunctions
            .filter { it.findAnnotation(ANN_ON_UNLOAD)?.stringArg("value")?.isEmpty() != false }
            .map { "${it.simpleName.asString()}()" }

        // Plugin lifecycle
        val onStartFuncs = resolver.getSymbolsWithAnnotation(ANN_ON_START)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.checkVisibility("OnStart") }
            .toList()
        if (onStartFuncs.size > 1) {
            logger.error("Only one @OnStart function allowed per plugin")
            return emptyList()
        }

        val onStopFuncs = resolver.getSymbolsWithAnnotation(ANN_ON_STOP)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.checkVisibility("OnStop") }
            .toList()
        if (onStopFuncs.size > 1) {
            logger.error("Only one @OnStop function allowed per plugin")
            return emptyList()
        }

        // Generate
        generatePluginDelegate(pkgName, defAnno, onStartFuncs.firstOrNull(), onStopFuncs.firstOrNull())
        // KAPT PluginDefinitionProcessor generates plugin.properties from @PluginDefinition annotation
        generateToolSets(toolFunctions, pkgName)
        generateRouteExtensions(routeFunctions, pkgName, namedOnLoad, namedOnUnload, globalOnLoad, globalOnUnload)
        generateCmdExtensions(cmdFunctions, pkgName, namedOnLoad, namedOnUnload, globalOnLoad, globalOnUnload)
        generatePassiveExtensions(passiveFunctions, pkgName, namedOnLoad, namedOnUnload, globalOnLoad, globalOnUnload)

        val hasExtension = routeFunctions.isNotEmpty() || cmdFunctions.isNotEmpty() || passiveFunctions.isNotEmpty()
        if (!hasExtension && toolFunctions.isNotEmpty()) {
            generateDefaultPassive(toolFunctions, pkgName)
        }

        generatePf4jExtensionsFile(routeFunctions, cmdFunctions, passiveFunctions, toolFunctions, pkgName, hasExtension)

        processed = true
        return emptyList()
    }

    // ========== @file:Definition ==========

    private fun findFileDefinition(resolver: Resolver): Pair<String, KSAnnotation?>? {
        for (file in resolver.getNewFiles()) {
            val defAnno = file.annotations.find { it.shortName.asString() == "Definition" }
            if (defAnno != null) return file.packageName.asString() to defAnno
        }
        for (file in resolver.getAllFiles()) {
            val defAnno = file.annotations.find { it.shortName.asString() == "Definition" }
            if (defAnno != null) return file.packageName.asString() to defAnno
        }
        return null
    }

    // ========== Plugin delegate ==========

    private fun generatePluginDelegate(
        pkgName: String,
        defAnno: KSAnnotation?,
        onStartFunc: KSFunctionDeclaration?,
        onStopFunc: KSFunctionDeclaration?,
    ) {
        val className = "GeneratedPlugin"
        val needsRunBlocking = onStartFunc.isSuspendFunc() || onStopFunc.isSuspendFunc()

        val content = buildString {
            appendLine("package $pkgName")
            appendLine()
            appendLine("import uesugi.spi.AgentPlugin")
            appendLine("import uesugi.spi.PluginDefinition")
            if (needsRunBlocking) {
                appendLine("import kotlinx.coroutines.runBlocking")
            }
            appendLine()
            appendLine(buildPluginDefinitionAnnotation(defAnno))
            appendLine("class $className : AgentPlugin() {")
            if (onStartFunc != null) {
                val call = onStartFunc.simpleName.asString() + "()"
                appendLine("    override fun start() {")
                appendLine("        super.start()")
                if (onStartFunc.isSuspendFunc()) {
                    appendLine("        runBlocking { $call }")
                } else {
                    appendLine("        $call")
                }
                appendLine("    }")
            }
            if (onStopFunc != null) {
                val call = onStopFunc.simpleName.asString() + "()"
                appendLine("    override fun stop() {")
                if (onStopFunc.isSuspendFunc()) {
                    appendLine("        runBlocking { $call }")
                } else {
                    appendLine("        $call")
                }
                appendLine("        super.stop()")
                appendLine("    }")
            }
            appendLine("}")
        }

        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false),
            packageName = pkgName,
            fileName = className
        ).writer().use { it.write(content) }
    }

    private fun buildPluginDefinitionAnnotation(defAnno: KSAnnotation?): String {
        if (defAnno == null) return "@PluginDefinition"
        val params = mutableListOf<String>()
        listOf("pluginId", "version", "requires", "dependencies", "description", "provider", "license").forEach { key ->
            val value = defAnno.stringArg(key).takeUnless { it.isEmpty() } ?: options[key]?.takeUnless { it.isEmpty() }
            if (value != null) params.add("$key = \"${value.escapeForLiteral()}\"")
        }
        return if (params.isEmpty()) "@PluginDefinition" else "@PluginDefinition(${params.joinToString(", ")})"
    }

    // ========== ToolSets ==========

    private fun generateToolSets(toolFunctions: List<KSFunctionDeclaration>, pkgName: String) {
        val grouped = toolFunctions.groupBy {
            it.findAnnotation(ANN_TOOL)?.stringArg("set")?.takeUnless { s -> s.isEmpty() } ?: DEFAULT_TOOLSET
        }

        for ((setName, functions) in grouped) {
            val className = toolSetClassName(setName)
            val hasNonSuspend = functions.any { !it.isSuspendFunc() }

            val content = buildString {
                appendLine("package $pkgName")
                appendLine()
                appendLine("import uesugi.spi.MetaToolSet")
                appendLine("import uesugi.spi.PluginContext")
                appendLine("import uesugi.spi.annotation.withPluginContext")
                appendLine("import ai.koog.agents.core.tools.annotations.Tool as KoogTool")
                appendLine("import ai.koog.agents.core.tools.annotations.LLMDescription as KoogLLMDescription")
                if (hasNonSuspend) {
                    appendLine("import kotlinx.coroutines.Dispatchers")
                    appendLine("import kotlinx.coroutines.withContext")
                }
                for (func in functions) {
                    appendLine("import $pkgName.${func.simpleName.asString()} as _erii_${func.simpleName.asString()}")
                }
                appendLine()
                appendLine("class $className(private val context: PluginContext) : MetaToolSet {")

                for (func in functions) {
                    val funcName = func.simpleName.asString()
                    val funcIsSuspend = func.isSuspendFunc()
                    val descAnno = func.findAnnotation(ANN_LLM_DESC)

                    val (paramDecls, argNames) = buildToolParams(func)

                    descAnno?.stringArg("description")?.takeUnless { it.isEmpty() }?.let {
                        appendLine("    @KoogLLMDescription(\"${it.escapeForLiteral()}\")")
                    }
                    appendLine("    @KoogTool")

                    val argsStr = argNames.joinToString(", ")
                    val callExpr = if (funcIsSuspend) {
                        "withPluginContext(context) { _erii_$funcName($argsStr) }"
                    } else {
                        "withPluginContext(context) { withContext(Dispatchers.IO) { _erii_$funcName($argsStr) } }"
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

            codeGenerator.createNewFile(
                dependencies = Dependencies(aggregating = false),
                packageName = pkgName,
                fileName = className
            ).writer().use { it.write(content) }
        }
    }

    private fun buildToolParams(func: KSFunctionDeclaration): Pair<List<String>, List<String>> {
        val paramDecls = mutableListOf<String>()
        val argNames = mutableListOf<String>()

        for (param in func.parameters) {
            val paramType = param.type.resolve().declaration.qualifiedName?.asString()
                ?.toShortKotlinType() ?: param.type.resolve().toString()
            val paramName = param.name?.asString() ?: continue
            val descAnno = param.findAnnotation(ANN_LLM_DESC)

            val line = buildString {
                descAnno?.stringArg("description")?.takeUnless { it.isEmpty() }?.let {
                    appendLine("        @KoogLLMDescription(\"${it.escapeForLiteral()}\")")
                }
                append("        $paramName: $paramType")
            }
            paramDecls.add(line)
            argNames.add(paramName)
        }

        return paramDecls to argNames
    }

    // ========== Route Extensions ==========

    private fun generateRouteExtensions(
        routeFunctions: List<KSFunctionDeclaration>,
        pkgName: String,
        namedOnLoad: Map<String, KSFunctionDeclaration>,
        namedOnUnload: Map<String, KSFunctionDeclaration>,
        globalOnLoad: List<String>,
        globalOnUnload: List<String>,
    ) {
        val pluginClassName = "GeneratedPlugin"

        for (func in routeFunctions) {
            val routeAnno = func.findAnnotation(ANN_ROUTE) ?: continue
            val funcName = func.simpleName.asString()
            val className = "GeneratedRoute_${funcName}"
            val onLoadCalls = resolveLifecycleCalls(routeAnno.stringArrayArg("onLoad"), namedOnLoad, funcName)
            val onUnloadCalls = resolveLifecycleCalls(routeAnno.stringArrayArg("onUnload"), namedOnUnload, funcName)
            val handlerIsSuspend = func.isSuspendFunc()
            val handlerHasMeta = func.hasMetaParam()
            val callExpr = if (handlerHasMeta) "$funcName(meta)" else "$funcName()"

            val content = buildString {
                appendLine("package $pkgName")
                appendLine()
                emitExtensionImports(handlerHasMeta, handlerIsSuspend)
                appendLine()
                appendLine("@Extension")
                appendLine("class $className : RouteExtension<$pluginClassName> {")
                appendLine("    override val matcher: Pair<String, String>")
                appendLine(
                    "        get() = \"${
                        routeAnno.stringArg("path").escapeForLiteral()
                    }\" to \"${routeAnno.stringArg("method").escapeForLiteral()}\""
                )
                appendLine()
                appendLine("    override fun onLoad(context: PluginContext) {")
                emitLifecycleCalls(onLoadCalls + globalOnLoad, "        ")
                appendLine("        context.chain { meta ->")
                appendLine("            withPluginContext(context) {")
                emitHandlerInvocation(callExpr, handlerIsSuspend, handlerHasMeta, "                ")
                appendLine("            }")
                appendLine("        }")
                emitToolSetRegistrations(routeAnno.stringArrayArg("toolSets"), pkgName, "        ")
                appendLine("    }")
                appendLine()
                appendLine("    override fun onUnload() {")
                emitLifecycleCalls(onUnloadCalls + globalOnUnload, "        ")
                appendLine("    }")
                appendLine("}")
            }

            codeGenerator.createNewFile(
                dependencies = Dependencies(aggregating = false),
                packageName = pkgName,
                fileName = className
            ).writer().use { it.write(content) }
        }
    }

    // ========== Cmd Extensions ==========

    private fun generateCmdExtensions(
        cmdFunctions: List<KSFunctionDeclaration>,
        pkgName: String,
        namedOnLoad: Map<String, KSFunctionDeclaration>,
        namedOnUnload: Map<String, KSFunctionDeclaration>,
        globalOnLoad: List<String>,
        globalOnUnload: List<String>,
    ) {
        val pluginClassName = "GeneratedPlugin"

        for (func in cmdFunctions) {
            val cmdAnno = func.findAnnotation(ANN_CMD) ?: continue
            val funcName = func.simpleName.asString()
            val cmdName = cmdAnno.stringArg("name")
            val className = "GeneratedCmd_${cmdName.replace("-", "_")}"
            val aliases = cmdAnno.stringArrayArg("alias")
            val aliasList = aliases.joinToString(", ") { "\"$it\"" }
            val onLoadCalls = resolveLifecycleCalls(cmdAnno.stringArrayArg("onLoad"), namedOnLoad, funcName)
            val onUnloadCalls = resolveLifecycleCalls(cmdAnno.stringArrayArg("onUnload"), namedOnUnload, funcName)
            val handlerIsSuspend = func.isSuspendFunc()
            val handlerHasMeta = func.hasMetaParam()
            val callExpr = if (handlerHasMeta) "$funcName(holder.args, meta)" else "$funcName(holder.args)"

            val content = buildString {
                appendLine("package $pkgName")
                appendLine()
                emitExtensionImports(handlerHasMeta, handlerIsSuspend)
                appendLine()
                appendLine("@Extension")
                appendLine("class $className : SlashCmdExtension<$pluginClassName> {")
                appendLine("    override val cmd: String")
                appendLine("        get() = \"$cmdName\"")
                if (aliases.isNotEmpty()) {
                    appendLine("    override val alias: List<String>")
                    appendLine("        get() = listOf($aliasList)")
                }
                appendLine()
                appendLine("    override fun onLoad(context: PluginContext) {")
                emitLifecycleCalls(onLoadCalls + globalOnLoad, "        ")
                appendLine("        context.chain { meta ->")
                appendLine("            val holder = meta.parser(Unit)")
                appendLine("            withPluginContext(context) {")
                emitHandlerInvocation(callExpr, handlerIsSuspend, handlerHasMeta, "                ")
                appendLine("            }")
                appendLine("        }")
                emitToolSetRegistrations(cmdAnno.stringArrayArg("toolSets"), pkgName, "        ")
                appendLine("    }")
                appendLine()
                appendLine("    override fun onUnload() {")
                emitLifecycleCalls(onUnloadCalls + globalOnUnload, "        ")
                appendLine("    }")
                appendLine("}")
            }

            codeGenerator.createNewFile(
                dependencies = Dependencies(aggregating = false),
                packageName = pkgName,
                fileName = className
            ).writer().use { it.write(content) }
        }
    }

    // ========== Passive Extensions ==========

    private fun generatePassiveExtensions(
        passiveFunctions: List<KSFunctionDeclaration>,
        pkgName: String,
        namedOnLoad: Map<String, KSFunctionDeclaration>,
        namedOnUnload: Map<String, KSFunctionDeclaration>,
        globalOnLoad: List<String>,
        globalOnUnload: List<String>,
    ) {
        val pluginClassName = "GeneratedPlugin"

        for (func in passiveFunctions) {
            val passiveAnno = func.findAnnotation(ANN_PASSIVE) ?: continue
            val funcName = func.simpleName.asString()
            val className = "GeneratedPassive_${funcName}"
            val onLoadCalls = resolveLifecycleCalls(passiveAnno.stringArrayArg("onLoad"), namedOnLoad, funcName)
            val onUnloadCalls = resolveLifecycleCalls(passiveAnno.stringArrayArg("onUnload"), namedOnUnload, funcName)
            val handlerIsSuspend = func.isSuspendFunc()
            val handlerHasMeta = func.hasMetaParam()
            val callExpr = if (handlerHasMeta) "$funcName(meta)" else "$funcName()"

            val content = buildString {
                appendLine("package $pkgName")
                appendLine()
                emitExtensionImports(handlerHasMeta, handlerIsSuspend)
                appendLine()
                appendLine("@Extension")
                appendLine("class $className : PassiveExtension<$pluginClassName> {")
                appendLine("    override fun onLoad(context: PluginContext) {")
                emitLifecycleCalls(onLoadCalls + globalOnLoad, "        ")
                appendLine("        context.chain { meta ->")
                appendLine("            withPluginContext(context) {")
                emitHandlerInvocation(callExpr, handlerIsSuspend, handlerHasMeta, "                ")
                appendLine("            }")
                appendLine("        }")
                emitToolSetRegistrations(passiveAnno.stringArrayArg("toolSets"), pkgName, "        ")
                appendLine("    }")
                appendLine()
                appendLine("    override fun onUnload() {")
                emitLifecycleCalls(onUnloadCalls + globalOnUnload, "        ")
                appendLine("    }")
                appendLine("}")
            }

            codeGenerator.createNewFile(
                dependencies = Dependencies(aggregating = false),
                packageName = pkgName,
                fileName = className
            ).writer().use { it.write(content) }
        }
    }

    // ========== Default Passive (auto-generated for tool-only plugins) ==========

    private fun generateDefaultPassive(toolFunctions: List<KSFunctionDeclaration>, pkgName: String) {
        val pluginClassName = "GeneratedPlugin"
        val toolSetClasses = toolFunctions
            .map { it.findAnnotation(ANN_TOOL)?.stringArg("set")?.takeUnless { s -> s.isEmpty() } ?: DEFAULT_TOOLSET }
            .distinct()
            .map { toolSetClassName(it) }

        val className = "GeneratedPassive_default"

        val content = buildString {
            appendLine("package $pkgName")
            appendLine()
            appendLine("import org.pf4j.Extension")
            appendLine("import uesugi.spi.PassiveExtension")
            appendLine("import uesugi.spi.PluginContext")
            appendLine()
            appendLine("@Extension")
            appendLine("class $className : PassiveExtension<$pluginClassName> {")
            appendLine("    override fun onLoad(context: PluginContext) {")
            for (tsClass in toolSetClasses) {
                appendLine("        context.tool { { $pkgName.$tsClass(context) } }")
            }
            appendLine("    }")
            appendLine()
            appendLine("    override fun onUnload() {")
            appendLine("    }")
            appendLine("}")
        }

        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false),
            packageName = pkgName,
            fileName = className
        ).writer().use { it.write(content) }
    }

    // ========== PF4J Extensions file ==========

    private fun generatePf4jExtensionsFile(
        routeFunctions: List<KSFunctionDeclaration>,
        cmdFunctions: List<KSFunctionDeclaration>,
        passiveFunctions: List<KSFunctionDeclaration>,
        toolFunctions: List<KSFunctionDeclaration>,
        pkgName: String,
        hasExtension: Boolean,
    ) {
        val extensionClasses = buildList {
            for (func in routeFunctions) {
                add("$pkgName.GeneratedRoute_${func.simpleName.asString()}")
            }
            for (func in cmdFunctions) {
                val cmdName = func.findAnnotation(ANN_CMD)?.stringArg("name")?.replace("-", "_") ?: continue
                add("$pkgName.GeneratedCmd_$cmdName")
            }
            for (func in passiveFunctions) {
                add("$pkgName.GeneratedPassive_${func.simpleName.asString()}")
            }
            if (!hasExtension && toolFunctions.isNotEmpty()) {
                add("$pkgName.GeneratedPassive_default")
            }
        }

        if (extensionClasses.isEmpty()) return

        val content = extensionClasses.joinToString("\n")

        codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false),
            packageName = "",
            fileName = PF4J_EXTENSION_PATH,
            extensionName = ""
        ).writer().use { it.write(content) }
    }

    // ========== Import / Handler / Lifecycle helpers ==========

    private fun StringBuilder.emitExtensionImports(handlerHasMeta: Boolean, handlerIsSuspend: Boolean) {
        appendLine("import org.pf4j.Extension")
        appendLine("import uesugi.spi.*")
        appendLine("import uesugi.spi.annotation.withPluginContext")
        when {
            !handlerHasMeta && handlerIsSuspend -> appendLine("import uesugi.spi.annotation.withMeta")
            !handlerHasMeta && !handlerIsSuspend -> appendLine("import uesugi.spi.annotation.withMetaIO")
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

            else -> appendLine("$indent$callExpr")
        }
    }

    private fun StringBuilder.emitToolSetRegistrations(
        toolSets: List<String>, packageName: String, indent: String
    ) {
        val filtered = toolSets.filter { it != DEFAULT_TOOLSET }
        if (filtered.isNotEmpty()) {
            for (setName in filtered) {
                val tsClassName = toolSetClassName(setName)
                appendLine("${indent}context.tool { { $packageName.$tsClassName(context) } }")
            }
        }
    }

    private fun StringBuilder.emitLifecycleCalls(calls: List<String>, indent: String) {
        for (call in calls) {
            appendLine("$indent$call")
        }
    }

    // ========== Utility methods ==========

    private fun resolveLifecycleCalls(
        names: List<String>,
        functions: Map<String, KSFunctionDeclaration>,
        callerName: String,
    ): List<String> = names.mapNotNull { name ->
        val func = functions[name]
        if (func == null) {
            logger.error("@OnLoad/OnUnload(\"$name\") not found, referenced by '$callerName'")
            null
        } else {
            "${func.simpleName.asString()}()"
        }
    }

    private fun KSFunctionDeclaration.hasMetaParam(): Boolean =
        parameters.any {
            it.type.resolve().declaration.qualifiedName?.asString() == "uesugi.spi.Meta"
        }

    private fun KSFunctionDeclaration?.isSuspendFunc(): Boolean =
        this != null && Modifier.SUSPEND in modifiers

    private fun KSFunctionDeclaration.checkVisibility(annotationName: String): Boolean {
        if (Modifier.PRIVATE in modifiers) {
            logger.error(
                "@$annotationName function '${simpleName.asString()}' must not be private. " +
                        "Top-level functions annotated with @$annotationName must be public or internal."
            )
            return false
        }
        return true
    }

    private fun String.escapeForLiteral(): String =
        replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    private fun String.toShortKotlinType(): String = when (this) {
        "kotlin.String" -> "String"
        "kotlin.Int" -> "Int"
        "kotlin.Long" -> "Long"
        "kotlin.Boolean" -> "Boolean"
        "kotlin.Float" -> "Float"
        "kotlin.Double" -> "Double"
        "kotlin.Byte" -> "Byte"
        "kotlin.Short" -> "Short"
        "kotlin.Char" -> "Char"
        "kotlin.Unit" -> "Unit"
        "kotlin.collections.List" -> "List"
        "kotlin.collections.Map" -> "Map"
        "kotlin.collections.Set" -> "Set"
        else -> this
    }

    private fun toolSetClassName(name: String): String =
        "${TOOLSET_CLASS_PREFIX}${name.replace("-", "_")}"

    // ========== KSP Annotation helpers ==========

    private fun KSAnnotated.findAnnotation(fqn: String): KSAnnotation? =
        annotations.find { it.annotationType.resolve().declaration.qualifiedName?.asString() == fqn }

    private fun KSAnnotation.stringArg(name: String): String =
        arguments.firstOrNull { it.name?.asString() == name }?.value as? String ?: ""

    private fun KSAnnotation.stringArrayArg(name: String): List<String> {
        val arg = arguments.firstOrNull { it.name?.asString() == name } ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (arg.value as? List<String>) ?: emptyList()
    }
}
