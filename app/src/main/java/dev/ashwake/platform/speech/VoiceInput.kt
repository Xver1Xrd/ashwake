package dev.ashwake.platform.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Что происходит с распознаванием. */
sealed interface VoiceResult {
    data object Listening : VoiceResult
    data class Partial(val text: String) : VoiceResult
    data class Final(val text: String) : VoiceResult
    data class Failed(val reason: String) : VoiceResult
}

/**
 * Голосовой ввод для быстрого добавления (п. 11).
 *
 * Распознанный текст уходит в тот же [dev.ashwake.domain.engine.nlp.QuickInputParser],
 * что и напечатанный: «купить молоко завтра в шесть» разбирается на дату
 * и время так же, как набранное руками.
 *
 * Оформлено потоком, а не колбэком: распознавание приходит частями,
 * и поле ввода должно наполняться по мере речи, а не ждать конца фразы.
 */
@Singleton
class VoiceInput @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun listen(): Flow<VoiceResult> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(VoiceResult.Failed("Распознавание речи недоступно"))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ru", "RU").toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(VoiceResult.Listening)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let { trySend(VoiceResult.Partial(it)) }
            }

            override fun onResults(results: Bundle?) {
                val text = firstResult(results)
                if (text.isNullOrBlank()) {
                    trySend(VoiceResult.Failed("Ничего не распознано"))
                } else {
                    trySend(VoiceResult.Final(text))
                }
                close()
            }

            override fun onError(error: Int) {
                trySend(VoiceResult.Failed(describeError(error)))
                close()
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        recognizer.startListening(intent)

        awaitClose {
            // Освобождать распознаватель обязательно: иначе микрофон остаётся
            // занятым и следующий запуск молча ничего не слышит
            recognizer.stopListening()
            recognizer.destroy()
        }
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun describeError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Ошибка записи звука"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            // Офлайн-распознавание есть не на всех устройствах — говорим прямо
            "Распознавание требует языкового пакета на устройстве"
        SpeechRecognizer.ERROR_NO_MATCH -> "Ничего не распознано"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Не расслышал"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознавание уже идёт"
        else -> "Не получилось распознать"
    }
}
