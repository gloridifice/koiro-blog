package htmlgen.page.home.subpage

import htmlgen.component.message
import htmlgen.SvgIcons
import htmlgen.asLoc
import htmlgen.component.touchableAvatar
import content.ResolvedFriendLink
import htmlgen.resourcesServerPath
import kotlinx.html.*

class AboutHomeSubPage(private val context: routing.PageContext,
    private val friends: List<ResolvedFriendLink>) : HomeSubPage() {
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
                if (friends.isNotEmpty()) div {
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
                        for (friend in friends) {
                            a {
                                href = friend.url
                                target = "_blank"
                                rel = "noopener noreferrer"
                                classes += arrayOf("button", "friend_link")

                                div {
                                    classes += "icon"
                                    classes += "start"
                                    SvgIcons.EXTERNAL_LINK.apply { showSvg() }
                                    div {
                                        lang = friend.nameLocale?.tag.orEmpty()
                                        +friend.name
                                    }
                                }
                                div {
                                    classes += "desc"
                                    lang = friend.actualLocale.tag
                                    +friend.description
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