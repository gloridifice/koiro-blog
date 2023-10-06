package htmlgen.page

import BlogContext
import htmlgen.SVGIcons
import kotlinx.html.*
import htmlgen.component.postPreview
import htmlgen.unsafeSVG

fun HTML.home(context: BlogContext) {
    layout(
        siteTitle = "Koiro's Cat Café",
        cssNames = arrayOf("home", "blog_preview")
    ) {
        contents(context)
    }
}

val titleEN = "Koiro's Cat Café"
val titleCN = "宏楼的猫咖"
val introduceDescription = "人类、学生、游戏开发者、平面设计笨蛋和技术美术笨蛋"
fun FlowContent.contents(context: BlogContext) {
    div {
        classes += "contents_wrapper"
        div {
            classes += "contents"

            introduce()
            lastUpdateTime(context)
            postPreviews(context)
        }
    }
}

fun FlowContent.lastUpdateTime(context: BlogContext) {
    div {
        classes += "last_update_time"
        h4 {
            +"Latest Update"
        }
        p {
            +context.dataDatabase.dataPages.first().page.lastEditedTime.split('T').first()
        }
    }
}

fun FlowContent.introduce() {
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
        h3 {
            +introduceDescription
        }
        div {
            classes += "subIntroduce"
            +"val cats = listOf<Cat>("
            i { +"TODO(\"Recruiting\")" }
            +")"
        }
    }
}

fun FlowContent.postPreviews(context: BlogContext) {
    div {
        classes += "post_previews_wrapper"
        val typeOptions = context.dataDatabase.database.properties["Class"]!!.select!!.options!!
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
                for (page in context.dataDatabase.dataPages.filter { it.post.type.name == name }) {
                    if (page.post.published) postPreview(page.post)
                }
            }
        }
    }
}

