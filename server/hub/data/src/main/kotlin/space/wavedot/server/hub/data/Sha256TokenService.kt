package space.wavedot.server.hub.data

import space.wavedot.server.hub.domain.account.TokenService
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class Sha256TokenService(private val random: SecureRandom = SecureRandom()) : TokenService {
    override fun generate(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}
