package de.beispiel.meintraining.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.components.dayLabel
import de.beispiel.meintraining.ui.screen.SubScreenHeader
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.DurationSummary
import de.beispiel.meintraining.util.STAGNATION_SESSIONS
import de.beispiel.meintraining.util.StagnatingExercise
import de.beispiel.meintraining.util.formatFullDate
import de.beispiel.meintraining.util.toDecimalString
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Hängt den Statistik-Screen an sein ViewModel. */
@Composable
fun StatsRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(uiState = uiState, onBack = onBack, modifier = modifier)
}

/**
 * Die Statistikseite, von oben nach unten vom Überblick ins Einzelne:
 *
 * 1. Kennzahlen – drei Kacheln: Trainings, pro Woche, Ziel-Serie.
 * 2. Wochenziel – die letzten zwölf Wochen gegen das Ziel.
 * 3. Kalender – die Heatmap der letzten zwölf Monate.
 * 4. Meilensteine – erreichte und nächste (noch nicht gebaut).
 * 5. Fortschritt – Gesamtzuwachs und schwerste Übung; Fortschritt je Übung und die „Nächsten
 *    Marken“ kommen darunter (noch nicht gebaut).
 * 6. Festgefahren – die Kehrseite des Fortschritts, deshalb gleich dahinter.
 * 7. Cardio – Minuten und Kilometer je Woche.
 * 8. Runden – wie die Runden ausgehen.
 * 9. Rhythmus – Wochentage, typische Uhrzeit und längste Serie, Trainingsdauer.
 * 10. Rückblick – der Einstieg in die Monats- und Jahresübersicht (noch nicht gebaut).
 *
 * Jeder Abschnitt ist eine eigene Karte; was keine Daten hat, lässt seine Karte weg oder sagt in
 * einem Satz, woher sie kommen. Neue Karten kommen an ihre Stelle in dieser Reihenfolge, statt
 * hinten angehängt zu werden – sonst wächst die Seite in der Reihenfolge ihrer Entstehung statt
 * in der ihres Inhalts.
 */
@Composable
fun StatsScreen(uiState: StatsUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        SubScreenHeader(title = stringResource(R.string.drawer_stats), onBack = onBack)

        if (!uiState.hasSessions) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.stats_empty),
                    style = AppTextStyles.Body,
                    color = TextSecondary
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
        ) {
            // Reihenfolge siehe oben.
            HeadlineTiles(uiState)
            WeeklyGoalCard(uiState.goalWeeks, uiState.weeklyGoal)
            uiState.heatmap?.let { HeatmapCard(it) }
            ProgressCard(uiState)
            if (uiState.stagnating.isNotEmpty()) StagnationCard(uiState.stagnating)
            RotationCard(uiState.rotations, uiState.dayNames)
            WeekdayCard(uiState)
            RhythmCard(uiState)
            DurationCard(uiState.duration, uiState.dayNames)
            Spacer(modifier = Modifier.height(Dimens.ListBottomPadding))
        }
    }
}

@Composable
private fun HeadlineTiles(uiState: StatsUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)) {
        Tile(
            value = uiState.totalSessions.toString(),
            label = stringResource(R.string.stats_total),
            modifier = Modifier.weight(1f)
        )
        Tile(
            value = uiState.sessionsPerWeek.roundTo(1).toDecimalString(),
            label = stringResource(R.string.stats_per_week),
            modifier = Modifier.weight(1f)
        )
        Tile(
            value = uiState.currentStreak.toString(),
            label = stringResource(R.string.stats_streak),
            accent = uiState.currentStreak > 0,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Tile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .padding(Dimens.SectionSpacingMedium),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = AppTextStyles.Title,
            color = if (accent) AccentGreen else TextPrimary
        )
        Text(
            text = label,
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
        )
    }
}

/** Säulen für die sieben Wochentage – zeigt, wann tatsächlich trainiert wird. */
@Composable
private fun WeekdayCard(uiState: StatsUiState) {
    val counts = uiState.weekdayCounts
    val max = counts.maxOrNull() ?: 0
    StatsCard(title = stringResource(R.string.stats_weekdays)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.WeekdayChartHeight),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall),
            verticalAlignment = Alignment.Bottom
        ) {
            DayOfWeek.entries.forEachIndexed { index, dayOfWeek ->
                val count = counts.getOrElse(index) { 0 }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = count.toString(),
                        style = AppTextStyles.ColumnLabel,
                        color = if (count > 0) TextPrimary else TextSecondary
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            // Auch die 0 bekommt einen Sockel, sonst fehlt die Spalte optisch.
                            .height(barHeight(count, max))
                            .clip(Dimens.CornerChip)
                            .background(if (count == max && count > 0) AccentBlue else ChipBackground)
                    )
                    Text(
                        text = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMANY),
                        style = AppTextStyles.ColumnLabel,
                        color = TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
                    )
                }
            }
        }
    }
}

