package de.beispiel.meintraining.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier

/**
 * Lässt zu langen Text endlos durchlaufen, statt ihn abzuschneiden.
 *
 * Passt der Text in die verfügbare Breite, passiert nichts – die Animation startet nur bei
 * Überlänge. Zwischen den Durchläufen liegt eine Pause, in der der Anfang zu lesen ist.
 *
 * Wichtig am Aufrufort: `maxLines = 1` und `softWrap = false` setzen und *kein*
 * `TextOverflow.Ellipsis`, sonst kürzt der Text sich weg, bevor er laufen kann.
 */
fun Modifier.loopingMarquee(): Modifier = basicMarquee(iterations = Int.MAX_VALUE)
