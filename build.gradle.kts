plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val kotlinxHtmlVersion = "0.9.1"
val notionSdkVersion = "1.9.0"
val mordantVersion = "2.1.0"
val kotlinCoroutinesVersion = "1.7.3"

dependencies {
    testImplementation(kotlin("test"))

    // include for JVM target
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:${kotlinxHtmlVersion}")
    implementation("com.github.seratch:notion-sdk-jvm-core:${notionSdkVersion}")
    implementation("com.github.ajalt.mordant:mordant:${mordantVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")

}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("MainKt")
}