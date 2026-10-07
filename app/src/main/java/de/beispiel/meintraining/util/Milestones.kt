package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.ExerciseKind
import de.beispiel.meintraining.data.model.WeightLog
import de.beispiel.meintraining.data.model.WorkoutSession
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * So viele Gewichts-Meilensteine zeigt „Als Nächstes“ höchstens – die, denen am wenigsten fehlt.
 * Mit einem Dutzend Übungen stünde sonst fast die ganze Übungsliste in der Karte.
 */
const val NEXT_WEIGHT_MILESTONES = 3

/** Kleiner als jeder Schritt (0,625 kg): Darunter ist ein Unterschied ein Rundungsrest. */
private const val THRESHOLD_TOLERANCE = 1e-6

/**
 * Die Arten von Meilensteinen, jede mit ihrer Leiter: Erst die festen Stufen, danach geht es in
 * gleichen Schritten weiter. So hat auch, wer die letzte feste Stufe hinter sich hat, immer noch
 * einen nächsten Meilenstein vor sich.
 *
 * [key] steht in der Kennung ([Milestone.id]), die in den Einstellungen gemerkt wird – einmal
 * vergeben, darf er sich nicht mehr ändern, sonst käme jeder gefeierte Meilenstein noch einmal.
 */
enum class MilestoneKind(val key: String, private val fixed: List<Int>, private val step: Int) {
    /** Abgehakte Trainings. */
    SESSIONS("trainings", listOf(10, 25, 50, 100, 150, 200), 50),

    /** Ziel-Serie in Wochen – siehe [currentWeeklyStreak]. */
    GOAL_STREAK("serie", listOf(4, 8, 12, 26, 52), 52),

    /** Volle Runden, in denen jeder Tag einmal dran war. */
    FULL_ROUNDS("runden", listOf(1, 10, 25, 50), 50),

    /**
     * Erstmals so viele kg bei einer Kraftübung mit Pfeil nach oben. Unter 50 kg gibt es keinen:
     * Dort ist eine runde Zahl noch keine Marke, bei der man feiern würde.
     */
    WEIGHT("gewicht", listOf(50, 60, 70, 80, 90, 100), 25),

    /** Gesamtzuwachs über alle Kraftübungen in kg – dieselbe Zahl wie auf der Statistikseite. */
    TOTAL_GAIN("zuwachs", listOf(25, 50, 100), 50),

    /** Kilometer aller Cardio-Einheiten zusammen. */
    CARDIO_KM("km", listOf(10, 50, 100, 250), 250),

    /** Minuten aller Cardio-Einheiten zusammen. */
    CARDIO_MINUTES("minuten", listOf(100, 500, 1000), 1000);

    /** Die Stufe an Stelle [index] der Leiter, beginnend bei 0. */
    fun threshold(index: Int): Int =
        if (index < fixed.size) fixed[index] else fixed.last() + (index - fixed.size + 1) * step

    /**
     * Die Stufe vor der an Stelle [index]. Vor der ersten liegt keine; dort gilt der Abstand zur
     * zweiten noch einmal nach unten (bei den Gewichten 40 vor 50), nie unter 0.
     */
    fun previousThreshold(index: Int): Int {
        if (index > 0) return threshold(index - 1)
        val gap = threshold(1) - threshold(0)
        return (threshold(0) - gap).coerceAtLeast(0)
    }
}

/**
 * Ein Meilenstein: eine Stufe [threshold] einer Leiter, bei [MilestoneKind.WEIGHT] für die Übung
 * [exercise].
 */
data class Milestone(val kind: MilestoneKind, val threshold: Int, val exercise: String? = null) {

    /**
     * Die Kennung, unter der ein gefeierter Meilenstein gemerkt wird, etwa `trainings:50` oder
     * `gewicht:100:Bankdrücken`. Der Name steht am Ende, weil er selbst einen Doppelpunkt
     * enthalten darf.
     */
    val id: String get() = if (exercise == null) "${kind.key}:$threshold" else "${kind.key}:$threshold:$exercise"
}

/** Ein erreichter Meilenstein und der Tag, an dem er erreicht wurde. */
data class ReachedMilestone(val milestone: Milestone, val date: LocalDate)

