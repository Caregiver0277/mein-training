package de.beispiel.meintraining.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.toSize
import de.beispiel.meintraining.ui.theme.CardDraggedBackground
import de.beispiel.meintraining.ui.theme.Dimens
import kotlin.math.roundToInt

/**
 * Der Haken, solange er noch aussteht: groß in der Bildmitte statt klein am Listenende.
 *
 * Der Haken ist der Abschluss des Trainings, und übersehen wird er trotzdem – deshalb steht er
 * mitten im Bild, über den unscharfen Übungen, und lässt sich nicht mehr wegscrollen. Mit dem
 * Druck fliegt er an seinen Platz unter der Liste, wird dabei klein und macht den Weg frei.
 * Diese eine Bewegung erklärt den Zusammenhang besser als jeder Hinweistext: Es ist derselbe
 * Knopf, er sitzt nur ab jetzt dort.
 *
 * Der Zustand hält dafür drei Dinge: wie weit der Anflug ist ([dockProgress]), wo der Platz am
 * Listenende liegt ([Modifier.floatingCheckSlot]) und ob es den schwebenden Haken überhaupt
 * gerade gibt ([isFloating]). Angelegt wird er über [rememberFloatingCheck], gezeichnet über
 * [FloatingCheckOverlay].
 */
@Stable
class FloatingCheck internal constructor(
    private val dock: Animatable<Float, AnimationVector1D>,
    initialFloating: Boolean
) {

    /**
     * Schwebt der Haken gerade über der Liste?
     *
     * Bleibt auch während des Anflugs `true` – erst am Ziel übernimmt der Knopf in der Liste.
     * Der Wert wird beim Zusammensetzen gelesen und wechselt deshalb bewusst höchstens zweimal
     * je Tippen; alles, was sich mit jedem Bild ändert, steckt in [dockProgress].
     */
    var isFloating: Boolean by mutableStateOf(initialFloating)
        internal set

    /** Der Platz am Listenende in Bildschirmkoordinaten; `null`, solange er nie gemessen wurde. */
    internal var slot: Rect? by mutableStateOf(null)

    /** Ursprung der Fläche, über der geschwebt wird – ebenfalls in Bildschirmkoordinaten. */
    internal var areaOrigin: Offset? by mutableStateOf(null)

    /**
     * 0 heißt: in der Bildmitte, 1: am Platz in der Liste.
     *
     * Bewusst eine Funktion und keine Eigenschaft: So fällt beim Lesen auf, dass sie erst im
     * Messen aufgerufen gehört – siehe [FloatingCheckOverlay].
     */
    internal fun dockProgress(): Float = dock.value

    /**
     * Wo der Haken in diesem Bild steht, gemessen in der Fläche, über der er schwebt.
     *
     * Der Platz in der Liste wird nur gelesen, wenn der Anflug schon läuft. Sonst hinge das
     * Messen des schwebenden Hakens am Scrollen der Liste – der Platz wandert dabei mit jedem
     * Bild –, obwohl der Haken ruhig in der Mitte steht.
     */
    internal fun bounds(constraints: Constraints, density: Density): Rect {
        val area = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val floating = floatingBounds(area, density)
        val progress = dockProgress()
        if (progress <= FLOATING) return floating
        return lerp(floating, dockedBounds(area, density), progress)
    }

    private fun floatingBounds(area: Size, density: Density): Rect {
        val width = area.width * FLOATING_WIDTH_FRACTION
        val height = with(density) { Dimens.FloatingCheckHeight.toPx() }
        return Rect(
            offset = Offset((area.width - width) / 2f, (area.height - height) / 2f),
            size = Size(width, height)
        )
    }

    private fun dockedBounds(area: Size, density: Density): Rect {
        val slot = slot
        val origin = areaOrigin
        if (slot == null || origin == null) return fallbackBounds(area, density)
        return slot.translate(-origin.x, -origin.y)
    }

    /**
     * Das Ziel, wenn der Platz in der Liste nicht bekannt ist – weil so weit heruntergescrollt
     * wurde, dass es ihn im Moment gar nicht gibt.
     *
     * Angeflogen wird dann der untere Bildrand: genau die Stelle, an der die Liste weitergeht.
     * Der Haken verschwindet damit nach unten aus dem Bild, statt an Ort und Stelle wegzublinken.
     */
    private fun fallbackBounds(area: Size, density: Density): Rect = with(density) {
        val height = Dimens.AddButtonHeight.toPx()
        val left = Dimens.ScreenPaddingHorizontal.toPx()
        val right = area.width - left - Dimens.AddButtonWidth.toPx() - Dimens.CardSpacing.toPx()
        val top = area.height - Dimens.ListBottomPadding.toPx() - height
        Rect(left = left, top = top, right = right.coerceAtLeast(left), bottom = top + height)
    }
}

/**
 * Legt den schwebenden Haken für den angezeigten Tag an.
 *
 * Geflogen wird nur der eine Übergang, um den es geht: das Abhaken. Ein Tageswechsel schaltet
 * dagegen sofort um – aus demselben Grund wie beim Schleier über den Übungen (siehe
 * [rememberUnconfirmedBlur]): Der Verlauf hängt am Tag, zum neuen Tag gehört ein neuer, und der
 * beginnt schon beim Zusammensetzen am Ziel.
 *
 * @param isConfirmed ob das Training dieses Tages als eingetragen gilt.
 * @param isReady ob [isConfirmed] überhaupt schon aus der Datenbank stammt. Vor deren erster
 *   Antwort gilt dort die Vorgabe „nicht abgehakt“ – der Haken erschiene beim Öffnen groß in
 *   der Mitte und flöge sofort wieder weg.
 */
