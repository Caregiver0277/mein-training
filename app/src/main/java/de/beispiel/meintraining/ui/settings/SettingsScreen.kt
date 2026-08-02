package de.beispiel.meintraining.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.ui.screen.SubScreenHeader
import de.beispiel.meintraining.ui.theme.AccentBlue
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

/** Hängt die Einstellungen an ihr ViewModel. */
@Composable
fun SettingsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val uiState by viewModel.uiState.collectAsState()
    var managingExercises by remember { mutableStateOf(false) }

    // Aus der Übungsverwaltung führt „Zurück“ erst eine Ebene hoch.
    BackHandler(enabled = managingExercises) { managingExercises = false }

    if (managingExercises) {
        ManageExercisesScreen(
            exercises = uiState.exercises,
            onDeleteExercise = viewModel::onDeleteExercise,
            onBack = { managingExercises = false },
            modifier = modifier
        )
    } else {
        SettingsScreen(
            uiState = uiState,
            onAppTitleChange = viewModel::onAppTitleChange,
            onBodyweightChange = viewModel::onBodyweightChange,
            onDeloadCycleChange = viewModel::onDeloadCycleChange,
            onRenameDay = viewModel::onRenameDay,
            onManageExercises = { managingExercises = true },
            onBack = onBack,
            modifier = modifier
        )
    }
}

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onAppTitleChange: (String) -> Unit,
    onBodyweightChange: (String) -> Unit,
    onDeloadCycleChange: (String) -> Unit,
    onRenameDay: (Int, String) -> Unit,
    onManageExercises: () -> Unit,
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
                    value = uiState.bodyweightKg?.toDecimalString().orEmpty(),
                    onValueChange = onBodyweightChange,
                    label = stringResource(R.string.settings_bodyweight),
                    supportingText = stringResource(R.string.settings_bodyweight_hint),
                    keyboardType = KeyboardType.Decimal
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

            SettingsCard(title = stringResource(R.string.settings_days)) {
                uiState.days.forEach { day ->
                    SettingsField(
                        value = day.name,
                        onValueChange = { onRenameDay(day.id, it) },
                        label = stringResource(R.string.settings_day_label, day.id),
                        keyboardType = KeyboardType.Text
                    )
                }
            }

            SubmenuRow(
                title = stringResource(R.string.settings_exercises_title),
                subtitle = pluralStringResource(
                    R.plurals.settings_exercise_count,
                    uiState.exercises.size,
                    uiState.exercises.size
                ),
                onClick = onManageExercises
            )

            Spacer(modifier = Modifier.height(Dimens.ListBottomPadding))
        }
    }
}

/**
 * Übungsverwaltung als eigene Ebene.
 *
 * Anders als das Löschen einer Zeile im Trainingsplan trifft das alles auf einmal – jeden
 * Trainingstag, die Übungsdatenbank und den Gewichtsverlauf.
 */
@Composable
private fun ManageExercisesScreen(
    exercises: List<ManagedExercise>,
    onDeleteExercise: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDeletion by remember { mutableStateOf<ManagedExercise?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        SubScreenHeader(title = stringResource(R.string.settings_exercises_title), onBack = onBack)

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
                ExerciseRow(exercise = exercise, onDelete = { pendingDeletion = exercise })
            }
            item { Spacer(modifier = Modifier.height(Dimens.ListBottomPadding)) }
        }
    }

    pendingDeletion?.let { exercise ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            containerColor = CardBackground,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text(text = stringResource(R.string.settings_delete_title, exercise.name)) },
            text = { Text(text = stringResource(R.string.settings_delete_body)) },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletion = null
                        onDeleteExercise(exercise.name)
                    }
                ) {
                    Text(text = stringResource(R.string.action_delete), color = AccentBlue)
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun SettingsField(
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

@Composable
private fun ExerciseRow(exercise: ManagedExercise, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .padding(start = Dimens.SectionSpacingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
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

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360, heightDp = 800)
@Composable
private fun SettingsScreenPreview() {
    MeinTrainingTheme {
        SettingsScreen(
            uiState = SettingsUiState(
                days = (1..4).map { TrainingDay(it, "Tag $it") },
                appTitle = "",
                bodyweightKg = 74.5,
                deloadCycleWeeks = 6,
                exercises = listOf(ManagedExercise("Bizep curls", 2, 4))
            ),
            onAppTitleChange = {},
            onBodyweightChange = {},
            onDeloadCycleChange = {},
            onRenameDay = { _, _ -> },
            onManageExercises = {},
            onBack = {}
        )
    }
}
