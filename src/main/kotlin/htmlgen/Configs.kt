package htmlgen

import htmlgen.component.NaviItem
import htmlgen.page.FriendLinkItem

val titleEN = "Koiro's Cat Café"
val titleCN = "宏楼的猫咖"

// Friend links
val friendLinkItems = arrayOf(
    FriendLinkItem("拉斯普的月台", "https://blog.rasp505.top/", "海拉鲁驿站"),
    FriendLinkItem("dyron503's", "https://career.dyron503.com/", "你知道吗？我的 ID 中「503」的出处是……"),
    FriendLinkItem("北依的树洞", "https://hanahoshikawa092.netlify.app", "音乐是救世主"),
)

// Navi
val HOME_NAVI_ITEM = NaviItem("主页", htmlServerPath("home".asLoc()), SVGIcons.HOME);
val NOTES_NAVI_ITEM = NaviItem(
    "笔记",
    "https://gloridifice.notion.site/3659ec2ee2f7498ab744662c364b518a?v=0d711eab95f748eb82a966726ec3f757&pvs=4",
    SVGIcons.PACKAGE,
    true
)
val navbarItems = arrayListOf(
    HOME_NAVI_ITEM,
    NOTES_NAVI_ITEM,
)