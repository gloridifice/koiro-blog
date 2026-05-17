package markdown.htmlgen.page.home.subpage

import kotlinx.html.*
import markdown.htmlgen.INTRODUCE
import markdown.htmlgen.SvgIcons
import markdown.htmlgen.asLoc
import markdown.htmlgen.component.touchableAvatar
import markdown.htmlgen.friendLinkItems
import markdown.htmlgen.resourcesServerPath

class AboutHomeSubPage : HomeSubPage() {
    override fun DIV.showSubPage() {
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
                        touchableAvatar(resourcesServerPath("doiro.png".asLoc()))
                    }
                    div {
                        classes += "description_wrapper"
                        p {
                            +INTRODUCE
                        }
                    }
                }

                // Friends
                div {
                    classes += "friends"
                    div {
                        classes += "title"
                        SvgIcons.PARTNER_EXCHANGE.apply { showSubPage() }
                        div {
                            +"友情链接"
                        }
                    }
                    div {
                        classes += "list"
                        for (it in friendLinkItems) {
                            a {
                                href = it.link
                                target = "_blank"
                                classes += arrayOf("button", "friend_link")

                                div {
                                    classes += "icon"
                                    classes += "start"
                                    SvgIcons.EXTERNAL_LINK.apply { showSubPage() }
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

    override fun getCssNames(): Array<String> {
        return arrayOf("subpage/about")
    }

    override fun getJsNames(): Array<String> {
        return super.getJsNames()
    }
}