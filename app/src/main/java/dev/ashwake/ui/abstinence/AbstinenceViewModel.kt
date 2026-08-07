package dev.ashwake.ui.abstinence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.model.abstinence.Abstinence
import dev.ashwake.domain.model.abstinence.AbstinenceMode
import dev.ashwake.domain.model.abstinence.Attempt
import dev.ashwake.domain.model.abstinence.Baseline
import dev.ashwake.domain.repository.abstinence.AbstinenceRepository
import dev.ashwake.domain.repository.abstinence.AbstinenceWithStats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Список отказов.
 *
 * Счётчик тикает каждую секунду, поэтому «сейчас» — это отдельный поток,
 * а не значение, взятое при подписке: иначе крупные цифры на карточках
 * замерли бы до следующего изменения базы.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AbstinenceViewModel @Inject constructor(
    private val abstinences: AbstinenceRepository,
    private val clock: AppClock
) : ViewModel() {

    private val ticker = MutableStateFlow(clock.now())

    val items: StateFlow<List<AbstinenceWithStats>> =
        ticker.flatMapLatest { now -> abstinences.observeAll(now) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            abstinences.ensureBuiltinData()
            while (true) {
                delay(TICK_MILLIS)
                ticker.value = clock.now()
            }
        }
    }

    /**
     * Создание отказа. Старт можно указать задним числом — «бросил три дня назад»
     * встречается чаще, чем «бросаю прямо сейчас».
     */
    fun create(
        name: String,
        mode: AbstinenceMode,
        startedAt: Instant,
        motivationText: String?,
        baseline: Baseline?,
        substitutes: List<String>
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            abstinences.create(
                Abstinence(
                    name = name.trim(),
                    mode = mode,
                    motivationText = motivationText?.takeIf { it.isNotBlank() },
                    baseline = baseline,
                    attempts = listOf(
                        Attempt(abstinenceId = 0, ordinal = 1, startedAt = startedAt)
                    ),
                    substitutes = substitutes.filter { it.isNotBlank() }
                        .mapIndexed { index, text ->
                            dev.ashwake.domain.model.abstinence.Substitute(
                                abstinenceId = 0, text = text, position = index
                            )
                        }
                )
            )
        }
    }

    fun archive(id: Long) {
        viewModelScope.launch { abstinences.archive(id) }
    }

    private companion object {
        /** Секунда: счётчик показывает секунды, чаще обновлять незачем. */
        const val TICK_MILLIS = 1_000L
    }
}
