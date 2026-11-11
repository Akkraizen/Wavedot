package space.wavedot.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AppearanceViewModel(backend: SettingsBackend) : ViewModel() {
    val accentId: StateFlow<String?> = backend.accentId.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
