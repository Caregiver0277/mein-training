package de.beispiel.meintraining.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.model.SetLog
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.ui.SetLogSheetState
import de.beispiel.meintraining.ui.components.dayLabel
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AccentGreenSurface
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.DEFAULT_PROGRESSION_STEP_KG
import de.beispiel.meintraining.util.MAX_LOGGED_REPS
import de.beispiel.meintraining.util.SetUnit
import de.beispiel.meintraining.util.decreaseWeight
import de.beispiel.meintraining.util.exerciseTitle
import de.beispiel.meintraining.util.formatSetSeries
import de.beispiel.meintraining.util.increaseWeight
import de.beispiel.meintraining.util.setsThisWeek
import de.beispiel.meintraining.util.suggestedReps
import de.beispiel.meintraining.util.toDecimalString
import de.beispiel.meintraining.util.toSetsRepsLabel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Sheet „Satz-Protokoll“: Satz für Satz Gewicht und Wiederholungen einer Übung, während des
 * Trainings.
 *
 * Die Meldungen mit „Rückgängig“ stehen hier im Sheet selbst ([snackbarHostState]): Es liegt in
 * einem eigenen Fenster über dem Hauptscreen, dessen Meldungen darunter verschwänden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetLogSheet(
    state: SetLogSheetState,
    /** Für den Namen des Tages, von dem „Letztes Mal“ stammt, wenn es ein anderer ist. */
    days: List<TrainingDay>,
    snackbarHostState: SnackbarHostState,
    onLogSet: (setNumber: Int, reps: Int, weightKg: Double?) -> Unit,
    onUpdateSet: (id: Long, reps: Int, weightKg: Double?) -> Unit,
    onDeleteSet: (id: Long) -> Unit,
    /** Der Knopf am Hinweis „Oberes Ende erreicht“ – tut genau, was der Pfeil tut. */
    onStepWeight: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardBackground,
        contentColor = TextPrimary
    ) {
        Box {
            SetLogSheetContent(
                state = state,
                days = days,
                onLogSet = onLogSet,
                onUpdateSet = onUpdateSet,
                onDeleteSet = onDeleteSet,
                onStepWeight = onStepWeight,
                onDone = onDismiss
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }
}

/**
 * Ein Satz, der noch nicht gespeichert ist oder gerade korrigiert wird. [weightKg] ist `null`
 * bei einer Übung ohne Gewicht – dann gibt es nur die Wiederholungen.
 */
private data class SetDraft(val weightKg: Double?, val reps: Int)

/**
 * Kopf mit Vorgabe und „Letztes Mal“, darunter je Satz eine Zeile.
 *
 * Eine offene Zeile hat zwei Stepper – das Gewicht um den Progressionsschritt, die
 * Wiederholungen um eins – und ✓, das den Satz sofort speichert. Gespeicherte Sätze stehen
 * grün hinterlegt als Text da; angetippt werden sie wieder zur offenen Zeile, zum Korrigieren
 * oder Löschen.
 *
 * Was in einer offenen Zeile eingestellt ist, merkt sich das Sheet selbst ([drafts]) und nur
 * die Abweichung von der Vorbelegung: Ändert sich das Gewicht der Übung, während das Sheet offen
 * ist, ziehen unberührte Zeilen mit. Nach dem Speichern bleibt der Entwurf stehen – bis die
 * Datenbank den Satz meldet, zeigte die Zeile sonst ein paar Bilder lang wieder die Vorbelegung,
 * und wird der Satz später gelöscht, kommt er mit seinen Werten zurück.
 */
@Composable
private fun SetLogSheetContent(
    state: SetLogSheetState,
    days: List<TrainingDay>,
    onLogSet: (setNumber: Int, reps: Int, weightKg: Double?) -> Unit,
    onUpdateSet: (id: Long, reps: Int, weightKg: Double?) -> Unit,
    onDeleteSet: (id: Long) -> Unit,
    onStepWeight: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exercise = state.exercise
    val drafts = remember(exercise.id) { mutableStateMapOf<Int, SetDraft>() }
    /** Der gespeicherte Satz, der gerade korrigiert wird; `null`: keiner. */
    var editing by remember(exercise.id) { mutableStateOf<Int?>(null) }
    /** Bis zu welcher Nummer „+ Satz“ Zeilen angefügt hat. */
    var extraUpTo by remember(exercise.id) { mutableIntStateOf(0) }

    val resources = LocalResources.current
    val weightLabel: (Double) -> String = { weight ->
        resources.getString(R.string.set_log_kg, weight.toDecimalString())
    }
    val rowCount = maxOf(
        state.plannedSets,
        state.todaysSets.maxOfOrNull { it.setNumber } ?: 0,
        extraUpTo
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(
                start = Dimens.SheetPadding,
                end = Dimens.SheetPadding,
                bottom = Dimens.SheetPadding
            ),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall)
    ) {
        Text(
            text = stringResource(R.string.set_log_title),
            style = AppTextStyles.Title,
            color = TextPrimary
        )
        SetLogHeader(state = state, days = days, weightLabel = weightLabel)
        state.suggestedWeightKg?.let { suggested ->
            TopReachedHint(
                lower = exercise.progressionDown,
                suggestedLabel = weightLabel(suggested),
                onClick = onStepWeight
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SectionSpacingSmall))
        SetColumnHeaders(showWeight = exercise.weightKg != null)

        for (number in 1..rowCount) {
            val saved = state.todaysSets.lastOrNull { it.setNumber == number }
            if (saved != null && editing != number) {
                SavedSetRow(
                    number = number,
                    // Ohne Gewicht stünde da eine nackte Zahl; ausgeschrieben ist klar, was sie zählt.
                    text = if (saved.weightKg == null) {
                        pluralStringResource(R.plurals.set_log_reps, saved.reps, saved.reps)
                    } else {
                        formatSetSeries(listOf(saved), weightLabel)
                    },
                    onEdit = {
                        drafts[number] = SetDraft(saved.weightKg, saved.reps)
                        editing = number
                    }
                )
            } else {
                val draft = drafts[number] ?: SetDraft(
                    weightKg = exercise.weightKg,
                    reps = suggestedReps(state.lastUnit, number, exercise.repsMin, exercise.repsMax)
                )
                OpenSetRow(
                    number = number,
                    draft = draft,
                    stepKg = exercise.progressionStepKg,
                    isCorrection = saved != null,
                    onChange = { drafts[number] = it },
                    onConfirm = {
                        drafts[number] = draft
                        if (saved != null) {
                            onUpdateSet(saved.id, draft.reps, draft.weightKg)
                        } else {
                            onLogSet(number, draft.reps, draft.weightKg)
                        }
                        editing = null
                    },
                    onDelete = {
                        saved?.let { onDeleteSet(it.id) }
                        editing = null
                    },
                    onCancel = {
                        drafts.remove(number)
                        editing = null
                    }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SectionSpacingSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { extraUpTo = rowCount + 1 }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(Dimens.StepperIconSize)
                )
                Text(
                    text = stringResource(R.string.set_log_add_set),
                    color = AccentBlue,
                    modifier = Modifier.padding(start = Dimens.SectionSpacingSmall / 2)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = onDone) {
                Text(text = stringResource(R.string.action_done))
            }
        }
    }
}

/**
 * Übung samt Notiz, die Vorgabe dieser Zeile – in der Deload-Woche mit halbierten Sätzen – und
 * was letztes Mal ging.
 */
@Composable
private fun SetLogHeader(
    state: SetLogSheetState,
    days: List<TrainingDay>,
    weightLabel: (Double) -> String
) {
    val exercise = state.exercise
    Text(
        text = exerciseTitle(exercise.name, exercise.variation),
        style = AppTextStyles.ExerciseName,
        color = TextPrimary
    )
    exercise.note?.takeIf { it.isNotBlank() }?.let { note ->
        Text(text = note.trim(), style = AppTextStyles.ColumnLabel, color = TextSecondary)
    }

    val setsLabel = setsThisWeek(exercise.sets, state.isDeloadWeek)
        .toSetsRepsLabel(exercise.repsMin, exercise.repsMax)
        .orEmpty()
    val target = exercise.weightKg?.let { "$setsLabel$TARGET_SEPARATOR${weightLabel(it)}" } ?: setsLabel
    Text(
        text = stringResource(R.string.set_log_target, target),
        style = AppTextStyles.Body,
        color = TextPrimary
    )
    if (state.isDeloadWeek) {
        Text(
            text = stringResource(R.string.set_log_deload),
            style = AppTextStyles.ColumnLabel,
            color = AccentGreen
        )
    }

    Text(
        text = lastTimeText(state.lastUnit, exercise.dayId, state.today, days, weightLabel),
        style = AppTextStyles.Body,
        color = TextSecondary
    )
}

/**
 * „Letztes Mal (vor 4 Tagen): 60 kg × 12 / 11 / 10“ – stammt es von einem anderen Trainingstag,
 * steht dessen Name mit in der Klammer.
 */
@Composable
private fun lastTimeText(
    last: SetUnit?,
    dayId: Int,
    today: LocalDate,
    days: List<TrainingDay>,
    weightLabel: (Double) -> String
): String {
    if (last == null) return stringResource(R.string.set_log_last_none)
    val daysAgo = ChronoUnit.DAYS.between(last.date, today).toInt()
    val ago = if (daysAgo <= 1) {
        stringResource(R.string.history_yesterday)
    } else {
        pluralStringResource(R.plurals.history_days_ago, daysAgo, daysAgo)
    }
    val series = formatSetSeries(last.sets, weightLabel)
    if (last.dayId == dayId) return stringResource(R.string.set_log_last, ago, series)
    val day = dayLabel(last.dayId, days.firstOrNull { it.id == last.dayId }?.name)
    return stringResource(R.string.set_log_last_other_day, day, ago, series)
}

/**
 * „Oberes Ende erreicht – Gewicht erhöhen?“ mit einem Knopf, der genau das tut, was der Pfeil
 * in der Liste tut; bei einem Pfeil nach unten heißt es „senken?“. Grün wie der Pfeil, der
 * dabei in der Liste aufleuchtet.
 */
@Composable
private fun TopReachedHint(lower: Boolean, suggestedLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerChip)
            .background(AccentGreenSurface)
            .border(Dimens.BadgeBorderWidth, AccentGreen, Dimens.CornerChip)
            .padding(start = Dimens.SectionSpacingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                if (lower) R.string.set_log_top_reached_lower else R.string.set_log_top_reached_raise
            ),
            style = AppTextStyles.Body,
            color = TextPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Dimens.SectionSpacingSmall)
        )
        TextButton(onClick = onClick) {
            Icon(
                painter = painterResource(
                    if (lower) R.drawable.ic_arrow_downward else R.drawable.ic_arrow_upward
                ),
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(Dimens.StepperIconSize)
            )
            Text(
                text = suggestedLabel,
                color = AccentGreen,
                modifier = Modifier.padding(start = Dimens.SectionSpacingSmall / 2)
            )
        }
    }
}

