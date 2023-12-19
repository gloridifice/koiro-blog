package htmlgen.page

import BlogContext
import BlogPost
import DevLogPost
import htmlgen.SVGIcons
import htmlgen.component.largePostPreview
import kotlinx.html.*
import htmlgen.component.blogPostPreview
import htmlgen.component.devLogPostPreview
import htmlgen.unsafeSVG
import java.text.SimpleDateFormat

fun HTML.home(context: BlogContext) {
    layout(
        siteTitle = "Koiro's Cat Café",
        cssNames = arrayOf("home", "blog_preview", "dev_log_post_preview"),
        headFont = "你好"
    ) {
        contents(context)
    }
}

val titleEN = "Koiro's Cat Café"
val titleCN = "宏楼的猫咖"
fun FlowContent.contents(context: BlogContext) {
    div {
        classes += "contents_wrapper"
        div {
            classes += "contents"

            introduce(context)
            recentPostPreview(context)
            postPreviews(context)
            devLogPreviews(context)
        }
    }
}

fun FlowContent.recentPostPreview(context: BlogContext){
    val post = context.latestPostPage;
    when(post){
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
                OutSidePageItem("Source", "https://github.com/gloridifice/kotlin-notion-blog", "猫咖主人博客生成器的仓库")
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
                div {
                    classes += "start"
                    unsafeSVG(SVGIcons.EXTERNAL_LINK)
                    div {
                        classes += "name"
                        +it.name
                    }
                }

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
            h3 {
                +"开发日志"
            }
        }
        div {
            classes += "dev_log_previews"
            val sort = context.devLogDataDatabase.publishedPages.map { it.devLogPost() }.sortedBy {
                -it.index
            }
            for (i in 0..<6){
                sort.getOrNull(i)?.let {
                    devLogPostPreview(it)
                }
            }
        }
    }
}
