package de.beispiel.meintraining.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.model.CardioValue
import de.beispiel.meintraining.data.model.IntensityUnit
import de.beispiel.meintraining.ui.components.cardioUnits
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.CardioProgress
import de.beispiel.meintraining.util.ProgressTempo
import de.beispiel.meintraining.util.StrengthProgress
import de.beispiel.meintraining.util.WeightForecast
import de.beispiel.meintraining.util.formatCardioValue
import de.beispiel.meintraining.util.formatMonthYear
import de.beispiel.meintraining.util.toDecimalString
import de.beispiel.meintraining.util.toSignedDecimalString
import kotlin.math.abs
import kotlin.math.roundToInt

/** Trennt die Angaben einer Zeile: „+12,5 kg · +21 % · 5 Steigerungen“. Reine Notation. */
private const val PART_SEPARATOR = " · "

/** Echtes Minuszeichen, wie bei den Gewichtsänderungen (siehe `toSignedDecimalString`). */
private const val MINUS_SIGN = '−'

/**
 * „Fortschritt je Übung“: jede Übung des Plans mit Start → aktuell und darunter, was sich getan
 * hat. Ein Tipp öffnet ihre Detailseite.
 *
 * Bei Pfeil nach unten steht die Veränderung, wie sie ist – „−10 kg“ –, grün, weil die Senkung
 * dort der Fortschritt ist. Ein „+10 kg“ neben „40 → 30 kg“ läse sich wie ein Rechenfehler.
 */
@Composable
internal fun ExerciseProgressCard(entries: List<ProgressEntry>, onEntryClick: (ProgressKey) -> Unit) {
    StatsCard(title = stringResource(R.string.stats_progress_exercises)) {
        Text(
            text = stringResource(R.string.stats_progress_exercises_hint),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary
        )
        entries.forEach { entry ->
            when (entry) {
                is ProgressEntry.Strength -> ProgressRow(
                    title = entry.key.title,
                    range = strengthRange(entry.progress),
                    summary = strengthSummary(entry.progress),
                    details = strengthDetails(entry),
                    isProgress = entry.progress.gainKg > 0.0,
                    onClick = { onEntryClick(entry.key) }
                )
                is ProgressEntry.Cardio -> ProgressRow(
                    title = entry.key.title,
                    range = cardioRange(entry.progress),
                    summary = cardioSummary(entry.progress),
                    details = null,
                    isProgress = entry.progress.gain > 0.0,
                    onClick = { onEntryClick(entry.key) }
                )
            }
        }
    }
}

@Composable
private fun ProgressRow(
    title: String,
    range: String,
    summary: String,
    details: String?,
    isProgress: Boolean,
    onClick: () -> Unit
) {
    val openLabel = stringResource(R.string.stats_progress_open, title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick)
            .padding(vertical = Dimens.SectionSpacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = AppTextStyles.Body,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = range,
                    style = AppTextStyles.ExerciseName,
                    color = TextPrimary,
                    maxLines = 1,
                    modifier = Modifier.padding(start = Dimens.SectionSpacingSmall)
                )
            }
            Text(
                text = summary,
                style = AppTextStyles.ColumnLabel,
                color = if (isProgress) AccentGreen else TextSecondary
            )
            details?.let {
                Text(text = it, style = AppTextStyles.ColumnLabel, color = TextSecondary)
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier
                .padding(start = Dimens.SectionSpacingSmall / 2)
                .size(Dimens.MenuIconSize)
        )
    }
}

/**
 * „Nächste Marken“: wann die Kraftübungen mit Pfeil nach oben im bisherigen Tempo ihre nächste
 * runde Marke erreichen – deutlich als grobe Schätzung gekennzeichnet (siehe `weightForecast`).
 */
@Composable
internal fun ForecastCard(forecasts: List<WeightForecast>) {
    StatsCard(title = stringResource(R.string.stats_forecast_title)) {
        Text(
            text = stringResource(R.string.stats_forecast_hint),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary
        )
        forecasts.forEach { forecast ->
            Fact(label = forecast.name, value = forecastValue(forecast))
        }
    }
}

/** „100 kg etwa im März 2027“. */
@Composable
internal fun forecastValue(forecast: WeightForecast): String = stringResource(
    R.string.stats_forecast_value,
    kgText(forecast.targetKg),
    formatMonthYear(forecast.date)
)

// --- Texte, die Liste und Detailseite teilen ------------------------------------

@Composable
internal fun kgText(kg: Double): String = stringResource(R.string.stats_kg_value, kg.toDecimalString())

/** „60 → 72,5 kg“ – die Einheit nur einmal, am Ende. */
@Composable
internal fun strengthRange(progress: StrengthProgress): String = stringResource(
    R.string.stats_progress_range,
    progress.fromKg.toDecimalString(),
    kgText(progress.toKg)
)

/** Die Veränderung in kg, mit Vorzeichen in ihrer echten Richtung: „+12,5 kg“, „−10 kg“. */
@Composable
internal fun strengthChange(progress: StrengthProgress): String =
    stringResource(R.string.stats_kg_value, (progress.toKg - progress.fromKg).toSignedDecimalString())

