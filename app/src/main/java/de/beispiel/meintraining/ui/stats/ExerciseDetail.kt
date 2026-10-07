package de.beispiel.meintraining.ui.stats

import androidx.compose.runtime.saveable.listSaver
import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.CardioTargets
import de.beispiel.meintraining.data.model.CardioValue
import de.beispiel.meintraining.data.model.ExerciseDefinition
import de.beispiel.meintraining.data.model.IntensityUnit
import de.beispiel.meintraining.data.model.SetLog
import de.beispiel.meintraining.data.model.WeightLog
import de.beispiel.meintraining.ui.tracking.AxisTick
import de.beispiel.meintraining.ui.tracking.ChartPoint
import de.beispiel.meintraining.ui.tracking.ChartSeries
import de.beispiel.meintraining.ui.tracking.TimeRange
import de.beispiel.meintraining.ui.tracking.TimeWindow
import de.beispiel.meintraining.ui.tracking.buildSeries
import de.beispiel.meintraining.ui.tracking.buildTimeAxis
import de.beispiel.meintraining.ui.tracking.cardioCurves
import de.beispiel.meintraining.ui.tracking.cardioSeries
import de.beispiel.meintraining.ui.tracking.timeWindowFor
import de.beispiel.meintraining.util.CardioProgress
import de.beispiel.meintraining.util.OneRepMaxPoint
import de.beispiel.meintraining.util.ProgressTempo
import de.beispiel.meintraining.util.RepRecord
import de.beispiel.meintraining.util.StrengthProgress
import de.beispiel.meintraining.util.WeekVolume
import de.beispiel.meintraining.util.WeightForecast
import de.beispiel.meintraining.util.bestSet
import de.beispiel.meintraining.util.cardioProgress
import de.beispiel.meintraining.util.exerciseTitle
import de.beispiel.meintraining.util.oneRepMaxHistory
import de.beispiel.meintraining.util.repRecords
import de.beispiel.meintraining.util.strengthProgress
import de.beispiel.meintraining.util.weeklyVolume
import de.beispiel.meintraining.util.weightForecast
import java.time.LocalDate
import java.time.ZoneId

/**
 * Welche Übung eine Zeile von „Fortschritt je Übung“ meint – und damit, welche Detailseite offen
 * ist. Eine Kraftübung ist ihr Name (das Gewicht hängt am Namen), eine Cardio-Übung Name samt
 * Variation (die Einheiten hängen an beidem, siehe `CardioLog`).
 */
data class ProgressKey(val name: String, val variation: String?, val isCardio: Boolean) {
    val title: String get() = exerciseTitle(name, variation)

    companion object {
        /** Für `rememberSaveable`: Die offene Detailseite übersteht das Drehen des Geräts. */
        val Saver = listSaver<ProgressKey?, Any?>(
            save = { key -> if (key == null) emptyList() else listOf(key.name, key.variation, key.isCardio) },
            restore = { values ->
                if (values.size < 3) null
                else ProgressKey(values[0] as String, values[1] as String?, values[2] as Boolean)
            }
        )
    }
}

/** Eine Zeile von „Fortschritt je Übung“. */
sealed interface ProgressEntry {
    val key: ProgressKey

    /** Eine Kraftübung; [tempo] und [daysSinceIncrease] sind schon für heute ausgerechnet. */
    data class Strength(
        val progress: StrengthProgress,
        val tempo: ProgressTempo?,
        val daysSinceIncrease: Long?
    ) : ProgressEntry {
        override val key get() = ProgressKey(progress.name, null, isCardio = false)
    }

    data class Cardio(val progress: CardioProgress) : ProgressEntry {
        override val key get() = ProgressKey(progress.name, progress.variation, isCardio = true)
    }
}

/** Ein fertig gerechneter Graph: Linien, Zeitfenster und Achse – genau, was `WeightChart` braucht. */
data class DetailChart(val series: List<ChartSeries>, val window: TimeWindow, val ticks: List<AxisTick>)

/** Die Detailseite einer Übung aus „Fortschritt je Übung“. */
sealed interface ExerciseDetail {
    val key: ProgressKey

    data class Strength(
        override val key: ProgressKey,
        val entry: ProgressEntry.Strength,
        /** Der Gewichtsverlauf über die ganze Zeit. */
        val chart: DetailChart,
        /** Nur bei Pfeil nach oben gerechnet; `null` auch, wenn die Daten nicht reichen. */
        val forecast: WeightForecast?,
        /** Aus dem Satz-Protokoll; `null` ohne protokollierten Satz. */
        val sets: SetStats?
    ) : ExerciseDetail

    data class Cardio(
        override val key: ProgressKey,
        /** `null` nur, wenn keine Einheit einen Wert hat – dann gibt es auch keine Kurven. */
        val progress: CardioProgress?,
        /** Die Kurven je Wert, nur für Werte, die in mindestens einer Einheit stehen. */
        val charts: Map<CardioValue, DetailChart>,
        /** Alle Einheiten der Übung, auch die ohne den Wert des Fortschritts. */
        val entries: Int,
        val lastAt: Long?
    ) : ExerciseDetail
}

/**
 * Was das Satz-Protokoll über eine Kraftübung sagt: das geschätzte 1RM als Verlauf, der beste
 * Satz, die Rekorde und das Volumen je Woche. Siehe `util/SetRecords.kt`.
 */
data class SetStats(
    /** `null`, solange kein Satz bis [de.beispiel.meintraining.util.EPLEY_MAX_REPS] dabei ist. */
    val oneRepMaxChart: DetailChart?,
    val bestOneRepMax: OneRepMaxPoint?,
    val bestSet: SetLog?,
    val records: List<RepRecord>,
    val weeklyVolume: List<WeekVolume>
)

