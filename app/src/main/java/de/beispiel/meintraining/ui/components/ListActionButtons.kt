package de.beispiel.meintraining.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.R
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AccentGreenSurface
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.OutlineColor
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

/**
 * Abschluss der Liste: links der Haken, der das Training in den Verlauf einträgt,
 * rechts kompakt das „+“ zum Anlegen einer Übung.
 *
 * [isCompleted] färbt den Haken grün und sperrt ihn: Jeder Trainingstag wird pro Runde nur
 * einmal abgehakt. Grün bleibt er, bis alle Tage dran waren – so sieht man auf einen Blick,
 * welche Tage der Runde noch offen sind.
 */
@Composable
fun ListActionButtons(
    onCompleteWorkout: () -> Unit,
    onAddExercise: () -> Unit,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
    ) {
        CompleteWorkoutButton(
            onClick = onCompleteWorkout,
            isCompleted = isCompleted,
            modifier = Modifier.weight(1f)
        )
        AddExerciseButton(onClick = onAddExercise)
    }
}

/**
 * Der Knopf, der das Training abhakt – mit dem einzigen Effekt der App.
 *
 * Das ist der Moment, auf den das Training hinausläuft, und er soll sich auch so anfühlen:
 * Der Knopf federt kurz ein, ein Ring läuft nach außen, ein paar Funken stieben weg, und das
 * Handy gibt einen kurzen Stoß. Alles zusammen dauert etwa eine halbe Sekunde und hält
 * niemanden auf.
 */
@Composable
private fun CompleteWorkoutButton(
    onClick: () -> Unit,
    isCompleted: Boolean,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    /**
     * Zählt jeden Druck hoch. Ein `Boolean` täte es nicht: Er müsste nach der Animation wieder
     * zurückgesetzt werden, und bis dahin liefe kein zweiter Druck an. Eine Zahl ist bei jedem
     * Druck neu und startet den Effekt damit zuverlässig.
     */
    var pressCount by remember { mutableIntStateOf(0) }

    // Zwei getrennte Verläufe: Der Ring läuft gleichmäßig aus, der Knopf federt zurück. In
    // einem gemeinsamen Verlauf müssten sie sich auf eine Kurve einigen.
    val burst = remember { Animatable(BURST_DONE) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(pressCount) {
        if (pressCount == 0) return@LaunchedEffect
        burst.snapTo(0f)
        burst.animateTo(BURST_DONE, animationSpec = tween(BURST_MILLIS, easing = LinearOutSlowInEasing))
    }

    LaunchedEffect(pressCount) {
        if (pressCount == 0) return@LaunchedEffect
        scale.snapTo(1f)
        scale.animateTo(PRESS_SCALE, animationSpec = tween(PRESS_MILLIS, easing = FastOutSlowInEasing))
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    val accent by animateColorAsState(
        targetValue = if (isCompleted) AccentGreen else TextSecondary,
        animationSpec = tween(COLOR_MILLIS),
        label = "completeAccent"
    )
    val fill by animateColorAsState(
        targetValue = if (isCompleted) AccentGreenSurface else Color.Transparent,
        animationSpec = tween(COLOR_MILLIS),
        label = "completeFill"
    )
    val border by animateColorAsState(
        targetValue = if (isCompleted) AccentGreen else OutlineColor,
        animationSpec = tween(COLOR_MILLIS),
        label = "completeBorder"
    )

    // Der äußere Kasten wird bewusst nicht zugeschnitten: Ring und Funken dürfen über die
    // Knopfkante hinauslaufen. Der innere trägt Form, Farbe und Druckfläche.
    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            drawBurst(burst.value)
        },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.AddButtonHeight)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(Dimens.CornerAddButton)
                .background(fill)
                .border(
                    width = Dimens.AddButtonBorderWidth,
                    color = border,
                    shape = Dimens.CornerAddButton
                )
                // Abgehakt ist abgehakt: kein zweiter Eintrag für denselben Tag.
                .clickable(enabled = !isCompleted, role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    pressCount++
                    onClick()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(
                    if (isCompleted) R.string.cd_workout_completed else R.string.cd_complete_workout
                ),
                tint = accent,
                modifier = Modifier
                    .size(Dimens.MenuIconSize)
                    // Der Haken schlägt kräftiger aus als der Knopf, sonst ginge er im
                    // Federn des Rahmens unter.
                    .graphicsLayer {
                        val extra = 1f + (scale.value - 1f) * ICON_SCALE_BOOST
                        scaleX = extra
                        scaleY = extra
                    }
            )
        }
    }
}

/**
 * Ring und Funken, ausgehend von der Mitte des Knopfes.
 *
 * [progress] läuft von 0 (Druck) bis 1 (vorbei); bei 1 wird nichts mehr gezeichnet, damit im
 * Ruhezustand keine unsichtbaren Kreise mitlaufen.
 */
private fun DrawScope.drawBurst(progress: Float) {
    if (progress >= BURST_DONE) return

    val center = Offset(size.width / 2f, size.height / 2f)
    val fade = 1f - progress

    // Ring: läuft nach außen und wird dabei dünner und blasser.
    drawCircle(
        color = AccentGreen.copy(alpha = fade * RING_ALPHA),
        radius = size.height * (RING_START + progress * RING_GROWTH),
        center = center,
        style = Stroke(width = size.height * RING_WIDTH * fade)
    )

    // Funken: gleichmäßig im Kreis verteilt, mit abklingender Geschwindigkeit nach außen.
    val distance = size.height * SPARK_DISTANCE * (1f - fade * fade)
    val radius = size.height * SPARK_RADIUS * fade
    repeat(SPARK_COUNT) { index ->
        val angle = index.toFloat() / SPARK_COUNT * TWO_PI
        drawCircle(
            color = AccentGreen.copy(alpha = fade),
            radius = radius,
            center = Offset(
                x = center.x + cos(angle) * distance,
                y = center.y + sin(angle) * distance
            )
        )
    }
}

/** Transparenter Button mit 1dp-Rahmen und zentriertem „+“. */
@Composable
private fun AddExerciseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(Dimens.AddButtonWidth)
            .height(Dimens.AddButtonHeight)
            .clip(Dimens.CornerAddButton)
            .border(Dimens.AddButtonBorderWidth, OutlineColor, Dimens.CornerAddButton)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.cd_add_exercise),
            tint = TextPrimary,
            modifier = Modifier.size(Dimens.MenuIconSize)
        )
    }
}

// Maße des Effekts als Vielfache der Knopfhöhe, damit er auf jedem Gerät gleich wirkt.
private const val BURST_DONE = 1f
private const val BURST_MILLIS = 620
private const val PRESS_MILLIS = 90
private const val COLOR_MILLIS = 260
private const val PRESS_SCALE = 0.92f
private const val ICON_SCALE_BOOST = 2.2f
private const val RING_START = 0.35f
private const val RING_GROWTH = 0.9f
private const val RING_WIDTH = 0.06f
private const val RING_ALPHA = 0.8f
private const val SPARK_COUNT = 10
private const val SPARK_DISTANCE = 1.0f
private const val SPARK_RADIUS = 0.07f
private const val TWO_PI = (2 * Math.PI).toFloat()

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360)
@Composable
private fun ListActionButtonsPreview() {
    MeinTrainingTheme {
        ListActionButtons(
            onCompleteWorkout = {},
            onAddExercise = {},
            isCompleted = true,
            modifier = Modifier.padding(Dimens.ScreenPaddingHorizontal)
        )
    }
}
