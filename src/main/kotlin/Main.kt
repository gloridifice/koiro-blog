import kotlin.io.path.Path

fun main(args: Array<String>) {
    require(args.size <= 3) { "Usage: generator [data-directory] [output-directory] [i18n-directory]" }
    KoiroCatCafe(Path(args.getOrElse(0) { "data" }), args.getOrElse(1) { "dist" },
        i18nPath = Path(args.getOrElse(2) { "i18n" })).generateAll()
}
