plugins {
    application
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}
kotlin { jvmToolchain(if (rootProject == project) JavaVersion.current().majorVersion.toInt() else 17) }
application { mainClass.set("app.vent.server.MainKt") }
dependencies {
    implementation(project(":shared-domain"))
    implementation(project(":shared-api"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.serialization.json)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)
    implementation(libs.logback)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.embedded.postgres)
}
tasks.test { useJUnitPlatform() }
tasks.register<JavaExec>("seed") {
    group = "application"
    description = "Idempotently seeds the Vent demo"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("app.vent.server.SeedKt")
}
