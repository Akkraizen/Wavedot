package space.wavedot.server.hub.domain.account

import space.wavedot.server.kernel.AppError
import java.security.MessageDigest

enum class RegistrationMode { OPEN, TOKEN, CLOSED }

class RegistrationPolicy(
    private val mode: RegistrationMode,
    private val token: String = "",
) {
    fun check(presented: String?) {
        when (mode) {
            RegistrationMode.OPEN -> Unit
            RegistrationMode.CLOSED -> throw AppError.Forbidden("Registration is closed on this hub")
            RegistrationMode.TOKEN -> if (!matches(presented)) throw AppError.Forbidden("A valid registration token is required")
        }
    }

    private fun matches(presented: String?): Boolean =
        token.isNotBlank() && presented != null && MessageDigest.isEqual(presented.toByteArray(), token.toByteArray())

    companion object {
        val OPEN: RegistrationPolicy = RegistrationPolicy(RegistrationMode.OPEN)
    }
}
