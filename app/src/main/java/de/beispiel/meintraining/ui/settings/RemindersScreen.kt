package de.beispiel.meintraining.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.screen.SubScreenHeader
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AccentRed
import de.beispiel.meintraining.ui.theme.AccentRedSurface
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.OutlineColor
import de.beispiel.meintraining.ui.theme.ScreenBackground
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.MAX_PAUSE_DAYS
import de.beispiel.meintraining.util.MIN_PAUSE_DAYS
import de.beispiel.meintraining.util.ReminderSettings
import de.beispiel.meintraining.util.formatClockTime

/** Welcher Schalter gerade auf die Antwort zur Berechtigung wartet. */
internal enum class ReminderSwitch { DELOAD, PAUSE, BACKUP }

/** Was der Bereich „Erinnerungen“ nach oben meldet – gebündelt, die Liste wäre sonst endlos. */
internal class ReminderActions(
    val onDeloadToggled: (Boolean) -> Unit,
    val onPauseToggled: (Boolean) -> Unit,
    val onBackupToggled: (Boolean) -> Unit,
    val onPauseDaysChange: (String) -> Unit,
    val onTimeChange: (Int) -> Unit
)

/**
 * Der Bereich „Erinnerungen“ samt der Frage nach der Berechtigung.
 *
 * Gefragt wird erst, wenn ein Schalter eingeschaltet wird – nicht beim Öffnen und nicht beim Start
 * der App: Eine Berechtigung ohne erkennbaren Anlass lehnt man ab. Bis zur Antwort steht der
 * Schalter an; wird abgelehnt, springt er zurück, und ein Hinweis oben sagt, warum. Nach
 * zweimaliger Ablehnung zeigt Android keinen Dialog mehr – dann führt der Hinweis in die
 * Benachrichtigungs-Einstellungen der App.
 */
@Composable
internal fun RemindersRoute(
    settings: ReminderSettings,
    actions: ReminderActions,
    permission: NotificationPermission,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var asking by remember { mutableStateOf<ReminderSwitch?>(null) }
    var refusal by remember { mutableStateOf<PermissionResult?>(null) }
    val context = LocalContext.current

    fun toggle(switch: ReminderSwitch, enabled: Boolean, apply: (Boolean) -> Unit) {
        if (!enabled) {
            apply(false)
            return
        }
        asking = switch
        permission.request { result ->
            asking = null
            if (result == PermissionResult.GRANTED) {
                refusal = null
                apply(true)
            } else {
                refusal = result
            }
        }
    }

    RemindersScreen(
        settings = settings,
        notificationsAllowed = permission.allowed,
        asking = asking,
        refusal = refusal,
        onDeloadToggled = { toggle(ReminderSwitch.DELOAD, it, actions.onDeloadToggled) },
        onPauseToggled = { toggle(ReminderSwitch.PAUSE, it, actions.onPauseToggled) },
        onBackupToggled = { toggle(ReminderSwitch.BACKUP, it, actions.onBackupToggled) },
        onPauseDaysChange = actions.onPauseDaysChange,
        onTimeChange = actions.onTimeChange,
        onAllow = {
            permission.request { result ->
                refusal = result.takeUnless { it == PermissionResult.GRANTED }
                // Ohne Dialog bleibt nur der Weg in die Systemeinstellungen – gleich dorthin.
                if (result == PermissionResult.BLOCKED) openNotificationSettings(context)
            }
        },
        onOpenSettings = { openNotificationSettings(context) },
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
internal fun RemindersScreen(
    settings: ReminderSettings,
    notificationsAllowed: Boolean,
    asking: ReminderSwitch?,
    refusal: PermissionResult?,
    onDeloadToggled: (Boolean) -> Unit,
    onPauseToggled: (Boolean) -> Unit,
    onBackupToggled: (Boolean) -> Unit,
    onPauseDaysChange: (String) -> Unit,
    onTimeChange: (Int) -> Unit,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pickingTime by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        SubScreenHeader(title = stringResource(R.string.settings_reminders), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
        ) {
            Text(
                text = stringResource(R.string.reminders_intro),
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary
            )

            // Ehrlich statt still: Ein Schalter, der an steht und nichts bewirkt, wäre schlimmer als
            // einer, der aus ist. „Sicherung fehlgeschlagen“ ist ab Werk an – ohne Berechtigung
            // stünde es sonst ohne jeden Hinweis wirkungslos da.
            when {
                refusal != null -> PermissionNotice(
                    text = if (refusal == PermissionResult.BLOCKED) {
                        stringResource(R.string.reminders_denied) + " " +
                            stringResource(R.string.reminders_denied_for_good)
                    } else {
                        stringResource(R.string.reminders_denied)
                    },
                    action = if (refusal == PermissionResult.BLOCKED) {
                        stringResource(R.string.reminders_open_settings)
                    } else {
                        null
                    },
                    onAction = onOpenSettings
                )
                settings.anyEnabled && !notificationsAllowed -> PermissionNotice(
                    text = stringResource(R.string.reminders_blocked),
                    action = stringResource(R.string.reminders_allow),
                    onAction = onAllow
                )
            }

            SettingsCard(title = stringResource(R.string.reminders_daily_title)) {
                TimeRow(settings = settings, onClick = { pickingTime = true })
                SwitchRow(
                    label = stringResource(R.string.reminders_deload),
                    hint = stringResource(R.string.reminders_deload_hint),
                    checked = settings.deload || asking == ReminderSwitch.DELOAD,
                    onCheckedChange = onDeloadToggled
                )
                SwitchRow(
                    label = stringResource(R.string.reminders_pause),
                    hint = stringResource(R.string.reminders_pause_hint),
                    checked = settings.pause || asking == ReminderSwitch.PAUSE,
                    onCheckedChange = onPauseToggled
                )
                SettingsField(
                    value = settings.pauseDays.toString(),
                    onValueChange = onPauseDaysChange,
                    label = stringResource(R.string.reminders_pause_days),
                    supportingText = stringResource(R.string.reminders_pause_days_hint, MIN_PAUSE_DAYS, MAX_PAUSE_DAYS),
                    keyboardType = KeyboardType.Number,
                    resetOnFocusLoss = true
                )
            }

            SettingsCard(title = stringResource(R.string.reminders_backup_title)) {
                SwitchRow(
                    label = stringResource(R.string.reminders_backup),
                    hint = stringResource(R.string.reminders_backup_hint),
                    checked = settings.backup || asking == ReminderSwitch.BACKUP,
                    onCheckedChange = onBackupToggled
                )
            }

            Spacer(modifier = Modifier.height(Dimens.ListBottomPadding))
        }
    }

    if (pickingTime) {
        ReminderTimeDialog(
            minuteOfDay = settings.minuteOfDay,
            onConfirm = { minute ->
                pickingTime = false
                onTimeChange(minute)
            },
            onDismiss = { pickingTime = false }
        )
    }
}

/** Die Uhrzeit als Zeile; ein Tipp öffnet die Auswahl. */
@Composable
private fun TimeRow(settings: ReminderSettings, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerChip)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.reminders_time), style = AppTextStyles.Body, color = TextPrimary)
            Text(
                text = stringResource(R.string.reminders_time_hint),
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary,
                modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
            )
        }
        Text(
            text = stringResource(R.string.reminders_time_value, formatClockTime(settings.time)),
            style = AppTextStyles.Body,
            color = AccentBlue,
            modifier = Modifier
                .padding(start = Dimens.SectionSpacingMedium)
                .clip(Dimens.CornerChip)
                .border(Dimens.AddButtonBorderWidth, OutlineColor, Dimens.CornerChip)
                .padding(horizontal = Dimens.SectionSpacingMedium, vertical = Dimens.SectionSpacingSmall)
        )
    }
}

