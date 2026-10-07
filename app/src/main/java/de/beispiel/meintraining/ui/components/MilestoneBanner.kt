package de.beispiel.meintraining.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AccentGreenSurface
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/** So lange steht die Meldung – etwas länger als das Konfetti, damit man sie zu Ende liest. */
private const val BANNER_MILLIS = 4_000L

/**
 * Eine Meldung über erreichte Meilensteine: [title] und darunter je Meilenstein eine Zeile.
 * [id] zählt hoch wie beim Konfetti – zweimal dieselben Zeilen sind zwei Meldungen.
 */
@Immutable
data class MilestoneMessage(val id: Int, val title: String, val lines: List<String>)

/**
 * Die kurze Meldung oben, wenn ein Meilenstein erreicht ist – zum Konfetti.
 *
 * Oben und nicht als Snackbar: Unten stehen die Meldungen mit „Rückgängig“, und die neueste
 * verdrängt dort die vorige (siehe `TrainingScreen`). Erreicht eine Gewichtserhöhung einen
 * Meilenstein, muss ihr „Rückgängig“ trotzdem erreichbar bleiben. Die Meldung fängt keine
 * Berührungen ab und verschwindet von selbst.
 *
 * [message] ist die jüngste Meldung und bleibt stehen, auch wenn sie nicht mehr zu sehen ist –
 * sonst hätte das Ausblenden nichts mehr, das es ausblenden könnte. `null` heißt wie die `0` beim
 * Konfetti „noch nichts zu melden“.
 */
@Composable
fun MilestoneBanner(message: MilestoneMessage?, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(message?.id) {
        if (message == null) return@LaunchedEffect
        visible = true
        delay(BANNER_MILLIS)
        visible = false
    }
    AnimatedVisibility(
        visible = visible && message != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        message?.let { MilestoneBannerContent(it) }
    }
}

@Composable
private fun MilestoneBannerContent(message: MilestoneMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.ScreenPaddingHorizontal)
            .clip(Dimens.CornerCard)
            .background(AccentGreenSurface)
            .border(Dimens.BadgeBorderWidth, AccentGreen, Dimens.CornerCard)
            .padding(Dimens.SectionSpacingMedium),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = AccentGreen,
            modifier = Modifier.size(Dimens.MenuIconSize)
        )
        Column {
            Text(text = message.title, style = AppTextStyles.ColumnLabel, color = AccentGreen)
            message.lines.forEach { line ->
                Text(
                    text = line,
                    style = AppTextStyles.ExerciseName,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360)
@Composable
private fun MilestoneBannerPreview() {
    MeinTrainingTheme {
        MilestoneBannerContent(
            MilestoneMessage(1, "Meilensteine erreicht", listOf("50 Trainings", "Bankdrücken: dreistellig ↑"))
        )
    }
}
