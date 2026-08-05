package de.beispiel.meintraining.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.model.MAX_DAY_COUNT
import de.beispiel.meintraining.data.model.MIN_DAY_COUNT
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.ui.screen.SubScreenHeader
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AccentRed
import de.beispiel.meintraining.ui.theme.AccentRedSurface
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.OutlineColor
import de.beispiel.meintraining.ui.theme.TextDisabled
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.MAX_CYCLE_WEEKS
import de.beispiel.meintraining.util.MIN_CYCLE_WEEKS
import de.beispiel.meintraining.util.toDecimalString

/** Die Ebenen der Einstellungen; die Untermenüs sind eigene Seiten. */
private enum class SettingsSection { OVERVIEW, DAYS, EXERCISES, BACKUP }

/** Hängt die Einstellungen an ihr ViewModel. */
@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(SettingsSection.OVERVIEW) }

    // Aus einem Untermenü führt „Zurück“ erst eine Ebene hoch.
    BackHandler(enabled = section != SettingsSection.OVERVIEW) {
        section = SettingsSection.OVERVIEW
    }

    when (section) {
        SettingsSection.DAYS -> ManageDaysScreen(
            days = uiState.days,
            dayCount = uiState.dayCount,
            onDayCountChange = viewModel::onDayCountChange,
            onRenameDay = viewModel::onRenameDay,
            onBack = { section = SettingsSection.OVERVIEW },
            modifier = modifier
        )
        SettingsSection.BACKUP -> BackupRoute(
            onBack = { section = SettingsSection.OVERVIEW },
            modifier = modifier
        )
        SettingsSection.EXERCISES -> ManageExercisesScreen(
            exercises = uiState.exercises,
            onDeleteExercises = viewModel::onDeleteExercises,
            onBack = { section = SettingsSection.OVERVIEW },
            modifier = modifier
        )
        SettingsSection.OVERVIEW -> SettingsScreen(
            uiState = uiState,
            onAppTitleChange = viewModel::onAppTitleChange,
            onDeloadCycleChange = viewModel::onDeloadCycleChange,
            onTimerSoundToggled = viewModel::onTimerSoundToggled,
            onManageDays = { section = SettingsSection.DAYS },
            onManageExercises = { section = SettingsSection.EXERCISES },
            onManageBackup = { section = SettingsSection.BACKUP },
            onDeleteAllData = viewModel::onDeleteAllData,
            onBack = onBack,
            modifier = modifier
        )
    }
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onAppTitleChange: (String) -> Unit,
    onDeloadCycleChange: (String) -> Unit,
    onTimerSoundToggled: (Boolean) -> Unit,
    onManageDays: () -> Unit,
    onManageExercises: () -> Unit,
    onManageBackup: () -> Unit,
    onDeleteAllData: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        SubScreenHeader(title = stringResource(R.string.drawer_settings), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
        ) {
            SettingsCard(title = stringResource(R.string.settings_general)) {
                SettingsField(
                    value = uiState.appTitle,
                    onValueChange = onAppTitleChange,
                    label = stringResource(R.string.settings_app_title),
                    supportingText = stringResource(R.string.settings_app_title_hint),
                    keyboardType = KeyboardType.Text
                )
                SettingsField(
                    value = uiState.deloadCycleWeeks.toString(),
                    onValueChange = onDeloadCycleChange,
                    label = stringResource(R.string.settings_deload_weeks),
                    supportingText = stringResource(
                        R.string.settings_deload_weeks_hint,
                        MIN_CYCLE_WEEKS,
                        MAX_CYCLE_WEEKS
                    ),
                    keyboardType = KeyboardType.Number,
                    // Eine Zahl außerhalb des erlaubten Bereichs wird nicht gespeichert und
                    // soll deshalb auch nicht im Feld stehen bleiben.
                    resetOnFocusLoss = true
                )
            }

            SettingsCard(title = stringResource(R.string.settings_timers)) {
                SwitchRow(
                    label = stringResource(R.string.settings_timer_sound),
                    hint = stringResource(R.string.settings_timer_sound_hint),
                    checked = uiState.timerSoundEnabled,
                    onCheckedChange = onTimerSoundToggled
                )
            }

            SubmenuRow(
                title = stringResource(R.string.settings_days),
                subtitle = pluralStringResource(
                    R.plurals.settings_day_count_summary,
                    uiState.dayCount,
                    uiState.dayCount
                ),
                onClick = onManageDays
            )

            SubmenuRow(
                title = stringResource(R.string.settings_exercises_title),
                subtitle = pluralStringResource(
                    R.plurals.settings_exercise_count,
                    uiState.exercises.size,
                    uiState.exercises.size
                ),
                onClick = onManageExercises
            )

            SubmenuRow(
                title = stringResource(R.string.settings_backup),
                subtitle = stringResource(R.string.settings_backup_summary),
                onClick = onManageBackup
            )

            DangerZone(onDeleteAllData = onDeleteAllData)

            Spacer(modifier = Modifier.height(Dimens.ListBottomPadding))
        }
    }
}

