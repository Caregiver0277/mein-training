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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import de.beispiel.meintraining.data.model.ExerciseItem
import de.beispiel.meintraining.data.model.TrainingDay
import de.beispiel.meintraining.ui.ExerciseForm
import de.beispiel.meintraining.ui.TrainingEvent
import de.beispiel.meintraining.ui.TrainingUiState
import de.beispiel.meintraining.ui.components.ListActionButtons
import de.beispiel.meintraining.ui.components.ColumnHeaderRow
import de.beispiel.meintraining.ui.components.DayTabRow
import de.beispiel.meintraining.ui.components.DraggableItem
import de.beispiel.meintraining.ui.components.ExerciseRow
import de.beispiel.meintraining.ui.components.draggableItem
import de.beispiel.meintraining.ui.components.rememberDragDropState
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
import de.beispiel.meintraining.ui.tracking.TrackingRoute
import de.beispiel.meintraining.util.DEFAULT_PROGRESSION_STEP_KG
import de.beispiel.meintraining.util.deloadSets
import de.beispiel.meintraining.util.exerciseTitle
import de.beispiel.meintraining.util.toSetsRepsLabel
import de.beispiel.meintraining.util.toWeightLabel
import kotlinx.coroutines.flow.Flow

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
    onDaySelected: (Int) -> Unit,
    onAddClick: () -> Unit,
    onCompleteWorkout: () -> Unit,
    onDeleteSession: (Long) -> Unit,
    onExerciseClick: (ExerciseItem) -> Unit,
    onExerciseLongClick: (ExerciseItem) -> Unit,
    onSelectionToggle: (ExerciseItem) -> Unit,
    onSelectionClear: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCreateSuperset: () -> Unit,
    onDissolveSuperset: () -> Unit,
    onProgressClick: (ExerciseItem) -> Unit,
    onReorder: (List<Long>) -> Unit,
    onFormChange: (ExerciseForm) -> Unit,
    onVariationToggle: () -> Unit,
    onFormSave: () -> Unit,
    onFormDelete: () -> Unit,
    onFormDismiss: () -> Unit,
    onUndo: (TrainingEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var menuDestination by rememberSaveable { mutableStateOf<MenuDestination?>(null) }

    val unit = stringResource(R.string.unit_kg)

    // „Zurück“ – Taste wie Wischgeste – beendet erst die Auswahl, dann den Menübereich.
    BackHandler(enabled = uiState.isSelectionMode) { onSelectionClear() }
    BackHandler(enabled = menuDestination != null) { menuDestination = null }

    // Snackbars für Progression und Löschen, jeweils mit „Rückgängig“.
    LaunchedEffect(events) {
        events.collect { event ->
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
                is TrainingEvent.WorkoutCompleted ->
                    context.getString(R.string.snackbar_workout_completed)
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.action_undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onUndo(event)
        }
    }

    Scaffold(
        containerColor = ScreenBackground,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (menuDestination) {
                null -> TrainingContent(
                    uiState = uiState,
                    unit = unit,
                    onDestinationClick = { menuDestination = it },
                    onDaySelected = onDaySelected,
                    onAddClick = onAddClick,
                    onCompleteWorkout = onCompleteWorkout,
                    onExerciseClick = onExerciseClick,
                    onExerciseLongClick = onExerciseLongClick,
                    onSelectionToggle = onSelectionToggle,
                    onSelectionClear = onSelectionClear,
                    onDeleteSelected = onDeleteSelected,
                    onCreateSuperset = onCreateSuperset,
                    onDissolveSuperset = onDissolveSuperset,
                    onProgressClick = onProgressClick,
                    onReorder = onReorder
                )
                MenuDestination.TRACKING ->
                    TrackingRoute(onBack = { menuDestination = null })
                MenuDestination.STATS ->
                    StatsRoute(onBack = { menuDestination = null })
                MenuDestination.HISTORY -> HistoryScreen(
                    sessions = uiState.sessions,
                    days = uiState.days,
                    today = uiState.today,
                    onDeleteSession = onDeleteSession,
                    onBack = { menuDestination = null }
                )
                MenuDestination.DELOAD -> DeloadScreen(
                    status = uiState.deload,
                    onBack = { menuDestination = null }
                )
                MenuDestination.SETTINGS ->
                    SettingsRoute(onBack = { menuDestination = null })
                else -> PlaceholderScreen(
                    destination = menuDestination!!,
                    onBack = { menuDestination = null }
                )
            }
        }
    }

    editorForm?.let { form ->
        ExerciseEditSheet(
            form = form,
            knownExerciseNames = uiState.knownExerciseNames,
            onFormChange = onFormChange,
            onVariationToggle = onVariationToggle,
            onSave = onFormSave,
            onDelete = onFormDelete,
            onDismiss = onFormDismiss
        )
    }
}

