package dev.ashwake.ui.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.domain.model.blocking.BlockingRule
import dev.ashwake.domain.model.blocking.BypassRecord
import dev.ashwake.domain.model.blocking.UnlockCondition
import dev.ashwake.domain.repository.blocking.BlockingRepository
import dev.ashwake.domain.repository.routines.RoutineRepository
import dev.ashwake.platform.blocking.BlockingService
import dev.ashwake.platform.blocking.ForegroundAppMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import javax.inject.Inject

data class InstalledApp(val packageName: String, val label: String)

data class PermissionState(
    val usageAccess: Boolean = false,
    val overlay: Boolean = false
) {
    val allGranted: Boolean get() = usageAccess && overlay
}

@HiltViewModel
class BlockingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blocking: BlockingRepository,
    private val routines: RoutineRepository,
    private val monitor: ForegroundAppMonitor
) : ViewModel() {

    val rules: StateFlow<List<BlockingRule>> = blocking.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bypasses: StateFlow<List<BypassRecord>> = blocking.observeBypasses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routineList = routines.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions.asStateFlow()

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    init {
        refreshPermissions()
        viewModelScope.launch { _apps.value = loadApps() }
    }

    fun refreshPermissions() {
        _permissions.value = PermissionState(
            usageAccess = monitor.hasUsageAccess(),
            overlay = monitor.hasOverlayPermission()
        )
    }

    /**
     * Список приложений для выбора.
     *
     * Системные без иконки запуска отфильтрованы: блокировать «Сервисы Google»
     * пользователь не собирается, а в списке из трёхсот строк нужное не найти.
     */
    private suspend fun loadApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val manager = context.packageManager
        val launchable = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        manager.queryIntentActivities(launchable, 0)
            .mapNotNull { resolved ->
                val info: ApplicationInfo = resolved.activityInfo?.applicationInfo
                    ?: return@mapNotNull null
                if (info.packageName == context.packageName) return@mapNotNull null
                InstalledApp(
                    packageName = info.packageName,
                    label = manager.getApplicationLabel(info).toString()
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    // --- правила -----------------------------------------------------------

    fun createRule(
        name: String,
        condition: UnlockCondition,
        routineId: Long?,
        unlockTime: LocalTime?,
        packages: List<String>
    ) {
        if (name.isBlank() || packages.isEmpty()) return
        viewModelScope.launch {
            blocking.upsertRule(
                BlockingRule(
                    name = name.trim(),
                    // Новое правило создаётся выключенным: функция включается
                    // осознанно, а не появлением правила в списке
                    enabled = false,
                    condition = condition,
                    routineId = routineId,
                    unlockTime = unlockTime,
                    packages = packages
                )
            )
        }
    }

    fun setEnabled(rule: BlockingRule, enabled: Boolean) {
        viewModelScope.launch {
            blocking.setEnabled(rule.id, enabled)
            syncService()
        }
    }

    fun delete(rule: BlockingRule) {
        viewModelScope.launch {
            blocking.deleteRule(rule.id)
            syncService()
        }
    }

    /** Сервис живёт ровно столько, сколько есть включённые правила. */
    private suspend fun syncService() {
        if (blocking.hasEnabledRules() && monitor.hasUsageAccess()) {
            BlockingService.start(context)
        } else {
            BlockingService.stop(context)
        }
    }

    // --- разрешения --------------------------------------------------------

    fun openUsageAccessSettings() {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openOverlaySettings() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun appLabel(packageName: String): String =
        _apps.value.firstOrNull { it.packageName == packageName }?.label ?: packageName
}
