package de.beispiel.meintraining.ui.tracking

import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.CardioValue
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.IntensityUnit
import de.beispiel.meintraining.util.amountOf
import de.beispiel.meintraining.util.exerciseTitle

/** Was der Tracking-Graph zeigt: die Gewichte der Kraftübungen oder die Cardio-Einheiten. */
enum class TrackingKind { STRENGTH, CARDIO }

/** Eine eingetragene Einheit als Punkt einer Cardio-Kurve: wann und wie viel des gewählten Werts. */
data class CardioPoint(val logId: Long, val timeMillis: Long, val amount: Double)

/**
 * Die Kurve einer Cardio-Übung für einen Wert, über die ganze Zeit, älteste Einheit zuerst.
 *
 * [name] steht in Legende und Auswahl und ist zugleich der Schlüssel fürs Ein- und Ausblenden:
 * die Übung samt Variation, beim Tempo dazu die Einheit, wenn die Übung beide kennt (siehe
 * [cardioCurves]). [exerciseName] ist der Name, an dem ihre Ziele hängen; [unit] die Einheit
 * ihres Tempos, nur beim Tempo gesetzt.
 */
data class CardioCurve(
    val name: String,
    val exerciseName: String,
    val unit: IntensityUnit?,
    val points: List<CardioPoint>
)

/**
 * Formt die Cardio-Einheiten in Kurven für den Wert [value] um – je Übung samt Variation eine,
 * aus den Einheiten, in denen dieser Wert eingetragen ist.
 *
 * Anders als ein Gewicht ist eine Einheit kein Zustand, der bis zur nächsten gilt: Sie fand an
 * einem Tag statt, und das war es. Die Kurve verbindet deshalb nur ihre Punkte, ohne
 * übernommenen Stand am Rand und ohne Fortsetzung bis heute.
 *
 * km/h und Stufe landen nie in einer Kurve: Eine Übung, die beides kennt – etwa weil das Gerät
 * gewechselt hat –, bekommt beim Tempo zwei, deren Namen die Einheit in Klammern tragen
 * ([unitLabel]). Kennt sie nur eine, bleibt es beim Namen.
 */
fun cardioCurves(
    logsOldestFirst: List<CardioLog>,
    value: CardioValue,
    unitLabel: (IntensityUnit) -> String
): List<CardioCurve> {
    val withValue = logsOldestFirst.mapNotNull { log -> log.amountOf(value)?.let { log to it } }
    val byExercise = withValue.groupBy { (log, _) -> exerciseTitle(log.exerciseName, log.variation) }
    return byExercise.flatMap { (title, entries) ->
        val byUnit = entries.groupBy { (log, _) ->
            // Ein Tempo ohne Einheit kann es nicht geben (siehe CardioLog.intensityUnit); käme
            // eines aus einer Sicherung von Hand, gilt es als km/h wie überall.
            if (value == CardioValue.INTENSITY) log.intensityUnit ?: IntensityUnit.KMH else null
        }
        byUnit.map { (unit, unitEntries) ->
            CardioCurve(
                name = if (unit != null && byUnit.size > 1) "$title (${unitLabel(unit)})" else title,
                exerciseName = unitEntries.first().first.exerciseName,
                unit = unit,
                points = unitEntries.map { (log, amount) -> CardioPoint(log.id, log.performedAt, amount) }
            )
        }
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

/**
 * Die sichtbaren Kurven im Zeitfenster als Linien für den Graphen; eine Kurve ohne Einheit im
 * Fenster fällt weg. Echte Punkte, keine übernommenen – siehe [cardioCurves].
 */
fun cardioSeries(
    curves: List<CardioCurve>,
    visibleNames: Set<String>,
    window: TimeWindow
): List<ChartSeries> = curves.mapNotNull { curve ->
    if (curve.name !in visibleNames) return@mapNotNull null
    val inside = curve.points.filter { it.timeMillis in window.startMillis..window.endMillis }
    if (inside.isEmpty()) return@mapNotNull null
    ChartSeries(
        name = curve.name,
        points = inside.map { ChartPoint(timeMillis = it.timeMillis, weightKg = it.amount) },
        intensityUnit = curve.unit
    )
}

/**
 * Die Kurven, bei denen in Prozent eine Senkung als Fortschritt zählt: die von Übungen, deren
 * Pfeil genau diesen Wert [value] nach unten steuert – etwa weniger Minuten auf dieselbe Strecke.
 * Für jeden anderen Wert derselben Übung bleibt mehr eben mehr.
 */
fun decreasingCardioNames(
    curves: List<CardioCurve>,
    definitions: List<ExerciseDefinition>,
    value: CardioValue
): Set<String> {
    val down = definitions
        .filter { it.cardio.arrowDown && it.cardio.arrowValue == value }
        .mapTo(HashSet()) { it.name }
    return curves.filter { it.exerciseName in down }.mapTo(HashSet()) { it.name }
}

