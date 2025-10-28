import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import xyz.jpenilla.runtask.task.AbstractRun
import xyz.jpenilla.runpaper.task.RunServer

plugins {
    kotlin("jvm") version "2.2.21"
    id("maven-publish")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("de.eldoria.plugin-yml.paper") version "0.8.0"
}

private val VERSION = "1.0.0"
private val RUN_NUMBER = System.getenv("GITHUB_RUN_NUMBER") ?: "DEV"

group = "dev.echonine.kite"
version = "$VERSION+$RUN_NUMBER"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Kotlin Standard Library
    paperLibrary(kotlin("stdlib"))
    // Kotlin Scripting Libraries
    addDualDependency("org.jetbrains.kotlin:kotlin-scripting-jvm")
    addDualDependency("org.jetbrains.kotlin:kotlin-scripting-common")
    addDualDependency("org.jetbrains.kotlin:kotlin-scripting-dependencies")
    addDualDependency("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven")
    addDualDependency("org.jetbrains.kotlin:kotlin-scripting-jvm-host")
    addDualDependency("org.jetbrains.kotlin:kotlin-script-runtime")
    // https://github.com/Kotlin/kotlinx.coroutines
    addDualDependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    // https://github.com/PaperMC/Paper
    addDualDependency("io.papermc.paper:paper-api:1.21.6-R0.1-SNAPSHOT")
}

paper {
    main = "dev.echonine.kite.Kite"
    loader = "dev.echonine.kite.PluginLibrariesLoader"
    apiVersion = "1.21.1"
    foliaSupported = true
    hasOpenClassloader = true
    generateLibrariesJson = true
    description = "A lightweight Kotlin scripting plugin"
    website = "https://docs.echonine.dev/kite/"
    authors = listOf("Saturn745", "Grabsky")
}

runPaper.folia.registerTask()

tasks {
    withType<RunServer> {
        minecraftVersion("1.21.8")
        downloadPlugins {
            // Downloading ViaVersion and ViaBackwards for testing on lower versions.
            modrinth("viaversion", "5.5.1")
            modrinth("viabackwards", "5.5.1")
            // Downloading MiniPlaceholders 3.1.0. ID must be used because same version number is used for multiple platforms.
            modrinth("miniplaceholders", "4zOT6txC")
            // Downloading PlaceholderAPI development build (yet to be released) with Folia support.
            url("https://ci.extendedclip.com/job/PlaceholderAPI/212/artifact/build/libs/PlaceholderAPI-2.11.7-DEV-212.jar")
        }
    }
    generatePaperPluginDescription {
        // Downloading libraries directly from Maven Central may be considered as violation of their Terms of Service.
        useGoogleMavenCentralProxy()
    }
    withType(KotlinCompile::class) {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
            // https://kotlinlang.org/docs/context-parameters.html
            freeCompilerArgs = listOf("-Xcontext-parameters")
        }
    }
    // Configuring 'runServer' task to use JetBrains' JDK 21 for expanded hot-swap features.
    withType(AbstractRun::class) {
        javaLauncher = project.javaToolchains.launcherFor {
            vendor = JvmVendorSpec.of("JetBrains")
            languageVersion = JavaLanguageVersion.of(21)
        }
        jvmArgs("-XX:+AllowEnhancedClassRedefinition", "-Dcom.mojang.eula.agree=true", "-Dnet.kyori.ansi.colorLevel=truecolor")
    }
}

publishing.publications {
    create("maven", MavenPublication::class) {
        from(components["kotlin"])
    }
}

// Returns formatted release name.
tasks.register("getRelease", {
    print(VERSION)
})

// Returns formatted tag name.
tasks.register("getTag", {
    print("${VERSION}+${RUN_NUMBER}")
})

// Adds specified dependency to 'paperLibrary' and 'api' configurations.
// This makes it easier for IDEA to resolve dependencies when working with .kite.kts scripts.
fun addDualDependency(dependencyNotation: String) {
    dependencies.add("paperLibrary", dependencyNotation)
    dependencies.add("api", dependencyNotation)
}