/** Die Spaltenköpfe über den Satz-Zeilen – an denselben Breiten ausgerichtet wie die Zeilen. */
@Composable
private fun SetColumnHeaders(showWeight: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.SetLogRowPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SetLogRowSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Über der Nummer steht nichts – „Satz“ passte nicht in die schmale Spalte, und die Zahl
        // erklärt sich selbst.
        Spacer(modifier = Modifier.width(Dimens.SetLogNumberWidth))
        if (showWeight) {
            ColumnHeader(stringResource(R.string.set_log_column_weight), Dimens.SetLogWeightBlockWidth)
        }
        ColumnHeader(stringResource(R.string.set_log_column_reps), Dimens.SetLogRepsBlockWidth)
    }
}

@Composable
private fun ColumnHeader(text: String, width: Dp) {
    Text(
        text = text,
        style = AppTextStyles.ColumnLabel,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = Modifier.width(width)
    )
}

/** Ein gespeicherter Satz: grün hinterlegt, als Text; Tippen öffnet ihn zum Korrigieren. */
@Composable
private fun SavedSetRow(number: Int, text: String, onEdit: () -> Unit) {
    val editLabel = stringResource(R.string.cd_edit_set, number)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.SetLogRowHeight)
            .clip(Dimens.CornerChip)
            .background(AccentGreenSurface)
            .clickable(onClickLabel = editLabel, role = Role.Button, onClick = onEdit)
            .padding(horizontal = Dimens.SetLogRowPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SetLogRowSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SetNumber(number = number)
        Text(
            text = text,
            style = AppTextStyles.ExerciseName,
            color = TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.size(Dimens.SetLogActionSize), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(Dimens.StepperIconSize)
            )
        }
    }
}

