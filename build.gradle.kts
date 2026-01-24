import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import xyz.jpenilla.runtask.task.AbstractRun
import xyz.jpenilla.runpaper.task.RunServer
import io.papermc.hangarpublishplugin.model.Platforms

plugins {
    kotlin("jvm") version "2.3.0"
    id("maven-publish")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("de.eldoria.plugin-yml.paper") version "0.8.0"
    id("com.modrinth.minotaur") version "2.8.10"
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
}

private val VERSION = "1.2.0"
private val RUN_NUMBER = System.getenv("GITHUB_RUN_NUMBER") ?: "DEV"
group = "dev.echonine.kite"
version = "$VERSION+$RUN_NUMBER"

repositories {
    mavenCentral()
    maven { name = "PaperMC"; url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { name = "TheNextLvl"; url = uri("https://repo.thenextlvl.net/releases") }
}

dependencies {
    // Kotlin Standard Library
    paperLibrary(kotlin("stdlib"))
    // Runtime Dependencies
    paperLibrary("io.github.revxrsal:zapper.api:1.0.3")
    paperLibrary("dev.faststats.metrics:bukkit:0.13.1")
    // Kotlin Scripting Libraries
    addDualDependency("org.jetbrains.kotlin:kotlin-scripting-jvm")
    addDualDependency("org.jetbrains.kotlin:kotlin-scripting-jvm-host")
    addDualDependency("org.jetbrains.kotlin:kotlin-scripting-dependencies")
    addDualDependency("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven")
    // https://github.com/Kotlin/kotlinx.coroutines
    addDualDependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // https://github.com/PaperMC/Paper
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

paper {
    main = "dev.echonine.kite.Kite"
    loader = "dev.echonine.kite.PluginLibrariesLoader"
    apiVersion = "1.21.1"
    foliaSupported = true
    hasOpenClassloader = true
    generateLibrariesJson = true
    description = "A lightweight Kotlin scripting plugin"
    website = "https://echonine.dev/kite/"
    authors = listOf("Saturn745", "Grabsky")
}

runPaper.folia.registerTask()

tasks {
    // Shared configuration for runServer and runFolia tasks.
    withType(RunServer::class) {
        minecraftVersion("1.21.1")
        downloadPlugins {
            // Downloading ViaVersion and ViaBackwards for testing on lower (or higher) versions.
            modrinth("viaversion", "5.6.0")
            modrinth("viabackwards", "5.6.0")
            // Downloading MiniPlaceholders 3.1.0. ID must be used because the same version number is used for multiple platforms.
            modrinth("miniplaceholders", "4zOT6txC")
            // Downloading PlaceholderAPI with Folia support included starting from 2.11.7.
            modrinth("placeholderapi", "2.11.7")
        }
    }
    // Configuring 'runServer' task to use JetBrains' JDK 21 for expanded hot-swap features.
    withType(AbstractRun::class) {
        javaLauncher = project.javaToolchains.launcherFor {
            vendor = JvmVendorSpec.JETBRAINS
            languageVersion = JavaLanguageVersion.of(21)
        }
        jvmArgs("-XX:+AllowEnhancedClassRedefinition", "-Dcom.mojang.eula.agree=true", "-Dnet.kyori.ansi.colorLevel=truecolor")
    }
    withType(KotlinCompile::class) {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
            // https://kotlinlang.org/docs/context-parameters.html
            freeCompilerArgs = listOf("-Xcontext-parameters")
        }
    }

}

tasks.modrinth {
    dependsOn(tasks.modrinthSyncBody)
}

publishing.publications {
    create("maven", MavenPublication::class) {
        from(components["kotlin"])
    }
}

// Returns the formatted release name.
tasks.register("getRelease") {
    print(VERSION)
}

// Returns the formatted tag name.
tasks.register("getTag") {
    print("${VERSION}+${RUN_NUMBER}")
}

// Adds specified dependency to 'paperLibrary' and 'api' configurations.
// This makes it easier for IDEA to resolve dependencies when working with .kite.kts scripts.
fun addDualDependency(dependencyNotation: String) {
    dependencies.add("paperLibrary", dependencyNotation)
    dependencies.add("api", dependencyNotation)
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("kite")
    versionNumber.set(version.toString())
    versionType.set("beta")
    uploadFile.set(tasks.jar.get())
    gameVersions.addAll("1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11")
    loaders.addAll("paper", "purpur", "folia")
    changelog.set(System.getenv("CHANGELOG"))
    syncBodyFrom = rootProject.file("README.md").readText()
}

hangarPublish {
    publications.register("plugin") {
        version.set(project.version as String)
        id.set("Kite")
        channel.set("Beta")
        changelog.set(System.getenv("CHANGELOG"))

        apiKey.set(System.getenv("HANGAR_TOKEN"))

        platforms {
            register(Platforms.PAPER) {
                jar.set(tasks.jar.flatMap { it.archiveFile })
                platformVersions.set(listOf("1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11"))
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("api") {
            groupId = "dev.echonine.kite"
            artifactId = "kite"
            version = (project.version as String).split("+")[0] // Use only the version part before '+' for publishing.

            from(components["java"])

            versionMapping {
                usage("java-api") { fromResolutionOf("runtimeClasspath") }
                usage("java-runtime") { fromResolutionResult() }
            }

            repositories {
                maven {
                    name = "CodebergPackages"
                    url = uri("https://codeberg.org/api/packages/EchoNine/maven")
                    credentials(HttpHeaderCredentials::class.java) {
                        name = "Authorization"
                        value = "token ${System.getenv("CODEBERG_TOKEN")}"
                    }
                    authentication {
                        val header by registering(HttpHeaderAuthentication::class)
                    }
                }
            }
        }
    }
}
