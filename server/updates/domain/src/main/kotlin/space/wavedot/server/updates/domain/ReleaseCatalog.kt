package space.wavedot.server.updates.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant

class ReleaseCatalog(
    private val releases: ReleaseRepository,
    private val clock: Clock,
    private val timeToLive: Duration = Duration.ofSeconds(30),
) {
    private data class Snapshot(val loadedAt: Instant, val releases: List<Release>)

    @Volatile
    private var snapshot: Snapshot? = null

    suspend fun active(): List<Release> {
        val now = clock.instant()
        snapshot?.takeIf { Duration.between(it.loadedAt, now) < timeToLive }?.let { return it.releases }
        return releases.listActive().also { snapshot = Snapshot(now, it) }
    }

    fun invalidate() {
        snapshot = null
    }
}
