package de.beispiel.meintraining.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.beispiel.meintraining.R
import de.beispiel.meintraining.data.local.RestTimer
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.ui.ExerciseForm
import de.beispiel.meintraining.ui.TrainingActions
import de.beispiel.meintraining.ui.TrainingEvent
import de.beispiel.meintraining.ui.TrainingUiState
import de.beispiel.meintraining.ui.components.ListActionButtons
import de.beispiel.meintraining.ui.components.ColumnHeaderRow
import de.beispiel.meintraining.ui.components.Confetti
import de.beispiel.meintraining.ui.components.DayTabRow
import de.beispiel.meintraining.ui.components.DraggableItem
import de.beispiel.meintraining.ui.components.ExerciseRow
import de.beispiel.meintraining.ui.components.FloatingCheckOverlay
import de.beispiel.meintraining.ui.components.draggableItem
import de.beispiel.meintraining.ui.components.floatingCheckArea
import de.beispiel.meintraining.ui.components.floatingCheckSlot
import de.beispiel.meintraining.ui.components.rememberDragDropState
import de.beispiel.meintraining.ui.components.rememberFloatingCheck
import de.beispiel.meintraining.ui.components.rememberUnconfirmedBlur
import de.beispiel.meintraining.ui.components.unconfirmedBlur
import de.beispiel.meintraining.ui.theme.AccentGreen
import de.beispiel.meintraining.ui.theme.AccentGreenSurface
import de.beispiel.meintraining.ui.theme.AppTextStyles
import de.beispiel.meintraining.ui.theme.Dimens
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme
import de.beispiel.meintraining.ui.theme.MenuButtonIcon
import de.beispiel.meintraining.ui.theme.MenuButtonSurface
import de.beispiel.meintraining.ui.theme.ScreenBackground
import de.beispiel.meintraining.ui.theme.SupersetBackground
import de.beispiel.meintraining.ui.theme.TextPrimary
import de.beispiel.meintraining.ui.settings.SettingsRoute
import de.beispiel.meintraining.ui.stats.StatsRoute
import de.beispiel.meintraining.ui.timer.RestTimerBar
import de.beispiel.meintraining.ui.timer.RestTimerRoute
import de.beispiel.meintraining.ui.tracking.TrackingRoute
import de.beispiel.meintraining.util.DEFAULT_PROGRESSION_STEP_KG
import de.beispiel.meintraining.util.deloadSets
import de.beispiel.meintraining.util.exerciseTitle
import de.beispiel.meintraining.util.toSetsRepsLabel
import de.beispiel.meintraining.util.toWeightLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Hauptscreen. Die Composable hält nur reinen UI-Zustand (Drawer, Listenreihenfolge während
 * des Ziehens); alle Daten und Aktionen kommen von außen.
 */
