plugins {
    kotlin("jvm")
    `java-library`
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

group = "com.wire.integrations"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("io.insert-koin:koin-bom:4.0.1"))
    implementation("io.insert-koin:koin-core")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.1")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
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
