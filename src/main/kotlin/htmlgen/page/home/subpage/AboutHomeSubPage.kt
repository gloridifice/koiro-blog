package htmlgen.page.home.subpage

import htmlgen.component.message
import htmlgen.SvgIcons
import htmlgen.asLoc
import htmlgen.component.touchableAvatar
import htmlgen.friendLinkItems
import htmlgen.resourcesServerPath
import kotlinx.html.*

class AboutHomeSubPage(private val context: routing.PageContext) : HomeSubPage() {
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
                            message(context, "site.aboutIntroduction")
                        }
                    }
                }

                // Friends
                div {
                    classes += "friends"
                    div {
                        classes += "title"
                        SvgIcons.PARTNER_EXCHANGE.apply { showSvg() }
                        div {
                            message(context, "site.friends")
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
                                    SvgIcons.EXTERNAL_LINK.apply { showSvg() }
                                    div {
                                        +it.name
                                    }
                                }
                                div {
                                    classes += "desc"
                                    message(context, "friends.${it.id}")
                                }
                            }
                        }
                    }
                }
            }
            div {
                classes += "about_bottom_space"
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