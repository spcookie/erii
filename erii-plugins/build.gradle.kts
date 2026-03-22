plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "uesugi.plugin"
version = "0.0.1"

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        compileOnly(project(":erii-common"))
        compileOnly(project(":erii-spi"))

        compileOnly("top.mrxiaom.mirai:overflow-core:1.0.8")
        compileOnly("org.jobrunr:jobrunr:8.3.1")
        compileOnly("com.github.ajalt.clikt:clikt:5.1.0")
        compileOnly("com.squareup.okio:okio-jvm:3.6.0")
        compileOnly("ai.koog:koog-agents:0.6.3")
        compileOnly("org.jetbrains.exposed:exposed-core:1.0.0-rc-3")
        compileOnly("org.jetbrains.exposed:exposed-jdbc:1.0.0-rc-3")
        compileOnly("io.ktor:ktor-server-core:3.3.2")
        compileOnly("io.ktor:ktor-serialization-jackson:3.3.2")
        compileOnly("io.ktor:ktor-server-content-negotiation:3.3.2")
        compileOnly("io.ktor:ktor-server-netty:3.3.2")
        compileOnly("io.ktor:ktor-client-core:3.3.2")
        compileOnly("com.typesafe:config:1.4.0")
        compileOnly("org.pf4j:pf4j:3.15.0")
        compileOnly("org.jetbrains.kotlinx:atomicfu:0.23.2")
        compileOnly("io.arrow-kt:arrow-core:2.0.0")
    }

    val pluginId: String by project
    val pluginClass: String by project
    val pluginProvider: String? by project
    val pluginDependencies: String? by project

    val pluginsDir = rootProject.layout.buildDirectory.dir("plugins")

    tasks.jar {
        archiveVersion.set(version.toString())
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

    val plugin by tasks.registering(Zip::class) {
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
