package de.beispiel.meintraining.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.HeatmapOneSession
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.HEATMAP_MONTHS
import de.beispiel.meintraining.util.Heatmap
import de.beispiel.meintraining.util.formatFullDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Nur jeder zweite Wochentag bekommt links einen Namen, sonst stehen sie zu dicht. */
private val LABELED_WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

/**
 * Der Kalender der letzten zwölf Monate: eine Spalte je Woche, ein Kästchen je Tag, gefärbt nach
 * der Zahl der Trainings – keins, eins, zwei und mehr.
 *
 * Ein Jahr passt nicht in die Breite eines Handys, deshalb scrollt der Kalender seitlich und
 * beginnt am rechten Ende: Was gerade passiert, interessiert zuerst. Die Wochentage links
 * scrollen nicht mit. Ein Tipp auf einen Tag nennt Datum und Trainings darunter, ein zweiter auf
 * denselben Tag nimmt das zurück.
 */
@Composable
internal fun HeatmapCard(heatmap: Heatmap) {
    // Nach Datum gemerkt, nicht nach Spalte: Um Mitternacht rückt der Kalender weiter.
    var selected by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val selectedDay = selected?.let { date ->
        heatmap.weeks.flatMap { it.days }.firstOrNull { it?.date == date }
    }

    StatsCard(
        title = stringResource(R.string.stats_heatmap),
        trailing = { HeatmapLegend() }
    ) {
        Row(modifier = Modifier.padding(top = Dimens.SectionSpacingSmall)) {
            WeekdayLabels()
            // Steht anfangs ganz rechts: Der Wert wird beim Ausmessen auf das Ende begrenzt.
            val scroll = rememberScrollState(initial = Int.MAX_VALUE)
            Box(modifier = Modifier.horizontalScroll(scroll)) {
                HeatmapGrid(
                    heatmap = heatmap,
                    selected = selectedDay?.date,
                    onDayTapped = { date -> selected = if (date == selected) null else date }
                )
            }
        }
        Text(
            text = selectedDay?.let { day ->
                stringResource(
                    R.string.stats_heatmap_day,
                    formatFullDate(day.date),
                    if (day.count == 0) {
                        stringResource(R.string.stats_heatmap_no_session)
                    } else {
                        pluralStringResource(R.plurals.stats_sessions, day.count, day.count)
                    }
                )
            } ?: stringResource(R.string.stats_heatmap_hint),
            style = AppTextStyles.ColumnLabel,
            color = if (selectedDay != null) TextPrimary else TextSecondary,
            modifier = Modifier.padding(top = Dimens.SectionSpacingSmall)
        )
    }
}

