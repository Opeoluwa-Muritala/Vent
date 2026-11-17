@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ksp)
}
kotlin {
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    iosArm64(); iosSimulatorArm64()
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework { baseName = "VentKit"; isStatic = true }
    }
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "vent.js" } }
        binaries.executable()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared-domain"))
            implementation(project(":shared-api"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.components.resources)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.engine.defaults)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.navigation3.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.12.0")
            implementation(libs.androidx.room.runtime)
        }
        wasmJsMain.dependencies {
            implementation(libs.navigation3.browser)
        }
    }
}
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
}
android {
    namespace = "app.vent"
    compileSdk = 37
    defaultConfig { applicationId = "app.vent"; minSdk = 34; targetSdk = 37; versionCode = 1; versionName = "1.0.0-demo" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    buildFeatures { compose = true }
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
}

val detektDesign = tasks.register("detektDesign") {
    group = "verification"
    description = "Checks Vent UI source for design-system violations."
    doLast {
        val themeSegment = "ui${File.separator}theme"
        val sources = fileTree("src") { include("**/*.kt") }
        val rules = mapOf(
            "hardcoded dp" to Regex("""\b\d+\.dp\b"""),
            "hardcoded sp" to Regex("""\b\d+\.sp\b"""),
            "hardcoded color" to Regex("""Color\(0x[0-9A-Fa-f]+\)"""),
            "filler copy" to Regex("""(?i)(Lorem ipsum|Lightning fast|Powered by AI|Get started in seconds|Beautiful and intuitive)"""),
            "decorative loop" to Regex("""rememberInfiniteTransition"""),
        )
        val violations = buildList {
            sources.filter { !it.path.contains(themeSegment) }.forEach { source ->
                val text = source.readText()
                rules.forEach { (label, regex) ->
                    regex.findAll(text).forEach { match ->
                        val line = text.take(match.range.first).count { it == '\n' } + 1
                        add("${source.relativeTo(rootDir)}:$line [$label] ${match.value}")
                    }
                }
            }
        }
        check(violations.isEmpty()) { "Vent design rule violations:\n${violations.joinToString("\n")}" }
    }
}

tasks.matching { it.name == "check" }.configureEach { dependsOn(detektDesign) }
