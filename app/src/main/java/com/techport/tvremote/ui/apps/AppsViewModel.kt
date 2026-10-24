package com.techport.tvremote.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.techport.tvremote.data.apps.AppLinkSuggestion
import com.techport.tvremote.data.apps.AppLinkSuggestions
import com.techport.tvremote.domain.model.AppShortcut
import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.remote.RemoteController
import com.techport.tvremote.domain.repository.AppShortcutRepository
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppsUiState(
    /** All saved shortcuts in display order (favorites first). Search filtering happens in the UI. */
    val shortcuts: List<AppShortcut> = emptyList(),
    val suggestions: List<AppLinkSuggestion> = emptyList(),
    val availability: LaunchAvailability = LaunchAvailability.NOT_CONNECTED,
    val loaded: Boolean = false,
)

class AppsViewModel(
    private val repository: AppShortcutRepository,
    private val controller: RemoteController,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    val state: StateFlow<AppsUiState> =
        combine(repository.shortcuts, controller.state) { shortcuts, connection ->
            AppsUiState(
                shortcuts = shortcuts,
                suggestions = AppLinkSuggestions.notSaved(shortcuts),
                availability = launchAvailability(connection),
                loaded = true,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    /**
     * Sends the shortcut's link to the TV. Returns false (and sends nothing) when the TV can't
     * launch apps right now. The protocol does not confirm the launch, so neither does the UI.
     */
    fun launch(shortcut: AppShortcut): Boolean {
        if (launchAvailability(controller.state.value) != LaunchAvailability.READY) return false
        controller.send(RemoteCommand.LaunchApp(shortcut.link))
        viewModelScope.launch { repository.markUsed(shortcut.id, clock()) }
        return true
    }

    /** Creates ([id] null) or updates a shortcut. Returns the validation error, or null once saved. */
    fun save(id: String?, label: String, link: String): ShortcutInputError? {
        validateShortcut(label, link)?.let { return it }
        viewModelScope.launch {
            val existing = id?.let { repository.get(it) }
            val shortcut = existing?.copy(label = label.trim(), link = link.trim())
                ?: AppShortcut(id = newId(), label = label.trim(), link = link.trim())
            repository.upsert(shortcut)
        }
        return null
    }

    fun addSuggestion(suggestion: AppLinkSuggestion) {
        viewModelScope.launch { repository.upsert(AppShortcut(id = newId(), label = suggestion.label, link = suggestion.link)) }
    }

    fun setFavorite(shortcut: AppShortcut, favorite: Boolean) {
        viewModelScope.launch { repository.setFavorite(shortcut.id, favorite) }
    }

    fun move(shortcut: AppShortcut, delta: Int) {
        viewModelScope.launch { repository.move(shortcut.id, delta) }
    }

    fun delete(shortcut: AppShortcut) {
        viewModelScope.launch { repository.remove(shortcut.id) }
    }
}