/** Die Namen von Montag, Mittwoch und Freitag, auf Höhe ihrer Zeile. */
@Composable
private fun WeekdayLabels() {
    Column(
        modifier = Modifier
            .width(Dimens.HeatmapWeekdayWidth)
            .padding(top = Dimens.HeatmapMonthRowHeight),
        verticalArrangement = Arrangement.spacedBy(Dimens.HeatmapCellGap)
    ) {
        DayOfWeek.entries.forEach { day ->
            Box(modifier = Modifier.height(Dimens.HeatmapCellSize), contentAlignment = Alignment.CenterStart) {
                if (day in LABELED_WEEKDAYS) {
                    Text(
                        text = day.getDisplayName(TextStyle.SHORT, Locale.GERMANY),
                        style = AppTextStyles.ColumnLabel,
                        color = TextSecondary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/**
 * Gezeichnet statt aus einzelnen Kästchen zusammengesetzt: Ein Jahr sind gut 370 Zellen, als
 * eigene Elemente samt Tipp-Erkennung wäre das für eine Karte unter vielen zu schwer.
 */
@Composable
private fun HeatmapGrid(heatmap: Heatmap, selected: LocalDate?, onDayTapped: (LocalDate) -> Unit) {
    val measurer = rememberTextMeasurer()
    val labelStyle = AppTextStyles.ColumnLabel.copy(color = TextSecondary)
    val monthNames = remember(heatmap) {
        heatmap.weeks.map { week -> week.monthLabel?.getDisplayName(TextStyle.SHORT, Locale.GERMANY) }
    }
    val pitch: Dp = Dimens.HeatmapCellSize + Dimens.HeatmapCellGap
    val width = pitch * heatmap.weeks.size - Dimens.HeatmapCellGap
    val height = Dimens.HeatmapMonthRowHeight + pitch * DayOfWeek.entries.size - Dimens.HeatmapCellGap
    val description = pluralStringResource(
        R.plurals.stats_heatmap_description,
        HEATMAP_MONTHS.toInt(),
        HEATMAP_MONTHS.toInt()
    )
    // Die Tipp-Erkennung startet nur neu, wenn sich der Kalender ändert – und ruft trotzdem
    // immer die aktuelle Reaktion auf.
    val onTap by rememberUpdatedState(onDayTapped)

    Canvas(
        modifier = Modifier
            .size(width = width, height = height)
            .semantics { contentDescription = description }
            .pointerInput(heatmap) {
                detectTapGestures { offset ->
                    val pitchPx = pitch.toPx()
                    val y = offset.y - Dimens.HeatmapMonthRowHeight.toPx()
                    if (y < 0f) return@detectTapGestures
                    val day = heatmap.dayAt((offset.x / pitchPx).toInt(), (y / pitchPx).toInt())
                    if (day != null) onTap(day.date)
                }
            }
    ) {
        val cell = Dimens.HeatmapCellSize.toPx()
        val pitchPx = pitch.toPx()
        val top = Dimens.HeatmapMonthRowHeight.toPx()
        val corner = CornerRadius(Dimens.HeatmapCellCorner.toPx())
        val mark = Stroke(width = Dimens.HeatmapMarkStroke.toPx())

        heatmap.weeks.forEachIndexed { column, week ->
            val x = column * pitchPx
            monthNames[column]?.let { name ->
                drawText(measurer, name, topLeft = Offset(x, 0f), style = labelStyle, softWrap = false)
            }
            week.days.forEachIndexed { row, day ->
                if (day == null) return@forEachIndexed
                val topLeft = Offset(x, top + row * pitchPx)
                drawRoundRect(
                    color = heatmapColor(day.count),
                    topLeft = topLeft,
                    size = Size(cell, cell),
                    cornerRadius = corner
                )
                val outline: Color? = when (day.date) {
                    selected -> AccentBlue
                    heatmap.today -> TextPrimary
                    else -> null
                }
                if (outline != null) {
                    drawRoundRect(
                        color = outline,
                        topLeft = topLeft,
                        size = Size(cell, cell),
                        cornerRadius = corner,
                        style = mark
                    )
                }
            }
        }
    }
}

/** Die drei Farbstufen samt ihrer Bedeutung, klein neben dem Titel. */
@Composable
private fun HeatmapLegend() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall / 2)
    ) {
        listOf(
            0 to stringResource(R.string.stats_heatmap_legend_none),
            1 to stringResource(R.string.stats_heatmap_legend_one),
            2 to stringResource(R.string.stats_heatmap_legend_many)
        ).forEach { (count, label) ->
            Box(
                modifier = Modifier
                    .size(Dimens.HeatmapLegendSize)
                    .clip(RoundedCornerShape(Dimens.HeatmapCellCorner))
                    .background(heatmapColor(count))
            )
            Text(
                text = label,
                style = AppTextStyles.ColumnLabel,
                color = TextSecondary,
                modifier = Modifier.padding(end = Dimens.SectionSpacingSmall / 2)
            )
        }
    }
}

private fun heatmapColor(count: Int): Color = when {
    count <= 0 -> ChipBackground
    count == 1 -> HeatmapOneSession
    else -> AccentGreen
}
