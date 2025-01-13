plugins {
    kotlin("jvm")
    application
}

group = "com.wire.integrations"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":lib"))
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

application {
    mainClass.set("com.wire.integrations.sample.MainKt")
}
