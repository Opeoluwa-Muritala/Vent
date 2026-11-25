pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "server"
include(":shared-domain", ":shared-api")
project(":shared-domain").projectDir = file("../shared-domain")
project(":shared-api").projectDir = file("../shared-api")
