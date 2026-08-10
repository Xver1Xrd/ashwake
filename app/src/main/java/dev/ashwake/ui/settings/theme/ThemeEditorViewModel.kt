package dev.ashwake.ui.settings.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.data.settings.AppSettings
import dev.ashwake.ui.theme.ThemeSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Редактор темы.
 *
 * Одна точка правки на все настройки оформления: экран отдаёт целиком
 * изменённые настройки, а не двадцать отдельных вызовов. Так добавление
 * новой ручки не требует нового метода ни здесь, ни в хранилище.
 */
@HiltViewModel
class ThemeEditorViewModel @Inject constructor(
    private val settings: AppSettings
) : ViewModel() {

    val theme: StateFlow<ThemeSettings> = settings.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeSettings())

    fun update(transform: (ThemeSettings) -> ThemeSettings) {
        viewModelScope.launch { settings.setTheme(transform(theme.value)) }
    }

    fun reset() {
        viewModelScope.launch { settings.setTheme(ThemeSettings()) }
    }
}
