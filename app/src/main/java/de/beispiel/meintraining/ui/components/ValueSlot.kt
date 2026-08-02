package de.beispiel.meintraining.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.TextPrimary

/**
 * Ein Spaltenplatz in der Übungskarte.
 *
 * Ohne Wert ([label] `null`) bleibt der Platz leer statt einen Chip mit Platzhalter zu zeigen.
 * Die feste Breite bleibt trotzdem stehen, damit Spaltenkopf und Karte übereinander liegen –
 * eine Karte ganz ohne Werte lässt den Bereich komplett weg (siehe `ExerciseRow`).
 */
@Composable
fun ValueSlot(label: String?, width: Dp, modifier: Modifier = Modifier) {
    if (label == null) {
        Spacer(modifier = modifier.width(width))
        return
    }
    Box(
        modifier = modifier
            .width(width)
            .height(Dimens.ChipHeight)
            .clip(Dimens.CornerChip)
            .background(ChipBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = AppTextStyles.ChipText,
            color = TextPrimary,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = Dimens.ChipPaddingHorizontal)
                .loopingMarquee()
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun ValueSlotPreview() {
    MeinTrainingTheme {
        Box(modifier = Modifier.padding(Dimens.SectionSpacingLarge)) {
            ValueSlot(label = "22,5 Kg", width = Dimens.ChipWeightWidth)
        }
    }
}
