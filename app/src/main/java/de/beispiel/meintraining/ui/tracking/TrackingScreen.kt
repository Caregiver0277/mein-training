package de.beispiel.meintraining.ui.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.annotation.StringRes
import androidx.compose.ui.unit.Dp
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.model.CardioValue
import de.beispiel.meintraining.ui.components.SegmentToggle
import de.beispiel.meintraining.ui.components.cardioUnits
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AccentRed
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MenuButtonIcon
import de.beispiel.meintraining.ui.theme.TabActiveSurface
import de.beispiel.meintraining.ui.theme.TabActiveText
import de.beispiel.meintraining.ui.theme.TabInactiveText
import de.beispiel.meintraining.ui.theme.TextDisabled
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import de.beispiel.meintraining.util.formatCardioValue
import de.beispiel.meintraining.util.formatFullDate
import de.beispiel.meintraining.util.toClockTime
import de.beispiel.meintraining.util.toLocalDate
import de.beispiel.meintraining.util.toWeightLabel

/**
 * Hängt den Tracking-Screen an sein ViewModel. Der Screen selbst bleibt zustandslos,
 * damit er sich wie der Rest der App in der Vorschau darstellen lässt.
 */
@Composable
fun TrackingRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TrackingViewModel = viewModel(factory = TrackingViewModel.Factory)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    TrackingScreen(
        uiState = uiState,
        onBack = onBack,
        onRangeSelected = viewModel::onRangeSelected,
        onManualYearSelected = viewModel::onManualYearSelected,
        onPercentSelected = viewModel::onPercentSelected,
        onKindSelected = viewModel::onKindSelected,
        onCardioValueSelected = viewModel::onCardioValueSelected,
        onPickerOpen = viewModel::onPickerOpen,
        onPickerDismiss = viewModel::onPickerDismiss,
        onExerciseToggled = viewModel::onExerciseToggled,
        onExerciseLongPressed = viewModel::onExerciseLongPressed,
        onToggleAll = viewModel::onToggleAll,
        onPointsDismiss = viewModel::onPointsDismiss,
        onDeletePoint = viewModel::onDeletePoint,
        modifier = modifier
    )
}

@Composable
fun TrackingScreen(
    uiState: TrackingUiState,
    onBack: () -> Unit,
    onRangeSelected: (TimeRange) -> Unit,
    onManualYearSelected: (Int) -> Unit,
    onPercentSelected: (Boolean) -> Unit,
    onKindSelected: (TrackingKind) -> Unit,
    onCardioValueSelected: (CardioValue) -> Unit,
    onPickerOpen: () -> Unit,
    onPickerDismiss: () -> Unit,
    onExerciseToggled: (String) -> Unit,
    onExerciseLongPressed: (String) -> Unit,
    onToggleAll: () -> Unit,
    onPointsDismiss: () -> Unit,
    onDeletePoint: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val isCardio = uiState.kind == TrackingKind.CARDIO
    val units = cardioUnits()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.HeaderHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(Dimens.TouchTargetSize)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = MenuButtonIcon,
                    modifier = Modifier.size(Dimens.MenuIconSize)
                )
            }
            Text(
                text = stringResource(R.string.drawer_tracking),
                style = AppTextStyles.Title,
                color = TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Dimens.SectionSpacingSmall)
            )
            UnitToggle(
                isPercent = uiState.isPercent,
                isCardio = isCardio,
                onPercentSelected = onPercentSelected
            )
            IconButton(onClick = onPickerOpen, modifier = Modifier.size(Dimens.TouchTargetSize)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.tracking_pick_exercises),
                    tint = MenuButtonIcon,
                    modifier = Modifier.size(Dimens.MenuIconSize)
                )
            }
        }

        KindSelector(
            kind = uiState.kind,
            cardioValue = uiState.cardioValue,
            onKindSelected = onKindSelected,
            onCardioValueSelected = onCardioValueSelected
        )

        Spacer(modifier = Modifier.height(Dimens.SectionSpacingSmall))

        RangeSelector(
            selected = uiState.range,
            manualYear = uiState.manualYear,
            availableYears = uiState.availableYears,
            onRangeSelected = onRangeSelected,
            onManualYearSelected = onManualYearSelected
        )

        Spacer(modifier = Modifier.height(Dimens.SectionSpacingLarge))

        WeightChart(
            series = uiState.series,
            window = uiState.window,
            ticks = uiState.ticks,
            emptyText = stringResource(
                when (uiState.emptyReason) {
                    ChartEmptyReason.NOTHING_SELECTED -> R.string.tracking_empty_selection
                    ChartEmptyReason.NOTHING_IN_RANGE ->
                        if (isCardio) R.string.tracking_cardio_empty_range else R.string.tracking_empty_range
                    ChartEmptyReason.NO_PERCENT_BASE ->
                        if (isCardio) R.string.tracking_cardio_empty_percent else R.string.tracking_empty_percent
                    else -> if (isCardio) R.string.tracking_cardio_empty else R.string.tracking_empty
                }
            ),
            isPercent = uiState.isPercent,
            valueLabel = if (isCardio) {
                { value, line -> formatCardioValue(uiState.cardioValue, value, line.intensityUnit, units) }
            } else {
                null
            }
        )

        Spacer(modifier = Modifier.height(Dimens.SectionSpacingMedium))

        Legend(
            series = uiState.series,
            noBaseText = if (isCardio) R.string.tracking_cardio_legend_no_base else R.string.tracking_legend_no_base,
            modifier = Modifier.verticalScroll(rememberScrollState())
        )
    }

    if (uiState.pickerOpen) {
        ExercisePickerDialog(
            emptyText = stringResource(if (isCardio) R.string.tracking_cardio_empty else R.string.tracking_empty),
            names = uiState.trackedNames,
            visibleNames = uiState.visibleNames,
            allVisible = uiState.allVisible,
            onToggle = onExerciseToggled,
            onLongPress = onExerciseLongPressed,
            onToggleAll = onToggleAll,
            onDismiss = onPickerDismiss
        )
    }

    // Liegt über dem Auswahlfenster: Von dort kommt der lange Druck, und danach steht die
    // Auswahl wieder offen, ohne dass man sie erneut aufrufen muss.
    uiState.pointsExercise?.let { name ->
        DataPointsDialog(
            exerciseName = name,
            points = uiState.points,
            cardioValue = if (isCardio) uiState.cardioValue else null,
            onDeletePoint = onDeletePoint,
            onDismiss = onPointsDismiss
        )
    }
}

