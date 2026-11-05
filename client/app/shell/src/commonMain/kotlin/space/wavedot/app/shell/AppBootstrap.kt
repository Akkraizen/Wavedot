package space.wavedot.app.shell

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.Koin
import space.wavedot.core.common.Logger
import space.wavedot.core.common.warn
import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.data.plugin.PluginManager
import space.wavedot.source.local.LocalMediaSource

fun Koin.bootstrap() {
    val scope = get<CoroutineScope>(APP_SCOPE)
    val local = get<LocalMediaSource>()
    get<MediaSourceRegistry>().register(local)
    scope.launch {
        runCatching { local.refresh() }.onFailure { get<Logger>().warn("Bootstrap", "Local scan failed", it) }
    }
    scope.launch { get<PluginManager>().loadAll() }
}