@Composable
fun rememberFloatingCheck(
    dayId: Int,
    isConfirmed: Boolean,
    isReady: Boolean
): FloatingCheck {
    val dock = remember(dayId, isReady) {
        Animatable(if (isConfirmed) DOCKED else FLOATING)
    }
    val state = remember(dock) {
        FloatingCheck(dock = dock, initialFloating = isReady && !isConfirmed)
    }

    LaunchedEffect(state, isConfirmed, isReady) {
        if (!isReady) {
            state.isFloating = false
            return@LaunchedEffect
        }
        val target = if (isConfirmed) DOCKED else FLOATING
        // Am Ziel gibt es nichts zu fliegen: Nach einem Tageswechsel steht der frische Verlauf
        // schon dort, und eine Animation darauf rechnete eine halbe Sekunde lang denselben Wert
        // aus und forderte dafür jedes Bild an.
        if (dock.value != target) {
            // Bis zur Landung bleibt der schwebende Haken zuständig; erst danach übernimmt der
            // Knopf in der Liste. Ohne dieses Nachlaufen verschwände er mitten im Flug.
            state.isFloating = true
            dock.animateTo(
                targetValue = target,
                animationSpec = tween(DOCK_MILLIS, easing = FastOutSlowInEasing)
            )
        }
        state.isFloating = target == FLOATING
    }

    return state
}

/**
 * Merkt sich den Platz des Hakens am Listenende – gehört an genau die Stelle, an der er
 * angedockt steht.
 */
fun Modifier.floatingCheckSlot(state: FloatingCheck): Modifier = onGloballyPositioned { coords ->
    state.slot = Rect(coords.positionInRoot(), coords.size.toSize())
}

/**
 * Steckt die Fläche ab, über der der Haken schwebt – üblicherweise der ganze Bildschirminhalt.
 * Der Ursprung wird gebraucht, um den Platz am Listenende in dieselbe Rechnung zu bekommen.
 */
fun Modifier.floatingCheckArea(state: FloatingCheck): Modifier = onGloballyPositioned { coords ->
    state.areaOrigin = coords.positionInRoot()
}

/**
 * Zeichnet den schwebenden Haken.
 *
 * Gehört als letztes Kind in die Fläche mit [Modifier.floatingCheckArea], damit er über der
 * Liste liegt. Größe und Ort entstehen beim Messen aus [FloatingCheck.bounds]: Der Anflug
 * ändert damit kein einziges Mal die Zusammensetzung, sondern nur die Maße eines einzigen
 * Elements.
 */
@Composable
fun FloatingCheckOverlay(
    state: FloatingCheck,
    isCompleted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CompleteWorkoutButton(
        onClick = onClick,
        isCompleted = isCompleted,
        iconScale = {
            // In der Mitte ist der Haken groß, am Listenende wieder so groß wie eh und je.
            FLOATING_ICON_SCALE + (1f - FLOATING_ICON_SCALE) * state.dockProgress()
        },
        modifier = modifier
            .layout { measurable, constraints ->
                // Ohne begrenzte Fläche gibt es keine Mitte, in die der Haken gehört. Das kommt
                // in der App nicht vor; in einer Vorschau mit unbegrenzter Höhe aber schon.
                if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
                    val empty = measurable.measure(Constraints.fixed(0, 0))
                    return@layout layout(0, 0) { empty.place(0, 0) }
                }
                val bounds = state.bounds(constraints, this)
                val placeable = measurable.measure(
                    Constraints.fixed(
                        width = bounds.width.roundToInt().coerceAtLeast(0),
                        height = bounds.height.roundToInt().coerceAtLeast(0)
                    )
                )
                // Die Ebene spannt die ganze Fläche auf, angetippt wird nur der Knopf darin.
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(bounds.left.roundToInt(), bounds.top.roundToInt())
                }
            }
            // Hinter dem Knopf, und deshalb *nach* dem Messen: So gilt hier seine eigene Größe
            // und nicht die ganze aufgespannte Fläche.
            //
            // Eine angehobene Fläche, solange er schwebt: In der Bildmitte stünde er sonst als
            // dünner Umriss über den Übungskarten und sähe aus wie ein Platzhalter. Sie blendet
            // mit dem Anflug weg, damit am Listenende wieder der schlichte Umriss steht – die
            // gleiche Erscheinung wie beim „+“ daneben.
            .drawBehind {
                val fade = 1f - state.dockProgress()
                if (fade <= 0f) return@drawBehind
                drawRoundRect(
                    color = CardDraggedBackground,
                    alpha = fade,
                    cornerRadius = CornerRadius(Dimens.CornerAddButton.topStart.toPx(size, this))
                )
            }
    )
}

private const val FLOATING = 0f
private const val DOCKED = 1f

/** Breite des schwebenden Hakens als Anteil der Fläche – so bleibt links und rechts Liste sichtbar. */
private const val FLOATING_WIDTH_FRACTION = 0.56f

/** Wie viel größer der Haken in der Bildmitte ausfällt. */
private const val FLOATING_ICON_SCALE = 2.1f

/**
 * Lang genug, um dem Auge zu folgen, und knapp länger als der Funkenflug beim Druck – der wird
 * vom schwebenden Haken gezeichnet und würde beim Landen sonst mitten im Ausklingen abgeschnitten.
 */
private const val DOCK_MILLIS = 640
