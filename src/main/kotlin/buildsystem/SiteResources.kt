package buildsystem

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.*

@Serializable
private data class FileState(val size: Long, val modified: String)
@Serializable
private data class AtomicRecord(val input: String, val files: Map<String, FileState>,
    val sources: Map<String, FileState> = emptyMap(), val fragments: Map<String, String> = emptyMap())
@Serializable
private data class AtomicCache(val version: Int = 1, val resources: Map<String, AtomicRecord> = emptyMap())

/** Files are public outputs; fragments are private cached values, such as RSS items. */
data class ResourceOutput(val files: Map<String, ByteArray> = emptyMap(),
    val fragments: Map<String, String> = emptyMap(), val sources: Set<String> = emptySet())

/** One owner for output safety, atomic resource reuse, full writes and obsolete-file removal. */
class SiteResources(outputPath: Path, sourcePath: Path) {
    private val root = outputPath.toAbsolutePath().normalize()
    private val sourceRoot = sourcePath.toAbsolutePath().normalize()
    private val manifest = checkedOutput(".generated-site-files.json")
    private val cacheFile = checkedOutput(".incremental-site.json")
    private val previous = if (manifest.exists()) Json.decodeFromString<List<String>>(manifest.readText()) else emptyList()
    private val old: Map<String, AtomicRecord>
    private val current = linkedMapOf<String, AtomicRecord>()
    private val planned = linkedSetOf<String>()
    private val folded = mutableSetOf<String>()
    private val spellings = mutableMapOf<String, String>()
    private val pending = linkedMapOf<String, ByteArray>()
    private var reused = 0
    private var built = 0

    init {
        previous.forEach(::checkedOutput)
        val cache = if (cacheFile.exists()) runCatching { Json.decodeFromString<AtomicCache>(cacheFile.readText()) }
            .getOrElse { println("Ignoring unreadable incremental cache: $cacheFile"); AtomicCache() } else AtomicCache()
        old = if (cache.version == 1) cache.resources else emptyMap()
        for (record in old.values) {
            record.files.keys.forEach(::checkedOutput)
            record.sources.keys.forEach(::source)
        }
    }

    fun put(path: String, bytes: ByteArray) {
        reserve(path)
        pending[path] = bytes
    }

