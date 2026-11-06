import kotlin.io.path.Path

fun main(args: Array<String>) {
    val release = "--release" in args
    val directories = args.filter { it != "--release" }
    require(directories.size <= 3 && directories.none { it.startsWith("--") }) {
        "Usage: generator [--release] [data-directory] [output-directory] [i18n-directory]"
    }
    KoiroCatCafe(Path(directories.getOrElse(0) { "data" }), directories.getOrElse(1) { "dist" },
        i18nPath = Path(directories.getOrElse(2) { "i18n" }), release = release).generateAll()
}
