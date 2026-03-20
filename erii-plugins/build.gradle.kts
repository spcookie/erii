plugins {
    alias(libs.plugins.kotlin.jvm)
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        implementation(project(":erii-spi"))
        compileOnly("org.pf4j:pf4j:3.15.0")
        compileOnly("top.mrxiaom.mirai:overflow-core:1.0.8")
    }

    val pluginId: String by project
    val pluginClass: String by project
    val pluginProvider: String by project
    val pluginDependencies: String by project

    val pluginsDir = rootProject.layout.buildDirectory.dir("plugins")

    tasks.jar {
        manifest {
            attributes(
                mapOf(
                    "Plugin-Class" to pluginClass,
                    "Plugin-Id" to pluginId,
                    "Plugin-Version" to archiveVersion.get().toString(),
                    "Plugin-Provider" to pluginProvider,
                    "Plugin-Dependencies" to pluginDependencies
                )
            )
        }
    }

    val plugin by tasks.registering(Jar::class) {
        archiveBaseName.set(pluginId)

        into("classes") {
            with(tasks.jar.get())
        }

        into("lib") {
            from(configurations.runtimeClasspath)
        }

        archiveExtension.set("zip")
    }

    tasks.register("assemblePlugin", Copy::class) {
        from(plugin)
        into(pluginsDir)
    }
}

tasks.register<Copy>("assemblePlugins") {
    dependsOn(subprojects.map { it.tasks.named("assemblePlugin") })
}

tasks.named("build") {
    dependsOn("assemblePlugins")
}