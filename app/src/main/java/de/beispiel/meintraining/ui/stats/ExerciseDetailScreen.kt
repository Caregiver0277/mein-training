package de.beispiel.meintraining.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.model.CardioValue
import de.beispiel.meintraining.ui.components.SegmentToggle
import de.beispiel.meintraining.ui.components.cardioUnits
import de.beispiel.meintraining.ui.screen.SubScreenHeader
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.ui.tracking.WeightChart
import de.beispiel.meintraining.util.formatCardioValue
import de.beispiel.meintraining.util.formatShortDate
import de.beispiel.meintraining.util.formatMonthYear
import de.beispiel.meintraining.util.toLocalDate
import java.time.LocalDate

/**
 * Die Detailseite einer Übung aus „Fortschritt je Übung“: oben ihr Graph, darunter die Zahlen.
 *
 * Kraft: Gewichtsverlauf, Start, aktuell, Veränderung, Steigerungen, Tempo, die Prognose – und mit
 * Satz-Protokoll das geschätzte 1RM als Verlauf, der beste Satz, die Rekorde und das Volumen je
 * Woche. Cardio: die Kurven aus dem Tracking, je Wert umschaltbar, und der Fortschritt des Werts,
 * den der Pfeil verschiebt.
 *
 * [detail] ist `null`, wenn es für die Übung nichts mehr zu zeigen gibt; [isLoading], solange die
 * Zahlen noch nicht da sind – dann bleibt die Seite unter dem Kopf leer, statt kurz „keinen
 * Verlauf“ zu behaupten.
 */
@Composable
fun ExerciseDetailScreen(
    title: String,
    detail: ExerciseDetail?,
    isLoading: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        SubScreenHeader(title = title, onBack = onBack)
        if (isLoading) return@Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
        ) {
            when (detail) {
                null -> Text(
                    text = stringResource(R.string.stats_detail_empty),
                    style = AppTextStyles.Body,
                    color = TextSecondary
                )
                is ExerciseDetail.Strength -> StrengthDetailContent(detail)
                is ExerciseDetail.Cardio -> CardioDetailContent(detail)
            }
            Spacer(modifier = Modifier.height(Dimens.ListBottomPadding))
        }
    }
}

@Composable
private fun StrengthDetailContent(detail: ExerciseDetail.Strength) {
    val progress = detail.entry.progress
    val today = LocalDate.now()

    StatsCard(title = stringResource(R.string.stats_detail_weight)) {
        DetailChartView(detail.chart, emptyText = stringResource(R.string.stats_detail_weight_empty))
        if (progress.isDecreasing) Hint(stringResource(R.string.stats_detail_down_hint))
        Fact(label = stringResource(R.string.stats_detail_start), value = kgText(progress.fromKg))
        Fact(label = stringResource(R.string.stats_detail_current), value = kgText(progress.toKg))
        Fact(
            label = stringResource(R.string.stats_detail_gain),
            value = listOfNotNull(strengthChange(progress), strengthChangePercent(progress))
                .joinToString(" · "),
            highlight = progress.gainKg > 0.0
        )
        Fact(
            label = stringResource(
                if (progress.isDecreasing) R.string.stats_detail_decreases else R.string.stats_detail_increases
            ),
            value = progress.increases.toString()
        )
        detail.entry.tempo?.let { tempo ->
            Fact(label = stringResource(R.string.stats_detail_tempo), value = tempoText(tempo, progress.isDecreasing))
        }
        progress.lastIncreaseAt?.let { at ->
            Fact(
                label = stringResource(
                    if (progress.isDecreasing) R.string.stats_detail_last_decrease else R.string.stats_detail_last_increase
                ),
                value = stringResource(
                    R.string.stats_detail_value_date,
                    formatShortDate(at.toLocalDate(), today),
                    sinceText(detail.entry.daysSinceIncrease ?: 0L)
                )
            )
        }
    }

    // Bei Pfeil nach unten gibt es keine Marke nach oben – und damit nichts zu erklären.
    if (!progress.isDecreasing) {
        StatsCard(title = stringResource(R.string.stats_detail_forecast)) {
            val forecast = detail.forecast
            if (forecast == null) {
                Hint(stringResource(R.string.stats_forecast_none))
            } else {
                Text(
                    text = stringResource(
                        R.string.stats_forecast_sentence,
                        kgText(forecast.targetKg),
                        formatMonthYear(forecast.date)
                    ),
                    style = AppTextStyles.ExerciseName,
                    color = TextPrimary
                )
                Hint(stringResource(R.string.stats_forecast_hint))
            }
        }
    }

    detail.sets?.let { SetStatsCards(it, today) }
}

