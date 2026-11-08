package space.wavedot.app.desktop

import org.koin.dsl.module
import space.wavedot.app.shell.MusicFolders
import space.wavedot.app.shell.PlatformEnvironment
import space.wavedot.app.shell.SECRETS_STORE
import space.wavedot.app.shell.SETTINGS_STORE
import space.wavedot.core.common.KeyValueStore
import space.wavedot.core.domain.update.AppInfo
import space.wavedot.core.domain.update.UpdateChannel
import space.wavedot.infra.database.createJvmDatabase
import space.wavedot.infra.network.HttpClientSettings
import space.wavedot.infra.network.defaultHttpClient
import space.wavedot.infra.playback.desktop.FfmpegAudioEngine
import space.wavedot.infra.storage.FileKeyValueStore
import space.wavedot.infra.streamproxy.LoopbackStreamProxy
import space.wavedot.infra.updater.UpdateTarget
import space.wavedot.playback.api.AudioEngine
import space.wavedot.playback.api.StreamProxy
import space.wavedot.protocol.core.ArchDto
import space.wavedot.protocol.core.PlatformDto
import space.wavedot.source.local.FileSystemTrackScanner
import space.wavedot.source.local.LocalTrackScanner
import java.io.File
import java.net.InetAddress
import java.util.UUID

const val APP_VERSION = "0.1.0"

object DesktopPaths {
    val dataDir: File by lazy {
        val os = System.getProperty("os.name").lowercase()
        val home = File(System.getProperty("user.home"))
        when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: home.path, "Wavedot")
            os.contains("mac") -> File(home, "Library/Application Support/Wavedot")
            else -> File(System.getenv("XDG_DATA_HOME") ?: File(home, ".local/share").path, "wavedot")
        }.apply { mkdirs() }
    }
}

private fun currentPlatform(): PlatformDto {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> PlatformDto.WINDOWS
        os.contains("mac") -> PlatformDto.MACOS
        else -> PlatformDto.LINUX
    }
}

private fun currentArch(): ArchDto =
    if (System.getProperty("os.arch").lowercase().let { it.contains("aarch64") || it.contains("arm64") }) ArchDto.ARM64 else ArchDto.X64

private fun installId(settings: File): String {
    val file = File(settings.parentFile, "install-id")
    return file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
        ?: UUID.randomUUID().toString().also { file.writeText(it) }
}

val desktopModule = module {
    single { defaultHttpClient(HttpClientSettings(userAgent = "Wavedot/$APP_VERSION")) }
    single { createJvmDatabase(File(DesktopPaths.dataDir, "wavedot.db")) }
    single<KeyValueStore>(SETTINGS_STORE) { FileKeyValueStore(File(DesktopPaths.dataDir, "settings.properties")) }
    single<KeyValueStore>(SECRETS_STORE) { FileKeyValueStore(File(DesktopPaths.dataDir, "secrets.properties")) }
    single<StreamProxy> { LoopbackStreamProxy() }
    single<AudioEngine> { FfmpegAudioEngine(get()) }
    single<LocalTrackScanner> {
        val folders = get<MusicFolders>()
        FileSystemTrackScanner(File(DesktopPaths.dataDir, "covers")) { folders.current().map(::File) }
    }
    single {
        PlatformEnvironment(
            app = AppInfo(APP_VERSION, UpdateChannel.STABLE, installId(File(DesktopPaths.dataDir, "settings.properties"))),
            updateTarget = UpdateTarget(currentPlatform(), currentArch()),
            platform = currentPlatform(),
            deviceName = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("Desktop"),
            supportsMusicFolders = true,
            cacheDirectory = File(DesktopPaths.dataDir, "cache").absolutePath,
        )
    }
}
