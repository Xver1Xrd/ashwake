package dev.ashwake.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshTheme
import kotlinx.coroutines.launch

/**
 * Монета, летящая от выполненного дела к кошельку.
 *
 * Это единственная анимация, которая объясняет экономику приложения целиком:
 * связь «сделал дело → стало больше монет» иначе существует только в базе.
 * Поэтому она заметная и с дугой — прямая линия читается как техническая
 * подсказка, а дуга как брошенный предмет.
 *
 * Координаты берутся в пикселях корневого слоя: строка списка и счётчик
 * живут в разных ветках разметки и друг о друге ничего не знают.
 */
@Stable
class CoinFlightState {
    /** Куда лететь. Пока кошелёк не измерен, полёта нет. */
    internal var target: Offset? = null
        private set

    /** Откуда лететь и на сколько монет. Ноль или больше одного значения не бывает. */
    internal var flight by mutableStateOf<Flight?>(null)
        private set

    internal data class Flight(val id: Long, val from: Offset, val amount: Int)

    private var nextId = 0L

    /** Кошелёк сообщает своё место. Вызывается при каждом измерении. */
    fun setTarget(coordinates: LayoutCoordinates) {
        target = coordinates.boundsInRoot().center
    }

    /** Запустить полёт от точки [from]. Без известной цели просто ничего не делает. */
    fun launch(from: Offset, amount: Int = 1) {
        if (target == null) return
        nextId += 1
        flight = Flight(nextId, from, amount)
    }

    /**
     * Монета на подлёте к кошельку. По этому признаку кошелёк дёргается,
     * а персонаж кивает — иначе полёт заканчивается в никуда.
     */
    var landing by mutableStateOf(false)
        internal set

    internal fun finish() {
        flight = null
    }
}

@Composable
fun rememberCoinFlightState(): CoinFlightState = remember { CoinFlightState() }

/**
 * Слой полёта. Кладётся последним в `Box` экрана, поверх всего содержимого,
 * иначе монета уедет под список.
 */
@Composable
fun BoxScope.CoinFlightHost(state: CoinFlightState) {
    val colors = AshTheme.colors
    val density = LocalDensity.current
    val flight = state.flight
    val target = state.target
    val reduceMotion = AshTheme.reduceMotion

    if (flight == null || target == null) return

    // Мгновенно завершаем при выключенном движении: полёт — украшение,
    // и без него всё остальное обязано работать ровно так же
    if (reduceMotion) {
        LaunchedEffect(flight.id) { state.finish() }
        return
    }

    val progress = remember(flight.id) { Animatable(0f) }

    LaunchedEffect(flight.id) {
        launch {
            progress.animateTo(1f, tween(FLIGHT_MS))
            state.landing = true
            state.finish()
        }
    }

    // Отдача короткая: это подтверждение зачисления, а не второй праздник
    LaunchedEffect(state.landing) {
        if (state.landing) {
            kotlinx.coroutines.delay(BUMP_MS.toLong())
            state.landing = false
        }
    }

    val t = progress.value
    // Дуга: горизонталь линейно, вертикаль с подъёмом. Прямая линия читается
    // как служебная подсказка, дуга — как брошенный предмет
    val x = flight.from.x + (target.x - flight.from.x) * t
    val straightY = flight.from.y + (target.y - flight.from.y) * t
    val lift = LIFT_PX * (4f * t * (1f - t))
    val y = straightY - lift

    Box(
        Modifier
            .offset(
                x = with(density) { x.toDp() } - CoinSize / 2,
                y = with(density) { (y).toDp() } - CoinSize / 2
            )
            .size(CoinSize)
            .graphicsLayer {
                // К концу монета сжимается и гаснет: она не приземляется,
                // а «впитывается» в счётчик, который в этот момент дёргается
                val shrink = 1f - 0.45f * t
                scaleX = shrink
                scaleY = shrink
                alpha = if (t > 0.85f) (1f - t) / 0.15f else 1f
                rotationZ = 220f * t
            }
    ) {
        Icon(
            imageVector = AshIcons.Coins,
            contentDescription = null,
            tint = colors.warm,
            modifier = Modifier.size(CoinSize)
        )
    }
}

/**
 * Помечает элемент как кошелёк: он и есть цель полёта.
 * Место пересчитывается на каждом измерении, потому что список прокручивается.
 */
fun Modifier.coinFlightTarget(state: CoinFlightState): Modifier =
    onGloballyPositioned { state.setTarget(it) }

private val CoinSize = 20.dp
private const val FLIGHT_MS = 620
private const val LIFT_PX = 160f
private const val BUMP_MS = 160