/**
 * Die einzelnen Datenpunkte einer Übung, jüngster zuerst, jeder für sich löschbar.
 *
 * Gelöscht wird sofort und ohne Rückfrage: Ein einzelner Punkt ist schnell wieder eingetragen,
 * und die Liste zeigt unmittelbar, was passiert ist. Das eingetragene Gewicht der Übung bleibt
 * unberührt – hier steht der Verlauf, nicht der heutige Stand.
 */
@Composable
private fun DataPointsDialog(
    exerciseName: String,
    points: List<TrackedPoint>,
    /** Unter Cardio der Wert der Kurve; `null` bei den Gewichten. */
    cardioValue: CardioValue?,
    onDeletePoint: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val unit = stringResource(R.string.unit_kg)
    val cardioUnits = cardioUnits()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        titleContentColor = TextPrimary,
        title = {
            Text(text = exerciseName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        text = {
            if (points.isEmpty()) {
                Text(
                    text = stringResource(R.string.tracking_points_empty),
                    style = AppTextStyles.Body,
                    color = TextSecondary
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = Dimens.PickerMaxHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.tracking_points_count,
                            points.size,
                            points.size
                        ),
                        style = AppTextStyles.ColumnLabel,
                        color = TextSecondary
                    )
                    if (cardioValue != null) {
                        Text(
                            text = stringResource(R.string.tracking_cardio_points_hint),
                            style = AppTextStyles.ColumnLabel,
                            color = TextSecondary
                        )
                    }
                    points.forEach { point ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (cardioValue != null) {
                                        formatCardioValue(cardioValue, point.weightKg, point.intensityUnit, cardioUnits)
                                    } else {
                                        point.weightKg.toWeightLabel(unit)
                                    },
                                    style = AppTextStyles.Body,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${formatFullDate(point.recordedAt.toLocalDate())}, " +
                                        point.recordedAt.toClockTime(),
                                    style = AppTextStyles.ColumnLabel,
                                    color = TextSecondary
                                )
                            }
                            IconButton(
                                onClick = { onDeletePoint(point.id) },
                                modifier = Modifier.size(Dimens.TouchTargetSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = AccentRed,
                                    modifier = Modifier.size(Dimens.MenuIconSize)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_done), color = AccentBlue)
            }
        }
    )
}