/** Die Veränderung in Prozent, ebenfalls in ihrer echten Richtung; `null` ohne Startgewicht. */
@Composable
internal fun strengthChangePercent(progress: StrengthProgress): String? {
    if (progress.fromKg <= 0.0) return null
    val percent = (progress.toKg - progress.fromKg) / progress.fromKg * 100.0
    return stringResource(R.string.stats_percent_value, percent.roundTo(0).toSignedDecimalString())
}

/** „5 Steigerungen“ – bei Pfeil nach unten „Senkungen“; ohne eine „noch keine Steigerung“. */
@Composable
internal fun increasesText(progress: StrengthProgress): String = when {
    progress.increases == 0 -> stringResource(
        if (progress.isDecreasing) R.string.stats_progress_no_decrease else R.string.stats_progress_no_increase
    )
    else -> pluralStringResource(
        if (progress.isDecreasing) R.plurals.stats_progress_decreases else R.plurals.stats_progress_increases,
        progress.increases,
        progress.increases
    )
}

/** „+12,5 kg · +21 % · 5 Steigerungen“; ohne Veränderung nur die Steigerungen. */
@Composable
private fun strengthSummary(progress: StrengthProgress): String {
    if (progress.increases == 0 && progress.gainKg == 0.0) return increasesText(progress)
    return listOfNotNull(
        strengthChange(progress),
        strengthChangePercent(progress),
        increasesText(progress)
    ).joinToString(PART_SEPARATOR)
}

/** „im Schnitt alle 11 Tage +2,5 kg · zuletzt vor 9 Tagen“; ohne Steigerung `null`. */
@Composable
private fun strengthDetails(entry: ProgressEntry.Strength): String? = listOfNotNull(
    entry.tempo?.let { tempoText(it, entry.progress.isDecreasing) },
    entry.daysSinceIncrease?.let { sinceText(it) }
).takeIf { it.isNotEmpty() }?.joinToString(PART_SEPARATOR)

/** „im Schnitt alle 11 Tage +2,5 kg“, bei Pfeil nach unten mit „−“. */
@Composable
internal fun tempoText(tempo: ProgressTempo, isDecreasing: Boolean): String {
    val days = tempo.daysPerIncrease.roundToInt().coerceAtLeast(1)
    val amount = if (isDecreasing) -tempo.amountPerIncrease else tempo.amountPerIncrease
    return pluralStringResource(
        R.plurals.stats_progress_tempo,
        days,
        days,
        stringResource(R.string.stats_kg_value, amount.roundTo(2).toSignedDecimalString())
    )
}

/** „zuletzt heute“, „zuletzt vor 9 Tagen“. */
@Composable
internal fun sinceText(days: Long): String =
    if (days == 0L) {
        stringResource(R.string.stats_progress_since_today)
    } else {
        pluralStringResource(R.plurals.stats_progress_since_days, days.toInt(), days.toInt())
    }

/** „20 → 30 min“, „6 → 7,5 km/h“ – der Wert, an dem der Fortschritt gemessen wird. */
@Composable
internal fun cardioRange(progress: CardioProgress): String {
    val units = cardioUnits()
    return stringResource(
        R.string.stats_progress_range,
        formatCardioValue(progress.value, progress.from, progress.unit, units),
        formatCardioValue(progress.value, progress.to, progress.unit, units)
    )
}

/** „Dauer +10 min · +50 % · 12 Einheiten“ – die Veränderung in ihrer echten Richtung. */
@Composable
private fun cardioSummary(progress: CardioProgress): String {
    val entries = pluralStringResource(R.plurals.stats_progress_entries, progress.entries, progress.entries)
    if (progress.entries < 2) return "${cardioValueName(progress.value, progress.unit)}$PART_SEPARATOR$entries"
    return listOfNotNull(
        "${cardioValueName(progress.value, progress.unit)} ${cardioChange(progress)}",
        cardioChangePercent(progress),
        entries
    ).joinToString(PART_SEPARATOR)
}

/**
 * Die Veränderung mit Vorzeichen: „+10 min“, „−500 m“, „+0,5 km/h“. Bei der Stufe nur die Zahl –
 * „Stufe“ steht schon als Name des Werts davor, und „+Stufe 1“ läse sich holprig.
 */
@Composable
internal fun cardioChange(progress: CardioProgress): String {
    val delta = progress.to - progress.from
    if (progress.value == CardioValue.INTENSITY && progress.unit == IntensityUnit.LEVEL) {
        return delta.toSignedDecimalString()
    }
    val sign = if (delta < 0) MINUS_SIGN.toString() else "+"
    return sign + formatCardioValue(progress.value, abs(delta), progress.unit, cardioUnits())
}

@Composable
internal fun cardioChangePercent(progress: CardioProgress): String? {
    if (progress.from <= 0.0) return null
    val percent = (progress.to - progress.from) / progress.from * 100.0
    return stringResource(R.string.stats_percent_value, percent.roundTo(0).toSignedDecimalString())
}

/** „Dauer“, „Distanz“, „Tempo“ oder „Stufe“, „Steigung“. */
@Composable
internal fun cardioValueName(value: CardioValue, unit: IntensityUnit?): String = stringResource(
    when (value) {
        CardioValue.DURATION -> R.string.cardio_value_duration
        CardioValue.DISTANCE -> R.string.cardio_value_distance
        CardioValue.INTENSITY ->
            if (unit == IntensityUnit.LEVEL) R.string.cardio_value_level else R.string.cardio_value_speed
        CardioValue.INCLINE -> R.string.cardio_value_incline
    }
)
