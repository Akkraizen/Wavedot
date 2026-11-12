package space.wavedot.plugin.radiobrowser

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import space.wavedot.core.common.InMemoryKeyValueStore
import space.wavedot.core.contract.ui.UiEffect
import space.wavedot.core.contract.ui.UiNode
import space.wavedot.plugin.api.PluginStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RadioBrowserUiTest {
    private class Store(private val delegate: InMemoryKeyValueStore = InMemoryKeyValueStore()) : PluginStore {
        override suspend fun get(key: String) = delegate.get(key)
        override suspend fun set(key: String, value: String) = delegate.set(key, value)
        override suspend fun delete(key: String) = delegate.delete(key)
        override fun observe(key: String) = delegate.observe(key)
    }

    private val server = FakeRadioBrowser()
    private val settings = Store()
    private val ui = RadioBrowserUi(server.api, settings).build()
    private val screen = ui.screens.single()

    private suspend fun loaded(): List<UiNode> = screen.content.first { nodes -> nodes.none { it is UiNode.Loading } }

    @Test
    fun screenListsTopStationsAndFollowsCountryChoice() = runTest {
        val everywhere = loaded()
        assertEquals("all", assertIs<UiNode.Choice>(everywhere.first()).selectedId)
        assertEquals(listOf("Top ALL"), assertIs<UiNode.Tracks>(everywhere.last()).tracks.map { it.title })
        assertNull(server.requests.last().url.parameters["countrycode"])

        val choice = assertIs<UiNode.Choice>(everywhere.first())
        assertEquals(UiEffect.None, choice.action.run("DE"))
        assertEquals("DE", settings.get(RadioBrowserUi.COUNTRY_KEY))

        val germany = screen.content.first { nodes -> nodes.any { it is UiNode.Tracks && it.tracks.first().title == "Top DE" } }
        assertEquals("DE", assertIs<UiNode.Choice>(germany.first()).selectedId)
        assertEquals("DE", server.requests.last().url.parameters["countrycode"])

        assertIs<UiNode.Choice>(germany.first()).action.run(RadioBrowserUi.ALL)
        assertNull(settings.get(RadioBrowserUi.COUNTRY_KEY))
    }

    @Test
    fun settingsPageOffersTheSameChoice() = runTest {
        settings.set(RadioBrowserUi.COUNTRY_KEY, "JP")
        val page = assertNotNull(ui.settings).first()
        assertEquals("JP", assertIs<UiNode.Choice>(page.last()).selectedId)
    }
}