/** Der rote Hinweis oben, wenn Benachrichtigungen nicht ankommen – wahlweise mit einem Knopf. */
@Composable
private fun PermissionNotice(text: String, action: String?, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(AccentRedSurface)
            .border(Dimens.AddButtonBorderWidth, AccentRed, Dimens.CornerCard)
            .padding(Dimens.SheetPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall)
    ) {
        Text(text = text, style = AppTextStyles.Body, color = TextPrimary)
        action?.let {
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text(text = it, color = AccentRed)
            }
        }
    }
}

/** Die Uhrzeit wählen: die Uhr von Material, im 24-Stunden-Format. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(minuteOfDay: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = minuteOfDay / MINUTES_PER_HOUR,
        initialMinute = minuteOfDay % MINUTES_PER_HOUR,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        titleContentColor = TextPrimary,
        title = { Text(text = stringResource(R.string.reminders_time_dialog_title)) },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = ScreenBackground,
                    selectorColor = AccentBlue,
                    timeSelectorSelectedContainerColor = AccentBlue,
                    timeSelectorSelectedContentColor = TextPrimary,
                    timeSelectorUnselectedContentColor = TextPrimary
                )
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * MINUTES_PER_HOUR + state.minute) }) {
                Text(text = stringResource(R.string.action_save), color = AccentBlue)
            }
        }
    )
}

private const val MINUTES_PER_HOUR = 60

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360, heightDp = 900)
@Composable
private fun RemindersScreenPreview() {
    MeinTrainingTheme {
        RemindersScreen(
            settings = ReminderSettings(pause = true),
            notificationsAllowed = false,
            asking = null,
            refusal = null,
            onDeloadToggled = {},
            onPauseToggled = {},
            onBackupToggled = {},
            onPauseDaysChange = {},
            onTimeChange = {},
            onAllow = {},
            onOpenSettings = {},
            onBack = {}
        )
    }
}
