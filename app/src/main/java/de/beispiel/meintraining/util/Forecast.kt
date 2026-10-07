package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.WeightLog
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Eine Prognose braucht so viele Steigerungen – aus zweien lässt sich jede Gerade ziehen, ein
 * Tempo ist das noch nicht.
 */
const val FORECAST_MIN_INCREASES = 3

/** … über mindestens so viele Tage, vom ersten Eintrag bis zur jüngsten Steigerung. */
const val FORECAST_MIN_DAYS = 28L

/** Gerechnet wird mit dem Tempo dieses Zeitraums bis heute: rund ein Vierteljahr. */
const val FORECAST_WINDOW_DAYS = 90L

/** Eine Marke weiter weg als so viele Jahre ist keine Schätzung mehr, sondern Raten. */
const val FORECAST_MAX_YEARS = 2L

/** Unter diesem Gewicht ist die nächste Marke ein Vielfaches von 5 kg, darüber von 10 kg. */
private const val SMALL_MARK_LIMIT_KG = 40.0
private const val SMALL_MARK_STEP_KG = 5.0
private const val LARGE_MARK_STEP_KG = 10.0

/** Bleibt der Rest unter dieser Schwelle, liegt das Gewicht schon *auf* der Marke. */
private const val MARK_TOLERANCE = 1e-6

/**
 * Die Prognose einer Kraftübung: Im bisherigen Tempo ist [targetKg] etwa am [date] erreicht.
 * [kgPerWeek] ist dieses Tempo – nur zur Erklärung, angezeigt wird das Datum.
 */
data class WeightForecast(
    val name: String,
    val currentKg: Double,
    val targetKg: Double,
    val date: LocalDate,
    val kgPerWeek: Double
)

/**
 * Die nächste runde Marke über [currentKg]: das nächste Vielfache von 10 kg, unter 40 kg von 5 kg.
 * Wer genau auf einer Marke steht, hat sie schon – die nächste liegt einen Schritt darüber.
 */
fun nextMark(currentKg: Double): Double {
    val step = if (currentKg < SMALL_MARK_LIMIT_KG) SMALL_MARK_STEP_KG else LARGE_MARK_STEP_KG
    return (floor(currentKg / step + MARK_TOLERANCE) + 1) * step
}

/**
 * Schätzt, wann eine Kraftübung mit Pfeil nach oben ihre nächste runde Marke ([nextMark])
 * erreicht; `null`, wo das keine ehrliche Schätzung wäre.
 *
 * Voraussetzung sind [FORECAST_MIN_INCREASES] Steigerungen über mindestens [FORECAST_MIN_DAYS]
 * Tage. Das Tempo kommt aus einer linearen Regression über die letzten [FORECAST_WINDOW_DAYS]
 * Tage: Ein Gewicht ist ein Zustand, der bis zur nächsten Änderung gilt (wie im Graphen, siehe
 * `buildSeries`), also geht jeder Tag des Zeitraums mit dem Gewicht ein, das an seinem Ende galt.
 * Die Änderungen selbst als Punkte zu nehmen hieße, eine Woche voller Korrekturen wöge mehr als
 * zwei Monate auf einem Gewicht – und eben die Stillstände bremsen das Tempo zu Recht.
 *
 * Gezählt wird ab dem jüngsten Stand, nicht ab der Geraden: Die Frage ist, wie lange es von
 * *hier* aus dauert. Steigt die Gerade nicht, oder läge die Marke weiter als [FORECAST_MAX_YEARS]
 * Jahre weg, gibt es keine Prognose.
 *
 * [logsOldestFirst] gehören zu genau dieser Übung; eine mit Pfeil nach unten gibt der Aufrufer gar
 * nicht erst herein – bei ihr gibt es keine Marke nach oben.
 */
fun weightForecast(
    name: String,
    logsOldestFirst: List<WeightLog>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): WeightForecast? {
    val progress = strengthProgress(name, logsOldestFirst, isDecreasing = false) ?: return null
    if (progress.increases < FORECAST_MIN_INCREASES) return null
    val firstDay = progress.firstAt.toLocalDate(zone)
    val lastIncreaseDay = progress.lastIncreaseAt?.toLocalDate(zone) ?: return null
    if (ChronoUnit.DAYS.between(firstDay, lastIncreaseDay) < FORECAST_MIN_DAYS) return null

    val slope = dailySlope(logsOldestFirst, today, zone) ?: return null
    if (slope <= 0.0) return null
    val current = progress.toKg
    val target = nextMark(current)
    val date = today.plusDays(ceil((target - current) / slope).toLong())
    if (date.isAfter(today.plusYears(FORECAST_MAX_YEARS))) return null
    return WeightForecast(
        name = name,
        currentKg = current,
        targetKg = target,
        date = date,
        kgPerWeek = slope * DAYS_PER_WEEK
    )
}

private const val DAYS_PER_WEEK = 7

/**
 * Steigung der Ausgleichsgeraden in kg je Tag über die Tage von `today - FORECAST_WINDOW_DAYS`
 * (frühestens dem ersten Eintrag) bis heute; `null` unter zwei Tagen.
 */
private fun dailySlope(logsOldestFirst: List<WeightLog>, today: LocalDate, zone: ZoneId): Double? {
    val byDay = logsOldestFirst.map { it.recordedAt.toLocalDate(zone) to it.weightKg }
    val start = maxOf(today.minusDays(FORECAST_WINDOW_DAYS), byDay.first().first)
    val days = ChronoUnit.DAYS.between(start, today).toInt() + 1
    if (days < 2) return null

    // Der Stand am Ende jedes Tags: der jüngste Eintrag bis einschließlich dieses Tags.
    var index = 0
    var weight = byDay.first().second
    val samples = DoubleArray(days) { offset ->
        val day = start.plusDays(offset.toLong())
        while (index < byDay.size && !byDay[index].first.isAfter(day)) {
            weight = byDay[index].second
            index++
        }
        weight
    }

    // Kleinste Quadrate mit x = 0, 1, 2, … Tage.
    val meanX = (days - 1) / 2.0
    val meanY = samples.average()
    var numerator = 0.0
    var denominator = 0.0
    samples.forEachIndexed { x, y ->
        numerator += (x - meanX) * (y - meanY)
        denominator += (x - meanX) * (x - meanX)
    }
    return numerator / denominator
}
