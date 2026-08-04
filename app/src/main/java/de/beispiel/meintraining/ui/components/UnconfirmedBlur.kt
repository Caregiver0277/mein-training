package de.beispiel.meintraining.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import de.beispiel.meintraining.ui.theme.Dimens

/**
 * Der leichte Schleier über den Übungen, solange das Training des Tages nicht abgehakt ist.
 *
 * Der Haken am Listenende ist schnell vergessen – man trainiert, legt das Handy weg, und der
 * Eintrag fehlt. Die leicht unscharfe Liste erinnert daran, ohne sich in den Weg zu stellen:
 * Lesen kann man sie weiter, und mit dem Abhaken zieht sie in einem Zug scharf.
 *
 * Der Wert läuft von 0 (scharf) bis 1 (verschleiert). Angelegt wird er über
 * [rememberUnconfirmedBlur], gezeichnet über [Modifier.unconfirmedBlur].
 */
@Stable
class UnconfirmedBlur internal constructor(
    private val progress: Animatable<Float, AnimationVector1D>
) {

    /**
     * Der gerade gültige Wert.
     *
     * Bewusst eine Funktion und keine Eigenschaft: So fällt beim Lesen auf, dass sie erst im
     * Zeichnen aufgerufen gehört – siehe [Modifier.unconfirmedBlur]. Beim Zusammensetzen
     * gelesen hinge die ganze Liste an einem Wert, der sich mit jedem Bild ändert, und würde
     * ebenso oft neu zusammengesetzt: für einen Verlauf, der nur die Zeichenebene betrifft.
     */
    fun current(): Float = progress.value
}

/**
 * Legt den Schleier für den angezeigten Tag an.
 *
 * Weich überblendet wird nur der eine Übergang, um den es geht: das Abhaken. Ein Tageswechsel
 * schaltet dagegen sofort um – die Liste des neuen Tages steht vom ersten Bild an richtig, so
 * wie Reiter und Zeilen auch. Ein Verlauf, der erst im Effekt umgestellt wird, käme ein Bild
 * später; der neue Tag blitzte in der Schärfe des alten auf. Deshalb hängt der Verlauf selbst
 * am Tag: Zum neuen Tag gehört ein neuer, und der beginnt schon beim Zusammensetzen am Ziel.
 *
 * @param isConfirmed ob das Training dieses Tages als eingetragen gilt – siehe
 *   `TrainingUiState.isSelectedDayConfirmed`.
 * @param isReady ob [isConfirmed] überhaupt schon aus der Datenbank stammt. Vor deren erster
 *   Antwort steht dort die Vorgabe „nicht abgehakt“, und ein Verlauf darauf zeigte beim Öffnen
 *   eine Blende, die nichts bedeutet.
 */
@Composable
fun rememberUnconfirmedBlur(
    dayId: Int,
    isConfirmed: Boolean,
    isReady: Boolean
): UnconfirmedBlur {
    val target = if (isConfirmed) SHARP else VEILED
    val progress = remember(dayId, isReady) { Animatable(target) }
    LaunchedEffect(progress, target) {
        // Nach einem Tageswechsel steht der frische Verlauf schon am Ziel. Ohne diese Abfrage
        // liefe trotzdem eine Animation los, die eine halbe Sekunde lang denselben Wert
        // ausrechnet und dafür jedes Bild anfordert.
        if (progress.value != target) {
            progress.animateTo(
                targetValue = target,
                animationSpec = tween(FADE_MILLIS, easing = FastOutSlowInEasing)
            )
        }
    }
    return remember(progress) { UnconfirmedBlur(progress) }
}

/**
 * Zeichnet den Inhalt einer Übungszeile weich.
 *
 * Gehört auf den *Inhalt* der Karte, nicht auf die Karte selbst: Hintergrund, Rahmen und der
 * Schatten der gezogenen Karte bleiben damit scharf, die Zeile behält ihre klare Kante. Weich
 * wird nur, was man liest – genau das wirkt wie ein unscharfes Foto und nicht wie ein Fehler.
 *
 * Der Wert wird erst hier gelesen: `graphicsLayer` mit Block wertet ihn beim Zeichnen aus, ohne
 * dass dafür irgendetwas neu zusammengesetzt werden muss. Am scharfen Ende bleibt die Ebene
 * deshalb einfach ohne Wirkung stehen, statt aus der Kette zu verschwinden: Fällt sie heraus,
 * behält die Zeile ihre alte Zeichenliste und verliert sichtbar ihren Kartenhintergrund –
 * ausgerechnet in dem Moment, in dem der Verlauf fertig ist. Eine wirkungslose Ebene kostet
 * dagegen nichts: ohne Weichzeichner und ohne Durchsichtigkeit zeichnet sie direkt durch.
 */
fun Modifier.unconfirmedBlur(blur: UnconfirmedBlur): Modifier = graphicsLayer {
    val amount = blur.current().coerceIn(SHARP, VEILED)
    if (amount <= SHARP) return@graphicsLayer

    val radius = Dimens.ExerciseBlurRadius.toPx() * amount
    // Clamp setzt die Randpixel fort, das Beschneiden hält das Weiche in der eigenen Fläche.
    // Ohne beides franste die Zeile an den Rändern aus und liefe in ihre Nachbarn.
    if (radius > 0f) {
        renderEffect = BlurEffect(
            radiusX = radius,
            radiusY = radius,
            edgeTreatment = TileMode.Clamp
        )
    }
    clip = true
    alpha = 1f - (1f - VEILED_ALPHA) * amount
}

/** Weichzeichnen beherrscht erst Android 12; darunter lässt Compose den Effekt stillschweigend fallen. */
private val CAN_BLUR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Wie weit der Inhalt zusätzlich abblendet.
 *
 * Zur Unschärfe ist das nur eine Nuance – zusammen wirkt die Zeile „noch nicht dran“. Auf
 * Geräten ohne Weichzeichner trägt das Abblenden den Hinweis allein und fällt deshalb
 * deutlicher aus; lesbar bleibt die Zeile in beiden Fällen.
 */
private val VEILED_ALPHA = if (CAN_BLUR) 0.82f else 0.5f

private const val SHARP = 0f
private const val VEILED = 1f

/** Kurz genug, um als Antwort auf den Haken durchzugehen, lang genug, um ihn zu quittieren. */
private const val FADE_MILLIS = 420
