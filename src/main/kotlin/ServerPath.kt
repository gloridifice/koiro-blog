data class ServerPath(val string: String) {
    val serverPath = if(string.startsWith("/")) string else "/$string"

    val staticPath = "static/$string"

    override fun toString(): String {
        return serverPath;
    }
}
