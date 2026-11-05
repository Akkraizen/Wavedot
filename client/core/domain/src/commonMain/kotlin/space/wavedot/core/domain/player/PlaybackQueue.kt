package space.wavedot.core.domain.player

import space.wavedot.core.model.Track
import kotlin.random.Random

data class PlaybackQueue(
    val tracks: List<Track> = emptyList(),
    val order: List<Int> = emptyList(),
    val position: Int = -1,
    val repeat: RepeatMode = RepeatMode.OFF,
    val shuffled: Boolean = false,
) {
    val current: Track? get() = order.getOrNull(position)?.let(tracks::getOrNull)

    val orderedTracks: List<Track> get() = order.map(tracks::get)

    val isEmpty: Boolean get() = order.isEmpty()

    fun next(auto: Boolean): PlaybackQueue? = when {
        isEmpty -> null
        auto && repeat == RepeatMode.ONE -> this
        position < order.lastIndex -> copy(position = position + 1)
        repeat != RepeatMode.OFF -> copy(position = 0)
        else -> null
    }

    fun previous(): PlaybackQueue = when {
        isEmpty -> this
        position > 0 -> copy(position = position - 1)
        repeat != RepeatMode.OFF -> copy(position = order.lastIndex)
        else -> copy(position = 0)
    }

    fun skipTo(orderIndex: Int): PlaybackQueue {
        require(orderIndex in order.indices) { "Queue index out of range: $orderIndex" }
        return copy(position = orderIndex)
    }

    fun withRepeat(mode: RepeatMode): PlaybackQueue = copy(repeat = mode)

    fun withShuffle(enabled: Boolean, random: Random = Random.Default): PlaybackQueue {
        if (enabled == shuffled || isEmpty) return copy(shuffled = enabled)
        val currentIndex = order[position]
        return if (enabled) {
            val rest = order.filter { it != currentIndex }.shuffled(random)
            copy(order = listOf(currentIndex) + rest, position = 0, shuffled = true)
        } else {
            val restored = order.sorted()
            copy(order = restored, position = restored.indexOf(currentIndex), shuffled = false)
        }
    }

    fun append(newTracks: List<Track>): PlaybackQueue {
        if (newTracks.isEmpty()) return this
        val start = tracks.size
        val added = newTracks.indices.map { start + it }
        val base = copy(tracks = tracks + newTracks, order = order + added)
        return if (position < 0) base.copy(position = 0) else base
    }

    fun playNext(track: Track): PlaybackQueue {
        val index = tracks.size
        val insertAt = (position + 1).coerceAtLeast(0)
        val newOrder = order.toMutableList().apply { add(insertAt, index) }
        return copy(tracks = tracks + track, order = newOrder, position = position.coerceAtLeast(0))
    }

    fun remove(orderIndex: Int): PlaybackQueue {
        require(orderIndex in order.indices) { "Queue index out of range: $orderIndex" }
        val newOrder = order.toMutableList().apply { removeAt(orderIndex) }
        val newPosition = when {
            newOrder.isEmpty() -> -1
            orderIndex < position -> position - 1
            else -> position.coerceAtMost(newOrder.lastIndex)
        }
        return copy(order = newOrder, position = newPosition)
    }

    companion object {
        fun of(
            tracks: List<Track>,
            startIndex: Int = 0,
            shuffle: Boolean = false,
            repeat: RepeatMode = RepeatMode.OFF,
            random: Random = Random.Default,
        ): PlaybackQueue {
            if (tracks.isEmpty()) return PlaybackQueue(repeat = repeat)
            require(startIndex in tracks.indices) { "Start index out of range: $startIndex" }
            val linear = PlaybackQueue(tracks, tracks.indices.toList(), startIndex, repeat, shuffled = false)
            return if (shuffle) linear.withShuffle(true, random) else linear
        }
    }
}
