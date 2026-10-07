@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.kkfittracking.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kkfittracking.BuildConfig
import com.kkfittracking.data.backup.BackupFile
import com.kkfittracking.data.backup.summary
import com.kkfittracking.model.AutoBackup
import com.kkfittracking.model.ExportFile
import com.kkfittracking.model.ThemeMode
import com.kkfittracking.model.TrainingStyle
import com.kkfittracking.model.UnitSystem
import com.kkfittracking.model.formatDuration
import com.kkfittracking.model.formatMinuteOfDay
import com.kkfittracking.model.formatNumber
import com.kkfittracking.model.inUserOrder
import com.kkfittracking.ui.components.rememberNotificationPermissionRequester
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val REST_STEP_SECONDS = 15

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lastBackupAt by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val today = LocalDate.now().toString()
    var arrangingLibrary by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val askNotifications = rememberNotificationPermissionRequester()
    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.refreshHealthStatus()
    }
    LifecycleResumeEffect(Unit) {
        viewModel.refreshHealthStatus()
        onPauseOrDispose { }
    }
    val sections by viewModel.sections.collectAsStateWithLifecycle()

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        it?.let(viewModel::backUp)
    }
    val restoreLauncher = rememberLauncherForActivityResult(OpenInAppFolder()) {
        it?.let(viewModel::openBackup)
    }
    val workoutsCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        it?.let(viewModel::exportWorkouts)
    }
    val bodyCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        it?.let(viewModel::exportBodyMeasurements)
    }

    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            viewModel.consumeMessage()
            snackbarHostState.showSnackbar(it, withDismissAction = true)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Units")
            UnitSystem.entries.filter { it.isAppWide }.forEach { unitSystem ->
                RadioRow(
                    label = unitSystem.label,
                    selected = current.unitSystem == unitSystem,
                    onClick = { viewModel.setUnitSystem(unitSystem) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Rest timer")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Default duration", modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = { viewModel.changeRestTimer(-REST_STEP_SECONDS) }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = formatDuration(current.restTimerSeconds),
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                FilledTonalIconButton(onClick = { viewModel.changeRestTimer(REST_STEP_SECONDS) }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
            SwitchRow(
                title = "Start automatically",
                subtitle = "Start the timer each time you save a set (in a superset: after the last exercise of each round)",
                checked = current.autoStartRestTimer,
                onCheckedChange = viewModel::setAutoStartRestTimer,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Drop sets and supersets")
            SwitchRow(
                title = "Drop sets",
                subtitle = "Show a \"Drop set\" option when logging weight and reps. Plan drop sets for an " +
                    "exercise in its set plan",
                checked = current.dropSetsEnabled,
                onCheckedChange = viewModel::setDropSetsEnabled,
            )
            if (current.dropSetsEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Default weight drop")
                        Text(
                            text = "Until you choose one in an exercise's set plan. Rounded to 0.5 kg or 1 lb",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(onClick = { viewModel.changeDropSetPercent(-5) }) {
                        Text("−", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "${current.dropSetPercent}%",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    FilledTonalIconButton(onClick = { viewModel.changeDropSetPercent(5) }) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Time to the next superset exercise")
                    Text(
                        text = "Default for new supersets; change it per superset in Superset edit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = { viewModel.changeSupersetTransition(-5) }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = "${current.supersetTransitionSeconds} s",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalIconButton(onClick = { viewModel.changeSupersetTransition(5) }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
            SwitchRow(
                title = "Supersets: go to the next exercise",
                subtitle = "After saving a set in a superset, open the next exercise of the superset",
                checked = current.supersetAutoAdvance,
                onCheckedChange = viewModel::setSupersetAutoAdvance,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Training help")
            SwitchRow(
                title = "Suggest the next step",
                subtitle = "From your last session: a little more weight, reps or time when you made all your sets",
                checked = current.progressionHints,
                onCheckedChange = viewModel::setProgressionHints,
            )
            SwitchRow(
                title = "Warm-up sets",
                subtitle = "Lighter sets before the first heavy set of a lift, with the plates to load",
                checked = current.warmUpSets,
                onCheckedChange = viewModel::setWarmUpSets,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Barbell weight")
                    Text(
                        text = "For the plate calculator and warm-ups",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = { viewModel.changeBar(-1) }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = "${formatNumber(current.unitSystem.weightFromKg(current.barKg))} ${current.unitSystem.weightUnit}",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                FilledTonalIconButton(onClick = { viewModel.changeBar(1) }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Reminders")
            Text(
                text = "A notification on the days you choose, with what is planned and a button to start.",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in current.reminderDays,
                        onClick = {
                            askNotifications()
                            viewModel.toggleReminderDay(day)
                        },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }
            if (current.reminderDays.isNotEmpty()) {
                ActionRow("Time", formatMinuteOfDay(current.reminderMinute), busy = false) { pickingTime = true }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Health Connect")
            Text(
                text = "Shares with Samsung Health, Google Fit and other health apps: the app reads your daily steps " +
                    "and writes your finished workouts. " + (viewModel.healthStatus?.let { "Status: $it." } ?: ""),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ActionRow("Connect Health Connect", "Choose what the app may read and write", busy = false) {
                healthLauncher.launch(viewModel.healthPermissions)
            }
            SwitchRow(
                title = "Send finished workouts",
                subtitle = "When a guided workout ends. Any day can also be sent from its menu",
                checked = current.sendWorkoutsToHealth,
                onCheckedChange = viewModel::setSendWorkoutsToHealth,
            )
            SwitchRow(
                title = "Include estimated calories",
                subtitle = "Leave off if your watch already counts calories all day (Samsung Health), or they count twice",
                checked = current.sendCaloriesToHealth,
                onCheckedChange = viewModel::setSendCaloriesToHealth,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Exercise library")
            ActionRow("Order of sections and styles", "Which body sections and training styles come first", busy = false) {
                arrangingLibrary = true
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Theme")
            ThemeMode.entries.forEach { mode ->
                RadioRow(
                    label = mode.label,
                    selected = current.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Your data")
            Text(
                text = "Everything is stored only on this phone. Backups go to Downloads/FitTraKKing with the " +
                    "date in their names; copy that folder to Google Drive or a computer now and then, so you can " +
                    "restore on a new phone.",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Last backup: " + (lastBackupAt?.let { formatTimestamp(it) } ?: "never"),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (lastBackupAt == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (viewModel.canSaveToDownloads) {
                ActionRow(
                    "Save to Downloads/FitTraKKing",
                    "A backup plus workouts and body measurements as CSV, dated, in one tap",
                    viewModel.isBusy,
                ) { viewModel.saveAllToDownloads() }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("Automatic backup")
                    Text(
                        text = "Saved to the same folder by itself; the newest ${ExportFile.AUTO_BACKUPS_KEPT} are kept",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AutoBackup.entries.forEach { frequency ->
                            FilterChip(
                                selected = current.autoBackup == frequency,
                                onClick = { viewModel.setAutoBackup(frequency) },
                                label = { Text(frequency.label) },
                            )
                        }
                    }
                }
            }
            ActionRow("Save a backup as…", "Pick where: Google Drive, a USB stick, another folder", viewModel.isBusy) {
                backupLauncher.launch("${ExportFile.PREFIX}_backup_$today.json")
            }
            ActionRow("Restore from a backup file", "Replaces the data in the app with the backup", viewModel.isBusy) {
                restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            }
            ActionRow("Save workouts as CSV…", "For Excel or Google Sheets", viewModel.isBusy) {
                workoutsCsvLauncher.launch("${ExportFile.PREFIX}_workouts_$today.csv")
            }
            ActionRow("Save body measurements as CSV…", "For Excel or Google Sheets", viewModel.isBusy) {
                bodyCsvLauncher.launch("${ExportFile.PREFIX}_body_$today.csv")
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (pickingTime) {
        val minute = settings?.reminderMinute ?: (17 * 60)
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text("Reminder time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickingTime = false
                        viewModel.setReminderMinute(state.hour * 60 + state.minute)
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Cancel") } },
        )
    }

    if (arrangingLibrary) {
        LibraryOrderDialog(
            sections = sections,
            styles = TrainingStyle.sectionOrder.inUserOrder(settings?.styleOrder.orEmpty()) { it.name },
            onMoveSection = viewModel::moveSection,
            onMoveStyle = viewModel::moveStyle,
            onReset = viewModel::resetLibraryOrder,
            onDismiss = { arrangingLibrary = false },
        )
    }

    viewModel.pendingRestore?.let { file ->
        RestoreDialog(file, onConfirm = viewModel::confirmRestore, onDismiss = viewModel::cancelRestore)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, busy: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = !busy, onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
    )
}

private fun formatTimestamp(millis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
private fun RestoreDialog(file: BackupFile, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val summary = file.summary()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore this backup?") },
        text = {
            Text(
                "Backup from ${formatTimestamp(summary.createdAt)}: ${summary.workouts} workouts, " +
                    "${summary.sets} sets, ${summary.plans} plans and ${summary.bodyMeasurements} body measurements.\n\n" +
                    "Everything currently in the app will be replaced. Consider backing up the current data first.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Replace and restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 16.dp))
    }
}

/** The file picker for restoring, opened in Downloads/FitTraKKing where the backups are. */
private class OpenInAppFolder : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        val folder = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:${Environment.DIRECTORY_DOWNLOADS}/${ExportFile.FOLDER}",
        )
        return super.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, folder)
    }
}
