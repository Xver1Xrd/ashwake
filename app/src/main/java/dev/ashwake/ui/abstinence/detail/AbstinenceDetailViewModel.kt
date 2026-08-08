package dev.ashwake.ui.abstinence.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.model.abstinence.CravingEvent
import dev.ashwake.domain.repository.abstinence.AbstinenceDetail
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.repository.abstinence.AbstinenceRepository
import dev.ashwake.domain.repository.character.CharacterRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AbstinenceDetailViewModel @Inject constructor(
    private val abstinences: AbstinenceRepository,
    private val character: CharacterRepository,
    private val clock: AppClock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val id: Long = savedStateHandle.get<String>(ARG_ID)?.toLongOrNull() ?: 0L
    private val ticker = MutableStateFlow(clock.now())

    val detail: StateFlow<AbstinenceDetail?> =
        ticker.flatMapLatest { now -> abstinences.observeDetail(id, now) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** id незакрытого эпизода тяги: по нему проставляется исход после дыхания. */
    private val _pendingCravingId = MutableStateFlow<Long?>(null)
    val pendingCravingId: StateFlow<Long?> = _pendingCravingId.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(1_000L)
                ticker.value = clock.now()
            }
        }
    }

    // --- срыв --------------------------------------------------------------

    fun registerRelapse(reasonId: Long?, note: String?, at: Instant?) {
        viewModelScope.launch {
            abstinences.registerRelapse(id, reasonId, note, at ?: clock.now())
        }
    }

    fun undoRelapse(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch { onResult(abstinences.undoRelapse(id)) }
    }

    // --- тяга --------------------------------------------------------------

    /**
     * Фиксация эпизода в момент нажатия «Тяжело», до того как человек решит,
     * выстоял он или нет. Иначе в статистику попадали бы только успехи,
     * и аналитика показывала бы неправдоподобно красивую картину.
     */
    fun startCraving(intensity: Int, triggerId: Long?) {
        viewModelScope.launch {
            val now = clock.now()
            val local = now.atZone(clock.zone() ?: ZoneId.systemDefault())
            val eventId = abstinences.recordCraving(
                CravingEvent(
                    abstinenceId = id,
                    at = now,
                    hourOfDay = local.hour,
                    dayOfWeek = local.dayOfWeek.value,
                    intensity = intensity,
                    triggerId = triggerId
                )
            )
            _pendingCravingId.value = eventId
        }
    }

    fun finishCraving(resisted: Boolean, durationSeconds: Int?, note: String?) {
        val eventId = _pendingCravingId.value ?: return
        viewModelScope.launch {
            abstinences.updateCravingOutcome(eventId, resisted, durationSeconds, note)
            // Награда только за переждённую тягу: платить за срыв бессмысленно
            if (resisted) {
                character.grantReward(
                    RewardContext(
                        source = RewardSource.CRAVING_RESISTED,
                        time = clock.now().atZone(clock.zone()).toLocalTime()
                    ),
                    refId = id.toString()
                )
                character.grantStatPoints(StatSource.CRAVING_RESISTED, refId = id.toString())
            }
            _pendingCravingId.value = null
        }
    }

    fun cancelCraving() { _pendingCravingId.value = null }

    // --- настройки отказа --------------------------------------------------

    fun acknowledgeWarning() {
        viewModelScope.launch { abstinences.acknowledgeSubstanceWarning(id) }
    }

    fun addMilestone(days: Int, title: String, userText: String?) {
        viewModelScope.launch { abstinences.addCustomMilestone(id, days, title, userText) }
    }

    fun setMotivation(text: String?) {
        viewModelScope.launch {
            val current = detail.value?.abstinence ?: return@launch
            abstinences.update(current.copy(motivationText = text?.takeIf { it.isNotBlank() }))
        }
    }

    fun setSubstitutes(texts: List<String>) {
        viewModelScope.launch { abstinences.setSubstitutes(id, texts) }
    }

    companion object {
        const val ARG_ID = "abstinenceId"
    }
}
