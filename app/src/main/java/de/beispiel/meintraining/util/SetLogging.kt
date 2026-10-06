package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.SetLog
import java.time.LocalDate
import java.time.ZoneId

/**
 * Wiederholungen, mit denen ein Satz vorbelegt wird, wenn es weder ein letztes Mal noch eine
 * Spanne gibt – eine Übung mit „3“ Sätzen und sonst nichts. Zehn ist die Mitte dessen, was man
 * für Kraft- und Muskelaufbau üblicherweise plant; der Stepper führt von dort schnell hin.
 */
const val DEFAULT_LOGGED_REPS = 10

// Notation einer Satzfolge: „60 kg × 12 / 11 / 10“. Reine Notation, keine übersetzbaren Texte –
// wie das „x“ der Sätze in Formatters.kt.
private const val REPS_TIMES = " × "
private const val REPS_SEPARATOR = " / "
private const val WEIGHT_GROUP_SEPARATOR = " · "

/**
 * Welche Übung ein Satz meint: Name und Variation (siehe [SetLog]). Der Trainingstag gehört
 * bewusst nicht dazu – „Letztes Mal“ greift auf einen anderen Tag zurück, wenn dieser noch
 * keine Einheit hat.
 */
data class SetLogKey(val name: String, val variation: String?)

/**
 * Eine Einheit: die Sätze einer Übung an einem Kalendertag und Trainingstag, nach Nummer
 * sortiert.
 */
data class SetUnit(
    val date: LocalDate,
    val dayId: Int,
    val sets: List<SetLog>
) {
    /**
     * Der Satz mit dieser Nummer; gäbe es ihn zweimal, gilt der jüngere. Das Protokoll legt keine
     * zwei an (siehe `TrainingRepository.logSet`), eine Sicherung von Hand könnte es aber.
     */
    fun set(number: Int): SetLog? = sets.lastOrNull { it.setNumber == number }

    /** Wie viele der Sätze 1 bis [planned] drin sind – Zusatzsätze zählen nicht mit. */
    fun plannedLogged(planned: Int): Int = (1..planned).count { set(it) != null }

    /** Sind alle [planned] Sätze protokolliert? Ohne geplante Sätze nie. */
    fun isComplete(planned: Int): Boolean = planned >= 1 && plannedLogged(planned) == planned
}

/** Das Protokoll nach Übung zerlegt und in Einheiten gefasst – einmal je Änderung gerechnet. */
fun setUnitsByExercise(
    logsOldestFirst: List<SetLog>,
    zone: ZoneId = ZoneId.systemDefault()
): Map<SetLogKey, List<SetUnit>> = logsOldestFirst
    .groupBy { SetLogKey(it.exerciseName, it.variation) }
    .mapValues { (_, logs) -> setUnits(logs, zone) }

/**
 * Fasst das Protokoll *einer* Übung in Einheiten, älteste zuerst.
 *
 * Eine Einheit ist ein Kalendertag an einem Trainingstag: Wer Tag 1 und Tag 3 am selben Abend
 * trainiert, hat zwei Einheiten, wer Tag 1 an zwei Tagen trainiert, ebenso.
 */
fun setUnits(
    logsOldestFirst: List<SetLog>,
    zone: ZoneId = ZoneId.systemDefault()
): List<SetUnit> = logsOldestFirst
    .groupBy { it.performedAt.toLocalDate(zone) to it.dayId }
    .map { (key, sets) ->
        SetUnit(
            date = key.first,
            dayId = key.second,
            sets = sets.sortedWith(compareBy({ it.setNumber }, { it.performedAt }, { it.id }))
        )
    }
    // Am selben Tag entscheidet der erste Satz, welche Einheit früher lag.
    .sortedWith(compareBy({ it.date }, { unit -> unit.sets.minOf { it.performedAt } }))

/** Die Einheit von heute an diesem Trainingstag – das, was gerade protokolliert wird. */
fun todaysUnit(units: List<SetUnit>, dayId: Int, today: LocalDate): SetUnit? =
    units.lastOrNull { it.dayId == dayId && it.date == today }

/**
 * „Letztes Mal“: die jüngste Einheit vor heute an diesem Trainingstag, sonst die jüngste an
 * einem anderen. Heute zählt nicht mit – das ist die Einheit, die gerade entsteht.
 */
fun lastUnit(units: List<SetUnit>, dayId: Int, today: LocalDate): SetUnit? {
    val earlier = units.filter { it.date < today }
    return earlier.lastOrNull { it.dayId == dayId } ?: earlier.lastOrNull()
}

/**
 * Womit die Wiederholungen eines Satzes vorbelegt sind: mit demselben Satz vom letzten Mal,
 * sonst dem unteren Ende der Spanne – oder, wo es nur eine Zahl gibt, mit dieser.
 */
fun suggestedReps(last: SetUnit?, setNumber: Int, repsMin: Int?, repsMax: Int?): Int =
    last?.set(setNumber)?.reps ?: repsMin ?: repsMax ?: DEFAULT_LOGGED_REPS

/**
 * Eine Folge von Sätzen in einer Zeile: `"60 kg × 12 / 11 / 10"`.
 *
 * Gleiche Gewichte hintereinander stehen einmal vorn, ein Wechsel beginnt eine neue Gruppe:
 * `"60 kg × 12 / 11 · 62,5 kg × 8"`. Sätze ohne Gewicht sind nur ihre Wiederholungen. Wie ein
 * Gewicht aussieht, sagt [weightLabel] – die Einheit kommt aus den Textressourcen.
 */
fun formatSetSeries(sets: List<SetLog>, weightLabel: (Double) -> String): String {
    val groups = mutableListOf<Pair<Double?, MutableList<Int>>>()
    sets.forEach { set ->
        val last = groups.lastOrNull()
        if (last != null && last.first == set.weightKg) {
            last.second += set.reps
        } else {
            groups += set.weightKg to mutableListOf(set.reps)
        }
    }
    return groups.joinToString(WEIGHT_GROUP_SEPARATOR) { (weight, reps) ->
        val series = reps.joinToString(REPS_SEPARATOR)
        if (weight == null) series else "${weightLabel(weight)}$REPS_TIMES$series"
    }
}
