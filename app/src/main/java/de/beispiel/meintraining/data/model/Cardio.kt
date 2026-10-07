package de.beispiel.meintraining.data.model

/**
 * Die Art einer Übung. Hängt wie Gewicht und Schritt am Namen (siehe [ExerciseDefinition.kind])
 * und gilt damit an allen Tagen, an denen die Übung vorkommt.
 */
enum class ExerciseKind {
    /** Gewicht, Sätze und Wiederholungen – alles, was es vor Cardio gab. */
    STRENGTH,

    /** Dauer, Distanz, Tempo oder Stufe und Steigung – Laufband, Rad, Rudergerät und Ähnliches. */
    CARDIO
}

/**
 * Einheit des dritten Cardio-Werts: ein Tempo in km/h oder eine Stufe am Gerät. Pro Übung
 * gewählt – auf dem Laufband zählt das Tempo, auf dem Crosstrainer die Stufe.
 */
enum class IntensityUnit { KMH, LEVEL }

/** Die Cardio-Werte, die der Pfeil einer Übung steigern (oder senken) kann. */
enum class CardioValue { DURATION, DISTANCE, INTENSITY, INCLINE }

/**
 * Die Zielwerte einer Cardio-Übung samt ihrem Pfeil – am Namen gespeichert, als Teil der
 * [ExerciseDefinition] (Spalten mit dem Vorsatz `cardio_`).
 *
 * Eigene Spalten an der Definition statt einer eigenen Tabelle: Die Ziele leben und sterben
 * genau mit dem Namen – Umbenennen, Aufräumen verwaister Namen, Wiederherstellen nach dem
 * Löschen und die Sicherung greifen damit ohne jede Zusatzregel. Eine eigene Tabelle bräuchte
 * für jeden dieser Wege eine zweite Abfrage, die man irgendwann vergisst.
 *
 * Bei einer Kraftübung stehen sie unbeachtet daneben: Wechselt eine Übung die Art, bleiben die
 * Werte der anderen erhalten und kommen beim Zurückwechseln wieder.
 */
data class CardioTargets(
    /** Dauer in Minuten, auch mit Bruchteil – 7,5 sind 7:30. */
    val durationMin: Double? = null,
    val distanceKm: Double? = null,
    /** Tempo oder Stufe, je nach [intensityUnit]. */
    val intensity: Double? = null,
    /** Bleibt gewählt, auch solange kein [intensity] eingetragen ist. */
    val intensityUnit: IntensityUnit = IntensityUnit.KMH,
    val inclinePercent: Double? = null,
    /** Welchen Wert der Pfeil verschiebt; `null`: keinen – dann gibt es keinen Pfeil. */
    val arrowValue: CardioValue? = null,
    /** Um wie viel; `null`: die Vorgabe des Werts (siehe `defaultCardioStep`). */
    val arrowStep: Double? = null,
    /**
     * Senkt der Pfeil den Wert, statt ihn zu erhöhen – etwa weniger Minuten auf dieselbe Distanz.
     *
     * Bewusst ein eigener Wert und nicht [ExerciseDefinition.progressionDown]: Teilten sich beide
     * Arten die Spalte, kippte ein Wechsel der Art die Richtung der anderen gleich mit.
     */
    val arrowDown: Boolean = false
) {
    val values: CardioValues
        get() = CardioValues(
            durationMin = durationMin,
            distanceKm = distanceKm,
            intensity = intensity,
            intensityUnit = intensityUnit.takeIf { intensity != null },
            inclinePercent = inclinePercent
        )

    /** Der Wert, den der Pfeil verschiebt; `null`, wenn er keinen hat. */
    fun valueOf(value: CardioValue): Double? = when (value) {
        CardioValue.DURATION -> durationMin
        CardioValue.DISTANCE -> distanceKm
        CardioValue.INTENSITY -> intensity
        CardioValue.INCLINE -> inclinePercent
    }

    /** Dieselben Ziele mit [value] auf [amount]. */
    fun with(value: CardioValue, amount: Double): CardioTargets = when (value) {
        CardioValue.DURATION -> copy(durationMin = amount)
        CardioValue.DISTANCE -> copy(distanceKm = amount)
        CardioValue.INTENSITY -> copy(intensity = amount)
        CardioValue.INCLINE -> copy(inclinePercent = amount)
    }
}

/**
 * Die Werte einer Cardio-Einheit, ob als Ziel oder als eingetragene Einheit – das, was
 * angezeigt und formatiert wird. Kein Teil der Datenbank; [CardioTargets] und [CardioLog]
 * liefern sie.
 *
 * [intensityUnit] ist `null`, solange es kein [intensity] gibt.
 */
data class CardioValues(
    val durationMin: Double? = null,
    val distanceKm: Double? = null,
    val intensity: Double? = null,
    val intensityUnit: IntensityUnit? = null,
    val inclinePercent: Double? = null
) {
    /** Steht überhaupt ein Wert da? Eine Einheit ganz ohne Werte gibt es nicht. */
    val isEmpty: Boolean
        get() = durationMin == null && distanceKm == null && intensity == null &&
            inclinePercent == null
}