/**
 * Ein offener Satz: Gewicht und Wiederholungen mit Steppern, ✓ speichert.
 *
 * Beim Korrigieren ([isCorrection]) steht darunter noch „Löschen“ und „Abbrechen“ – Löschen
 * gibt es bewusst nur hier, einen Schritt hinter dem Antippen, damit kein Satz aus Versehen
 * verschwindet.
 */
@Composable
private fun OpenSetRow(
    number: Int,
    draft: SetDraft,
    stepKg: Double,
    isCorrection: Boolean,
    onChange: (SetDraft) -> Unit,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerChip)
            .background(ChipBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.SetLogRowHeight)
                .padding(horizontal = Dimens.SetLogRowPadding),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SetLogRowSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SetNumber(number = number)
            draft.weightKg?.let { weight ->
                Stepper(
                    value = weight.toDecimalString(),
                    valueWidth = Dimens.SetLogWeightValueWidth,
                    lessLabel = stringResource(R.string.cd_less_weight),
                    moreLabel = stringResource(R.string.cd_more_weight),
                    onLess = { onChange(draft.copy(weightKg = decreaseWeight(weight, stepKg))) },
                    onMore = { onChange(draft.copy(weightKg = increaseWeight(weight, stepKg))) }
                )
            }
            Stepper(
                value = draft.reps.toString(),
                valueWidth = Dimens.SetLogRepsValueWidth,
                lessLabel = stringResource(R.string.cd_fewer_reps),
                moreLabel = stringResource(R.string.cd_more_reps),
                onLess = { onChange(draft.copy(reps = (draft.reps - 1).coerceAtLeast(0))) },
                onMore = { onChange(draft.copy(reps = (draft.reps + 1).coerceAtMost(MAX_LOGGED_REPS))) }
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(Dimens.SetLogActionSize)
                    .clip(CircleShape)
                    .border(Dimens.AddButtonBorderWidth, AccentGreen, CircleShape)
                    .clickable(role = Role.Button, onClick = onConfirm),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.cd_save_set, number),
                    tint = AccentGreen,
                    modifier = Modifier.size(Dimens.StepperIconSize)
                )
            }
        }
        if (isCorrection) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDelete) {
                    Text(text = stringResource(R.string.action_delete), color = AccentBlue)
                }
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
                }
            }
        }
    }
}

