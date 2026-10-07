package de.beispiel.meintraining.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Die Bilanz der abgeschlossenen Runden – siehe [rotationSummary].
 *
 * Eine Runde ist *vollständig*, wenn jeder Tag einmal dran war, und *abgebrochen*, wenn der Pfeil
 * neben dem Haken sie vorher beendet hat.
 */
data class RotationSummary(
    /** Abgeschlossene Runden; die laufende zählt nicht. */
    val count: Int,
    /** Davon vollständig. */
    val fullCount: Int,
    /** Ø Tage vom ersten bis zum letzten Training einer Runde, beide mitgezählt. */
    val averageDays: Double,
    /**
     * Der Trainingstag, der in abgebrochenen Runden am häufigsten gefehlt hat; `null` ohne
     * abgebrochene Runde. Fehlen mehrere gleich oft, gilt der vorderste.
     */
    val mostMissedDayId: Int? = null,
    /** In wie vielen abgebrochenen Runden [mostMissedDayId] gefehlt hat. */
    val mostMissedCount: Int = 0
) {
    val abortedCount: Int get() = count - fullCount

    /** Anteil der vollständigen Runden, 0 bis 1. */
    val fullShare: Double get() = if (count > 0) fullCount.toDouble() / count else 0.0
}

/**
 * Wie die abgeschlossenen Runden ausgegangen sind: wie viele, wie lang im Schnitt, wie viele
 * davon vollständig und welcher Tag in den abgebrochenen am häufigsten gefehlt hat. `null`,
 * solange keine Runde abgeschlossen ist.
 *
 * Die Parameter sind dieselben wie bei [rotations], und die Runden sind genau die, die auch der
 * Verlauf zeigt. Die laufende Runde zählt nicht, auch eine volle nicht, die bis Mitternacht
 * stehen bleibt: Sie geht ab dem nächsten Tag in die Bilanz ein.
 *
 * Grenzen dieser Rechnung, alle drei gewollt in Kauf genommen:
 * - Die Rundenschnitte wandern nicht mit in die Sicherung; `BackupRepository.restore` leert sie
 *   sogar (der Grund steht dort). Nach dem Einlesen einer Sicherung fehlen also alle Schnitte,
 *   und jede früher per Pfeil abgebrochene Runde verschmilzt mit der folgenden.
 * - Gespeichert werden höchstens die letzten 100 Schnitte (`MAX_ROTATION_CUTS` in
 *   `SettingsStore`). Was davor abgebrochen wurde, verschmilzt genauso.
 * - Gerechnet wird mit der *aktuellen* Tageszahl: Nach einer verlängerten oder verkürzten Runde
 *   teilen sich auch vergangene Runden neu auf, so als hätte immer diese Zahl gegolten.
 * Die Aufteilung vergangener Runden stimmt danach also nicht mehr ganz. Für eine Bilanz auf der
 * Statistikseite ist das vertretbar; genauer ginge es nur, wenn Schnitte und Tageszahl je Runde
 * mitgeschrieben – und mitgesichert – würden.
 */
fun rotationSummary(
    entriesOldestFirst: List<RotationEntry>,
    dayCount: Int,
    today: LocalDate,
    cuts: List<Long> = emptyList()
): RotationSummary? {
    val finished = rotations(entriesOldestFirst, dayCount, today, cuts)
        .dropLast(1)
        .filter { !it.isEmpty }
    if (finished.isEmpty()) return null

    val averageDays = finished.map { rotation ->
        val first = entriesOldestFirst[rotation.entryIndices.first()].date
        val last = rotation.lastDate ?: first
        ChronoUnit.DAYS.between(first, last) + 1
    }.average()

    val missed = IntArray(dayCount + 1)
    finished.filter { !it.isFull }.forEach { rotation ->
        (1..dayCount).filter { it !in rotation.completedDayIds }.forEach { missed[it]++ }
    }
    val mostMissed = (1..dayCount).maxByOrNull { missed[it] }?.takeIf { missed[it] > 0 }

    return RotationSummary(
        count = finished.size,
        fullCount = finished.count { it.isFull },
        averageDays = averageDays,
        mostMissedDayId = mostMissed,
        mostMissedCount = mostMissed?.let { missed[it] } ?: 0
    )
}
