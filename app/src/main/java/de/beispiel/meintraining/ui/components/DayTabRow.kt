package de.beispiel.meintraining.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.TabActiveSurface
import de.beispiel.meintraining.ui.theme.TabActiveText
import de.beispiel.meintraining.ui.theme.TabInactiveSurface
import de.beispiel.meintraining.ui.theme.TabInactiveText

/** Vier gleich breite Pill-Buttons für die Tagesauswahl. */
@Composable
fun DayTabRow(
    days: List<TrainingDay>,
    selectedDayId: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.TabSpacing)
    ) {
        days.forEach { day ->
            DayTab(
                label = day.name,
                isSelected = day.id == selectedDayId,
                onClick = { onDaySelected(day.id) }
            )
        }
    }
}

@Composable
private fun RowScope.DayTab(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(Dimens.TabHeight)
            .clip(Dimens.CornerTab)
            .background(if (isSelected) TabActiveSurface else TabInactiveSurface)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = AppTextStyles.TabLabel,
            color = if (isSelected) TabActiveText else TabInactiveText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360)
@Composable
private fun DayTabRowPreview() {
    MeinTrainingTheme {
        DayTabRow(
            days = (1..4).map { TrainingDay(id = it, name = "Tag $it") },
            selectedDayId = 1,
            onDaySelected = {},
            modifier = Modifier.padding(Dimens.ScreenPaddingHorizontal)
        )
    }
}
