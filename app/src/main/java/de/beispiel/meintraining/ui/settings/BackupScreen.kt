package de.beispiel.meintraining.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import android.net.Uri
import android.widget.Toast
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.backup.MAX_BACKUP_INTERVAL_DAYS
import de.beispiel.meintraining.data.backup.MIN_BACKUP_INTERVAL_DAYS
import de.beispiel.meintraining.ui.screen.SubScreenHeader
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AccentRed
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.TextDisabled
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.formatFullDate
import de.beispiel.meintraining.util.toClockTime
import de.beispiel.meintraining.util.toLocalDate

/** Der Sicherungsbereich mit seinem ViewModel. */
@Composable
fun BackupRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)
    val uiState by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    val defaultName = stringResource(R.string.backup_default_filename)
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    // Rückmeldung als Toast: Der Bereich hat kein Scaffold, und für „hat geklappt“ genügt das.
    LaunchedEffect(message) {
        val text = when (val current = message) {
            null -> return@LaunchedEffect
            BackupMessage.Exported -> context.getString(R.string.backup_done)
            BackupMessage.Imported -> context.getString(R.string.backup_imported)
            is BackupMessage.Failed ->
                context.getString(R.string.backup_failed, current.reason)
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        viewModel.onMessageShown()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_TYPE)
    ) { uri -> uri?.let(viewModel::onExportChosen) }

    val targetLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(MIME_TYPE)
    ) { uri -> uri?.let(viewModel::onBackupTargetChosen) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImport = uri }

    BackupScreen(
        uiState = uiState,
        onExport = { exportLauncher.launch(defaultName) },
        onChooseTarget = { targetLauncher.launch(defaultName) },
        onImport = { importLauncher.launch(arrayOf(MIME_TYPE, "application/octet-stream", "*/*")) },
        onAutoBackupToggled = viewModel::onAutoBackupToggled,
        onIntervalChanged = viewModel::onIntervalChanged,
        onBack = onBack,
        modifier = modifier
    )

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = CardBackground,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text(text = stringResource(R.string.backup_import_title)) },
            text = { Text(text = stringResource(R.string.backup_import_body)) },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingImport = null
                        viewModel.onImportChosen(uri)
                    }
                ) {
                    Text(text = stringResource(R.string.backup_import_confirm), color = AccentRed)
                }
            }
        )
    }
}

@Composable
private fun BackupScreen(
    uiState: BackupUiState,
    onExport: () -> Unit,
    onChooseTarget: () -> Unit,
    onImport: () -> Unit,
    onAutoBackupToggled: (Boolean) -> Unit,
    onIntervalChanged: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        SubScreenHeader(title = stringResource(R.string.settings_backup), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
        ) {
            SettingsCard(title = stringResource(R.string.backup_manual_title)) {
                ActionRow(
                    label = stringResource(R.string.backup_export),
                    hint = stringResource(R.string.backup_export_hint),
                    enabled = !uiState.busy,
                    onClick = onExport
                )
                ActionRow(
                    label = stringResource(R.string.backup_import),
                    hint = stringResource(R.string.backup_import_hint),
                    enabled = !uiState.busy,
                    onClick = onImport
                )
            }

            SettingsCard(title = stringResource(R.string.backup_auto_title)) {
                Text(
                    text = stringResource(R.string.backup_auto_hint),
                    style = AppTextStyles.ColumnLabel,
                    color = TextSecondary
                )
                ActionRow(
                    label = stringResource(R.string.backup_target),
                    hint = uiState.targetName?.let {
                        stringResource(R.string.backup_target_current, it)
                    } ?: stringResource(R.string.backup_target_missing),
                    enabled = !uiState.busy,
                    onClick = onChooseTarget
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.backup_auto_switch),
                        style = AppTextStyles.Body,
                        color = if (uiState.canEnableAutoBackup) TextPrimary else TextDisabled,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.autoBackupEnabled,
                        onCheckedChange = onAutoBackupToggled,
                        // Ohne Ziel gäbe es nichts zu beschreiben.
                        enabled = uiState.canEnableAutoBackup,
                        colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                    )
                }
                SettingsField(
                    value = uiState.intervalDays.toString(),
                    onValueChange = onIntervalChanged,
                    label = stringResource(R.string.backup_interval),
                    supportingText = stringResource(
                        R.string.backup_interval_hint,
                        MIN_BACKUP_INTERVAL_DAYS,
                        MAX_BACKUP_INTERVAL_DAYS
                    ),
                    keyboardType = KeyboardType.Number,
                    resetOnFocusLoss = true
                )
                Text(
                    text = lastBackupText(uiState),
                    style = AppTextStyles.ColumnLabel,
                    color = if (uiState.lastBackupError != null) AccentRed else TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(Dimens.ListBottomPadding))
        }
    }
}

@Composable
private fun lastBackupText(uiState: BackupUiState): String {
    val timestamp = uiState.lastBackupAt ?: return stringResource(R.string.backup_never)
    val stamp = "${formatFullDate(timestamp.toLocalDate())}, ${timestamp.toClockTime()}"
    val error = uiState.lastBackupError
    return if (error == null) {
        stringResource(R.string.backup_last_ok, stamp)
    } else {
        stringResource(R.string.backup_last_failed, stamp, error)
    }
}

@Composable
private fun ActionRow(
    label: String,
    hint: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerChip)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .background(CardBackground)
            .padding(vertical = Dimens.SectionSpacingSmall)
    ) {
        Text(
            text = label,
            style = AppTextStyles.Body,
            color = if (enabled) AccentBlue else TextDisabled
        )
        Text(
            text = hint,
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
        )
    }
}

/** JSON, damit die Datei überall als Text erkannt und angezeigt wird. */
private const val MIME_TYPE = "application/json"