@Composable
private fun RhythmCard(uiState: StatsUiState) {
    StatsCard(title = stringResource(R.string.stats_rhythm)) {
        uiState.typicalTime?.let { time ->
            Fact(
                label = stringResource(R.string.stats_typical_time),
                value = stringResource(
                    R.string.stats_time_value,
                    "%02d:%02d".format(time.hour, time.minute)
                )
            )
        }
        Fact(
            label = stringResource(R.string.stats_longest_streak),
            value = pluralStringResource(
                R.plurals.stats_weeks,
                uiState.longestStreak,
                uiState.longestStreak
            )
        )
        uiState.firstSession?.let { date ->
            Fact(
                label = stringResource(R.string.stats_since),
                value = formatFullDate(date)
            )
        }
        Fact(
            label = stringResource(R.string.stats_exercise_count),
            value = uiState.exerciseCount.toString()
        )
    }
}

/**
 * Ø Dauer, gesamt und je Trainingstag. Gezählt werden nur Trainings mit bekannter Dauer; ohne
 * eine einzige erklärt die Karte, woher die Dauer kommt.
 */
@Composable
private fun DurationCard(duration: DurationSummary?, dayNames: Map<Int, String>) {
    StatsCard(title = stringResource(R.string.stats_duration)) {
        if (duration == null) {
            Text(
                text = stringResource(R.string.stats_duration_none),
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary,
                modifier = Modifier.padding(top = Dimens.SectionSpacingSmall)
            )
            return@StatsCard
        }
        Fact(
            label = stringResource(R.string.stats_duration_average),
            value = stringResource(R.string.stats_minutes_value, duration.averageMinutes)
        )
        duration.perDay.forEach { (dayId, minutes) ->
            Fact(
                label = dayLabel(dayId, dayNames[dayId]),
                value = stringResource(R.string.stats_minutes_value, minutes)
            )
        }
    }
}

@Composable
private fun ProgressCard(uiState: StatsUiState) {
    StatsCard(title = stringResource(R.string.stats_progress)) {
        Fact(
            label = stringResource(R.string.stats_total_gain),
            value = stringResource(
                R.string.stats_kg_value,
                uiState.totalGainKg.roundTo(2).toDecimalString()
            ),
            highlight = uiState.totalGainKg > 0.0
        )
        uiState.heaviestExercise?.let { (name, weight) ->
            Fact(
                label = stringResource(R.string.stats_heaviest),
                value = "$name · ${stringResource(R.string.stats_kg_value, weight.toDecimalString())}"
            )
        }
        if (uiState.totalGainKg <= 0.0) {
            Text(
                text = stringResource(R.string.stats_no_gains),
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary,
                modifier = Modifier.padding(top = Dimens.SectionSpacingSmall)
            )
        }
    }
}

@Composable
private fun StagnationCard(entries: List<StagnatingExercise>) {
    StatsCard(title = stringResource(R.string.stats_stagnation)) {
        Text(
            text = pluralStringResource(
                R.plurals.stats_stagnation_hint,
                STAGNATION_SESSIONS,
                STAGNATION_SESSIONS
            ),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary
        )
        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SectionSpacingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.name,
                    style = AppTextStyles.Body,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        R.string.stats_stagnation_since,
                        pluralStringResource(
                            R.plurals.stats_stagnation_sessions,
                            entry.sinceSessions,
                            entry.sinceSessions
                        ),
                        pluralStringResource(
                            R.plurals.stats_days,
                            entry.sinceDays.toInt(),
                            entry.sinceDays.toInt()
                        )
                    ),
                    style = AppTextStyles.ColumnLabel,
                    color = TextSecondary
                )
            }
        }
    }
}

/** Eine Karte der Statistikseite; [trailing] steht rechts neben dem Titel, etwa eine Legende. */
@Composable
internal fun StatsCard(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .padding(Dimens.SheetPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Dimens.SectionSpacingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = AppTextStyles.ExerciseName,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
        content()
    }
}

@Composable
internal fun Fact(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.SectionSpacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AppTextStyles.Body,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = AppTextStyles.ExerciseName,
            color = if (highlight) AccentGreen else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun barHeight(count: Int, max: Int) =
    if (max <= 0) Dimens.StatsBarMinHeight
    else Dimens.StatsBarMinHeight + (Dimens.WeekdayBarMaxHeight - Dimens.StatsBarMinHeight) *
        (count.toFloat() / max)

internal fun Double.roundTo(digits: Int): Double {
    var factor = 1.0
    repeat(digits) { factor *= 10 }
    return (this * factor).roundToInt() / factor
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360, heightDp = 900)
@Composable
private fun StatsScreenPreview() {
    MeinTrainingTheme {
        StatsScreen(
            uiState = StatsUiState(
                totalSessions = 34,
                sessionsPerWeek = 3.4,
                currentStreak = 5,
                longestStreak = 8,
                firstSession = java.time.LocalDate.now().minusWeeks(10),
                weekdayCounts = listOf(8, 2, 7, 1, 9, 4, 3),
                typicalTime = java.time.LocalTime.of(18, 40),
                totalGainKg = 47.5,
                stagnating = listOf(StagnatingExercise("Nordic curl", 20.0, 7, 43)),
                exerciseCount = 38,
                heaviestExercise = "Adductor/Abductor" to 85.0,
                duration = DurationSummary(
                    averageMinutes = 64,
                    perDay = listOf(1 to 58, 2 to 71, 3 to 62)
                ),
                dayNames = mapOf(1 to "Push", 2 to "Pull", 3 to "Beine")
            ),
            onBack = {}
        )
    }
}
