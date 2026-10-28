import kotlin.io.path.Path

fun main() {
    KoiroCatCafe(Path("data"), "./static").generateAll()
}