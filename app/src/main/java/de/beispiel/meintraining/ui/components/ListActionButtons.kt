package de.beispiel.meintraining.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AccentGreenSurface
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.OutlineColor
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary

/**
 * Abschluss der Liste: links der Haken, der das Training in den Verlauf einträgt,
 * rechts kompakt das „+“ zum Anlegen einer Übung.
 *
 * [isCompleted] färbt den Haken grün und sperrt ihn: Jeder Trainingstag wird pro Runde nur
 * einmal abgehakt. Grün bleibt er, bis alle Tage dran waren – so sieht man auf einen Blick,
 * welche Tage der Runde noch offen sind.
 */
@Composable
fun ListActionButtons(
    onCompleteWorkout: () -> Unit,
    onAddExercise: () -> Unit,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
    ) {
        CompleteWorkoutButton(
            onClick = onCompleteWorkout,
            isCompleted = isCompleted,
            modifier = Modifier.weight(1f)
        )
        AddExerciseButton(onClick = onAddExercise)
    }
}

@Composable
private fun CompleteWorkoutButton(
    onClick: () -> Unit,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = if (isCompleted) AccentGreen else TextSecondary
    Box(
        modifier = modifier
            .height(Dimens.AddButtonHeight)
            .clip(Dimens.CornerAddButton)
            .background(if (isCompleted) AccentGreenSurface else Color.Transparent)
            .border(
                width = Dimens.AddButtonBorderWidth,
                color = if (isCompleted) accent else OutlineColor,
                shape = Dimens.CornerAddButton
            )
            // Abgehakt ist abgehakt: kein zweiter Eintrag für denselben Tag.
            .clickable(enabled = !isCompleted, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = stringResource(
                if (isCompleted) R.string.cd_workout_completed else R.string.cd_complete_workout
            ),
            tint = accent,
            modifier = Modifier.size(Dimens.MenuIconSize)
        )
    }
}

/** Transparenter Button mit 1dp-Rahmen und zentriertem „+“. */
@Composable
private fun AddExerciseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(Dimens.AddButtonWidth)
            .height(Dimens.AddButtonHeight)
            .clip(Dimens.CornerAddButton)
            .border(Dimens.AddButtonBorderWidth, OutlineColor, Dimens.CornerAddButton)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.cd_add_exercise),
            tint = TextPrimary,
            modifier = Modifier.size(Dimens.MenuIconSize)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360)
@Composable
private fun ListActionButtonsPreview() {
    MeinTrainingTheme {
        ListActionButtons(
            onCompleteWorkout = {},
            onAddExercise = {},
            isCompleted = true,
            modifier = Modifier.padding(Dimens.ScreenPaddingHorizontal)
        )
    }
}