@Composable
private fun TrainingContent(
    uiState: TrainingUiState,
    unit: String,
    onDestinationClick: (MenuDestination) -> Unit,
    onDaySelected: (Int) -> Unit,
    onAddClick: () -> Unit,
    onCompleteWorkout: () -> Unit,
    onExerciseClick: (ExerciseItem) -> Unit,
    onExerciseLongClick: (ExerciseItem) -> Unit,
    onSelectionToggle: (ExerciseItem) -> Unit,
    onSelectionClear: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCreateSuperset: () -> Unit,
    onDissolveSuperset: () -> Unit,
    onProgressClick: (ExerciseItem) -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Eigene Kopie der Liste: Sie wird beim Ziehen sofort umsortiert, ohne auf die
    // Datenbank zu warten – nur so folgen die Karten dem Finger ruckelfrei.
    val visibleExercises = remember { mutableStateListOf<ExerciseItem>() }

    val dragDropState = rememberDragDropState(
        lazyListState = listState,
        itemCount = visibleExercises.size,
        onMove = { from, to -> visibleExercises.add(to, visibleExercises.removeAt(from)) },
        onMoveFinished = { onReorder(visibleExercises.map { it.id }) }
    )
    val isDragging = dragDropState.draggingItemIndex != null

    LaunchedEffect(uiState.exercises, isDragging) {
        if (isDragging) return@LaunchedEffect
        val incoming = uiState.exercises
        if (incoming.map { it.id }.toSet() == visibleExercises.map { it.id }.toSet()) {
            // Dieselben Übungen: Nach dem Ablegen liefert Room die neue Reihenfolge erst
            // verzögert nach. Die lokale Sortierung bleibt deshalb stehen (sonst springt die
            // Liste kurz zurück), nur die Inhalte werden aktualisiert.
            val byId = incoming.associateBy { it.id }
            visibleExercises.indices.forEach { index ->
                val updated = byId.getValue(visibleExercises[index].id)
                if (updated != visibleExercises[index]) visibleExercises[index] = updated
            }
        } else {
            // Anderer Tag, neue oder gelöschte Übung: komplett übernehmen.
            visibleExercises.clear()
            visibleExercises.addAll(incoming)
        }
    }

    // Sortieren ohne Ziehen – für TalkBack und andere Bedienhilfen.
    val moveUpLabel = stringResource(R.string.action_move_up)
    val moveDownLabel = stringResource(R.string.action_move_down)
    val moveAndSave: (Int, Int) -> Unit = { from, to ->
        visibleExercises.add(to, visibleExercises.removeAt(from))
        onReorder(visibleExercises.map { it.id })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingHorizontal)
    ) {
        if (uiState.isSelectionMode) {
            SelectionBar(
                count = uiState.selectedIds.size,
                canCreateSuperset = uiState.canCreateSuperset,
                canDissolveSuperset = uiState.canDissolveSuperset,
                onClear = onSelectionClear,
                onDelete = onDeleteSelected,
                onCreateSuperset = onCreateSuperset,
                onDissolveSuperset = onDissolveSuperset
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
            onDaySelected = onDaySelected
        )

        Spacer(modifier = Modifier.height(Dimens.SectionSpacingLarge))
        ColumnHeaderRow()
        Spacer(modifier = Modifier.height(Dimens.SectionSpacingSmall))

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(
                items = visibleExercises,
                key = { _, exercise -> exercise.id }
            ) { index, exercise ->
                val isSelected = exercise.id in uiState.selectedIds
                DraggableItem(state = dragDropState, index = index) { itemIsDragging ->
                    SupersetContainer(
                        exercises = visibleExercises,
                        index = index
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
                                    onSelectionToggle(exercise)
                                } else {
                                    onExerciseClick(exercise)
                                }
                            },
                            onLongClick = { onExerciseLongClick(exercise) },
                            onProgressClick = { onProgressClick(exercise) },
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
                                    if (index < visibleExercises.lastIndex) {
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
                            )
                        )
                    }
                }
            }
            // Haken und „+“ scrollen als letztes Listenelement mit.
            item {
                ListActionButtons(
                    onCompleteWorkout = onCompleteWorkout,
                    onAddExercise = onAddClick,
                    isCompleted = uiState.isSelectedDayCompleted,
                    modifier = Modifier.padding(bottom = Dimens.ListBottomPadding)
                )
            }
        }
    }
}

/**
 * Legt den grauen Kasten um zusammengehörige Superset-Übungen.
 *
 * Weil die Mitglieder immer direkt untereinander liegen, genügt es, jeder Zeile den passenden
 * Ausschnitt des Kastens mitzugeben: oben abgerundet, unten abgerundet oder gerade
 * durchlaufend. Zusammen ergibt das eine geschlossene Fläche.
 */
@Composable
private fun SupersetContainer(
    exercises: List<ExerciseItem>,
    index: Int,
    content: @Composable () -> Unit
) {
    val supersetId = exercises[index].supersetId
    val isFirst = supersetId != null && exercises.getOrNull(index - 1)?.supersetId != supersetId
    val isLast = supersetId != null && exercises.getOrNull(index + 1)?.supersetId != supersetId

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
            onDestinationClick = {},
            onDaySelected = {},
            onAddClick = {},
            onCompleteWorkout = {},
            onExerciseClick = {},
            onExerciseLongClick = {},
            onSelectionToggle = {},
            onSelectionClear = {},
            onDeleteSelected = {},
            onCreateSuperset = {},
            onDissolveSuperset = {},
            onProgressClick = {},
            onReorder = {}
        )
    }
}
