package htmlgen.page

class OutsideLink(val displayName: String, val url: String) : HomeSelection {
    override fun displayName(): String =
        displayName

    override fun url(): String = url
}