/** Die Nummer des Satzes, klein vor der Zeile. */
@Composable
private fun SetNumber(number: Int) {
    Text(
        text = number.toString(),
        style = AppTextStyles.ChipText,
        color = TextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(Dimens.SetLogNumberWidth)
    )
}

/**
 * „−  60  +“: ein Wert mit je einem Knopf links und rechts, gemeinsam auf einer Fläche – so ist
 * klar, dass das „+“ des Gewichts und das „−“ der Wiederholungen daneben zu Verschiedenem gehören.
 */
@Composable
private fun Stepper(
    value: String,
    valueWidth: Dp,
    lessLabel: String,
    moreLabel: String,
    onLess: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(CardBackground),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(
            painter = painterResource(R.drawable.ic_remove),
            label = lessLabel,
            onClick = onLess
        )
        Text(
            text = value,
            style = AppTextStyles.Timer,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(valueWidth)
        )
        StepperButton(
            painter = rememberVectorPainter(Icons.Filled.Add),
            label = moreLabel,
            onClick = onMore
        )
    }
}

@Composable
private fun StepperButton(painter: Painter, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Dimens.StepperButtonSize)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painter,
            contentDescription = label,
            tint = TextPrimary,
            modifier = Modifier.size(Dimens.StepperIconSize)
        )
    }
}

/** Trenner zwischen Sätzen und Gewicht in der Vorgabe: „3 x 8-12 · 60 kg“. Reine Notation. */
private const val TARGET_SEPARATOR = " · "

@Preview(showBackground = true, backgroundColor = 0xFF1C222B, widthDp = 360, heightDp = 640)
@Composable
private fun SetLogSheetContentPreview() {
    val today = LocalDate.now()
    fun set(id: Long, number: Int, reps: Int, daysAgo: Long) = SetLog(
        id = id,
        exerciseName = "Bankdrücken",
        dayId = 1,
        performedAt = today.minusDays(daysAgo).atTime(18, number).atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli(),
        setNumber = number,
        reps = reps,
        weightKg = 60.0
    )
    val last = listOf(set(1, 1, 12, 4), set(2, 2, 11, 4), set(3, 3, 10, 4))
    MeinTrainingTheme {
        SetLogSheetContent(
            state = SetLogSheetState(
                exercise = ExerciseItem(
                    id = 1, dayId = 1, name = "Bankdrücken", variation = null, sets = 3,
                    repsMin = 8, repsMax = 12, position = 0, supersetId = null, weightKg = 60.0,
                    progressionStepKg = DEFAULT_PROGRESSION_STEP_KG, note = "Bank Stufe 3",
                    logSets = true
                ),
                plannedSets = 3,
                isDeloadWeek = false,
                todaysSets = listOf(set(4, 1, 12, 0)),
                lastUnit = SetUnit(date = today.minusDays(4), dayId = 1, sets = last),
                today = today,
                suggestedWeightKg = 62.5
            ),
            days = listOf(TrainingDay(id = 1, name = "Tag 1")),
            onLogSet = { _, _, _ -> },
            onUpdateSet = { _, _, _ -> },
            onDeleteSet = {},
            onStepWeight = {},
            onDone = {}
        )
    }
}