/**
 * Ein noch offener Meilenstein: [current] ist der Stand heute, [fraction] der Fortschritt für den
 * Balken (0 bis 1).
 */
data class NextMilestone(val milestone: Milestone, val current: Double, val fraction: Double)

/** Alles über die Meilensteine – siehe [milestones]. */
data class MilestoneOverview(
    /** Die erreichten, der jüngste zuerst. */
    val reached: List<ReachedMilestone>,
    /** Die nächsten, je Art einer; Gewichte zuletzt und höchstens [NEXT_WEIGHT_MILESTONES]. */
    val next: List<NextMilestone>
) {
    val reachedIds: Set<String> get() = reached.mapTo(HashSet()) { it.milestone.id }
}

/**
 * Was die Meilensteine brauchen – so, wie es aus der Datenbank und den Einstellungen kommt.
 * [weightLogs] älteste zuerst; die übrigen Listen in beliebiger Reihenfolge.
 */
data class MilestoneData(
    val sessions: List<WorkoutSession>,
    val weightLogs: List<WeightLog>,
    val cardioLogs: List<CardioLog>,
    val definitions: List<ExerciseDefinition>,
    val dayCount: Int,
    val rotationCuts: List<Long>,
    val weeklyGoal: Int
)

/** Ein Stand auf dem Weg zu den Stufen: am Tag [date] stand der Wert bei [value]. */
private data class Point(val date: LocalDate, val value: Double)

/**
 * Rechnet alle Meilensteine aus: welche erreicht sind und wann, und was als Nächstes kommt.
 *
 * Ein Meilenstein gilt mit dem ersten Tag als erreicht, an dem der Wert seine Stufe erreicht – und
 * bleibt es, auch wenn der Wert danach wieder fällt: Wer einmal 100 kg gedrückt hat, hat sie
 * gedrückt, auch nach einer Deload-Woche. Daraus ergibt sich das Datum, das die Karte zeigt, und
 * der Rückblick kann nach ihm filtern.
 *
 * Die Regeln im Einzelnen:
 * - Trainings, Ziel-Serie und volle Runden zählen wie auf der Statistikseite: die Serie am
 *   *aktuellen* Wochenziel (siehe [currentWeeklyStreak]), die Runden samt einer gerade vollen,
 *   die noch bis Mitternacht stehen bleibt (siehe [rotations]) – gefeiert wird im Moment des
 *   letzten Hakens, nicht am nächsten Morgen.
 * - Gewichte zählen nur bei Kraftübungen mit Pfeil nach oben, und nur Stufen *über* dem ersten
 *   Eintrag: Wer eine Übung mit 80 kg anlegt, hat die 50, 60, 70 und 80 nicht in dieser App
 *   erreicht, sondern mitgebracht.
 * - Der Gesamtzuwachs ist die Summe der Zuwächse aller Kraftübungen wie bei [exerciseGains]:
 *   ohne Cardio, bei Pfeil nach unten das Gesunkene, eine Übung im Minus zählt als 0.
 * - Cardio summiert jede eingetragene Einheit, wie [cardioTotals].
 */
