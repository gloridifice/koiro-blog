plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    //Fill this in with the version of kotlinx in use in your project
    val kotlinxHtmlVersion = "0.9.1"
    val notionSdkVersion = "1.9.0"
    val mordantVersion = "2.1.0"

    // include for JVM target
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:${kotlinxHtmlVersion}")
    implementation("com.github.seratch:notion-sdk-jvm-core:${notionSdkVersion}")
    implementation("com.github.ajalt.mordant:mordant:${mordantVersion}")

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