/**
 * Der Fortschritt einer Kraftübung als Zeile, `null` ohne Gewichtsverlauf. [logsOldestFirst]
 * gehören zu genau dieser Übung.
 */
fun strengthEntry(
    name: String,
    logsOldestFirst: List<WeightLog>,
    isDecreasing: Boolean,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): ProgressEntry.Strength? {
    val progress = strengthProgress(name, logsOldestFirst, isDecreasing) ?: return null
    return ProgressEntry.Strength(
        progress = progress,
        tempo = progress.tempo(zone),
        daysSinceIncrease = progress.daysSinceLastIncrease(today, zone)
    )
}

/**
 * Rechnet die Detailseite für [key] aus; `null`, wenn es für die Übung nichts zu zeigen gibt –
 * etwa weil sie gelöscht und ihr Verlauf mit ihr verschwunden ist.
 *
 * Bewusst unabhängig davon, ob die Übung noch im Plan steht: Wer die Seite offen hat, während
 * eine Übung ausgeblendet wird, soll ihre Zahlen nicht unter den Fingern verlieren.
 */
fun exerciseDetail(
    key: ProgressKey,
    weightLogs: List<WeightLog>,
    setLogs: List<SetLog>,
    cardioLogs: List<CardioLog>,
    definitions: List<ExerciseDefinition>,
    today: LocalDate,
    now: Long,
    cardioUnitLabel: (IntensityUnit) -> String,
    zone: ZoneId = ZoneId.systemDefault()
): ExerciseDetail? {
    val definition = definitions.firstOrNull { it.name == key.name }
    return if (key.isCardio) {
        val logs = cardioLogs.filter { it.exerciseName == key.name && it.variation == key.variation }
        if (logs.isEmpty()) return null
        cardioDetail(key, logs, definition, now, cardioUnitLabel)
    } else {
        val logs = weightLogs.filter { it.exerciseName == key.name }
        val isDecreasing = definition?.progressionDown == true
        val entry = strengthEntry(key.name, logs, isDecreasing, today, zone) ?: return null
        val window = timeWindowFor(TimeRange.TOTAL, today.year, logs, now, zone)
        val sets = setLogs.filter { it.exerciseName == key.name }
        ExerciseDetail.Strength(
            key = key,
            entry = entry,
            chart = DetailChart(
                // Läuft bis heute weiter, wie im Tracking bei einer Übung, die trainiert wird.
                series = buildSeries(logs, listOf(key.name), window, now, activeNames = setOf(key.name)),
                window = window,
                ticks = buildTimeAxis(window, zone)
            ),
            forecast = if (isDecreasing) null else weightForecast(key.name, logs, today, zone),
            // Bei Pfeil nach unten ist das Gewicht eine Unterstützung: Ein 1RM, Rekorde oder ein
            // Volumen daraus gäben an, wie viel geholfen wurde – das Gegenteil dessen, was zählt.
            sets = if (isDecreasing || sets.isEmpty()) null else setStats(key.title, sets, today, now, zone)
        )
    }
}

private fun setStats(
    title: String,
    setsOldestFirst: List<SetLog>,
    today: LocalDate,
    now: Long,
    zone: ZoneId
): SetStats {
    val history = oneRepMaxHistory(setsOldestFirst, zone)
    val chart = history.takeIf { it.isNotEmpty() }?.let {
        val window = timeWindowFor(TimeRange.TOTAL, today.year, it.first().performedAt, now, zone)
        DetailChart(
            // Wie Cardio-Einheiten: Jeder Punkt war ein Training, kein Zustand bis zum nächsten.
            series = listOf(
                ChartSeries(name = title, points = it.map { point -> ChartPoint(point.performedAt, point.kg) })
            ),
            window = window,
            ticks = buildTimeAxis(window, zone)
        )
    }
    return SetStats(
        oneRepMaxChart = chart,
        bestOneRepMax = history.maxByOrNull { it.kg },
        bestSet = bestSet(setsOldestFirst),
        records = repRecords(setsOldestFirst),
        weeklyVolume = weeklyVolume(setsOldestFirst, today, zone = zone)
    )
}

private fun cardioDetail(
    key: ProgressKey,
    logsOldestFirst: List<CardioLog>,
    definition: ExerciseDefinition?,
    now: Long,
    cardioUnitLabel: (IntensityUnit) -> String
): ExerciseDetail.Cardio {
    val targets = definition?.cardio ?: CardioTargets()
    val firstMillis = logsOldestFirst.first().performedAt
    val window = timeWindowFor(TimeRange.TOTAL, 0, firstMillis, now)
    val ticks = buildTimeAxis(window)
    val charts = CardioValue.entries.mapNotNull { value ->
        // Die Kurven aus dem Tracking, nur für diese eine Übung – beim Tempo womöglich zwei,
        // eine je Einheit (siehe cardioCurves).
        val curves = cardioCurves(logsOldestFirst, value, cardioUnitLabel)
        val series = cardioSeries(curves, curves.mapTo(HashSet()) { it.name }, window)
        if (series.isEmpty()) null else value to DetailChart(series, window, ticks)
    }.toMap()
    return ExerciseDetail.Cardio(
        key = key,
        progress = cardioProgress(key.name, key.variation, logsOldestFirst, targets),
        charts = charts,
        entries = logsOldestFirst.size,
        lastAt = logsOldestFirst.last().performedAt
    )
}
