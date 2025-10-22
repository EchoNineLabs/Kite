plugins {
    kotlin("jvm") version "2.2.0"
    id("de.eldoria.plugin-yml.paper") version "0.8.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
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

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")

    // Global libraries
    library(kotlin("stdlib"))
    library("org.jetbrains.kotlin:kotlin-scripting-jvm")
    library("org.jetbrains.kotlin:kotlin-scripting-common")
    library("org.jetbrains.kotlin:kotlin-scripting-dependencies")
    library("org.jetbrains.kotlin:kotlin-scripting-dependencies-maven")
    library("org.jetbrains.kotlin:kotlin-scripting-jvm-host")
    library("org.jetbrains.kotlin:kotlin-script-runtime")
    library("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
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