fun milestones(
    data: MilestoneData,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): MilestoneOverview {
    val reached = mutableListOf<ReachedMilestone>()
    val next = mutableListOf<NextMilestone>()

    fun ladder(kind: MilestoneKind, points: List<Point>, current: Double, exercise: String? = null) {
        val (hits, open) = climb(kind, points, exercise)
        reached += hits
        next += NextMilestone(
            milestone = Milestone(kind, kind.threshold(open), exercise),
            current = current,
            fraction = (current / kind.threshold(open)).coerceIn(0.0, 1.0)
        )
    }

    val sessionDates = data.sessions.sortedBy { it.completedAt }.map { it.completedAt.toLocalDate(zone) }
    ladder(
        MilestoneKind.SESSIONS,
        sessionDates.mapIndexed { index, date -> Point(date, index + 1.0) },
        sessionDates.size.toDouble()
    )
    ladder(
        MilestoneKind.GOAL_STREAK,
        streakPoints(sessionDates, data.weeklyGoal),
        currentWeeklyStreak(sessionDates, today, data.weeklyGoal).toDouble()
    )
    val fullRounds = fullRoundDates(data.sessions, sessionDates, data.dayCount, today, data.rotationCuts)
    ladder(
        MilestoneKind.FULL_ROUNDS,
        fullRounds.mapIndexed { index, date -> Point(date, index + 1.0) },
        fullRounds.size.toDouble()
    )

    val cardioNames = data.definitions.filter { it.kind == ExerciseKind.CARDIO }.mapTo(HashSet()) { it.name }
    val decreasing = data.definitions.filter { it.progressionDown }.mapTo(HashSet()) { it.name }
    val strengthLogs = data.weightLogs.filter { it.exerciseName !in cardioNames }
    val gainPoints = gainPoints(strengthLogs, decreasing, zone)
    ladder(MilestoneKind.TOTAL_GAIN, gainPoints, gainPoints.lastOrNull()?.value ?: 0.0)

    if (data.cardioLogs.isNotEmpty()) {
        val cardio = data.cardioLogs.sortedBy { it.performedAt }
        var km = 0.0
        var minutes = 0.0
        val kmPoints = mutableListOf<Point>()
        val minutePoints = mutableListOf<Point>()
        cardio.forEach { log ->
            val date = log.performedAt.toLocalDate(zone)
            km += log.distanceKm ?: 0.0
            minutes += log.durationMin ?: 0.0
            kmPoints += Point(date, km)
            minutePoints += Point(date, minutes)
        }
        ladder(MilestoneKind.CARDIO_KM, kmPoints, km)
        ladder(MilestoneKind.CARDIO_MINUTES, minutePoints, minutes)
    }

    // Gewichte: jede Kraftübung mit Pfeil nach oben für sich.
    val known = data.definitions.associateBy { it.name }
    val weightNext = mutableListOf<NextMilestone>()
    strengthLogs.filter { it.exerciseName !in decreasing }
        .groupBy { it.exerciseName }
        .forEach { (name, logs) ->
            val first = logs.first().weightKg
            val points = logs.map { Point(it.recordedAt.toLocalDate(zone), it.weightKg) }
            val (hits, open) = climb(MilestoneKind.WEIGHT, points, name, above = first)
            reached += hits
            // Als Nächstes nur bei Übungen, die es noch gibt – und erst ab der Stufe davor: Bei
            // 20 kg Curls ist „noch 30 kg bis 50“ kein Meilenstein in Sichtweite.
            if (name !in known) return@forEach
            val current = logs.last().weightKg
            val target = MilestoneKind.WEIGHT.threshold(open)
            val from = MilestoneKind.WEIGHT.previousThreshold(open)
            if (current < from) return@forEach
            weightNext += NextMilestone(
                milestone = Milestone(MilestoneKind.WEIGHT, target, name),
                current = current,
                fraction = ((current - from) / (target - from)).coerceIn(0.0, 1.0)
            )
        }
    next += weightNext.sortedByDescending { it.fraction }.take(NEXT_WEIGHT_MILESTONES)

    return MilestoneOverview(
        reached = reached.sortedWith(
            compareByDescending<ReachedMilestone> { it.date }
                .thenByDescending { it.milestone.kind.ordinal }
                .thenByDescending { it.milestone.threshold }
        ),
        next = next
    )
}

/**
 * Steigt die Leiter von [kind] entlang [points] hinauf: Jede Stufe ist am ersten Punkt erreicht,
 * dessen Wert an sie heranreicht. Liefert die erreichten Stufen und die Stelle der ersten offenen.
 *
 * Stufen bis einschließlich [above] werden übersprungen – sie lagen schon vor dem ersten Punkt.
 */
private fun climb(
    kind: MilestoneKind,
    points: List<Point>,
    exercise: String?,
    above: Double = Double.NEGATIVE_INFINITY
): Pair<List<ReachedMilestone>, Int> {
    val hits = mutableListOf<ReachedMilestone>()
    var index = 0
    while (kind.threshold(index) <= above + THRESHOLD_TOLERANCE) index++
    while (true) {
        val threshold = kind.threshold(index)
        val hit = points.firstOrNull { it.value >= threshold - THRESHOLD_TOLERANCE } ?: break
        hits += ReachedMilestone(Milestone(kind, threshold, exercise), hit.date)
        index++
    }
    return hits to index
}

