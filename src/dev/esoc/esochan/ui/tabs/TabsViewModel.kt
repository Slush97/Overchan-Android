package dev.esoc.esochan.ui.tabs

import androidx.lifecycle.ViewModel
import dev.esoc.esochan.common.MainApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reactive wrapper around [TabsState] for lifecycle-aware tab state observation.
 *
 * This ViewModel does NOT take over mutation logic from [TabsAdapter] — it only
 * exposes the current tab state as [StateFlow] so that future consumers (e.g.
 * RecyclerView adapter, Compose UI) can observe changes reactively.
 */
class TabsViewModel : ViewModel() {

    private val tabsState: TabsState
        get() = MainApplication.getInstance().tabsState

    private val _tabs = MutableStateFlow<List<TabModel>>(emptyList())
    val tabs: StateFlow<List<TabModel>> = _tabs.asStateFlow()

    private val _selectedPosition = MutableStateFlow(TabModel.POSITION_NEWTAB)
    val selectedPosition: StateFlow<Int> = _selectedPosition.asStateFlow()

    init {
        syncFromState()
    }

    /**
     * Called by [TabsAdapter] after mutations to sync flows.
     * Serialization is already handled by TabsAdapter, so [serialize] is
     * accepted for API symmetry but not used here.
     */
    fun notifyTabsChanged(@Suppress("UNUSED_PARAMETER") serialize: Boolean = false) {
        syncFromState()
    }

    /**
     * Called from the broadcast receiver when [TabsTrackerService] updates
     * unread counts. Re-emits the tab list so observers pick up changes.
     */
    fun refreshFromTrackerService() {
        syncFromState()
    }

    private fun syncFromState() {
        _tabs.value = ArrayList(tabsState.tabsArray)
        _selectedPosition.value = tabsState.position
    }
}
