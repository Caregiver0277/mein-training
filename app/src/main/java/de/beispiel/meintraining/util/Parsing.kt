package de.beispiel.meintraining.util

import java.math.BigDecimal

/**
 * Einlesen der Eingabefelder und Rechnen mit Gewichten.
 *
 * Getrennt von den Formatierern: Dort geht es darum, wie ein Wert aussieht, hier darum, ob er
 * überhaupt einer ist. Diese Funktionen sind die einzige Stelle, an der getippter Text zu
 * gespeicherten Zahlen wird – was sie durchlassen, steht anschließend in der Datenbank.
 */

/**
 * Liest einen Progressionsschritt ein. Komma und Punkt sind als Dezimaltrenner erlaubt;
 * leere, ungültige oder nicht positive Eingaben fallen auf [DEFAULT_PROGRESSION_STEP_KG] zurück.
 */
fun parseProgressionStep(input: String): Double {
    val value = parseOptionalDecimal(input) ?: return DEFAULT_PROGRESSION_STEP_KG
    return if (value > 0.0) value else DEFAULT_PROGRESSION_STEP_KG
}

/**
 * Optionale Dezimalzahl; akzeptiert Komma und Punkt. Leer oder ungültig → `null`.
 *
 * Aussortiert werden auch `NaN` und `Infinity`: Die Java-Zahlenlesung nimmt beide klaglos an,
 * und über die Zwischenablage kommen sie trotz Zifferntastatur ins Feld. Ein solcher Wert
 * landete sonst als Gewicht in der Datenbank und ließe [increaseWeight] beim nächsten Druck
 * auf den Pfeil scheitern – mitten in einer Coroutine, also mit Absturz.
 *
 * Negative Gewichte fallen aus demselben Grund weg wie bei [parseOptionalInt]: Es gibt sie
 * nicht, und der Graph zeichnete sie klaglos mit.
 */
fun parseOptionalDecimal(input: String): Double? {
    val normalized = input.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
}

/** Längste Dauer, die sich nachtragen lässt – dieselbe Grenze wie beim Abhaken. */
const val MAX_WORKOUT_MINUTES = (MAX_WORKOUT_MILLIS / 60_000L).toInt()

/**
 * Die Dauer aus dem Nachtragen-Dialog in Minuten: `"64" → 64`. Leer, ungültig oder außerhalb
 * von 1 bis [MAX_WORKOUT_MINUTES] → `null`; ob das Feld leer oder falsch ist, sagt
 * [isValidDurationInput].
 *
 * Kürzer als beim Abhaken darf es sein: Wer eine Dauer von Hand einträgt, weiß sie – die
 * Untergrenze dort fängt nur aus Versehen gestartete Pausenuhren ab.
 */
fun parseDurationMinutes(input: String): Int? =
    input.trim().toIntOrNull()?.takeIf { it in 1..MAX_WORKOUT_MINUTES }

/** Leer (keine Angabe) oder eine Dauer, die [parseDurationMinutes] annimmt. */
fun isValidDurationInput(input: String): Boolean =
    input.isBlank() || parseDurationMinutes(input) != null

/** Optionale Ganzzahl. Leer, ungültig oder negativ → `null`. */
fun parseOptionalInt(input: String): Int? {
    val value = input.trim().toIntOrNull() ?: return null
    return if (value >= 0) value else null
}

/**
 * Erhöht ein Gewicht um den Progressionsschritt.
 * Rechnet mit [BigDecimal], damit aus `20 + 2,5` nicht `22,499999…` wird.
 *
 * Setzt endliche Werte voraus; dafür sorgt [parseOptionalDecimal] beim Einlesen.
 */
fun increaseWeight(currentKg: Double, stepKg: Double): Double =
    BigDecimal.valueOf(currentKg).add(BigDecimal.valueOf(stepKg)).toDouble()

/**
 * Senkt ein Gewicht um den Progressionsschritt – für Übungen, deren Pfeil nach unten zeigt
 * (siehe [de.beispiel.meintraining.data.model.ExerciseDefinition.progressionDown]).
 *
 * Bei 0 kg ist Schluss: Negative Gewichte gibt es nicht, sie kämen an keinem Eingabefeld
 * vorbei ([parseOptionalDecimal]) und der Verlaufsgraph zeichnete sie klaglos mit. Wer schon
 * bei 0 steht, bleibt dort – der Druck auf den Pfeil bewirkt dann nichts.
 *
 * Gerechnet wird mit [BigDecimal] wie beim Erhöhen, damit aus `22,5 − 2,5` nicht
 * `19,999999…` wird.
 */
fun decreaseWeight(currentKg: Double, stepKg: Double): Double =
    BigDecimal.valueOf(currentKg).subtract(BigDecimal.valueOf(stepKg)).toDouble()
        .coerceAtLeast(0.0)

/**
 * Ein Progressionsschritt in die Richtung, die der Pfeil der Übung zeigt – oder mit
 * [reverse] genau einen Schritt dagegen: Ein langer Druck auf den Pfeil nimmt eine zu
 * optimistische Steigerung zurück, ohne die Richtung der Übung umzustellen.
 *
 * Unten ist wie bei [decreaseWeight] bei 0 kg Schluss: Ein Schritt, der darunter führte,
 * endet auf 0, und wer schon bei 0 steht, bleibt dort.
 */
fun stepWeight(
    currentKg: Double,
    stepKg: Double,
    progressionDown: Boolean,
    reverse: Boolean = false
): Double = if (progressionDown != reverse) {
    decreaseWeight(currentKg, stepKg)
} else {
    increaseWeight(currentKg, stepKg)
}
