pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("net.fabricmc.fabric-loom") version "1.19-SNAPSHOT" apply false
}

stonecutter {
    create(rootProject) {
        // Each version needs a matching section in stonecutter.properties.toml.
        versions("26.2", "26.3")
        vcsVersion = "26.3"
    }
}

rootProject.name = "regrowth-fabric-port"
