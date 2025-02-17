import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    application
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    id("io.ktor.plugin") version "3.0.3"
    id("app.cash.sqldelight") version "2.0.2"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
    id("com.gradleup.shadow") version "8.3.6"
}

sqldelight {
    databases {
        create("PayloadDatabase") {
            deriveSchemaFromMigrations.set(true)
            migrationOutputDirectory = file("${layout.buildDirectory.get()}/generated/migrations")
            migrationOutputFileFormat = ".sql"
            packageName.set("no.nav.emottak.queries")
            dialect(libs.sqldelight.postgresql.dialect)
        }
    }
}

tasks {
    shadowJar {
        archiveFileName.set("app.jar")
    }
    test {
        useJUnitPlatform()
    }
    ktlintFormat {
        enabled = true
    }
    ktlintCheck {
        dependsOn("ktlintFormat")
    }
    ktlint {
        filter {
            exclude { it.file.path.contains("/generated/") }
            exclude { it.file.path.contains("\\generated\\") }
        }
    }
    compileKotlin.configure {
        // sqldelight task generateMainPayloadDatabaseMigrations will output .sqm files as valid SQL
        // in the output directory, with the output format.
        // Create a dependency from compileKotlin where flyway will have the files available on the classpath
        dependsOn("generateMainPayloadDatabaseMigrations")
    }
    build {
        dependsOn("ktlintCheck")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs = listOf("-opt-in=kotlin.uuid.ExperimentalUuidApi,arrow.fx.coroutines.await.ExperimentalAwaitAllApi")
    }
}

dependencies {
    implementation(libs.arrow.core)
    implementation(libs.arrow.functions)
    implementation(libs.arrow.fx.coroutines)
    implementation(libs.arrow.resilience)
    implementation(libs.arrow.suspendapp)
    implementation(libs.arrow.suspendapp.ktor)
    implementation(libs.bundles.jakarta.mail)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.prometheus)
    implementation(libs.flyway.core)
    implementation(libs.hikari)
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.hocon)
    implementation(libs.kotlin.kafka)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.auth.jvm)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.postgresql)
    implementation(libs.sqldelight.jdbc.driver)
    implementation(libs.token.validation.ktor.v3)
    implementation(libs.vault.jdbc)
    testImplementation(testLibs.bundles.greenmail)
    testImplementation(testLibs.bundles.kotest)
    testImplementation(testLibs.kotest.assertions.arrow)
    testImplementation(testLibs.kotest.extensions.testcontainers)
    testImplementation(testLibs.kotest.extensions.testcontainers.kafka)
    testImplementation(testLibs.ktor.server.test.host)
    testImplementation(testLibs.ktor.client.mock)
    testImplementation(testLibs.mock.oauth2.server)
    testImplementation(testLibs.postgresql)
    testImplementation(testLibs.testcontainers)
    testImplementation(testLibs.testcontainers.kafka)
    testImplementation(testLibs.testcontainers.postgresql)
    testImplementation(testLibs.turbine)
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("no.nav.emottak.AppKt")
}
