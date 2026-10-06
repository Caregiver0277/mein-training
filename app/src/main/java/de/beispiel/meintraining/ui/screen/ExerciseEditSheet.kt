package de.beispiel.meintraining.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.ExerciseForm
import de.beispiel.meintraining.ui.components.Sparkline
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
import de.beispiel.meintraining.util.WeightChange
import de.beispiel.meintraining.util.WeightHistory
import de.beispiel.meintraining.util.formatShortDate
import de.beispiel.meintraining.util.parseOptionalInt
import de.beispiel.meintraining.util.parseProgressionStep
import de.beispiel.meintraining.util.toDecimalString
import de.beispiel.meintraining.util.toSignedDecimalString
import java.time.LocalDate
import kotlin.math.abs

/** Bottom-Sheet zum Anlegen und Bearbeiten einer Übung. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditSheet(
    form: ExerciseForm,
    /** Verlauf der Übung, deren Gewicht in den Feldern steht; `null` blendet die Zeile aus. */
    weightHistory: WeightHistory?,
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
            weightHistory = weightHistory,
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
    weightHistory: WeightHistory?,
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
                    capitalization = KeyboardCapitalization.Sentences,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(variationFocus)
                )
            }
            VariationToggle(expanded = form.showVariation, onClick = onVariationToggle)
        }

        // „Weiter“ auf der Tastatur springt von Feld zu Feld, im letzten schließt „Fertig“ sie –
        // eine Übung lässt sich so in einem Zug eintippen, ohne jedes Feld einzeln anzutippen.
        SheetTextField(
            value = form.weight,
            onValueChange = { onFormChange(form.copy(weight = it)) },
            label = stringResource(R.string.field_weight),
            keyboardType = KeyboardType.Decimal,
            supportingText = stringResource(R.string.hint_weight_shared)
        )
        weightHistory?.let { WeightHistoryLine(history = it) }

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
            imeAction = ImeAction.Done,
            supportingText = stringResource(
                if (form.progressionDown) {
                    R.string.hint_progression_step_down
                } else {
                    R.string.hint_progression_step
                }
            )
        )

        Text(
            text = stringResource(R.string.quick_select_label),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary
        )
        // Die Richtung steht neben der Schnellauswahl und nicht darunter: Schritt und Richtung
        // beschreiben zusammen eine Bewegung, und der Knopf sitzt damit auf derselben Höhe wie
        // die Stufen, auf die er sich bezieht.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressionStepChips(
                value = form.progressionStep,
                onSelect = { onFormChange(form.copy(progressionStep = it)) },
                modifier = Modifier.weight(1f)
            )
            ProgressionDirectionToggle(
                down = form.progressionDown,
                onClick = { onFormChange(form.copy(progressionDown = !form.progressionDown)) }
            )
        }

        // Mehrzeilig: Ein Zeilenumbruch ist hier ein Zeilenumbruch und kein „Weiter“. In der
        // Liste stehen die Zeilen dann hintereinander (siehe noteLine).
        SheetTextField(
            value = form.note,
            onValueChange = { onFormChange(form.copy(note = it)) },
            label = stringResource(R.string.field_note),
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Default,
            singleLine = false,
            supportingText = stringResource(R.string.hint_note_shared)
        )

        LogSetsSwitch(
            checked = form.logSets,
            // Ohne Sätze-Zahl gibt es in der Liste keinen Chip und damit nichts anzutippen – das
            // steht dann gleich hier, statt dass der Schalter scheinbar nichts bewirkt.
            needsSets = (parseOptionalInt(form.sets) ?: 0) < 1,
            onCheckedChange = { onFormChange(form.copy(logSets = it)) }
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
private fun ProgressionStepChips(
    value: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeStep = remember(value) { parseProgressionStep(value) }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall),
        modifier = modifier
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
 * Schalter „Sätze protokollieren“ unter der Notiz.
 *
 * Er hängt wie Gewicht und Notiz am Namen; das sagt der Hinweis darunter. Ist er an, aber keine
 * Sätze-Zahl eingetragen, tritt an dessen Stelle, dass das Protokoll eine braucht.
 */
@Composable
private fun LogSetsSwitch(
    checked: Boolean,
    needsSets: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerChip)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.field_log_sets),
                style = AppTextStyles.Body,
                color = TextPrimary
            )
            Text(
                text = stringResource(
                    if (checked && needsSets) {
                        R.string.hint_log_sets_needs_sets
                    } else {
                        R.string.hint_log_sets
                    }
                ),
                style = AppTextStyles.ColumnLabel,
                color = if (checked && needsSets) TextPrimary else TextSecondary,
                modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
            )
        }
        // Die ganze Zeile schaltet; der Schalter selbst zeigt nur an.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
            modifier = Modifier.padding(start = Dimens.SectionSpacingMedium)
        )
    }
}

/**
 * Kleiner Kasten mit Pfeil nach unten, rechts neben der Schnellauswahl: Angetippt steht er blau
 * da und der Pfeil in der Liste senkt das Gewicht, statt es zu erhöhen.
 *
 * Der Pfeil im Kasten zeigt immer nach unten – er sagt, was der Knopf bewirkt, nicht was gerade
 * gilt. Was gerade gilt, sagt die blaue Markierung, genau wie bei der gewählten Stufe daneben.
 */
