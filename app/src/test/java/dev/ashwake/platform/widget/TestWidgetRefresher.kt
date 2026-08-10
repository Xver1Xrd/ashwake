package dev.ashwake.platform.widget

import androidx.test.core.app.ApplicationProvider

/**
 * Обновлятель виджетов для тестов.
 *
 * Настоящий ходит в Glance, которого под Robolectric нет, и молча
 * проглатывает ошибку — этого достаточно: поведение виджетов проверяется
 * на устройстве, а тестам нужен только объект нужного типа.
 */
fun testWidgetRefresher(): WidgetRefresher =
    WidgetRefresher(ApplicationProvider.getApplicationContext())
