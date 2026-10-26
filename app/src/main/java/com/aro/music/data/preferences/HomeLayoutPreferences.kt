package com.aro.music.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class HomeSection { RECENT, SPEED_DIAL, DAILY_MIX, YOUR_MIX, DISCOVER, STATS }
data class HomeLayout(val order: List<HomeSection> = HomeSection.entries.toList(), val hidden: Set<HomeSection> = emptySet())

internal fun normalizeHomeLayout(order: List<String>, hidden: Set<String>): HomeLayout {
    val sections = HomeSection.entries.associateBy { it.name }
    val normalized = (order.mapNotNull(sections::get) + HomeSection.entries).distinct()
    val hiddenSections = hidden.mapNotNull(sections::get).toSet()
    return HomeLayout(normalized, if (hiddenSections.size == sections.size) hiddenSections - normalized.first() else hiddenSections)
}

@Singleton
class HomeLayoutPreferences @Inject constructor(private val store: DataStore<Preferences>) {
    private val orderKey = stringPreferencesKey("home_section_order_v1")
    private val hiddenKey = stringSetPreferencesKey("home_hidden_sections_v1")
    val layout = store.data.map {
        normalizeHomeLayout(it[orderKey]?.split(',').orEmpty(), it[hiddenKey].orEmpty())
    }.distinctUntilChanged()
    suspend fun save(layout: HomeLayout) {
        val normalized = normalizeHomeLayout(layout.order.map { it.name }, layout.hidden.map { it.name }.toSet())
        store.edit {
            it[orderKey] = normalized.order.joinToString(",") { section -> section.name }
            it[hiddenKey] = normalized.hidden.map { section -> section.name }.toSet()
        }
    }
}
