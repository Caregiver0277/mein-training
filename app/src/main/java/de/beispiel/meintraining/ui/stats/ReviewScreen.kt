package de.beispiel.meintraining.ui.stats

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.components.SegmentToggle
import de.beispiel.meintraining.ui.screen.SubScreenHeader
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.MenuButtonIcon
import de.beispiel.meintraining.ui.theme.ScreenBackground
import de.beispiel.meintraining.ui.theme.TextDisabled
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.HeatmapWeek
import de.beispiel.meintraining.util.Review
import de.beispiel.meintraining.util.ReviewPeriod
import de.beispiel.meintraining.util.formatMonthYear
import de.beispiel.meintraining.util.toDecimalString
import de.beispiel.meintraining.util.toggled
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** So viele Meilensteine nennt die Karte; der Rest steht darunter auf der Seite. */
private const val CARD_MILESTONES = 3
private const val MINUTES_PER_HOUR = 60

/**
 * Was die Seite zeigt: der [review] des gewählten Zeitraums, dazu [firstDate], der erste Tag mit
 * Daten – davor gibt es nichts zurückzublättern –, und [today].
 */
data class ReviewPage(val review: Review, val firstDate: LocalDate?, val today: LocalDate)

/** Sichert den gewählten Zeitraum über das Drehen hinweg – als Art und Zahl. */
val ReviewPeriodSaver: Saver<ReviewPeriod?, Any> = Saver(
    save = { period ->
        when (period) {
            null -> null
            is ReviewPeriod.Month -> listOf(0, period.month.year, period.month.monthValue)
            is ReviewPeriod.Year -> listOf(1, period.year, 0)
        }
    },
    restore = { saved ->
        val (kind, year, month) = (saved as List<*>).map { it as Int }
        if (kind == 0) ReviewPeriod.Month(YearMonth.of(year, month)) else ReviewPeriod.Year(year)
    }
)

/**
 * Der Rückblick auf einen Monat oder ein Jahr – geöffnet von der Statistikseite.
 *
 * Oben die Wahl des Zeitraums, darunter die Karte mit allem Wichtigen und der Knopf „Teilen“: Er
 * macht aus genau dieser Karte ein Bild. Was man sieht, ist also, was man teilt – ohne eine zweite
 * Fassung, die nur im Bild existiert und unbemerkt veralten kann. Unter der Karte stehen die
 * Meilensteine, die nicht mehr hineinpassen.
 *
 * [page] ist `null`, solange gerechnet wird.
 */
@Composable
fun ReviewScreen(
    period: ReviewPeriod,
    page: ReviewPage?,
    onPeriodChange: (ReviewPeriod) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        SubScreenHeader(title = stringResource(R.string.review_title), onBack = onBack)
        val today = page?.today ?: LocalDate.now()
        PeriodPicker(
            period = period,
            canGoBack = page?.firstDate?.let { period.start.isAfter(it) } ?: false,
            canGoForward = period.end.isBefore(today),
            onPeriodChange = onPeriodChange,
            today = today
        )
        if (page == null) return@Column

        val graphicsLayer = rememberGraphicsLayer()
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val resources = LocalResources.current
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
        ) {
            // Aufgezeichnet wird mit dem Hintergrund der Seite drumherum: Das Bild bekommt so
            // einen dunklen Rand, statt dass die runden Ecken der Karte durchsichtig ausfransen.
            Box(
                modifier = Modifier
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
                    .background(ScreenBackground)
                    .padding(Dimens.SectionSpacingSmall)
            ) {
                ReviewCard(page.review)
            }
            Button(
                onClick = {
                    scope.launch {
                        runCatching {
                            shareReviewImage(
                                context = context,
                                bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap(),
                                chooserTitle = resources.getString(R.string.review_share_chooser)
                            )
                        }.onFailure {
                            Toast.makeText(context, R.string.review_share_failed, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = TextPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = Dimens.SectionSpacingSmall)
                        .size(Dimens.StepperIconSize)
                )
                Text(text = stringResource(R.string.review_share), style = AppTextStyles.TabLabel)
            }
            val more = page.review.milestones.drop(CARD_MILESTONES)
            if (more.isNotEmpty()) {
                StatsCard(title = stringResource(R.string.review_milestones)) {
                    more.forEach { ReachedMilestoneRow(it, page.review.period.end) }
                }
            }
            Spacer(modifier = Modifier.height(Dimens.ListBottomPadding))
        }
    }
}

