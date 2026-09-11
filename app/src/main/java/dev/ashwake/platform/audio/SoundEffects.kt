package dev.ashwake.platform.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Random
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.sin

/**
 * Процедурные звуковые эффекты для интерфейса.
 *
 * Синглтон: аудио-треки генерируются асинхронно в фоне один раз на всё приложение.
 * Главный поток никогда не блокируется операциями с AudioTrack.
 */
@Singleton
class SoundEffects @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var thudTrack: AudioTrack? = null

    @Volatile
    private var fireTrack: AudioTrack? = null

    init {
        scope.launch {
            runCatching {
                thudTrack = createStaticTrack(generateThudPcm())
                fireTrack = createStaticTrack(generateCampfirePcm())
            }
        }
    }

    /** Глухой плотный стук при заморозке привычки. */
    fun playThud() {
        playTrack(thudTrack)
    }

    /** Потрескивание и вспышка костра при тапе по огоньку. */
    fun playCampfire() {
        playTrack(fireTrack)
    }

    private fun playTrack(track: AudioTrack?) {
        scope.launch {
            runCatching {
                track?.let {
                    it.stop()
                    it.reloadStaticData()
                    it.play()
                }
            }
        }
    }

    private fun createStaticTrack(pcmData: ShortArray): AudioTrack? {
        if (pcmData.isEmpty()) return null
        val byteBuffer = ByteArray(pcmData.size * 2)
        for (i in pcmData.indices) {
            val v = pcmData[i].toInt()
            byteBuffer[i * 2] = (v and 0xFF).toByte()
            byteBuffer[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }

        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build()

        val format = AudioFormat.Builder()
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .build()

        return AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(byteBuffer.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build().apply {
                write(byteBuffer, 0, byteBuffer.size)
            }
    }

    companion object {
        private const val SAMPLE_RATE = 44100

        @Volatile
        private var instance: SoundEffects? = null

        fun get(context: Context): SoundEffects =
            instance ?: synchronized(this) {
                instance ?: SoundEffects(context.applicationContext).also { instance = it }
            }

        /** 70 мс: глубокий глухой стук (удар по льду / камню, 75Hz). */
        private fun generateThudPcm(): ShortArray {
            val count = (SAMPLE_RATE * 0.070).toInt()
            val result = ShortArray(count)
            val twoPi = 2.0 * Math.PI
            for (i in 0 until count) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / count
                val freq = 75.0 * (1.0 - progress * 0.4)
                val envelope = exp(-progress * 6.0)
                val sample = sin(twoPi * freq * t) * envelope
                result[i] = (sample * 24000).toInt().coerceIn(-32768, 32767).toShort()
            }
            return result
        }

        /** 160 мс: тёплый шелест пламени и щелчки искр костра. */
        private fun generateCampfirePcm(): ShortArray {
            val count = (SAMPLE_RATE * 0.160).toInt()
            val result = ShortArray(count)
            val random = Random(42)
            val twoPi = 2.0 * Math.PI
            for (i in 0 until count) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / count
                val flameTone = sin(twoPi * 180.0 * t) * 0.4
                val noise = (random.nextDouble() * 2.0 - 1.0) * 0.6
                val spark = if (i % 650 < 30) (random.nextDouble() * 2.0 - 1.0) * 1.5 else 0.0
                val envelope = sin(progress * Math.PI) * exp(-progress * 1.8)
                val sample = (flameTone + noise + spark) * envelope
                result[i] = (sample * 14000).toInt().coerceIn(-32768, 32767).toShort()
            }
            return result
        }
    }
}
