package de.beispiel.meintraining.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.ExerciseForm
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AccentBlueSurface
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.OutlineColor
import de.beispiel.meintraining.ui.theme.TextDisabled
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.PROGRESSION_STEP_SUGGESTIONS
import de.beispiel.meintraining.util.parseProgressionStep
import de.beispiel.meintraining.util.toDecimalString
import kotlin.math.abs

/** Bottom-Sheet zum Anlegen und Bearbeiten einer Übung. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditSheet(
    form: ExerciseForm,
    knownExerciseNames: List<String>,
    onFormChange: (ExerciseForm) -> Unit,
    onVariationToggle: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardBackground,
        contentColor = TextPrimary
    ) {
        ExerciseEditSheetContent(
            form = form,
            knownExerciseNames = knownExerciseNames,
            onFormChange = onFormChange,
            onVariationToggle = onVariationToggle,
            onSave = onSave,
            onDelete = onDelete,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun ExerciseEditSheetContent(
    form: ExerciseForm,
    knownExerciseNames: List<String>,
    onFormChange: (ExerciseForm) -> Unit,
    onVariationToggle: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(
                start = Dimens.SheetPadding,
                end = Dimens.SheetPadding,
                bottom = Dimens.SheetPadding
            ),
        verticalArrangement = Arrangement.spacedBy(Dimens.SheetFieldSpacing)
    ) {
        Text(
            text = stringResource(
                if (form.isEditMode) R.string.sheet_title_edit else R.string.sheet_title_add
            ),
            style = AppTextStyles.Title,
            color = TextPrimary
        )

        // Wer auf „+“ drückt, will sofort tippen – der Cursor springt deshalb ins neue Feld.
        // Beim Bearbeiten einer Übung, die schon eine Variation hat, passiert das nicht:
        // dort ist das Feld von Anfang an sichtbar und soll den Fokus nicht an sich reißen.
        val variationFocus = remember { FocusRequester() }
        var variationWasVisible by remember { mutableStateOf(form.showVariation) }
        LaunchedEffect(form.showVariation) {
            if (form.showVariation && !variationWasVisible) variationFocus.requestFocus()
            variationWasVisible = form.showVariation
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SheetFieldSpacing),
            verticalAlignment = Alignment.Top
        ) {
            NameField(
                form = form,
                knownExerciseNames = knownExerciseNames,
                onFormChange = onFormChange,
                modifier = Modifier.weight(1f)
            )
            if (form.showVariation) {
                SheetTextField(
                    value = form.variation,
                    onValueChange = { onFormChange(form.copy(variation = it)) },
                    label = stringResource(R.string.field_variation),
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(variationFocus)
                )
            }
            VariationToggle(expanded = form.showVariation, onClick = onVariationToggle)
        }

        SheetTextField(
            value = form.weight,
            onValueChange = { onFormChange(form.copy(weight = it)) },
            label = stringResource(R.string.field_weight),
            keyboardType = KeyboardType.Decimal,
            supportingText = stringResource(R.string.hint_weight_shared)
        )

        SheetTextField(
            value = form.sets,
            onValueChange = { onFormChange(form.copy(sets = it)) },
            label = stringResource(R.string.field_sets),
            keyboardType = KeyboardType.Number
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SheetFieldSpacing)) {
            SheetTextField(
                value = form.repsMin,
                onValueChange = { onFormChange(form.copy(repsMin = it)) },
                label = stringResource(R.string.field_reps_min),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
            SheetTextField(
                value = form.repsMax,
                onValueChange = { onFormChange(form.copy(repsMax = it)) },
                label = stringResource(R.string.field_reps_max),
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
        }

        SheetTextField(
            value = form.progressionStep,
            onValueChange = { onFormChange(form.copy(progressionStep = it)) },
            label = stringResource(R.string.field_progression_step),
            keyboardType = KeyboardType.Decimal,
            supportingText = stringResource(R.string.hint_progression_step)
        )

        Text(
            text = stringResource(R.string.quick_select_label),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary
        )
        ProgressionStepChips(
            value = form.progressionStep,
            onSelect = { onFormChange(form.copy(progressionStep = it)) }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SectionSpacingSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall)
        ) {
            if (form.isEditMode) {
                TextButton(onClick = onDelete) {
                    Text(text = stringResource(R.string.action_delete), color = AccentBlue)
                }
            }
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
            }
            Button(
                onClick = onSave,
                enabled = form.canSave,
                modifier = Modifier.weight(1f)
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        }
    }
}

/**
 * Schnellauswahl der Progressionsschritte – die Stufe, die gerade gilt, steht blau da.
 *
 * Verglichen wird der eingelesene Wert und nicht der getippte Text: „0.625“, „0,625“ und die
 * über die Schnellauswahl gesetzte Schreibweise sind derselbe Schritt und sollen auch dieselbe
 * Stufe hervorheben. Ein leeres Feld hebt die Vorgabe hervor – genau der Wert, der beim
 * Speichern einspränge (siehe [parseProgressionStep]).
 *
 * [FlowRow] statt einer Zeile: Bei großer Schriftgröße passen vier Stufen nicht mehr
 * nebeneinander, und abgeschnitten wäre die letzte nicht mehr zu treffen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressionStepChips(value: String, onSelect: (String) -> Unit) {
    val activeStep = remember(value) { parseProgressionStep(value) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall)
    ) {
        PROGRESSION_STEP_SUGGESTIONS.forEach { suggestion ->
            val label = suggestion.toDecimalString()
            // Die Vorschläge sind allesamt Brüche mit Zweierpotenz im Nenner und damit exakt
            // darstellbar; der Spielraum fängt trotzdem ab, was über getippte Ziffern
            // hereinkommt – etwa „0,6250“.
            val isActive = abs(activeStep - suggestion) < STEP_MATCH_TOLERANCE
            AssistChip(
                onClick = { onSelect(label) },
                label = { Text(text = label, style = AppTextStyles.ChipText) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (isActive) AccentBlueSurface else ChipBackground,
                    labelColor = if (isActive) AccentBlue else TextPrimary
                ),
                border = if (isActive) {
                    BorderStroke(Dimens.BadgeBorderWidth, AccentBlue)
                } else {
                    null
                }
            )
        }
    }
}

/**
 * Namensfeld mit Vorschlagsliste: Ab dem ersten Buchstaben werden passende, bereits
 * angelegte Übungen angeboten. Die Auswahl läuft über den normalen Weg der Namensänderung –
 * das ViewModel übernimmt dabei Gewicht und Progressionsschritt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NameField(
    form: ExerciseForm,
    knownExerciseNames: List<String>,
    onFormChange: (ExerciseForm) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = remember(form.name, knownExerciseNames) {
        val query = form.name.trim()
        if (query.isEmpty()) {
            emptyList()
        } else {
            knownExerciseNames.filter {
                it.startsWith(query, ignoreCase = true) && !it.equals(query, ignoreCase = true)
            }
        }
    }

    ExposedDropdownMenuBox(
        expanded = suggestions.isNotEmpty(),
        onExpandedChange = { /* Die Liste steuert allein der eingegebene Text. */ },
        modifier = modifier
    ) {
        SheetTextField(
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it)) },
            label = stringResource(R.string.field_name),
            keyboardType = KeyboardType.Text,
            isError = form.name.isBlank(),
            supportingText = if (form.name.isBlank()) {
                stringResource(R.string.error_name_required)
            } else {
                null
            },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = suggestions.isNotEmpty(),
            onDismissRequest = { }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(text = suggestion, style = AppTextStyles.ExerciseName) },
                    onClick = { onFormChange(form.copy(name = suggestion)) }
                )
            }
        }
    }
}

