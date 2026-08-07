package dev.ashwake

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.nlp.QuickInputParser
import dev.ashwake.domain.model.tasks.Tag
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.usecase.tasks.SaveTaskUseCase
import dev.ashwake.ui.navigation.AshwakeRoot
import dev.ashwake.ui.theme.AshwakeTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var saveTask: SaveTaskUseCase
    @Inject lateinit var parser: QuickInputParser
    @Inject lateinit var clock: AppClock

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        handleShare(intent)

        setContent {
            AshwakeTheme {
                AshwakeRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * «Поделиться» текстом или ссылкой из другого приложения (п. 11).
     * Текст прогоняется через тот же парсер, что и быстрый ввод, а ссылка
     * кладётся в отдельное поле, чтобы не мусорить в названии.
     */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (shared.isEmpty()) return
        // Гасим интент, иначе поворот экрана создаст задачу повторно
        intent.action = null

        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()
        val link = LINK_REGEX.find(shared)?.value
        val text = subject?.takeIf { it.isNotBlank() }
            ?: shared.replace(link.orEmpty(), "").trim().ifBlank { link.orEmpty() }

        lifecycleScope.launch {
            val parsed = parser.parse(text, clock.today())
            saveTask(
                Task(
                    title = parsed.title.ifBlank { text.take(120) },
                    dueDate = parsed.date,
                    dueTime = parsed.time,
                    estimateMinutes = parsed.estimateMinutes,
                    sourceLink = link,
                    tags = parsed.tagNames.map { Tag(name = it, color = 0) }
                )
            )
            Toast.makeText(this@MainActivity, "Задача создана", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        val LINK_REGEX = Regex("https?://\\S+")
    }
}
