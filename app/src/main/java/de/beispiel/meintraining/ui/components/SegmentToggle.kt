package de.beispiel.meintraining.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.TabActiveSurface
import de.beispiel.meintraining.ui.theme.TabActiveText
import de.beispiel.meintraining.ui.theme.TabInactiveText

/**
 * Umschalter aus nebeneinanderliegenden Reitern wie „Kraft | Cardio“ oder „km/h | Stufe“ – im
 * Stil des „kg | %“ im Tracking, damit er als Auswahl gelesen wird und nicht als Knopf.
 *
 * Jeder Reiter ist [segmentWidth] breit; die Beschriftungen bleiben einzeilig.
 */
@Composable
fun SegmentToggle(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    segmentWidth: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(Dimens.CornerTab)
            .background(ChipBackground)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Box(
                modifier = Modifier
                    .height(Dimens.UnitToggleHeight)
                    .width(segmentWidth)
                    .clip(Dimens.CornerTab)
                    .background(if (isSelected) TabActiveSurface else ChipBackground)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .semantics { selected = isSelected },
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
    }
}
