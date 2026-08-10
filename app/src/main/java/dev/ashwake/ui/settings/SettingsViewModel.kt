package dev.ashwake.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.DEFAULT_DAY_START_HOUR
import dev.ashwake.data.settings.Appearance
import dev.ashwake.data.settings.AppSettings
import dev.ashwake.domain.model.tasks.TimeboxSettings
import dev.ashwake.ui.theme.AccentColor
import dev.ashwake.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettings
) : ViewModel() {

    val timebox: StateFlow<TimeboxSettings> = settings.timebox
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimeboxSettings())

    val dayStartHour: StateFlow<Int> = settings.dayStartHour
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_DAY_START_HOUR)

    val useCalendar: StateFlow<Boolean> = settings.useSystemCalendar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val appearance: StateFlow<Appearance> = settings.appearance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Appearance())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setAccent(accent: AccentColor) {
        viewModelScope.launch { settings.setAccent(accent) }
    }

    fun setDayStartHour(hour: Int) {
        viewModelScope.launch { settings.setDayStartHour(hour) }
    }

    fun setWorkStart(hour: Int) {
        viewModelScope.launch {
            val current = settings.timebox.first()
            settings.setWorkHours(hour * 60, current.workEndMinute)
        }
    }

    fun setWorkEnd(hour: Int) {
        viewModelScope.launch {
            val current = settings.timebox.first()
            settings.setWorkHours(current.workStartMinute, hour * 60)
        }
    }

    fun setBuffer(minutes: Int) {
        viewModelScope.launch { settings.setBuffer(minutes) }
    }

    fun setLunch(enabled: Boolean, hour: Int? = null) {
        viewModelScope.launch { settings.setLunch(enabled, hour?.times(60)) }
    }

    fun setUseCalendar(enabled: Boolean) {
        viewModelScope.launch { settings.setUseSystemCalendar(enabled) }
    }
}
