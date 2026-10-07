package de.beispiel.meintraining.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.WeekCount
import de.beispiel.meintraining.util.formatShortDate

/**
 * Das Wochenziel: ein Balken je Woche für die letzten zwölf Wochen, eine Linie auf Höhe des
 * Ziels. Grün ist jede Woche, die es erreicht hat – die laufende, sobald sie es geschafft hat.
 *
 * Die Höhe richtet sich nach dem Ziel oder der stärksten Woche, je nachdem, was höher ist: So
 * steht die Ziellinie auch dann im Bild, wenn noch keine Woche an sie heranreicht.
 */
@Composable
internal fun WeeklyGoalCard(weeks: List<WeekCount>, goal: Int) {
    if (weeks.isEmpty()) return
    val scale = maxOf(goal, weeks.maxOf { it.count })
    val reached = weeks.count { it.count >= goal }

    StatsCard(
        title = stringResource(R.string.stats_goal),
        trailing = {
            Text(
                text = pluralStringResource(R.plurals.stats_goal_value, goal, goal),
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary
            )
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SectionSpacingSmall),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall / 2)
        ) {
            weeks.forEach { week ->
                Text(
                    text = week.count.toString(),
                    style = AppTextStyles.ColumnLabel,
                    color = if (week.count > 0) TextPrimary else TextSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.GoalBarMaxHeight)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomStart),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall / 2),
                verticalAlignment = Alignment.Bottom
            ) {
                weeks.forEach { week ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(goalBarHeight(week.count, scale))
                            .clip(Dimens.CornerChip)
                            .background(if (week.count >= goal) AccentGreen else ChipBackground)
                    )
                }
            }
            // Die Ziellinie: Ein Balken, der das Ziel genau erreicht, endet auf ihr.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(bottom = goalBarHeight(goal, scale) - Dimens.GoalLineWidth)
                    .height(Dimens.GoalLineWidth)
                    .background(TextPrimary)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SectionSpacingSmall / 2)
        ) {
            Text(
                text = formatShortDate(weeks.first().weekStart),
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
            label = stringResource(R.string.stats_goal_reached),
            value = pluralStringResource(
                R.plurals.stats_goal_reached_value,
                weeks.size,
                reached,
                weeks.size
            ),
            highlight = reached > 0
        )
    }
}

/** Wie bei den Wochentagen bekommt auch die 0 einen Sockel, sonst fehlt die Woche optisch. */
private fun goalBarHeight(count: Int, scale: Int): Dp =
    if (scale <= 0) Dimens.StatsBarMinHeight
    else Dimens.StatsBarMinHeight + (Dimens.GoalBarMaxHeight - Dimens.StatsBarMinHeight) *
        (count.toFloat() / scale)