/**
 * Der einzige Knopf der App, der wirklich alles vernichtet – deshalb steht er unten, ist rot
 * abgesetzt und fragt vorher nach. Der Dialog zählt auf, was genau verschwindet: „Alle Daten“
 * ist zu abstrakt, um es guten Gewissens zu bestätigen.
 */
@Composable
private fun DangerZone(onDeleteAllData: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val requiredWord = stringResource(R.string.settings_delete_all_word)
    val mayDelete = typed.forConfirmation() == requiredWord.forConfirmation()
    // Nach dem Löschen sieht der Bildschirm fast unverändert aus – ohne Rückmeldung bliebe
    // offen, ob der Knopf überhaupt etwas getan hat.
    val context = LocalContext.current
    val doneMessage = stringResource(R.string.settings_delete_all_done)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .padding(Dimens.SheetPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall)
    ) {
        Text(
            text = stringResource(R.string.settings_danger_zone),
            style = AppTextStyles.ExerciseName,
            color = TextPrimary
        )
        Text(
            text = stringResource(R.string.settings_delete_all_summary),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SectionSpacingSmall)
                .clip(Dimens.CornerChip)
                .background(AccentRedSurface)
                .border(Dimens.AddButtonBorderWidth, AccentRed, Dimens.CornerChip)
                .clickable(role = Role.Button) {
                    typed = ""
                    confirming = true
                }
                .padding(Dimens.SectionSpacingMedium),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(Dimens.MenuIconSize)
            )
            Text(
                text = stringResource(R.string.settings_delete_all),
                style = AppTextStyles.Body,
                color = AccentRed,
                modifier = Modifier.padding(start = Dimens.SectionSpacingSmall)
            )
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = CardBackground,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text(text = stringResource(R.string.settings_delete_all_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingMedium)) {
                    Text(text = stringResource(R.string.settings_delete_all_body))
                    Text(
                        text = stringResource(R.string.settings_delete_all_prompt, requiredWord),
                        style = AppTextStyles.Body,
                        color = TextPrimary
                    )
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        label = { Text(text = requiredWord) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            autoCorrectEnabled = false
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = AccentRed,
                            unfocusedBorderColor = OutlineColor,
                            focusedLabelColor = AccentRed,
                            unfocusedLabelColor = TextSecondary,
                            cursorColor = AccentRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    // Erst wenn das Wort dasteht. Ein Fehlgriff auf diesen Knopf kostet sonst
                    // den gesamten Trainingsverlauf, und den holt niemand zurück.
                    enabled = mayDelete,
                    onClick = {
                        confirming = false
                        onDeleteAllData()
                        Toast.makeText(context, doneMessage, Toast.LENGTH_LONG).show()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.settings_delete_all_confirm),
                        color = if (mayDelete) AccentRed else TextDisabled
                    )
                }
            }
        )
    }
}

/**
 * Bringt die Eingabe auf eine vergleichbare Form: Groß- und Kleinschreibung ist egal, und
 * „löschen“ zählt genauso wie „loeschen“ – am Umlaut soll es nicht scheitern.
 */
private fun String.forConfirmation(): String = trim().lowercase()
    .replace("ä", "ae")
    .replace("ö", "oe")
    .replace("ü", "ue")

/**
 * Trainingstage als eigene Ebene: wie viele es sind und wie sie heißen.
 *
 * Die Anzahl steht oben, weil sie bestimmt, wie viele Namensfelder darunter überhaupt
 * auftauchen.
 */
