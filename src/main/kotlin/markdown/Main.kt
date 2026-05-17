package markdown

import kotlin.io.path.Path

fun main() {
    KoiroCatCafe(Path("/Users/yfan/Library/Mobile Documents/iCloud~md~obsidian/Documents/blog/blog"), "./static").generateAll()
}