package de.beispiel.meintraining.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.beispiel.meintraining.R
import de.beispiel.meintraining.util.CardioUnits

/** Die Einheiten der Cardio-Werte aus den Textressourcen – für die Formatierer in `util/Cardio.kt`. */
@Composable
fun cardioUnits(): CardioUnits = CardioUnits(
    minutes = stringResource(R.string.cardio_unit_minutes),
    kilometers = stringResource(R.string.cardio_unit_km),
    meters = stringResource(R.string.cardio_unit_m),
    kmh = stringResource(R.string.cardio_unit_kmh),
    level = stringResource(R.string.cardio_unit_level),
    percent = stringResource(R.string.cardio_unit_percent)
)