/** „+“ im Rechteck ganz rechts neben dem Namen; blendet das Variationsfeld ein und aus. */
@Composable
private fun VariationToggle(expanded: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.height(Dimens.SheetFieldHeight),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.TouchTargetSize)
                .clip(Dimens.CornerChip)
                .border(Dimens.AddButtonBorderWidth, OutlineColor, Dimens.CornerChip)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Add,
                contentDescription = stringResource(
                    if (expanded) R.string.cd_remove_variation else R.string.cd_add_variation
                ),
                tint = TextPrimary,
                modifier = Modifier.size(Dimens.MenuIconSize)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { text ->
            { Text(text = text, style = AppTextStyles.ColumnLabel) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = AccentBlue,
            unfocusedBorderColor = OutlineColor,
            focusedLabelColor = AccentBlue,
            unfocusedLabelColor = TextSecondary,
            cursorColor = AccentBlue,
            focusedSupportingTextColor = TextSecondary,
            unfocusedSupportingTextColor = TextSecondary,
            focusedPlaceholderColor = TextDisabled,
            unfocusedPlaceholderColor = TextDisabled
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * Spielraum beim Vergleich mit einer Stufe der Schnellauswahl.
 *
 * Kleiner als der Abstand zweier Stufen und größer als jede Ungenauigkeit, die beim Einlesen
 * getippter Ziffern entstehen kann.
 */
private const val STEP_MATCH_TOLERANCE = 1e-6

@Preview(showBackground = true, backgroundColor = 0xFF1C222B, widthDp = 360, heightDp = 720)
@Composable
private fun ExerciseEditSheetContentPreview() {
    MeinTrainingTheme {
        ExerciseEditSheetContent(
            form = ExerciseForm(
                id = 1L,
                name = "Trizeps",
                variation = "Seil",
                showVariation = true,
                weight = "20",
                sets = "3",
                repsMin = "4",
                repsMax = "6",
                // Die feinste Stufe: In der Schnellauswahl steht sie blau da.
                progressionStep = "0,625"
            ),
            knownExerciseNames = listOf("Trizeps", "Bankdrücken"),
            onFormChange = {},
            onVariationToggle = {},
            onSave = {},
            onDelete = {},
            onDismiss = {}
        )
    }
}
