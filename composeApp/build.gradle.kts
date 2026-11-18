@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ksp)
}
kotlin {
    android {
        namespace = "app.vent.shared"
        androidResources.enable = true
        compileSdk = 37
        minSdk = 34
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
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
            implementation(libs.compose.ui.tooling.preview)
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
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.view)
            implementation(libs.androidx.camera.mlkit.vision)
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.androidx.activity.compose)
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
