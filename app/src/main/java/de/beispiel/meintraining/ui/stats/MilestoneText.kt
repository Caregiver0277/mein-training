package de.beispiel.meintraining.ui.stats

import android.content.res.Resources
import de.beispiel.meintraining.R
import de.beispiel.meintraining.util.Milestone
import de.beispiel.meintraining.util.MilestoneKind
import de.beispiel.meintraining.util.NextMilestone
import de.beispiel.meintraining.util.toDecimalString
import kotlin.math.roundToInt

/** Bei genau so viel kg heißt der Gewichts-Meilenstein „dreistellig“. */
private const val THREE_DIGITS_KG = 100

/**
 * Der Name eines Meilensteins, etwa „50 Trainings“ oder „Bankdrücken: dreistellig ↑“.
 *
 * Mit [Resources] statt als Composable: Die Meldung beim Erreichen setzt ihren Text außerhalb
 * der Komposition zusammen, wie die Snackbars.
 */
fun milestoneTitle(milestone: Milestone, resources: Resources): String {
    val threshold = milestone.threshold
    return when (milestone.kind) {
        MilestoneKind.SESSIONS ->
            resources.getQuantityString(R.plurals.milestone_sessions, threshold, threshold)
        MilestoneKind.GOAL_STREAK ->
            resources.getQuantityString(R.plurals.milestone_streak, threshold, threshold)
        MilestoneKind.FULL_ROUNDS ->
            resources.getQuantityString(R.plurals.milestone_rounds, threshold, threshold)
        MilestoneKind.WEIGHT -> if (threshold == THREE_DIGITS_KG) {
            resources.getString(R.string.milestone_weight_three_digits, milestone.exercise.orEmpty())
        } else {
            resources.getString(R.string.milestone_weight, milestone.exercise.orEmpty(), threshold.toString())
        }
        MilestoneKind.TOTAL_GAIN -> resources.getString(R.string.milestone_gain, threshold.toString())
        MilestoneKind.CARDIO_KM -> resources.getString(R.string.milestone_cardio_km, threshold.toString())
        MilestoneKind.CARDIO_MINUTES -> resources.getString(R.string.milestone_cardio_minutes, threshold.toString())
    }
}

/** Stand und Ziel eines offenen Meilensteins in seiner Einheit: „36 von 50“, „85 von 90 kg“. */
fun milestoneProgressText(next: NextMilestone, resources: Resources): String {
    val threshold = next.milestone.threshold
    val current = when (next.milestone.kind) {
        MilestoneKind.SESSIONS, MilestoneKind.GOAL_STREAK, MilestoneKind.FULL_ROUNDS,
        MilestoneKind.CARDIO_MINUTES -> next.current.roundToInt().toString()
        MilestoneKind.WEIGHT, MilestoneKind.TOTAL_GAIN, MilestoneKind.CARDIO_KM ->
            next.current.roundTo(1).toDecimalString()
    }
    val target = when (next.milestone.kind) {
        MilestoneKind.SESSIONS, MilestoneKind.FULL_ROUNDS -> threshold.toString()
        MilestoneKind.GOAL_STREAK -> resources.getQuantityString(R.plurals.stats_weeks, threshold, threshold)
        MilestoneKind.WEIGHT, MilestoneKind.TOTAL_GAIN ->
            resources.getString(R.string.stats_kg_value, threshold.toString())
        MilestoneKind.CARDIO_KM -> resources.getString(R.string.stats_km_value, threshold.toString())
        MilestoneKind.CARDIO_MINUTES -> resources.getString(R.string.stats_minutes_value, threshold)
    }
    return resources.getString(R.string.stats_milestones_progress, current, target)
}
