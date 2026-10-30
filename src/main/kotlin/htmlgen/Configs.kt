package htmlgen

const val HOME_PAGE_SIZE = 10

data class FriendLinkItem(val id: String, val name: String, val link: String)
val friendLinkItems = arrayOf(
    FriendLinkItem("rasp", "拉斯普的月台", "https://blog.rasp505.top/"),
    FriendLinkItem("dyron", "dyron503's", "https://career.dyron503.top/"),
    FriendLinkItem("hana", "北依的树洞", "https://hanahoshikawa092.netlify.app"),
    FriendLinkItem("iz", "以子iz", "https://www.dangoiz.com"),
    FriendLinkItem("duck", "三重鸭", "https://akacmd.github.io/blog/"),
)
