package space.wavedot.core.contract.ui

import space.wavedot.core.model.Track

sealed interface UiNode {
    data class Heading(val text: String) : UiNode

    data class Text(val text: String, val style: UiTextStyle = UiTextStyle.BODY) : UiNode

    data class Status(val text: String, val tone: UiTone = UiTone.INFO) : UiNode

    data class Tracks(val tracks: List<Track>) : UiNode

    data class Cards(val cards: List<UiCard>) : UiNode

    data class Button(
        val label: String,
        val action: UiAction,
        val style: UiButtonStyle = UiButtonStyle.PRIMARY,
        val icon: UiIcon? = null,
    ) : UiNode

    data class Toggle(
        val label: String,
        val checked: Boolean,
        val action: UiValueAction<Boolean>,
        val description: String? = null,
    ) : UiNode

    data class TextField(
        val label: String,
        val action: UiValueAction<String>,
        val value: String = "",
        val hint: String = "",
        val secret: Boolean = false,
        val submitLabel: String = "Save",
    ) : UiNode

    data class Choice(
        val label: String,
        val options: List<UiOption>,
        val selectedId: String?,
        val action: UiValueAction<String>,
    ) : UiNode

    data object Loading : UiNode
}

enum class UiTextStyle { TITLE, BODY, CAPTION }

enum class UiTone { INFO, SUCCESS, WARNING, ERROR }

enum class UiButtonStyle { PRIMARY, TONAL, TEXT }

data class UiOption(val id: String, val label: String)

data class UiCard(
    val title: String,
    val action: UiAction,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
)
