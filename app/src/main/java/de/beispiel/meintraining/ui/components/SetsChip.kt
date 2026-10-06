package de.beispiel.meintraining.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AccentGreenSurface
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.ChipBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.LoggableChipOutline
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.TextPrimary

/**
 * Wie weit das Satz-Protokoll einer Zeile heute ist: [logged] der [planned] geplanten Sätze.
 * Zusatzsätze zählen nicht mit – der Chip zeigt den Plan, nicht das Volumen.
 */
@Immutable
data class SetsProgress(val logged: Int, val planned: Int) {
    val fraction: Float get() = if (planned <= 0) 0f else (logged.toFloat() / planned).coerceIn(0f, 1f)
    val isComplete: Boolean get() = planned > 0 && logged >= planned
}

/**
 * Der Sätze-Chip einer Übung mit Satz-Protokoll: antippbar, und der heutige Fortschritt steht
 * als Füllstand hinter dem Text – wie der Balken der Pausenuhr, nur wächst er hier. Sind alle
 * geplanten Sätze drin, sitzt ein grüner Haken an seiner Ecke.
 *
 * Der feine Rand ist der Hinweis, dass sich hier etwas antippen lässt; ohne Protokoll hat der
 * Chip keinen (siehe [ValueSlot]).
 *
 * Mit [enabled] `false` – im Auswahlmodus – fängt der Chip nichts ab: Ein Tippen geht an die
 * Zeile darunter und markiert sie, wie überall sonst auf der Karte. Der lange Druck ([onLongClick])
 * ist derselbe wie auf der Zeile, damit der Chip kein Loch in ihre Bedienung reißt.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetsChip(
    label: String,
    progress: SetsProgress,
    width: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = pluralStringResource(
        R.plurals.cd_sets_progress,
        progress.planned,
        progress.logged,
        progress.planned
    )
    Box(modifier = modifier.width(width)) {
        Box(
            modifier = Modifier
                .width(width)
                .height(Dimens.ChipHeight)
                .clip(Dimens.CornerChip)
                .background(ChipBackground)
                .drawBehind {
                    if (progress.logged == 0) return@drawBehind
                    drawRect(
                        color = AccentGreenSurface,
                        size = Size(width = size.width * progress.fraction, height = size.height)
                    )
                }
                .border(Dimens.SelectionBorderWidth, LoggableChipOutline, Dimens.CornerChip)
                .then(
                    if (enabled) {
                        Modifier.combinedClickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.action_log_sets),
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    } else {
                        Modifier
                    }
                )
                .semantics { stateDescription = state },
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
        if (progress.isComplete) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = Dimens.SetsCheckOffset, y = -Dimens.SetsCheckOffset)
                    .size(Dimens.SetsCheckSize)
                    .clip(CircleShape)
                    .background(AccentGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    // Der Stand steht schon in der Beschreibung des Chips.
                    contentDescription = null,
                    tint = CardBackground,
                    modifier = Modifier.size(Dimens.SetsCheckIconSize)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1C222B)
@Composable
private fun SetsChipPreview() {
    MeinTrainingTheme {
        Box(modifier = Modifier.padding(Dimens.SectionSpacingLarge)) {
            SetsChip(
                label = "3 x 8-12",
                progress = SetsProgress(logged = 2, planned = 3),
                width = Dimens.ChipSetsWidth,
                enabled = true,
                onClick = {},
                onLongClick = {}
            )
        }
    }
}
