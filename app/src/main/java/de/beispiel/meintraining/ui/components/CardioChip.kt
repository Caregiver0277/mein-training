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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
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
import de.beispiel.meintraining.ui.theme.TextSecondary

/**
 * Was eine Cardio-Zeile statt Gewicht und Sätzen zeigt.
 *
 * [label] sind die Zielwerte als Text, etwa „20 min · 6 km/h · 8 %“; `null`, wenn keiner gesetzt
 * ist – eingetragen werden kann trotzdem. [isLoggedToday]: Für diese Zeile steht heute eine
 * Einheit da. [arrowDescription] beschreibt den Pfeil für TalkBack („Tempo erhöhen“); `null`
 * heißt: kein Pfeil, weil kein Wert gewählt oder der gewählte leer ist.
 */
@Immutable
data class CardioChipState(
    val label: String?,
    val isLoggedToday: Boolean = false,
    val arrowDescription: String? = null
)

/**
 * Der breite Chip einer Cardio-Zeile: die Zielwerte über beide Spalten, antippbar zum Eintragen
 * der Einheit. Bei Überlänge läuft der Text durch wie in jedem anderen Chip.
 *
 * Aufgebaut wie der Sätze-Chip mit Protokoll ([SetsChip]): Der feine Rand sagt, dass sich hier
 * etwas antippen lässt, und sobald die Einheit von heute drin ist, steht der Chip grün da und
 * trägt den Haken an seiner Ecke.
 *
 * Mit [enabled] `false` – im Auswahlmodus – fängt der Chip nichts ab: Ein Tippen markiert die
 * Zeile darunter, wie überall sonst auf der Karte. Der lange Druck ([onLongClick]) ist derselbe
 * wie auf der Zeile.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardioChip(
    state: CardioChipState,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateText = stringResource(
        if (state.isLoggedToday) R.string.cd_cardio_logged else R.string.cd_cardio_not_logged
    )
    Box(modifier = modifier.width(Dimens.ChipCardioWidth)) {
        Box(
            modifier = Modifier
                .width(Dimens.ChipCardioWidth)
                .height(Dimens.ChipHeight)
                .clip(Dimens.CornerChip)
                .background(if (state.isLoggedToday) AccentGreenSurface else ChipBackground)
                .border(Dimens.SelectionBorderWidth, LoggableChipOutline, Dimens.CornerChip)
                .then(
                    if (enabled) {
                        Modifier.combinedClickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.cardio_log_title),
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    } else {
                        Modifier
                    }
                )
                .semantics { stateDescription = stateText },
            contentAlignment = Alignment.Center
        ) {
            Text(
                // Ganz ohne Ziele lädt der Chip nur zum Eintragen ein – leer sähe er kaputt aus.
                text = state.label ?: stringResource(R.string.cardio_chip_no_targets),
                style = AppTextStyles.ChipText,
                color = if (state.label != null) TextPrimary else TextSecondary,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = Dimens.ChipPaddingHorizontal)
                    .loopingMarquee()
            )
        }
        if (state.isLoggedToday) {
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
private fun CardioChipPreview() {
    MeinTrainingTheme {
        Box(modifier = Modifier.padding(Dimens.SectionSpacingLarge)) {
            CardioChip(
                state = CardioChipState(label = "20 min · 6 km/h · 8 %", isLoggedToday = true),
                enabled = true,
                onClick = {},
                onLongClick = {}
            )
        }
    }
}
