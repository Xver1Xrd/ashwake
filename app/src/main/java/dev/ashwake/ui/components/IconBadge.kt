package dev.ashwake.ui.components

import dev.ashwake.R
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ashwake.data.icons.IconStore
import dev.ashwake.ui.theme.AshShapes
import dev.ashwake.ui.theme.AshTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Значок сущности: картинка из галереи или эмодзи.
 *
 * Одно место, которое знает правило «картинка важнее эмодзи», — иначе это
 * правило пришлось бы повторять в каждой строке каждого списка и однажды
 * разойтись.
 */
@Composable
fun EntityIcon(
    emoji: String?,
    iconPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    background: Color = AshTheme.colors.surface2,
    fallback: (@Composable () -> Unit)? = null
) {
    when {
        iconPath != null -> ImageIcon(iconPath, modifier, size)
        emoji != null -> EmojiBadge(emoji, modifier, size, background)
        fallback != null -> fallback()
    }
}

/**
 * Картинка-значок из хранилища приложения.
 *
 * Файл читается в фоне и кэшируется в [IconStore]: строка списка не имеет
 * права ждать диск, а список прокручивается постоянно.
 */
@Composable
fun ImageIcon(
    iconPath: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    val store = LocalIconStore.current
    var bitmap by remember(iconPath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(iconPath, store) {
        bitmap = withContext(Dispatchers.IO) { store?.load(iconPath) }
    }

    Box(
        modifier
            .size(size)
            .clip(AshShapes.squircle(size / 3))
            .background(AshTheme.colors.surface2),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Хранилище значков для композиции.
 *
 * Строке списка неоткуда взять зависимость: она не ViewModel и не должна
 * знать про Hilt. Хранилище кладётся в тему один раз на приложение.
 */
val LocalIconStore = androidx.compose.runtime.staticCompositionLocalOf<IconStore?> { null }

/**
 * Выбор значка: эмодзи из набора или картинка из галереи.
 *
 * Системный выбор картинок (Photo Picker) не требует разрешения на всю
 * галерею — человек отдаёт приложению ровно один файл. Просить доступ ко
 * всем фотографиям ради одного значка было бы несоразмерно.
 */
@Composable
fun IconPicker(
    emoji: String?,
    iconPath: String?,
    onEmoji: (String?) -> Unit,
    onIcon: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors
    val store = LocalIconStore.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null || store == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = store.save(uri)
            if (saved != null) {
                // Старый файл больше никому не нужен: значок один
                iconPath?.let(store::delete)
                onIcon(saved)
            }
        }
    }

    androidx.compose.foundation.layout.Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChipButton(
                text = stringResource(R.string.components_iz_galerei),
                icon = AshIcons.Download,
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
            if (iconPath != null) {
                ImageIcon(iconPath, size = 34.dp)
                ChipButton(
                    text = stringResource(R.string.editor_ubrat),
                    icon = AshIcons.Close,
                    onClick = {
                        store?.delete(iconPath)
                        onIcon(null)
                    }
                )
            }
        }

        // Эмодзи остаётся доступной и при выбранной картинке: сняв картинку,
        // человек должен вернуться к тому значку, который уже выбирал
        EmojiPicker(
            selected = emoji,
            onSelect = onEmoji
        )

        if (iconPath != null && emoji != null) {
            Text(
                stringResource(R.string.components_pokazyvaetsya_kartinka_uberite_ee_chtoby_ver),
                style = AshTheme.type.footnote,
                color = colors.text2,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * Кнопка значка в форме: показывает выбранное, по нажатию раскрывает выбор.
 */
@Composable
fun IconButtonSlot(
    emoji: String?,
    iconPath: String?,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AshTheme.colors
    Box(
        modifier
            .size(52.dp)
            .background(
                if (expanded) colors.accent.copy(alpha = 0.16f) else colors.surface2,
                AshShapes.group
            )
            .border(
                width = if (expanded) 1.5.dp else 0.dp,
                color = if (expanded) colors.accent else Color.Transparent,
                shape = AshShapes.group
            )
            .tappable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        EntityIcon(
            emoji = emoji,
            iconPath = iconPath,
            size = 40.dp,
            background = Color.Transparent,
            fallback = {
                Icon(
                    AshIcons.AutoAwesome,
                    contentDescription = stringResource(R.string.components_vybrat_znachok),
                    tint = colors.text3,
                    modifier = Modifier.size(22.dp)
                )
            }
        )
    }
}
