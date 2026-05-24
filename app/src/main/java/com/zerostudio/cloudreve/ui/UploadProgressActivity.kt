package com.zerostudio.cloudreve.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zerostudio.cloudreve.R
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_PREFERENCES_NAME
import com.zerostudio.cloudreve.core.common.locale.APP_LANGUAGE_TAG_KEY
import com.zerostudio.cloudreve.core.common.locale.AppLanguage
import com.zerostudio.cloudreve.core.common.locale.AppLanguageController
import com.zerostudio.cloudreve.core.common.locale.createLocalizedContext
import com.zerostudio.cloudreve.core.domain.model.TransferStatus
import com.zerostudio.cloudreve.core.domain.model.TransferTask
import com.zerostudio.cloudreve.core.domain.repository.CloudreveRepository
import com.zerostudio.cloudreve.core.transfer.TransferScheduler
import com.zerostudio.cloudreve.core.ui.MiuixCloudreveTheme
import com.zerostudio.cloudreve.core.ui.enableCloudreveImmersiveSystemBars
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.theme.MiuixTheme

class UploadProgressActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val preferences = newBase.getSharedPreferences(
            APP_LANGUAGE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val language = AppLanguage.fromLanguageTag(preferences.getString(APP_LANGUAGE_TAG_KEY, null))
        super.attachBaseContext(newBase.createLocalizedContext(language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableCloudreveImmersiveSystemBars()
        setContent {
            UploadProgressApp(onBack = ::finish)
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, UploadProgressActivity::class.java)
    }
}

@Composable
private fun UploadProgressApp(
    onBack: () -> Unit,
    repository: CloudreveRepository = koinInject(),
    transferScheduler: TransferScheduler = koinInject(),
    languageController: AppLanguageController = koinInject(),
) {
    val language = languageController.currentLanguage.collectAsStateWithLifecycle().value
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, language) {
        baseContext.createLocalizedContext(language)
    }
    val localizedConfiguration = remember(localizedContext) {
        localizedContext.resources.configuration
    }
    val transferFlow = remember(repository) { repository.transferTasks() }
    val tasks = transferFlow.collectAsStateWithLifecycle(initialValue = emptyList()).value
    val scope = rememberCoroutineScope()

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
        MiuixCloudreveTheme {
            UploadProgressScreen(
                tasks = tasks,
                onBack = onBack,
                onDeleteTasks = { targetTasks ->
                    scope.launch {
                        transferScheduler.deleteTransfers(targetTasks.map { it.id })
                    }
                },
                onRetryTasks = { targetTasks ->
                    scope.launch {
                        transferScheduler.retryUploads(targetTasks)
                    }
                },
            )
        }
    }
}

@Composable
private fun UploadProgressScreen(
    tasks: List<TransferTask>,
    onBack: () -> Unit,
    onDeleteTasks: (List<TransferTask>) -> Unit,
    onRetryTasks: (List<TransferTask>) -> Unit,
) {
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val selectedTasks = remember(tasks, selectedIds) {
        tasks.filter { it.id in selectedIds }
    }
    val selectionActive = selectedIds.isNotEmpty()

    LaunchedEffect(tasks) {
        val taskIds = tasks.mapTo(mutableSetOf()) { it.id }
        selectedIds = selectedIds.filterTo(mutableSetOf()) { it in taskIds }
    }

    BackHandler(enabled = selectionActive) {
        selectedIds = emptySet()
    }

    fun toggleSelection(task: TransferTask) {
        selectedIds = if (task.id in selectedIds) {
            selectedIds - task.id
        } else {
            selectedIds + task.id
        }
    }

    fun selectTask(task: TransferTask) {
        selectedIds = selectedIds + task.id
    }

    fun deleteTasks(targetTasks: List<TransferTask>) {
        if (targetTasks.isEmpty()) return
        selectedIds = selectedIds - targetTasks.map { it.id }.toSet()
        onDeleteTasks(targetTasks)
    }

    fun retryTasks(targetTasks: List<TransferTask>) {
        if (targetTasks.isEmpty()) return
        selectedIds = selectedIds - targetTasks.map { it.id }.toSet()
        onRetryTasks(targetTasks)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.transfers_title),
                largeTitle = stringResource(R.string.transfers_title),
                titlePadding = 16.dp,
                navigationIconPadding = 16.dp,
                actionIconPadding = 16.dp,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.transfers_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectionActive,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                TransferActionNavigationBar(
                    canRetry = selectedTasks.any { it.sourceUri.isNotBlank() },
                    onDelete = { deleteTasks(selectedTasks) },
                    onRetry = { retryTasks(selectedTasks) },
                )
            }
        },
        containerColor = MiuixTheme.colorScheme.background,
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.transfers_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = tasks,
                    key = { it.id },
                    contentType = { "transfer_task" },
                ) { task ->
                    TransferTaskCard(
                        task = task,
                        selectionActive = selectionActive,
                        selected = task.id in selectedIds,
                        onToggleSelected = { toggleSelection(task) },
                        onClick = {
                            if (selectionActive) {
                                toggleSelection(task)
                            }
                        },
                        onLongPress = { selectTask(task) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferActionNavigationBar(
    canRetry: Boolean,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        color = MiuixTheme.colorScheme.surface,
        showDivider = true,
        defaultWindowInsetsPadding = true,
    ) {
        TransferActionNavigationItem(
            onClick = onDelete,
            icon = MiuixIcons.Regular.Delete,
            label = stringResource(R.string.transfers_action_delete),
        )
        TransferActionNavigationItem(
            onClick = onRetry,
            icon = MiuixIcons.Regular.Refresh,
            label = stringResource(R.string.transfers_action_retry),
            enabled = canRetry,
        )
    }
}

@Composable
private fun RowScope.TransferActionNavigationItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val baseTint = if (isSystemInDarkTheme()) Color.White else MiuixTheme.colorScheme.onSurface
    val tint = baseTint.copy(alpha = if (enabled) 1f else 0.38f)
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(26.dp),
            tint = tint,
        )
        Text(
            text = label,
            color = tint,
        )
    }
}

@Composable
private fun TransferTaskCard(
    task: TransferTask,
    selectionActive: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        showIndication = true,
        onClick = onClick,
        onLongPress = onLongPress,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = selectionActive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Checkbox(
                            state = if (selected) ToggleableState.On else ToggleableState.Off,
                            onClick = onToggleSelected,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.UploadCloud,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = task.name, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            text = transferStatusText(task.status),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Text(
                        text = "${(task.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinearProgressIndicator(
                    progress = if (task.totalBytes > 0L || task.status == TransferStatus.Succeeded) {
                        task.progress
                    } else {
                        null
                    },
                )
                Text(
                    text = "${formatBytes(task.transferredBytes)} / ${formatBytes(task.totalBytes)}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun transferStatusText(status: TransferStatus): String = when (status) {
    TransferStatus.Queued -> stringResource(R.string.transfer_status_queued)
    TransferStatus.Running -> stringResource(R.string.transfer_status_running)
    TransferStatus.Paused -> stringResource(R.string.transfer_status_paused)
    TransferStatus.Succeeded -> stringResource(R.string.transfer_status_succeeded)
    TransferStatus.Failed -> stringResource(R.string.transfer_status_failed)
    TransferStatus.Canceled -> stringResource(R.string.transfer_status_canceled)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (value >= 10 || unit == 0) {
        "${value.toInt()} ${units[unit]}"
    } else {
        "%.1f %s".format(value, units[unit])
    }
}
