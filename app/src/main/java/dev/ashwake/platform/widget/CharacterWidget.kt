package dev.ashwake.platform.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.ashwake.domain.model.character.EquipItem
import dev.ashwake.ui.character.render.CharacterBitmapRenderer
import dev.ashwake.ui.character.render.CharacterLayer
import dev.ashwake.ui.character.render.buildCharacterLayers

/**
 * Виджет с пиксельным персонажем в текущей экипировке (п. 18).
 *
 * Glance не умеет рисовать Canvas — только показывать готовое изображение,
 * поэтому персонаж собирается в Bitmap тем же рендером, что и портрет.
 * Масштаб небольшой: виджет обновляется не чаще раза в полчаса,
 * и гнать туда картинку в восемь раз крупнее незачем.
 */
class CharacterWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = context.widgetDependencies()
        val state = deps.characterRepository().state()
        val catalog = deps.catalogLoader().load()

        val layers = buildCharacterLayers(
            items = state.equipped.values.toList(),
            tints = catalog.paletteTints
        )
        val bitmap = CharacterBitmapRenderer().render(
            layers = layers,
            scale = WIDGET_SCALE,
            withFloor = true
        )

        provideContent {
            GlanceTheme { Content(bitmap, state.profile.name, state.level) }
        }
    }

    @Composable
    private fun Content(bitmap: Bitmap, name: String, level: Int) {
        val context = LocalContext.current

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(8.dp)
                .clickable(actionStartActivity(appIntent(context, AppRoutes.TASKS))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "Персонаж",
                modifier = GlanceModifier.size(WIDGET_IMAGE_DP.dp)
            )
            Text(
                "$name · ур. $level",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
            )
        }
    }


    private companion object {
        const val WIDGET_SCALE = 2
        const val WIDGET_IMAGE_DP = 128
        const val DEFAULT_TINT = 0xFF6E7BA6.toInt()
    }
}

class CharacterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CharacterWidget()
}
