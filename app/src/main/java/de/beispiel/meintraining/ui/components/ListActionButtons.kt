package de.beispiel.meintraining.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * [isCompleted] färbt den Haken grün. Grün bleibt er, bis alle Tage der Runde dran waren – so
 * sieht man auf einen Blick, welche noch offen sind. Ein zweites Tippen nimmt das Abhaken
 * wieder zurück; der Haken ist damit sein eigenes „Rückgängig“ und braucht keine Meldung, die
 * sich über ihn schiebt.
 *
 * Solange das Training aussteht, ist der Haken gar nicht hier, sondern schwebt groß über der
 * Liste – siehe [FloatingCheck]. [isCheckFloating] sagt, ob das gerade so ist; sein Platz
 * bleibt dann trotzdem stehen, damit die Zeile nicht springt und der Anflug ein Ziel hat.
 */
@Composable
fun ListActionButtons(
    onToggleWorkoutCompleted: () -> Unit,
    onAddExercise: () -> Unit,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    isCheckFloating: Boolean = false,
    /** Kommt von außen, weil nur der schwebende Haken wissen muss, wo sein Platz liegt. */
    checkSlotModifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
    ) {
        val slot = checkSlotModifier
            .weight(1f)
            .height(Dimens.AddButtonHeight)
        if (isCheckFloating) {
            Box(modifier = slot)
        } else {
            CompleteWorkoutButton(
                onClick = onToggleWorkoutCompleted,
                isCompleted = isCompleted,
                modifier = slot
            )
        }
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
 *
 * Beim Zurücknehmen federt der Knopf nur; Ring und Funken bleiben dem Abhaken vorbehalten.
 * Ein Feuerwerk fürs Rückgängigmachen wäre am Anlass vorbei.
 *
 * Seine Größe bringt der Knopf nicht selbst mit, sie kommt über [modifier]: Derselbe Knopf
 * steht einmal schmal am Listenende und einmal groß in der Bildmitte, und dazwischen wächst er
 * Bild für Bild – siehe [FloatingCheckOverlay].
 *
 * Fläche, Rahmen und Haken werden deshalb gezeichnet statt zusammengesetzt: Kein einziger Wert
 * dieses Knopfes wird beim Zusammensetzen gelesen, und während Anflug, Farbwechsel und
 * Federn wird nichts neu zusammengesetzt – es wird nur neu gezeichnet. Mit `background`,
 * `border` und einem `Icon` lief dagegen jedes Bild des Farbübergangs durch die ganze
 * Composable samt neuer Modifier-Kette, und das ausgerechnet in den ersten Millisekunden des
 * Anflugs, wo gleichzeitig der Schleier von der Liste zieht.
 *
 * @param iconScale wie groß der Haken darin ausfällt, 1 heißt: wie am Listenende, höchstens
 *   [MAX_ICON_SCALE]. Eine Funktion, weil der Wert sich während des Anflugs mit jedem Bild
 *   ändert und deshalb erst beim Zeichnen gelesen gehört.
 */
@Composable
internal fun CompleteWorkoutButton(
    onClick: () -> Unit,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    /**
     * Läuft im Moment des Drucks, noch vor [onClick] – für alles, was nicht auf die Antwort
     * der Datenbank warten soll.
     */
    onPressed: () -> Unit = {},
    iconScale: () -> Float = { 1f }
) {
    val haptics = LocalHapticFeedback.current

    /**
     * Der Haken als Malwerkzeug statt als Composable.
     *
     * Ein Vektorbild rastert sich in der Größe, in der es gezeichnet wird, und legt das
     * Ergebnis ab. Wuchs der Haken über seine gemessene Größe, entstand dieses Zwischenbild
     * bei *jedem* Bild des Anflugs neu – der teuerste Posten der ganzen Bewegung. Gerastert
     * wird deshalb einmal in der größten vorkommenden Größe; kleiner wird er über die
     * Zeichenfläche, und das bleibt scharf.
     */
    val check = rememberVectorPainter(Icons.Filled.Check)

    // Beide zählen Drücke hoch. Ein `Boolean` täte es nicht: Er müsste nach der Animation
    // wieder zurückgesetzt werden, und bis dahin liefe kein zweiter Druck an. Eine Zahl ist
    // bei jedem Druck neu und startet den Effekt damit zuverlässig.
    //
    // Getrennt, weil der Knopf bei jedem Druck federt, Ring und Funken aber nur beim Abhaken.
    var pressCount by remember { mutableIntStateOf(0) }
    var burstCount by remember { mutableIntStateOf(0) }

    // Zwei getrennte Verläufe: Der Ring läuft gleichmäßig aus, der Knopf federt zurück. In
    // einem gemeinsamen Verlauf müssten sie sich auf eine Kurve einigen.
    val burst = remember { Animatable(BURST_DONE) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(burstCount) {
        if (burstCount == 0) return@LaunchedEffect
        burst.snapTo(0f)
        burst.animateTo(
            targetValue = BURST_DONE,
            animationSpec = tween(durationMillis = BURST_MILLIS, easing = LinearOutSlowInEasing)
        )
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

    /**
     * Ein einziger Verlauf für alle drei Farben – Haken, Fläche und Rahmen wechseln ohnehin
     * gemeinsam. Gelesen wird er erst beim Zeichnen; ein `animateColorAsState` je Farbe hätte
     * denselben Wert dreimal geführt und dafür jedes Bild lang neu zusammengesetzt.
     *
     * 0 heißt offen, 1 abgehakt.
     */
    val tone = remember { Animatable(if (isCompleted) DONE else OPEN) }
    LaunchedEffect(isCompleted) {
        val target = if (isCompleted) DONE else OPEN
        if (tone.value != target) tone.animateTo(target, tween(COLOR_MILLIS))
    }

    val description = stringResource(
        if (isCompleted) R.string.cd_workout_completed else R.string.cd_complete_workout
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
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .clip(Dimens.CornerAddButton)
                // Kein zweiter Eintrag für denselben Tag: Das zweite Tippen nimmt zurück.
                .clickable(role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    pressCount++
                    if (!isCompleted) burstCount++
                    onPressed()
                    onClick()
                }
                // Der Haken ist gezeichnet und nicht mehr als eigenes Element vorhanden; die
                // Beschriftung gehört damit an die Druckfläche selbst.
                .semantics { contentDescription = description }
                .drawBehind {
                    drawFace(
                        tone = tone.value,
                        // Der Haken schlägt kräftiger aus als der Knopf, sonst ginge er im
                        // Federn des Rahmens unter. Gezeichnet wird innerhalb der schon
                        // gefederten Ebene, deshalb zählt hier nur der Aufschlag.
                        iconScale = iconScale() * (1f + (scale.value - 1f) * ICON_SCALE_BOOST),
                        check = check
                    )
                }
        )
    }
}

/**
 * Fläche, Rahmen und Haken des Knopfes in einem Zug.
 *
 * [tone] blendet von offen (0) nach abgehakt (1); der Rahmen liegt wie bei `Modifier.border`
 * innen an der Kante, deshalb der halbe Strich Einzug.
 */
private fun DrawScope.drawFace(tone: Float, iconScale: Float, check: VectorPainter) {
    val radius = Dimens.CornerAddButton.topStart.toPx(size, this)
    val fill = lerp(Color.Transparent, AccentGreenSurface, tone)
    if (fill.alpha > 0f) {
        drawRoundRect(color = fill, cornerRadius = CornerRadius(radius))
    }

    val stroke = Dimens.AddButtonBorderWidth.toPx()
    drawRoundRect(
        color = lerp(OutlineColor, AccentGreen, tone),
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius((radius - stroke / 2f).coerceAtLeast(0f)),
        style = Stroke(width = stroke)
    )

    // Immer in derselben Größe gerastert und nur über die Zeichenfläche verkleinert – so
    // entsteht das Zwischenbild des Vektors ein einziges Mal statt bei jedem Bild neu.
    val side = Dimens.MenuIconSize.toPx() * MAX_ICON_SCALE
    val factor = iconScale.coerceIn(0f, MAX_ICON_SCALE) / MAX_ICON_SCALE
    scale(scale = factor, pivot = center) {
        translate(left = center.x - side / 2f, top = center.y - side / 2f) {
            with(check) {
                draw(
                    size = Size(side, side),
                    colorFilter = ColorFilter.tint(lerp(TextSecondary, AccentGreen, tone))
                )
            }
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

/**
 * Die größte Größe, in der der Haken vorkommt – als Vielfaches von [Dimens.MenuIconSize].
 *
 * In dieser Größe wird sein Vektorbild gerastert. Wer ihn größer zeichnen ließe, bekäme ein
 * hochgerechnetes und damit weiches Bild; der schwebende Haken hält sich deshalb genau daran.
 */
internal const val MAX_ICON_SCALE = 2.1f

// Farbverlauf des Knopfes: offen und abgehakt.
private const val OPEN = 0f
private const val DONE = 1f

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
            onToggleWorkoutCompleted = {},
            onAddExercise = {},
            isCompleted = true,
            modifier = Modifier.padding(Dimens.ScreenPaddingHorizontal)
        )
    }
}
