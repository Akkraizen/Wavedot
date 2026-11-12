package space.wavedot.core.common

object SecureUrl {
    private val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "[::1]")

    fun isAcceptable(url: String): Boolean {
        val value = url.trim().lowercase()
        if (value.startsWith("https://")) return value.length > "https://".length
        if (!value.startsWith("http://")) return false
        val authority = value.removePrefix("http://").substringBefore('/').substringBefore('?').substringAfterLast('@')
        val host = if (authority.startsWith('[')) authority.substringBefore(']') + "]" else authority.substringBefore(':')
        return host in LOOPBACK_HOSTS
    }

    fun require(url: String, what: String): String {
        if (!isAcceptable(url)) {
            throw WavedotException(WavedotError.Invalid("$what must use https (plain http is allowed only for localhost)"))
        }
        return url.trim()
    }
}
