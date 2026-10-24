package com.techport.tvremote.ui.keyboard

import com.techport.tvremote.domain.model.AppSettings
import com.techport.tvremote.domain.model.TvTextField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/**
 * Emits the focused field each time the TV *starts* showing a text field (null → non-null)
 * while [AppSettings.autoOpenKeyboard] is on. Collect it on the remote screen and navigate to
 * the keyboard.
 *
 * The value present when collection starts is only a baseline and never emits: otherwise
 * coming back from the keyboard while the TV field is still focused would reopen it at once.
 */
fun keyboardOpenRequests(
    textField: Flow<TvTextField?>,
    settings: StateFlow<AppSettings>,
): Flow<TvTextField> = flow {
    var first = true
    var previous: TvTextField? = null
    textField.collect { field ->
        if (!first && previous == null && field != null && settings.value.autoOpenKeyboard) emit(field)
        first = false
        previous = field
    }
}
