package dev.ashwake.platform.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Перерисовка виджетов после изменений в приложении.
 *
 * Glance перерисовывает виджет сам только когда меняется его собственное
 * состояние или система решит его обновить. Данные же живут в базе, и без
 * явного пинка виджет на экране показывал закрытую полчаса назад задачу как
 * открытую — то есть врал ровно в том месте, ради которого его и ставят.
 *
 * Обновление идёт в своей области, а не в области вызывающего: отметка
 * задачи не должна ждать перерисовку виджета, а отмена вызывающей корутины
 * не должна оставлять виджет со старыми данными.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Виджеты задач. Вызывается после любой правки задачи. */
    fun refreshTasks() = refresh { TasksWidget().updateAll(context) }

    /** Виджеты привычек. */
    fun refreshHabits() = refresh { HabitsWidget().updateAll(context) }

    /** Виджеты отказов. */
    fun refreshAbstinence() = refresh { AbstinenceWidget().updateAll(context) }

    /** Виджет персонажа: монеты и экипировка меняются от любого начисления. */
    fun refreshCharacter() = refresh { CharacterWidget().updateAll(context) }

    /** Всё сразу: после восстановления из архива меняется вообще всё. */
    fun refreshAll() {
        refreshTasks()
        refreshHabits()
        refreshAbstinence()
        refreshCharacter()
    }

    /**
     * Виджета может не быть на экране вовсе, и тогда Glance бросает. Это не
     * ошибка приложения, поэтому падать здесь нечему — только записать.
     */
    private fun refresh(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onFailure { Log.d(TAG, "Виджет не обновлён", it) }
        }
    }

    private companion object {
        const val TAG = "WidgetRefresher"
    }
}
