package space.wavedot.presentation.extensions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import space.wavedot.core.contract.ui.PluginAccent
import space.wavedot.core.contract.ui.PluginScreen
import space.wavedot.core.contract.ui.PluginSection
import space.wavedot.core.contract.ui.PluginTrackAction
import space.wavedot.core.contract.ui.PluginUi
import space.wavedot.core.contract.ui.RegisteredPluginUi
import space.wavedot.core.contract.ui.UiAction
import space.wavedot.core.contract.ui.UiEffect
import space.wavedot.core.contract.ui.UiExtensionRegistry
import space.wavedot.core.contract.ui.UiIcon
import space.wavedot.core.contract.ui.UiNode
import space.wavedot.core.contract.ui.UiTone
import space.wavedot.core.contract.ui.UiValueAction
import space.wavedot.core.domain.player.FixedPlaybackPreferences
import space.wavedot.core.domain.player.PlayerController
import space.wavedot.core.domain.player.ResolveStream
import space.wavedot.core.model.SourceId
import space.wavedot.core.testing.FakeAudioEngine
import space.wavedot.core.testing.FakeMediaSource
import space.wavedot.core.testing.TestRegistry
import space.wavedot.core.testing.TestTracks
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val tracks = TestTracks.tracks(3)
    private val otherTrack = TestTracks.track("x", source = SourceId("other"))

    private class Registry(initial: List<RegisteredPluginUi>) : UiExtensionRegistry {
        override val extensions = MutableStateFlow(initial)
        override fun register(extension: RegisteredPluginUi) {
            extensions.value = extensions.value.filterNot { it.pluginId == extension.pluginId } + extension
        }
        override fun unregister(pluginId: String) {
            extensions.value = extensions.value.filterNot { it.pluginId == pluginId }
        }
    }

    private val screenNodes = MutableStateFlow<List<UiNode>>(listOf(UiNode.Heading("Top")))

    private val ui = PluginUi(
        screens = listOf(PluginScreen("home", "Home", UiIcon.EXPLORE, screenNodes)),
        librarySections = listOf(PluginSection("broken", "Broken", flow { error("offline") })),
        trackActions = listOf(
            PluginTrackAction(
                id = "more",
                label = "More like this",
                icon = UiIcon.PLAYLIST,
                action = UiValueAction { UiEffect.Play(tracks, 1) },
                appliesTo = { it.id.source == TestTracks.SOURCE },
            ),
        ),
        settings = flowOf(listOf(UiNode.Text("Settings page"))),
        accents = listOf(PluginAccent("violet", "Violet", 0xFF8800FF)),
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.create(registry: Registry = Registry(listOf(RegisteredPluginUi("demo", "Demo", ui))))
        : Triple<ExtensionsViewModel, PlayerController, Registry> {
        val sources = TestRegistry(FakeMediaSource(tracks = tracks))
        val player = PlayerController(FakeAudioEngine(), ResolveStream(sources), FixedPlaybackPreferences(), backgroundScope)
        val viewModel = ExtensionsViewModel(registry, player)
        backgroundScope.launch { viewModel.state.collect {} }
        return Triple(viewModel, player, registry)
    }

    @Test
    fun exposesContributionsWithNamespacedKeys() = runTest(dispatcher) {
        val (viewModel, _, _) = create()
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(listOf(ExtensionScreen("demo/home", "Home", UiIcon.EXPLORE, "Demo")), state.screens)
        assertEquals(listOf("demo/broken"), state.librarySections.map { it.key })
        assertEquals(listOf(ExtensionAccent("demo/violet", "Violet", 0xFF8800FF)), state.accents)
        assertEquals(setOf("demo"), state.settingsPages)
    }

    @Test
    fun streamsScreenContentAndReportsFailures() = runTest(dispatcher) {
        val (viewModel, _, _) = create()
        val screen = viewModel.screenContent("demo/home")
        val section = viewModel.sectionContent("demo/broken")
        backgroundScope.launch { screen.collect {} }
        backgroundScope.launch { section.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(UiNode.Heading("Top")), screen.value)
        screenNodes.value = listOf(UiNode.Text("Updated"))
        advanceUntilIdle()
        assertEquals(listOf(UiNode.Text("Updated")), screen.value)
        val status = assertIs<UiNode.Status>(section.value.single())
        assertEquals(UiTone.ERROR, status.tone)
        assertEquals(listOf(UiNode.Text("Settings page")), viewModel.settingsContent("demo").first { it != listOf(UiNode.Loading) })
    }

    @Test
    fun filtersTrackActionsAndPlaysTheirResult() = runTest(dispatcher) {
        val (viewModel, player, _) = create()
        assertEquals(emptyList(), viewModel.trackActions(otherTrack))
        val action = viewModel.trackActions(tracks[0]).single()
        assertEquals("demo/more", action.key)

        viewModel.onIntent(ExtensionsIntent.RunTrackAction(action.key, tracks[0]))
        advanceUntilIdle()
        assertEquals(tracks[1], player.state.first { it.current != null }.current)
    }

    @Test
    fun showsMessagesFromActionsAndErrors() = runTest(dispatcher) {
        val (viewModel, _, _) = create()
        viewModel.onIntent(ExtensionsIntent.Run(UiAction { UiEffect.Message("Saved") }))
        advanceUntilIdle()
        assertEquals("Saved", viewModel.state.value.message)

        viewModel.onIntent(ExtensionsIntent.RunWithValue(UiValueAction<Boolean> { error("nope $it") }, true))
        advanceUntilIdle()
        assertEquals("nope true", viewModel.state.value.message)

        viewModel.onIntent(ExtensionsIntent.DismissMessage)
        advanceUntilIdle()
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun dropsContributionsWhenPluginUnloads() = runTest(dispatcher) {
        val (viewModel, _, registry) = create()
        advanceUntilIdle()
        registry.unregister("demo")
        advanceUntilIdle()
        assertEquals(ExtensionsUiState(), viewModel.state.value)
        assertEquals(emptyList(), viewModel.trackActions(tracks[0]))
    }
}
