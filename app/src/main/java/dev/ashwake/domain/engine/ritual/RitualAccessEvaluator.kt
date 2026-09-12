package dev.ashwake.domain.engine.ritual

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** Начало вечернего ритуала: 20:00 (8 вечера). */
const val RITUAL_START_HOUR = 20

/**
 * Статус доступности вечернего ритуала.
 */
sealed interface RitualAccessStatus {
    /**
     * Окно открыто (с 20:00 до [dayStartHour] утра), ритуал за [targetDate] ещё не заполнен.
     */
    data class Available(
        val targetDate: LocalDate,
        val windowEnd: LocalDateTime
    ) : RitualAccessStatus

    /**
     * Ритуал за [targetDate] уже заполнен. Повторно пройти нельзя — только на следующий день.
     */
    data class AlreadyCompleted(
        val targetDate: LocalDate,
        val nextAvailableAt: LocalDateTime
    ) : RitualAccessStatus

    /**
     * Ещё не наступило 20:00. Ритуал откроется вечером в [opensAt].
     */
    data class TooEarly(
        val targetDate: LocalDate,
        val opensAt: LocalDateTime,
        val dayStartHour: Int
    ) : RitualAccessStatus
}

/**
 * Оценка доступности вечернего ритуала (п. 9).
 *
 * Правила:
 * 1. Ритуал доступен только в интервале с 20:00 (8 вечера) до [dayStartHour] (начала нового дня, по умолчанию 4 утра).
 * 2. Если ритуал за целевой день уже пройден, повторно ответить нельзя до следующего дня (до 20:00 следующего дня).
 * 3. Вне интервала (с [dayStartHour] до 20:00) ритуал закрыт и откроется сегодня в 20:00.
 *
 * Чистый класс без зависимостей от Android: легко тестируется юнит-тестами.
 */
@Singleton
class RitualAccessEvaluator @Inject constructor() {

    fun evaluate(
        currentDateTime: LocalDateTime,
        dayStartHour: Int,
        hasCompletedReview: (LocalDate) -> Boolean
    ): RitualAccessStatus {
        val currentDate = currentDateTime.toLocalDate()
        val currentHour = currentDateTime.hour

        val isNightTail = dayStartHour > 0 && currentHour < dayStartHour
        val isEvening = currentHour >= RITUAL_START_HOUR
        val isWithinWindow = isEvening || isNightTail

        // Целевой день, за который подводится итог
        val targetDate = if (isNightTail) currentDate.minusDays(1) else currentDate

        if (!isWithinWindow) {
            val opensAt = currentDate.atTime(RITUAL_START_HOUR, 0)
            return RitualAccessStatus.TooEarly(
                targetDate = targetDate,
                opensAt = opensAt,
                dayStartHour = dayStartHour
            )
        }

        // Мы внутри окна [20:00 .. dayStartHour]
        if (hasCompletedReview(targetDate)) {
            // Ритуал уже пройден! Следующий откроется только в 20:00 следующего дня
            val nextAvailableAt = targetDate.plusDays(1).atTime(RITUAL_START_HOUR, 0)
            return RitualAccessStatus.AlreadyCompleted(
                targetDate = targetDate,
                nextAvailableAt = nextAvailableAt
            )
        }

        // Окно открыто и ритуал ещё не заполнен
        val windowEnd = if (dayStartHour == 0) {
            currentDate.atTime(LocalTime.MAX)
        } else {
            targetDate.plusDays(1).atTime(dayStartHour, 0)
        }

        return RitualAccessStatus.Available(
            targetDate = targetDate,
            windowEnd = windowEnd
        )
    }
}
