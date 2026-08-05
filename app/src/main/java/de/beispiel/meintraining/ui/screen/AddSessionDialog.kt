package de.beispiel.meintraining.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.TabActiveSurface
import de.beispiel.meintraining.ui.theme.TabActiveText
import de.beispiel.meintraining.ui.theme.TabInactiveSurface
import de.beispiel.meintraining.ui.theme.TabInactiveText
import de.beispiel.meintraining.ui.theme.TextDisabled
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.formatClockTime
import de.beispiel.meintraining.util.formatFullDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Trägt ein vergessenes Training nach: Trainingstag, Datum, Uhrzeit.
 *
 * Der Haken hakt immer *jetzt* ab – wer ihn vergisst, hat sonst keine Möglichkeit mehr, das
 * Training von gestern in den Verlauf zu bekommen, und damit stimmen Runde, Streak und
 * Deload-Zyklus nicht mehr. Hier lässt sich der Zeitpunkt deshalb frei wählen.
 *
 * Nur eben nicht in der Zukunft: Ein Training, das noch nicht stattgefunden hat, verschöbe die
 * Deload-Rechnung und ließe die Runde vorzeitig voll erscheinen. Der Datumswähler gibt kommende
 * Tage gar nicht erst her, und eine Uhrzeit von heute Abend fängt der Hinweis unter den Feldern
 * ab.
 *
 * [days] sind die tatsächlich sichtbaren Trainingstage; hinter der eingestellten Rundenlänge
 * verborgene stehen bewusst nicht zur Wahl – ein Eintrag darauf zählte in keiner Runde mit.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddSessionDialog(
    days: List<TrainingDay>,
    /** Kommt von außen, damit „heute“ auch nach Mitternacht noch heute ist. */
    today: LocalDate,
    onConfirm: (dayId: Int, completedAt: Long) -> Unit,
    onDismiss: () -> Unit
) {
    // Gespeichert wird jeweils die schlichteste Form – Epochentag und Minute des Tages –, damit
    // die Auswahl eine Drehung des Geräts übersteht.
    var selectedDayId by rememberSaveable(days) { mutableIntStateOf(days.firstOrNull()?.id ?: 0) }
    var epochDay by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var minuteOfDay by rememberSaveable {
        mutableIntStateOf(LocalTime.now().let { it.hour * MINUTES_PER_HOUR + it.minute })
    }

    var isPickingDate by rememberSaveable { mutableStateOf(false) }
    var isPickingTime by rememberSaveable { mutableStateOf(false) }

    val date = remember(epochDay) { LocalDate.ofEpochDay(epochDay) }
    val time = remember(minuteOfDay) {
        LocalTime.of(minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR)
    }
    val completedAt = remember(date, time) {
        // Fällt die Uhrzeit in die Lücke der Sommerzeitumstellung, rückt Java sie von selbst
        // hinter den Sprung – das ist die einzige Auslegung, die einen Zeitstempel ergibt.
        LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    // Nur bei jeder Änderung geprüft, nicht laufend: Wer die Uhrzeit auf gleich stehen lässt,
    // bis sie vorbei ist, darf sie eintragen – dann hat das Training ja stattgefunden.
    val isFuture = remember(completedAt) { completedAt > System.currentTimeMillis() }
    val canSave = selectedDayId != 0 && !isFuture

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(text = stringResource(R.string.history_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SheetFieldSpacing)) {
                Text(text = stringResource(R.string.history_add_hint), style = AppTextStyles.Body)

                if (days.isEmpty()) {
                    Text(
                        text = stringResource(R.string.history_add_no_days),
                        style = AppTextStyles.Body,
                        color = AccentBlue
                    )
                } else {
                    FieldLabel(text = stringResource(R.string.history_add_day))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.TabSpacing),
                        verticalArrangement = Arrangement.spacedBy(Dimens.TabSpacing)
                    ) {
                        days.forEach { day ->
                            DayChip(
                                label = day.name,
                                isSelected = day.id == selectedDayId,
                                onClick = { selectedDayId = day.id }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)) {
                    PickerField(
                        label = stringResource(R.string.history_add_date),
                        value = formatFullDate(date),
                        onClick = { isPickingDate = true },
                        modifier = Modifier.weight(WEIGHT_DATE)
                    )
                    PickerField(
                        label = stringResource(R.string.history_add_time),
                        value = formatClockTime(time),
                        onClick = { isPickingTime = true },
                        modifier = Modifier.weight(WEIGHT_TIME)
                    )
                }

                if (isFuture) {
                    Text(
                        text = stringResource(R.string.history_add_future),
                        style = AppTextStyles.Body,
                        color = AccentBlue
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedDayId, completedAt) },
                enabled = canSave
            ) {
                Text(
                    text = stringResource(R.string.history_add_confirm),
                    color = if (canSave) AccentBlue else TextDisabled
                )
            }
        }
    )

    if (isPickingDate) {
        val pickerState = rememberDatePickerState(
            // Der Kalender rechnet in UTC, nicht in der Zeitzone des Geräts: Der Epochentag
            // wandert deshalb ohne Umrechnung hin und zurück.
            initialSelectedDateMillis = epochDay * MILLIS_PER_DAY,
            selectableDates = remember(today) { DatesUntilToday(today) }
        )
        DatePickerDialog(
            onDismissRequest = { isPickingDate = false },
            colors = DatePickerDefaults.colors(containerColor = CardBackground),
            dismissButton = {
                TextButton(onClick = { isPickingDate = false }) {
                    Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            epochDay = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay()
                        }
                        isPickingDate = false
                    }
                ) {
                    Text(text = stringResource(R.string.action_done), color = AccentBlue)
                }
            }
        ) {
            DatePicker(
                state = pickerState,
                colors = DatePickerDefaults.colors(containerColor = CardBackground)
            )
        }
    }

    if (isPickingTime) {
        val timeState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { isPickingTime = false },
            containerColor = CardBackground,
            titleContentColor = TextPrimary,
            title = { Text(text = stringResource(R.string.history_add_time)) },
            // Eingabefelder statt Zifferblatt: Die Uhrzeit von gestern weiß man, man tippt sie
            // ein. Über das Zifferblatt wären es mehrere Züge für dasselbe Ergebnis.
            text = { TimeInput(state = timeState) },
            dismissButton = {
                TextButton(onClick = { isPickingTime = false }) {
                    Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        minuteOfDay = timeState.hour * MINUTES_PER_HOUR + timeState.minute
                        isPickingTime = false
                    }
                ) {
                    Text(text = stringResource(R.string.action_done), color = AccentBlue)
                }
            }
        )
    }
}

