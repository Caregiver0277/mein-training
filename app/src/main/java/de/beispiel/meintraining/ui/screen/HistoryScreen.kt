package de.beispiel.meintraining.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.data.model.WorkoutSession
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AccentGreenSurface
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

@Composable
fun HistoryRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        cycles = uiState.cycles,
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
 * Oben eine kurze Bilanz – so sieht man auf einen Blick, ob man dran ist –, darunter jedes
 * Training von heute rückwärts. Jedes bekommt seinen eigenen Kasten, auch wenn an einem Tag
 * mehrere stehen: Zusammengefasst waren sie eine Aufzählung in einer Zeile, in der weder zu
 * erkennen war, welches wann stattfand, noch welches ein langer Druck erwischt.
 *
 * Zwischen den Kästen stehen die Runden. Sie sind der Grund, warum ein nachgetragenes Training
 * mal auf dem Hauptscreen als Haken auftaucht und mal nicht: Es zählt für die Runde, in deren
 * Zeitraum sein Zeitpunkt fällt. Mit den Überschriften ist das nachzusehen statt zu erraten.
 *
 * Die Bilanz oben zählt Trainings, nicht Trainingstage: „7 Tage“ ist die Spanne, gezählt wird
 * darin jedes Training. Vorher zählte sie Tage, was zu je einem Kasten pro Tag passte – neben
 * getrennten Kästen stünde dort eine Zahl, die sich nicht mehr nachzählen lässt. Es ist auch
 * dieselbe Zählweise wie unter „Statistiken“, wo „Trainings“ seit jeher die Einträge meint.
 *
 * Das „+“ in der Kopfzeile trägt ein vergessenes Training nach, der lange Druck auf eine Zeile
 * nimmt genau dieses eine wieder heraus.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    /** Die Runden mit ihren Trainings, jüngste zuerst – siehe [HistoryUiState.cycles]. */
    cycles: List<HistoryCycle>,
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
    /** Der Eintrag, der gerade zum Löschen ansteht. */
    var pendingDeletion by remember { mutableStateOf<HistoryEntry?>(null) }
    var isAdding by rememberSaveable { mutableStateOf(false) }
    val entries = remember(cycles) { cycles.flatMap { it.entries } }
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

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = AppTextStyles.Body,
                    color = TextSecondary
                )
            }
            return@Column
        }

        // Gemerkt, weil hier über den ganzen Verlauf gezählt wird und die Zahlen sich nur
        // ändern, wenn ein Training dazukommt oder der Kalendertag wechselt.
        val summary = remember(entries, today) {
            HistorySummary(
                last7 = entries.count { ChronoUnit.DAYS.between(it.date, today) < DAYS_WEEK },
                last30 = entries.count { ChronoUnit.DAYS.between(it.date, today) < DAYS_MONTH },
                total = entries.size
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)) {
            SummaryTile(
                value = summary.last7.toString(),
                label = stringResource(R.string.history_last_week),
                modifier = Modifier.weight(1f)
            )
            SummaryTile(
                value = summary.last30.toString(),
                label = stringResource(R.string.history_last_month),
                modifier = Modifier.weight(1f)
            )
            SummaryTile(
                value = summary.total.toString(),
                label = stringResource(R.string.history_total),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.SectionSpacingLarge))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
        ) {
            cycles.forEach { cycle ->
                item(key = CYCLE_KEY_PREFIX + cycle.number) { CycleHeader(cycle = cycle) }
                // Die Kennung des Eintrags als Schlüssel: eindeutig auch dann, wenn an einem Tag
                // mehrere Trainings stehen, und stabil, wenn eines dazwischen gelöscht wird.
                items(items = cycle.entries, key = { it.session.id }) { entry ->
                    HistoryRow(
                        date = entry.date,
                        today = today,
                        label = entry.label(dayNames),
                        // Langer Druck fragt nach, statt sofort zu löschen – die Snackbar mit
                        // „Rückgängig“ ist irgendwann weg, ein Fehlgriff soll bleiben können.
                        onLongClick = { pendingDeletion = entry }
                    )
                }
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

    // Gefragt wird nach genau dem Eintrag, auf den gedrückt wurde – seit jeder seinen eigenen
    // Kasten hat, gibt es nichts mehr auszuwählen. Vorher stand hier eine Liste zur Wahl, weil
    // ein Kasten für den ganzen Tag nicht sagen konnte, welches Training gemeint ist.
    pendingDeletion?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            containerColor = CardBackground,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text(text = stringResource(R.string.history_delete_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.history_delete_body,
                        entry.label(dayNames),
                        formatFullDate(entry.date)
                    )
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(text = stringResource(R.string.action_cancel), color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletion = null
                        onDeleteSession(entry.session.id)
                    }
                ) {
                    Text(text = stringResource(R.string.action_delete), color = AccentBlue)
                }
            }
        )
    }
}

