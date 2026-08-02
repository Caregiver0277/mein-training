package de.beispiel.meintraining.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.TextSecondary

/**
 * Spaltenlabels über der Liste.
 * Verwendet exakt dieselben Gewichtungen und Breiten wie [ExerciseRow],
 * damit die Labels genau über den Chips sitzen.
 */
@Composable
fun ColumnHeaderRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Links derselbe Innenabstand wie in der Karte, rechts die Breite des Pfeil-Buttons.
        Spacer(modifier = Modifier.width(Dimens.CardPaddingStart))
        Text(
            text = stringResource(R.string.column_exercise),
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(Dimens.ChipSpacing))
        HeaderLabel(
            text = stringResource(R.string.column_weight),
            width = Dimens.ChipWeightWidth
        )
        Spacer(modifier = Modifier.width(Dimens.ChipSpacing))
        HeaderLabel(
            text = stringResource(R.string.column_sets_reps),
            width = Dimens.ChipSetsWidth
        )
        Spacer(modifier = Modifier.width(Dimens.TouchTargetSize))
    }
}

/**
 * Label über einem Chip. Der Text wird nicht umbrochen und darf über die Chipbreite
 * hinausragen – dadurch bleibt seine Mitte exakt auf der Chipmitte.
 */
@Composable
private fun HeaderLabel(text: String, width: Dp) {
    Box(modifier = Modifier.width(width), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = AppTextStyles.ColumnLabel,
            color = TextSecondary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360)
@Composable
private fun ColumnHeaderRowPreview() {
    MeinTrainingTheme {
        ColumnHeaderRow(modifier = Modifier.padding(Dimens.ScreenPaddingHorizontal))
    }
}
