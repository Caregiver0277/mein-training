package de.beispiel.meintraining.ui.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MenuButtonIcon
import de.beispiel.meintraining.ui.theme.TabActiveSurface
import de.beispiel.meintraining.ui.theme.TabActiveText
import de.beispiel.meintraining.ui.theme.TabInactiveText
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary

/**
 * Hängt den Tracking-Screen an sein ViewModel. Der Screen selbst bleibt zustandslos,
 * damit er sich wie der Rest der App in der Vorschau darstellen lässt.
 */
@Composable
fun TrackingRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TrackingViewModel = viewModel(factory = TrackingViewModel.Factory)
    val uiState by viewModel.uiState.collectAsState()

    TrackingScreen(
        uiState = uiState,
        onBack = onBack,
        onRangeSelected = viewModel::onRangeSelected,
        onManualYearSelected = viewModel::onManualYearSelected,
        onPickerOpen = viewModel::onPickerOpen,
        onPickerDismiss = viewModel::onPickerDismiss,
        onExerciseToggled = viewModel::onExerciseToggled,
        onToggleAll = viewModel::onToggleAll,
        modifier = modifier
    )
}

@Composable
fun TrackingScreen(
    uiState: TrackingUiState,
    onBack: () -> Unit,
    onRangeSelected: (TimeRange) -> Unit,
    onManualYearSelected: (Int) -> Unit,
    onPickerOpen: () -> Unit,
    onPickerDismiss: () -> Unit,
    onExerciseToggled: (String) -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            IconButton(onClick = onPickerOpen, modifier = Modifier.size(Dimens.TouchTargetSize)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.tracking_pick_exercises),
                    tint = MenuButtonIcon,
                    modifier = Modifier.size(Dimens.MenuIconSize)
                )
            }
        }

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
            ticks = uiState.ticks
        )

        Spacer(modifier = Modifier.height(Dimens.SectionSpacingMedium))

        Legend(series = uiState.series, modifier = Modifier.verticalScroll(rememberScrollState()))
    }

    if (uiState.pickerOpen) {
        ExercisePickerDialog(
            names = uiState.trackedNames,
            visibleNames = uiState.visibleNames,
            allVisible = uiState.allVisible,
            onToggle = onExerciseToggled,
            onToggleAll = onToggleAll,
            onDismiss = onPickerDismiss
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

/** Legende: kurzes Linienstück im Aussehen der Kurve, dahinter der Name. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(series: List<ChartSeries>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingLarge),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingSmall)
    ) {
        series.forEachIndexed { index, line ->
            val appearance = appearanceFor(index)
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
                        pathEffect = appearance.style.pathEffect()
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
                    text = line.name,
                    style = AppTextStyles.ColumnLabel,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ExercisePickerDialog(
    names: List<String>,
    visibleNames: Set<String>,
    allVisible: Boolean,
    onToggle: (String) -> Unit,
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
                    text = stringResource(R.string.tracking_empty),
                    style = AppTextStyles.Body,
                    color = TextSecondary
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = Dimens.PickerMaxHeight)
                        .verticalScroll(rememberScrollState())
                ) {
                    names.forEach { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(name) },
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
