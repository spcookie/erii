package uesugi.core.component.search

import uesugi.common.extend.ISearch
import uesugi.common.toolkit.ConfigHolder
import java.util.*

object SearchManager {
    private val providers: Map<String, ISearch> by lazy {
        ServiceLoader.load(ISearch::class.java).associateBy { it.id }
    }

    fun get(): ISearch {
        val id = ConfigHolder.getSearchProvider()
        return providers[id] ?: error("No search provider found for id: $id, available: ${providers.keys}")
    }
}