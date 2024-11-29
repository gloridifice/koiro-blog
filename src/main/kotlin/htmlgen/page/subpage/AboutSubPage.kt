package htmlgen.page.subpage

import STATIC_PATH
import childPath
import htmlgen.*
import htmlgen.page.introduce
import kotlinx.html.*
import java.nio.file.Path

class AboutSubPage : SubPage() {
    override fun DIV.show() {
        div {
            classes += "top_gap_space"
        }
        div {
            classes += "about_page"
            div {
                // Introduce
                div {
                    classes += "introduce"
                    div {
                        classes += "avatar_wrapper"
                        img {
                            src = resourcesServerPath("Koiro_Arc.png".asLoc())
                        }
                    }
                    div {
                        classes += "description_wrapper"
                        p {
                            +introduce
                        }
                    }
                }

                // Friends
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
            }
        }
    }

    override fun getHtmlFilePath(): Path {
        return STATIC_PATH.childPath("about.html")
    }

    override fun displayName(): String {
        return "关于"
    }

    override fun getCssNames(): Array<String> {
        return arrayOf("about_page")
    }

    override fun getJsNames(): Array<String> {
        return super.getJsNames()
    }
}