    private fun canonicalizeOldCase() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        root.createDirectories()
        val planned = current.values.flatMap { it.files.keys } + this.planned
        for (relative in planned) {
            val parts = relative.split('/')
            var parent = root
            for ((index, part) in parts.withIndex()) {
                val expected = parent.resolve(part)
                val actual = parent.listDirectoryEntries().firstOrNull {
                    it.name.equals(part, ignoreCase = true) && Files.isSameFile(it, expected)
                } ?: break
                if (actual.name != part) {
                    val temporary = Files.createTempFile(parent, ".site-case-", ".tmp")
                    temporary.deleteExisting()
                    Files.move(actual, temporary)
                    Files.move(temporary, expected)
                }
                parent = expected
            }
        }
    }

    /** All declared outputs must survive unchanged for an atom to hit. */
    fun atomic(id: String, input: String, paths: Set<String> = emptySet(), fragmentKeys: Set<String> = emptySet(),
        immediate: Boolean = false, restoreSources: (Set<String>) -> Unit = {},
        generate: () -> ResourceOutput): Map<String, String> {
        current[id]?.let {
            require(it.input == input && it.files.keys == paths && it.fragments.keys == fragmentKeys) { "Conflicting atomic resource: $id" }
            return it.fragments
        }
        paths.forEach(::reserve)
        val cached = old[id]?.takeIf { record ->
            record.input == input && record.files.keys == paths && record.fragments.keys == fragmentKeys &&
                record.files.all { (path, stamp) -> stateOrNull(checkedOutput(path)) == stamp } &&
                record.sources.all { (path, stamp) -> stateOrNull(source(path)) == stamp }
        }
        if (cached != null) {
            restoreSources(cached.sources.keys)
            current[id] = cached
            reused++
            return cached.fragments
        }
        val result = generate()
        require(result.files.keys == paths && result.fragments.keys == fragmentKeys) { "Unexpected outputs for atomic resource: $id" }
        for ((path, bytes) in result.files) {
            if (immediate) writeOutput(path, bytes)
            else pending[path] = bytes
        }
        current[id] = AtomicRecord(input, paths.associateWith { FileState(0, "") },
            result.sources.associateWith { state(source(it)) }, result.fragments)
        built++
        return result.fragments
    }

    fun source(relative: String): Path = checkedPath(sourceRoot, relative, "content source")
    fun sourceSignature(relative: String): String = state(source(relative)).toString()

    /** Page generation must finish successfully before replacing HTML or deleting obsolete files. */
    fun finish() {
        canonicalizeOldCase()
        for ((relative, bytes) in pending) writeOutput(relative, bytes)
        val replacements = planned.associateBy { it.lowercase() }
        for (obsolete in previous.toSet() - planned) {
            val oldPath = checkedOutput(obsolete)
            val replacement = replacements[obsolete.lowercase()]?.let(::checkedOutput)
            // On case-insensitive filesystems a case-only rename is the new file,
            // not an obsolete file to delete. Linux still removes the distinct old file.
            if (replacement != null && oldPath.exists() && replacement.exists() && Files.isSameFile(oldPath, replacement)) continue
            oldPath.deleteIfExists()
        }
        val records = current.mapValues { (_, record) ->
            record.copy(files = record.files.keys.associateWith { state(checkedOutput(it)) })
        }
        replaceState(cacheFile, Json.encodeToString(AtomicCache(resources = records)))
        replaceState(manifest, Json.encodeToString(planned.toList()))
        println("Incremental resources: $reused reused, $built built; ${planned.size} output files")
    }

    private fun reserve(relative: String) {
        checkedOutput(relative)
        require(relative !in setOf(".generated-site-files.json", ".incremental-site.json")) { "Reserved output: $relative" }
        require(folded.add(relative.lowercase())) { "Duplicate or case-colliding generated output: $relative" }
        var prefix = ""
        for (part in relative.split('/')) {
            prefix = if (prefix.isEmpty()) part else "$prefix/$part"
            val previousSpelling = spellings.putIfAbsent(prefix.lowercase(), prefix)
            require(previousSpelling == null || previousSpelling == prefix) { "Case-colliding output paths: $previousSpelling and $prefix" }
        }
        planned += relative
    }

    private fun checkedOutput(relative: String) = checkedPath(root, relative, "output")

    private fun writeOutput(relative: String, bytes: ByteArray) {
        val target = checkedOutput(relative)
        root.createDirectories()
        var parent = root
        val parts = relative.split('/')
        for ((index, part) in parts.withIndex()) {
            val expected = parent.resolve(part)
            val present = expected.exists() || (System.getProperty("os.name").startsWith("Windows") && parent.isDirectory() && parent.listDirectoryEntries().any {
                it.name.equals(part, ignoreCase = true) && Files.isSameFile(it, expected)
            })
            if (present) {
                // Windows otherwise preserves the old spelling after a case-only
                // source rename, which breaks the exact-case deployment contract.
                val actual = parent.listDirectoryEntries().firstOrNull {
                    it.name.equals(part, ignoreCase = true) && Files.isSameFile(it, expected)
                }
                if (actual != null && actual.name != part) {
                    val temporary = Files.createTempFile(parent, ".site-case-", ".tmp")
                    temporary.deleteExisting()
                    Files.move(actual, temporary)
                    try { Files.move(temporary, expected) }
                    catch (error: Exception) { Files.move(temporary, actual); throw error }
                }
                if (System.getProperty("os.name").startsWith("Windows")) {
                    val current = parent.listDirectoryEntries().firstOrNull {
                        it.name.equals(part, ignoreCase = true) && Files.isSameFile(it, expected)
                    }
                    if (current != null) parent = expected
                }
            } else {
                // Do not reuse a differently cased directory left by an old build.
                val obsolete = parent.listDirectoryEntries().filter { it.name.equals(part, ignoreCase = true) && it.name != part }
                if (obsolete.isNotEmpty()) {
                    require(index < parts.lastIndex) { "Cannot replace differently cased output file: $expected" }
                    val lower = prefixLower(parts, index)
                    require(previous.none { it.lowercase() == lower }) { "Differently cased output is not managed: $expected" }
                    for (old in obsolete) {
                        val temporary = Files.createTempDirectory(parent, ".site-case-dir-")
                        Files.move(old, temporary.resolve(old.name))
                        try { old.toFile().deleteRecursively() }
                        catch (error: Exception) {
                            temporary.resolve(old.name).moveTo(old)
                            throw error
                        }
                        temporary.deleteIfExists()
                    }
                }
                if (index < parts.lastIndex) expected.createDirectory()
            }
            parent = expected
            if (index < parts.lastIndex) {
                // A just-created case-insensitive directory may still resolve
                // through an old spelling; subsequent checks must see the new path.
                val oldCase = parent.parent.listDirectoryEntries().firstOrNull {
                    it.name.equals(part, ignoreCase = true) && Files.isSameFile(it, parent) && it.name != part
                }
                if (oldCase != null) {
                    val temporary = Files.createTempFile(parent.parent, ".site-case-", ".tmp")
                    temporary.deleteExisting()
                    Files.move(oldCase, temporary)
                    Files.move(temporary, parent)
                }
            }
        }
        target.writeBytes(bytes)
    }

    private fun prefixLower(parts: List<String>, index: Int): String =
        parts.take(index + 1).joinToString("/").lowercase()

    private fun replaceState(target: Path, text: String) {
        root.createDirectories()
        val temp = Files.createTempFile(root, ".site-state-", ".tmp")
        try {
            temp.writeText(text)
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        } finally { temp.deleteIfExists() }
    }
}

