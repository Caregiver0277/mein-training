package de.beispiel.meintraining.util

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Vorgabewert für den Progressionsschritt, wenn keine gültige Eingabe vorliegt. */
const val DEFAULT_PROGRESSION_STEP_KG = 2.5

/** Vorschlagswerte der Schnellauswahl im Bearbeiten-Sheet. */
val PROGRESSION_STEP_SUGGESTIONS = listOf(1.25, 2.5, 5.0)

// Trennzeichen der Sätze-/Wiederholungs-Notation. Reine Notation, keine übersetzbaren Texte.
private const val SETS_REPS_SEPARATOR = " x "
private const val REPS_RANGE_SEPARATOR = "-"

/**
 * [DecimalFormat] ist nicht threadsicher, ein neues Exemplar je Aufruf aber Verschwendung:
 * Der Graph formatiert seine Achsenbeschriftungen in jedem Zeichendurchgang. Ein Exemplar
 * pro Thread löst beides.
 */
private val GERMAN_DECIMAL_FORMAT: ThreadLocal<DecimalFormat> =
    ThreadLocal.withInitial { DecimalFormat("0.##", DecimalFormatSymbols(Locale.GERMANY)) }

/** `20.0 → "20"`, `22.5 → "22,5"`, `1.25 → "1,25"` – immer mit deutschem Dezimalkomma. */
fun Double.toDecimalString(): String =
    // withInitial liefert immer ein Exemplar; nur die Java-Signatur weiß das nicht.
    GERMAN_DECIMAL_FORMAT.get()!!.format(this)

private val FULL_DATE = DateTimeFormatter.ofPattern("EEE, d. MMMM yyyy", Locale.GERMANY)
private val SHORT_DATE = DateTimeFormatter.ofPattern("d. MMM", Locale.GERMANY)
private val CLOCK_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)

/** Zeitstempel als Datum in der Zeitzone des Geräts. */
fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

/** `"18:42"` – Uhrzeit eines Zeitstempels in der Zeitzone des Geräts. */
fun Long.toClockTime(zone: ZoneId = ZoneId.systemDefault()): String =
    CLOCK_TIME.format(Instant.ofEpochMilli(this).atZone(zone))

/** `"Sa, 2. August 2026"` – für den Verlauf. */
fun formatFullDate(date: LocalDate): String = FULL_DATE.format(date)

/** `"2. Aug"` – wo wenig Platz ist. */
fun formatShortDate(date: LocalDate): String = SHORT_DATE.format(date)

/**
 * Anzeigename einer Übung. Die Variation steht in Klammern dahinter:
 * `"Trizeps", "Seil" → "Trizeps (Seil)"`, ohne Variation bleibt es beim Namen.
 */
fun exerciseTitle(name: String, variation: String?): String =
    if (variation.isNullOrBlank()) name else "$name (${variation.trim()})"

/**
 * Gewichtslabel für die Karte. `20.0 → "20 Kg"`, `22.5 → "22,5 Kg"`.
 * [unit] kommt aus den String-Ressourcen.
 */
fun Double.toWeightLabel(unit: String): String = "${toDecimalString()} $unit"

/**
 * Sätze-/Wiederholungslabel für die Karte.
 * `3, 4, 6 → "3 x 4-6"`, `3, 6, 6 → "3 x 6"`, `3, null, null → "3"`.
 * Ohne Sätze gibt es nichts anzuzeigen: `null → null`.
 */
fun Int?.toSetsRepsLabel(repsMin: Int?, repsMax: Int?): String? {
    val sets = this ?: return null
    val reps = when {
        repsMin != null && repsMax != null && repsMin != repsMax ->
            "$repsMin$REPS_RANGE_SEPARATOR$repsMax"
        repsMin != null -> repsMin.toString()
        repsMax != null -> repsMax.toString()
        else -> null
    }
    return if (reps == null) sets.toString() else "$sets$SETS_REPS_SEPARATOR$reps"
}

/**
 * Liest einen Progressionsschritt ein. Komma und Punkt sind als Dezimaltrenner erlaubt;
 * leere, ungültige oder nicht positive Eingaben fallen auf [DEFAULT_PROGRESSION_STEP_KG] zurück.
 */
fun parseProgressionStep(input: String): Double {
    val value = parseOptionalDecimal(input) ?: return DEFAULT_PROGRESSION_STEP_KG
    return if (value > 0.0) value else DEFAULT_PROGRESSION_STEP_KG
}

/** Optionale Dezimalzahl; akzeptiert Komma und Punkt. Leer oder ungültig → `null`. */
fun parseOptionalDecimal(input: String): Double? {
    val normalized = input.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    return normalized.toDoubleOrNull()
}

/** Optionale Ganzzahl. Leer, ungültig oder negativ → `null`. */
fun parseOptionalInt(input: String): Int? {
    val value = input.trim().toIntOrNull() ?: return null
    return if (value >= 0) value else null
}

/**
 * Erhöht ein Gewicht um den Progressionsschritt.
 * Rechnet mit [BigDecimal], damit aus `20 + 2,5` nicht `22,499999…` wird.
 */
fun increaseWeight(currentKg: Double, stepKg: Double): Double =
    BigDecimal.valueOf(currentKg).add(BigDecimal.valueOf(stepKg)).toDouble()