@Composable
fun TrainingScreen(
    uiState: TrainingUiState,
    /** Offenes Bearbeiten-Sheet; steht neben [uiState], weil es bei jedem Tastendruck wechselt. */
    editorForm: ExerciseForm?,
    events: Flow<TrainingEvent>,
    /** Eine volle Runde – der einzige Anlass, zu dem es Konfetti regnet. */
    celebrations: Flow<Unit>,
    actions: TrainingActions,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var menuDestination by rememberSaveable { mutableStateOf<MenuDestination?>(null) }

    // Gezählt statt geschaltet: Jede volle Runde ist eine neue Zahl und startet den Regen
    // zuverlässig, auch wenn der vorige noch läuft.
    var celebrationCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(celebrations) { celebrations.collect { celebrationCount++ } }

    val unit = stringResource(R.string.unit_kg)

    // „Zurück“ – Taste wie Wischgeste – beendet erst die Auswahl, dann den Menübereich.
    BackHandler(enabled = uiState.isSelectionMode) { actions.onSelectionClear() }
    BackHandler(enabled = menuDestination != null) { menuDestination = null }

    // Snackbars für Progression und Löschen, jeweils mit „Rückgängig“.
    //
    // `collectLatest` statt `collect`: Eine neue Meldung löst die vorige ab, statt sich hinter
    // ihr anzustellen. Der Pfeil ist zweimal angetippt, bevor die erste Meldung verschwunden
    // ist – angeboten gehört dann das Zurücknehmen der *zweiten* Erhöhung. Die erste ist zu
    // diesem Zeitpunkt ohnehin nicht mehr zurückzunehmen, ohne den Verlauf zu verbiegen (siehe
    // TrainingRepository.revertWeight); eine Meldung, deren Knopf nichts mehr tut, ist
    // schlimmer als gar keine. Das Abbrechen von `showSnackbar` blendet die alte gleich mit weg.
    LaunchedEffect(events) {
        events.collectLatest { event ->
            val message = when (event) {
                is TrainingEvent.WeightIncreased -> context.getString(
                    R.string.snackbar_weight_increased,
                    event.newWeightKg.toWeightLabel(unit)
                )
                is TrainingEvent.ExercisesDeleted -> if (event.exercises.size == 1) {
                    context.getString(
                        R.string.snackbar_exercise_deleted,
                        event.exercises.first().name
                    )
                } else {
                    context.resources.getQuantityString(
                        R.plurals.snackbar_exercises_deleted,
                        event.exercises.size,
                        event.exercises.size
                    )
                }
                TrainingEvent.CycleStarted -> context.getString(R.string.snackbar_cycle_started)
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.action_undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) actions.onUndo(event)
        }
    }

    Scaffold(
        containerColor = ScreenBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            // Vollständig aufgezählt statt mit `else`: Ein neuer Menüpunkt landete sonst
            // stillschweigend im Platzhalter, statt den Übersetzer zu beschäftigen.
            when (menuDestination) {
                null -> TrainingContent(
                    uiState = uiState,
                    unit = unit,
                    actions = actions,
                    onDestinationClick = { menuDestination = it }
                )
                MenuDestination.TRACKING ->
                    TrackingRoute(onBack = { menuDestination = null })
                MenuDestination.STATS ->
                    StatsRoute(onBack = { menuDestination = null })
                MenuDestination.HISTORY ->
                    HistoryRoute(onBack = { menuDestination = null })
                MenuDestination.DELOAD -> DeloadScreen(
                    status = uiState.deload,
                    onBack = { menuDestination = null }
                )
                MenuDestination.SETTINGS ->
                    SettingsRoute(onBack = { menuDestination = null })
                MenuDestination.ABOUT -> PlaceholderScreen(
                    destination = MenuDestination.ABOUT,
                    onBack = { menuDestination = null }
                )
            }

            // Über allem, damit die Schnipsel auch vor dem schwebenden Haken landen. Ausgelöst
            // wird nur auf dem Hauptbildschirm; steht es zufällig ein Menübereich offen, regnet
            // es eben dort – das ist immer noch besser als ein verschluckter Anlass.
            Confetti(burstId = celebrationCount)
        }
    }

    editorForm?.let { form ->
        ExerciseEditSheet(
            form = form,
            knownExerciseNames = uiState.knownExerciseNames,
            onFormChange = actions.onFormChange,
            onVariationToggle = actions.onVariationToggle,
            onSave = actions.onFormSave,
            onDelete = actions.onFormDelete,
            onDismiss = actions.onFormDismiss
        )
    }
}

