package de.beispiel.meintraining.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.data.model.WorkoutSession
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.MenuButtonIcon
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.formatFullDate
import de.beispiel.meintraining.util.toClockTime
import de.beispiel.meintraining.util.toLocalDate
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Ein Trainingstag im Verlauf mit allen an diesem Tag abgehakten Einheiten. */
private data class HistoryDay(val date: LocalDate, val sessions: List<WorkoutSession>)

@Composable
fun HistoryRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
    val uiState by viewModel.uiState.collectAsState()
    HistoryScreen(
        sessions = uiState.sessions,
        days = uiState.days,
        selectableDays = uiState.selectableDays,
        today = uiState.today,
        onDeleteSession = viewModel::onDeleteSession,
        onAddSession = viewModel::onAddSession,
        onBack = onBack,
        modifier = modifier
    )
}

/**
 * Verlauf: welche Trainings wann abgehakt wurden.
 *
 * Oben eine kurze Bilanz – so sieht man auf einen Blick, ob man dran ist –, darunter die
 * Tage von heute rückwärts. Das „+“ in der Kopfzeile trägt ein vergessenes Training nach,
 * der lange Druck auf eine Zeile nimmt eines wieder heraus.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    sessions: List<WorkoutSession>,
    days: List<TrainingDay>,
    /** Die Tage, die beim Nachtragen zur Wahl stehen – siehe [HistoryUiState.selectableDays]. */
    selectableDays: List<TrainingDay>,
    /** Kommt von außen, damit „heute“ auch nach Mitternacht noch heute ist. */
    today: LocalDate,
    onDeleteSession: (Long) -> Unit,
    onAddSession: (Int, Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    /** Der Tag, dessen Einträge gerade zum Löschen anstehen. */
    var pendingDeletion by remember { mutableStateOf<HistoryDay?>(null) }
    var isAdding by rememberSaveable { mutableStateOf(false) }
    val historyDays = remember(sessions) {
        sessions.groupBy { it.completedAt.toLocalDate() }
            .map { (date, entries) -> HistoryDay(date, entries) }
            .sortedByDescending { it.date }
    }
    val dayNames = remember(days) { days.associate { it.id to it.name } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        SubScreenHeader(title = stringResource(R.string.drawer_history), onBack = onBack) {
            // Ohne Trainingstage gibt es nichts einzutragen; dann bleibt der Knopf weg, statt
            // in einen Dialog ohne Auswahl zu führen.
            if (selectableDays.isNotEmpty()) {
                IconButton(
                    onClick = { isAdding = true },
                    modifier = Modifier.size(Dimens.TouchTargetSize)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.cd_add_session),
                        tint = MenuButtonIcon,
                        modifier = Modifier.size(Dimens.MenuIconSize)
                    )
                }
            }
        }

        if (historyDays.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = AppTextStyles.Body,
                    color = TextSecondary
                )
            }
            return@Column
        }

        val last7 = historyDays.count { ChronoUnit.DAYS.between(it.date, today) < 7 }
        val last30 = historyDays.count { ChronoUnit.DAYS.between(it.date, today) < 30 }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)) {
            SummaryTile(
                value = last7.toString(),
                label = stringResource(R.string.history_last_week),
                modifier = Modifier.weight(1f)
            )
            SummaryTile(
                value = last30.toString(),
                label = stringResource(R.string.history_last_month),
                modifier = Modifier.weight(1f)
            )
            SummaryTile(
                value = historyDays.size.toString(),
                label = stringResource(R.string.history_total),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SectionSpacingLarge))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
        ) {
            items(items = historyDays, key = { it.date.toEpochDay() }) { day ->
                HistoryRow(
                    date = day.date,
                    today = today,
                    labels = day.sessions.sortedBy { it.completedAt }.map { session ->
                        val name = dayNames[session.dayId]
                            ?: stringResource(R.string.day_name, session.dayId)
                        stringResource(
                            R.string.history_entry,
                            name,
                            session.completedAt.toClockTime()
                        )
                    },
                    // Langer Druck fragt nach, statt sofort zu löschen – die Snackbar mit
                    // „Rückgängig“ ist irgendwann weg, ein Fehlgriff soll bleiben können.
                    onLongClick = { pendingDeletion = day }
                )
            }
            item { Spacer(modifier = Modifier.height(Dimens.ListBottomPadding)) }
        }
    }

    if (isAdding) {
        AddSessionDialog(
            days = selectableDays,
            today = today,
            onConfirm = { dayId, completedAt ->
                isAdding = false
                onAddSession(dayId, completedAt)
            },
            onDismiss = { isAdding = false }
        )
    }

    pendingDeletion?.let { day ->
        // Neueste zuerst, wie im Verlauf selbst.
        val entries = remember(day) { day.sessions.sortedByDescending { it.completedAt } }
        // Ein einzelnes Training wird bestätigt, mehrere werden ausgewählt: Sonst träfe es
        // immer nur das jüngste, und ein nachgetragenes Training von vorgestern früh ließe
        // sich nie wieder loswerden, ohne alles danach mitzunehmen.
        val isPicking = entries.size > 1

        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            containerColor = CardBackground,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text(text = stringResource(R.string.history_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)) {
                    Text(
                        text = stringResource(
                            if (isPicking) R.string.history_delete_pick
                            else R.string.history_delete_body,
                            formatFullDate(day.date)
                        )
                    )
                    if (isPicking) {
                        entries.forEach { session ->
                            SessionChoice(
                                label = stringResource(
                                    R.string.history_entry,
                                    dayNames[session.dayId]
                                        ?: stringResource(R.string.day_name, session.dayId),
                                    session.completedAt.toClockTime()
                                ),
                                onClick = {
                                    pendingDeletion = null
                                    onDeleteSession(session.id)
                                }
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
                }
            },
            confirmButton = {
                if (!isPicking) {
                    TextButton(
                        onClick = {
                            pendingDeletion = null
                            entries.firstOrNull()?.let { onDeleteSession(it.id) }
                        }
                    ) {
                        Text(text = stringResource(R.string.action_delete), color = AccentBlue)
                    }
                }
            }
        )
    }
}

