# Dependencies and versions

## Version baseline

Verified on 2026-10-30: the latest stable release on the official GitHub repository was **0.12.0**, published on 2025-01-15. The versions below are a verified example baseline, not a requirement to upgrade every project.

The 0.12.0 build script uses Kotlin 2.0.21, and its JVM POM depends on `kotlin-stdlib:2.0.21`. README badges do not replace build metadata. These facts do not guarantee compatibility with every older compiler. Preserve existing version pins; for new dependencies, check Kotlin metadata and toolchain compatibility.

## Gradle Kotlin DSL

Insert these snippets into the existing build configuration, preserving its plugins and repositories.

A regular JVM module can explicitly select the JVM artifact:

```kotlin
repositories { mavenCentral() }
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0")
}
```

For Kotlin Multiplatform, use the root coordinates in the source set that needs shared HTML DSL. Gradle metadata selects the variant:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-html:0.12.0")
        }
    }
}
```

Modern Kotlin/JS projects can also resolve the JS variant through the root coordinates. Maven JVM projects use `org.jetbrains.kotlinx:kotlinx-html-jvm`. Do not copy Bintray/JCenter repositories, historical webjar/assembly artifacts, or legacy Kotlin/JS configuration from old documentation.

In Ktor projects, use `io.ktor:ktor-server-html-builder` at the project's existing Ktor version. It brings in kotlinx.html transitively. Inspect the resolved dependencies before deciding whether an explicit kotlinx.html dependency or constraint is needed; avoid unnecessary version overrides.

## Platform boundaries

- `createHTML` / `appendHTML` work with strings and the common `Appendable` interface, allowing shared rendering logic.
- Keep `java.io.Writer`, file access, and JVM DOM operations in JVM code.
- Kotlin/JS and WasmJS can build browser DOM. A browser environment provides `document`; Node.js does not automatically provide it.
- String generation on Native and WasmWasi does not imply browser DOM availability.
- Version 0.12.0 includes multiple Native and Wasm targets. Before adding a target, verify that its variant exists in published metadata. Multiplatform support does not imply that every possible target resolves.

Inspect tasks and dependencies with the project's wrapper, for example `./gradlew tasks` or, on JVM, `dependencyInsight --dependency kotlinx-html --configuration runtimeClasspath`. On Windows, use `gradlew.bat`. KMP configuration and compilation task names vary by target; inspect the project instead of assuming JVM names.

Sources: [0.12.0 release](https://github.com/Kotlin/kotlinx.html/releases/tag/0.12.0), [build configuration](https://github.com/Kotlin/kotlinx.html/blob/0.12.0/build.gradle.kts), [JVM POM](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-html-jvm/0.12.0/kotlinx-html-jvm-0.12.0.pom).
