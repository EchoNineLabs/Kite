import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.0"
    id("de.eldoria.plugin-yml.paper") version "0.8.0"
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.2.2"
    `maven-publish`
}

group = "dev.echonine.kite"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

fun dep(dependencyNotation: String) {
    dependencies.add("library", dependencyNotation)
    dependencies.add("api", dependencyNotation)
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")

    // Global libraries
    library(kotlin("stdlib"))
    dep("org.jetbrains.kotlin:kotlin-scripting-jvm")
    dep("org.jetbrains.kotlin:kotlin-scripting-common")
    dep("org.jetbrains.kotlin:kotlin-scripting-dependencies")
    dep("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven")
    dep("org.jetbrains.kotlin:kotlin-scripting-jvm-host")
    dep("org.jetbrains.kotlin:kotlin-script-runtime")
    dep("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

paper {
    description = "A lightweight Kotlin scripting plugin"
    website = "https://docs.echonine.dev/kite/"
    main = "dev.echonine.kite.Kite"

    loader = "dev.echonine.kite.PluginLibrariesLoader"
    hasOpenClassloader = true
    generateLibrariesJson = true

    foliaSupported = true

    apiVersion = "1.21"

    authors = listOf("Saturn745", "Grabsky")
}

tasks {
    runServer {
        minecraftVersion("1.21.8")
    }

}

kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()
        }
    }
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xcontext-parameters"))
}