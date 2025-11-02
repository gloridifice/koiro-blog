package htmlgen

val INTRODUCE = "人类、学生、游戏开发者、平面设计笨蛋和技术美术笨蛋。"
data class FriendLinkItem(val name: String, val link: String, val description: String = "")
// Friend links
val friendLinkItems = arrayOf(
    FriendLinkItem("拉斯普的月台", "https://blog.rasp505.top/", "海拉鲁驿站"),
    FriendLinkItem("dyron503's", "https://career.dyron503.top/", "你知道吗？我的 ID 中「503」的出处是……"),
    FriendLinkItem("北依的树洞", "https://hanahoshikawa092.netlify.app", "音乐是救世主"),
    FriendLinkItem("以子iz", "https://www.dangoiz.com", "插画师和游戏开发者"),
)