pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            name = "NeoForge"
            url = uri("https://maven.neoforged.net/releases")
        }
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        maven {
            name = "Sponge Snapshots"
            url = uri("https://repo.spongepowered.org/repository/maven-public/")
        }
    }
}

rootProject.name = "AsyncLocator"
include("Common", "Fabric", "NeoForge")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
