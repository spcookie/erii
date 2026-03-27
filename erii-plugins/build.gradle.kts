plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
}

val pluginsDir: Provider<Directory> = project.layout.buildDirectory.dir("plugins")

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.kapt")

    kapt {
        arguments {
            arg("plugin.id", project.name)
            arg("plugin.version", project.version.toString())
        }
    }

    dependencies {
        compileOnly(project(":erii-common"))
        compileOnly(project(":erii-spi"))

        compileOnly("top.mrxiaom.mirai:overflow-core:1.0.8")
        compileOnly("org.jobrunr:jobrunr:8.3.1")
        compileOnly("com.github.ajalt.clikt:clikt:5.1.0")
        compileOnly("ai.koog:koog-agents:0.7.2")
        compileOnly("org.jetbrains.exposed:exposed-core:1.1.1")
        compileOnly("org.jetbrains.exposed:exposed-jdbc:1.1.1")
        compileOnly("io.ktor:ktor-server-core:3.3.2")
        compileOnly("io.ktor:ktor-client-core:3.3.2")
        compileOnly("com.typesafe:config:1.4.0")
        compileOnly("org.pf4j:pf4j:3.15.0")
        compileOnly("com.squareup.okio:okio-jvm:3.6.0")
        compileOnly("io.ktor:ktor-serialization-jackson:3.3.2")
        compileOnly("org.jetbrains.kotlinx:atomicfu:0.23.2")
        compileOnly("io.arrow-kt:arrow-core:2.0.0")
        compileOnly("io.github.oshai:kotlin-logging-jvm:7.0.13")
    }

    val plugin by tasks.registering(Zip::class) {
        archiveBaseName.set(project.name)
        archiveExtension.set("zip")

        from(
            sourceSets.main.map {
                it.output.asFileTree.matching {
                    include("plugin.properties")
                }
            }
        )

        into("classes") {
            from(
                tasks.named<Jar>("jar")
                    .flatMap { it.archiveFile }
                    .map { zipTree(it) }
            ) {
                exclude("plugin.properties")
            }
        }

        into("lib") {
            from(
                configurations.runtimeClasspath.get()
                    .filterNot { file ->
                        val dep = configurations.runtimeClasspath.get()
                            .resolvedConfiguration
                            .resolvedArtifacts
                            .find { it.file == file }
                        dep?.moduleVersion?.id?.group in setOf(
                            "org.jetbrains.kotlin",
                            "org.jetbrains.kotlinx",
                            "org.jetbrains"
                        )
                    }
            )
        }
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