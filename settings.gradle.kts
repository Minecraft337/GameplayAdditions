pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.neoforged.net/")
    }
}

plugins {
    id("net.neoforged.moddev") version "2.0.141" apply false
}

rootProject.name = "GameplayAdditions"
