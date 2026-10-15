package dev.ashwake.platform.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.R
import dev.ashwake.data.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AppIcon(
    val key: String,
    val aliasName: String,
    val title: String,
    @DrawableRes val previewRes: Int
) {
    DEFAULT("default", "DefaultIconAlias", "Исходная", R.drawable.ic_launcher_foreground),
    OBSIDIAN("obsidian", "ObsidianIconAlias", "Обсидиан", R.drawable.app_icon_obsidian),
    FROST("frost", "FrostIconAlias", "Лёд", R.drawable.app_icon_frost),
    IVORY("ivory", "IvoryIconAlias", "Светлая", R.drawable.app_icon_ivory),
    NEON("neon", "NeonIconAlias", "Неон", R.drawable.app_icon_neon),
    SILVER("silver", "SilverIconAlias", "Серебро", R.drawable.app_icon_silver),
    FOREST("forest", "ForestIconAlias", "Лес", R.drawable.app_icon_forest);

    companion object {
        fun fromKey(key: String): AppIcon = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Переключает видимый launcher alias, не меняя точку входа приложения. */
@Singleton
class AppIconManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettings
) {
    val selected: Flow<AppIcon> = settings.appIcon.map(AppIcon::fromKey)

    suspend fun select(icon: AppIcon) {
        settings.setAppIcon(icon.key)
        val manager = context.packageManager
        // Сначала включаем новый компонент: на рабочем столе не возникает окна без иконки.
        setState(manager, icon, PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        AppIcon.entries.filterNot { it == icon }.forEach { candidate ->
            setState(manager, candidate, PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        }
    }

    private fun setState(manager: PackageManager, icon: AppIcon, state: Int) {
        val component = ComponentName(
            context.packageName,
            "dev.ashwake.launcher.${icon.aliasName}"
        )
        manager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}
