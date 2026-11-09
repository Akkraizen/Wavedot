package space.wavedot.app.shell

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import space.wavedot.core.common.ConsoleLogger
import space.wavedot.core.common.KeyValueStore
import space.wavedot.core.common.Logger
import space.wavedot.core.common.SystemTimeProvider
import space.wavedot.core.common.TimeProvider
import space.wavedot.core.contract.LyricsProviderRegistry
import space.wavedot.core.contract.MediaSourceRegistry
import space.wavedot.core.data.plugin.DefaultPluginContextFactory
import space.wavedot.core.data.plugin.PluginManager
import space.wavedot.core.data.source.DefaultLyricsProviderRegistry
import space.wavedot.core.data.source.DefaultMediaSourceRegistry
import space.wavedot.core.domain.library.FavoritesRepository
import space.wavedot.core.domain.library.LocalLibrary
import space.wavedot.core.domain.library.PlaylistEditor
import space.wavedot.core.domain.library.PlaylistRepository
import space.wavedot.core.domain.library.ToggleFavorite
import space.wavedot.core.domain.lyrics.GetLyrics
import space.wavedot.core.domain.lyrics.LyricsCache
import space.wavedot.core.domain.player.FixedPlaybackPreferences
import space.wavedot.core.domain.player.PlaybackPreferences
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.domain.player.ResolveStream
import space.wavedot.core.domain.search.LoadCollection
import space.wavedot.core.domain.search.SearchCatalog
import space.wavedot.core.domain.sync.SyncGateway
import space.wavedot.core.domain.sync.SyncLibrary
import space.wavedot.core.domain.sync.SyncStateStore
import space.wavedot.core.domain.update.CheckForUpdates
import space.wavedot.core.domain.update.UpdateChecker
import space.wavedot.core.model.AudioQuality
import space.wavedot.feature.library.LibraryViewModel
import space.wavedot.feature.library.PlaylistPickerViewModel
import space.wavedot.feature.player.LyricsViewModel
import space.wavedot.feature.player.PlayerViewModel
import space.wavedot.feature.search.SearchViewModel
import space.wavedot.feature.settings.SettingsBackend
import space.wavedot.feature.settings.SettingsViewModel
import space.wavedot.infra.database.SqlFavoritesRepository
import space.wavedot.infra.database.SqlLyricsCache
import space.wavedot.infra.database.SqlPlaylistRepository
import space.wavedot.infra.database.SqlSyncStateStore
import space.wavedot.infra.hub.HubClient
import space.wavedot.infra.hub.HubSettings
import space.wavedot.infra.updater.HttpUpdateChecker
import space.wavedot.plugin.api.WavedotPlugin
import space.wavedot.plugin.lrclib.LrclibPlugin
import space.wavedot.plugin.yandexmusic.YandexMusicPlugin
import space.wavedot.source.local.LocalMediaSource

val APP_SCOPE = named("appScope")
val SETTINGS_STORE = named(StoreNames.SETTINGS)
val SECRETS_STORE = named(StoreNames.SECRETS)

val builtInPluginsModule: Module = module {
    single<List<WavedotPlugin>> { listOf(YandexMusicPlugin(), LrclibPlugin()) }
}

val coreModule: Module = module {
    single<CoroutineScope>(APP_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<Logger> { ConsoleLogger() }
    single<TimeProvider> { SystemTimeProvider }
    single<MediaSourceRegistry> { DefaultMediaSourceRegistry() }
    single<LyricsProviderRegistry> { DefaultLyricsProviderRegistry() }
    single<LyricsCache> { SqlLyricsCache(get(), Dispatchers.IO) }
    factory { GetLyrics(get(), get(), get(), get(), get()) }
    single {
        PluginManager(
            plugins = get(),
            registry = get(),
            lyricsRegistry = get(),
            contextFactory = DefaultPluginContextFactory(get(), get(SETTINGS_STORE), get(SECRETS_STORE), get()),
            logger = get(),
        )
    }
    single { LocalMediaSource(get()) }
    single<LocalLibrary> { LocalLibraryAdapter(get()) }
    single { MusicFolders(get<KeyValueStore>(SETTINGS_STORE)) }
    single<FavoritesRepository> { SqlFavoritesRepository(get(), Dispatchers.IO) }
    single<PlaylistRepository> { SqlPlaylistRepository(get(), Dispatchers.IO) }
    factory { PlaylistEditor(get(), get()) }
    single<SyncStateStore> { SqlSyncStateStore(get(), Dispatchers.IO) }
    single { HubSettings(get(SETTINGS_STORE)) }
    single { HubClient(get(), get()) } bind SyncGateway::class
    single<UpdateChecker> {
        val settings = get<KeyValueStore>(SETTINGS_STORE)
        val environment = get<PlatformEnvironment>()
        HttpUpdateChecker(get(), { settings.get("updates.url") ?: environment.defaultUpdatesUrl }, environment.updateTarget)
    }
    single<PlaybackPreferences> { FixedPlaybackPreferences(AudioQuality.LOSSLESS) }
    single { ResolveStream(get()) }
    single { PlayerController(get(), get(), get(), get(APP_SCOPE), get()) }
    factory { SearchCatalog(get()) }
    factory { LoadCollection(get()) }
    factory { ToggleFavorite(get(), get(), get(), get()) }
    factory { SyncLibrary(get(), get(), get(), get(), get()) }
    factory { CheckForUpdates(get(), get<PlatformEnvironment>().app) }
    single<SettingsBackend> {
        DefaultSettingsBackend(
            pluginManager = get(),
            settingsStore = get(SETTINGS_STORE),
            secretsStore = get(SECRETS_STORE),
            hubClient = get(),
            hubSettings = get(),
            syncLibrary = { get() },
            updateCheck = { get() },
            folders = get(),
            localLibrary = get(),
            environment = get(),
            appearance = get(),
        )
    }
    single { AppearanceSettings(get(SETTINGS_STORE)) }
}

val viewModelModule: Module = module {
    viewModelOf(::PlayerViewModel)
    viewModelOf(::LyricsViewModel)
    viewModelOf(::LibraryViewModel)
    viewModelOf(::PlaylistPickerViewModel)
    viewModel { SearchViewModel(get(), get(), get(), get(), get()) }
    viewModelOf(::SettingsViewModel)
}

fun wavedotModules(platform: Module): List<Module> = listOf(platform, builtInPluginsModule, coreModule, viewModelModule)
