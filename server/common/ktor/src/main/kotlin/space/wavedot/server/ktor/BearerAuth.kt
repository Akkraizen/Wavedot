package space.wavedot.server.ktor

import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.bearer

fun <P : Any> AuthenticationConfig.bearerToken(name: String, resolve: suspend (String) -> P?) {
    bearer(name) {
        authenticate { credential -> resolve(credential.token) }
    }
}
