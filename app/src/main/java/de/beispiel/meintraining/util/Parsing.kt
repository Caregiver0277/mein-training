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
