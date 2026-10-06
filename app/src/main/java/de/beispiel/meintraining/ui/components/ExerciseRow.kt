package de.beispiel.meintraining.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AccentBlue
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.CardBackground
import de.beispiel.meintraining.ui.theme.CardDraggedBackground
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary

/**
 * Eine Übungszeile. Die Composable ist zustandslos.
 *
 * [note] steht als zweite, kleine Zeile unter dem Namen – eine Zeile, bei Überlänge als
 * Laufschrift wie der Name. Die Karte wird dafür nicht höher; ohne Notiz sieht sie aus wie
 * immer.
 *
 * [weightLabel] und [setsLabel] sind `null`, wenn nichts eingetragen ist. Ohne Gewicht rückt
 * der Name in dessen Spalte vor; die Sätze-Spalte bleibt dabei an ihrem Platz, weil sie
 * rechts am Pfeilbutton hängt. Sind beide leer, entfällt der Wertebereich ganz.
 *
 * Mit [setsProgress] führt die Übung ein Satz-Protokoll: Der Sätze-Chip wird antippbar
 * ([onSetsClick]) und zeigt den heutigen Stand (siehe [SetsChip]).
 *
 * Den blauen Pfeil gibt es nur mit Gewicht – ohne Gewicht gäbe es nichts zu verschieben. Sein
 * Platz bleibt trotzdem frei, solange die Zeile Werte zeigt, damit die Spalten stehen bleiben.
 * Mit [progressionDown] zeigt er nach unten und senkt das Gewicht, statt es zu erhöhen.
 * Lang gedrückt geht er einen Schritt in die Gegenrichtung ([onProgressLongClick]).
 *
 * [dragModifier] wird im Auswahlmodus auf die markierte Zeile gelegt: Wer ausgewählt hat,
 * kann direkt schieben. [isDragging] hebt die Karte dabei optisch ab.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExerciseRow(
    name: String,
    weightLabel: String?,
    setsLabel: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onProgressClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    onProgressLongClick: () -> Unit = {},
    progressionDown: Boolean = false,
    setsProgress: SetsProgress? = null,
    onSetsClick: () -> Unit = {},
    isDragging: Boolean = false,
    isSelectable: Boolean = false,
    isSelected: Boolean = false,
    dragModifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.CardHeight)
                // Nur die markierte Zeile lässt sich schieben; alle anderen scrollen weiter.
                .then(if (isSelected) dragModifier else Modifier)
                .shadow(
                    elevation = if (isDragging) {
                        Dimens.CardElevationDragged
                    } else {
                        Dimens.CardElevationResting
                    },
                    shape = Dimens.CornerCard
                )
                .clip(Dimens.CornerCard)
                .background(if (isDragging) CardDraggedBackground else CardBackground)
                .border(
                    width = if (isSelected) Dimens.SelectionBorderWidth else 0.dp,
                    color = if (isSelected) AccentBlue else Color.Transparent,
                    shape = Dimens.CornerCard
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rechts liefert der 48dp-Pfeilbutton den optischen Rand, links reicht ein
            // schmaler Innenabstand – im Auswahlmodus steht dort der Haken.
            if (isSelectable) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(Dimens.SelectionMarkWidth),
                    contentAlignment = Alignment.Center
                ) {
                    SelectionMark(isSelected = isSelected)
                }
            } else {
                Spacer(modifier = Modifier.width(Dimens.CardPaddingStart))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = AppTextStyles.ExerciseName,
                    color = TextPrimary,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.loopingMarquee()
                )
                if (note != null) {
                    Text(
                        text = note,
                        style = AppTextStyles.ExerciseNote,
                        color = TextSecondary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.loopingMarquee()
                    )
                }
            }
            if (weightLabel != null || setsLabel != null) {
                // Ohne Gewicht entfällt die Spalte ganz und der Name bekommt ihre Breite.
                if (weightLabel != null) {
                    Spacer(modifier = Modifier.width(Dimens.ChipSpacing))
                    ValueSlot(label = weightLabel, width = Dimens.ChipWeightWidth)
                }
                // Die Sätze-Spalte bleibt reserviert: Sie hält das Gewicht in seiner Spalte,
                // auch wenn hier nichts steht.
                Spacer(modifier = Modifier.width(Dimens.ChipSpacing))
                if (setsLabel != null && setsProgress != null) {
                    SetsChip(
                        label = setsLabel,
                        progress = setsProgress,
                        width = Dimens.ChipSetsWidth,
                        enabled = !isSelectable,
                        onClick = onSetsClick,
                        onLongClick = onLongClick
                    )
                } else {
                    ValueSlot(label = setsLabel, width = Dimens.ChipSetsWidth)
                }
            }
            when {
                // Verschieben kann man nur, was ein Gewicht hat.
                // Ein IconButton kennt keinen langen Druck; die Box bildet ihn nach – gleiche
                // Größe, runde Welle, im Auswahlmodus gesperrt. Der lange Druck nutzt die
                // übliche Android-Wartezeit und vibriert kurz, wenn er greift.
                weightLabel != null -> Box(
                    modifier = Modifier
                        .size(Dimens.TouchTargetSize)
                        .combinedClickable(
                            interactionSource = null,
                            // Dieselbe runde 40dp-Welle wie beim IconButton.
                            indication = ripple(bounded = false, radius = 20.dp),
                            enabled = !isSelectable,
                            role = Role.Button,
                            onLongClickLabel = stringResource(R.string.action_weight_step_back),
                            onLongClick = onProgressLongClick,
                            hapticFeedbackEnabled = true,
                            onClick = onProgressClick
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (progressionDown) {
                                R.drawable.ic_arrow_downward
                            } else {
                                R.drawable.ic_arrow_upward
                            }
                        ),
                        contentDescription = stringResource(
                            if (progressionDown) {
                                R.string.cd_decrease_weight
                            } else {
                                R.string.cd_increase_weight
                            }
                        ),
                        tint = AccentBlue,
                        modifier = Modifier.size(Dimens.ArrowIconSize)
                    )
                }
                // Zeile mit Werten, aber ohne Gewicht: Platz halten, sonst wandern die Spalten.
                setsLabel != null -> Spacer(modifier = Modifier.width(Dimens.TouchTargetSize))
                // Ganz ohne Werte reicht ein schmaler Rand.
                else -> Spacer(modifier = Modifier.width(Dimens.CardPaddingStart))
            }
        }
    }
}

@Composable
private fun SelectionMark(isSelected: Boolean) {
    if (isSelected) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.cd_selected),
            tint = AccentBlue,
            modifier = Modifier.size(Dimens.SelectionMarkSize)
        )
    } else {
        // Der leere Kreis sagt TalkBack von sich aus nichts; ohne Beschriftung wäre im
        // Auswahlmodus nur die markierte Hälfte der Zeilen zu erkennen.
        val description = stringResource(R.string.cd_not_selected)
        Box(
            modifier = Modifier
                .size(Dimens.SelectionMarkSize)
                .border(Dimens.SelectionBorderWidth, TextSecondary, CircleShape)
                .semantics { contentDescription = description }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360)
@Composable
private fun ExerciseRowPreview() {
    MeinTrainingTheme {
        ExerciseRow(
            name = "Trizeps (Seil)",
            weightLabel = "20 Kg",
            setsLabel = "3 x 4-6",
            onClick = {},
            onLongClick = {},
            onProgressClick = {},
            modifier = Modifier.padding(Dimens.ScreenPaddingHorizontal),
            note = "Kabel ganz oben · Ellbogen fest",
            setsProgress = SetsProgress(logged = 1, planned = 3)
        )
    }
}

/** Ohne jeden Wert bleibt die ganze Breite für den Namen. */
@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360)
@Composable
private fun ExerciseRowWithoutValuesPreview() {
    MeinTrainingTheme {
        ExerciseRow(
            name = "Hüfte dehn routine",
            weightLabel = null,
            setsLabel = null,
            onClick = {},
            onLongClick = {},
            onProgressClick = {},
            modifier = Modifier.padding(Dimens.ScreenPaddingHorizontal)
        )
    }
}

/** Nur Sätze: Die Gewichtsspalte bleibt leer, aber an ihrem Platz. */
@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360)
@Composable
private fun ExerciseRowSelectedPreview() {
    MeinTrainingTheme {
        ExerciseRow(
            name = "ATG Split Squat",
            weightLabel = null,
            setsLabel = "3 x 6-10",
            onClick = {},
            onLongClick = {},
            onProgressClick = {},
            isSelectable = true,
            isSelected = true,
            modifier = Modifier.padding(Dimens.ScreenPaddingHorizontal)
        )
    }
}
