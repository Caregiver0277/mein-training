package de.beispiel.meintraining.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier

/**
 * Lässt zu langen Text ein paar Mal durchlaufen, statt ihn abzuschneiden.
 *
 * Passt der Text in die verfügbare Breite, passiert nichts – die Animation startet nur bei
 * Überlänge. Zwischen den Durchläufen liegt eine Pause, in der der Anfang zu lesen ist.
 *
 * Bewusst endlich: Ein laufender Text zeichnet sich in voller Bildwiederholrate neu, und in
 * der Trainingsliste hängt der an *jeder* zu langen Übung und an jedem zu schmalen Wertechip
 * gleichzeitig. Endlos gelaufen hieße: Solange die Liste offen ist – im Studio zwischen den
 * Sätzen also durchgehend – rechnet und zeichnet das Gerät ununterbrochen, für Text, den man
 * nach dem zweiten Durchlauf gelesen hat. Nach [MARQUEE_ITERATIONS] Durchläufen kommt die
 * Zeile zur Ruhe; ein Tageswechsel oder ein geänderter Wert startet sie wieder.
 *
 * Wichtig am Aufrufort: `maxLines = 1` und `softWrap = false` setzen und *kein*
 * `TextOverflow.Ellipsis`, sonst kürzt der Text sich weg, bevor er laufen kann.
 */
fun Modifier.loopingMarquee(): Modifier = basicMarquee(iterations = MARQUEE_ITERATIONS)

/** Zweimal reicht zum Lesen, das dritte Mal ist für den, der gerade weggesehen hat. */
private const val MARQUEE_ITERATIONS = 3
