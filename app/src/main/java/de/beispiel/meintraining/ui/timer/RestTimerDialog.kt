package de.beispiel.meintraining.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.local.MAX_REST_TIMER_SECONDS
import de.beispiel.meintraining.data.local.MIN_REST_TIMER_SECONDS
import de.beispiel.meintraining.data.local.SECONDS_PER_MINUTE
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.OutlineColor
import de.beispiel.meintraining.ui.theme.TextDisabled
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.formatRestTime

/** Gängige Satzpausen als Schnellauswahl, damit man selten tippen muss. */
private val QUICK_DURATIONS_SECONDS = listOf(60, 90, 120, 180)

/**
 * Stellt die Dauer einer Pausenuhr ein – Minuten und Sekunden getrennt.
 *
 * Getrennte Felder statt einer Sekundenzahl: „drei Minuten“ tippt sich als `3` und `0`, nicht
 * als `180`. Der Bestätigen-Knopf bleibt gesperrt, solange die Eingabe keine gültige Dauer
 * ergibt; so kann hier keine Uhr mit 0:00 herauskommen.
 */
@Composable
fun RestTimerDialog(
    initialSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var minutes by remember {
        mutableStateOf((initialSeconds / SECONDS_PER_MINUTE).toString())
    }
    var seconds by remember {
        mutableStateOf((initialSeconds % SECONDS_PER_MINUTE).toString())
    }

    val total = totalSeconds(minutes, seconds)
    val isValid = total != null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(text = stringResource(R.string.timer_config_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingMedium)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SheetFieldSpacing)) {
                    DurationField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(MAX_DIGITS) },
                        label = stringResource(R.string.timer_minutes),
                        modifier = Modifier.weight(1f)
                    )
                    DurationField(
                        value = seconds,
                        onValueChange = { seconds = it.filter(Char::isDigit).take(MAX_DIGITS) },
                        label = stringResource(R.string.timer_seconds),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = stringResource(R.string.quick_select_label),
                    style = AppTextStyles.ColumnLabel,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.ChipSpacing)) {
                    QUICK_DURATIONS_SECONDS.forEach { suggestion ->
                        QuickDurationChip(
                            seconds = suggestion,
                            isSelected = total == suggestion,
                            onClick = {
                                minutes = (suggestion / SECONDS_PER_MINUTE).toString()
                                seconds = (suggestion % SECONDS_PER_MINUTE).toString()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.timer_config_hint),
                    style = AppTextStyles.ColumnLabel,
                    color = TextSecondary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
            }
        },
        confirmButton = {
            TextButton(enabled = isValid, onClick = { total?.let(onConfirm) }) {
                Text(
                    text = stringResource(R.string.action_save),
                    color = if (isValid) AccentGreen else TextDisabled
                )
            }
        }
    )
}

/**
 * Die eingegebene Dauer in Sekunden, oder `null`, wenn daraus keine gültige wird.
 *
 * Leere Felder zählen als 0 – „2“ und nichts heißt zwei Minuten glatt. Die Sekunden dürfen
 * dabei über 59 stehen: Wer `0` und `90` tippt, meint anderthalb Minuten.
 */
private fun totalSeconds(minutes: String, seconds: String): Int? {
    val min = if (minutes.isBlank()) 0 else minutes.toIntOrNull() ?: return null
    val sec = if (seconds.isBlank()) 0 else seconds.toIntOrNull() ?: return null
    val total = min * SECONDS_PER_MINUTE + sec
    return total.takeIf { it in MIN_REST_TIMER_SECONDS..MAX_REST_TIMER_SECONDS }
}

@Composable
private fun DurationField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(text = label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = AccentGreen,
            unfocusedBorderColor = OutlineColor,
            focusedLabelColor = AccentGreen,
            unfocusedLabelColor = TextSecondary,
            cursorColor = AccentGreen
        ),
        modifier = modifier
    )
}

@Composable
private fun QuickDurationChip(
    seconds: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = formatRestTime(seconds),
        style = AppTextStyles.ChipText,
        color = if (isSelected) AccentGreen else TextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .clip(Dimens.CornerChip)
            .background(ChipBackground)
            .border(
                width = Dimens.AddButtonBorderWidth,
                color = if (isSelected) AccentGreen else OutlineColor,
                shape = Dimens.CornerChip
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Dimens.SectionSpacingSmall)
    )
}

private const val MAX_DIGITS = 2

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun RestTimerDialogPreview() {
    MeinTrainingTheme {
        RestTimerDialog(initialSeconds = 90, onConfirm = {}, onDismiss = {})
    }
}
