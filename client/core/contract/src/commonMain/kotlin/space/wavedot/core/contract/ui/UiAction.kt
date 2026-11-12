package space.wavedot.core.contract.ui

import space.wavedot.core.model.Track

fun interface UiAction {
    suspend fun run(): UiEffect
}

fun interface UiValueAction<T> {
    suspend fun run(value: T): UiEffect
}

sealed interface UiEffect {
    data object None : UiEffect

    data class Message(val text: String) : UiEffect

    data class Play(val tracks: List<Track>, val startIndex: Int = 0) : UiEffect
}
