import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import xyz.jpenilla.runtask.task.AbstractRun
import xyz.jpenilla.runpaper.task.RunServer
import io.papermc.hangarpublishplugin.model.Platforms

plugins {
    kotlin("jvm") version "2.4.0"
    id("com.vanniktech.maven.publish") version "0.36.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("de.eldoria.plugin-yml.paper") version "0.9.0"
    id("com.modrinth.minotaur") version "2.9.0"
    id("io.papermc.hangar-publish-plugin") version "0.1.4"
    id("com.gradleup.shadow") version "9.4.2"
}

private val NAME = "Kite"
private val DESCRIPTION = "A lightweight Kotlin scripting plugin"
private val SUPPORTED_VERSIONS = listOf(
    "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2"
)

group = "dev.echonine.kite"
version = "1.5.2"

if (System.getenv("CI") != "true") {
    val commitHash = ProcessBuilder(listOf("git", "rev-parse", "--short", "--verify", "HEAD"))
            .directory(project.rootDir)
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText().trim()
    version = "$version+${if (commitHash.length == 7) commitHash else "DEV"}"
}

val shadowImplementation by configurations.creating

configurations.implementation {
    extendsFrom(shadowImplementation)
}

tasks.named("runServer") {
    dependsOn(tasks.named("jar"))
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://repo.alessiodp.com/snapshots") }
    maven { url = uri("https://repo.faststats.dev/releases") }
}

// Shading Maven Central dependencies is not needed.
// Only if the repository becomes unreliable in the future.
dependencies {
    // Kotlin Standard Library
    paperLibrary(kotlin("stdlib"))
    // Kotlin Scripting Libraries
    addDualDependency("org.jetbrains.kotlin:kotlin-scripting-jvm")
    addDualDependency("org.jetbrains.kotlin:kotlin-scripting-jvm-host")
    // https://github.com/Kotlin/kotlinx.coroutines
    addDualDependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    // https://github.com/PaperMC/Paper
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    // https://github.com/AlessioDP/libby
    shadowImplementation("com.alessiodp.libby:libby-bukkit:2.0.0-SNAPSHOT")
    // https://github.com/faststats-dev/faststats-java
    shadowImplementation("dev.faststats.metrics:bukkit:0.27.1")
}

paper {
    name = NAME
    description = DESCRIPTION
    main = "dev.echonine.kite.Kite"
    loader = "dev.echonine.kite.PluginLibrariesLoader"
    apiVersion = "1.21.1"
    foliaSupported = true
    hasOpenClassloader = true
    generateLibrariesJson = true
    website = "https://echonine.dev/kite/"
    authors = listOf("Saturn745", "Grabsky")
}

runPaper.folia.registerTask()

tasks {
    // Shared configuration for runServer and runFolia tasks.
    withType(RunServer::class) {
        minecraftVersion("26.1.1")
        downloadPlugins {
            // Downloading ViaVersion and ViaBackwards for testing on lower (or higher) versions.
            modrinth("viaversion", "5.7.2")
            modrinth("viabackwards", "5.7.2")
            // Downloading MiniPlaceholders 3.1.0. ID must be used because the same version number is used for multiple platforms.
            modrinth("miniplaceholders", "4zOT6txC")
            // Downloading PlaceholderAPI with Folia support included starting from 2.11.7.
            modrinth("placeholderapi", "2.12.2")
        }
    }
    // Configuring 'runServer' task to use JetBrains' JDK 21 for expanded hot-swap features.
    withType(AbstractRun::class) {
        javaLauncher = project.javaToolchains.launcherFor {
            vendor = JvmVendorSpec.JETBRAINS
            languageVersion = JavaLanguageVersion.of(25)
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
    shadowJar {
        configurations = listOf(shadowImplementation)
        archiveFileName.set("${project.name}-${version}.jar")
        relocate("dev.faststats", "dev.echonine.kite.libs.dev.faststats")
        relocate("com.alessiodp", "dev.echonine.kite.libs.com.alessiodp")
    }
}

tasks.modrinth {
    dependsOn(tasks.modrinthSyncBody)
}

// Returns the formatted release name.
tasks.register("getRelease") {
    print(version)
}

// Returns the formatted tag name.
tasks.register("getTag") {
    print(version)
}

// Adds specified dependency to 'paperLibrary' and 'api' configurations.
// This makes it easier for IDEA to resolve dependencies when working with .kite.kts scripts.
fun addDualDependency(dependencyNotation: String) {
    dependencies.add("paperLibrary", dependencyNotation)
    dependencies.add("api", dependencyNotation)
}

modrinth {
    projectId = "kite"
    uploadFile = tasks.jar.get()
    versionType = "beta"
    versionNumber = project.version as String
    gameVersions = SUPPORTED_VERSIONS
    loaders = listOf("paper", "purpur", "folia")
    token = System.getenv("MODRINTH_TOKEN")
    changelog = System.getenv("CHANGELOG")
    syncBodyFrom = rootProject.file("README.md").readText()
}

hangarPublish {
    publications.register("plugin") {
        id = "Kite"
        version = project.version as String
        channel = "Beta"
        changelog = System.getenv("CHANGELOG")
        apiKey = System.getenv("HANGAR_TOKEN")
        platforms {
            register(Platforms.PAPER) {
                jar = tasks.jar.flatMap { it.archiveFile }
                platformVersions = SUPPORTED_VERSIONS
            }
        }
    }
}

java {
    withSourcesJar()
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates("dev.echonine", "kite", project.version as String)
    pom {
        name = NAME
        description = DESCRIPTION
        inceptionYear = "2025"
        url = "https://github.com/EchoNineLabs/Kite"
        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/license/mit"
            }
        }
        developers {
            developer {
                id = "Saturn745"
                name = "Saturn"
                email = "element@echonine.dev"
                url = "https://github.com/Saturn745"
            }
            developer {
                id = "Grabsky"
                name = "Michał Czopek"
                email = "michal.czopek.foss@proton.me"
                url = "https://github.com/Grabsky"
            }
        }
        scm {
            connection = "scm:git:https://github.com/EchoNineLabs/Kite.git"
            developerConnection = "scm:git:https://github.com/EchoNineLabs/Kite.git"
            url = "https://github.com/EchoNineLabs/Kite"
        }
    }
}
