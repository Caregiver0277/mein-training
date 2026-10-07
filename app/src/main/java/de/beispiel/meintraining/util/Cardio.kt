package de.beispiel.meintraining.util

import de.beispiel.meintraining.data.local.SECONDS_PER_MINUTE
import de.beispiel.meintraining.data.model.CardioLog
import de.beispiel.meintraining.data.model.CardioValue
import de.beispiel.meintraining.data.model.CardioValues
import de.beispiel.meintraining.data.model.IntensityUnit
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/**
 * Cardio-Werte formatieren, einlesen und mit dem Pfeil verschieben.
 *
 * Eine eigene Datei statt neuer Abschnitte in `Formatters.kt` und `Parsing.kt`: Die Dauer läuft
 * hier in beide Richtungen über dieselbe Schreibweise – „7:30“ im Feld wie in der Liste –, und
 * das lässt sich an einer Stelle leichter beieinanderhalten.
 */

/**
 * Die Einheiten als Text; sie kommen aus den String-Ressourcen, wie die Einheit beim Gewicht
 * (siehe [toWeightLabel]). [level] steht *vor* der Zahl: „Stufe 8“.
 */
data class CardioUnits(
    val minutes: String,
    val kilometers: String,
    val meters: String,
    val kmh: String,
    val level: String,
    val percent: String
)

/** Trennt die Werte einer Einheit: „20 min · 6 km/h · 8 %“. */
private const val VALUE_SEPARATOR = " · "

/**
 * Zwischen Zahl und Einheit ein geschütztes Leerzeichen: Bricht eine lange Zeile um, dann an den
 * Trennpunkten – und nicht so, dass das „%“ allein in der nächsten Zeile steht.
 */
const val UNIT_SPACE = '\u00A0'

private const val METERS_PER_KM = 1000

// --- Formatieren -------------------------------------------------------------

/**
 * Eine Dauer in Minuten als Text, auf die Sekunde gerundet: `20.0 → "20"`, `7.5 → "7:30"`,
 * `0.75 → "0:45"`. Ganze Minuten ohne Sekunden – „20:00“ wäre auf den ersten Blick eine Uhrzeit.
 *
 * Dieselbe Schreibweise füllt das Eingabefeld, und [parseCardioDuration] liest sie wieder: Was
 * im Sheet steht, kommt beim Speichern unverändert zurück.
 */
fun formatDurationValue(minutes: Double): String {
    val totalSeconds = (minutes * SECONDS_PER_MINUTE).roundToLong().coerceAtLeast(0)
    val wholeMinutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (seconds == 0L) {
        wholeMinutes.toString()
    } else {
        "$wholeMinutes:${seconds.toString().padStart(2, '0')}"
    }
}

/** `20.0 → "20 min"`, `7.5 → "7:30 min"`. */
fun formatDuration(minutes: Double, units: CardioUnits): String =
    "${formatDurationValue(minutes)}$UNIT_SPACE${units.minutes}"

/**
 * Eine Distanz: ab 1 km in km mit Komma (`3.4 → "3,4 km"`), darunter in Metern
 * (`0.8 → "800 m"`) – „0,8 km“ liest sich auf dem Laufband-Display niemand so ab.
 *
 * Gerundet wird auf ganze Meter, *bevor* entschieden wird: 0,9996 km sind 1000 m und damit „1 km“.
 */
fun formatDistance(km: Double, units: CardioUnits): String {
    val meters = (km * METERS_PER_KM).roundToLong().coerceAtLeast(0)
    return if (meters < METERS_PER_KM) {
        "$meters$UNIT_SPACE${units.meters}"
    } else {
        "${km.toDecimalString()}$UNIT_SPACE${units.kilometers}"
    }
}

/** `6.5, KMH → "6,5 km/h"`, `8.0, LEVEL → "Stufe 8"`. */
fun formatIntensity(value: Double, unit: IntensityUnit, units: CardioUnits): String =
    when (unit) {
        IntensityUnit.KMH -> "${value.toDecimalString()}$UNIT_SPACE${units.kmh}"
        IntensityUnit.LEVEL -> "${units.level}$UNIT_SPACE${value.toDecimalString()}"
    }

/** `8.0 → "8 %"`, `2.5 → "2,5 %"`. */
fun formatIncline(percent: Double, units: CardioUnits): String =
    "${percent.toDecimalString()}$UNIT_SPACE${units.percent}"

/**
 * Ein einzelner Wert in seiner Schreibweise – für alles, was von genau einem Wert spricht: den
 * Schritt des Pfeils, die Meldung „Tempo auf 6,5 km/h erhöht“.
 *
 * [unit] zählt nur beim Tempo; ohne eine bekannte Einheit gilt km/h.
 */
