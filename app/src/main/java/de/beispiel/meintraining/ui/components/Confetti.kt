package de.beispiel.meintraining.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.SeriesColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Kurzer Konfetti-Regen aus der Bildmitte – der Applaus für eine volle Runde.
 *
 * Er kommt genau einmal, wenn das letzte offene Training der Runde abgehakt wird, dauert zwei
 * Sekunden und hält niemanden auf: Die Schnipsel fangen nichts ab, was darunter liegt, weil
 * eine [Canvas] keine Berührungen entgegennimmt.
 *
 * [burstId] zählt die Anlässe hoch statt ein `Boolean` zu setzen, aus demselben Grund wie beim
 * Haken: Eine Zahl ist bei jedem Mal neu und startet den Verlauf zuverlässig, auch wenn der
 * vorige noch läuft. `0` heißt „noch nichts zu feiern“ – so regnet es beim Öffnen der App nicht
 * los, nur weil der Zustand frisch ist.
 */
@Composable
fun Confetti(burstId: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(DONE) }
    // Die Schnipsel werden je Anlass neu ausgewürfelt; zweimal derselbe Regen fiele auf.
    val pieces = remember { mutableStateOf(emptyList<ConfettiPiece>()) }

    LaunchedEffect(burstId) {
        if (burstId == 0) return@LaunchedEffect
        pieces.value = confettiPieces()
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = DONE,
            // Gleichmäßig, das Ausklingen steckt in der Flugbahn selbst.
            animationSpec = tween(durationMillis = CONFETTI_MILLIS, easing = LinearEasing)
        )
        // Aufräumen: Sonst hielte der Bildschirm die Schnipsel bis zum nächsten Mal im Speicher.
        pieces.value = emptyList()
    }

    // Gelesen wird erst beim Zeichnen. Die Fläche wird deshalb während der zwei Sekunden kein
    // einziges Mal neu zusammengesetzt, und im Ruhezustand kostet sie gar nichts.
    Canvas(modifier = modifier.fillMaxSize()) {
        val value = progress.value
        if (value >= DONE) return@Canvas
        pieces.value.forEach { piece -> drawPiece(piece, value) }
    }
}

/** Ein einzelnes Schnipsel. Alle Maße sind Bruchteile der Fläche, damit es überall gleich wirkt. */
@Immutable
private class ConfettiPiece(
    /** Startrichtung im Bogenmaß. */
    val angle: Float,
    /** Wie weit es fliegt, als Vielfaches der kürzeren Bildkante. */
    val distance: Float,
    val width: Float,
    /** Höhe im Verhältnis zur Breite – aus Quadraten werden so auch Streifen. */
    val ratio: Float,
    val color: Color,
    /** Umdrehungen während des Fluges; negativ heißt andersherum. */
    val spin: Float,
    /** Wo im Taumeln das Schnipsel startet. */
    val phase: Float
)

private fun confettiPieces(random: Random = Random.Default): List<ConfettiPiece> =
    List(PIECE_COUNT) {
        ConfettiPiece(
            angle = random.nextFloat() * TWO_PI,
            distance = random.nextFloat(MIN_DISTANCE, MAX_DISTANCE),
            width = random.nextFloat(MIN_WIDTH, MAX_WIDTH),
            ratio = random.nextFloat(MIN_RATIO, MAX_RATIO),
            color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.size)],
            spin = random.nextFloat(MIN_SPIN, MAX_SPIN) * if (random.nextBoolean()) 1f else -1f,
            phase = random.nextFloat() * TWO_PI
        )
    }

/**
 * Ein Schnipsel zum Zeitpunkt [progress] (0 = Start, 1 = vorbei).
 *
 * Der Flug ist bewusst keine gerade Linie: Nach außen wird es schnell langsamer, nach unten
 * dagegen immer schneller – zusammen ergibt das den Bogen, den ein weggeschossenes Stück Papier
 * beschreibt. Das Taumeln entsteht allein aus der schwankenden Breite; ein echtes Kippen in der
 * Tiefe wäre für zwei Sekunden zu viel Aufwand und sähe kaum anders aus.
 */
private fun DrawScope.drawPiece(piece: ConfettiPiece, progress: Float) {
    val outward = 1f - (1f - progress) * (1f - progress)
    val reach = piece.distance * size.minDimension * outward
    val x = center.x + cos(piece.angle) * reach
    val y = center.y + sin(piece.angle) * reach + GRAVITY * size.height * progress * progress

    val width = piece.width * size.minDimension
    val height = width * piece.ratio
    // Beim Taumeln verschwindet das Schnipsel nie ganz: Eine Mindestbreite bleibt stehen.
    val flutter = MIN_FLUTTER +
        (1f - MIN_FLUTTER) * abs(cos(piece.phase + piece.spin * progress * TWO_PI))
    val visible = width * flutter

    val fade = (1f - progress) / FADE_PORTION
    rotate(degrees = piece.spin * progress * FULL_TURN, pivot = Offset(x, y)) {
        drawRect(
            color = piece.color.copy(alpha = fade.coerceIn(0f, 1f)),
            topLeft = Offset(x - visible / 2f, y - height / 2f),
            size = Size(visible, height)
        )
    }
}

private fun Random.nextFloat(from: Float, until: Float): Float =
    from + nextFloat() * (until - from)

/** Die Farben des Trackings plus das Grün des Hakens – bunt, aber aus derselben Palette. */
private val CONFETTI_COLORS = SeriesColors + AccentGreen

private const val DONE = 1f
private const val CONFETTI_MILLIS = 2_000
private const val PIECE_COUNT = 56
private const val MIN_DISTANCE = 0.25f
private const val MAX_DISTANCE = 0.95f
private const val MIN_WIDTH = 0.016f
private const val MAX_WIDTH = 0.032f
private const val MIN_RATIO = 0.4f
private const val MAX_RATIO = 1.4f
private const val MIN_SPIN = 0.8f
private const val MAX_SPIN = 3f
private const val MIN_FLUTTER = 0.15f
/** Wie fällt es nach unten weg – als Vielfaches der Bildhöhe am Ende des Fluges. */
private const val GRAVITY = 0.85f
/** Der letzte Teil des Fluges wird ausgeblendet; davor bleiben die Schnipsel voll sichtbar. */
private const val FADE_PORTION = 0.35f
private const val FULL_TURN = 360f
private const val TWO_PI = (2 * Math.PI).toFloat()

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360, heightDp = 640)
@Composable
private fun ConfettiPreview() {
    MeinTrainingTheme {
        Confetti(burstId = 1)
    }
}
