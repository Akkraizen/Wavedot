package space.wavedot.core.contract

import space.wavedot.core.model.Cursor
import space.wavedot.core.model.Entity
import space.wavedot.core.model.MediaId
import space.wavedot.core.model.Page

interface EntityProvider<out T : Entity> {
    suspend fun get(id: MediaId): T?

    suspend fun getMany(ids: List<MediaId>): List<T> = ids.mapNotNull { get(it) }
}

interface PagedQuery<in Q, out T> {
    suspend fun page(query: Q, cursor: Cursor? = null, limit: Int = DEFAULT_PAGE_SIZE): Page<T>

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 50
    }
}
