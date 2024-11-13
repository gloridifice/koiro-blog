package htmlgen.page

import BlogContext
import BlogPost
import DevLogPost
import OUT_PUT_PATH
import childPath
import htmlgen.SVGIcons
import htmlgen.component.*
import kotlinx.html.*
import htmlgen.unsafeSVG
import isImage
import java.text.SimpleDateFormat
import kotlin.io.path.Path


val friendLinkItems = arrayOf(
    FriendLinkItem("拉斯普的月台", "https://blog.rasp505.top/", "海拉鲁驿站"),
    FriendLinkItem("dyron503's", "https://career.dyron503.com/", "你知道吗？我的 ID 中「503」的出处是……"),
    FriendLinkItem("北依的树洞", "https://hanahoshikawa092.netlify.app", "音乐是救世主"),
)

fun HTML.home(context: BlogContext) {
    layout(
        siteTitle = "Koiro's Cat Café",
        cssNames = arrayOf(
            "home",
            "blog_preview",
            "dev_log_post_preview",
            "album",
            "scroll_animation",
        ),
        jsNames = arrayOf("scroll_animation"),
        headFont = "你好",
    ) {
        div {
            classes += "about"
            img {
                src = "/assets/resources/about_icon.png"
            }
            outSidePages(
                arrayOf(
                    OutSidePageItem("itch.io", "https://gloridifice.itch.io/", "一些游戏开发作品"),
                    OutSidePageItem(
                        "Source",
                        "https://github.com/gloridifice/kotlin-notion-blog",
                        "博客仓库"
                    )
                )
            )

            div {
                classes += "introduction"
                div {
                    classes += "icon"
                    unsafeSVG(SVGIcons.ACCOUNT_CIRCLE);
                }
                div {
                    classes += "texts"
                    h2 {
                        +"这里是宏楼的猫咖！"
                    }
                    p {
                        +"人类、学生、平面设计爱好者、图形学爱好者和游戏开发者。"
                    }
                }

            }

            div {
                classes += "friends"
                div {
                    classes += "title"
                    unsafeSVG(SVGIcons.PARTNER_EXCHANGE);
                    div {
                        +"友情链接"
                    }
                }
                div {
                    classes += "list"
                    friendLinkItems.forEach {
                        div {
                            onClick = "window.open('${it.link}')"
                            classes += "friend_link"
                            div {
                                classes += "icon"
                                classes += "start"
                                unsafeSVG(SVGIcons.EXTERNAL_LINK)
                                div {
                                    +it.name
                                }
                            }
                            div {
                                classes += "desc"
                                +it.description
                            }
                        }
                    }
                }
            }

            div {
                classes += "navi"
            }

        }
        div {
            classes += "contents_wrapper"
            div {
                classes += "contents"

//                introduce(context)
                recentPostPreview(context)
                postPreviews(context)
                devLogPreviews(context)
                albumPart()
            }
        }
    }
}

val titleEN = "Koiro's Cat Café"
val titleCN = "宏楼的猫咖"

fun FlowContent.albumPart() {
    val albumItems = ArrayList<AlbumItem>()
    Path(OUT_PUT_PATH).childPath("album").toFile().walk().iterator()
        .asSequence().sortedBy { -it.lastModified() }.forEach {
            if (it.isFile && it.isImage()) {
                albumItems.add(AlbumItem("/album/" + it.name))
            }
        }
    div {
        classes += "album_part"
        h2 {
            classes += "reveal"
            +"📷 🏷️"
        }
        album(albumItems)
    }
}

fun FlowContent.recentPostPreview(context: BlogContext) {
    val post = context.latestPostPage;
    when (post) {
        is BlogPost -> largePostPreview(post)
        is DevLogPost -> devLogPostPreview(post)
    }
}

fun FlowContent.lastUpdateTime(context: BlogContext) {
    div {
        classes += "last_update_time"
        h4 {
            +"Latest Update"
        }
        p {
            +context.latestPostPage.getLastEditedTimeDay()
        }
    }
}

fun FlowContent.introduce(context: BlogContext) {
    div {
        classes += "introduce"

        div {
            classes += "title_icon"
            unsafeSVG(
                SVGIcons.CAT_WALK
            )
        }
        h1 {
            +titleEN
        }
        h2 {
            +titleCN
        }
        div {
            classes += "subIntroduce"
            +"val cats = listOf<Cat>("
            i { +"TODO(\"Recruiting\")" }
            +")"
        }
        outSidePages(
            arrayOf(
                OutSidePageItem("itch.io", "https://gloridifice.itch.io/", "一些游戏开发作品"),
                OutSidePageItem(
                    "Source",
                    "https://github.com/gloridifice/kotlin-notion-blog",
                    "博客仓库"
                )
            )
        )

        lastUpdateTime(context)
    }
}

data class OutSidePageItem(val name: String, val link: String, val desc: String)

fun FlowContent.outSidePages(items: Array<OutSidePageItem>) {
    div {
        classes += "outside_pages"
        items.forEach {
            div {
                onClick = "window.open('${it.link}')"
                classes += "outside_page_item"

                unsafeSVG(SVGIcons.EXTERNAL_LINK)

                div {
                    classes += "desc"
                    +it.desc
                }
            }
        }
    }

}

fun FlowContent.postPreviews(context: BlogContext) {
    div {
        classes += "post_previews_wrapper"
        val typeOptions = context.blogDataDatabase.database.properties["Class"]!!.select!!.options!!
        div {
            classes += "post_type_buttons"
            classes += "reveal"

            for (i in typeOptions.indices) {
                div {
                    val option = typeOptions[i]
                    val name = option.name!!
                    classes += "post_type_button"
                    id = "${name.lowercase()}_type_button"
                    if (i == 0) {
                        classes += "is_selected"
                    }
                    h3 {
                        classes += "type_name"
                        +name.uppercase()
                    }
                }
            }
        }
        for (i in typeOptions.indices) {
            val option = typeOptions[i]
            val name = option.name!!
            div {
                if (i != 0) {
                    classes += "hidden"
                }
                classes += "post_type_previews"
                id = "${name.lowercase()}_type_previews"
                for (page in context.blogDataDatabase.publishedPages.filter { it.blogPost().type.name == name }) {
                    val post = page.blogPost()
                    if (post.published) blogPostPreview(post)
                }
            }
        }
    }
}

fun FlowContent.devLogPreviews(context: BlogContext) {
    div {
        classes += "dev_log_previews_wrapper"
        div {
            classes += "reveal"
            h3 {
                +"开发日志"
            }
        }
        div {
            classes += "dev_log_previews"
            val sort = context.devLogDataDatabase.publishedPages.map { it.devLogPost() }.sortedBy {
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                fmt.parse(it.page.createdTime)
            }.reversed()
            for (i in 0..<6) {
                sort.getOrNull(i)?.let {
                    devLogPostPreview(it)
                }
            }
        }
    }
}
