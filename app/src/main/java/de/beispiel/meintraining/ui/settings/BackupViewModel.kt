package de.beispiel.meintraining.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.beispiel.meintraining.MeinTrainingApp
import de.beispiel.meintraining.data.backup.BackupFormatException
import de.beispiel.meintraining.data.backup.BackupProblem
import de.beispiel.meintraining.data.backup.BackupWorker
import de.beispiel.meintraining.data.backup.describe
import de.beispiel.meintraining.data.backup.DEFAULT_BACKUP_INTERVAL_DAYS
import de.beispiel.meintraining.data.backup.MAX_BACKUP_INTERVAL_DAYS
import de.beispiel.meintraining.data.backup.MIN_BACKUP_INTERVAL_DAYS
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Rückmeldung nach einer Sicherung oder einem Import; verschwindet nach dem Anzeigen. */
sealed interface BackupMessage {
    data object Exported : BackupMessage
    data object Imported : BackupMessage
    data class Failed(val reason: String) : BackupMessage
}

data class BackupUiState(
    val autoBackupEnabled: Boolean = false,
    val intervalDays: Int = DEFAULT_BACKUP_INTERVAL_DAYS,
    /** Anzeigename der Sicherungsdatei; `null`, solange keine gewählt ist. */
    val targetName: String? = null,
    val lastBackupAt: Long? = null,
    val lastBackupError: String? = null,
    val busy: Boolean = false
) {
    /** Ohne Ziel läuft nichts automatisch – der Schalter bleibt dann wirkungslos. */
    val canEnableAutoBackup: Boolean get() = targetName != null
}

class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<MeinTrainingApp>()
    private val settings get() = app.settingsStore
    private val backups get() = app.backupRepository

    private val busy = MutableStateFlow(false)

    private val messages = MutableStateFlow<BackupMessage?>(null)
    val message: StateFlow<BackupMessage?> = messages.asStateFlow()

    val uiState = combine(
        settings.backupEnabled,
        settings.backupIntervalDays,
        settings.backupTargetUri,
        combine(settings.lastBackupAt, settings.lastBackupError) { at, error -> at to error },
        busy
    ) { enabled, interval, target, (lastAt, lastError), isBusy ->
        BackupUiState(
            autoBackupEnabled = enabled,
            intervalDays = interval,
            targetName = target?.let(::displayNameFor),
            lastBackupAt = lastAt,
            lastBackupError = lastError,
            busy = isBusy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = BackupUiState()
    )

    /** Einmalige Sicherung in eine frisch gewählte Datei. */
    fun onExportChosen(uri: Uri) {
        runBackupAction {
            backups.writeTo(uri, backups.createBackup())
            BackupMessage.Exported
        }
    }

    /**
     * Legt die Datei fest, in die automatisch gesichert wird, und schreibt gleich die erste
     * Sicherung hinein – so zeigt sich sofort, ob der Zugriff überhaupt funktioniert, statt
     * erst in einer Woche.
     */
    fun onBackupTargetChosen(uri: Uri) {
        runBackupAction {
            val previous = settings.backupTargetUri.first()
            backups.persistAccess(uri)
            backups.writeTo(uri, backups.createBackup())
            settings.setBackupTargetUri(uri.toString())
            settings.setLastBackupResult(System.currentTimeMillis(), null)
            // Erst wenn das neue Ziel wirklich steht, den Zugriff auf das alte zurückgeben.
            previous?.takeIf { it != uri.toString() }?.let { backups.releaseAccess(Uri.parse(it)) }
            BackupMessage.Exported
        }
    }

    fun onImportChosen(uri: Uri) {
        runBackupAction {
            backups.restore(backups.decode(backups.readFrom(uri)))
            BackupMessage.Imported
        }
    }

    fun onAutoBackupToggled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setBackupEnabled(enabled)
            if (enabled) {
                BackupWorker.schedule(app, settings.backupIntervalDays.first())
            } else {
                BackupWorker.cancel(app)
            }
        }
    }

    fun onIntervalChanged(input: String) {
        val days = input.trim().toIntOrNull() ?: return
        if (days !in MIN_BACKUP_INTERVAL_DAYS..MAX_BACKUP_INTERVAL_DAYS) return
        viewModelScope.launch {
            settings.setBackupIntervalDays(days)
            if (settings.backupEnabled.first()) BackupWorker.schedule(app, days)
        }
    }

    fun onMessageShown() {
        messages.value = null
    }

    /**
     * Führt eine Sicherungsaktion aus und meldet Erfolg oder Grund des Scheiterns.
     *
     * Nicht `run` genannt, obwohl es kürzer wäre: An der Aufrufstelle sähe das nach der
     * gleichnamigen Funktion aus der Standardbibliothek aus, die sofort und im selben Faden
     * abliefe. Hier startet stattdessen eine Nebenläufigkeit, die Fehler in eine Meldung
     * verwandelt – ein Unterschied, den der Name nennen muss.
     */
    private fun runBackupAction(block: suspend () -> BackupMessage) {
        if (!busy.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            messages.value = try {
                block()
            } catch (throwable: Exception) {
                BackupMessage.Failed(reasonFor(throwable))
            }
            busy.value = false
        }
    }

    /**
     * Der anzeigbare Grund eines Fehlschlags.
     *
     * Was die Sicherung selbst ablehnt, kommt als [BackupProblem] und wird hier in einen Satz
     * gesetzt. Alles andere – wegfallende Berechtigung, volle Platte, abgezogener Speicher –
     * bringt nur mit, was das System dazu sagt; ein eigener Text dafür wäre geraten und
     * verdeckte den einzigen Hinweis, den es gibt.
     */
    private fun reasonFor(throwable: Exception): String = when (throwable) {
        is BackupFormatException -> app.describe(throwable.problem)
        else -> throwable.message ?: throwable.javaClass.simpleName
    }

    private fun displayNameFor(uri: String): String =
        Uri.parse(uri).lastPathSegment?.substringAfterLast('/') ?: uri

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MeinTrainingApp
                BackupViewModel(app)
            }
        }
    }
}