/** Die Zahlen der Bilanz über der Liste. */
private data class HistorySummary(val last7: Int, val last30: Int, val total: Int)

/**
 * Überschrift einer Runde: „Runde 12“ und daneben, wie weit sie ist.
 *
 * Bewusst keine Kachel, sondern eine schmale Zeile ohne Fläche: Die Kästen darunter sind die
 * Trainings, die Überschrift ordnet sie nur. Die laufende Runde hebt sich grün ab – sie ist die,
 * auf die der Haken auf dem Hauptscreen zeigt.
 */
@Composable
private fun CycleHeader(cycle: HistoryCycle) {
    val accent = if (cycle.isCurrent) AccentGreen else TextSecondary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.SectionSpacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.history_cycle, cycle.number),
            style = AppTextStyles.ColumnLabel,
            color = accent,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (cycle.entries.isEmpty()) {
                stringResource(R.string.history_cycle_empty)
            } else {
                stringResource(
                    R.string.history_cycle_progress,
                    cycle.completedDays,
                    cycle.dayCount
                )
            },
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary
        )
        if (cycle.isCurrent) {
            Text(
                text = stringResource(R.string.history_cycle_current),
                style = AppTextStyles.ColumnLabel,
                color = AccentGreen,
                modifier = Modifier
                    .padding(start = Dimens.SectionSpacingSmall)
                    .clip(Dimens.CornerChip)
                    .background(AccentGreenSurface)
                    .padding(
                        horizontal = Dimens.SectionSpacingSmall,
                        vertical = Dimens.SectionSpacingSmall / 2
                    )
            )
        }
    }
}

/**
 * „Tag 2 · 18:30 Uhr“ – Name des Trainingstages und Uhrzeit.
 *
 * Fehlt der Name, weil der Tag inzwischen hinter einer verkürzten Runde liegt, tritt die
 * Nummer an seine Stelle; ein Eintrag ohne Beschriftung wäre nicht wiederzuerkennen.
 */
@Composable
private fun HistoryEntry.label(dayNames: Map<Int, String>): String = stringResource(
    R.string.history_entry,
    dayNames[session.dayId] ?: stringResource(R.string.day_name, session.dayId),
    session.completedAt.toClockTime()
)

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

/** Ein Training als eigener Kasten: Datum, Eintrag und der Abstand zu heute. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    date: LocalDate,
    today: LocalDate,
    label: String,
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
                text = label,
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

/** Die Spannen der Bilanz: die letzte Woche und der letzte Monat, jeweils ab heute rückwärts. */
private const val DAYS_WEEK = 7
private const val DAYS_MONTH = 30

/**
 * Schlüssel der Rundenüberschriften.
 *
 * Text statt Zahl, weil sich Überschriften und Einträge eine Liste teilen: Die Einträge sind mit
 * ihrer Kennung geschlüsselt, und eine blanke Rundennummer träfe irgendwann auf dieselbe Zahl.
 */
private const val CYCLE_KEY_PREFIX = "cycle-"

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360, heightDp = 640)
@Composable
private fun HistoryScreenPreview() {
    val now = System.currentTimeMillis()
    val oneDay = 24L * 60 * 60 * 1000
    fun entry(id: Long, dayId: Int, at: Long) = HistoryEntry(
        session = WorkoutSession(id = id, dayId = dayId, completedAt = at),
        date = at.toLocalDate()
    )
    MeinTrainingTheme {
        HistoryScreen(
            cycles = listOf(
                HistoryCycle(
                    number = 2,
                    // Zwei Trainings am selben Tag – jedes bekommt seinen eigenen Kasten.
                    entries = listOf(
                        entry(id = 1, dayId = 2, at = now),
                        entry(id = 2, dayId = 1, at = now - 3 * 60 * 60 * 1000)
                    ),
                    completedDays = 2,
                    dayCount = 4,
                    isCurrent = true
                ),
                HistoryCycle(
                    number = 1,
                    entries = listOf(
                        entry(id = 3, dayId = 1, at = now - 2 * oneDay),
                        entry(id = 4, dayId = 4, at = now - 5 * oneDay)
                    ),
                    completedDays = 2,
                    dayCount = 4
                )
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