/** „Monat | Jahr“ und dazwischen blättern: ‹ Oktober 2026 ›. */
@Composable
private fun PeriodPicker(
    period: ReviewPeriod,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPeriodChange: (ReviewPeriod) -> Unit,
    today: LocalDate
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Dimens.SectionSpacingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SegmentToggle(
            labels = listOf(stringResource(R.string.review_month), stringResource(R.string.review_year)),
            selectedIndex = if (period is ReviewPeriod.Year) 1 else 0,
            onSelect = { index ->
                if ((index == 1) != (period is ReviewPeriod.Year)) onPeriodChange(period.toggled(today))
            },
            segmentWidth = Dimens.IntensityToggleWidth
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { onPeriodChange(period.previous()) }, enabled = canGoBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.review_previous),
                tint = if (canGoBack) MenuButtonIcon else TextDisabled
            )
        }
        Text(
            text = periodLabel(period),
            style = AppTextStyles.ExerciseName,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        IconButton(onClick = { onPeriodChange(period.next()) }, enabled = canGoForward) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.review_next),
                tint = if (canGoForward) MenuButtonIcon else TextDisabled
            )
        }
    }
}

private fun periodLabel(period: ReviewPeriod): String = when (period) {
    is ReviewPeriod.Month -> formatMonthYear(period.start)
    is ReviewPeriod.Year -> period.year.toString()
}

/**
 * Die kompakte Karte – auf der Seite und als Bild zum Teilen: Zeitraum, drei Kacheln, die
 * wichtigsten Zahlen, bis zu drei Meilensteine und der Kalender des Zeitraums.
 */
@Composable
internal fun ReviewCard(review: Review) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.CornerCard)
            .background(CardBackground)
            .padding(Dimens.SheetPadding)
    ) {
        Text(
            text = stringResource(R.string.review_title),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary
        )
        Text(text = periodLabel(review.period), style = AppTextStyles.Title, color = TextPrimary)

        if (review.isEmpty) {
            Text(
                text = stringResource(R.string.review_empty),
                style = AppTextStyles.Body,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = Dimens.SectionSpacingMedium)
            )
        } else {
            ReviewFacts(review)
        }

        if (review.weeks.isNotEmpty()) {
            MiniHeatmap(
                weeks = review.weeks,
                showMonths = review.period is ReviewPeriod.Year,
                modifier = Modifier.padding(top = Dimens.SectionSpacingLarge)
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SectionSpacingMedium)
        )
    }
}

@Composable
private fun ReviewFacts(review: Review) {
    Row(
        modifier = Modifier.padding(top = Dimens.SectionSpacingMedium),
        horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
    ) {
        ReviewTile(
            value = review.sessions.toString(),
            label = stringResource(R.string.review_sessions),
            modifier = Modifier.weight(1f)
        )
        ReviewTile(
            value = review.trainingMinutes?.let { formatTrainingTime(it) } ?: stringResource(R.string.review_none),
            label = stringResource(R.string.review_time),
            modifier = Modifier.weight(1f)
        )
        ReviewTile(
            value = if (review.gainKg > 0.0) {
                stringResource(R.string.review_gain_value, review.gainKg.roundTo(2).toDecimalString())
            } else {
                stringResource(R.string.review_none)
            },
            label = stringResource(R.string.review_gain),
            accent = review.gainKg > 0.0,
            modifier = Modifier.weight(1f)
        )
    }
    review.topGain?.let { gain ->
        Fact(
            label = stringResource(R.string.review_top_gain),
            value = stringResource(R.string.review_top_gain_value, gain.name, gain.gainKg.roundTo(2).toDecimalString())
        )
    }
    if (review.cardioSessions > 0) {
        Fact(
            label = stringResource(R.string.review_cardio_minutes),
            value = stringResource(R.string.stats_minutes_value, review.cardioMinutes.roundToInt())
        )
        if (review.cardioKm > 0.0) {
            Fact(
                label = stringResource(R.string.review_cardio_km),
                value = stringResource(R.string.stats_km_value, review.cardioKm.roundTo(1).toDecimalString())
            )
        }
    }
    review.topWeekday?.let { day ->
        Fact(
            label = stringResource(R.string.review_weekday),
            value = stringResource(
                R.string.review_weekday_value,
                day.getDisplayName(TextStyle.FULL, Locale.GERMANY),
                review.topWeekdayCount
            )
        )
    }
    if (review.bestStreak > 0) {
        Fact(
            label = stringResource(R.string.review_streak),
            value = pluralStringResource(R.plurals.stats_weeks, review.bestStreak, review.bestStreak),
            highlight = true
        )
    }
    if (review.milestones.isNotEmpty()) {
        Text(
            text = stringResource(R.string.review_milestones),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            modifier = Modifier.padding(top = Dimens.SectionSpacingMedium)
        )
        // Das Jahr steht oben; am Datum fehlt es deshalb, auch in einem vergangenen Zeitraum.
        review.milestones.take(CARD_MILESTONES).forEach { ReachedMilestoneRow(it, review.period.end) }
        val rest = review.milestones.size - CARD_MILESTONES
        if (rest > 0) {
            Text(
                text = pluralStringResource(R.plurals.milestone_banner_more, rest, rest),
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary,
                modifier = Modifier.padding(top = Dimens.SectionSpacingSmall / 2)
            )
        }
    }
}

