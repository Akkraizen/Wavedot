package space.wavedot.feature.settings

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

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeBackend : SettingsBackend {
        val saved = mutableListOf<Triple<String, String, String>>()
        val folders = MutableStateFlow(listOf("/music"))
        override val plugins: Flow<List<PluginInfo>> = MutableStateFlow(emptyList())
        override val hubConnected: Flow<Boolean> = MutableStateFlow(false)
        override val musicFolders: Flow<List<String>> = folders
        override val appVersion: String = "0.1.0"
        override val accentId = MutableStateFlow("wavedot")
        override val systemAccentAvailable: Boolean = true

        override suspend fun updatePluginValue(pluginId: String, key: String, value: String) {
            saved += Triple(pluginId, key, value)
        }

        override suspend fun connectHub(url: String) = error("unreachable")
        override suspend fun disconnectHub() = Unit
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

        viewModel.onIntent(SettingsIntent.ConnectHub("https://hub"))
        assertEquals("unreachable", viewModel.state.first { it.message == "unreachable" }.message)

        viewModel.onIntent(SettingsIntent.SelectAccent("teal"))
        assertEquals("teal", viewModel.state.first { it.accentId == "teal" }.accentId)
        assertEquals("system", viewModel.state.value.accents.first().id)

        viewModel.onIntent(SettingsIntent.AddFolder("/more"))
        assertEquals(listOf("/music", "/more"), viewModel.state.first { it.musicFolders?.size == 2 }.musicFolders)
    }
}