private fun checkedPath(root: Path, relative: String, label: String): Path {
    require(relative.isNotBlank() && !relative.contains('\\') && !relative.contains(':') &&
        relative.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "Unsafe $label: $relative" }
    val path = root.resolve(relative).normalize()
    require(path.startsWith(root) && path != root) { "Escaping $label: $relative" }
    var ancestor: Path? = path
    while (ancestor != null && ancestor.startsWith(root)) {
        require(!ancestor.isSymbolicLink()) { "Symlink in $label: $ancestor" }
        ancestor = ancestor.parent
    }
    return path
}

private fun state(path: Path): FileState {
    require(path.isRegularFile()) { "Missing resource: $path" }
    return FileState(Files.size(path), path.getLastModifiedTime().toString())
}
private fun stateOrNull(path: Path): FileState? = if (path.isRegularFile()) state(path) else null

/** Length framing prevents ambiguous concatenation of independently meaningful inputs. */
fun fingerprint(vararg parts: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    for (part in parts) {
        val bytes = part.toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
        digest.update(0.toByte())
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

fun directoryFingerprint(root: Path, extension: String): String {
    if (!root.isDirectory()) return "missing:$root"
    val parts = Files.walk(root).use { stream -> stream.filter { it.isRegularFile() && it.extension == extension }
        .sorted().toList().flatMap { listOf(root.relativize(it).invariantSeparatorsPathString, fingerprint(it.readText())) } }
    return fingerprint(*parts.toTypedArray())
}

/** Compiled project code invalidates templates/encoders automatically, including packaged runs. */
fun implementationFingerprint(vararg anchors: Class<*>): String {
    val parts = anchors.mapNotNull { it.protectionDomain?.codeSource?.location?.toURI()?.let(Path::of) }.distinct()
        .flatMap { location ->
            if (location.isDirectory()) Files.walk(location).use { stream ->
                stream.filter { it.isRegularFile() }.sorted().toList().map { file ->
                    location.relativize(file).invariantSeparatorsPathString + ":" +
                        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                }
            } else listOf(MessageDigest.getInstance("SHA-256").digest(location.readBytes()).joinToString("") { "%02x".format(it) })
        }
    // Dependency versions are encoded in the runtime classpath.
    return fingerprint(System.getProperty("java.class.path"), System.getProperty("java.runtime.version"),
        System.getProperty("java.vendor"), *parts.toTypedArray())
}