/** „45 min“, ab einer Stunde „12,5 h“. */
@Composable
private fun formatTrainingTime(minutes: Int): String =
    if (minutes < MINUTES_PER_HOUR) {
        stringResource(R.string.stats_minutes_value, minutes)
    } else {
        stringResource(R.string.review_hours, (minutes.toDouble() / MINUTES_PER_HOUR).roundTo(1).toDecimalString())
    }

@Composable
private fun ReviewTile(value: String, label: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Column(
        modifier = modifier
            .clip(Dimens.CornerCard)
            .background(ScreenBackground)
            .padding(vertical = Dimens.SectionSpacingMedium, horizontal = Dimens.SectionSpacingSmall),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = AppTextStyles.Title,
            color = if (accent) AccentGreen else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Der Kalender des Zeitraums, so breit wie die Karte: eine Spalte je Woche, Montag oben, in den
 * Farben des Kalenders der Statistik. Ein Monat hat wenige, große Felder, ein Jahr viele kleine
 * und darüber die Monatsnamen.
 */
@Composable
private fun MiniHeatmap(weeks: List<HeatmapWeek>, showMonths: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = AppTextStyles.ColumnLabel.copy(color = TextSecondary)
    val monthNames = remember(weeks) {
        weeks.map { week -> week.monthLabel?.getDisplayName(TextStyle.SHORT, Locale.GERMANY) }
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = Dimens.ReviewHeatmapGap
        val cell: Dp = min(Dimens.ReviewHeatmapMaxCell, (maxWidth + gap) / weeks.size - gap)
        val pitch = cell + gap
        val top = if (showMonths) Dimens.HeatmapMonthRowHeight else 0.dp
        val rows = DayOfWeek.entries.size
        Canvas(
            modifier = Modifier.size(width = pitch * weeks.size - gap, height = top + pitch * rows - gap)
        ) {
            val cellPx = cell.toPx()
            val pitchPx = pitch.toPx()
            val topPx = top.toPx()
            val corner = CornerRadius(cellPx / 5)
            weeks.forEachIndexed { column, week ->
                val x = column * pitchPx
                if (showMonths) {
                    monthNames[column]?.let { name ->
                        drawText(measurer, name, topLeft = Offset(x, 0f), style = labelStyle, softWrap = false)
                    }
                }
                week.days.forEachIndexed { row, day ->
                    if (day == null) return@forEachIndexed
                    drawRoundRect(
                        color = heatmapColor(day.count),
                        topLeft = Offset(x, topPx + row * pitchPx),
                        size = Size(cellPx, cellPx),
                        cornerRadius = corner
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360, heightDp = 900)
@Composable
private fun ReviewScreenPreview() {
    val today = LocalDate.now()
    val period = ReviewPeriod.Month(YearMonth.from(today))
    val dates = (0L until 30L step 2).map { today.minusDays(it) }
    MeinTrainingTheme {
        ReviewScreen(
            period = period,
            page = ReviewPage(
                review = de.beispiel.meintraining.util.review(
                    period = period,
                    sessions = dates.map {
                        de.beispiel.meintraining.data.model.WorkoutSession(
                            dayId = 1,
                            completedAt = it.atTime(18, 0).atZone(java.time.ZoneId.systemDefault())
                                .toInstant().toEpochMilli()
                        )
                    },
                    weightLogsOldestFirst = emptyList(),
                    cardioLogs = emptyList(),
                    definitions = emptyList(),
                    weeklyGoal = 3,
                    reached = emptyList(),
                    today = today
                ),
                firstDate = today.minusYears(1),
                today = today
            ),
            onPeriodChange = {},
            onBack = {}
        )
    }
}
