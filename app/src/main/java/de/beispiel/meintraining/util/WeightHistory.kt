package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.model.WeightLog
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Wie viele der jüngsten Gewichte die kleine Kurve im Bearbeiten-Sheet zeigt. */
const val WEIGHT_HISTORY_RECENT_POINTS = 10

/** Echtes Minuszeichen statt Bindestrich: gleich breit wie das Plus, die Zahlen springen nicht. */
private const val MINUS_SIGN = '−'

/** Eine Gewichtsänderung: um wie viel – negativ heißt gesenkt – und an welchem Tag. */
data class WeightChange(val deltaKg: Double, val date: LocalDate)

/**
 * Der Gewichtsverlauf einer Übung, zusammengefasst für die Zeile unter dem Gewichtsfeld:
 * „60 kg seit 12 Tagen · zuletzt +2,5 kg am 18. Sept.“
 */
data class WeightHistory(
    /** Das zuletzt eingetragene Gewicht. */
    val currentKg: Double,
    /** Seit wann es so steht: der Tag der letzten echten Änderung, sonst der erste Eintrag. */
    val since: LocalDate,
    /** Kalendertage seit [since]; nie negativ. */
    val daysSince: Int,
    /** Die letzte echte Änderung; `null`, solange es nur den Anfangswert gibt. */
    val lastChange: WeightChange?,
    /** Die jüngsten Gewichte, älteste zuerst – höchstens [WEIGHT_HISTORY_RECENT_POINTS]. */
    val recentWeights: List<Double>
)

/**
 * Fasst den Verlauf einer Übung zusammen; ohne Einträge gibt es nichts zu sagen (`null`).
 *
 * [logsOldestFirst] ist der Verlauf *einer* Übung in der Reihenfolge, in der die DAO ihn
 * liefert. Ein Eintrag mit demselben Gewicht wie sein Vorgänger ist keine Änderung: Er
 * verlängert die Zeit seit der letzten, statt sie neu beginnen zu lassen.
 */
fun weightHistory(
    logsOldestFirst: List<WeightLog>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): WeightHistory? {
    val latest = logsOldestFirst.lastOrNull() ?: return null
    // Die letzte Stelle, an der sich das Gewicht wirklich geändert hat.
    val changeIndex = logsOldestFirst.indices.reversed().firstOrNull { index ->
        index > 0 && logsOldestFirst[index].weightKg != logsOldestFirst[index - 1].weightKg
    }
    val sinceLog = changeIndex?.let { logsOldestFirst[it] } ?: logsOldestFirst.first()
    val since = sinceLog.recordedAt.toLocalDate(zone)
    return WeightHistory(
        currentKg = latest.weightKg,
        since = since,
        // Eine zurückgestellte Uhr ergäbe sonst „seit -3 Tagen“.
        daysSince = ChronoUnit.DAYS.between(since, today).toInt().coerceAtLeast(0),
        lastChange = changeIndex?.let { index ->
            WeightChange(
                deltaKg = logsOldestFirst[index].weightKg - logsOldestFirst[index - 1].weightKg,
                date = since
            )
        },
        recentWeights = logsOldestFirst.takeLast(WEIGHT_HISTORY_RECENT_POINTS).map { it.weightKg }
    )
}

/** `2.5 → "+2,5"`, `-1.25 → "−1,25"` – eine Änderung trägt ihr Vorzeichen immer sichtbar. */
fun Double.toSignedDecimalString(): String =
    if (this < 0) "$MINUS_SIGN${(-this).toDecimalString()}" else "+${toDecimalString()}"
