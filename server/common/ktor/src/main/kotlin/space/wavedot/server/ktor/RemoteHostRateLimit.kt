package space.wavedot.server.ktor

import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimitConfig
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration

fun RateLimitConfig.perRemoteHost(name: RateLimitName, limit: Int, period: Duration) {
    register(name) {
        rateLimiter(limit = limit, refillPeriod = period)
        requestKey { call -> call.request.origin.remoteHost }
    }
}
