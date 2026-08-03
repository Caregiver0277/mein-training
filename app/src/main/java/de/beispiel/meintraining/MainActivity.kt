package de.beispiel.meintraining

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.beispiel.meintraining.ui.TrainingActions
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
                // Getrennt eingesammelt: Das Formular ändert sich bei jedem Tastendruck und
                // soll damit nicht den ganzen Hauptscreen neu zusammensetzen.
                val editorForm by viewModel.editorForm.collectAsStateWithLifecycle()

                // Über Nacht offen gebliebene App: Beim Zurückkehren kann ein neuer Tag
                // angebrochen sein.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumed() }

                // Einmal gebündelt: Das ViewModel wechselt nicht, also bleibt auch das Bündel
                // stehen und der Screen muss nicht bei jeder Recomposition neue Lambdas prüfen.
                val actions = remember(viewModel) {
                    TrainingActions(
                        onDaySelected = viewModel::onDaySelected,
                        onAddClick = viewModel::onAddClick,
                        onCompleteWorkout = viewModel::onCompleteWorkout,
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
                        onFormSave = viewModel::onFormSave,
                        onFormDelete = viewModel::onFormDelete,
                        onFormDismiss = viewModel::onFormDismiss,
                        onUndo = viewModel::onUndo
                    )
                }

                TrainingScreen(
                    uiState = uiState,
                    editorForm = editorForm,
                    events = viewModel.events,
                    actions = actions
                )
            }
        }
    }
}
