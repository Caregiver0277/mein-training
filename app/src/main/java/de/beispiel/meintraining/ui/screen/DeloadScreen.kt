package de.beispiel.meintraining.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.OutlineColor
import de.beispiel.meintraining.ui.theme.SupersetBackground
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.DEFAULT_DELOAD_CYCLE_WEEKS
import de.beispiel.meintraining.util.DeloadStatus
import de.beispiel.meintraining.util.REST_RESETS_CYCLE_DAYS
import de.beispiel.meintraining.util.formatFullDate

/**
 * Deload-Bereich: Wo stehe ich im Zyklus, wann ist der nächste Deload, was passiert dann.
 */
@Composable
fun DeloadScreen(
    status: DeloadStatus,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        SubScreenHeader(title = stringResource(R.string.drawer_deload), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
        ) {
            StatusCard(status = status)
            WeekStrip(status = status)
            if (status.cycleStart != null) FactsCard(status = status)
            RulesCard(cycleWeeks = status.cycleWeeks)
            Spacer(modifier = Modifier.height(Dimens.ListBottomPadding))
        }
    }
}

/** Die Kernaussage: Deload ja oder nein – und was daraus folgt. */
@Composable
private fun StatusCard(status: DeloadStatus) {
    val headline = when {
        status.isDeloadWeek -> stringResource(R.string.deload_state_now)
        status.isResting -> stringResource(R.string.deload_state_resting)
        status.cycleStart == null -> stringResource(R.string.deload_state_no_data)
        else -> pluralStringResource(
            R.plurals.deload_state_weeks_left,
            status.weeksUntilDeload,
            status.weeksUntilDeload
        )
    }
    val detail = when {
        status.isDeloadWeek -> stringResource(R.string.deload_detail_now)
        status.isResting -> stringResource(R.string.deload_detail_resting)
        status.cycleStart == null -> stringResource(R.string.deload_detail_no_data)
        else -> stringResource(
            R.string.deload_detail_upcoming,
            status.deloadWeekStart?.let(::formatFullDate).orEmpty()
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(if (status.isDeloadWeek) SupersetBackground else CardBackground)
            .padding(Dimens.SheetPadding)
    ) {
        Text(text = headline, style = AppTextStyles.Title, color = TextPrimary)
        Text(
            text = detail,
            style = AppTextStyles.Body,
            color = TextSecondary,
            modifier = Modifier.padding(top = Dimens.SectionSpacingSmall)
        )
    }
}

/** Sechs Punkte für die sechs Wochen des Blocks; der letzte ist die Deload-Woche. */
@Composable
private fun WeekStrip(status: DeloadStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .padding(Dimens.SheetPadding)
    ) {
        Text(
            text = stringResource(R.string.deload_cycle_title, status.cycleWeeks),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SectionSpacingMedium),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall)
        ) {
            (1..status.cycleWeeks).forEach { week ->
                val isDeloadWeek = week == status.cycleWeeks
                val isCurrent = week == status.weekInCycle && status.cycleStart != null
                val isDone = status.cycleStart != null && week < status.weekInCycle
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.WeekDotHeight)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCurrent && isDeloadWeek -> AccentGreen
                                isCurrent -> AccentBlue
                                isDone -> OutlineColor
                                else -> CardBackground
                            }
                        )
                        .border(Dimens.AddButtonBorderWidth, OutlineColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = week.toString(),
                        style = AppTextStyles.ColumnLabel,
                        color = if (isCurrent) TextPrimary else TextSecondary
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.deload_cycle_hint, status.cycleWeeks),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            modifier = Modifier.padding(top = Dimens.SectionSpacingMedium)
        )
    }
}

@Composable
private fun FactsCard(status: DeloadStatus) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .padding(Dimens.SheetPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingMedium)
    ) {
        Fact(
            label = stringResource(R.string.deload_fact_cycle_start),
            value = status.cycleStart?.let(::formatFullDate).orEmpty()
        )
        Fact(
            label = stringResource(R.string.deload_fact_last_session),
            value = status.lastSessionDate?.let(::formatFullDate).orEmpty()
        )
        Fact(
            label = stringResource(R.string.deload_fact_sessions_cycle),
            value = status.sessionsInCycle.toString()
        )
        Fact(
            label = stringResource(R.string.deload_fact_sessions_total),
            value = status.totalSessions.toString()
        )
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = AppTextStyles.Body,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(text = value, style = AppTextStyles.ExerciseName, color = TextPrimary)
    }
}

/** Kurz erklärt, wonach sich der Zyklus richtet. */
@Composable
private fun RulesCard(cycleWeeks: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .padding(Dimens.SheetPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingMedium)
    ) {
        Text(
            text = stringResource(R.string.deload_rules_title),
            style = AppTextStyles.ExerciseName,
            color = TextPrimary
        )
        Rule(text = stringResource(R.string.deload_rule_sets))
        Rule(text = stringResource(R.string.deload_rule_weights))
        Rule(text = stringResource(R.string.deload_rule_cycle, cycleWeeks))
        Rule(text = stringResource(R.string.deload_rule_rest, REST_RESETS_CYCLE_DAYS.toInt()))
        Rule(text = stringResource(R.string.deload_rule_display))
    }
}

@Composable
private fun Rule(text: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .padding(top = Dimens.SectionSpacingSmall, end = Dimens.SectionSpacingMedium)
                .size(Dimens.BulletSize)
                .clip(CircleShape)
                .background(AccentBlue)
        )
        Text(text = text, style = AppTextStyles.Body, color = TextSecondary)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360, heightDp = 800)
@Composable
private fun DeloadScreenPreview() {
    MeinTrainingTheme {
        DeloadScreen(
            status = DeloadStatus(
                isDeloadWeek = true,
                weekInCycle = DEFAULT_DELOAD_CYCLE_WEEKS,
                cycleStart = java.time.LocalDate.now().minusWeeks(5),
                deloadWeekStart = java.time.LocalDate.now(),
                lastSessionDate = java.time.LocalDate.now().minusDays(1),
                sessionsInCycle = 14,
                totalSessions = 31
            ),
            onBack = {}
        )
    }
}
