package de.beispiel.meintraining.data.backup

import android.content.Context
import de.beispiel.meintraining.R

/**
 * Setzt ein [BackupProblem] in den Satz um, der dem Nutzer angezeigt wird.
 *
 * Die einzige Stelle, an der beides zusammenkommt. Sie steht neben dem Grund und nicht bei der
 * Oberfläche, weil nicht nur die ihn braucht: Die automatische Sicherung läuft ohne Bildschirm
 * und legt ihren Fehlschlag als fertigen Satz ab (siehe [BackupWorker]).
 *
 * Bewusst keine Composable – ein Toast entsteht außerhalb der Composition, und [BackupCodec]
 * bleibt davon unberührt: Der kennt weiterhin nur [BackupProblem] und lässt sich ohne Gerät
 * prüfen.
 */
fun Context.describe(problem: BackupProblem): String = when (problem) {
    BackupProblem.NotReadable -> getString(R.string.backup_error_not_readable)
    BackupProblem.NotWritable -> getString(R.string.backup_error_not_writable)
    is BackupProblem.TooLarge -> getString(R.string.backup_error_too_large, problem.megabytes)
    BackupProblem.Invalid -> getString(R.string.backup_error_invalid)
    is BackupProblem.FutureVersion -> getString(
        R.string.backup_error_future_version,
        problem.fileVersion,
        problem.supported
    )
    is BackupProblem.DuplicateDays ->
        getString(R.string.backup_error_duplicate_days, problem.listed)
    is BackupProblem.DuplicateExercises ->
        getString(R.string.backup_error_duplicate_exercises, problem.listed)
    is BackupProblem.DuplicateDefinitions ->
        getString(R.string.backup_error_duplicate_definitions, problem.listed)
    is BackupProblem.UnknownDays ->
        getString(R.string.backup_error_unknown_days, problem.listed)
    BackupProblem.NotReadBack -> getString(R.string.backup_error_not_read_back)
    BackupProblem.Incomplete -> getString(R.string.backup_error_incomplete)
}
