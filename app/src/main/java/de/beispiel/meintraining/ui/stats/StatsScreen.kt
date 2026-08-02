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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import de.beispiel.meintraining.R
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
import de.beispiel.meintraining.util.STAGNATION_DAYS
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
    val uiState by viewModel.uiState.collectAsState()
    StatsScreen(uiState = uiState, onBack = onBack, modifier = modifier)
}

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
            HeadlineTiles(uiState)
            WeekdayCard(uiState)
            RhythmCard(uiState)
            ProgressCard(uiState)
            if (uiState.stagnating.isNotEmpty()) StagnationCard(uiState.stagnating)
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
            text = stringResource(R.string.stats_stagnation_hint, STAGNATION_DAYS.toInt()),
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
                    text = pluralStringResource(
                        R.plurals.stats_days,
                        entry.sinceDays.toInt(),
                        entry.sinceDays.toInt()
                    ),
                    style = AppTextStyles.ColumnLabel,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun StatsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .padding(Dimens.SheetPadding)
    ) {
        Text(
            text = title,
            style = AppTextStyles.ExerciseName,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = Dimens.SectionSpacingSmall)
        )
        content()
    }
}

@Composable
private fun Fact(label: String, value: String, highlight: Boolean = false) {
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

private fun Double.roundTo(digits: Int): Double {
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
                stagnating = listOf(StagnatingExercise("Nordic curl", 0.0, 43)),
                exerciseCount = 38,
                heaviestExercise = "Adductor/Abductor" to 85.0
            ),
            onBack = {}
        )
    }
}
