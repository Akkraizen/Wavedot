package space.wavedot.presentation.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import space.wavedot.core.domain.sync.SyncReport
import space.wavedot.core.domain.update.AvailableUpdate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeBackend(
        override val plugins: Flow<List<PluginInfo>> = MutableStateFlow(emptyList()),
        supportsFolders: Boolean = true,
    ) : SettingsBackend {
        val saved = mutableListOf<Triple<String, String, String>>()
        val hubRequests = mutableListOf<Pair<String, String>>()
        val folders = MutableStateFlow(listOf("/music"))
        override val hubConnected: Flow<Boolean> = MutableStateFlow(false)
        override val musicFolders: Flow<List<String>>? = folders.takeIf { supportsFolders }
        override val appVersion: String = "0.1.0"
        override val accentId = MutableStateFlow<String?>("wavedot")

        override suspend fun updatePluginValue(pluginId: String, key: String, value: String) {
            saved += Triple(pluginId, key, value)
        }

        override suspend fun connectHub(url: String, registrationToken: String) {
            hubRequests += url to registrationToken
        }

        override suspend fun disconnectHub() = Unit

        override suspend fun deleteHubAccount() {
            hubRequests += "delete" to ""
        }

        override suspend fun syncNow() = SyncReport(imported = 2, pushed = 1, pulled = 3)
        override suspend fun checkForUpdates(): AvailableUpdate? = null
        override suspend fun setMusicFolders(folders: List<String>) {
            this.folders.value = folders
        }

        override suspend fun rescanLocal(): Int = 12

        override suspend fun setAccent(id: String) {
            accentId.value = id
        }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun reportsOutcomesAndErrors() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = SettingsViewModel(backend)
        backgroundScope.launch { viewModel.state.collect {} }

        viewModel.onIntent(SettingsIntent.SavePluginValue("yandex-music", "token", "  abc "))
        assertEquals(Triple("yandex-music", "token", "abc"), backend.saved.single())

        viewModel.onIntent(SettingsIntent.SyncNow)
        assertEquals("Imported 2, sent 1, received 3", viewModel.state.first { it.message?.startsWith("Imported") == true }.message)

        viewModel.onIntent(SettingsIntent.ConnectHub(" https://hub ", " invite "))
        assertEquals("Connected to hub", viewModel.state.first { it.message == "Connected to hub" }.message)
        assertEquals("https://hub" to "invite", backend.hubRequests.single())

        viewModel.onIntent(SettingsIntent.DeleteHubAccount)
        assertEquals("delete" to "", backend.hubRequests.last())

        viewModel.onIntent(SettingsIntent.SelectAccent("teal"))
        assertEquals("teal", viewModel.state.first { it.accentId == "teal" }.accentId)

        viewModel.onIntent(SettingsIntent.AddFolder("/more"))
        assertEquals(listOf("/music", "/more"), viewModel.state.first { it.musicFolders?.size == 2 }.musicFolders)
    }

    @Test
    fun opensAndClosesSections() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(FakeBackend())
        backgroundScope.launch { viewModel.state.collect {} }
        assertEquals(SettingsSection.entries, viewModel.state.value.sections)
        assertNull(viewModel.state.value.section)

        viewModel.onIntent(SettingsIntent.OpenSection(SettingsSection.PLUGINS))
        assertEquals(SettingsSection.PLUGINS, viewModel.state.first { it.section != null }.section)

        viewModel.onIntent(SettingsIntent.CloseSection)
        assertNull(viewModel.state.first { it.section == null }.section)
    }

    @Test
    fun hidesLibrarySectionWithoutMusicFolders() = runTest(dispatcher) {
        val viewModel = SettingsViewModel(FakeBackend(supportsFolders = false))
        backgroundScope.launch { viewModel.state.collect {} }
        viewModel.onIntent(SettingsIntent.OpenSection(SettingsSection.LIBRARY))
        assertFalse(SettingsSection.LIBRARY in viewModel.state.value.sections)
        assertNull(viewModel.state.value.section)
    }

    @Test
    fun groupsPluginsIntoSourcesAndLyrics() = runTest(dispatcher) {
        fun plugin(id: String, group: PluginGroup) = PluginInfo(id, id, "", "Active", true, emptyList(), group)
        val plugins = listOf(plugin("yandex-music", PluginGroup.SOURCES), plugin("lrclib", PluginGroup.LYRICS), plugin("audius", PluginGroup.SOURCES))
        val viewModel = SettingsViewModel(FakeBackend(plugins = MutableStateFlow(plugins)))
        backgroundScope.launch { viewModel.state.collect {} }
        val state = viewModel.state.first { it.plugins.isNotEmpty() }
        assertEquals(listOf("yandex-music", "audius"), state.sourcePlugins.map { it.id })
        assertEquals(listOf("lrclib"), state.lyricsPlugins.map { it.id })
    }

    @Test
    fun appearanceFollowsStoredAccent() = runTest(dispatcher) {
        val backend = FakeBackend()
        val viewModel = AppearanceViewModel(backend)
        assertEquals("wavedot", viewModel.accentId.first { it != null })
        backend.setAccent("teal")
        assertEquals("teal", viewModel.accentId.first { it == "teal" })
    }
}
