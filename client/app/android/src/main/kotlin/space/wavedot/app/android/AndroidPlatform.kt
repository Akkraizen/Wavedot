package space.wavedot.app.android

import android.os.Build
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import space.wavedot.app.shell.PlatformEnvironment
import space.wavedot.app.shell.SECRETS_STORE
import space.wavedot.app.shell.SETTINGS_STORE
import space.wavedot.core.common.KeyValueStore
import space.wavedot.core.domain.update.AppInfo
import space.wavedot.core.domain.update.UpdateChannel
import space.wavedot.infra.database.createAndroidDatabase
import space.wavedot.infra.network.HttpClientSettings
import space.wavedot.infra.network.defaultHttpClient
import space.wavedot.infra.playback.android.Media3AudioEngine
import space.wavedot.infra.storage.FileKeyValueStore
import space.wavedot.infra.streamproxy.LoopbackStreamProxy
import space.wavedot.infra.updater.UpdateTarget
import space.wavedot.playback.api.AudioEngine
import space.wavedot.playback.api.StreamProxy
import space.wavedot.protocol.core.ArchDto
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.source.local.LocalTrackScanner
import space.wavedot.source.local.MediaStoreTrackScanner
import java.io.File
import java.util.UUID

const val APP_VERSION = "0.1.0"

val androidModule = module {
    single { defaultHttpClient(HttpClientSettings(userAgent = "Wavedot/$APP_VERSION")) }
    single { createAndroidDatabase(androidContext()) }
    single<KeyValueStore>(SETTINGS_STORE) { FileKeyValueStore(File(androidContext().filesDir, "settings.properties")) }
    single<KeyValueStore>(SECRETS_STORE) { FileKeyValueStore(File(androidContext().noBackupFilesDir, "secrets.properties")) }
    single<StreamProxy> { LoopbackStreamProxy() }
    single { Media3AudioEngine(androidContext(), get()) }
    single<AudioEngine> { get<Media3AudioEngine>() }
    single<LocalTrackScanner> { MediaStoreTrackScanner(androidContext()) }
    single {
        val idFile = File(androidContext().filesDir, "install-id")
        val installId = idFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also { idFile.writeText(it) }
        PlatformEnvironment(
            app = AppInfo(APP_VERSION, UpdateChannel.STABLE, installId),
            updateTarget = UpdateTarget(PlatformDto.ANDROID, ArchDto.UNIVERSAL),
            platform = PlatformDto.ANDROID,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            supportsMusicFolders = false,
            cacheDirectory = androidContext().cacheDir.absolutePath,
        )
    }
}