fun formatCardioValue(
    value: CardioValue,
    amount: Double,
    unit: IntensityUnit?,
    units: CardioUnits
): String = when (value) {
    CardioValue.DURATION -> formatDuration(amount, units)
    CardioValue.DISTANCE -> formatDistance(amount, units)
    CardioValue.INTENSITY -> formatIntensity(amount, unit ?: IntensityUnit.KMH, units)
    CardioValue.INCLINE -> formatIncline(amount, units)
}

/**
 * Alle gesetzten Werte in einer Zeile: `"22 min · 3,4 km · 6 km/h · 8 %"`; ohne einen Wert `null`.
 *
 * Mit [distanceFirst] steht die Distanz vorn – `"5 km · 30 min"`: Wer eine Strecke als Ziel hat,
 * liest die Zeit als „in wie vielen Minuten“. Eine eingetragene Einheit beginnt dagegen mit der
 * Dauer, wie ein Gerätedisplay am Ende.
 */
fun formatCardioValues(
    values: CardioValues,
    units: CardioUnits,
    distanceFirst: Boolean = false
): String? {
    val duration = values.durationMin?.let { formatDuration(it, units) }
    val distance = values.distanceKm?.let { formatDistance(it, units) }
    val parts = buildList {
        if (distanceFirst) add(distance)
        add(duration)
        if (!distanceFirst) add(distance)
        add(values.intensity?.let { formatIntensity(it, values.intensityUnit ?: IntensityUnit.KMH, units) })
        add(values.inclinePercent?.let { formatIncline(it, units) })
    }.filterNotNull()
    return parts.takeIf { it.isNotEmpty() }?.joinToString(VALUE_SEPARATOR)
}

// --- Einlesen ----------------------------------------------------------------

/**
 * Liest eine Dauer: Minuten, auch mit Komma oder Punkt (`"7,5" → 7.5`), oder Minuten und
 * Sekunden (`"7:30" → 7.5`, `"0:45" → 0.75`). Leer, ungültig oder nicht länger als null → `null`.
 *
 * Die Sekunden brauchen genau zwei Ziffern unter 60, wie auf jeder Uhr: „7:5“ könnte 7:05 oder
 * ein halb getipptes 7:50 sein, und „7:75“ ist ein Tippfehler, keine 8:15. Ob ein Feld leer
 * oder falsch ist, sagt [isValidCardioDurationInput].
 */
fun parseCardioDuration(input: String): Double? {
    val text = input.trim()
    if (text.isEmpty()) return null
    val parts = text.split(':')
    val minutes = when (parts.size) {
        1 -> parseOptionalDecimal(text)
        2 -> {
            val wholeMinutes = parts[0].takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
                ?.toLongOrNull()
            val seconds = parts[1].takeIf { it.length == 2 && it.all(Char::isDigit) }
                ?.toInt()
                ?.takeIf { it < SECONDS_PER_MINUTE }
            if (wholeMinutes == null || seconds == null) {
                null
            } else {
                wholeMinutes + seconds.toDouble() / SECONDS_PER_MINUTE
            }
        }
        else -> null
    }
    return minutes?.takeIf { it > 0.0 }
}

/** Leer (kein Ziel) oder eine Dauer, die [parseCardioDuration] annimmt. */
fun isValidCardioDurationInput(input: String): Boolean =
    input.isBlank() || parseCardioDuration(input) != null

// --- Der Pfeil ---------------------------------------------------------------

/**
 * Die Schnellauswahl für den Schritt, passend zum Wert: Minuten in ganzen Schritten, Distanzen
 * in Viertel-Kilometern, ein Tempo in Zehnteln, Stufen einzeln, die Steigung in halben Prozent.
 */
fun cardioStepSuggestions(value: CardioValue, unit: IntensityUnit): List<Double> = when (value) {
    CardioValue.DURATION -> listOf(1.0, 2.0, 5.0)
    CardioValue.DISTANCE -> listOf(0.25, 0.5, 1.0)
    CardioValue.INTENSITY -> when (unit) {
        IntensityUnit.KMH -> listOf(0.1, 0.5, 1.0)
        IntensityUnit.LEVEL -> listOf(1.0)
    }
    CardioValue.INCLINE -> listOf(0.5, 1.0, 2.0)
}

/**
 * Der Schritt, wenn keiner eingetragen ist – und der, mit dem das Feld beim Wechsel des Werts
 * neu beginnt: eine Minute, ein halber Kilometer, 0,5 km/h, eine Stufe, ein halbes Prozent.
 * Jeweils eine Steigerung, die man in der nächsten Einheit tatsächlich schafft.
 */
fun defaultCardioStep(value: CardioValue, unit: IntensityUnit): Double = when (value) {
    CardioValue.DURATION -> 1.0
    CardioValue.DISTANCE -> 0.5
    CardioValue.INTENSITY -> when (unit) {
        IntensityUnit.KMH -> 0.5
        IntensityUnit.LEVEL -> 1.0
    }
    CardioValue.INCLINE -> 0.5
}

