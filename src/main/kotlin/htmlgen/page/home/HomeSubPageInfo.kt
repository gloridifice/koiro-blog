package htmlgen.page.home

import ServerPath
import htmlgen.SvgIcon
import htmlgen.SvgIcons
import routing.PageContext
import routing.PageKey

data class HomeSubPageInfo(val serverPath: ServerPath, val name: String, val svgIcon: SvgIcon, val key: PageKey, val language: String)
fun PageContext.navigation(): List<HomeSubPageInfo> = listOf(
    Triple(PageKey.Home(), "nav.home", SvgIcons.HOME), Triple(PageKey.Blogs, "nav.blogs", SvgIcons.BLOGS),
    Triple(PageKey.Portfolio, "nav.portfolio", SvgIcons.PROJECTS), Triple(PageKey.About, "nav.about", SvgIcons.DOG_BARK)
).map { (key, label, icon) -> HomeSubPageInfo(routes.page(locale, key), text(label), icon, key, message(label).actualLocale.tag) }
