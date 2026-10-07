package de.beispiel.meintraining.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.CardioTotals
import de.beispiel.meintraining.util.formatShortDate
import de.beispiel.meintraining.util.toDecimalString
import kotlin.math.roundToInt

/**
 * Cardio auf einen Blick: Minuten und Kilometer je Woche für die letzten zwölf Wochen, darunter
 * diese Woche, der Schnitt und die Summen seit Beginn.
 *
 * Ein Wert, der nie eingetragen wurde – etwa Kilometer am Rudergerät mit nur einer Dauer –,
 * bekommt keine Balken: Zwölf leere Wochen sagten nur, dass es ihn nicht gibt.
 */
@Composable
internal fun CardioCard(totals: CardioTotals) {
    val hasMinutes = totals.totalMinutes > 0.0
    val hasKm = totals.totalKm > 0.0
    val thisWeek = totals.weeks.last()

    StatsCard(title = stringResource(R.string.stats_cardio)) {
        if (hasMinutes) {
            WeekBars(
                label = stringResource(R.string.stats_cardio_minutes_per_week),
                values = totals.weeks.map { it.minutes },
                maxLabel = { minutesText(it) }
            )
        }
        if (hasKm) {
            WeekBars(
                label = stringResource(R.string.stats_cardio_km_per_week),
                values = totals.weeks.map { it.km },
                maxLabel = { kmText(it) }
            )
        }
        if (hasMinutes || hasKm) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.SectionSpacingSmall / 2)
            ) {
                Text(
                    text = formatShortDate(totals.weeks.first().weekStart),
                    style = AppTextStyles.ColumnLabel,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.stats_goal_this_week),
                    style = AppTextStyles.ColumnLabel,
                    color = TextSecondary
                )
            }
            Fact(
                label = stringResource(R.string.stats_cardio_this_week),
                value = cardioText(thisWeek.minutes, thisWeek.km, hasMinutes, hasKm)
            )
            Fact(
                label = stringResource(R.string.stats_cardio_average, totals.weeks.size),
                value = cardioText(
                    totals.weeks.sumOf { it.minutes } / totals.weeks.size,
                    totals.weeks.sumOf { it.km } / totals.weeks.size,
                    hasMinutes,
                    hasKm
                )
            )
            Fact(
                label = stringResource(R.string.stats_cardio_total),
                value = cardioText(totals.totalMinutes, totals.totalKm, hasMinutes, hasKm),
                highlight = true
            )
        }
        Fact(
            label = stringResource(R.string.stats_cardio_sessions),
            value = totals.sessions.toString()
        )
    }
}

/**
 * Ein Balken je Woche, ohne Zahlen darüber – bei Minuten wären sie für zwölf Spalten zu breit.
 * Den Maßstab gibt der höchste Wert rechts über den Balken an.
 */
@Composable
private fun WeekBars(label: String, values: List<Double>, maxLabel: @Composable (Double) -> String) {
    val max = values.maxOrNull() ?: 0.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.SectionSpacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.stats_cardio_max, maxLabel(max)),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.SectionSpacingSmall / 2)
            .height(Dimens.CardioBarMaxHeight),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall / 2),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEach { value ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(cardioBarHeight(value, max))
                    .clip(Dimens.CornerChip)
                    .background(if (value > 0.0) AccentBlue else ChipBackground)
            )
        }
    }
}

/** „95 min · 12,4 km“ – nur mit den Werten, die es überhaupt gibt. */
@Composable
private fun cardioText(minutes: Double, km: Double, hasMinutes: Boolean, hasKm: Boolean): String = when {
    hasMinutes && hasKm -> stringResource(R.string.stats_cardio_pair, minutesText(minutes), kmText(km))
    hasMinutes -> minutesText(minutes)
    else -> kmText(km)
}

@Composable
private fun minutesText(minutes: Double): String =
    stringResource(R.string.stats_minutes_value, minutes.roundToInt())

@Composable
private fun kmText(km: Double): String =
    stringResource(R.string.stats_km_value, km.roundTo(1).toDecimalString())

private fun cardioBarHeight(value: Double, max: Double): Dp =
    if (max <= 0.0) Dimens.StatsBarMinHeight
    else Dimens.StatsBarMinHeight + (Dimens.CardioBarMaxHeight - Dimens.StatsBarMinHeight) *
        (value / max).toFloat()
