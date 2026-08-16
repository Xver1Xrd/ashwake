package dev.ashwake.core.result

/**
 * Результат операции с разбором причины ошибки (п. 1a).
 *
 * Мягкий вариант `Result` для UI-слоя: вместо класса исключения —
 * человекочитаемая строка, которую можно показать в снакбаре.
 * Причина исключения не теряется, но не протекает в интерфейс.
 */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val reason: String, val cause: Throwable? = null) : Outcome<Nothing>
}

/** Обёртка над try/catch: всё, что упало, становится Failure с текстом. */
inline fun <T> outcomeOf(block: () -> T): Outcome<T> =
    try {
        Outcome.Success(block())
    } catch (e: Throwable) {
        Outcome.Failure(e.message ?: "Неизвестная ошибка", e)
    }

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

fun <T> Outcome<T>.getOrDefault(default: T): T = getOrNull() ?: default

val <T> Outcome<T>.isSuccess: Boolean get() = this is Outcome.Success

val <T> Outcome<T>.isFailure: Boolean get() = this is Outcome.Failure

inline fun <T> Outcome<T>.onSuccess(block: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) block(value)
    return this
}

inline fun <T> Outcome<T>.onFailure(block: (reason: String, cause: Throwable?) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) block(reason, cause)
    return this
}

inline fun <T> Outcome<T>.map(transform: (T) -> T): Outcome<T> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}
