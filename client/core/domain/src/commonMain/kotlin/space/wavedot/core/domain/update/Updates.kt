package space.wavedot.core.domain.update

enum class UpdateChannel { STABLE, BETA, NIGHTLY }

data class AppInfo(
    val version: String,
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val installId: String,
)

data class AvailableUpdate(
    val version: String,
    val notes: String,
    val mandatory: Boolean,
    val downloadUrl: String,
    val sha256: String,
    val size: Long,
)

interface UpdateChecker {
    suspend fun latest(app: AppInfo): AvailableUpdate?
}

class CheckForUpdates(
    private val checker: UpdateChecker,
    private val app: AppInfo,
) {
    suspend operator fun invoke(): AvailableUpdate? = checker.latest(app)
}