/**
 * Die Ziel-Serie im Verlauf: für jede Woche, die das Ziel erreicht, die Länge der Serie bis zu
 * ihr – am Tag des Trainings, mit dem die Woche ihr Ziel erreicht hat.
 */
private fun streakPoints(datesOldestFirst: List<LocalDate>, goal: Int): List<Point> {
    val needed = goal.coerceAtLeast(1)
    var run = 0
    var previous: LocalDate? = null
    return datesOldestFirst.groupBy { it.weekStart() }
        .mapNotNull { (week, inWeek) -> inWeek.getOrNull(needed - 1)?.let { week to it } }
        .sortedBy { it.first }
        .map { (week, date) ->
            run = if (previous?.let { ChronoUnit.WEEKS.between(it, week) } == 1L) run + 1 else 1
            previous = week
            Point(date, run.toDouble())
        }
}

/** Der Tag, an dem jede volle Runde voll wurde – älteste zuerst, die laufende eingeschlossen. */
private fun fullRoundDates(
    sessions: List<WorkoutSession>,
    datesOldestFirst: List<LocalDate>,
    dayCount: Int,
    today: LocalDate,
    cuts: List<Long>
): List<LocalDate> {
    val entries = sessions.sortedBy { it.completedAt }.mapIndexed { index, session ->
        RotationEntry(dayId = session.dayId, date = datesOldestFirst[index], completedAt = session.completedAt)
    }
    return rotations(entries, dayCount, today, cuts).filter { it.isFull }.mapNotNull { it.lastDate }
}

/** Der Gesamtzuwachs nach jedem Eintrag im Gewichtsverlauf – siehe [milestones]. */
private fun gainPoints(logsOldestFirst: List<WeightLog>, decreasing: Set<String>, zone: ZoneId): List<Point> {
    val first = HashMap<String, Double>()
    val gains = HashMap<String, Double>()
    var total = 0.0
    return logsOldestFirst.map { log ->
        val name = log.exerciseName
        val start = first.getOrPut(name) { log.weightKg }
        val gain = (if (name in decreasing) start - log.weightKg else log.weightKg - start).coerceAtLeast(0.0)
        total += gain - (gains.put(name, gain) ?: 0.0)
        Point(log.recordedAt.toLocalDate(zone), total)
    }
}

/**
 * Was davon gefeiert wird: die eben erst gemerkten [fresh] – und von denen nur, was heute erreicht
 * wurde.
 *
 * Alles Ältere wird still gemerkt. Das ist der Fall nach dem Einlesen einer Sicherung, nach dem
 * ersten Start mit Meilensteinen, nach einem nachgetragenen alten Training, einer umbenannten
 * Übung oder einem gesenkten Wochenziel: Erreicht ist dann etwas, das schon zurückliegt, und ein
 * Konfetti dafür feierte keinen Moment, sondern eine Rechnung.
 */
fun milestonesToCelebrate(
    reached: List<ReachedMilestone>,
    fresh: Set<String>,
    today: LocalDate
): List<Milestone> = reached
    .filter { it.milestone.id in fresh && !it.date.isBefore(today) }
    .map { it.milestone }

/** Zieht die Gewichts-Meilensteine einer umbenannten Übung auf ihren neuen Namen um. */
fun renameMilestoneIds(ids: Set<String>, from: String, to: String): Set<String> = ids.mapTo(HashSet()) { id ->
    val parts = id.split(':', limit = 3)
    if (parts.size == 3 && parts[0] == MilestoneKind.WEIGHT.key && parts[2] == from) "${parts[0]}:${parts[1]}:$to" else id
}

/** Nimmt die Gewichts-Meilensteine gelöschter Übungen heraus. */
fun dropMilestoneIds(ids: Set<String>, names: Set<String>): Set<String> = ids.filterTo(HashSet()) { id ->
    val parts = id.split(':', limit = 3)
    !(parts.size == 3 && parts[0] == MilestoneKind.WEIGHT.key && parts[2] in names)
}