/**
 * Liest den Schritt des Pfeils. Bei der Dauer gilt auch „0:30“; leer, ungültig oder nicht
 * positiv → die Vorgabe des Werts ([defaultCardioStep]), wie beim Progressionsschritt.
 */
fun parseCardioStep(input: String, value: CardioValue, unit: IntensityUnit): Double {
    val parsed = if (value == CardioValue.DURATION) {
        parseCardioDuration(input)
    } else {
        parseOptionalDecimal(input)
    }
    return parsed?.takeIf { it > 0.0 } ?: defaultCardioStep(value, unit)
}

/** Der Schritt als Text fürs Feld – bei der Dauer in derselben Schreibweise wie die Dauer selbst. */
fun formatCardioStepInput(step: Double, value: CardioValue): String =
    if (value == CardioValue.DURATION) formatDurationValue(step) else step.toDecimalString()

/**
 * Ein Schritt des Pfeils auf einem Cardio-Ziel – nach oben, mit [down] nach unten, mit
 * [reverse] genau einen gegen die Pfeilrichtung.
 *
 * Dieselbe Rechnung wie beim Gewicht ([stepWeight]): exakt in Dezimalen, damit aus
 * `6 + 0,1` nicht `6,1000000001` wird, und unten bei null Schluss.
 */
fun stepCardioTarget(current: Double, step: Double, down: Boolean, reverse: Boolean = false): Double =
    stepWeight(currentKg = current, stepKg = step, progressionDown = down, reverse = reverse)

// --- Letztes Mal ---------------------------------------------------------------

/** Die zuletzt eingetragene Einheit einer Übung: ihre Werte und wie lange sie her ist. */
data class LastCardioEntry(
    val values: CardioValues,
    val date: LocalDate,
    /** Kalendertage seit [date]; nie negativ. */
    val daysAgo: Int
)

/**
 * Die jüngste Einheit aus [logsOldestFirst] – den Einheiten *einer* Übung in der Reihenfolge der
 * DAO; ohne Einträge `null`. Für die Zeile im Bearbeiten-Sheet, die bei Cardio an der Stelle des
 * Gewichtsverlaufs steht: „Letztes Mal (vor 3 Tagen): 22 min · 3,4 km · 6 km/h · 8 %“.
 */
fun lastCardioEntry(
    logsOldestFirst: List<CardioLog>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): LastCardioEntry? {
    val latest = logsOldestFirst.lastOrNull() ?: return null
    val date = latest.performedAt.toLocalDate(zone)
    return LastCardioEntry(
        values = latest.values,
        date = date,
        // Eine zurückgestellte Uhr ergäbe sonst „vor -3 Tagen“.
        daysAgo = ChronoUnit.DAYS.between(date, today).toInt().coerceAtLeast(0)
    )
}

// --- Heute eingetragen ---------------------------------------------------------

/**
 * Die Einheiten nach Übung samt Variation zerlegt – einmal je Änderung und nicht bei jedem
 * Zusammensetzen der Liste. Derselbe Schlüssel wie beim Satz-Protokoll ([SetLogKey]): Auch hier
 * gehört der Trainingstag nicht dazu, „Letztes Mal“ darf von einem anderen stammen.
 */
fun cardioLogsByExercise(logsOldestFirst: List<CardioLog>): Map<SetLogKey, List<CardioLog>> =
    logsOldestFirst.groupBy { SetLogKey(it.exerciseName, it.variation) }

/**
 * Die heute an Trainingstag [dayId] eingetragene Einheit aus [logsOldestFirst] – den Einheiten
 * *einer* Übung; ohne eine `null`. Sie hakt den Chip in der Liste ab, und „Cardio eintragen“
 * korrigiert sie, statt eine zweite anzulegen (siehe `TrainingRepository.logCardio`).
 */
fun todaysCardioLog(
    logsOldestFirst: List<CardioLog>,
    dayId: Int,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): CardioLog? = logsOldestFirst.lastOrNull {
    it.dayId == dayId && it.performedAt.toLocalDate(zone) == today
}

/**
 * „Letztes Mal“ über dem Dialog „Cardio eintragen“: die jüngste Einheit dieser Übung außer der,
 * die der Dialog gerade bearbeitet ([current]) – sonst stünde beim Korrigieren die eigene
 * Eingabe von eben als letztes Mal da. Eine Einheit an einem anderen Trainingstag von heute
 * zählt mit; die Ziele hängen am Namen, nicht am Tag.
 */
fun lastCardioEntryBefore(
    logsOldestFirst: List<CardioLog>,
    current: CardioLog?,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): LastCardioEntry? = lastCardioEntry(
    logsOldestFirst = if (current == null) logsOldestFirst else logsOldestFirst.filter { it.id != current.id },
    today = today,
    zone = zone
)
