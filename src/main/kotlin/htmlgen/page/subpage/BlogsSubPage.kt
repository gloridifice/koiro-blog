package htmlgen.page.subpage

import GlobalContext
import STATIC_PATH
import childPath
import htmlgen.component.blogPostPreview
import htmlgen.component.footer
import htmlgen.component.largePostPreview
import htmlgen.model.BlogPostPage
import htmlgen.toNormalString
import kotlinx.html.*
import java.nio.file.Path

class BlogsSubPage(val context: GlobalContext) : SubPage() {
    override fun DIV.show() {
        div {
            classes += "top_gap_space"
        }
        div {
            classes += "post_previews_wrapper"
            val typeOptions = context.blogDatabaseData.database.properties["Class"]!!.select!!.options!!

            div {
                p {
                    classes += "reveal"
                    +"最近更新"
                }
                largePostPreview(context.blogDatabaseData.publishedPages.first())
            }

            div {
                p {
                    classes += "reveal"
                    +"分类"
                }
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
                        for (page in context.blogDatabaseData.publishedPages.filter { it.type.name == name }) {
                            if (page.published) blogPostPreview(page)
                        }
                    }
                }
            }
        }
    }

    override fun getHtmlFilePath(): Path {
        return STATIC_PATH.childPath("blogs.html");
    }

    override fun displayName(): String {
        return "博客"
    }

    override fun getCssNames(): Array<String> {
        return arrayOf("blogs_page", "blog_preview", "scroll_animation")
    }

    override fun getJsNames(): Array<String> {
        return arrayOf("scroll_animation")
    }

}