@Composable
private fun ManageDaysScreen(
    days: List<TrainingDay>,
    dayCount: Int,
    onDayCountChange: (String) -> Unit,
    onRenameDay: (Int, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        SubScreenHeader(title = stringResource(R.string.settings_days), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
        ) {
            Text(
                text = stringResource(R.string.settings_days_hint),
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary
            )

            SettingsCard(title = stringResource(R.string.settings_day_count_card)) {
                SettingsField(
                    value = dayCount.toString(),
                    onValueChange = onDayCountChange,
                    label = stringResource(R.string.settings_day_count),
                    supportingText = stringResource(
                        R.string.settings_day_count_hint,
                        MIN_DAY_COUNT,
                        MAX_DAY_COUNT
                    ),
                    keyboardType = KeyboardType.Number,
                    resetOnFocusLoss = true
                )
            }

            SettingsCard(title = stringResource(R.string.settings_day_names)) {
                days.forEach { day ->
                    SettingsField(
                        value = day.name,
                        onValueChange = { onRenameDay(day.id, it) },
                        label = stringResource(R.string.settings_day_label, day.id),
                        keyboardType = KeyboardType.Text
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.ListBottomPadding))
        }
    }
}

/**
 * Übungsverwaltung als eigene Ebene.
 *
 * Anders als das Löschen einer Zeile im Trainingsplan trifft das alles auf einmal – jeden
 * Trainingstag, die Übungsdatenbank und den Gewichtsverlauf. Deshalb geht es hier immer über
 * eine Rückfrage, auch bei einer einzelnen Übung.
 *
 * Langer Druck startet die Auswahl, danach schaltet ein Tippen sie um – dieselbe Geste wie in
 * der Trainingsliste.
 */
@Composable
private fun ManageExercisesScreen(
    exercises: List<ManagedExercise>,
    onDeleteExercises: (Set<String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmDeletion by remember { mutableStateOf(false) }

    // Übungen, die inzwischen weg sind, dürfen nicht markiert bleiben.
    val names = exercises.map { it.name }
    val selection = selected intersect names.toSet()
    val isSelectionMode = selection.isNotEmpty()

    // „Zurück“ beendet erst die Auswahl, dann die Ebene.
    BackHandler(enabled = isSelectionMode) { selected = emptySet() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        if (isSelectionMode) {
            ExerciseSelectionBar(
                count = selection.size,
                allSelected = selection.size == names.size,
                onToggleAll = {
                    selected = if (selection.size == names.size) emptySet() else names.toSet()
                },
                onClear = { selected = emptySet() },
                onDelete = { confirmDeletion = true }
            )
        } else {
            SubScreenHeader(
                title = stringResource(R.string.settings_exercises_title),
                onBack = onBack
            )
        }

        Text(
            text = stringResource(R.string.settings_exercises_hint),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = Dimens.SectionSpacingMedium)
        )

        if (exercises.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.settings_no_exercises),
                    style = AppTextStyles.Body,
                    color = TextSecondary
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
        ) {
            items(items = exercises, key = { it.name }) { exercise ->
                ExerciseRow(
                    exercise = exercise,
                    isSelectionMode = isSelectionMode,
                    isSelected = exercise.name in selection,
                    onToggleSelection = {
                        selected = if (exercise.name in selection) {
                            selection - exercise.name
                        } else {
                            selection + exercise.name
                        }
                    },
                    onDelete = {
                        selected = setOf(exercise.name)
                        confirmDeletion = true
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(Dimens.ListBottomPadding)) }
        }
    }

    if (confirmDeletion && selection.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { confirmDeletion = false },
            containerColor = CardBackground,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = if (selection.size == 1) {
                        stringResource(R.string.settings_delete_title, selection.first())
                    } else {
                        pluralStringResource(
                            R.plurals.settings_delete_selected_title,
                            selection.size,
                            selection.size
                        )
                    }
                )
            },
            text = {
                Text(
                    text = if (selection.size == 1) {
                        stringResource(R.string.settings_delete_body)
                    } else {
                        // Die Namen mit aufzählen: Bei einer langen Auswahl ist eine Zahl
                        // allein zu wenig, um die Löschung zu verantworten.
                        stringResource(
                            R.string.settings_delete_selected_body,
                            names.filter { it in selection }.joinToString(separator = ", ")
                        )
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletion = false }) {
                    Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeletion = false
                        onDeleteExercises(selection)
                        selected = emptySet()
                    }
                ) {
                    Text(text = stringResource(R.string.action_delete), color = AccentBlue)
                }
            }
        )
    }
}

/** Kopfzeile im Auswahlmodus der Übungsverwaltung. */
@Composable
private fun ExerciseSelectionBar(
    count: Int,
    allSelected: Boolean,
    onToggleAll: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SectionSpacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClear, modifier = Modifier.size(Dimens.TouchTargetSize)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cd_end_selection),
                tint = TextPrimary,
                modifier = Modifier.size(Dimens.MenuIconSize)
            )
        }
        Text(
            text = pluralStringResource(R.plurals.selection_count, count, count),
            style = AppTextStyles.Title,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.SectionSpacingSmall)
        )
        // Das Kästchen für „alle auswählen“ erscheint erst, wenn schon etwas markiert ist –
        // vorher gäbe es nichts, worauf es sich bezöge.
        Row(
            modifier = Modifier.clickable(role = Role.Checkbox, onClick = onToggleAll),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_select_all),
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary
            )
            Checkbox(
                checked = allSelected,
                onCheckedChange = { onToggleAll() },
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentBlue,
                    uncheckedColor = TextSecondary,
                    checkmarkColor = TextPrimary
                )
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(Dimens.TouchTargetSize)) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = TextPrimary,
                modifier = Modifier.size(Dimens.MenuIconSize)
            )
        }
    }
}