/**
 * Umschalter „kg | %“: Gewichte oder ihre Veränderung seit Beginn des Zeitraums.
 *
 * Zwei kleine Reiter im Stil der Zeitraum-Auswahl, damit er als Auswahl und nicht als Knopf
 * gelesen wird.
 */
@Composable
private fun UnitToggle(isPercent: Boolean, isCardio: Boolean, onPercentSelected: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(Dimens.CornerTab)
            .background(ChipBackground)
    ) {
        // Unter Cardio gibt es nicht die eine Einheit – Minuten, km, km/h, Stufe, Prozent –,
        // also schlicht „Wert“.
        UnitChip(
            label = stringResource(if (isCardio) R.string.tracking_value_label else R.string.tracking_unit_kg),
            description = stringResource(
                if (isCardio) R.string.cd_tracking_unit_value else R.string.cd_tracking_unit_kg
            ),
            isSelected = !isPercent,
            onClick = { onPercentSelected(false) },
            width = if (isCardio) Dimens.UnitToggleWideWidth else Dimens.UnitToggleWidth
        )
        UnitChip(
            label = stringResource(R.string.tracking_unit_percent),
            description = stringResource(R.string.cd_tracking_unit_percent),
            isSelected = isPercent,
            onClick = { onPercentSelected(true) }
        )
    }
}

/**
 * Umschalter „Kraft | Cardio“ und unter Cardio die Wahl des Werts, aus dem die Kurven entstehen.
 * Tempo und Stufe stehen unter einem Reiter; ihre Kurven bleiben trotzdem getrennt (siehe
 * [cardioCurves]).
 */
@Composable
private fun KindSelector(
    kind: TrackingKind,
    cardioValue: CardioValue,
    onKindSelected: (TrackingKind) -> Unit,
    onCardioValueSelected: (CardioValue) -> Unit
) {
    SegmentToggle(
        labels = listOf(stringResource(R.string.kind_strength), stringResource(R.string.kind_cardio)),
        selectedIndex = kind.ordinal,
        onSelect = { onKindSelected(TrackingKind.entries[it]) },
        segmentWidth = Dimens.KindToggleWidth
    )
    if (kind != TrackingKind.CARDIO) return
    Spacer(modifier = Modifier.height(Dimens.SectionSpacingSmall))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.TabSpacing)
    ) {
        CardioValue.entries.forEach { value ->
            RangeChip(
                label = stringResource(
                    when (value) {
                        CardioValue.DURATION -> R.string.tracking_cardio_duration
                        CardioValue.DISTANCE -> R.string.tracking_cardio_distance
                        CardioValue.INTENSITY -> R.string.tracking_cardio_intensity
                        CardioValue.INCLINE -> R.string.tracking_cardio_incline
                    }
                ),
                isSelected = value == cardioValue,
                onClick = { onCardioValueSelected(value) }
            )
        }
    }
}

@Composable
private fun UnitChip(
    label: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    width: Dp = Dimens.UnitToggleWidth
) {
    Box(
        modifier = Modifier
            .height(Dimens.UnitToggleHeight)
            .width(width)
            .clip(Dimens.CornerTab)
            .background(if (isSelected) TabActiveSurface else ChipBackground)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                selected = isSelected
                contentDescription = description
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = AppTextStyles.TabLabel,
            color = if (isSelected) TabActiveText else TabInactiveText,
            maxLines = 1
        )
    }
}

