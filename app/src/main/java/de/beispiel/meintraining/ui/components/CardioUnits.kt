package de.beispiel.meintraining.ui.components

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import de.beispiel.meintraining.R
import de.beispiel.meintraining.util.CardioUnits

/** Die Einheiten der Cardio-Werte aus den Textressourcen – für die Formatierer in `util/Cardio.kt`. */
@Composable
fun cardioUnits(): CardioUnits = cardioUnits(LocalResources.current)

/** Dasselbe außerhalb einer Composable – für Meldungen, die in einem Effekt entstehen. */
fun cardioUnits(resources: Resources): CardioUnits = CardioUnits(
    minutes = resources.getString(R.string.cardio_unit_minutes),
    kilometers = resources.getString(R.string.cardio_unit_km),
    meters = resources.getString(R.string.cardio_unit_m),
    kmh = resources.getString(R.string.cardio_unit_kmh),
    level = resources.getString(R.string.cardio_unit_level),
    percent = resources.getString(R.string.cardio_unit_percent)
)
