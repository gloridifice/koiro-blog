data class ServerPath(val string: String) {
    fun serverPath(): String {
        return string;
    }

    override fun toString(): String {
        return string;
    }

    fun staticPath(): String {
        return "static/$string"
    }
}