@Composable
private fun TrainingContent(
    uiState: TrainingUiState,
    unit: String,
    actions: TrainingActions,
    onDestinationClick: (MenuDestination) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Die Pausenuhren als Platzhalter statt fest verdrahtet: Sie bringen ihr eigenes ViewModel
     * mit, das es in der Vorschau nicht gibt. So kann die Vorschau stattdessen die reine
     * Darstellung einsetzen.
     */
    restTimers: @Composable () -> Unit = { RestTimerRoute() }
) {
    // Je Tag ein eigener Listenzustand, schon beim Zusammensetzen angelegt: Der neue Tag steht
    // damit vom ersten Frame an oben. Ein nachträgliches Scrollen im Effekt käme erst einen
    // Frame später und die Liste spränge sichtbar von der alten Position nach oben.
    val listState = rememberSaveable(uiState.selectedDayId, saver = LazyListState.Saver) {
        LazyListState()
    }

    // Ohne Trainingstage kommt der Zustand noch nicht aus der Datenbank – vor der ersten
    // Antwort steht dort die Vorgabe, und die sagt „nicht abgehakt“.
    val isReady = uiState.days.isNotEmpty()

    // Solange das Training des Tages nicht eingetragen ist, liegt ein leichter Schleier über
    // den Übungen; mit dem Haken zieht die Liste scharf.
    val blur = rememberUnconfirmedBlur(
        dayId = uiState.selectedDayId,
        isConfirmed = uiState.isSelectedDayConfirmed,
        isReady = isReady
    )

    // Und solange er aussteht, steht der Haken selbst groß über den unscharfen Übungen; mit dem
    // Druck fliegt er an seinen Platz unter der Liste.
    val floatingCheck = rememberFloatingCheck(
        dayId = uiState.selectedDayId,
        isConfirmed = uiState.isSelectedDayConfirmed,
        isReady = isReady
    )
    // Einmal für alle Zeilen und einmal gemerkt: Der Verlauf steckt allein in [blur] und wird
    // erst beim Zeichnen gelesen. Die Liste wird deshalb während der ganzen Blende kein
    // einziges Mal neu zusammengesetzt.
    val rowBlur = remember(blur) { Modifier.unconfirmedBlur(blur) }

    // Aus demselben Grund gemerkt: Das letzte Listenelement bekäme sonst bei jeder
    // Recomposition eine neue Modifier-Kette, nur um dieselbe Stelle zu melden.
    val checkSlot = remember(floatingCheck) { Modifier.floatingCheckSlot(floatingCheck) }

    /**
     * Reihenfolge, die die Oberfläche selbst gesetzt hat: beim Ziehen und danach, bis die
     * Datenbank sie bestätigt. `null` heißt, es gilt unverändert, was von dort kommt.
     *
     * Nur die *Reihenfolge* wird vorgemerkt, nicht die Zeilen selbst. Eine eigene Kopie der
     * Liste müsste in einem Effekt nachgezogen werden, und Effekte laufen erst im nächsten
     * Frame – der angetippte Reiter leuchtete dann eine Bildwiederholung vor der zugehörigen
     * Übungsliste auf. So wird die Anzeige direkt beim Zusammensetzen abgeleitet und der
     * Tageswechsel geschieht in einem Zug.
     */
    var pendingOrder by remember { mutableStateOf<List<Long>?>(null) }

    val exercises = remember(uiState.exercises, pendingOrder) {
        val order = pendingOrder
        val byId = uiState.exercises.associateBy { it.id }
        // Passt die Vormerkung nicht mehr zum Bestand – anderer Tag, gelöschte oder neue
        // Zeile –, gilt sofort wieder die Datenbank.
        if (order != null && order.size == byId.size && byId.keys.containsAll(order)) {
            order.map { byId.getValue(it) }
        } else {
            uiState.exercises
        }
    }

    val dragDropState = rememberDragDropState(
        lazyListState = listState,
        itemCount = exercises.size,
        onMove = { from, to ->
            val moved = (pendingOrder ?: exercises.map { it.id }).toMutableList()
            moved.add(to, moved.removeAt(from))
            pendingOrder = moved
        },
        onMoveFinished = { pendingOrder?.let { actions.onReorder(it) } }
    )
    val isDragging = dragDropState.draggingItemIndex != null

    /**
     * Räumt die Vormerkung weg, sobald sie erledigt ist – entweder bestätigt, weil die
     * Datenbank dieselbe Reihenfolge liefert, oder überholt, weil ganz andere Zeilen kommen.
     *
     * Dieser Effekt entscheidet bewusst nicht mehr, *was* angezeigt wird; er hält nur auf,
     * dass die Vormerkung ewig stehen bleibt.
     */
    LaunchedEffect(uiState.exercises, isDragging) {
        if (isDragging) return@LaunchedEffect
        val order = pendingOrder ?: return@LaunchedEffect
        val incomingIds = uiState.exercises.map { it.id }
        if (incomingIds == order || incomingIds.toSet() != order.toSet()) pendingOrder = null
    }

    // Sortieren ohne Ziehen – für TalkBack und andere Bedienhilfen.
    val moveUpLabel = stringResource(R.string.action_move_up)
    val moveDownLabel = stringResource(R.string.action_move_down)
    val moveAndSave: (Int, Int) -> Unit = { from, to ->
        val moved = (pendingOrder ?: exercises.map { it.id }).toMutableList()
        moved.add(to, moved.removeAt(from))
        pendingOrder = moved
        actions.onReorder(moved)
    }

    // Der schwebende Haken liegt über dem ganzen Bildschirminhalt und wird an dessen Rändern
    // beschnitten: Fliegt er zu einem Platz, der weit unten in der Liste liegt, verschwindet er
    // nach unten aus dem Bild, statt über die Systemleiste zu malen.
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .floatingCheckArea(floatingCheck)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.ScreenPaddingHorizontal)
        ) {
            if (uiState.isSelectionMode) {
                SelectionBar(
                    count = uiState.selectedIds.size,
                    canCreateSuperset = uiState.canCreateSuperset,
                    canDissolveSuperset = uiState.canDissolveSuperset,
                    onClear = actions.onSelectionClear,
                    onDelete = actions.onDeleteSelected,
                    onCreateSuperset = actions.onCreateSuperset,
                    onDissolveSuperset = actions.onDissolveSuperset
                )
            } else {
                ScreenHeader(
                    title = uiState.appTitle.ifBlank { stringResource(R.string.screen_title) },
                    isDeloadWeek = uiState.deload.isDeloadWeek,
                    onDestinationClick = onDestinationClick
                )
            }

            DayTabRow(
                days = uiState.days,
                selectedDayId = uiState.selectedDayId,
                onDaySelected = actions.onDaySelected
            )

            Spacer(modifier = Modifier.height(Dimens.SectionSpacingMedium))
            restTimers()

            Spacer(modifier = Modifier.height(Dimens.SectionSpacingLarge))
            ColumnHeaderRow()
            Spacer(modifier = Modifier.height(Dimens.SectionSpacingSmall))

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(
                    items = exercises,
                    key = { _, exercise -> exercise.id }
                ) { index, exercise ->
                    val isSelected = exercise.id in uiState.selectedIds
                    DraggableItem(state = dragDropState, index = index) { itemIsDragging ->
                        // Nur die drei Kennungen statt der ganzen Liste, damit eine Zeile an drei
                        // Werten hängt und nicht an jeder Änderung irgendwo in der Liste.
                        SupersetContainer(
                            supersetId = exercise.supersetId,
                            previousSupersetId = exercises.getOrNull(index - 1)?.supersetId,
                            nextSupersetId = exercises.getOrNull(index + 1)?.supersetId
                        ) {
                            ExerciseRow(
                                name = exerciseTitle(exercise.name, exercise.variation),
                                // Nur die eingetragene Last. Bei Körpergewichtsübungen ist das die
                                // Zusatzlast – das eigene Körpergewicht gehört ins Tracking, beim
                                // Trainieren zählt, was auf die Stange kommt.
                                weightLabel = exercise.weightKg?.toWeightLabel(unit),
                                // In der Deload-Woche zeigt die Liste halbierte Sätze; der
                                // gespeicherte Plan bleibt davon unberührt.
                                setsLabel = exercise.sets
                                    .let { if (uiState.deload.isDeloadWeek) deloadSets(it) else it }
                                    .toSetsRepsLabel(
                                        repsMin = exercise.repsMin,
                                        repsMax = exercise.repsMax
                                    ),
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        actions.onSelectionToggle(exercise)
                                    } else {
                                        actions.onExerciseClick(exercise)
                                    }
                                },
                                onLongClick = { actions.onExerciseLongClick(exercise) },
                                onProgressClick = { actions.onProgressClick(exercise) },
                                modifier = Modifier.semantics {
                                    customActions = buildList {
                                        if (index > 0) {
                                            add(
                                                CustomAccessibilityAction(moveUpLabel) {
                                                    moveAndSave(index, index - 1)
                                                    true
                                                }
                                            )
                                        }
                                        if (index < exercises.lastIndex) {
                                            add(
                                                CustomAccessibilityAction(moveDownLabel) {
                                                    moveAndSave(index, index + 1)
                                                    true
                                                }
                                            )
                                        }
                                    }
                                },
                                isDragging = itemIsDragging,
                                isSelectable = uiState.isSelectionMode,
                                isSelected = isSelected,
                                dragModifier = Modifier.draggableItem(
                                    state = dragDropState,
                                    key = exercise.id,
                                    index = index
                                ),
                                contentModifier = rowBlur
                            )
                        }
                    }
                }
                // Haken und „+“ scrollen als letztes Listenelement mit.
                item {
                    ListActionButtons(
                        onToggleWorkoutCompleted = actions.onToggleWorkoutCompleted,
                        onAddExercise = actions.onAddClick,
                        isCompleted = uiState.isSelectedDayCompleted,
                        modifier = Modifier.padding(bottom = Dimens.ListBottomPadding),
                        isCheckFloating = floatingCheck.isFloating,
                        checkSlotModifier = checkSlot,
                        // Im Auswahlmodus geht es ums Markieren von Übungen; ein Sprung zwischen
                        // den Runden hätte dort nichts zu suchen.
                        showPreviousCycle = uiState.canReturnToPreviousCycleHere &&
                            !uiState.isSelectionMode,
                        onPreviousCycle = actions.onReturnToPreviousCycle,
                        showNextCycle = uiState.canStartNextCycle && !uiState.isSelectionMode,
                        isNextCycleDone = uiState.isSelectedDayConfirmed,
                        onNextCycle = actions.onStartNextCycle
                    )
                }
            }
        }

        // Im Auswahlmodus hat der Haken nichts über der Liste zu suchen: Dort geht es ums
        // Markieren, und er läge genau über den Zeilen, die angetippt werden sollen.
        if (floatingCheck.isFloating && !uiState.isSelectionMode) {
            FloatingCheckOverlay(
                state = floatingCheck,
                isCompleted = uiState.isSelectedDayCompleted,
                onClick = actions.onToggleWorkoutCompleted
            )
        }
    }
}

