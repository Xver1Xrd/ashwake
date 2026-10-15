package dev.ashwake.ui.onboarding

import dev.ashwake.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.draw.clip
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
 * Полное интерактивное промо-знакомство при первом запуске (все функции Ashwake).
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextAction(
                text = stringResource(R.string.onboarding_propustit),
                color = colors.text2,
                onClick = viewModel::skip
            )
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> TasksFeaturePage()
                2 -> HabitsFeaturePage()
                3 -> AbstinenceFeaturePage()
                4 -> FocusAndRoutinesPage()
                5 -> ToolsAndPrivacyPage()
                else -> HabitsPage(
                    categories = state.categories.map { it.title to it.habits },
                    picked = state.picked,
                    onToggle = viewModel::toggle
                )
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
            // Индикаторы страниц
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
                    PAGES - 1 -> if (state.picked.isEmpty()) stringResource(R.string.onboarding_nachat) else stringResource(R.string.onboarding_nachat_s_1_s, state.picked.size)
                    else -> stringResource(R.string.onboarding_dalshe)
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

// ---------------------------------------------------------------------------
// Экраны презентации функций
// ---------------------------------------------------------------------------

@Composable
private fun WelcomePage() {
    FeatureOverviewPage(
        icon = AshIcons.AutoAwesome,
        title = "Добро пожаловать в Ashwake",
        subtitle = "Персональная система для порядка, фокуса и дисциплины — без шума и рекламы.",
        features = listOf(
            FeatureItem(AshIcons.CheckCircle, "Умные задачи", "Понимание дат, времени, подзадач и сложности прямо из текста"),
            FeatureItem(AshIcons.Flame, "Привычки и живой огонёк", "Дисциплина каждый день с защитой стрика от выгорания"),
            FeatureItem(AshIcons.Prohibit, "Отказ от зависимостей", "Точные счётчики чистоты, экстренная помощь и экономия"),
            FeatureItem(AshIcons.Timer, "Фокус и рутины", "Помодоро, утренние привычки и вечерний ритуал дня")
        )
    )
}

@Composable
private fun TasksFeaturePage() {
    FeatureOverviewPage(
        icon = AshIcons.CheckCircle,
        title = "Быстрый ввод и умные задачи",
        subtitle = "Просто напишите фразу или надиктуйте голосом — Ashwake сделает остальное.",
        features = listOf(
            FeatureItem(AshIcons.AutoAwesome, "Быстрый ввод задачи (NLP)", "«Завтра в 17:30 прочесть книгу 40м p1 #учёба: глава 1, глава 2» — парсер сам расставит все поля"),
            FeatureItem(AshIcons.Mic, "Голосовой ввод", "Надиктуйте задачу на ходу без рук: время, важность и подзадачи распознаются автоматически"),
            FeatureItem(AshIcons.Target, "Сложность и подзадачи", "Оценка усилий от 1 до 5 точек, чеклисты и вложенные шаги выполнения"),
            FeatureItem(AshIcons.GridView, "Матрица и таймбоксинг", "Сортировка Эйзенхауэра (важно/срочно) и визуальное планирование дня блоками времени")
        )
    )
}

@Composable
private fun HabitsFeaturePage() {
    FeatureOverviewPage(
        icon = AshIcons.Flame,
        title = "Привычки и живой огонёк",
        subtitle = "Формируйте полезные привычки и поддерживайте огонь активности.",
        features = listOf(
            FeatureItem(AshIcons.Flame, "Огонёк активности", "Пламя разгорается за 3 дня дисциплины и мотивирует не сбавлять темп"),
            FeatureItem(AshIcons.AcUnit, "Заморозка дней", "Отпуск или форс-мажор не сломают ваш накопленный рекорд"),
            FeatureItem(AshIcons.Repeat, "Гибкие графики", "Ежедневно, по выбранным дням недели или N раз в неделю"),
            FeatureItem(AshIcons.BarChart, "Глубокая аналитика", "Наглядные тепловые карты, динамика серий и статистика")
        )
    )
}

