package dev.ashwake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme

/**
 * Сгруппированный список — основной строительный блок всего приложения
 * (раздел 5 дизайн-системы).
 *
 * Одна метафора на все экраны: так же выглядят и настройки, и списки задач,
 * и привычки. Именно это, а не количество экранов, делает интерфейс понятным.
 *
 * Группа — блок радиусом 10 на «Поверхности 1». Строки внутри разделены
 * линиями 0.5dp, **сдвинутыми вправо до начала текста**, и у последней строки
 * разделителя нет: линия во всю ширину — материаловский приём (раздел 10).
 */

/** Поля экрана. Одно значение на всё приложение. */
val ScreenPadding = 16.dp

/** Отступ разделителя слева по умолчанию: до начала текста в обычной строке. */
private val DefaultDividerInset = 16.dp

/** Минимальные высоты строк из раздела 4. */
val ListRowMinHeight = 44.dp
val ListRowWithSubtitleHeight = 60.dp

@Composable
fun ListGroupHeader(text: String, modifier: Modifier = Modifier) {
    // Единственное место в приложении, где есть капс — заголовки групп,
    // как в iOS. Везде остальное набирается обычным регистром (раздел 3).
    Text(
        text = text.uppercase(),
        style = AshTheme.type.caption,
        color = AshTheme.colors.text2,
        modifier = modifier.padding(start = ScreenPadding, end = ScreenPadding, bottom = 6.dp)
    )
}

@Composable
fun ListGroupFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AshTheme.type.footnote,
        color = AshTheme.colors.text2,
        modifier = modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 6.dp)
    )
}

/** Разделитель со сдвигом. Отдельно — чтобы его можно было ставить вручную. */
@Composable
fun ListDivider(inset: Dp = DefaultDividerInset) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(0.5.dp)
            .background(AshTheme.colors.separator)
    )
}

/**
 * Группа-контейнер: заголовок, блок с фоном и скруглением, пояснение снизу.
 * Разделители внутри расставляет вызывающий — это нужно там, где строки
 * разнородные. Для однородного списка есть перегрузка с `items`.
 */
@Composable
fun ListGroup(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        header?.let { ListGroupHeader(it) }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding)
                .clip(AshShapes.group)
                .background(AshTheme.colors.surface1),
            content = content
        )
        footer?.let { ListGroupFooter(it) }
    }
}

/**
 * Группа из однородных строк: разделители расставляются сами и не рисуются
 * у последней строки.
 */
@Composable
fun <T> ListGroup(
    items: List<T>,
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    dividerInset: Dp = DefaultDividerInset,
    row: @Composable (T) -> Unit
) {
    if (items.isEmpty()) return
    ListGroup(modifier = modifier, header = header, footer = footer) {
        items.forEachIndexed { index, item ->
            row(item)
            if (index != items.lastIndex) ListDivider(dividerInset)
        }
    }
}

/**
 * Обычная строка списка: ведущий элемент, название, подпись, значение справа
 * и шеврон. Внутренние отступы 16dp по горизонтали и 11dp по вертикали,
 * высота не меньше 44dp — это же минимальная зона нажатия.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    showChevron: Boolean = false,
    titleColor: androidx.compose.ui.graphics.Color = AshTheme.colors.text,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val colors = AshTheme.colors
    val clickable = if (onClick != null) {
        Modifier.tappable(onLongClick = onLongClick, onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .defaultMinSize(minHeight = if (subtitle == null) ListRowMinHeight else ListRowWithSubtitleHeight)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading?.invoke()

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = AshTheme.type.headline,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = AshTheme.type.subhead,
                    color = colors.text2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        value?.let {
            Text(text = it, style = AshTheme.type.subhead, color = colors.text2)
        }
        trailing?.invoke()

        if (showChevron) {
            Icon(
                imageVector = AshIcons.ChevronRight,
                contentDescription = null,
                tint = colors.text3,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