/**
 * Legt den grauen Kasten um zusammengehörige Superset-Übungen.
 *
 * Weil die Mitglieder immer direkt untereinander liegen, genügt es, jeder Zeile den passenden
 * Ausschnitt des Kastens mitzugeben: oben abgerundet, unten abgerundet oder gerade
 * durchlaufend. Zusammen ergibt das eine geschlossene Fläche.
 *
 * Übergeben werden nur die Kennungen der Nachbarn, nicht die Liste: So hängt die Zeile an drei
 * Werten statt an jeder Änderung irgendwo in der Liste.
 */
@Composable
private fun SupersetContainer(
    supersetId: Long?,
    previousSupersetId: Long?,
    nextSupersetId: Long?,
    content: @Composable () -> Unit
) {
    val isFirst = supersetId != null && previousSupersetId != supersetId
    val isLast = supersetId != null && nextSupersetId != supersetId

    val shape: Shape = when {
        supersetId == null -> RectangleShape
        isFirst && isLast -> Dimens.CornerSuperset
        isFirst -> Dimens.CornerSuperset.copy(
            bottomStart = ZeroCornerSize,
            bottomEnd = ZeroCornerSize
        )
        isLast -> Dimens.CornerSuperset.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)
        else -> RectangleShape
    }

    Box(
        modifier = Modifier
            // Zwischen Superset-Mitgliedern bleibt kein Spalt, sonst zerfiele der Kasten.
            .padding(bottom = if (supersetId != null && !isLast) 0.dp else Dimens.CardSpacing)
            .background(
                color = if (supersetId != null) SupersetBackground else Color.Transparent,
                shape = shape
            )
            .padding(
                start = if (supersetId != null) Dimens.SupersetInset else 0.dp,
                end = if (supersetId != null) Dimens.SupersetInset else 0.dp,
                top = if (isFirst) Dimens.SupersetInset else 0.dp,
                bottom = when {
                    supersetId == null -> 0.dp
                    isLast -> Dimens.SupersetInset
                    else -> Dimens.SupersetInnerSpacing
                }
            )
    ) {
        content()
    }
}