@Composable
private fun AbstinenceFeaturePage() {
    FeatureOverviewPage(
        icon = AshIcons.Prohibit,
        title = "Отказ от зависимостей",
        subtitle = "Осознанное преодоление вредных привычек день за днём.",
        features = listOf(
            FeatureItem(AshIcons.Timer, "Счётчики чистоты", "Таймеры трезвости в реальном времени и фиксация рекордов"),
            FeatureItem(AshIcons.BeachAccess, "Кнопка «Сейчас тяжело»", "Дыхательные практики и список здоровых замен, когда накрыло"),
            FeatureItem(AshIcons.Coins, "Подсчёт сбережений", "Наглядный счётчик сэкономленных денег и сохранённого времени"),
            FeatureItem(AshIcons.Check, "Мягкий режим", "Срыв не обнуляет весь прогресс, а начисляет штрафные дни")
        )
    )
}

@Composable
private fun FocusAndRoutinesPage() {
    FeatureOverviewPage(
        icon = AshIcons.Timer,
        title = "Фокус и ритуалы дня",
        subtitle = "Инструменты для глубокой концентрации и гармоничного завершения дня.",
        features = listOf(
            FeatureItem(AshIcons.Timer, "Помодоро и таймеры", "Настраиваемые интервалы работы и отдыха для глубокого погружения"),
            FeatureItem(AshIcons.Moon, "Вечерний ритуал", "Осознанное закрытие дня, разгрузка мыслей и подготовка ко сну"),
            FeatureItem(AshIcons.Sun, "Утренние рутины", "Пошаговые сценарии для энергичного и спокойного старта дня"),
            FeatureItem(AshIcons.Block, "Блокировка отвлечений", "Ограничение доступа к соцсетям на время фокусировки")
        )
    )
}

@Composable
private fun ToolsAndPrivacyPage() {
    FeatureOverviewPage(
        icon = AshIcons.Lock,
        title = "Виджеты, бэкапы и приватность",
        subtitle = "Ваши данные под вашим полным контролем — всегда и везде.",
        features = listOf(
            FeatureItem(AshIcons.GridView, "Виджеты на рабочий стол", "Быстрый просмотр дел, отметка привычек и таймеры прямо с домашнего экрана Android"),
            FeatureItem(AshIcons.Archive, "Полные резервные копии", "Сохранение всех данных, времени выполнения задач и истории. Шифрование архивов паролем"),
            FeatureItem(AshIcons.Export, "Шеринг карточек и CSV", "Экспорт списков задач в виде красивых постеров для мессенджеров или в таблицы"),
            FeatureItem(AshIcons.Lock, "100% приватность и офлайн", "Никаких серверов, облаков и скрытой телеметрии. Все данные хранятся только у вас")
        )
    )
}

private data class FeatureItem(
    val icon: ImageVector,
    val title: String,
    val description: String
)

@Composable
private fun FeatureOverviewPage(
    icon: ImageVector,
    title: String,
    subtitle: String,
    features: List<FeatureItem>
) {
    val colors = AshTheme.colors
    val breath = rememberBreath()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))

        Box(
            Modifier
                .size(76.dp)
                .background(colors.accent.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size((38 * breath).dp)
            )
        }

        Text(
            text = title,
            style = AshTheme.type.title2,
            color = colors.text,
            textAlign = TextAlign.Center
        )

        Text(
            text = subtitle,
            style = AshTheme.type.subhead,
            color = colors.text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            features.forEach { feature ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(AshShapes.card)
                        .background(colors.surface1)
                        .border(
                            width = if (colors.isDark) 0.dp else 0.75.dp,
                            color = if (colors.isDark) Color.Transparent else colors.surface3,
                            shape = AshShapes.card
                        )
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(colors.surface2, AshShapes.squircle(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            feature.icon,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = feature.title,
                            style = AshTheme.type.headline,
                            color = colors.text
                        )
                        Text(
                            text = feature.description,
                            style = AshTheme.type.footnote,
                            color = colors.text2
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Выбор первых привычек на последнем шаге знакомства.
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
            text = stringResource(R.string.onboarding_s_chego_nachnem),
            style = AshTheme.type.title2,
            color = colors.text
        )
        Text(
            text = stringResource(R.string.onboarding_vyberite_odnu_dve_bolshe_soblazn_kotoryy_che),
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

private const val PAGES = 7

/** Сколько привычек показывать из каждой категории: остальные есть в каталоге. */
private const val HABITS_PER_CATEGORY = 4
