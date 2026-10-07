package de.beispiel.meintraining.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.CardioTargets
import de.beispiel.meintraining.data.model.CardioValues
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.model.ExerciseKind
import de.beispiel.meintraining.data.model.IntensityUnit
import de.beispiel.meintraining.ui.CardioEntryForm
import de.beispiel.meintraining.ui.CardioLogDialogState
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AccentRed
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.DEFAULT_PROGRESSION_STEP_KG
import de.beispiel.meintraining.util.LastCardioEntry
import de.beispiel.meintraining.util.exerciseTitle
import java.time.LocalDate

/**
 * „Cardio eintragen“ – öffnet sich mit einem Tipp auf den Chip einer Cardio-Zeile.
 *
 * Die Felder stehen schon auf den Zielwerten, im Normalfall genügt also „Speichern“. Darüber
 * steht „Letztes Mal“, damit sich die Einheit von heute daran messen lässt. Ist heute schon eine
 * eingetragen, zeigen die Felder sie: „Speichern“ korrigiert sie, „Löschen“ nimmt sie heraus.
 *
 * Ein Dialog und kein Sheet wie beim Satz-Protokoll: Hier entsteht eine einzige Eingabe in einem
 * Zug, nicht Satz für Satz über das ganze Training.
 */
@Composable
fun CardioLogDialog(
    state: CardioLogDialogState,
    onSave: (CardioValues) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    // An Zeile und Einheit gebunden: Kommt die Einheit von heute erst nach dem Öffnen an – auf
    // einem anderen Weg eingetragen –, stehen danach ihre Werte da statt der Ziele.
    var form by remember(state.exercise.id, state.todaysLog?.id) { mutableStateOf(state.initialForm()) }
    val isCorrection = state.todaysLog != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(text = stringResource(R.string.cardio_log_title)) },
        text = {
            CardioLogDialogContent(
                state = state,
                form = form,
                onFormChange = { form = it }
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(form.toValues()) }, enabled = form.canSave) {
                Text(
                    text = stringResource(R.string.action_save),
                    color = if (form.canSave) AccentBlue else TextSecondary
                )
            }
        },
        dismissButton = {
            Row {
                if (isCorrection) {
                    TextButton(onClick = onDelete) {
                        Text(text = stringResource(R.string.action_delete), color = AccentRed)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
                }
            }
        }
    )
}

/** Name, „Letztes Mal“, der Hinweis aufs Korrigieren und die vier Felder. */
@Composable
internal fun CardioLogDialogContent(
    state: CardioLogDialogState,
    form: CardioEntryForm,
    onFormChange: (CardioEntryForm) -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall)
    ) {
        Text(
            text = exerciseTitle(state.exercise.name, state.exercise.variation),
            style = AppTextStyles.ExerciseName,
            color = TextPrimary
        )
        Text(
            text = state.lastEntry?.let { lastCardioText(it) }
                ?: stringResource(R.string.cardio_log_last_none),
            style = AppTextStyles.ColumnLabel
        )
        if (state.todaysLog != null) {
            Text(text = stringResource(R.string.cardio_log_correcting), style = AppTextStyles.ColumnLabel)
        }

        // Text statt Ziffernblock wie im Bearbeiten-Sheet: „22:30“ braucht den Doppelpunkt.
        SheetTextField(
            value = form.duration,
            onValueChange = { onFormChange(form.copy(duration = it)) },
            label = stringResource(R.string.field_cardio_duration),
            keyboardType = KeyboardType.Text,
            isError = !form.isDurationValid,
            supportingText = if (form.isDurationValid) null else stringResource(R.string.error_cardio_duration)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SheetFieldSpacing)) {
            SheetTextField(
                value = form.distance,
                onValueChange = { onFormChange(form.copy(distance = it)) },
                label = stringResource(R.string.field_cardio_distance),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f)
            )
            SheetTextField(
                value = form.incline,
                onValueChange = { onFormChange(form.copy(incline = it)) },
                label = stringResource(R.string.field_cardio_incline),
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f)
            )
        }
        SheetTextField(
            value = form.intensity,
            onValueChange = { onFormChange(form.copy(intensity = it)) },
            label = stringResource(
                when (form.intensityUnit) {
                    IntensityUnit.KMH -> R.string.field_cardio_speed
                    IntensityUnit.LEVEL -> R.string.field_cardio_level
                }
            ),
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360)
@Composable
private fun CardioLogDialogContentPreview() {
    val exercise = ExerciseItem(
        id = 1,
        dayId = 1,
        name = "Laufband",
        variation = null,
        sets = null,
        repsMin = null,
        repsMax = null,
        position = 0,
        supersetId = null,
        weightKg = null,
        progressionStepKg = DEFAULT_PROGRESSION_STEP_KG,
        kind = ExerciseKind.CARDIO,
        cardio = CardioTargets(durationMin = 20.0, intensity = 6.0, inclinePercent = 8.0)
    )
    val state = CardioLogDialogState(
        exercise = exercise,
        todaysLog = CardioLog(id = 3, exerciseName = "Laufband", dayId = 1, performedAt = 0, durationMin = 22.5),
        lastEntry = LastCardioEntry(
            values = CardioValues(durationMin = 22.0, distanceKm = 3.4, intensity = 6.0, intensityUnit = IntensityUnit.KMH),
            date = LocalDate.of(2026, 10, 4),
            daysAgo = 3
        )
    )
    MeinTrainingTheme {
        CardioLogDialogContent(state = state, form = state.initialForm(), onFormChange = {})
    }
}