@Composable
private fun ProgressionDirectionToggle(down: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Dimens.TouchTargetSize)
            .clip(Dimens.CornerChip)
            .background(if (down) AccentBlueSurface else ChipBackground)
            .border(
                width = Dimens.AddButtonBorderWidth,
                color = if (down) AccentBlue else OutlineColor,
                shape = Dimens.CornerChip
            )
            .toggleable(
                value = down,
                role = Role.Checkbox,
                onValueChange = { onClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_downward),
            contentDescription = stringResource(R.string.cd_progression_down),
            tint = if (down) AccentBlue else TextPrimary,
            modifier = Modifier.size(Dimens.MenuIconSize)
        )
    }
}

/**
 * Namensfeld mit Vorschlagsliste: Ab dem ersten Buchstaben werden passende, bereits
 * angelegte Übungen angeboten. Die Auswahl läuft über den normalen Weg der Namensänderung –
 * das ViewModel übernimmt dabei Gewicht und Progressionsschritt.
 *
 * Die Liste erscheint nur, während am Namen getippt wird. Sie liegt über den Feldern darunter,
 * und beim Öffnen einer Übung, deren Name der Anfang einer anderen ist – „Bankdrücken“ neben
 * „Bankdrücken KH“ –, stünde sie sonst sofort über Gewicht und Sätzen. Ein Tipp daneben oder
 * der Sprung ins nächste Feld blendet sie aus; weitertippen holt sie zurück.
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
    var hasFocus by remember { mutableStateOf(false) }
    // Erst ein Tastendruck öffnet die Liste, ein Tipp daneben schließt sie wieder.
    var isTyping by remember { mutableStateOf(false) }
    val expanded = hasFocus && isTyping && suggestions.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { /* Die Liste steuert allein der eingegebene Text. */ },
        modifier = modifier
    ) {
        SheetTextField(
            value = form.name,
            onValueChange = {
                isTyping = true
                onFormChange(form.copy(name = it))
            },
            label = stringResource(R.string.field_name),
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Sentences,
            isError = form.name.isBlank(),
            supportingText = if (form.name.isBlank()) {
                stringResource(R.string.error_name_required)
            } else {
                null
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .onFocusChanged { hasFocus = it.isFocused }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { isTyping = false }
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(text = suggestion, style = AppTextStyles.ExerciseName) },
                    onClick = {
                        // Gewählt ist gewählt: Die Liste bliebe sonst stehen, sobald es eine
                        // längere Übung gleichen Anfangs gibt – nach „Bankdrücken“ etwa noch
                        // „Bankdrücken KH“.
                        isTyping = false
                        onFormChange(form.copy(name = suggestion))
                    }
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

/**
 * Ein Eingabefeld des Sheets.
 *
 * [capitalization] ist für Namen gedacht: Übungen schreiben sich groß, ohne Vorgabe beginnt die
 * Tastatur aber klein.
 */
@OptIn(ExperimentalMaterial3Api::class)
/**
 * „60 kg seit 12 Tagen · zuletzt +2,5 kg am 18. Sept.“ und daneben die kleine Kurve der
 * jüngsten Werte – damit beim Anpassen des Gewichts sichtbar ist, wie es bisher lief.
 *
 * Das Gewicht kommt aus dem Verlauf, nicht aus dem Feld darüber: Die Zeile erzählt, was war,
 * und springt nicht beim Tippen mit.
 */
@Composable
private fun WeightHistoryLine(history: WeightHistory) {
    val weight = history.currentKg.toDecimalString()
    val since = if (history.daysSince == 0) {
        stringResource(R.string.weight_history_since_today, weight)
    } else {
        pluralStringResource(
            R.plurals.weight_history_since,
            history.daysSince,
            weight,
            history.daysSince
        )
    }
    val text = history.lastChange?.let { change: WeightChange ->
        stringResource(
            R.string.weight_history_with_change,
            since,
            change.deltaKg.toSignedDecimalString(),
            formatShortDate(change.date)
        )
    } ?: since
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SectionSpacingLarge),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Sparkline(
            values = history.recentWeights,
            modifier = Modifier.padding(start = Dimens.SectionSpacingMedium)
        )
    }
}

@Composable
private fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(text = label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else NOTE_MIN_LINES,
        maxLines = if (singleLine) 1 else NOTE_MAX_LINES,
        isError = isError,
        supportingText = supportingText?.let { text ->
            { Text(text = text, style = AppTextStyles.ColumnLabel) }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
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

/**
 * Höhe des Notizfelds in Zeilen: zwei gleich zu Beginn, damit es als mehrzeilig erkennbar ist,
 * höchstens vier – darüber scrollt es in sich, statt die Knöpfe aus dem Sheet zu schieben.
 */
private const val NOTE_MIN_LINES = 2
private const val NOTE_MAX_LINES = 4

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
                progressionStep = "0,625",
                note = "Kabel ganz oben, Ellbogen fest",
                logSets = true
            ),
            weightHistory = WeightHistory(
                currentKg = 20.0,
                since = LocalDate.of(2026, 9, 18),
                daysSince = 12,
                lastChange = WeightChange(deltaKg = 0.625, date = LocalDate.of(2026, 9, 18)),
                recentWeights = listOf(17.5, 18.125, 18.75, 18.75, 19.375, 20.0)
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
