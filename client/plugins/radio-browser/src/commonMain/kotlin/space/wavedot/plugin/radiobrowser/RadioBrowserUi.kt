package space.wavedot.plugin.radiobrowser

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import space.wavedot.core.common.toWavedotError
import space.wavedot.core.contract.ui.PluginScreen
import space.wavedot.core.contract.ui.PluginUi
import space.wavedot.core.contract.ui.UiEffect
import space.wavedot.core.contract.ui.UiIcon
import space.wavedot.core.contract.ui.UiNode
import space.wavedot.core.contract.ui.UiOption
import space.wavedot.core.contract.ui.UiTextStyle
import space.wavedot.core.contract.ui.UiTone
import space.wavedot.core.contract.ui.UiValueAction
import space.wavedot.plugin.api.PluginStore

@OptIn(ExperimentalCoroutinesApi::class)
internal class RadioBrowserUi(
    private val api: RadioBrowserApi,
    private val settings: PluginStore,
) {
    private val country: Flow<String> = settings.observe(COUNTRY_KEY).map { it ?: ALL }.distinctUntilChanged()

    fun build(): PluginUi = PluginUi(
        screens = listOf(PluginScreen(SCREEN_ID, "Radio", UiIcon.RADIO, stations())),
        settings = country.map { selected ->
            listOf(
                UiNode.Text("The Radio screen lists the most popular stations of this country.", UiTextStyle.CAPTION),
                countryChoice(selected),
            )
        },
    )

    private fun stations(): Flow<List<UiNode>> = country.transformLatest { selected ->
        val chooser = countryChoice(selected)
        emit(listOf(chooser, UiNode.Loading))
        val nodes = try {
            val tracks = api.top(selected.takeIf { it != ALL }, LIMIT).map(RadioBrowserSource::track)
            if (tracks.isEmpty()) listOf(UiNode.Status("No stations found."))
            else listOf(UiNode.Heading("Most popular"), UiNode.Tracks(tracks))
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            listOf(UiNode.Status(t.toWavedotError().message, UiTone.ERROR))
        }
        emit(listOf(chooser) + nodes)
    }

    private fun countryChoice(selected: String) = UiNode.Choice(
        label = "Country",
        options = COUNTRIES,
        selectedId = selected,
        action = UiValueAction { code ->
            if (code == ALL) settings.delete(COUNTRY_KEY) else settings.set(COUNTRY_KEY, code)
            UiEffect.None
        },
    )

    companion object {
        const val SCREEN_ID: String = "stations"
        const val COUNTRY_KEY: String = "country"
        const val ALL: String = "all"
        const val LIMIT: Int = 30
        val COUNTRIES: List<UiOption> = listOf(
            UiOption(ALL, "Everywhere"),
            UiOption("DE", "Germany"),
            UiOption("RU", "Russia"),
            UiOption("US", "United States"),
            UiOption("GB", "United Kingdom"),
            UiOption("FR", "France"),
            UiOption("JP", "Japan"),
            UiOption("BR", "Brazil"),
        )
    }
}