/** Auch der Sicherungsbereich baut seine Abschnitte damit – deshalb nicht dateiprivat. */
@Composable
internal fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .padding(Dimens.SheetPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SheetFieldSpacing)
    ) {
        Text(text = title, style = AppTextStyles.ExerciseName, color = TextPrimary)
        content()
    }
}

/**
 * Ein Schalter mit Erklärung darunter.
 *
 * Die ganze Zeile schaltet um, nicht nur der Schalter selbst: Der Text sagt, worum es geht, und
 * ein Ziel von 48 dp Höhe trifft man auch mit klammen Fingern.
 */
@Composable
private fun SwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerChip)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = AppTextStyles.Body, color = TextPrimary)
            Text(
                text = hint,
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary,
                modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
            modifier = Modifier.padding(start = Dimens.SectionSpacingMedium)
        )
    }
}

@Composable
private fun SubmenuRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Dimens.SheetPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AppTextStyles.ExerciseName, color = TextPrimary)
            Text(
                text = subtitle,
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary,
                modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(Dimens.MenuIconSize)
        )
    }
}

/**
 * Eingabefeld, das seinen Inhalt selbst hält und jede Änderung sofort weitergibt.
 * Der gespeicherte Wert kommt verzögert zurück; er überschreibt die Eingabe nur, wenn er
 * wirklich abweicht – sonst spränge der Cursor beim Tippen.
 *
 * [resetOnFocusLoss] ist für Felder gedacht, die eine Eingabe auch ablehnen können: Beim
 * Verlassen gilt dann wieder der gespeicherte Wert, statt dass eine nie angenommene Zahl
 * stehen bleibt. Für Felder, deren Eingabe nur verzögert gespeichert wird, wäre das falsch –
 * dort stünde beim Weitertippen kurz der alte Wert im Feld.
 */
@Composable
internal fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    supportingText: String? = null,
    resetOnFocusLoss: Boolean = false
) {
    var text by remember { mutableStateOf(value) }
    var hasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(value) { if (value != text) text = value }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onValueChange(it)
        },
        label = { Text(text = label) },
        singleLine = true,
        supportingText = supportingText?.let { hint ->
            { Text(text = hint, style = AppTextStyles.ColumnLabel) }
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
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (resetOnFocusLoss && hasFocus && !state.isFocused) text = value
                hasFocus = state.isFocused
            }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseRow(
    exercise: ManagedExercise,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .border(
                width = if (isSelected) Dimens.SelectionBorderWidth else 0.dp,
                color = if (isSelected) AccentBlue else Color.Transparent,
                shape = Dimens.CornerCard
            )
            // Langer Druck startet die Auswahl; im Auswahlmodus schaltet ein Tippen um.
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() },
                onLongClick = onToggleSelection
            )
            .padding(start = Dimens.SectionSpacingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                colors = CheckboxDefaults.colors(
                    checkedColor = AccentBlue,
                    uncheckedColor = TextSecondary,
                    checkmarkColor = TextPrimary
                )
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                style = AppTextStyles.ExerciseName,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (exercise.dayCount == 0) {
                    pluralStringResource(
                        R.plurals.settings_only_history,
                        exercise.historyEntries,
                        exercise.historyEntries
                    )
                } else {
                    pluralStringResource(
                        R.plurals.settings_day_count,
                        exercise.dayCount,
                        exercise.dayCount
                    )
                },
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary,
                modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
            )
        }
        // Im Auswahlmodus wäre ein zweiter Löschweg je Zeile nur verwirrend.
        if (!isSelectionMode) {
            IconButton(onClick = onDelete, modifier = Modifier.size(Dimens.TouchTargetSize)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.settings_delete_everywhere),
                    tint = TextSecondary,
                    modifier = Modifier.size(Dimens.MenuIconSize)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360, heightDp = 800)
@Composable
private fun SettingsScreenPreview() {
    MeinTrainingTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                days = (1..4).map { TrainingDay(it, "Tag $it") },
                dayCount = 4,
                appTitle = "",
                deloadCycleWeeks = 6,
                exercises = listOf(ManagedExercise("Bizep curls", 2, 4))
            ),
            onAppTitleChange = {},
            onDeloadCycleChange = {},
            onTimerSoundToggled = {},
            onManageDays = {},
            onManageExercises = {},
            onManageBackup = {},
            onDeleteAllData = {},
            onBack = {}
        )
    }
}