/** Ein Eintrag zur Auswahl, wenn an einem Tag mehrere Trainings stehen. */
@Composable
private fun SessionChoice(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = AppTextStyles.ExerciseName,
        color = TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerChip)
            .background(ChipBackground)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Dimens.SectionSpacingMedium)
    )
}

@Composable
private fun SummaryTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .padding(Dimens.SectionSpacingMedium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, style = AppTextStyles.Title, color = TextPrimary)
        Text(
            text = label,
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    date: LocalDate,
    today: LocalDate,
    labels: List<String>,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(Dimens.SectionSpacingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatFullDate(date),
                style = AppTextStyles.ExerciseName,
                color = TextPrimary
            )
            Text(
                text = labels.joinToString(separator = ", "),
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary,
                modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
            )
        }
        val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
        Text(
            text = when (daysAgo) {
                0 -> stringResource(R.string.history_today)
                1 -> stringResource(R.string.history_yesterday)
                else -> pluralStringResource(R.plurals.history_days_ago, daysAgo, daysAgo)
            },
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            modifier = Modifier
                .clip(Dimens.CornerChip)
                .background(ChipBackground)
                .padding(
                    horizontal = Dimens.SectionSpacingSmall,
                    vertical = Dimens.SectionSpacingSmall / 2
                )
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360, heightDp = 640)
@Composable
private fun HistoryScreenPreview() {
    val now = System.currentTimeMillis()
    val oneDay = 24L * 60 * 60 * 1000
    MeinTrainingTheme {
        HistoryScreen(
            sessions = listOf(
                WorkoutSession(id = 1, dayId = 2, completedAt = now),
                WorkoutSession(id = 2, dayId = 1, completedAt = now - 2 * oneDay),
                WorkoutSession(id = 3, dayId = 4, completedAt = now - 5 * oneDay)
            ),
            days = (1..4).map { TrainingDay(id = it, name = "Tag $it") },
            selectableDays = (1..4).map { TrainingDay(id = it, name = "Tag $it") },
            today = LocalDate.now(),
            onDeleteSession = {},
            onAddSession = { _, _ -> },
            onBack = {}
        )
    }
}
