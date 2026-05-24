package htmlgen.page.home.subpage

import htmlgen.component.blogPostPreview
import htmlgen.component.largeBlogPostPreview
import kotlinx.html.*
import BlogRecord
import org.intellij.markdown.lexer.push
import java.util.TreeMap

class BlogsHomeSubPage(
    // Sorted by date and first is latest
    blogs: Iterable<BlogRecord>,
) : HomeSubPage() {

    val map: TreeMap<String, ArrayList<BlogRecord>> = TreeMap();
    val latestBlog: BlogRecord? = blogs.firstOrNull()

    init {
        for (record in blogs) {
            map.getOrPut(record.header.blogClass, { arrayListOf<BlogRecord>() } ).push(record)
        }
    }

    override fun DIV.showSubPage() {
        div {
            classes += "top_gap_space"
        }
        div {
            classes += "post_previews_wrapper"

            val typeOptions = map.keys.toTypedArray()

            if (latestBlog != null) {
                div {
                    p {
                        +"最近更新"
                    }
                    largeBlogPostPreview(latestBlog)
                }
            }

            div {
                p {
                    +"分类"
                }
                div {
                    classes += "post_type_buttons"

                    for (i in typeOptions.indices) {
                        div {
                            val option = typeOptions[i]
                            classes += "post_type_button"
                            id = "${option.lowercase()}_type_button"
                            if (i == 0) {
                                classes += "is_selected"
                            }
                            h3 {
                                classes += "type_name"
                                +option.uppercase()
                            }
                        }
                    }
                }
                for (i in typeOptions.indices) {
                    val option = typeOptions[i]
                    div {
                        if (i != 0) {
                            classes += "hidden"
                        }
                        classes += "post_type_previews"
                        id = "${option.lowercase()}_type_previews"
                        for (page in map.get(option)!!) {
                            if (page.header.published) blogPostPreview(page)
                        }
                    }
                }
            }
        }
    }

    override fun getCssNames(): Array<String> {
        return arrayOf("subpage/blogs", "post_preview", "scroll_animation")
    }

    override fun getJsNames(): Array<String> {
        return arrayOf("scroll_animation")
    }

}