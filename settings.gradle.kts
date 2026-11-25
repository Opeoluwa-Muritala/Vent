pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    // Kotlin/Wasm registers the official Node.js distribution as an Ivy
    // project repository for kotlinWasmNodeJsSetup.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "vent"
include(":shared-domain", ":shared-api", ":server", ":composeApp", ":androidApp")
project(":composeApp").projectDir = file("frontend/composeApp")
project(":androidApp").projectDir = file("frontend/androidApp")
