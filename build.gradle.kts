plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val kotlinxHtmlVersion = "0.12.0"
val notionSdkVersion = "1.11.1"
val mordantVersion = "2.1.0"
val kotlinCoroutinesVersion = "1.7.3"
val markdownVersion = "0.7.3"

dependencies {
    testImplementation(kotlin("test"))

    // include for JVM target
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:${kotlinxHtmlVersion}")
    // no use implementation("com.github.seratch:notion-sdk-jvm-core:${notionSdkVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
    implementation("com.github.ajalt.mordant:mordant:${mordantVersion}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")
    implementation("net.coobird:thumbnailator:0.4.20")
    implementation("org.jetbrains:markdown:$markdownVersion")
    implementation("com.akuleshov7:ktoml-core:0.7.1")
    implementation("com.akuleshov7:ktoml-file:0.7.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("MainKt")
}