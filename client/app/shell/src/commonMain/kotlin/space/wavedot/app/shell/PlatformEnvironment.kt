package space.wavedot.app.shell

import space.wavedot.core.domain.update.AppInfo
import space.wavedot.infra.updater.UpdateTarget
import space.wavedot.protocol.core.PlatformDto

data class PlatformEnvironment(
    val app: AppInfo,
    val updateTarget: UpdateTarget,
    val platform: PlatformDto,
    val deviceName: String,
    val supportsMusicFolders: Boolean,
    val defaultUpdatesUrl: String? = null,
)

object StoreNames {
    const val SETTINGS: String = "settings"
    const val SECRETS: String = "secrets"
}
