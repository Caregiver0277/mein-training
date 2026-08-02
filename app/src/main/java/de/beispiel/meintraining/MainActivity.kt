package de.beispiel.meintraining

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.beispiel.meintraining.ui.TrainingViewModel
import de.beispiel.meintraining.ui.screen.TrainingScreen
import de.beispiel.meintraining.ui.theme.MeinTrainingTheme

/** Single Activity – die gesamte Oberfläche ist Compose. */
class MainActivity : ComponentActivity() {

    private val viewModel: TrainingViewModel by viewModels { TrainingViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Systemleisten transparent und dauerhaft im dunklen Stil
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        setContent {
            MeinTrainingTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                // Über Nacht offen gebliebene App: Beim Zurückkehren kann ein neuer Tag
                // angebrochen sein.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

                TrainingScreen(
                    uiState = uiState,
                    events = viewModel.events,
                    onDaySelected = viewModel::onDaySelected,
                    onAddClick = viewModel::onAddClick,
                    onCompleteWorkout = viewModel::onCompleteWorkout,
                    onDeleteSession = viewModel::onDeleteSession,
                    onExerciseClick = viewModel::onExerciseClick,
                    onExerciseLongClick = viewModel::onExerciseLongClick,
                    onSelectionToggle = viewModel::onSelectionToggle,
                    onSelectionClear = viewModel::onSelectionClear,
                    onDeleteSelected = viewModel::onDeleteSelected,
                    onCreateSuperset = viewModel::onCreateSuperset,
                    onDissolveSuperset = viewModel::onDissolveSuperset,
                    onProgressClick = viewModel::onProgressClick,
                    onReorder = viewModel::onReorder,
                    onFormChange = viewModel::onFormChange,
                    onVariationToggle = viewModel::onVariationToggle,
                    onBodyweightToggle = viewModel::onBodyweightToggle,
                    onFormSave = viewModel::onFormSave,
                    onFormDelete = viewModel::onFormDelete,
                    onFormDismiss = viewModel::onFormDismiss,
                    onUndo = viewModel::onUndo
                )
            }
        }
    }
}
