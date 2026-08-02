package de.beispiel.meintraining.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Zustand für das Umsortieren einer [LazyColumn][androidx.compose.foundation.lazy.LazyColumn]
 * per Ziehen.
 *
 * [itemCount] ist die Anzahl der ziehbaren Einträge; alles danach (z. B. ein „+“-Button als
 * letztes Listenelement) bleibt an Ort und Stelle und kommt nicht als Ziel in Frage.
 * [onMove] wird während des Ziehens bei jedem Positionswechsel aufgerufen und muss die
 * angezeigte Liste sofort umsortieren, [onMoveFinished] genau einmal beim Loslassen.
 */
@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    itemCount: Int,
    onMove: (from: Int, to: Int) -> Unit,
    onMoveFinished: () -> Unit
): DragDropState {
    val scope = rememberCoroutineScope()
    val state = remember(lazyListState, scope) { DragDropState(lazyListState, scope) }

    // Die Callbacks kommen bei jeder Recomposition neu; der Zustand selbst bleibt bestehen.
    SideEffect {
        state.itemCount = itemCount
        state.onMove = onMove
        state.onMoveFinished = onMoveFinished
    }

    // Zieht man an den Rand der Liste, scrollt sie automatisch weiter.
    LaunchedEffect(state) {
        while (true) {
            val distance = state.scrollChannel.receive()
            lazyListState.scrollBy(distance)
        }
    }
    return state
}

class DragDropState internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope
) {

    /** Index des gerade gezogenen Eintrags oder `null`, wenn nicht gezogen wird. */
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set

    internal var itemCount: Int = 0
    internal var onMove: (Int, Int) -> Unit = { _, _ -> }
    internal var onMoveFinished: () -> Unit = {}

    internal val scrollChannel = Channel<Float>()

    private var draggedDistance by mutableFloatStateOf(0f)
    private var initialOffset by mutableIntStateOf(0)
    private var hasMoved = false

    /**
     * Eine Verschiebung, die erst nach einer Scrollkorrektur greifen kann. Solange sie läuft,
     * ist die angezeigte Liste noch nicht umsortiert – dann darf keine weitere Zielsuche
     * stattfinden, sonst würde gegen einen veralteten Stand gerechnet.
     */
    private var pendingMove: Job? = null

    private val isMoving: Boolean get() = pendingMove?.isActive == true

    /**
     * Verschiebung des gezogenen Eintrags gegenüber seiner aktuellen Layoutposition.
     * Wandert er in der Liste eine Stelle weiter, ändert sich seine Layoutposition –
     * die Differenz gleicht das hier automatisch wieder aus.
     */
    internal val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            initialOffset + draggedDistance - item.offset
        } ?: 0f

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggingItemIndex }

    internal fun onDragStart(index: Int) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        draggingItemIndex = index
        initialOffset = item.offset
        draggedDistance = 0f
        hasMoved = false
    }

    internal fun onDrag(deltaY: Float) {
        if (draggingItemIndex == null) return
        draggedDistance += deltaY
        if (isMoving) return

        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset + draggingItemOffset
        val endOffset = startOffset + draggingItem.size
        val middleOffset = startOffset + draggingItem.size / 2f

        // Ziel ist der Eintrag, über dessen Fläche die Mitte des gezogenen Eintrags liegt.
        val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != draggingItem.index &&
                item.index < itemCount &&
                middleOffset.toInt() in item.offset..(item.offset + item.size)
        }

        if (targetItem != null) {
            moveTo(draggingItem.index, targetItem.index)
        } else {
            autoScroll(startOffset, endOffset)
        }
    }

    /** Beendet das Ziehen; gespeichert wird nur, wenn sich die Reihenfolge geändert hat. */
    internal fun onDragEnd() {
        val changed = hasMoved
        val pending = pendingMove
        draggingItemIndex = null
        draggedDistance = 0f
        initialOffset = 0
        hasMoved = false
        if (!changed) return
        if (pending == null || pending.isCompleted) {
            onMoveFinished()
        } else {
            // Die endgültige Reihenfolge steht erst fest, wenn die letzte Verschiebung durch ist.
            scope.launch {
                pending.join()
                onMoveFinished()
            }
        }
    }

    private fun moveTo(from: Int, to: Int) {
        val firstVisible = listState.firstVisibleItemIndex
        // Ist der oberste sichtbare Eintrag beteiligt, verschiebt sich beim Umsortieren der
        // Scrollanker. Vorher zurückscrollen verhindert, dass die Liste sichtbar springt.
        val scrollToIndex = when (firstVisible) {
            to -> from
            from -> to
            else -> null
        }
        draggingItemIndex = to
        hasMoved = true
        if (scrollToIndex == null) {
            onMove(from, to)
            return
        }
        // Erst scrollen, dann umsortieren – bis dahin sperrt [isMoving] weitere Zielsuchen.
        pendingMove = scope.launch {
            listState.scrollToItem(scrollToIndex, listState.firstVisibleItemScrollOffset)
            onMove(from, to)
        }
    }

    private fun autoScroll(startOffset: Float, endOffset: Float) {
        val distance = when {
            draggedDistance > 0 ->
                (endOffset - listState.layoutInfo.viewportEndOffset).coerceAtLeast(0f)
            draggedDistance < 0 ->
                (startOffset - listState.layoutInfo.viewportStartOffset).coerceAtMost(0f)
            else -> 0f
        }
        if (distance != 0f) scrollChannel.trySend(distance)
    }
}

/**
 * Macht einen markierten Eintrag verschiebbar: Ziehen startet sofort, Tippen bleibt dem
 * Umschalten der Markierung. Eine Vibration meldet den Beginn der Bewegung.
 *
 * Bewusst nur für markierte Zeilen gedacht – läge die Geste auf jeder Zeile, ließe sich die
 * Liste nicht mehr scrollen.
 *
 * [key] muss den Eintrag stabil identifizieren (dieselbe Kennung wie in der Liste), denn der
 * [index] wechselt mitten in der Geste, sobald die Zeile ihren Platz tauscht. Als Schlüssel für
 * `pointerInput` würde er den Gestenerkenner neu aufbauen und das Ziehen dabei abbrechen.
 */
@Composable
fun Modifier.draggableItem(state: DragDropState, key: Any, index: Int): Modifier {
    val currentIndex = rememberUpdatedState(index)
    val haptics = LocalHapticFeedback.current
    return pointerInput(state, key) {
        detectDragGestures(
            onDragStart = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.onDragStart(currentIndex.value)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragEnd() }
        )
    }
}

/**
 * Hülle für einen Listeneintrag: der gezogene Eintrag folgt dem Finger und liegt über den
 * anderen, alle übrigen wechseln ihre Plätze animiert.
 */
@Composable
fun LazyItemScope.DraggableItem(
    state: DragDropState,
    index: Int,
    content: @Composable (isDragging: Boolean) -> Unit
) {
    val isDragging = index == state.draggingItemIndex
    val modifier = if (isDragging) {
        Modifier
            .zIndex(1f)
            .graphicsLayer { translationY = state.draggingItemOffset }
    } else {
        Modifier.animateItem()
    }
    Box(modifier = modifier) { content(isDragging) }
}
