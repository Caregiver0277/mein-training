package de.beispiel.meintraining.ui.stats

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.components.dayLabel
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.RotationSummary
import de.beispiel.meintraining.util.toDecimalString
import kotlin.math.roundToInt

/**
 * Wie die abgeschlossenen Runden ausgegangen sind. Ohne eine einzige erklärt die Karte, wann eine
 * Runde als abgeschlossen gilt – sonst bliebe rätselhaft, worauf sie wartet.
 */
@Composable
internal fun RotationCard(summary: RotationSummary?, dayNames: Map<Int, String>) {
    StatsCard(title = stringResource(R.string.stats_rounds)) {
        if (summary == null) {
            Text(
                text = stringResource(R.string.stats_rounds_none),
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary,
                modifier = Modifier.padding(top = Dimens.SectionSpacingSmall)
            )
            return@StatsCard
        }
        Fact(
            label = stringResource(R.string.stats_rounds_finished),
            value = summary.count.toString()
        )
        val days = summary.averageDays.roundTo(1)
        Fact(
            label = stringResource(R.string.stats_rounds_duration),
            value = if (days % 1.0 == 0.0) {
                pluralStringResource(R.plurals.stats_days, days.toInt(), days.toInt())
            } else {
                stringResource(R.string.stats_days_decimal, days.toDecimalString())
            }
        )
        Fact(
            label = stringResource(R.string.stats_rounds_full),
            value = stringResource(
                R.string.stats_rounds_full_value,
                (summary.fullShare * 100).roundToInt(),
                summary.fullCount,
                summary.count
            ),
            highlight = summary.fullCount == summary.count
        )
        summary.mostMissedDayId?.let { dayId ->
            Fact(
                label = stringResource(R.string.stats_rounds_missed),
                value = stringResource(
                    R.string.stats_rounds_missed_value,
                    dayLabel(dayId, dayNames[dayId]),
                    pluralStringResource(
                        R.plurals.stats_rounds_count,
                        summary.mostMissedCount,
                        summary.mostMissedCount
                    )
                )
            )
        }
    }
}
