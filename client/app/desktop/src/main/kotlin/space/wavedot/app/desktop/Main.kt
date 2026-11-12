package space.wavedot.app.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.koin.core.context.startKoin
import space.wavedot.app.shell.PlatformEnvironment
import space.wavedot.app.shell.bootstrap
import space.wavedot.app.shell.wavedotModules
import space.wavedot.core.domain.artwork.ResolveArtwork
import space.wavedot.playback.api.AudioEngine
import space.wavedot.playback.api.StreamProxy
import space.wavedot.ui.app.NavigationLayout
import space.wavedot.ui.app.WavedotApp
import space.wavedot.ui.designsystem.wavedotLogoVector
import space.wavedot.ui.window.WavedotTitleBar
import space.wavedot.ui.window.WindowController
import space.wavedot.ui.window.WindowResizeEdges
import java.awt.Dimension

fun main() {
    val koin = startKoin { modules(wavedotModules(desktopModule)) }.koin
    koin.bootstrap()
    val resolveArtwork = koin.get<ResolveArtwork>()
    application {
        val shutdown = {
            koin.get<AudioEngine>().close()
            koin.get<StreamProxy>().close()
            exitApplication()
        }
        Window(
            onCloseRequest = shutdown,
            title = "Wavedot",
            undecorated = true,
            icon = rememberVectorPainter(remember { wavedotLogoVector(Color.White, Color.Black, padding = 12f) }),
            state = rememberWindowState(width = 1100.dp, height = 760.dp),
        ) {
            window.minimumSize = Dimension(1100, 760)
            val controller = remember(window) { WindowController(window) }
            Box(Modifier.fillMaxSize()) {
                WavedotApp(
                    httpClient = koin.get(),
                    imageCacheDirectory = koin.get<PlatformEnvironment>().cacheDirectory,
                    artworkResolver = { resolveArtwork(it) },
                    navigationLayout = NavigationLayout.SIDE_RAIL,
                    titleBar = { WavedotTitleBar(controller, onClose = shutdown) },
                )
                WindowResizeEdges(window, controller)
            }
        }
    }
}