@Composable
private fun SetStatsCards(sets: SetStats, today: LocalDate) {
    StatsCard(title = stringResource(R.string.stats_detail_one_rep_max)) {
        val chart = sets.oneRepMaxChart
        if (chart == null) {
            Hint(stringResource(R.string.stats_detail_one_rep_max_none))
        } else {
            DetailChartView(chart, emptyText = stringResource(R.string.stats_detail_one_rep_max_none))
            Hint(stringResource(R.string.stats_detail_one_rep_max_hint))
        }
        sets.bestOneRepMax?.let { best ->
            Fact(
                label = stringResource(R.string.stats_detail_best_one_rep_max),
                value = stringResource(
                    R.string.stats_detail_value_date,
                    kgText(best.kg.roundTo(1)),
                    formatShortDate(best.date, today)
                ),
                highlight = true
            )
        }
        sets.bestSet?.let { set ->
            Fact(
                label = stringResource(R.string.stats_detail_best_set),
                value = stringResource(
                    R.string.stats_detail_value_date,
                    stringResource(R.string.stats_detail_set_value, kgText(set.weightKg ?: 0.0), set.reps),
                    formatShortDate(set.performedAt.toLocalDate(), today)
                )
            )
        }
    }

    if (sets.records.isNotEmpty()) {
        StatsCard(title = stringResource(R.string.stats_detail_records)) {
            Hint(stringResource(R.string.stats_detail_records_hint))
            sets.records.forEach { record ->
                Fact(
                    label = pluralStringResource(R.plurals.stats_detail_reps, record.reps, record.reps),
                    value = stringResource(
                        R.string.stats_detail_value_date,
                        kgText(record.weightKg),
                        formatShortDate(record.performedAt.toLocalDate(), today)
                    )
                )
            }
        }
    }

    StatsCard(title = stringResource(R.string.stats_detail_volume)) {
        Hint(stringResource(R.string.stats_detail_volume_hint))
        WeekBars(
            label = stringResource(R.string.stats_detail_volume),
            values = sets.weeklyVolume.map { it.volumeKg },
            maxLabel = { kgText(it.roundTo(0)) }
        )
        Fact(
            label = stringResource(R.string.stats_detail_this_week),
            value = kgText(sets.weeklyVolume.lastOrNull()?.volumeKg?.roundTo(0) ?: 0.0)
        )
        Fact(
            label = pluralStringResource(
                R.plurals.stats_cardio_average,
                sets.weeklyVolume.size,
                sets.weeklyVolume.size
            ),
            value = kgText(
                (sets.weeklyVolume.sumOf { it.volumeKg } / sets.weeklyVolume.size.coerceAtLeast(1)).roundTo(0)
            )
        )
    }
}

@Composable
private fun CardioDetailContent(detail: ExerciseDetail.Cardio) {
    val today = LocalDate.now()
    val progress = detail.progress
    val values = detail.charts.keys.toList()
    // Vorgewählt ist der Wert des Fortschritts; gemerkt wird die Wahl über das Drehen hinweg.
    var selected by rememberSaveable(detail.key) {
        mutableStateOf(progress?.value ?: values.firstOrNull() ?: CardioValue.DURATION)
    }
    val value = selected.takeIf { it in detail.charts } ?: values.firstOrNull()
    val units = cardioUnits()

    StatsCard(title = stringResource(R.string.stats_detail_cardio_chart)) {
        if (values.size > 1) {
            SegmentToggle(
                // Beim Tempo „Stufe“, wenn alle Kurven Stufen sind – sonst „Tempo“.
                labels = values.map { cardioValueName(it, detail.charts.getValue(it).intensityUnit) },
                selectedIndex = values.indexOf(value),
                onSelect = { selected = values[it] },
                segmentWidth = Dimens.CardioValueToggleWidth,
                modifier = Modifier.padding(bottom = Dimens.SectionSpacingSmall)
            )
        }
        val chart = value?.let { detail.charts[it] }
        if (chart != null) {
            WeightChart(
                series = chart.series,
                window = chart.window,
                ticks = chart.ticks,
                emptyText = stringResource(R.string.tracking_cardio_empty),
                valueLabel = { amount, line -> formatCardioValue(value, amount, line.intensityUnit, units) }
            )
        }
        progress?.let {
            Fact(
                label = cardioValueName(it.value, it.unit),
                value = cardioRange(it)
            )
            if (it.entries >= 2) {
                Fact(
                    label = stringResource(R.string.stats_detail_gain),
                    value = listOfNotNull(cardioChange(it), cardioChangePercent(it)).joinToString(" · "),
                    highlight = it.gain > 0.0
                )
            }
        }
        Fact(label = stringResource(R.string.stats_cardio_sessions), value = detail.entries.toString())
        detail.lastAt?.let { at ->
            Fact(
                label = stringResource(R.string.stats_detail_last_entry),
                value = formatShortDate(at.toLocalDate(), today)
            )
        }
    }
}

/** Ein Graph der Detailseite – der Graph aus dem Tracking, mit Kilogramm am Cursor. */
@Composable
private fun DetailChartView(chart: DetailChart, emptyText: String) {
    WeightChart(series = chart.series, window = chart.window, ticks = chart.ticks, emptyText = emptyText)
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = AppTextStyles.ColumnLabel,
        color = TextSecondary,
        modifier = Modifier.padding(top = Dimens.SectionSpacingSmall)
    )
}

/** Die Einheit aller Kurven des Graphen, wenn sie sich eine teilen – nur beim Tempo gesetzt. */
private val DetailChart.intensityUnit
    get() = series.map { it.intensityUnit }.distinct().singleOrNull()
