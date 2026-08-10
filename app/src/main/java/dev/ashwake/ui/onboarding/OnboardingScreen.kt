package dev.ashwake.ui.onboarding

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.ui.components.AshIcons
import dev.ashwake.ui.components.PrimaryButton
import dev.ashwake.ui.components.ScreenPadding
import dev.ashwake.ui.components.TextAction
import dev.ashwake.ui.components.rememberBreath
import dev.ashwake.ui.components.tappable
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import kotlinx.coroutines.launch

/**
 * Знакомство при первом запуске.
 *
 * Три экрана вместо пустого списка с одной кнопкой. Первый объясняет, чем
 * это приложение отличается от списка дел, второй даёт выбрать привычки из
 * готового каталога — он уже есть, и заводить первую привычку руками нет
 * причин, — третий рассказывает про персонажа, потому что иначе он выглядит
 * украшением.
 *
 * Пропустить можно с любого шага: знакомство, из которого нельзя выйти, —
 * это не помощь, а препятствие.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AshTheme.colors
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { PAGES })

    LaunchedEffect(state.finished) { if (state.finished) onDone() }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.accent.copy(alpha = 0.18f),
                        colors.background,
                        colors.background
                    )
                )
            )
    ) {
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextAction(text = "Пропустить", color = colors.text2, onClick = viewModel::skip)
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> IntroPage()
                1 -> HabitsPage(
                    categories = state.categories.map { it.title to it.habits },
                    picked = state.picked,
                    onToggle = viewModel::toggle
                )

                else -> CharacterPage()
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(PAGES) { index ->
                    val active = pager.currentPage == index
                    val width by animateFloatAsState(
                        targetValue = if (active) 22f else 7f,
                        label = "dot-$index"
                    )
                    Box(
                        Modifier
                            .height(7.dp)
                            .width(width.dp)
                            .background(
                                if (active) colors.accent else colors.surface3,
                                AshShapes.pill
                            )
                    )
                }
            }

            PrimaryButton(
                text = when (pager.currentPage) {
                    PAGES - 1 -> if (state.picked.isEmpty()) "Начать" else "Начать с ${state.picked.size}"
                    else -> "Дальше"
                },
                onClick = {
                    if (pager.currentPage == PAGES - 1) {
                        viewModel.finish()
                    } else {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    }
                }
            )

            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun IntroPage() {
    Page(
        icon = AshIcons.Sun,
        title = "Ashwake",
        text = "Это не ещё один список дел. Задачи, привычки и отказы живут " +
            "в одном месте, а за сделанное растёт персонаж — чтобы у усилий " +
            "был видимый след, а не только галочка."
    )
}

@Composable
private fun CharacterPage() {
    Page(
        icon = AshIcons.Person,
        title = "Персонаж растёт от дел",
        text = "Монеты приходят за выполненное, характеристики — за поведение: " +
            "силу нельзя купить, её надо натренировать. Одежду и снаряжение " +
            "можно купить в магазине на заработанное."
    )
}

/** Общая раскладка страницы знакомства: значок, заголовок, два абзаца. */
@Composable
private fun Page(icon: ImageVector, title: String, text: String) {
    val colors = AshTheme.colors
    val breath = rememberBreath()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(96.dp)
                .background(colors.accent.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size((44 * breath).dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = AshTheme.type.title1,
            color = colors.text,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = text,
            style = AshTheme.type.body,
            color = colors.text2,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Выбор первых привычек.
 *
 * Каталог уже есть в приложении, и первый шаг «заведите привычку руками»
 * отсекает ровно тех, кто ещё не понял, зачем она.
 */
@Composable
private fun HabitsPage(
    categories: List<Pair<String, List<Habit>>>,
    picked: Set<String>,
    onToggle: (Habit) -> Unit
) {
    val colors = AshTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "С чего начнём?",
            style = AshTheme.type.title2,
            color = colors.text
        )
        Text(
            text = "Выберите одну-две. Больше — соблазн, который через неделю " +
                "превращается в список невыполненного",
            style = AshTheme.type.subhead,
            color = colors.text2
        )

        categories.forEach { (title, habits) ->
            Text(
                text = title.uppercase(),
                style = AshTheme.type.caption,
                color = colors.text3,
                modifier = Modifier.padding(top = 6.dp)
            )
            habits.take(HABITS_PER_CATEGORY).forEach { habit ->
                val selected = habit.name in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) colors.accent.copy(alpha = 0.14f) else colors.surface1,
                            AshShapes.group
                        )
                        .border(
                            width = if (selected) 1.5.dp else 0.dp,
                            color = if (selected) colors.accent else Color.Transparent,
                            shape = AshShapes.group
                        )
                        .tappable(onClick = { onToggle(habit) })
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (selected) AshIcons.CheckCircle else AshIcons.Circle,
                        contentDescription = null,
                        tint = if (selected) colors.accent else colors.text3,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = habit.name,
                        style = AshTheme.type.body,
                        color = colors.text
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

private const val PAGES = 3

/** Сколько привычек показывать из каждой категории: остальные есть в каталоге. */
private const val HABITS_PER_CATEGORY = 4
