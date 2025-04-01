/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
    id("com.gradleup.shadow") version "9.0.0-beta6"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
    id("io.gitlab.arturbosch.detekt") version("1.23.7")
    id("app.cash.sqldelight") version "2.0.2"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.wire.integrations"
version = "0.0.1-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

val ktorVersion = "3.1.2"
val wireMockVersion = "3.12.0"

dependencies {
    constraints {
        api("commons-io:commons-io:2.18.0")
    }

    implementation(platform("io.insert-koin:koin-bom:4.0.3"))
    implementation("io.insert-koin:koin-core")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-logging:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-client-websockets:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("com.wire:core-crypto-jvm:4.1.0")
    implementation("com.wire:core-crypto-uniffi-jvm:4.1.0")
    implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
    implementation("app.cash.sqldelight:sqlite-3-24-dialect:2.0.2")
    implementation("org.zalando:logbook-core:3.11.0")
    implementation("org.zalando:logbook-ktor-client:3.11.0")
    implementation("org.zalando:logbook-json:3.11.0")
    implementation("com.google.protobuf:protobuf-kotlin:4.30.0")
    implementation("com.google.protobuf:protobuf-gradle-plugin:0.9.4")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.insert-koin:koin-test-junit5")
    testImplementation("org.wiremock:wiremock:$wireMockVersion")
    testImplementation("org.mockito:mockito-core:5.16.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

sqldelight {
    databases {
        create("AppsSdkDatabase") {
            packageName.set("com.wire.integrations.jvm")
            dialect("app.cash.sqldelight:sqlite-3-24-dialect:2.0.2")
        }
    }
}

ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    coloredOutput.set(true)
    reporters {
        reporter(ReporterType.CHECKSTYLE)
        reporter(ReporterType.JSON)
        reporter(ReporterType.HTML)
    }
    filter {
        exclude { element ->
            element.file.path.contains("generated/")
        }
    }
}

detekt {
    toolVersion = "1.23.7"
    config.setFrom(file("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    parallel = true
    buildUponDefaultConfig = true
    source.setFrom("src/main/kotlin")
}

protobuf {
    generatedFilesBaseDir = "$projectDir/generated"
    protoc {
        artifact = "com.google.protobuf:protoc:3.24.0"
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        mergeServiceFiles()
        archiveBaseName = "wire-jvm-sdk"
    }
    build {
        dependsOn(shadowJar)
    }
}