/** Zeitraum-Auswahl; „Jahr“ öffnet eine Liste der Jahre, für die es Daten gibt. */
@Composable
private fun RangeSelector(
    selected: TimeRange,
    manualYear: Int,
    availableYears: List<Int>,
    onRangeSelected: (TimeRange) -> Unit,
    onManualYearSelected: (Int) -> Unit
) {
    var yearMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Dimens.TabSpacing)
    ) {
        RangeChip(
            label = stringResource(R.string.range_total),
            isSelected = selected == TimeRange.TOTAL,
            onClick = { onRangeSelected(TimeRange.TOTAL) }
        )
        RangeChip(
            label = stringResource(R.string.range_year),
            isSelected = selected == TimeRange.YEAR_1,
            onClick = { onRangeSelected(TimeRange.YEAR_1) }
        )
        RangeChip(
            label = stringResource(R.string.range_months_6),
            isSelected = selected == TimeRange.MONTHS_6,
            onClick = { onRangeSelected(TimeRange.MONTHS_6) }
        )
        RangeChip(
            label = stringResource(R.string.range_months_3),
            isSelected = selected == TimeRange.MONTHS_3,
            onClick = { onRangeSelected(TimeRange.MONTHS_3) }
        )
        RangeChip(
            label = stringResource(R.string.range_month_1),
            isSelected = selected == TimeRange.MONTH_1,
            onClick = { onRangeSelected(TimeRange.MONTH_1) }
        )
        Box {
            RangeChip(
                label = if (selected == TimeRange.MANUAL_YEAR) {
                    manualYear.toString()
                } else {
                    stringResource(R.string.range_manual_year)
                },
                isSelected = selected == TimeRange.MANUAL_YEAR,
                onClick = { yearMenuOpen = true }
            )
            DropdownMenu(expanded = yearMenuOpen, onDismissRequest = { yearMenuOpen = false }) {
                availableYears.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(text = year.toString()) },
                        onClick = {
                            yearMenuOpen = false
                            onManualYearSelected(year)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(Dimens.TabHeight)
            .clip(Dimens.CornerTab)
            .background(if (isSelected) TabActiveSurface else ChipBackground)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = Dimens.SectionSpacingMedium),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = AppTextStyles.TabLabel,
            color = if (isSelected) TabActiveText else TabInactiveText,
            maxLines = 1
        )
    }
}

/**
 * Legende: kurzes Linienstück im Aussehen der Kurve, dahinter der Name.
 *
 * Eine Kurve ohne Bezugswert für Prozent steht ausgegraut da, mit dem Grund dahinter – sonst
 * sähe es aus, als fehlte sie aus Versehen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(
    series: List<ChartSeries>,
    /** „… (beginnt bei 0 kg, kein Prozentwert)“ – unter Cardio ohne „kg“. */
    @StringRes noBaseText: Int,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingLarge),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall)
    ) {
        series.forEachIndexed { index, line ->
            val appearance = appearanceFor(index).let {
                if (line.hasNoPercentBase) it.copy(color = it.color.copy(alpha = NO_BASE_ALPHA)) else it
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(
                    modifier = Modifier
                        .width(Dimens.LegendLineWidth)
                        .height(Dimens.LegendLineHeight)
                ) {
                    drawLine(
                        color = appearance.color,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = appearance.style.pathEffect
                    )
                    drawCircle(
                        color = appearance.color,
                        radius = 3.dp.toPx(),
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                Spacer(modifier = Modifier.width(Dimens.SectionSpacingSmall))
                Text(
                    text = if (line.hasNoPercentBase) {
                        stringResource(noBaseText, line.name)
                    } else {
                        line.name
                    },
                    style = AppTextStyles.ColumnLabel,
                    color = if (line.hasNoPercentBase) TextDisabled else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Wie blass eine Kurve ohne Bezugswert in der Legende steht. */
private const val NO_BASE_ALPHA = 0.4f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExercisePickerDialog(
    /** Steht da, wenn es gar keine Kurve gibt. */
    emptyText: String,
    names: List<String>,
    visibleNames: Set<String>,
    allVisible: Boolean,
    onToggle: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onToggleAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBackground,
        titleContentColor = TextPrimary,
        title = { Text(text = stringResource(R.string.tracking_pick_exercises)) },
        text = {
            if (names.isEmpty()) {
                Text(
                    text = emptyText,
                    style = AppTextStyles.Body,
                    color = TextSecondary
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = Dimens.PickerMaxHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.tracking_points_hint),
                        style = AppTextStyles.ColumnLabel,
                        color = TextSecondary
                    )
                    names.forEach { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Kurz: ein- und ausblenden. Lang: die Datenpunkte ansehen.
                                .combinedClickable(
                                    onClick = { onToggle(name) },
                                    onLongClick = { onLongPress(name) }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = name in visibleNames,
                                onCheckedChange = { onToggle(name) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AccentBlue,
                                    uncheckedColor = TextSecondary,
                                    checkmarkColor = TextPrimary
                                )
                            )
                            Text(
                                text = name,
                                style = AppTextStyles.Body,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            // Ein Schalter statt zwei Knöpfen: zeigt an, was der nächste Druck bewirkt.
            TextButton(onClick = onToggleAll, enabled = names.isNotEmpty()) {
                Text(
                    text = stringResource(
                        if (allVisible) R.string.tracking_hide_all else R.string.tracking_show_all
                    ),
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_done), color = AccentBlue)
            }
        }
    )
}