/** Kopfzeile im Auswahlmodus. */
@Composable
private fun SelectionBar(
    count: Int,
    canCreateSuperset: Boolean,
    canDissolveSuperset: Boolean,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onCreateSuperset: () -> Unit,
    onDissolveSuperset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.HeaderHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClear, modifier = Modifier.size(Dimens.TouchTargetSize)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cd_end_selection),
                tint = MenuButtonIcon,
                modifier = Modifier.size(Dimens.MenuIconSize)
            )
        }
        Text(
            text = pluralStringResource(R.plurals.selection_count, count, count),
            style = AppTextStyles.Title,
            color = TextPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.SectionSpacingSmall)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(Dimens.TouchTargetSize)) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MenuButtonIcon,
                modifier = Modifier.size(Dimens.MenuIconSize)
            )
        }
        Box {
            IconButton(
                onClick = { menuOpen = true },
                enabled = canCreateSuperset || canDissolveSuperset,
                modifier = Modifier.size(Dimens.TouchTargetSize)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.cd_open_menu),
                    tint = MenuButtonIcon,
                    modifier = Modifier.size(Dimens.MenuIconSize)
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (canCreateSuperset) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_superset)) },
                        onClick = {
                            menuOpen = false
                            onCreateSuperset()
                        }
                    )
                }
                if (canDissolveSuperset) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.action_superset_dissolve)) },
                        onClick = {
                            menuOpen = false
                            onDissolveSuperset()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    isDeloadWeek: Boolean,
    onDestinationClick: (MenuDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.HeaderHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Überschrift und Badge teilen sich den Platz links; der Menüknopf sitzt außerhalb
        // dieser Gruppe und bleibt dadurch immer am rechten Rand – unabhängig davon, wie
        // lang oder kurz die Überschrift ist.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = AppTextStyles.Title,
                color = TextPrimary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isDeloadWeek) {
                Text(
                    text = stringResource(R.string.deload_badge),
                    style = AppTextStyles.ColumnLabel,
                    color = AccentGreen,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = Dimens.SectionSpacingSmall)
                        .clip(Dimens.CornerChip)
                        .background(AccentGreenSurface)
                        .border(Dimens.BadgeBorderWidth, AccentGreen, Dimens.CornerChip)
                        .padding(
                            horizontal = Dimens.SectionSpacingSmall,
                            vertical = Dimens.SectionSpacingSmall / 2
                        )
                )
            }
        }
        Box {
            Box(
                modifier = Modifier
                    .size(Dimens.MenuButtonSize)
                    .clip(Dimens.CornerMenuButton)
                    .background(MenuButtonSurface)
                    .clickable(role = Role.Button) { menuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.cd_open_menu),
                    tint = MenuButtonIcon,
                    modifier = Modifier.size(Dimens.MenuIconSize)
                )
            }
            AppMenu(
                expanded = menuOpen,
                selected = null,
                onDismiss = { menuOpen = false },
                onDestinationClick = { destination ->
                    menuOpen = false
                    onDestinationClick(destination)
                }
            )
        }
    }
}

