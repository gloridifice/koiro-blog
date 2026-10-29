data class ServerPath(val string: String) {
    val relativePath = string.trimStart('/')
    val serverPath = "/$relativePath"

    override fun toString(): String = serverPath
}
