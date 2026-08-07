package dev.ashwake.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Начало суток. Отметка в 01:00 логически принадлежит вчерашнему дню —
 * иначе ночная активность рвёт стрики и обнуляет счётчики отказов.
 * Значение настраиваемое, здесь только умолчание.
 */
const val DEFAULT_DAY_START_HOUR: Int = 4

/**
 * Единственный источник «сейчас» в приложении. Ни один расчёт не зовёт
 * System.currentTimeMillis() напрямую — иначе движки нечем тестировать.
 */
interface AppClock {
    fun now(): Instant
    fun zone(): ZoneId

    /** Логическая дата «сегодня» с учётом смещённого начала суток. */
    fun today(dayStartHour: Int = DEFAULT_DAY_START_HOUR): LocalDate =
        toLogicalDate(now(), dayStartHour)

    /** Момент → логическая дата. Вынесено сюда, чтобы правило жило в одном месте. */
    fun toLogicalDate(instant: Instant, dayStartHour: Int = DEFAULT_DAY_START_HOUR): LocalDate {
        val local: LocalDateTime = LocalDateTime.ofInstant(instant, zone())
        return if (local.hour < dayStartHour) local.toLocalDate().minusDays(1)
        else local.toLocalDate()
    }
}

@Singleton
class SystemAppClock @Inject constructor() : AppClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Фиксированные часы для юнит-тестов движков. */
class FixedAppClock(
    private var instant: Instant,
    private val zone: ZoneId = ZoneId.of("UTC")
) : AppClock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zone
    fun set(newInstant: Instant) { instant = newInstant }
}
