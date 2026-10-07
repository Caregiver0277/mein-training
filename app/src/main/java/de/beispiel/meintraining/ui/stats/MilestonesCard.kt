package de.beispiel.meintraining.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.MilestoneOverview
import de.beispiel.meintraining.util.NextMilestone
import de.beispiel.meintraining.util.ReachedMilestone
import de.beispiel.meintraining.util.formatShortDate
import java.time.LocalDate

/** So viele erreichte Meilensteine zeigt die Karte, bis man sie aufklappt – die jüngsten. */
private const val REACHED_PREVIEW = 5

/**
 * Die Meilensteine: oben die nächsten mit Balken, darunter die erreichten mit Datum, die jüngsten
 * zuerst. Gefeiert werden sie beim Erreichen auf dem Trainingsbildschirm; hier stehen sie zum
 * Nachlesen.
 */
@Composable
internal fun MilestonesCard(overview: MilestoneOverview, today: LocalDate) {
    StatsCard(title = stringResource(R.string.stats_milestones)) {
        if (overview.next.isNotEmpty()) {
            SectionLabel(stringResource(R.string.stats_milestones_next))
            overview.next.forEach { NextMilestoneRow(it) }
        }

        SectionLabel(stringResource(R.string.stats_milestones_reached))
        if (overview.reached.isEmpty()) {
            Text(
                text = stringResource(R.string.stats_milestones_none),
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary,
                modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
            )
            return@StatsCard
        }
        var expanded by rememberSaveable { mutableStateOf(false) }
        val shown = if (expanded) overview.reached else overview.reached.take(REACHED_PREVIEW)
        shown.forEach { ReachedMilestoneRow(it, today) }
        if (overview.reached.size > REACHED_PREVIEW) {
            Text(
                text = if (expanded) {
                    stringResource(R.string.stats_milestones_show_less)
                } else {
                    stringResource(R.string.stats_milestones_show_all, overview.reached.size)
                },
                style = AppTextStyles.ColumnLabel,
                color = AccentBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.TouchTargetSize)
                    .clickable { expanded = !expanded }
                    .padding(top = Dimens.SectionSpacingMedium)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = AppTextStyles.ColumnLabel,
        color = TextSecondary,
        modifier = Modifier.padding(top = Dimens.SectionSpacingMedium)
    )
}

/** Ein offener Meilenstein: Name und Stand, darunter der Balken. */
@Composable
private fun NextMilestoneRow(next: NextMilestone) {
    val resources = LocalResources.current
    Column(modifier = Modifier.padding(top = Dimens.SectionSpacingSmall)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = milestoneTitle(next.milestone, resources),
                style = AppTextStyles.Body,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = milestoneProgressText(next, resources),
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary,
                modifier = Modifier.padding(start = Dimens.SectionSpacingSmall)
            )
        }
        MilestoneBar(
            fraction = next.fraction.toFloat(),
            modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
        )
    }
}

/** Der Fortschritt zum nächsten Meilenstein; auch bei 0 ist die Bahn zu sehen. */
@Composable
internal fun MilestoneBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.MilestoneBarHeight)
            .clip(Dimens.CornerChip)
            .background(ChipBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(AccentBlue)
        )
    }
}

/** Ein erreichter Meilenstein mit seinem Datum. */
@Composable
internal fun ReachedMilestoneRow(reached: ReachedMilestone, today: LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.SectionSpacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier
                .padding(end = Dimens.SectionSpacingSmall)
                .size(Dimens.MilestoneIconSize)
        )
        Text(
            text = milestoneTitle(reached.milestone, LocalResources.current),
            style = AppTextStyles.Body,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatShortDate(reached.date, today),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            modifier = Modifier.padding(start = Dimens.SectionSpacingSmall)
        )
    }
}