/** Alles bis einschließlich heute steht zur Wahl, kommende Tage bleiben grau. */
@OptIn(ExperimentalMaterial3Api::class)
private class DatesUntilToday(today: LocalDate) : SelectableDates {

    private val lastMillis = today.toEpochDay() * MILLIS_PER_DAY
    private val lastYear = today.year

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= lastMillis

    override fun isSelectableYear(year: Int): Boolean = year <= lastYear
}

@Composable
private fun FieldLabel(text: String) {
    Text(text = text, style = AppTextStyles.ColumnLabel, color = TextSecondary)
}

@Composable
private fun DayChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = AppTextStyles.TabLabel,
        color = if (isSelected) TabActiveText else TabInactiveText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(Dimens.CornerTab)
            .background(if (isSelected) TabActiveSurface else TabInactiveSurface)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(
                horizontal = Dimens.SectionSpacingMedium,
                vertical = Dimens.SectionSpacingSmall
            )
    )
}

/** Beschriftetes Feld, das beim Antippen den passenden Wähler öffnet. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(Dimens.CornerChip)
            .background(ChipBackground)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Dimens.SectionSpacingMedium)
    ) {
        FieldLabel(text = label)
        Text(
            text = value,
            style = AppTextStyles.ExerciseName,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SectionSpacingSmall / 2)
        )
    }
}

private const val MINUTES_PER_HOUR = 60
private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000

// Das Datum steht ausgeschrieben da und braucht mehr Platz als „18:42“.
private const val WEIGHT_DATE = 2f
private const val WEIGHT_TIME = 1f

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360, heightDp = 640)
@Composable
private fun AddSessionDialogPreview() {
    MeinTrainingTheme {
        AddSessionDialog(
            days = (1..4).map { TrainingDay(id = it, name = "Tag $it") },
            today = LocalDate.now(),
            onConfirm = { _, _ -> },
            onDismiss = {}
        )
    }
}
