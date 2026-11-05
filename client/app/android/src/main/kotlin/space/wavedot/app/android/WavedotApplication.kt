package space.wavedot.app.android

import android.app.Application
import androidx.media3.common.Player
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import space.wavedot.app.shell.bootstrap
import space.wavedot.app.shell.wavedotModules
import space.wavedot.infra.playback.android.Media3AudioEngine
import space.wavedot.infra.playback.android.PlayerHost

class WavedotApplication : Application(), PlayerHost {
    private val koin by lazy {
        startKoin {
            androidContext(this@WavedotApplication)
            modules(wavedotModules(androidModule))
        }.koin
    }

    override val sessionPlayer: Player get() = koin.get<Media3AudioEngine>().player

    override fun onCreate() {
        super.onCreate()
        koin.bootstrap()
    }
}