private fun previewExercise(
    id: Long,
    name: String,
    position: Int,
    variation: String? = null,
    weightKg: Double? = null,
    sets: Int? = null,
    repsMin: Int? = null,
    repsMax: Int? = null,
    supersetId: Long? = null
) = ExerciseItem(
    id = id,
    dayId = 1,
    name = name,
    variation = variation,
    sets = sets,
    repsMin = repsMin,
    repsMax = repsMax,
    position = position,
    supersetId = supersetId,
    weightKg = weightKg,
    progressionStepKg = DEFAULT_PROGRESSION_STEP_KG
)

@Preview(showBackground = true, backgroundColor = 0xFF10141A, widthDp = 360, heightDp = 640)
@Composable
private fun TrainingContentPreview() {
    MeinTrainingTheme {
        TrainingContent(
            uiState = TrainingUiState(
                days = (1..4).map { TrainingDay(id = it, name = "Tag $it") },
                selectedDayId = 1,
                exercises = listOf(
                    previewExercise(
                        id = 1, name = "Bankdrücken", position = 0,
                        weightKg = 60.0, sets = 3, repsMin = 4, repsMax = 6
                    ),
                    previewExercise(
                        id = 2, name = "Trizeps", variation = "Seil", position = 1,
                        weightKg = 20.0, sets = 3, repsMin = 8, repsMax = 12, supersetId = 1
                    ),
                    previewExercise(
                        id = 3, name = "Trizeps", variation = "Stange", position = 2,
                        weightKg = 20.0, sets = 3, repsMin = 8, repsMax = 12, supersetId = 1
                    ),
                    previewExercise(id = 4, name = "Beispiel Übung 4", position = 3)
                )
            ),
            unit = "Kg",
            actions = TrainingActions(),
            onDestinationClick = {},
            restTimers = {
                RestTimerBar(
                    timers = listOf(
                        RestTimer(durationSeconds = 90),
                        RestTimer(durationSeconds = 180)
                    ),
                    onToggle = {},
                    onReset = {},
                    onDurationChange = { _, _ -> }
                )
            }
        )
    }
}
