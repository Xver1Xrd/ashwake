package dev.ashwake.platform.blocking

import dev.ashwake.R
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.domain.model.blocking.BlockingVerdict
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Полноэкранный оверлей поверх заблокированного приложения (п. 7).
 *
 * Собран на View, а не на Compose: `ComposeView` в окне `WindowManager`
 * требует своего lifecycle-владельца и `SavedStateRegistry`, и ради экрана
 * из трёх текстов и двух кнопок это лишняя механика.
 *
 * Оверлей показывает, что осталось сделать, и даёт две кнопки: уйти
 * в приложение и экстренный обход с обратным отсчётом.
 */
@Singleton
class BlockingOverlay @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var view: View? = null
    private var countdownText: TextView? = null

    val isShowing: Boolean get() = view != null

    fun show(
        verdict: BlockingVerdict,
        onOpenApp: () -> Unit,
        onBypass: () -> Unit
    ) {
        if (view != null) return
        val manager = context.getSystemService<WindowManager>() ?: return

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(BACKGROUND_COLOR)
            setPadding(PADDING, PADDING, PADDING, PADDING)

            addView(title(verdict.ruleName ?: context.getString(R.string.blockingoverlay_prilozhenie_zablokirovano)))
            addView(subtitle(context.getString(R.string.blockingoverlay_snachala_vot_eto)))
            verdict.remaining.forEach { addView(item("· $it")) }

            addView(primaryButton(context.getString(R.string.blockingoverlay_otkryt_ashwake)) { onOpenApp() })
            addView(bypassButton(onBypass))
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // Оверлей забирает фокус: иначе набранное уходило бы в приложение под ним
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        runCatching { manager.addView(root, params) }
            .onSuccess { view = root }
    }

    fun hide() {
        val current = view ?: return
        runCatching { context.getSystemService<WindowManager>()?.removeView(current) }
        view = null
        countdownText = null
    }

    /** Обновление обратного отсчёта перед обходом. */
    fun updateCountdown(secondsLeft: Int) {
        countdownText?.text = if (secondsLeft > 0) {
            context.getString(R.string.blockingoverlay_ekstrennyy_obhod_cherez_1_s, secondsLeft)
        } else {
            context.getString(R.string.blockingoverlay_ekstrennyy_obhod)
        }
        countdownText?.isEnabled = secondsLeft <= 0
    }

    // --- сборка вида --------------------------------------------------------

    private fun title(text: String) = TextView(context).apply {
        this.text = text
        textSize = 22f
        setTextColor(TEXT_COLOR)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, PADDING / 2)
    }

    private fun subtitle(text: String) = TextView(context).apply {
        this.text = text
        textSize = 15f
        setTextColor(MUTED_COLOR)
        gravity = Gravity.CENTER
    }

    private fun item(text: String) = TextView(context).apply {
        this.text = text
        textSize = 17f
        setTextColor(TEXT_COLOR)
        gravity = Gravity.CENTER
        setPadding(0, PADDING / 6, 0, PADDING / 6)
    }

    private fun primaryButton(text: String, onClick: () -> Unit) = Button(context).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    /**
     * Кнопка обхода изначально выключена: пятнадцать секунд отсчёта — это
     * не наказание, а пауза, за которую порыв «просто гляну» часто проходит сам.
     */
    private fun bypassButton(onClick: () -> Unit) = Button(context).apply {
        text = context.getString(R.string.blockingoverlay_ekstrennyy_obhod)
        isEnabled = false
        setOnClickListener { onClick() }
        countdownText = this
    }

    private companion object {
        val BACKGROUND_COLOR = Color.parseColor("#F20D0B12")
        val TEXT_COLOR = Color.parseColor("#E8E0D0")
        val MUTED_COLOR = Color.parseColor("#9A93A8")
        const val PADDING = 64
    }
}
